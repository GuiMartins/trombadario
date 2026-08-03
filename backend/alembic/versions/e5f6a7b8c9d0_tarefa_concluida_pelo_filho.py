"""tarefa concluída pelo filho

Revision ID: e5f6a7b8c9d0
Revises: d4e5f6a7b8c9
Create Date: 2026-08-03

Tabela nova, nada em coluna existente. Sem enum aqui também - period_key é
texto livre calculado em Python (app/periodo.py), não um Enum do SQLAlchemy.

A UniqueConstraint(task_id, period_key) é o que trava "uma vez por período"
de verdade - não só a validação da rota. Vale um round-trip contra o banco
migrado de propósito (não só create_all): inserir duas vezes e esperar
IntegrityError é exatamente o tipo de coisa que só a migration real garante.
Ver tests/test_migrations.py.
"""

import sqlalchemy as sa

from alembic import op

revision = "e5f6a7b8c9d0"
down_revision = "d4e5f6a7b8c9"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "task_completions",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("task_id", sa.Integer(), sa.ForeignKey("tasks.id", ondelete="CASCADE"), nullable=False),
        sa.Column("child_id", sa.Integer(), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("note", sa.Text(), nullable=False, server_default=""),
        sa.Column("period_key", sa.String(length=10), nullable=False),
        sa.Column("completed_at", sa.DateTime(timezone=True), nullable=False),
        sa.UniqueConstraint("task_id", "period_key", name="uq_task_completion_period"),
    )
    op.create_index("ix_task_completions_task_id", "task_completions", ["task_id"])
    op.create_index("ix_task_completions_period_key", "task_completions", ["period_key"])


def downgrade() -> None:
    op.drop_index("ix_task_completions_period_key", table_name="task_completions")
    op.drop_index("ix_task_completions_task_id", table_name="task_completions")
    op.drop_table("task_completions")
