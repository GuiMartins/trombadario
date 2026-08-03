import enum
import secrets
import uuid
from datetime import UTC, datetime

from sqlalchemy import Column, Enum, ForeignKey, Integer, String, Table, Text, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base
from app.types import UtcDateTime


class Role(str, enum.Enum):
    ADMIN = "admin"
    CHILD = "child"


class Category(str, enum.Enum):
    """Que tipo de coisa foi. Lista fechada de propósito: campo livre viraria
    dez jeitos de escrever "falta de respeito" e nenhum relatório sairia.

    Ordem importa - é a ordem em que aparecem na tela, do mais comum ao menos.
    Acrescentar valor é migration; tirar valor exige decidir o que fazer com o
    que já está gravado, então na prática só se aposenta escondendo da tela."""

    DESRESPEITO = "desrespeito"
    EDUCACAO = "educacao"
    NAO_FEZ = "nao_fez"
    MENTIRA = "mentira"
    BIRRA = "birra"
    ESCOLA = "escola"
    AGRESSAO = "agressao"
    OUTRA = "outra"


class Periodicity(str, enum.Enum):
    DAILY = "daily"
    WEEKLY = "weekly"
    MONTHLY = "monthly"
    ONCE = "once"


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
    """What the child did. Distinct from a Task (what they were supposed to do)
    and from a Punishment (what followed)."""

    __tablename__ = "trombadices"

    id: Mapped[int] = mapped_column(primary_key=True)
    title: Mapped[str] = mapped_column(String(200))
    description: Mapped[str] = mapped_column(Text, default="")

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
