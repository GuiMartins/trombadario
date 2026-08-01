from fastapi.testclient import TestClient
from sqlalchemy.orm import Session

from app.models import User
from tests.conftest import as_admin, auth_header

OCCURRED_AT = "2026-08-01T14:30:00+00:00"


def test_admin_cria_conta_de_filho(client: TestClient, admin: User) -> None:
    headers = as_admin(client)

    response = client.post(
        "/api/users",
        headers=headers,
        json={"username": "filho2", "password": "senha-nova", "display_name": "Filho 2"},
    )

    assert response.status_code == 201
    assert response.json()["role"] == "child"
    assert client.post(
        "/api/auth/login", data={"username": "filho2", "password": "senha-nova"}
    ).status_code == 200


def test_filho_nao_acessa_gestao_de_usuarios(client: TestClient, admin: User, child: User) -> None:
    headers = auth_header(client, "filho", "senha-do-filho")

    assert client.get("/api/users", headers=headers).status_code == 403
    assert client.post(
        "/api/users",
        headers=headers,
        json={"username": "eu2", "password": "123456", "display_name": "Eu"},
    ).status_code == 403
    assert client.patch(
        f"/api/users/{child.id}", headers=headers, json={"display_name": "Chefe"}
    ).status_code == 403
    assert client.delete(f"/api/users/{child.id}", headers=headers).status_code == 403


def test_username_duplicado_da_409(client: TestClient, admin: User, child: User) -> None:
    headers = as_admin(client)

    response = client.post(
        "/api/users",
        headers=headers,
        json={"username": "filho", "password": "outra-senha", "display_name": "Impostor"},
    )

    assert response.status_code == 409


def test_admin_troca_a_senha_do_filho(client: TestClient, admin: User, child: User) -> None:
    headers = as_admin(client)

    response = client.patch(
        f"/api/users/{child.id}", headers=headers, json={"password": "senha-trocada"}
    )

    assert response.status_code == 200
    assert client.post(
        "/api/auth/login", data={"username": "filho", "password": "senha-trocada"}
    ).status_code == 200
    assert client.post(
        "/api/auth/login", data={"username": "filho", "password": "senha-do-filho"}
    ).status_code == 401


def test_nao_desativa_o_unico_admin(client: TestClient, admin: User) -> None:
    headers = as_admin(client)

    response = client.patch(f"/api/users/{admin.id}", headers=headers, json={"is_active": False})

    assert response.status_code == 400


def test_nao_apaga_o_unico_admin(client: TestClient, admin: User) -> None:
    headers = as_admin(client)

    assert client.delete(f"/api/users/{admin.id}", headers=headers).status_code == 400


def test_nao_apaga_quem_ja_cadastrou_trombadice(
    client: TestClient, db: Session, admin: User, child: User
) -> None:
    headers = as_admin(client)
    client.post(
        "/api/users",
        headers=headers,
        json={"username": "mae", "password": "senha-da-mae", "display_name": "Mãe", "role": "admin"},
    )
    mae_headers = auth_header(client, "mae", "senha-da-mae")
    client.post(
        "/api/trombadices",
        headers=mae_headers,
        json={"title": "Nota baixa", "occurred_at": OCCURRED_AT, "child_id": child.id},
    )
    mae_id = db.query(User).filter(User.username == "mae").one().id

    response = client.delete(f"/api/users/{mae_id}", headers=headers)

    # RESTRICT on author_id: the history must survive the account being removed.
    assert response.status_code == 400


def test_apagar_filho_leva_as_trombadices_dele_junto(
    client: TestClient, admin: User, child: User
) -> None:
    headers = as_admin(client)
    client.post(
        "/api/trombadices",
        headers=headers,
        json={"title": "Bagunça", "occurred_at": OCCURRED_AT, "child_id": child.id},
    )

    assert client.delete(f"/api/users/{child.id}", headers=headers).status_code == 204
    assert client.get("/api/trombadices", headers=headers).json() == []
