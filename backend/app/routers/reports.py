"""O que dá para dizer olhando os dados.

Tudo é contado em Python, sobre as linhas do período, e não com `GROUP BY` no
SQLite. Dois motivos: o banco guarda UTC e o agrupamento que interessa é por dia
**local** (agrupar por `date(occurred_at)` jogaria tudo depois das 21h para o dia
seguinte), e o volume aqui é o de uma casa - algumas centenas de linhas por ano.
Se um dia virar dezenas de milhares, é aqui que se mexe.
"""

from collections import Counter
from datetime import date, datetime, timedelta

from fastapi import APIRouter
from sqlalchemy import select

from app.deps import AdminUser, DbSession
from app.models import Kind, Punishment, Role, Task, Trombadice, User
from app.periodo import data_local, hoje_local, intervalo, mes_de, semana_de
from app.schemas import Contagem, Report

router = APIRouter(prefix="/api/reports", tags=["reports"])

# Padrão de "os últimos tempos" quando ninguém pede período. Trinta dias mostra
# a tendência do mês sem virar histórico de vida.
JANELA_PADRAO = timedelta(days=29)


def _contagens(rotulos: list[str], ordenar_por_total: bool = False) -> list[Contagem]:
    contador = Counter(rotulos)
    itens = contador.most_common() if ordenar_por_total else sorted(contador.items())
    return [Contagem(rotulo=r, total=t) for r, t in itens]


def _serie_diaria(dias: list[date], por_dia: Counter) -> list[Contagem]:
    """Todo dia do período, inclusive os zerados.

    Os zeros são o ponto: uma lista só com os dias que tiveram trombadice não
    mostra a sequência limpa, que é justamente a notícia boa."""
    return [Contagem(rotulo=d.isoformat(), total=por_dia.get(d, 0)) for d in dias]


def _maior_sequencia_limpa(dias: list[date], por_dia: Counter) -> int:
    maior = atual = 0
    for dia in dias:
        atual = 0 if por_dia.get(dia) else atual + 1
        maior = max(maior, atual)
    return maior


def _dias_de_castigo(castigos: list[Punishment], inicio: datetime, fim: datetime) -> float:
    """Quanto tempo de castigo caiu dentro do período, em dias.

    Conta a parte que se sobrepõe ao período, não o castigo inteiro: um castigo
    de dez dias que começou antes da janela não pode contar dez dias dentro
    dela. E respeita o perdão antecipado - o que vale é o que foi cumprido."""
    total = timedelta()
    for p in castigos:
        termina = min(p.ended_early_at, p.ends_at) if p.ended_early_at else p.ends_at
        de = max(p.starts_at, inicio)
        ate = min(termina, fim)
        if ate > de:
            total += ate - de
    return round(total.total_seconds() / 86400, 1)


@router.get("", response_model=Report)
def report(
    admin: AdminUser,
    db: DbSession,
    child_id: int | None = None,
    de: date | None = None,
    ate: date | None = None,
) -> Report:
    ate = ate or hoje_local()
    de = de or (ate - JANELA_PADRAO)
    if de > ate:
        de, ate = ate, de

    # de/ate nunca são None aqui, então o intervalo vem completo.
    inicio, fim = intervalo(de, ate)

    no_periodo = select(Trombadice).where(
        Trombadice.occurred_at >= inicio, Trombadice.occurred_at < fim
    )
    # Os números do relatório são sobre trombadice. Conquista entra como um
    # contador à parte: misturar as duas na mesma soma daria um total que não
    # responde nem "como foi o comportamento" nem "quanta coisa boa teve".
    query = no_periodo.where(Trombadice.kind == Kind.TROMBADICE)
    conquistas_query = no_periodo.where(Trombadice.kind == Kind.CONQUISTA)
    castigos_query = select(Punishment).where(
        Punishment.ends_at >= inicio, Punishment.starts_at < fim
    )
    if child_id is not None:
        query = query.where(Trombadice.child_id == child_id)
        conquistas_query = conquistas_query.where(Trombadice.child_id == child_id)
        castigos_query = castigos_query.where(Punishment.child_id == child_id)

    trombadices = list(db.scalars(query))
    conquistas = list(db.scalars(conquistas_query))
    castigos = list(db.scalars(castigos_query))

    dias = [de + timedelta(days=n) for n in range((ate - de).days + 1)]
    datas = [data_local(t.occurred_at) for t in trombadices]
    por_dia = Counter(datas)

    nomes = {u.id: u.display_name for u in db.scalars(select(User).where(User.role == Role.CHILD))}
    tarefas = {t.id: t.name for t in db.scalars(select(Task))}

    com_registro = [d for d in dias if por_dia.get(d)]
    mais_pesado = por_dia.most_common(1)

    return Report(
        de=de,
        ate=ate,
        total=len(trombadices),
        por_dia=_serie_diaria(dias, por_dia),
        por_semana=_contagens([semana_de(d) for d in datas]),
        por_mes=_contagens([mes_de(d) for d in datas]),
        por_categoria=_contagens([t.category.value for t in trombadices], ordenar_por_total=True),
        por_filho=_contagens(
            [nomes.get(t.child_id, "?") for t in trombadices], ordenar_por_total=True
        ),
        por_tarefa=_contagens(
            [tarefas[t.task_id] for t in trombadices if t.task_id in tarefas],
            ordenar_por_total=True,
        ),
        dias_com_registro=len(com_registro),
        # Média sobre os dias que tiveram alguma coisa, não sobre o período
        # inteiro: dividir por 30 num mês com duas trombadices dá 0,07 e não
        # diz nada. "Nos dias em que aconteceu, aconteceu 2x" diz.
        media_por_dia_com_registro=(
            round(len(trombadices) / len(com_registro), 1) if com_registro else 0.0
        ),
        dia_mais_pesado=(
            Contagem(rotulo=mais_pesado[0][0].isoformat(), total=mais_pesado[0][1])
            if mais_pesado
            else None
        ),
        castigos_no_periodo=len(castigos),
        dias_de_castigo=_dias_de_castigo(castigos, inicio, fim),
        maior_sequencia_limpa=_maior_sequencia_limpa(dias, por_dia),
        nao_vistas=sum(1 for t in trombadices if t.seen_at is None),
        conquistas=len(conquistas),
        conquistas_por_categoria=_contagens(
            [c.category.value for c in conquistas], ordenar_por_total=True
        ),
    )


