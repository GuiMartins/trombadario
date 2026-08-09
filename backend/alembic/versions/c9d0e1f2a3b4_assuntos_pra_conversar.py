"""assuntos pra conversar, e o acesso do filho a eles

Revision ID: c9d0e1f2a3b4
Revises: b8c9d0e1f2a3
Create Date: 2026-08-09

`can_discuss` precisa de `server_default`: quem já tem conta tem que nascer
podendo usar a funcionalidade nova, não travado por uma migration silenciosa.
Mesmo motivo do `can_request`, na migration dos pedidos.

Nenhuma coluna de enum aqui - `assuntos` guarda "já conversamos" como instante
nulável (`talked_at`), não como status. Então a pegadinha do `native_enum=False`
(que guarda o NOME do membro, não o valor) não se aplica a esta migration.
"""

import sqlalchemy as sa

from alembic import op

revision = "c9d0e1f2a3b4"
down_revision = "b8c9d0e1f2a3"
branch_labels = None
depends_on = None


def upgrade() -> None:
    # Sem batch_alter_table, pelo mesmo motivo documentado na migration dos
    # pedidos: `users` é referenciada por FK de quase toda tabela do banco, e o
    # modo batch (que recria a tabela) esbarra na FK com linha de verdade
    # dentro. ADD COLUMN direto basta no SQLite 3.35+.
    op.add_column(
        "users", sa.Column("can_discuss", sa.Boolean(), nullable=False, server_default="1")
    )

    op.create_table(
        "assuntos",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("title", sa.String(length=200), nullable=False),
        sa.Column("description", sa.Text(), nullable=False, server_default=""),
        sa.Column(
            "child_id", sa.Integer(), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False
        ),
        sa.Column(
            "author_id", sa.Integer(), sa.ForeignKey("users.id", ondelete="RESTRICT"), nullable=False
        ),
        sa.Column("talked_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column(
            "talked_by_id",
            sa.Integer(),
            sa.ForeignKey("users.id", ondelete="RESTRICT"),
            nullable=True,
        ),
        sa.Column("talked_note", sa.Text(), nullable=False, server_default=""),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("seen_by_parent_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("seen_by_child_at", sa.DateTime(timezone=True), nullable=True),
    )
    op.create_index("ix_assuntos_child_id", "assuntos", ["child_id"])


def downgrade() -> None:
    op.drop_index("ix_assuntos_child_id", table_name="assuntos")
    op.drop_table("assuntos")

    op.drop_column("users", "can_discuss")
