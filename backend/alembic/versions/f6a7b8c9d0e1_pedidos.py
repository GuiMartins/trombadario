"""pedidos: filho pede, pai decide

Revision ID: f6a7b8c9d0e1
Revises: e5f6a7b8c9d0
Create Date: 2026-08-03

`can_request` precisa de server_default: linhas de conta já existentes têm
que continuar podendo pedir, não ficar travadas por uma migration silenciosa.

`status` é enum `native_enum=False` - guarda o NOME do membro, não o valor
("PENDENTE", não "pendente"). Mesma pegadinha documentada na migration de
`category`; test_migrations.py ganha um caso pra esta também.
"""

import sqlalchemy as sa

from alembic import op

revision = "f6a7b8c9d0e1"
down_revision = "e5f6a7b8c9d0"
branch_labels = None
depends_on = None


def upgrade() -> None:
    # Sem batch_alter_table aqui de propósito: `users` é referenciada por FK
    # de quase toda tabela do banco (trombadices, tarefas, castigos,
    # conclusões, frases...). O modo batch recria a tabela inteira - dropa e
    # renomeia - e o SQLite recusa dropar uma tabela ainda referenciada por
    # linhas de verdade em outras (erra com "FOREIGN KEY constraint failed",
    # visto ao testar contra um banco com dado dentro, não só create_all). Um
    # ADD/DROP COLUMN direto no SQLite 3.35+ não precisa recriar nada.
    op.add_column("users", sa.Column("can_request", sa.Boolean(), nullable=False, server_default="1"))

    op.create_table(
        "pedidos",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("title", sa.String(length=200), nullable=False),
        sa.Column("justification", sa.Text(), nullable=False, server_default=""),
        sa.Column("status", sa.String(length=20), nullable=False, server_default="PENDENTE"),
        sa.Column("decision_note", sa.Text(), nullable=False, server_default=""),
        sa.Column("decided_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column(
            "decided_by_id", sa.Integer(), sa.ForeignKey("users.id", ondelete="RESTRICT"), nullable=True
        ),
        sa.Column("child_id", sa.Integer(), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("seen_by_parent_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("seen_by_child_at", sa.DateTime(timezone=True), nullable=True),
    )
    op.create_index("ix_pedidos_status", "pedidos", ["status"])
    op.create_index("ix_pedidos_child_id", "pedidos", ["child_id"])


def downgrade() -> None:
    op.drop_index("ix_pedidos_child_id", table_name="pedidos")
    op.drop_index("ix_pedidos_status", table_name="pedidos")
    op.drop_table("pedidos")

    op.drop_column("users", "can_request")
