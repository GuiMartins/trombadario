"""frases de abertura

Revision ID: ac8900eb456b
Revises: 11c91335ab07
Create Date: 2026-08-01 22:53:24.197747

"""
from collections.abc import Sequence

import sqlalchemy as sa

import app.types
from alembic import op

revision: str = 'ac8900eb456b'
down_revision: str | None = '11c91335ab07'
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table('splash_messages',
    sa.Column('id', sa.Integer(), nullable=False),
    sa.Column('text', sa.String(length=300), nullable=False),
    sa.Column('child_id', sa.Integer(), nullable=True),
    sa.Column('author_id', sa.Integer(), nullable=False),
    sa.Column('is_active', sa.Boolean(), nullable=False),
    sa.Column('created_at', app.types.UtcDateTime(timezone=True), nullable=False),
    sa.ForeignKeyConstraint(['author_id'], ['users.id'], ondelete='RESTRICT'),
    sa.ForeignKeyConstraint(['child_id'], ['users.id'], ondelete='CASCADE'),
    sa.PrimaryKeyConstraint('id')
    )
    with op.batch_alter_table('splash_messages', schema=None) as batch_op:
        batch_op.create_index(batch_op.f('ix_splash_messages_child_id'), ['child_id'], unique=False)



def downgrade() -> None:
    with op.batch_alter_table('splash_messages', schema=None) as batch_op:
        batch_op.drop_index(batch_op.f('ix_splash_messages_child_id'))

    op.drop_table('splash_messages')
