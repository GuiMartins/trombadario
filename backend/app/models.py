import enum
import secrets
import uuid
from datetime import UTC, datetime

from sqlalchemy import Column, Enum, ForeignKey, Integer, String, Table, Text, UniqueConstraint, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base
from app.types import UtcDateTime


class Role(str, enum.Enum):
    ADMIN = "admin"
    CHILD = "child"


class Kind(str, enum.Enum):
    """Trombadice (o que fez de errado) ou conquista (o que fez de bom).

    O registro é o mesmo: quem, quando, o quê, de que tipo. O que muda é o
    sinal. Duas tabelas separadas obrigariam a duplicar filtro, calendário,
    edição e relatório - e a responder duas vezes "o que aconteceu no dia 5"."""

    TROMBADICE = "trombadice"
    CONQUISTA = "conquista"


class Category(str, enum.Enum):
    """Que tipo de coisa foi. Lista fechada de propósito: campo livre viraria
    dez jeitos de escrever "falta de respeito" e nenhum relatório sairia.

    Ordem importa - é a ordem em que aparecem na tela, do mais comum ao menos.
    Acrescentar valor é migration; tirar valor exige decidir o que fazer com o
    que já está gravado, então na prática só se aposenta escondendo da tela.

    **Cada categoria pertence a um tipo** (ver `CATEGORIAS_POR_TIPO`): "falta de
    respeito" não descreve coisa boa, e "ajudou sem pedir" não descreve
    trombadice. Oferecer as dezesseis nas duas telas só produziria registro sem
    sentido."""

    # Trombadices
    DESRESPEITO = "desrespeito"
    EDUCACAO = "educacao"
    NAO_FEZ = "nao_fez"
    MENTIRA = "mentira"
    BIRRA = "birra"
    ESCOLA = "escola"
    AGRESSAO = "agressao"
    OUTRA = "outra"

    # Conquistas
    AJUDOU = "ajudou"
    RESPONSABILIDADE = "responsabilidade"
    ESTUDOU = "estudou"
    GENTILEZA = "gentileza"
    INICIATIVA = "iniciativa"
    SUPEROU = "superou"
    CUIDOU = "cuidou"
    OUTRA_BOA = "outra_boa"


CATEGORIAS_POR_TIPO: dict[Kind, tuple[Category, ...]] = {
    Kind.TROMBADICE: (
        Category.DESRESPEITO,
        Category.EDUCACAO,
        Category.NAO_FEZ,
        Category.MENTIRA,
        Category.BIRRA,
        Category.ESCOLA,
        Category.AGRESSAO,
        Category.OUTRA,
    ),
    Kind.CONQUISTA: (
        Category.AJUDOU,
        Category.RESPONSABILIDADE,
        Category.ESTUDOU,
        Category.GENTILEZA,
        Category.INICIATIVA,
        Category.SUPEROU,
        Category.CUIDOU,
        Category.OUTRA_BOA,
    ),
}


def categoria_padrao(kind: Kind) -> Category:
    return Category.OUTRA if kind is Kind.TROMBADICE else Category.OUTRA_BOA


def categoria_combina(category: Category, kind: Kind) -> bool:
    return category in CATEGORIAS_POR_TIPO[kind]


class Periodicity(str, enum.Enum):
    DAILY = "daily"
    WEEKLY = "weekly"
    MONTHLY = "monthly"
    ONCE = "once"


class RequestStatus(str, enum.Enum):
    PENDENTE = "pendente"
    APROVADO = "aprovado"
    NEGADO = "negado"


class RequestKind(str, enum.Enum):
    """Pedido é o que o filho está pedindo pra fazer; proposta de conquista é
    o filho relatando algo bom que já fez, pedindo que o pai confirme. Mesma
    tabela e mesmo fluxo de decisão pros dois - a diferença real é só o que
    acontece quando o pai aprova (ver Trombadice criada na aprovação de uma
    proposta, em routers/pedidos.py)."""

    PEDIDO = "pedido"
    CONQUISTA_PROPOSTA = "conquista_proposta"


def _utcnow() -> datetime:
    return datetime.now(UTC)


# A punishment can be caused by several trombadices (requirement 5.3, plural),
# and one trombadice can weigh into more than one punishment over time.
punishment_trombadices = Table(
    "punishment_trombadices",
    Base.metadata,
    Column("punishment_id", Integer, ForeignKey("punishments.id", ondelete="CASCADE"),
           primary_key=True),
    Column("trombadice_id", Integer, ForeignKey("trombadices.id", ondelete="CASCADE"),
           primary_key=True),
)


class User(Base):
    __tablename__ = "users"

    id: Mapped[int] = mapped_column(primary_key=True)
    username: Mapped[str] = mapped_column(String(64), unique=True, index=True)
    password_hash: Mapped[str] = mapped_column(String(255))
    display_name: Mapped[str] = mapped_column(String(120))
    role: Mapped[Role] = mapped_column(Enum(Role, native_enum=False), default=Role.CHILD)
    is_active: Mapped[bool] = mapped_column(default=True)
    # Só o pai liga/desliga, por filho - se o filho pode iniciar um pedido (ou,
    # desde a fase seguinte, propor uma conquista). Inline como is_active, não
    # uma tabela de settings à parte: não existe outro flag por filho no
    # schema que justificasse o padrão.
    can_request: Mapped[bool] = mapped_column(default=True)
    # Irmão do `can_request`, com uma diferença que importa: aquele só barra o
    # filho de **criar** um pedido, este tira a funcionalidade inteira da frente
    # dele - não lista, não cria. Foi o pedido literal ("desabilitar acesso"), e
    # meio acesso seria pior que nenhum: ver os assuntos que o pai cadastrou sem
    # poder responder nada só deixaria uma tela morta na mão da criança.
    can_discuss: Mapped[bool] = mapped_column(default=True)
    created_at: Mapped[datetime] = mapped_column(UtcDateTime, default=_utcnow)

    trombadices_about_me: Mapped[list["Trombadice"]] = relationship(
        back_populates="child",
        foreign_keys="Trombadice.child_id",
        cascade="all, delete-orphan",
    )
    tasks: Mapped[list["Task"]] = relationship(
        back_populates="child",
        foreign_keys="Task.child_id",
        cascade="all, delete-orphan",
    )
    punishments: Mapped[list["Punishment"]] = relationship(
        back_populates="child",
        foreign_keys="Punishment.child_id",
        cascade="all, delete-orphan",
    )


class Trombadice(Base):
    """O que a criança fez - de errado **ou** de bom, conforme o `kind`.

    O nome da tabela ficou de quando só havia trombadice. Renomear de novo (já
    houve um `events` → `trombadices`) custaria mais do que explica: são as
    mesmas colunas, os mesmos filtros e o mesmo calendário, com um campo a mais
    dizendo o sinal.

    Continua distinta de Task (o que era pra fazer) e de Punishment (o que veio
    depois)."""

    __tablename__ = "trombadices"

    id: Mapped[int] = mapped_column(primary_key=True)
    title: Mapped[str] = mapped_column(String(200))
    description: Mapped[str] = mapped_column(Text, default="")

    kind: Mapped[Kind] = mapped_column(
        Enum(Kind, native_enum=False), default=Kind.TROMBADICE, index=True
    )

    category: Mapped[Category] = mapped_column(
        Enum(Category, native_enum=False), default=Category.OUTRA, index=True
    )

    # Quando o filho abriu o detalhe desta trombadice. NULL = ainda não viu.
    #
    # É o instante e não um booleano porque "viu" sem "quando" não responde a
    # pergunta que o pai realmente faz, que é se ele viu antes ou depois de
    # alguma coisa. Marcado ao abrir o detalhe, não ao aparecer na lista: rolar
    # o feed não é ler.
    seen_at: Mapped[datetime | None] = mapped_column(UtcDateTime, nullable=True)
    # Separate from created_at on purpose: the parent registers things after the
    # fact, so when it happened is editable and what the feed sorts by.
    occurred_at: Mapped[datetime] = mapped_column(UtcDateTime, index=True)

    child_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    # No cascade: deleting the parent account must never wipe the history.
    author_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="RESTRICT"))

    # Optional link to the task that wasn't done. SET NULL rather than CASCADE:
    # removing a task must not erase the record of what happened because of it.
    task_id: Mapped[int | None] = mapped_column(
        ForeignKey("tasks.id", ondelete="SET NULL"), nullable=True, index=True
    )

    created_at: Mapped[datetime] = mapped_column(UtcDateTime, default=_utcnow)
    updated_at: Mapped[datetime] = mapped_column(UtcDateTime, default=_utcnow, onupdate=_utcnow)

    child: Mapped[User] = relationship(
        back_populates="trombadices_about_me", foreign_keys=[child_id]
    )
    author: Mapped[User] = relationship(foreign_keys=[author_id])
    task: Mapped["Task | None"] = relationship(back_populates="trombadices")


class Task(Base):
    """What the child is supposed to do, and how often."""

    __tablename__ = "tasks"

    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(String(200))
    description: Mapped[str] = mapped_column(Text, default="")

    periodicity: Mapped[Periodicity] = mapped_column(
        Enum(Periodicity, native_enum=False), default=Periodicity.DAILY
    )
    # Only meaningful for WEEKLY: comma-separated Python weekday numbers
    # (0 = Monday). Stored as text because SQLite has no array type and a join
    # table for at most seven small integers would cost more than it explains.
    weekdays: Mapped[str] = mapped_column(String(20), default="")
    # Only meaningful for MONTHLY.
    day_of_month: Mapped[int | None] = mapped_column(nullable=True)

    child_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    author_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="RESTRICT"))

    is_active: Mapped[bool] = mapped_column(default=True)
    created_at: Mapped[datetime] = mapped_column(UtcDateTime, default=_utcnow)
    updated_at: Mapped[datetime] = mapped_column(UtcDateTime, default=_utcnow, onupdate=_utcnow)

    child: Mapped[User] = relationship(back_populates="tasks", foreign_keys=[child_id])
    author: Mapped[User] = relationship(foreign_keys=[author_id])
    trombadices: Mapped[list[Trombadice]] = relationship(back_populates="task")
    completions: Mapped[list["TaskCompletion"]] = relationship(
        back_populates="task", cascade="all, delete-orphan"
    )


class TaskCompletion(Base):
    """O filho marcando que fez a tarefa, uma vez por período.

    `ondelete="CASCADE"` no `task_id`, e não `SET NULL` como
    `Trombadice.task_id` - lá o `SET NULL` existe porque apagar a tarefa não
    pode apagar o registro do que aconteceu por causa dela. Aqui é o
    contrário: uma conclusão não tem sentido nenhum sem a tarefa que ela
    completa, então apagar a tarefa apaga as conclusões junto.
    """

    __tablename__ = "task_completions"

    id: Mapped[int] = mapped_column(primary_key=True)
    task_id: Mapped[int] = mapped_column(ForeignKey("tasks.id", ondelete="CASCADE"), index=True)
    child_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"))
    note: Mapped[str] = mapped_column(Text, default="")
    # A chave do período que esta conclusão satisfaz (ver app/periodo.py). É o
    # que a UniqueConstraint trava - "uma vez por período" é regra do banco,
    # não só da rota.
    period_key: Mapped[str] = mapped_column(String(10), index=True)
    completed_at: Mapped[datetime] = mapped_column(UtcDateTime, default=_utcnow)

    task: Mapped[Task] = relationship(back_populates="completions")
    child: Mapped[User] = relationship(foreign_keys=[child_id])

    __table_args__ = (UniqueConstraint("task_id", "period_key", name="uq_task_completion_period"),)


class Punishment(Base):
    __tablename__ = "punishments"

    id: Mapped[int] = mapped_column(primary_key=True)
    reason: Mapped[str] = mapped_column(Text, default="")

    starts_at: Mapped[datetime] = mapped_column(UtcDateTime, default=_utcnow, index=True)
    ends_at: Mapped[datetime] = mapped_column(UtcDateTime, index=True)
    # Set when the parent lets them off early; keeps the original ends_at intact
    # so the history still shows what was handed down versus what was served.
    ended_early_at: Mapped[datetime | None] = mapped_column(UtcDateTime, nullable=True)

    # Quando o filho abriu a tela de castigo e viu este castigo. Mesma ideia do
    # seen_at da trombadice.
    seen_at: Mapped[datetime | None] = mapped_column(UtcDateTime, nullable=True)

    # A primeira coluna gravável pelo filho no schema inteiro - texto livre (o
    # teclado já tem seletor de emoji) ou um dos emojis prontos da tela. O pai
    # nunca escreve aqui, só lê.
    reaction_text: Mapped[str | None] = mapped_column(String(256), nullable=True)
    reaction_at: Mapped[datetime | None] = mapped_column(UtcDateTime, nullable=True)

    child_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    author_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="RESTRICT"))

    created_at: Mapped[datetime] = mapped_column(UtcDateTime, default=_utcnow)

    child: Mapped[User] = relationship(back_populates="punishments", foreign_keys=[child_id])
    author: Mapped[User] = relationship(foreign_keys=[author_id])
    trombadices: Mapped[list[Trombadice]] = relationship(secondary=punishment_trombadices)

    def is_active_at(self, moment: datetime) -> bool:
        """Computed, never stored: a boolean column would need something running
        to flip it, and would be wrong in between runs."""
        if self.ended_early_at is not None and self.ended_early_at <= moment:
            return False
        return self.starts_at <= moment < self.ends_at


class ServerIdentity(Base):
    """Single row. The UUID is what the app pairs with on first setup, so that
    "the server answered" can't be satisfied by some unrelated service that
    happens to sit on the same host:port of a foreign network.

    Also holds the JWT signing key, generated on first boot. It lives here
    rather than in the environment so the app can be installed from the CasaOS
    store and just work - nobody is going to write a .env over SSH."""

    __tablename__ = "server_identity"

    id: Mapped[int] = mapped_column(primary_key=True)
    server_id: Mapped[str] = mapped_column(String(36), default=lambda: str(uuid.uuid4()))
    secret_key: Mapped[str] = mapped_column(
        String(128), default=lambda: secrets.token_urlsafe(48)
    )
    created_at: Mapped[datetime] = mapped_column(UtcDateTime, server_default=func.now())


class SplashMessage(Base):
    """A phrase the parent writes, shown to the child as the loading screen when
    they open the app."""

    __tablename__ = "splash_messages"

    id: Mapped[int] = mapped_column(primary_key=True)
    text: Mapped[str] = mapped_column(String(300))

    # NULL means "for every child". Targeting one child is per-phrase, so the
    # same list holds "bom dia" and "arruma essa cama, João".
    child_id: Mapped[int | None] = mapped_column(
        ForeignKey("users.id", ondelete="CASCADE"), nullable=True, index=True
    )
    author_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="RESTRICT"))

    is_active: Mapped[bool] = mapped_column(default=True)
    created_at: Mapped[datetime] = mapped_column(UtcDateTime, default=_utcnow)

    child: Mapped["User | None"] = relationship(foreign_keys=[child_id])
    author: Mapped[User] = relationship(foreign_keys=[author_id])


class Pedido(Base):
    """O filho pede, o pai aprova ou nega - com justificativa dos dois lados.

    Duas colunas de "visto" em vez de reaproveitar `app/visto.py` como está:
    aquele helper só cobre "o filho vê o que o pai cadastrou". Pedido precisa
    das duas direções - o pai precisa saber que chegou um pedido novo, o
    filho precisa saber que saiu uma decisão - e são fatos genuinamente
    diferentes, não um único `seen_at` bidirecional.
    """

    __tablename__ = "pedidos"

    id: Mapped[int] = mapped_column(primary_key=True)
    kind: Mapped[RequestKind] = mapped_column(
        Enum(RequestKind, native_enum=False), default=RequestKind.PEDIDO, index=True
    )
    title: Mapped[str] = mapped_column(String(200))
    justification: Mapped[str] = mapped_column(Text, default="")
    # Só relevante pra CONQUISTA_PROPOSTA - mesmo padrão de campo-só-de-um-caso
    # já usado em Task.weekdays/day_of_month.
    category: Mapped[Category | None] = mapped_column(
        Enum(Category, native_enum=False), nullable=True
    )
    status: Mapped[RequestStatus] = mapped_column(
        Enum(RequestStatus, native_enum=False), default=RequestStatus.PENDENTE, index=True
    )
    # Escrito pelo pai, só quando decide. Vazio enquanto pendente.
    decision_note: Mapped[str] = mapped_column(Text, default="")
    decided_at: Mapped[datetime | None] = mapped_column(UtcDateTime, nullable=True)
    decided_by_id: Mapped[int | None] = mapped_column(
        ForeignKey("users.id", ondelete="RESTRICT"), nullable=True
    )

    child_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    created_at: Mapped[datetime] = mapped_column(UtcDateTime, default=_utcnow)

    seen_by_parent_at: Mapped[datetime | None] = mapped_column(UtcDateTime, nullable=True)
    seen_by_child_at: Mapped[datetime | None] = mapped_column(UtcDateTime, nullable=True)

    child: Mapped[User] = relationship(foreign_keys=[child_id])
    decided_by: Mapped["User | None"] = relationship(foreign_keys=[decided_by_id])


class Assunto(Base):
    """Um assunto que alguém quer conversar pessoalmente, cara a cara.

    A única coisa do app que os dois lados escrevem em pé de igualdade: pai e
    filho cadastram, com os mesmos campos e na mesma lista. Trombadice, tarefa
    e castigo são o pai falando; pedido e proposta são o filho pedindo. Aqui
    ninguém está pedindo licença pro outro - é uma pauta.

    **Não guarda a conversa, só a pauta.** O texto é o assunto a ser tratado,
    não o que foi dito - a conversa acontece na sala, que é o ponto da
    funcionalidade. Por isso não existe thread de resposta: um chat aqui viraria
    o substituto da conversa em vez do lembrete dela.

    "Já conversamos" é `talked_at`, nulo enquanto pendente - mesmo espírito de
    `Punishment.is_active_at`: nada de coluna de status que precisaria de algo
    rodando pra ficar certa, e o instante responde "quando", que um booleano não
    responde.
    """

    __tablename__ = "assuntos"

    id: Mapped[int] = mapped_column(primary_key=True)
    title: Mapped[str] = mapped_column(String(200))
    description: Mapped[str] = mapped_column(Text, default="")

    # De quem é a conversa. Quando o filho cadastra, é ele mesmo; quando o pai
    # cadastra, ele escolhe. Comparar com `author_id` é o que diz quem trouxe o
    # assunto - não existe coluna repetindo isso.
    child_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    author_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="RESTRICT"))

    # Nulo = ainda não conversaram. Quem marca é só o pai: o filho riscar da
    # lista um assunto que o pai levantou seria apagar a pauta do outro.
    talked_at: Mapped[datetime | None] = mapped_column(UtcDateTime, nullable=True)
    talked_by_id: Mapped[int | None] = mapped_column(
        ForeignKey("users.id", ondelete="RESTRICT"), nullable=True
    )
    # O que ficou decidido, em uma linha. Opcional - conversa boa nem sempre
    # tem conclusão pra anotar.
    talked_note: Mapped[str] = mapped_column(Text, default="")

    created_at: Mapped[datetime] = mapped_column(UtcDateTime, default=_utcnow)

    # Dois carimbos como em `Pedido`, mas com regra própria: **quem vê é sempre
    # quem não escreveu**. O assunto que o filho trouxe é o pai que precisa ver
    # chegar; o que o pai trouxe é o filho. Um `seen_at` só não distinguiria os
    # dois, e aqui os dois acontecem na mesma tabela.
    seen_by_parent_at: Mapped[datetime | None] = mapped_column(UtcDateTime, nullable=True)
    seen_by_child_at: Mapped[datetime | None] = mapped_column(UtcDateTime, nullable=True)

    child: Mapped[User] = relationship(foreign_keys=[child_id])
    author: Mapped[User] = relationship(foreign_keys=[author_id])
    talked_by: Mapped["User | None"] = relationship(foreign_keys=[talked_by_id])

    @property
    def by_child(self) -> bool:
        """Quem trouxe o assunto. Derivado do papel do autor, não de uma coluna
        própria: duas fontes para o mesmo fato dariam duas respostas no dia em
        que uma delas fosse escrita errado."""
        return self.author.role is Role.CHILD
