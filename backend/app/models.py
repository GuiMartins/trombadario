import enum
import uuid
from datetime import UTC, datetime

from sqlalchemy import Enum, ForeignKey, String, Text, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base
from app.types import UtcDateTime


class Role(str, enum.Enum):
    ADMIN = "admin"
    CHILD = "child"


def _utcnow() -> datetime:
    return datetime.now(UTC)


class User(Base):
    __tablename__ = "users"

    id: Mapped[int] = mapped_column(primary_key=True)
    username: Mapped[str] = mapped_column(String(64), unique=True, index=True)
    password_hash: Mapped[str] = mapped_column(String(255))
    display_name: Mapped[str] = mapped_column(String(120))
    role: Mapped[Role] = mapped_column(Enum(Role, native_enum=False), default=Role.CHILD)
    is_active: Mapped[bool] = mapped_column(default=True)
    created_at: Mapped[datetime] = mapped_column(UtcDateTime, default=_utcnow)

    events_about_me: Mapped[list["Event"]] = relationship(
        back_populates="child",
        foreign_keys="Event.child_id",
        cascade="all, delete-orphan",
    )


class Event(Base):
    __tablename__ = "events"

    id: Mapped[int] = mapped_column(primary_key=True)
    title: Mapped[str] = mapped_column(String(200))
    description: Mapped[str] = mapped_column(Text, default="")
    # Separate from created_at on purpose: the parent registers things after the
    # fact, so when it happened is editable and what the feed sorts by.
    occurred_at: Mapped[datetime] = mapped_column(UtcDateTime, index=True)

    child_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    # No cascade: deleting the parent account must never wipe the history.
    author_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="RESTRICT"))

    created_at: Mapped[datetime] = mapped_column(UtcDateTime, default=_utcnow)
    updated_at: Mapped[datetime] = mapped_column(
        UtcDateTime, default=_utcnow, onupdate=_utcnow
    )

    child: Mapped[User] = relationship(back_populates="events_about_me", foreign_keys=[child_id])
    author: Mapped[User] = relationship(foreign_keys=[author_id])


class ServerIdentity(Base):
    """Single row. Its UUID is what the app pairs with on first setup, so that
    "the server answered" can't be satisfied by some unrelated service that
    happens to sit on the same host:port of a foreign network."""

    __tablename__ = "server_identity"

    id: Mapped[int] = mapped_column(primary_key=True)
    server_id: Mapped[str] = mapped_column(String(36), default=lambda: str(uuid.uuid4()))
    created_at: Mapped[datetime] = mapped_column(UtcDateTime, server_default=func.now())
