"""categoria da trombadice e marca de visto pelo filho

Revision ID: b7c1d2e3f4a5
Revises: ac8900eb456b
Create Date: 2026-08-02

Nada destrutivo: três colunas novas. A categoria entra como "outra" no que já
está gravado, que é a verdade - ninguém escolheu categoria nenhuma naquela
época. O `seen_at` entra nulo, ou seja "ainda não viu", que também é o certo:
não dá para inventar que o filho leu algo antes de existir a marcação.
"""

import sqlalchemy as sa

from alembic import op

revision = "b7c1d2e3f4a5"
down_revision = "ac8900eb456b"
branch_labels = None
depends_on = None


def upgrade() -> None:
    # batch_alter_table porque o SQLite não faz ALTER no lugar - o Alembic
    # recria a tabela e copia os dados.
    with op.batch_alter_table("trombadices") as batch:
        batch.add_column(
            sa.Column(
                "category",
                sa.String(length=20),
                nullable=False,
                server_default="outra",
            )
        )
        batch.add_column(sa.Column("seen_at", sa.DateTime(timezone=True), nullable=True))
    op.create_index("ix_trombadices_category", "trombadices", ["category"])

    with op.batch_alter_table("punishments") as batch:
        batch.add_column(sa.Column("seen_at", sa.DateTime(timezone=True), nullable=True))


def downgrade() -> None:
    with op.batch_alter_table("punishments") as batch:
        batch.drop_column("seen_at")

    op.drop_index("ix_trombadices_category", table_name="trombadices")
    with op.batch_alter_table("trombadices") as batch:
        batch.drop_column("seen_at")
        batch.drop_column("category")
