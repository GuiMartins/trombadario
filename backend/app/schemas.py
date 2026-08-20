from datetime import date, datetime
from typing import Literal

from pydantic import AwareDatetime, BaseModel, ConfigDict, Field, field_validator, model_validator

from app.models import (
    Category,
    Kind,
    Periodicity,
    RequestKind,
    RequestStatus,
    Role,
    categoria_combina,
    categoria_padrao,
)


class Token(BaseModel):
    access_token: str
    token_type: str = "bearer"


class UserOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    username: str
    display_name: str
    role: Role
    is_active: bool
    # Só fazem sentido pro filho, mas mandados sempre - o pai lê os próprios
    # valores como "true" e nunca usa.
    can_request: bool
    can_discuss: bool
    # Nulo enquanto o pai não cadastrar. Só a do filho tem efeito - é o app
    # dele que vira tela de aniversário no dia (ver routers/birthday.py).
    birth_date: date | None
    created_at: datetime


class UserCreate(BaseModel):
    username: str = Field(min_length=3, max_length=64)
    password: str = Field(min_length=6, max_length=128)
    display_name: str = Field(min_length=1, max_length=120)
    role: Role = Role.CHILD
    birth_date: date | None = None


class UserUpdate(BaseModel):
    display_name: str | None = Field(default=None, min_length=1, max_length=120)
    password: str | None = Field(default=None, min_length=6, max_length=128)
    is_active: bool | None = None
    can_request: bool | None = None
    can_discuss: bool | None = None
    # Aqui nulo **apaga** a data, ao contrário de `password`, onde nulo é
    # "mantém a que está". A rota usa `exclude_unset`, então quem não quer
    # mexer no aniversário simplesmente não manda o campo - mandar nulo é uma
    # escolha, e a única forma de desfazer um cadastro errado.
    birth_date: date | None = None


class BirthdayOut(BaseModel):
    """Se hoje é o aniversário de quem está perguntando.

    Quem responde é o servidor, não o celular: o dia local é conta dele
    (`app/periodo.py`), do mesmo jeito que a chave do período de uma tarefa e o
    sorteio da frase de abertura. Um app que decidisse isso pelo próprio
    relógio faria aniversário todo dia com dois toques em Data e hora."""

    is_birthday: bool
    display_name: str
    birth_date: date | None
    # Quantos anos faz hoje. Nulo fora do aniversário - a idade nos outros dias
    # não é pergunta que este endpoint responde.
    age: int | None = None


class SetupRequest(BaseModel):
    """First-run wizard: creates the one account that owns the app."""

    username: str = Field(min_length=3, max_length=64)
    password: str = Field(min_length=6, max_length=128)
    display_name: str = Field(min_length=1, max_length=120)


class TrombadiceOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    title: str
    description: str
    kind: Kind
    category: Category
    occurred_at: datetime
    child_id: int
    author_id: int
    task_id: int | None
    seen_at: datetime | None
    created_at: datetime
    updated_at: datetime


class TrombadiceCreate(BaseModel):
    # Vazio é aceito quando vem `task_id`: aí o título é o nome da tarefa que
    # não foi cumprida, e obrigar a repetir isso na mão só produz divergência.
    title: str = Field(default="", max_length=200)
    description: str = ""
    kind: Kind = Kind.TROMBADICE
    # Nulo = usa a padrão do tipo. Sem isso, quem manda uma conquista sem
    # categoria receberia "Outra" de trombadice, que é de outra lista.
    category: Category | None = None
    # AwareDatetime, not datetime: a naive value would be ambiguous and the
    # storage layer rejects it anyway (see app/types.py). Better a 422 than a
    # 500, and better an explicit offset than a silent 3-hour shift.
    occurred_at: AwareDatetime
    child_id: int
    task_id: int | None = None

    @model_validator(mode="after")
    def coerente(self) -> "TrombadiceCreate":
        if not self.title.strip() and self.task_id is None:
            raise ValueError("sem tarefa atrelada, o título é obrigatório")
        if self.kind is Kind.CONQUISTA and self.task_id is not None:
            # Tarefa existe para registrar o que **não** foi cumprido. Conquista
            # atrelada a tarefa diria o contrário do que o vínculo significa.
            raise ValueError("conquista não se atrela a tarefa")
        if self.category is None:
            self.category = categoria_padrao(self.kind)
        elif not categoria_combina(self.category, self.kind):
            raise ValueError("essa categoria não é desse tipo de registro")
        return self


class TrombadiceUpdate(BaseModel):
    title: str | None = Field(default=None, min_length=1, max_length=200)
    description: str | None = None
    # O tipo não se edita: trombadice não vira conquista nem o contrário. Errou
    # o tipo, apaga e cadastra de novo - é mais honesto que reescrever o
    # significado de um registro que a criança já pode ter visto.
    category: Category | None = None
    occurred_at: AwareDatetime | None = None
    task_id: int | None = None


class TaskCompletionOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    task_id: int
    child_id: int
    note: str
    # O período que esta conclusão satisfaz (ver app/periodo.py). Vai junto
    # porque sem ele "feito em 03/08" não responde se o período de agora está
    # cumprido - quem sabe traduzir data em período é o servidor.
    period_key: str
    completed_at: datetime


class TaskCompletionCreate(BaseModel):
    note: str = Field(default="", max_length=500)


class TaskOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    name: str
    description: str
    periodicity: Periodicity
    weekdays: list[int]
    day_of_month: int | None
    child_id: int
    author_id: int
    is_active: bool
    created_at: datetime

    # Os três campos abaixo são calculados pelo servidor, como o `is_active` do
    # castigo: o relógio e o calendário do celular não decidem se hoje é dia da
    # tarefa nem se o período de agora já foi cumprido.
    #
    # Vêm junto da tarefa de propósito. Enquanto não vinham, a tela só sabia
    # dizer "feito" depois de um GET por tarefa (N+1 a cada ON_RESUME), e ainda
    # assim não sabia a qual período cada conclusão pertencia.
    due_today: bool = False
    current_completion: TaskCompletionOut | None = None
    recent_completions: list[TaskCompletionOut] = Field(default_factory=list)

    @field_validator("weekdays", mode="before")
    @classmethod
    def split_weekdays(cls, value: object) -> object:
        # Stored as "0,2,4" (see models.Task); the API speaks a real list.
        if isinstance(value, str):
            return [int(day) for day in value.split(",") if day.strip()]
        return value


class TaskCreate(BaseModel):
    name: str = Field(min_length=1, max_length=200)
    description: str = ""
    periodicity: Periodicity = Periodicity.DAILY
    weekdays: list[int] = Field(default_factory=list)
    day_of_month: int | None = Field(default=None, ge=1, le=31)
    child_id: int

    @field_validator("weekdays")
    @classmethod
    def check_weekdays(cls, value: list[int]) -> list[int]:
        if any(day < 0 or day > 6 for day in value):
            raise ValueError("dia da semana precisa estar entre 0 (segunda) e 6 (domingo)")
        return sorted(set(value))


class TaskUpdate(BaseModel):
    name: str | None = Field(default=None, min_length=1, max_length=200)
    description: str | None = None
    periodicity: Periodicity | None = None
    weekdays: list[int] | None = None
    day_of_month: int | None = Field(default=None, ge=1, le=31)
    is_active: bool | None = None


class PunishmentOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    reason: str
    starts_at: datetime
    ends_at: datetime
    ended_early_at: datetime | None
    seen_at: datetime | None
    # O filho escreve, os dois papéis leem - mesmo padrão de seen_at, mas na
    # direção contrária: o pai nunca grava aqui.
    reaction_text: str | None
    reaction_at: datetime | None
    child_id: int
    author_id: int
    created_at: datetime
    trombadice_ids: list[int] = Field(default_factory=list)
    # As trombadices completas, não só o id: sem isto o filho precisaria de uma
    # segunda busca (e de uma segunda tela de carregamento) só pra saber o
    # título de cada uma - o pai já lê isso de graça porque busca a lista
    # inteira de qualquer forma.
    trombadices: list[TrombadiceOut] = Field(default_factory=list)
    is_active: bool = False


class PunishmentCreate(BaseModel):
    reason: str = ""
    starts_at: AwareDatetime | None = None
    ends_at: AwareDatetime
    child_id: int
    # Sem default: castigo é consequência de alguma coisa, e essa coisa já está
    # registrada. Quem recusa a lista vazia é a rota (400, com a mensagem que a
    # tela mostra) - aqui só não existe mais o caminho de omitir o campo.
    trombadice_ids: list[int]


class PunishmentUpdate(BaseModel):
    reason: str | None = None
    ends_at: AwareDatetime | None = None
    trombadice_ids: list[int] | None = None
    # True ends it now; the original ends_at is kept so the history shows what
    # was handed down as well as what was actually served.
    end_now: bool | None = None


class PunishmentReaction(BaseModel):
    """Nulo/vazio apaga a reação - o filho pode mudar de ideia."""

    reaction_text: str | None = Field(default=None, max_length=256)


class SplashMessageRandom(BaseModel):
    """Resposta do sorteio. `text` nulo quando o pai não cadastrou nenhuma frase
    que se aplique - o app pula a tela e vai direto pro conteúdo."""

    text: str | None = None


class Contagem(BaseModel):
    """Um rótulo e quantas vezes. Serve para dia, semana, mês, categoria,
    filho e tarefa - todos são a mesma pergunta com um agrupamento diferente."""

    rotulo: str
    total: int


class Report(BaseModel):
    """O que dá para dizer olhando os dados, sem inventar.

    Todas as datas são **locais** (fuso do servidor), não UTC: o pai pergunta
    "quantas ontem", e ontem é o dia dele, não o do meridiano de Greenwich."""

    de: date
    ate: date
    total: int

    por_dia: list[Contagem]
    por_semana: list[Contagem]
    por_mes: list[Contagem]
    por_categoria: list[Contagem]
    por_filho: list[Contagem]
    # As tarefas mais descumpridas. Vazio quando nenhuma trombadice foi atrelada
    # a tarefa nenhuma - o que também é uma informação.
    por_tarefa: list[Contagem]

    dias_com_registro: int
    media_por_dia_com_registro: float
    dia_mais_pesado: Contagem | None

    castigos_no_periodo: int
    dias_de_castigo: float
    maior_sequencia_limpa: int
    nao_vistas: int

    # Contadas à parte de propósito: somar coisa boa com coisa ruim daria um
    # número que não responde nenhuma das duas perguntas.
    conquistas: int = 0
    conquistas_por_categoria: list[Contagem] = Field(default_factory=list)


class DatasComRegistro(BaseModel):
    """Os dias que têm alguma coisa, para o calendário só deixar clicar neles."""

    datas: list[date]


class Health(BaseModel):
    app: str
    server_id: str
    setup_required: bool


class UnseenCounts(BaseModel):
    """O que ainda não foi visto, sem marcar nada como visto - ver
    `routers/unseen.py`. Os cinco campos sempre vêm juntos; os que não valem
    pro papel de quem pergunta ficam em 0, em vez de uma união discriminada.

    Trombadice e conquista vêm **separadas** mesmo sendo a mesma tabela: o app
    toca um som diferente pra cada uma, e o som no Android é propriedade do
    canal de notificação, então cada uma precisa da própria contagem pra saber
    em qual canal avisar."""

    pedidos_pendentes: int = 0
    trombadices_novas: int = 0
    conquistas_novas: int = 0
    castigos_novos: int = 0
    decisoes_novas: int = 0
    # O único campo que vale pros dois papéis, porque assunto é a única coisa
    # que os dois cadastram. Em cada um ele conta a mesma pergunta a partir do
    # outro lado: "o que o outro trouxe e eu ainda não vi". Um campo por papel
    # não daria informação nova nenhuma - e são notificações do mesmo canal, com
    # o mesmo som, porque é o mesmo fato.
    assuntos_novos: int = 0


class SplashMessageOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    text: str
    child_id: int | None
    is_active: bool
    created_at: datetime


class SplashMessageCreate(BaseModel):
    text: str = Field(min_length=1, max_length=300)
    # None = vale pra todos os filhos.
    child_id: int | None = None


class SplashMessageUpdate(BaseModel):
    text: str | None = Field(default=None, min_length=1, max_length=300)
    child_id: int | None = None
    is_active: bool | None = None


class PedidoOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    kind: RequestKind
    title: str
    justification: str
    category: Category | None
    status: RequestStatus
    decision_note: str
    decided_at: datetime | None
    decided_by_id: int | None
    child_id: int
    created_at: datetime
    seen_by_parent_at: datetime | None
    seen_by_child_at: datetime | None


class PedidoCreate(BaseModel):
    title: str = Field(min_length=1, max_length=200)
    justification: str = Field(default="", max_length=1000)


class PropostaConquistaCreate(BaseModel):
    title: str = Field(min_length=1, max_length=200)
    justification: str = Field(default="", max_length=1000)
    category: Category

    @field_validator("category")
    @classmethod
    def categoria_de_conquista(cls, category: Category) -> Category:
        # Mesma regra de TrombadiceCreate.coerente: uma proposta de conquista
        # promove pra Kind.CONQUISTA na aprovação, então a categoria já
        # precisa ser desse tipo - senão a Trombadice nasceria com uma
        # combinação que o resto do sistema nunca deixa acontecer.
        if not categoria_combina(category, Kind.CONQUISTA):
            raise ValueError("essa categoria não é de conquista")
        return category


class PedidoDecision(BaseModel):
    # Nunca PENDENTE aqui - decidir é sair de pendente, não voltar pra ele.
    status: Literal[RequestStatus.APROVADO, RequestStatus.NEGADO]
    decision_note: str = Field(default="", max_length=1000)


class AssuntoOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    title: str
    description: str
    child_id: int
    author_id: int
    talked_at: datetime | None
    talked_by_id: int | None
    talked_note: str
    created_at: datetime
    seen_by_parent_at: datetime | None
    seen_by_child_at: datetime | None
    # Quem trouxe o assunto, resolvido no servidor. O app poderia comparar
    # `author_id` com o id de quem está logado, mas só acertaria pro próprio
    # lado: o filho não conhece o id do pai, e nem toda tela tem a lista de
    # contas na mão pra descobrir.
    by_child: bool = False


class AssuntoCreate(BaseModel):
    title: str = Field(min_length=1, max_length=200)
    description: str = Field(default="", max_length=2000)
    # Só o pai escolhe de quem é o assunto. Vindo do filho o campo é ignorado,
    # nunca respeitado - mesmo padrão do `child_id` nas listagens dele.
    child_id: int | None = None


class AssuntoUpdate(BaseModel):
    title: str | None = Field(default=None, min_length=1, max_length=200)
    description: str | None = Field(default=None, max_length=2000)


class AssuntoConversa(BaseModel):
    """Marcar (ou desmarcar) que o assunto já foi conversado.

    `talked=False` existe pelo mesmo motivo que desmarcar tarefa existe: um
    toque errado não pode encerrar pra sempre um assunto que ninguém conversou,
    e apagar e recadastrar levaria junto quem trouxe e quando."""

    talked: bool = True
    note: str = Field(default="", max_length=2000)
