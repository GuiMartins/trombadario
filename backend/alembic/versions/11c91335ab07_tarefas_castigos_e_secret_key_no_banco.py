"""tarefas, castigos e secret key no banco

Revision ID: 11c91335ab07
Revises: a1b2c3d4e5f6
Create Date: 2026-08-01

"""
import secrets
from collections.abc import Sequence

import sqlalchemy as sa

import app.types
from alembic import op

revision: str = "11c91335ab07"
down_revision: str | None = "a1b2c3d4e5f6"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "tasks",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("name", sa.String(length=200), nullable=False),
        sa.Column("description", sa.Text(), nullable=False),
        sa.Column(
            "periodicity",
            sa.Enum("DAILY", "WEEKLY", "MONTHLY", "ONCE", name="periodicity", native_enum=False),
            nullable=False,
        ),
        sa.Column("weekdays", sa.String(length=20), nullable=False),
        sa.Column("day_of_month", sa.Integer(), nullable=True),
        sa.Column("child_id", sa.Integer(), nullable=False),
        sa.Column("author_id", sa.Integer(), nullable=False),
        sa.Column("is_active", sa.Boolean(), nullable=False),
        sa.Column("created_at", app.types.UtcDateTime(timezone=True), nullable=False),
        sa.Column("updated_at", app.types.UtcDateTime(timezone=True), nullable=False),
        sa.ForeignKeyConstraint(["author_id"], ["users.id"], ondelete="RESTRICT"),
        sa.ForeignKeyConstraint(["child_id"], ["users.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
    )
    with op.batch_alter_table("tasks", schema=None) as batch_op:
        batch_op.create_index(batch_op.f("ix_tasks_child_id"), ["child_id"], unique=False)

    op.create_table(
        "punishments",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("reason", sa.Text(), nullable=False),
        sa.Column("starts_at", app.types.UtcDateTime(timezone=True), nullable=False),
        sa.Column("ends_at", app.types.UtcDateTime(timezone=True), nullable=False),
        sa.Column("ended_early_at", app.types.UtcDateTime(timezone=True), nullable=True),
        sa.Column("child_id", sa.Integer(), nullable=False),
        sa.Column("author_id", sa.Integer(), nullable=False),
        sa.Column("created_at", app.types.UtcDateTime(timezone=True), nullable=False),
        sa.ForeignKeyConstraint(["author_id"], ["users.id"], ondelete="RESTRICT"),
        sa.ForeignKeyConstraint(["child_id"], ["users.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
    )
    with op.batch_alter_table("punishments", schema=None) as batch_op:
        batch_op.create_index(batch_op.f("ix_punishments_child_id"), ["child_id"], unique=False)
        batch_op.create_index(batch_op.f("ix_punishments_ends_at"), ["ends_at"], unique=False)
        batch_op.create_index(batch_op.f("ix_punishments_starts_at"), ["starts_at"], unique=False)

    op.create_table(
        "punishment_trombadices",
        sa.Column("punishment_id", sa.Integer(), nullable=False),
        sa.Column("trombadice_id", sa.Integer(), nullable=False),
        sa.ForeignKeyConstraint(["punishment_id"], ["punishments.id"], ondelete="CASCADE"),
        sa.ForeignKeyConstraint(["trombadice_id"], ["trombadices.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("punishment_id", "trombadice_id"),
    )

    # Three steps, not one: the deployed server already has a server_identity
    # row, and adding a NOT NULL column straight away would fail against it.
    # Add nullable -> generate a key for the existing row -> tighten to NOT NULL.
    with op.batch_alter_table("server_identity", schema=None) as batch_op:
        batch_op.add_column(sa.Column("secret_key", sa.String(length=128), nullable=True))

    op.execute(
        sa.text("UPDATE server_identity SET secret_key = :key WHERE secret_key IS NULL").bindparams(
            key=secrets.token_urlsafe(48)
        )
    )

    with op.batch_alter_table("server_identity", schema=None) as batch_op:
        batch_op.alter_column("secret_key", existing_type=sa.String(length=128), nullable=False)

    with op.batch_alter_table("trombadices", schema=None) as batch_op:
        batch_op.add_column(sa.Column("task_id", sa.Integer(), nullable=True))
        batch_op.create_index(batch_op.f("ix_trombadices_task_id"), ["task_id"], unique=False)
        batch_op.create_foreign_key(
            "fk_trombadices_task_id", "tasks", ["task_id"], ["id"], ondelete="SET NULL"
        )


def downgrade() -> None:
    with op.batch_alter_table("trombadices", schema=None) as batch_op:
        batch_op.drop_constraint("fk_trombadices_task_id", type_="foreignkey")
        batch_op.drop_index(batch_op.f("ix_trombadices_task_id"))
        batch_op.drop_column("task_id")

    with op.batch_alter_table("server_identity", schema=None) as batch_op:
        batch_op.drop_column("secret_key")

    op.drop_table("punishment_trombadices")

    with op.batch_alter_table("punishments", schema=None) as batch_op:
        batch_op.drop_index(batch_op.f("ix_punishments_starts_at"))
        batch_op.drop_index(batch_op.f("ix_punishments_ends_at"))
        batch_op.drop_index(batch_op.f("ix_punishments_child_id"))
    op.drop_table("punishments")

    with op.batch_alter_table("tasks", schema=None) as batch_op:
        batch_op.drop_index(batch_op.f("ix_tasks_child_id"))
    op.drop_table("tasks")
