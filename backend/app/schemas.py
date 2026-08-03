from datetime import date, datetime
from typing import Literal

from pydantic import AwareDatetime, BaseModel, ConfigDict, Field, field_validator, model_validator

from app.models import (
    Category,
    Kind,
    Periodicity,
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
    # Só faz sentido pro filho, mas mandado sempre - o pai lê o próprio valor
    # como "true" e nunca usa.
    can_request: bool
    created_at: datetime


class UserCreate(BaseModel):
    username: str = Field(min_length=3, max_length=64)
    password: str = Field(min_length=6, max_length=128)
    display_name: str = Field(min_length=1, max_length=120)
    role: Role = Role.CHILD


class UserUpdate(BaseModel):
    display_name: str | None = Field(default=None, min_length=1, max_length=120)
    password: str | None = Field(default=None, min_length=6, max_length=128)
    is_active: bool | None = None
    can_request: bool | None = None


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


class TaskCompletionOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    task_id: int
    child_id: int
    note: str
    completed_at: datetime


class TaskCompletionCreate(BaseModel):
    note: str = Field(default="", max_length=500)


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
    trombadice_ids: list[int] = Field(default_factory=list)


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
    title: str
    justification: str
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


class PedidoDecision(BaseModel):
    # Nunca PENDENTE aqui - decidir é sair de pendente, não voltar pra ele.
    status: Literal[RequestStatus.APROVADO, RequestStatus.NEGADO]
    decision_note: str = Field(default="", max_length=1000)
