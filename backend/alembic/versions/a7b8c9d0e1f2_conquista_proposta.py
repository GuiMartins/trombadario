"""conquista proposta pelo filho

Revision ID: a7b8c9d0e1f2
Revises: f6a7b8c9d0e1
Create Date: 2026-08-03

Duas colunas novas em `pedidos`, reaproveitando a tabela em vez de duplicar
todo o fluxo de decisão pra um segundo conceito quase igual. `kind` é enum
`native_enum=False` - guarda o NOME do membro ("PEDIDO"), não o valor
("pedido"). `category` reaproveita o enum `Category` que já existe pra
trombadice/conquista - mesma pegadinha se escrita errada, mesmo cuidado.

Linhas já existentes são pedidos comuns, então `kind` entra com
server_default="PEDIDO" - não teria sentido nenhum reclassificá-las como
proposta de conquista.
"""

import sqlalchemy as sa

from alembic import op

revision = "a7b8c9d0e1f2"
down_revision = "f6a7b8c9d0e1"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column(
        "pedidos",
        sa.Column("kind", sa.String(length=20), nullable=False, server_default="PEDIDO"),
    )
    op.add_column("pedidos", sa.Column("category", sa.String(length=20), nullable=True))
    op.create_index("ix_pedidos_kind", "pedidos", ["kind"])


def downgrade() -> None:
    op.drop_index("ix_pedidos_kind", table_name="pedidos")
    op.drop_column("pedidos", "category")
    op.drop_column("pedidos", "kind")
