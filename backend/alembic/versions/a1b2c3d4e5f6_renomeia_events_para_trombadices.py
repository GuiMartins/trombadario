"""renomeia events para trombadices

Revision ID: a1b2c3d4e5f6
Revises: db747f86f888
Create Date: 2026-08-01

Rename only - no data is created, dropped or rewritten. `op.rename_table` keeps
the rows and the primary keys exactly as they are; the indexes are renamed to
match so the next autogenerate doesn't think they need recreating.
"""
from collections.abc import Sequence

from alembic import op

revision: str = "a1b2c3d4e5f6"
down_revision: str | None = "db747f86f888"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.rename_table("events", "trombadices")
    # SQLite carries the old index names along with the renamed table.
    with op.batch_alter_table("trombadices", schema=None) as batch_op:
        batch_op.drop_index("ix_events_child_id")
        batch_op.drop_index("ix_events_occurred_at")
        batch_op.create_index("ix_trombadices_child_id", ["child_id"], unique=False)
        batch_op.create_index("ix_trombadices_occurred_at", ["occurred_at"], unique=False)


def downgrade() -> None:
    with op.batch_alter_table("trombadices", schema=None) as batch_op:
        batch_op.drop_index("ix_trombadices_occurred_at")
        batch_op.drop_index("ix_trombadices_child_id")
        batch_op.create_index("ix_events_child_id", ["child_id"], unique=False)
        batch_op.create_index("ix_events_occurred_at", ["occurred_at"], unique=False)

    op.rename_table("trombadices", "events")
