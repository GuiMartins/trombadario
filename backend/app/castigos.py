"""Quando o castigo começa, quanto ele dura, e quem o cria sozinho.

Mora fora dos routers porque a API e o painel web precisam da **mesma**
resposta: duas contas em dois lugares é como o prazo sugerido e o começo da
fila sairiam diferentes conforme a tela. Mesmo motivo de `app/periodo.py`
concentrar a tradução de fuso.

O desenho é o de sempre neste projeto: nada de coluna de status, nada de algo
rodando pra virar número. O nível de recorrência é **calculado** a partir das
anotações; o que se grava é o retrato do que foi decidido.
"""

from datetime import UTC, date, datetime, timedelta

from fastapi import HTTPException, status
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models import Kind, Punishment, Trombadice, TrombadiceCategory, User
from app.periodo import data_local

SOBREPOSICAO = HTTPException(
    status_code=status.HTTP_400_BAD_REQUEST,
    detail="Esse período se sobrepõe a outro castigo desse filho",
)


def proximo_inicio(db: Session, child_id: int, agora: datetime) -> datetime:
    """Quando o próximo castigo deste filho começa: emendado no fim do último da
    fila, ou agora, quando não há fila.

    É o que faz castigo virar **sequência** sem o pai ter que fazer conta de
    calendário. Aplicar um castigo hoje e outro em seguida quer dizer "e mais um
    dia depois desse", não dois castigos sobrepostos - dois valendo ao mesmo
    tempo não significam nada pra criança, que só pode estar de castigo ou não.
    Desde que o castigo é automático, é também o que garante isso sem ninguém
    pedir: trombadice durante castigo emenda no fim, não em cima.

    Quem manda é o **fim de verdade** (`effective_end`), não o `ends_at`: um
    castigo que o pai encerrou antes já acabou e não segura mais a fila.

    Fica aqui, e não em cada tela, porque app e painel precisam da mesma
    resposta - e porque quando o castigo começa é conta de servidor, como todo
    resto de data neste projeto.
    """
    fins = [
        p.effective_end
        for p in db.scalars(select(Punishment).where(Punishment.child_id == child_id))
    ]
    return max([agora, *fins])


def sem_sobreposicao(
    db: Session,
    child_id: int,
    starts_at: datetime,
    ends_at: datetime,
    ignorando: int | None = None,
) -> None:
    """Recusa um período que cruze outro castigo do mesmo filho.

    Requisito do usuário: não pode haver dois castigos valendo ao mesmo tempo.
    O automático nunca tropeça nisto (nasce em `proximo_inicio`), mas o cadastro
    à mão tropeçaria, e a checagem tem que ser no servidor pelo mesmo motivo de
    sempre - o filho tem o APK na mão.

    **O que já está no banco não é reescrito.** Uma instalação que tenha dois
    castigos sobrepostos de antes continua com eles, e por isso a tela do filho
    segue preparada pra mostrar mais de um. A invariante vale pra escrita nova.

    Compara pelo `effective_end`: um castigo que o pai encerrou antes já acabou e
    não impede nada depois disso.
    """
    outros = db.scalars(select(Punishment).where(Punishment.child_id == child_id))
    for outro in outros:
        if outro.id == ignorando:
            continue
        if starts_at < outro.effective_end and outro.starts_at < ends_at:
            raise SOBREPOSICAO


def dias_de_castigo(
    db: Session,
    child_id: int,
    categoria: TrombadiceCategory,
    dia: date,
    ignorando: int | None = None,
) -> tuple[int, int]:
    """Quantos dias de castigo uma trombadice deste tipo custa neste dia, e em
    que nível de recorrência ela cai. Devolve `(dias, nivel)`.

    A conta é `base + aumento * nivel`, limitada ao teto. O nível é quantas
    recorrências estão na conta, e ele **sobe a cada ocorrência e desce um por
    dia limpo** - dia em que aquele tipo não aconteceu com aquele filho.

    O `- 1` na contagem de dias limpos não é acidente: o dia da ocorrência
    seguinte não é um dia limpo. Segunda e terça seguidas têm zero dias limpos
    entre elas, e é por isso que a recorrência cresce; de segunda a sábado são
    quatro (ter, qua, qui, sex).

    Caminha sobre as anotações até `dia` inclusive, e não sobre um contador
    guardado, pelo mesmo motivo de `is_active_at` ser calculado: contador em
    coluna precisaria de algo rodando pra decair e ficaria errado no intervalo.
    O volume é o de uma casa - algumas centenas de linhas por ano.

    Anotação com data futura não entra: o pai registra depois do fato, então o
    que ainda não aconteceu não pode cobrar recorrência de nada.

    `ignorando` tira uma anotação da conta - é a própria, quando quem pergunta é
    `criar_castigo_automatico` depois de gravá-la. Sem isso ela se contaria e a
    primeira vez já sairia como recorrência. É também o que faz a previsão do
    formulário e o castigo criado darem o mesmo número.
    """
    if categoria.punishment_days <= 0:
        return 0, 0

    anteriores = db.scalars(
        select(Trombadice)
        .where(
            Trombadice.child_id == child_id,
            Trombadice.category_id == categoria.id,
            Trombadice.kind == Kind.TROMBADICE,
        )
        .order_by(Trombadice.occurred_at, Trombadice.id)
    )

    nivel = 0
    anterior: date | None = None
    for t in anteriores:
        if t.id == ignorando:
            continue
        d = data_local(t.occurred_at)
        if d > dia:
            break
        nivel = _decaido(nivel, anterior, d) + 1
        anterior = d

    nivel = _decaido(nivel, anterior, dia)
    dias = categoria.punishment_days + categoria.escalation_days * nivel
    if categoria.max_days > 0:
        dias = min(dias, categoria.max_days)
    return dias, nivel


def _decaido(nivel: int, anterior: date | None, dia: date) -> int:
    """O nível depois dos dias limpos entre `anterior` e `dia`: um a menos por
    dia em que não aconteceu, nunca abaixo de zero."""
    if anterior is None:
        return nivel
    limpos = max(0, (dia - anterior).days - 1)
    return max(0, nivel - limpos)


def criar_castigo_automatico(
    db: Session, trombadice: Trombadice, autor: User
) -> Punishment | None:
    """O castigo que a anotação gera, já emendado no fim da fila do filho.

    Devolve `None` quando não há castigo a dar: conquista (que nunca causa
    castigo), anotação sem tipo, ou tipo que custa zero dia - que é como toda
    instalação existente começa, até o pai preencher.

    O castigo nasce com a trombadice como causa, então a regra "castigo sem
    trombadice não existe" fica satisfeita por construção, não por validação.

    Não faz `commit`: quem chama está no meio de criar a anotação, e as duas
    coisas são um fato só - uma anotação registrada sem o castigo dela seria
    metade do que o pai pediu.
    """
    if trombadice.kind is not Kind.TROMBADICE or trombadice.category is None:
        return None

    dias, nivel = dias_de_castigo(
        db,
        trombadice.child_id,
        trombadice.category,
        data_local(trombadice.occurred_at),
        ignorando=trombadice.id,
    )
    if dias <= 0:
        return None

    inicio = proximo_inicio(db, trombadice.child_id, datetime.now(UTC))
    castigo = Punishment(
        # O nome do tipo, e não vazio: o cartão do painel usa `reason` como
        # título e um castigo sem título seria um cartão em branco. É retrato do
        # que o pai teria digitado, não derivação - corrigir o tipo depois não
        # reescreve o que já foi dado.
        reason=trombadice.category.name,
        starts_at=inicio,
        ends_at=inicio + timedelta(days=dias),
        child_id=trombadice.child_id,
        author_id=autor.id,
        origin_trombadice_id=trombadice.id,
        recurrence_level=nivel,
    )
    castigo.trombadices = [trombadice]
    db.add(castigo)
    return castigo


def apagar_castigos_sem_causa(db: Session, trombadice: Trombadice) -> None:
    """Apaga todo castigo que ficaria sem causa nenhuma ao apagar esta anotação.

    Pega o gerado automaticamente por ela e também o cadastrado à mão cuja única
    causa era ela. Antes desta função o vínculo caía por CASCADE e sobrava um
    castigo órfão - uma punição que a criança lê sem saber de onde veio, que é
    exatamente o que "castigo sem trombadice não existe" proíbe.

    É aqui, e não numa FK do banco, por duas razões. A decisão tem uma condição
    que o banco não sabe avaliar - o castigo que o pai depois atrelou a outras
    trombadices ainda tem por que existir. E, no SQLite, acrescentar a coluna com
    FK exigiria recriar a tabela, o que apagaria todos os vínculos de causa da
    instalação (ver o comentário em `models.Punishment.origin_trombadice_id`).

    Então o SET NULL também é feito aqui, no castigo que sobrevive: ele deixa de
    dizer que nasceu de uma anotação que não existe mais, e continua apontando as
    causas que restaram.

    Deixa buraco na fila em vez de puxar os seguintes pra frente, mantendo o que
    Encerrar já faz: um castigo dado com data continua com ela.
    """
    for castigo in list(
        db.scalars(
            select(Punishment).where(Punishment.trombadices.any(Trombadice.id == trombadice.id))
        )
    ):
        if all(t.id == trombadice.id for t in castigo.trombadices):
            db.delete(castigo)

    # O que sobrou e apontava esta anotação como origem para de apontar: o rastro
    # não pode citar uma linha que deixou de existir.
    for castigo in db.scalars(
        select(Punishment).where(Punishment.origin_trombadice_id == trombadice.id)
    ):
        castigo.origin_trombadice_id = None
