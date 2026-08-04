"""As migrations rodando de verdade, contra um banco com linha dentro.

O resto da suíte monta o schema com `Base.metadata.create_all` e insere tudo
pelo ORM. Isso é rápido e serve para quase tudo, mas deixa um buraco: nada nunca
passa pelas migrations, então um `server_default` que discorde de como o ORM
grava o valor só aparece em produção.

Foi exatamente o que aconteceu com `category`: a migration punha "outra" e o
`Enum(..., native_enum=False)` guarda o **nome** do membro, "OUTRA". Todos os
testes passavam e a página quebrava com LookupError na primeira leitura.
"""

import sqlite3
from datetime import UTC, datetime
from pathlib import Path

import pytest
from sqlalchemy import create_engine, select
from sqlalchemy.orm import sessionmaker

from alembic import command
from alembic.config import Config
from app.models import Category, Kind, Trombadice

BACKEND_DIR = Path(__file__).resolve().parent.parent

# A revisão logo antes da que acrescentou `category` e `seen_at`.
ANTES_DA_CATEGORIA = "ac8900eb456b"


def _alembic(url: str) -> Config:
    config = Config(str(BACKEND_DIR / "alembic.ini"))
    config.set_main_option("script_location", str(BACKEND_DIR / "alembic"))
    config.set_main_option("sqlalchemy.url", url)
    return config


@pytest.fixture
def banco_antigo(tmp_path: Path) -> str:
    """Um banco na versão anterior, com uma trombadice já cadastrada."""
    arquivo = tmp_path / "antigo.db"
    url = f"sqlite:///{arquivo}"
    command.upgrade(_alembic(url), ANTES_DA_CATEGORIA)

    agora = datetime.now(UTC).strftime("%Y-%m-%d %H:%M:%S.%f")
    conexao = sqlite3.connect(arquivo)
    conexao.execute(
        "insert into users (username,password_hash,display_name,role,is_active,created_at)"
        " values ('pai','x','Pai','ADMIN',1,?)",
        (agora,),
    )
    conexao.execute(
        "insert into users (username,password_hash,display_name,role,is_active,created_at)"
        " values ('filho','x','Joao','CHILD',1,?)",
        (agora,),
    )
    conexao.execute(
        "insert into trombadices (title,description,occurred_at,child_id,author_id,"
        "created_at,updated_at) values ('Machou a irma','no parquinho',?,2,1,?,?)",
        (agora, agora, agora),
    )
    conexao.commit()
    conexao.close()
    return url


def test_linha_antiga_continua_legivel_pelo_orm(banco_antigo: str) -> None:
    command.upgrade(_alembic(banco_antigo), "head")

    with sessionmaker(bind=create_engine(banco_antigo))() as sessao:
        trombadice = sessao.scalars(select(Trombadice)).one()

        assert trombadice.title == "Machou a irma"
        # O que a migration escreveu tem que ser exatamente o que o ORM entende.
        assert trombadice.category is Category.OUTRA
        assert trombadice.seen_at is None
        # O que já existia é trombadice: conquista não existia quando aquilo
        # foi cadastrado.
        assert trombadice.kind is Kind.TROMBADICE


def test_o_que_ja_existia_sobrevive(banco_antigo: str) -> None:
    command.upgrade(_alembic(banco_antigo), "head")

    conexao = sqlite3.connect(banco_antigo.removeprefix("sqlite:///"))
    try:
        assert conexao.execute("select count(*) from users").fetchone()[0] == 2
        assert conexao.execute("select count(*) from trombadices").fetchone()[0] == 1
        # `batch_alter_table` recria a tabela; o ON DELETE do vínculo com tarefa
        # tem que sobreviver à recriação, senão apagar uma tarefa passaria a
        # levar junto o registro do que aconteceu.
        fks = conexao.execute("pragma foreign_key_list('trombadices')").fetchall()
        para_tarefa = [f for f in fks if f[2] == "tasks"]
        assert para_tarefa and para_tarefa[0][6] == "SET NULL"
    finally:
        conexao.close()


def test_task_completion_trava_um_por_periodo(tmp_path: Path) -> None:
    """`UniqueConstraint(task_id, period_key)` não é enum - a pegadinha do
    `native_enum=False` não se aplica aqui. O que só a migration real garante
    é que a constraint existe de fato no banco, não só no `create_all` que o
    resto da suíte usa. Sem isso, "uma vez por período" seria só uma promessa
    da rota, quebrável por qualquer segunda escrita concorrente."""
    arquivo = tmp_path / "novo.db"
    url = f"sqlite:///{arquivo}"
    command.upgrade(_alembic(url), "head")

    agora = datetime.now(UTC).strftime("%Y-%m-%d %H:%M:%S.%f")
    conexao = sqlite3.connect(arquivo)
    conexao.execute(
        "insert into users (username,password_hash,display_name,role,is_active,created_at)"
        " values ('pai','x','Pai','ADMIN',1,?)",
        (agora,),
    )
    conexao.execute(
        "insert into users (username,password_hash,display_name,role,is_active,created_at)"
        " values ('filho','x','Joao','CHILD',1,?)",
        (agora,),
    )
    conexao.execute(
        "insert into tasks (name,description,periodicity,weekdays,day_of_month,child_id,"
        "author_id,is_active,created_at,updated_at) values "
        "('Arrumar a cama','','DAILY','',null,2,1,1,?,?)",
        (agora, agora),
    )
    conexao.execute(
        "insert into task_completions (task_id,child_id,note,period_key,completed_at)"
        " values (1,2,'','2026-08-03',?)",
        (agora,),
    )
    conexao.commit()

    with pytest.raises(sqlite3.IntegrityError):
        conexao.execute(
            "insert into task_completions (task_id,child_id,note,period_key,completed_at)"
            " values (1,2,'de novo','2026-08-03',?)",
            (agora,),
        )
    conexao.close()


def test_pedido_pendente_legivel_pelo_orm(banco_antigo: str) -> None:
    """`status` é enum `native_enum=False` - mesma pegadinha do `category`:
    a migration escreve "PENDENTE" (o nome do membro), não "pendente" (o
    valor). Errar isso passaria em toda a suíte (que usa create_all) e só
    estouraria LookupError na primeira leitura em produção."""
    command.upgrade(_alembic(banco_antigo), "head")

    agora = datetime.now(UTC).strftime("%Y-%m-%d %H:%M:%S.%f")
    conexao = sqlite3.connect(banco_antigo.removeprefix("sqlite:///"))
    conexao.execute(
        "insert into pedidos (title,justification,status,decision_note,child_id,created_at)"
        " values ('Ir na casa do amigo','fim de semana',?,'',2,?)",
        ("PENDENTE", agora),
    )
    conexao.commit()
    conexao.close()

    with sessionmaker(bind=create_engine(banco_antigo))() as sessao:
        from app.models import Pedido, RequestStatus

        pedido = sessao.scalars(select(Pedido)).one()
        assert pedido.status is RequestStatus.PENDENTE
        assert pedido.child_id == 2


def test_conquista_proposta_legivel_pelo_orm(banco_antigo: str) -> None:
    """`kind` e `category` em `pedidos` são a mesma pegadinha de novo: a
    migration escreve "PEDIDO"/"AJUDOU" (o nome do membro), não "pedido"/
    "ajudou" (o valor)."""
    command.upgrade(_alembic(banco_antigo), "head")

    agora = datetime.now(UTC).strftime("%Y-%m-%d %H:%M:%S.%f")
    conexao = sqlite3.connect(banco_antigo.removeprefix("sqlite:///"))
    conexao.execute(
        "insert into pedidos (title,justification,status,decision_note,child_id,created_at,"
        "kind,category) values ('Ajudei a lavar a louça','depois do jantar',?,'',2,?,?,?)",
        ("PENDENTE", agora, "CONQUISTA_PROPOSTA", "AJUDOU"),
    )
    conexao.commit()
    conexao.close()

    with sessionmaker(bind=create_engine(banco_antigo))() as sessao:
        from app.models import Category, Pedido, RequestKind

        pedido = sessao.scalars(select(Pedido)).one()
        assert pedido.kind is RequestKind.CONQUISTA_PROPOSTA
        assert pedido.category is Category.AJUDOU


def test_ida_e_volta_da_migration(banco_antigo: str) -> None:
    config = _alembic(banco_antigo)
    command.upgrade(config, "head")

    command.downgrade(config, ANTES_DA_CATEGORIA)

    conexao = sqlite3.connect(banco_antigo.removeprefix("sqlite:///"))
    try:
        colunas = [c[1] for c in conexao.execute("pragma table_info('trombadices')")]
        assert "category" not in colunas
        assert "kind" not in colunas
        assert conexao.execute("select count(*) from trombadices").fetchone()[0] == 1
    finally:
        conexao.close()
