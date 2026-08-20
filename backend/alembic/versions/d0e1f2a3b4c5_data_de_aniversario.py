"""data de aniversário do filho

Revision ID: d0e1f2a3b4c5
Revises: c9d0e1f2a3b4
Create Date: 2026-08-20

Uma coluna nula, que é o estado certo de toda conta que já existe: ninguém
tinha aniversário cadastrado antes desta migration, e inventar uma data seria
pior que a ausência dela.

`sa.Date` e não `sa.DateTime`: aniversário é dia, não instante. Guardado com
hora, o dia mudaria conforme o fuso de quem lesse - exatamente o que
`app/periodo.py` existe pra evitar.

Nenhuma coluna de enum aqui, então a pegadinha do `native_enum=False` (que
guarda o NOME do membro, não o valor) não se aplica. O que `tests/test_migrations.py`
cobre desta é o outro risco do SQLite: ele não tem tipo de data nativo, guarda
texto, e uma coluna declarada errada só apareceria na primeira leitura pelo ORM.
"""

import sqlalchemy as sa

from alembic import op

revision = "d0e1f2a3b4c5"
down_revision = "c9d0e1f2a3b4"
branch_labels = None
depends_on = None


def upgrade() -> None:
    # Sem batch_alter_table, pelo mesmo motivo das migrations de `can_request` e
    # `can_discuss`: `users` é referenciada por FK de quase toda tabela do banco,
    # e o modo batch (que recria a tabela) esbarra na FK com linha de verdade
    # dentro. ADD COLUMN direto basta no SQLite 3.35+.
    #
    # Sem server_default porque nulo já é o padrão de uma coluna nulável - e
    # aqui, ao contrário de `can_discuss`, não existe valor de partida honesto.
    op.add_column("users", sa.Column("birth_date", sa.Date(), nullable=True))


def downgrade() -> None:
    op.drop_column("users", "birth_date")
