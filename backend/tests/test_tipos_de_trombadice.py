"""A lista de tipos de trombadice, que agora é do pai.

Era um enum de oito valores no código. O que muda de verdade não é onde a lista
mora: é que ela pode crescer, ser renomeada e ser aposentada sem deploy - e que
o registro passou a apontar a linha, não uma cópia do nome.
"""

from fastapi.testclient import TestClient
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.categorias import TIPOS_INICIAIS
from app.models import TrombadiceCategory, User
from tests.conftest import as_admin, as_child, corpo_de_trombadice, tipo_id


def criar_tipo(client: TestClient, nome: str, **extra):
    return client.post(
        "/api/trombadice-categories", headers=as_admin(client), json={"name": nome, **extra}
    )


def test_a_lista_inicial_e_a_do_enum_antigo(client: TestClient, admin: User) -> None:
    tipos = client.get("/api/trombadice-categories", headers=as_admin(client)).json()

    # Quem já usava o app não recadastra nada, e quem instala hoje não começa
    # numa tela sem opção nenhuma.
    assert [t["name"] for t in tipos] == list(TIPOS_INICIAIS)


def test_setup_cria_a_lista_inicial(client: TestClient, db: Session) -> None:
    # Sem o `admin` da conftest: aqui o que cria a conta do pai é o assistente
    # de primeira execução, que é quem semeia a lista em produção.
    resposta = client.post(
        "/api/setup",
        json={"username": "pai", "password": "senha-do-pai", "display_name": "Pai"},
    )

    assert resposta.status_code == 201, resposta.text
    nomes = list(db.scalars(select(TrombadiceCategory.name).order_by(TrombadiceCategory.position)))
    assert nomes == list(TIPOS_INICIAIS)


def test_pai_cadastra_um_tipo_novo(client: TestClient, admin: User) -> None:
    resposta = criar_tipo(client, "Mexeu no celular escondido")

    assert resposta.status_code == 201, resposta.text
    assert resposta.json()["name"] == "Mexeu no celular escondido"
    # Sem posição pedida, entra no fim: quem cadastra o nono tipo não quer ter
    # que saber que ele é o nono.
    assert resposta.json()["position"] == len(TIPOS_INICIAIS)
    assert resposta.json()["em_uso"] == 0


def test_nome_repetido_e_recusado(client: TestClient, admin: User) -> None:
    # Dois "Mentira" na lista partiriam o relatório ao meio sem ninguém ver.
    assert criar_tipo(client, "mentira").status_code == 409


def test_filho_le_a_lista_mas_nao_mexe(client: TestClient, admin: User, child: User) -> None:
    # Ele precisa dos nomes: são os chips de filtro do feed dele.
    assert client.get("/api/trombadice-categories", headers=as_child(client)).status_code == 200

    assert client.post(
        "/api/trombadice-categories", headers=as_child(client), json={"name": "Nada"}
    ).status_code == 403
    assert client.delete(
        f"/api/trombadice-categories/{tipo_id(client)}", headers=as_child(client)
    ).status_code == 403


def test_filho_nao_ve_tipo_desativado(client: TestClient, admin: User, child: User) -> None:
    escola = tipo_id(client, "Escola")
    client.patch(
        f"/api/trombadice-categories/{escola}",
        headers=as_admin(client),
        json={"is_active": False},
    )

    dele = client.get("/api/trombadice-categories", headers=as_child(client)).json()
    do_pai = client.get("/api/trombadice-categories", headers=as_admin(client)).json()

    # Tipo pausado não é opção de filtro - é lixo na tela dele. O pai continua
    # vendo, porque é ele quem reativa.
    assert "Escola" not in [t["name"] for t in dele]
    assert "Escola" in [t["name"] for t in do_pai]


def test_tipo_sem_uso_pode_ser_apagado(client: TestClient, admin: User) -> None:
    novo = criar_tipo(client, "Coisa que nunca aconteceu").json()

    apagar = client.delete(
        f"/api/trombadice-categories/{novo['id']}", headers=as_admin(client)
    )

    assert apagar.status_code == 204
    restantes = client.get("/api/trombadice-categories", headers=as_admin(client)).json()
    assert novo["id"] not in [t["id"] for t in restantes]


def test_tipo_em_uso_nao_pode_ser_apagado(client: TestClient, admin: User, child: User) -> None:
    mentira = tipo_id(client, "Mentira")
    client.post(
        "/api/trombadices",
        headers=as_admin(client),
        json=corpo_de_trombadice(client, child.id, category_id=mentira),
    )

    apagar = client.delete(f"/api/trombadice-categories/{mentira}", headers=as_admin(client))

    # Apagar deixaria uma anotação sem dizer o que aconteceu - e a criança já
    # pode ter lido aquilo. Para tirar da frente existe o desativar.
    assert apagar.status_code == 409
    assert "Desative" in apagar.json()["detail"]


def test_em_uso_conta_as_anotacoes_do_tipo(client: TestClient, admin: User, child: User) -> None:
    mentira = tipo_id(client, "Mentira")
    for _ in range(2):
        client.post(
            "/api/trombadices",
            headers=as_admin(client),
            json=corpo_de_trombadice(client, child.id, category_id=mentira),
        )

    tipos = client.get("/api/trombadice-categories", headers=as_admin(client)).json()

    # A tela precisa saber disso **antes** do toque, para não oferecer um botão
    # de excluir que só devolveria erro.
    assert {t["name"]: t["em_uso"] for t in tipos}["Mentira"] == 2


def test_desativar_nao_apaga_o_que_ja_foi_registrado(
    client: TestClient, admin: User, child: User
) -> None:
    escola = tipo_id(client, "Escola")
    antiga = client.post(
        "/api/trombadices",
        headers=as_admin(client),
        json=corpo_de_trombadice(client, child.id, category_id=escola),
    ).json()

    client.patch(
        f"/api/trombadice-categories/{escola}",
        headers=as_admin(client),
        json={"is_active": False},
    )

    depois = client.get(f"/api/trombadices/{antiga['id']}", headers=as_admin(client)).json()
    assert depois["title"] == "Escola"
    assert depois["category_name"] == "Escola"


def test_a_ordem_e_a_que_o_pai_escolheu(client: TestClient, admin: User) -> None:
    criar_tipo(client, "Primeiro de tudo", position=0)

    nomes = [
        t["name"] for t in client.get(
            "/api/trombadice-categories", headers=as_admin(client)
        ).json()
    ]

    # Era a ordem do enum, do mais comum ao menos; agora é a escolha dele.
    # (Empate de posição desempata pelo nome, então o que vale é vir antes de
    # quem tem posição maior - não o alfabeto.)
    assert nomes.index("Primeiro de tudo") < nomes.index("Mentira")
