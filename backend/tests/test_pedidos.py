from fastapi.testclient import TestClient

from app.models import User
from tests.conftest import as_admin, as_child, auth_header


def create_pedido(client: TestClient, title: str = "Ir na casa do amigo", **extra) -> dict:
    response = client.post(
        "/api/pedidos", headers=as_child(client), json={"title": title, **extra}
    )
    assert response.status_code == 201, response.text
    return response.json()


def test_filho_faz_um_pedido(client: TestClient, admin: User, child: User) -> None:
    pedido = create_pedido(client, "Ir na casa do amigo", justification="fim de semana")

    assert pedido["title"] == "Ir na casa do amigo"
    assert pedido["justification"] == "fim de semana"
    assert pedido["status"] == "pendente"
    assert pedido["child_id"] == child.id
    assert pedido["decided_at"] is None


def test_pai_aprova_pedido(client: TestClient, admin: User, child: User) -> None:
    pedido = create_pedido(client)

    response = client.patch(
        f"/api/pedidos/{pedido['id']}/decidir",
        headers=as_admin(client),
        json={"status": "aprovado", "decision_note": "pode sim"},
    )

    assert response.status_code == 200, response.text
    assert response.json()["status"] == "aprovado"
    assert response.json()["decision_note"] == "pode sim"
    assert response.json()["decided_at"] is not None
    assert response.json()["decided_by_id"] == admin.id


def test_pai_nega_pedido(client: TestClient, admin: User, child: User) -> None:
    pedido = create_pedido(client)

    response = client.patch(
        f"/api/pedidos/{pedido['id']}/decidir",
        headers=as_admin(client),
        json={"status": "negado", "decision_note": "não dessa vez"},
    )

    assert response.status_code == 200
    assert response.json()["status"] == "negado"


def test_pai_nao_redecide_pedido_ja_decidido(client: TestClient, admin: User, child: User) -> None:
    pedido = create_pedido(client)
    client.patch(
        f"/api/pedidos/{pedido['id']}/decidir",
        headers=as_admin(client),
        json={"status": "aprovado"},
    )

    segunda = client.patch(
        f"/api/pedidos/{pedido['id']}/decidir",
        headers=as_admin(client),
        json={"status": "negado"},
    )

    assert segunda.status_code == 400


def test_filho_sem_can_request_nao_pede(client: TestClient, admin: User, child: User) -> None:
    client.patch(f"/api/users/{child.id}", headers=as_admin(client), json={"can_request": False})

    response = client.post(
        "/api/pedidos", headers=as_child(client), json={"title": "Ir na casa do amigo"}
    )

    assert response.status_code == 403


def test_pai_nao_pode_pedir(client: TestClient, admin: User, child: User) -> None:
    response = client.post(
        "/api/pedidos", headers=as_admin(client), json={"title": "Ir na casa do amigo"}
    )

    assert response.status_code == 403


def test_filho_nao_ve_pedido_do_irmao(
    client: TestClient, admin: User, child: User, other_child: User
) -> None:
    da_outra = client.post(
        "/api/pedidos",
        headers=auth_header(client, "filha", "senha-da-filha"),
        json={"title": "Comprar um jogo"},
    ).json()

    assert client.get("/api/pedidos", headers=as_child(client)).json() == []
    assert client.get(
        f"/api/pedidos/{da_outra['id']}", headers=as_child(client)
    ).status_code == 404


def test_pai_ve_pedidos_de_qualquer_filho(client: TestClient, admin: User, child: User) -> None:
    create_pedido(client)

    response = client.get("/api/pedidos", headers=as_admin(client))

    assert response.status_code == 200
    assert len(response.json()) == 1
