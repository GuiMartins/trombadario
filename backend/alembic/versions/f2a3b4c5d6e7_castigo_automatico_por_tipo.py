"""castigo automático: o tipo de trombadice diz quantos dias custa

Revision ID: f2a3b4c5d6e7
Revises: e1f2a3b4c5d6
Create Date: 2026-09-13

Três números no tipo (dias base, aumento por recorrência, teto) e duas colunas
no castigo dizendo de onde ele veio.

**Zero é o desligado, não nulo**, nas três colunas de configuração - e é também
o valor com que toda instalação existente chega aqui, de propósito: nenhum tipo
passa a gerar castigo até o pai preencher. Uma migration que adivinhasse "um dia
por mentira" estaria decidindo pela casa dos outros.

`recurrence_level` e `origin_trombadice_id` são nulos porque é o estado certo de
todo castigo que já existe: eles foram cadastrados à mão, e nulo diz exatamente
isso. Inventar nível zero afirmaria que a conta automática os produziu.

Nenhuma coluna de enum aqui, então a pegadinha do `native_enum=False` (que guarda
o NOME do membro, não o valor) não se aplica. O que `tests/test_migrations.py`
cobre desta é o outro risco: `server_default` chega como texto no SQLite, e uma
coluna que volte como `"0"` em vez de `0` só apareceria na primeira conta.
"""

import sqlalchemy as sa

from alembic import op

revision = "f2a3b4c5d6e7"
down_revision = "e1f2a3b4c5d6"
branch_labels = None
depends_on = None

CONFIG = ("punishment_days", "escalation_days", "max_days")


def upgrade() -> None:
    for coluna in CONFIG:
        # `server_default="0"` para as linhas que já existem; `nullable=False`
        # porque zero já é o "desligado" e uma coluna nula só acrescentaria um
        # segundo jeito de dizer a mesma coisa.
        op.add_column(
            "trombadice_categories",
            sa.Column(coluna, sa.Integer(), nullable=False, server_default="0"),
        )

    # `add_column` direto, e **sem FK** em `origin_trombadice_id`. Não é descuido:
    # FK exigiria `batch_alter_table` (o SQLite não faz ALTER de constraint), o
    # modo batch recria a tabela, e o `DROP TABLE punishments` do meio do caminho
    # - com `PRAGMA foreign_keys=ON`, que a aplicação liga - dispararia o ON
    # DELETE CASCADE de `punishment_trombadices` e apagaria **todos** os vínculos
    # de causa da instalação. Um castigo sem causa é justamente o que o resto do
    # sistema proíbe, e seria esta migration que o produziria em massa.
    #
    # O comportamento de SET NULL vive em `apagar_castigos_sem_causa`, que já
    # precisava decidir o caso difícil (só apaga castigo que ficaria sem causa
    # nenhuma). Ver o comentário em models.Punishment.
    op.add_column("punishments", sa.Column("origin_trombadice_id", sa.Integer(), nullable=True))
    op.add_column("punishments", sa.Column("recurrence_level", sa.Integer(), nullable=True))
    op.create_index(
        "ix_punishments_origin_trombadice_id", "punishments", ["origin_trombadice_id"]
    )


def downgrade() -> None:
    op.drop_index("ix_punishments_origin_trombadice_id", table_name="punishments")
    op.drop_column("punishments", "recurrence_level")
    op.drop_column("punishments", "origin_trombadice_id")
    for coluna in reversed(CONFIG):
        op.drop_column("trombadice_categories", coluna)
