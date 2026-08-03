"""reação do filho ao castigo

Revision ID: d4e5f6a7b8c9
Revises: c8d2e3f4a5b6
Create Date: 2026-08-03

Duas colunas novas, as duas nulas - "sem reação ainda" é o estado certo de
todo castigo já gravado. Nenhum enum envolvido, então nada da pegadinha do
`native_enum=False` documentada na migration anterior.
"""

import sqlalchemy as sa

from alembic import op

revision = "d4e5f6a7b8c9"
down_revision = "c8d2e3f4a5b6"
branch_labels = None
depends_on = None


def upgrade() -> None:
    with op.batch_alter_table("punishments") as batch:
        batch.add_column(sa.Column("reaction_text", sa.String(length=256), nullable=True))
        batch.add_column(sa.Column("reaction_at", sa.DateTime(timezone=True), nullable=True))


def downgrade() -> None:
    with op.batch_alter_table("punishments") as batch:
        batch.drop_column("reaction_at")
        batch.drop_column("reaction_text")
