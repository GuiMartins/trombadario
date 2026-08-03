from fastapi.testclient import TestClient

from app.models import User
from tests.conftest import as_admin, as_child


def create_proposta(
    client: TestClient, title: str = "Arrumei o quarto sozinho", **extra
) -> dict:
    response = client.post(
        "/api/conquistas-propostas",
        headers=as_child(client),
        json={"title": title, "category": "ajudou", **extra},
    )
    assert response.status_code == 201, response.text
    return response.json()


def test_filho_propoe_conquista(client: TestClient, admin: User, child: User) -> None:
    proposta = create_proposta(client, "Ajudei a lavar a louça", justification="depois do jantar")

    assert proposta["kind"] == "conquista_proposta"
    assert proposta["title"] == "Ajudei a lavar a louça"
    assert proposta["category"] == "ajudou"
    assert proposta["status"] == "pendente"
    assert proposta["child_id"] == child.id


def test_proposta_pendente_nao_aparece_nas_trombadices_nem_no_relatorio(
    client: TestClient, admin: User, child: User
) -> None:
    create_proposta(client)

    trombadices = client.get("/api/trombadices", headers=as_admin(client))
    assert trombadices.status_code == 200
    assert trombadices.json() == []

    relatorio = client.get("/api/reports", headers=as_admin(client))
    assert relatorio.status_code == 200
    assert relatorio.json()["conquistas"] == 0


def test_pai_aprova_proposta_cria_trombadice_de_verdade(
    client: TestClient, admin: User, child: User
) -> None:
    proposta = create_proposta(
        client, "Ajudei a lavar a louça", justification="depois do jantar", category="ajudou"
    )

    decisao = client.patch(
        f"/api/pedidos/{proposta['id']}/decidir",
        headers=as_admin(client),
        json={"status": "aprovado", "decision_note": "orgulho"},
    )
    assert decisao.status_code == 200, decisao.text
    assert decisao.json()["status"] == "aprovado"

    trombadices = client.get("/api/trombadices", headers=as_admin(client)).json()
    assert len(trombadices) == 1
    conquista = trombadices[0]
    assert conquista["kind"] == "conquista"
    assert conquista["title"] == "Ajudei a lavar a louça"
    assert conquista["description"] == "depois do jantar"
    assert conquista["category"] == "ajudou"
    assert conquista["child_id"] == child.id
    # Autor é quem confirmou de verdade, não quem teve a ideia.
    assert conquista["author_id"] == admin.id


def test_pai_nega_proposta_nunca_promove(client: TestClient, admin: User, child: User) -> None:
    proposta = create_proposta(client)

    decisao = client.patch(
        f"/api/pedidos/{proposta['id']}/decidir",
        headers=as_admin(client),
        json={"status": "negado", "decision_note": "não foi bem assim"},
    )
    assert decisao.status_code == 200
    assert decisao.json()["status"] == "negado"

    trombadices = client.get("/api/trombadices", headers=as_admin(client)).json()
    assert trombadices == []


def test_filho_sem_can_request_nao_propoe(client: TestClient, admin: User, child: User) -> None:
    client.patch(f"/api/users/{child.id}", headers=as_admin(client), json={"can_request": False})

    response = client.post(
        "/api/conquistas-propostas",
        headers=as_child(client),
        json={"title": "Ajudei a lavar a louça", "category": "ajudou"},
    )

    assert response.status_code == 403


def test_pai_nao_pode_propor(client: TestClient, admin: User, child: User) -> None:
    response = client.post(
        "/api/conquistas-propostas",
        headers=as_admin(client),
        json={"title": "Ajudei a lavar a louça", "category": "ajudou"},
    )

    assert response.status_code == 403


def test_categoria_de_trombadice_e_rejeitada_na_proposta(
    client: TestClient, admin: User, child: User
) -> None:
    response = client.post(
        "/api/conquistas-propostas",
        headers=as_child(client),
        json={"title": "Ajudei a lavar a louça", "category": "agressao"},
    )

    assert response.status_code == 422


def test_pedido_comum_e_proposta_nao_se_misturam_na_listagem(
    client: TestClient, admin: User, child: User
) -> None:
    client.post(
        "/api/pedidos", headers=as_child(client), json={"title": "Ir na casa do amigo"}
    )
    create_proposta(client)

    pedidos = client.get("/api/pedidos", headers=as_admin(client)).json()
    assert len(pedidos) == 2
    kinds = {p["kind"] for p in pedidos}
    assert kinds == {"pedido", "conquista_proposta"}
