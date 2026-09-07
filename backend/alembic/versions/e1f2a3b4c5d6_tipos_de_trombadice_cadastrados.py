"""o tipo da trombadice vira lista cadastrada pelo pai

Revision ID: e1f2a3b4c5d6
Revises: d0e1f2a3b4c5
Create Date: 2026-09-07

Nada se perde. Os oito valores do enum antigo viram as oito primeiras linhas de
`trombadice_categories`, com os mesmos nomes de tela, e cada trombadice já
gravada passa a apontar para a linha equivalente - ninguém precisa recadastrar
nada nem escolher tipo de novo.

A coluna `category` continua existindo, com outro nome (`conquista_category`) e
outro escopo: só conquista, que continua no enum fechado. Nas linhas de
trombadice ela é zerada **depois** da cópia, e isso não é limpeza opcional - os
valores de trombadice saíram do enum em Python, então uma linha que os
mantivesse estouraria `LookupError` na primeira leitura pelo ORM. É a mesma
pegadinha do `native_enum=False` que `tests/test_migrations.py` existe para
pegar, agora pelo avesso: aqui o perigo é o valor que **sobrou**.
"""

from datetime import UTC, datetime

import sqlalchemy as sa

from alembic import op

revision = "e1f2a3b4c5d6"
down_revision = "d0e1f2a3b4c5"
branch_labels = None
depends_on = None

# O nome do membro do enum (é o que o banco guarda) e como ele se chamava na
# tela. A tela é que vira o nome da linha: é o que o pai reconhece.
TIPOS_ANTIGOS: tuple[tuple[str, str], ...] = (
    ("DESRESPEITO", "Falta de respeito"),
    ("EDUCACAO", "Falta de educação"),
    ("NAO_FEZ", "Não fez o que devia"),
    ("MENTIRA", "Mentira"),
    ("BIRRA", "Birra / descontrole"),
    ("ESCOLA", "Escola"),
    ("AGRESSAO", "Agressão"),
    ("OUTRA", "Outra"),
)


def upgrade() -> None:
    op.create_table(
        "trombadice_categories",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("name", sa.String(length=60), nullable=False),
        sa.Column("position", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("is_active", sa.Boolean(), nullable=False, server_default=sa.text("1")),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("name"),
    )
    op.create_index(
        "ix_trombadice_categories_position", "trombadice_categories", ["position"]
    )

    conexao = op.get_bind()
    agora = datetime.now(UTC).strftime("%Y-%m-%d %H:%M:%S.%f")
    for posicao, (_, nome) in enumerate(TIPOS_ANTIGOS):
        conexao.execute(
            sa.text(
                "insert into trombadice_categories (name, position, is_active, created_at)"
                " values (:nome, :posicao, 1, :agora)"
            ),
            {"nome": nome, "posicao": posicao, "agora": agora},
        )

    # Fora do batch: o índice está na coluna que vai ser renomeada, e recriar a
    # tabela com ele apontando para um nome que deixou de existir falha.
    op.drop_index("ix_trombadices_category", table_name="trombadices")
    with op.batch_alter_table("trombadices") as batch:
        batch.add_column(sa.Column("category_id", sa.Integer(), nullable=True))
        batch.alter_column(
            "category",
            new_column_name="conquista_category",
            existing_type=sa.String(length=20),
            existing_nullable=False,
            nullable=True,
        )
        # RESTRICT: sem tipo, a anotação deixa de dizer o que aconteceu. Apagar
        # um tipo em uso é 409 na rota, e o banco recusaria de qualquer jeito.
        batch.create_foreign_key(
            "fk_trombadices_category_id",
            "trombadice_categories",
            ["category_id"],
            ["id"],
            ondelete="RESTRICT",
        )
    op.create_index("ix_trombadices_category_id", "trombadices", ["category_id"])
    op.create_index(
        "ix_trombadices_conquista_category", "trombadices", ["conquista_category"]
    )

    for valor, nome in TIPOS_ANTIGOS:
        conexao.execute(
            sa.text(
                "update trombadices set category_id ="
                " (select id from trombadice_categories where name = :nome)"
                " where kind = 'TROMBADICE' and conquista_category = :valor"
            ),
            {"nome": nome, "valor": valor},
        )
    conexao.execute(
        sa.text("update trombadices set conquista_category = null where kind = 'TROMBADICE'")
    )


def downgrade() -> None:
    conexao = op.get_bind()
    # O caminho de volta perde o que o pai cadastrou depois: tipo que não existe
    # no enum antigo vira "OUTRA", porque lá não há onde guardá-lo. É o preço
    # honesto de voltar - e a tabela nova só é apagada no fim, então uma volta
    # seguida de ida não inventa nada.
    for valor, nome in TIPOS_ANTIGOS:
        conexao.execute(
            sa.text(
                "update trombadices set conquista_category = :valor where category_id ="
                " (select id from trombadice_categories where name = :nome)"
            ),
            {"nome": nome, "valor": valor},
        )
    conexao.execute(
        sa.text(
            "update trombadices set conquista_category = 'OUTRA'"
            " where conquista_category is null"
        )
    )

    op.drop_index("ix_trombadices_conquista_category", table_name="trombadices")
    op.drop_index("ix_trombadices_category_id", table_name="trombadices")
    with op.batch_alter_table("trombadices") as batch:
        batch.drop_constraint("fk_trombadices_category_id", type_="foreignkey")
        batch.drop_column("category_id")
        batch.alter_column(
            "conquista_category",
            new_column_name="category",
            existing_type=sa.String(length=20),
            existing_nullable=True,
            nullable=False,
        )
    op.create_index("ix_trombadices_category", "trombadices", ["category"])

    op.drop_index("ix_trombadice_categories_position", table_name="trombadice_categories")
    op.drop_table("trombadice_categories")
