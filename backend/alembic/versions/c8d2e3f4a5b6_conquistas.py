"""conquistas: o registro passa a ter tipo

Revision ID: c8d2e3f4a5b6
Revises: b7c1d2e3f4a5
Create Date: 2026-08-03

Uma coluna nova. Tudo que já está gravado vira `TROMBADICE`, que é a verdade -
conquista não existia quando aquilo foi cadastrado.
"""

import sqlalchemy as sa

from alembic import op

revision = "c8d2e3f4a5b6"
down_revision = "b7c1d2e3f4a5"
branch_labels = None
depends_on = None


def upgrade() -> None:
    with op.batch_alter_table("trombadices") as batch:
        batch.add_column(
            sa.Column(
                "kind",
                sa.String(length=20),
                nullable=False,
                # O NOME do membro, não o valor: `Enum(..., native_enum=False)`
                # guarda o nome. Ver tests/test_migrations.py - foi exatamente
                # isto que quebrou em produção na migration anterior.
                server_default="TROMBADICE",
            )
        )
    op.create_index("ix_trombadices_kind", "trombadices", ["kind"])


def downgrade() -> None:
    op.drop_index("ix_trombadices_kind", table_name="trombadices")
    with op.batch_alter_table("trombadices") as batch:
        batch.drop_column("kind")
