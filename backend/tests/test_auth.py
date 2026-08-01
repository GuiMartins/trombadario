from fastapi.testclient import TestClient
from sqlalchemy.orm import Session

from app.models import User
from tests.conftest import as_child


def test_login_com_senha_certa_devolve_token(client: TestClient, admin: User) -> None:
    response = client.post("/api/auth/login", data={"username": "pai", "password": "senha-do-pai"})

    assert response.status_code == 200
    assert response.json()["token_type"] == "bearer"
    assert response.json()["access_token"]


def test_login_com_senha_errada_da_401(client: TestClient, admin: User) -> None:
    response = client.post("/api/auth/login", data={"username": "pai", "password": "errada"})

    assert response.status_code == 401


def test_login_de_usuario_inexistente_da_401(client: TestClient, admin: User) -> None:
    response = client.post("/api/auth/login", data={"username": "ninguem", "password": "qualquer"})

    assert response.status_code == 401


def test_conta_desativada_nao_loga(client: TestClient, db: Session, child: User) -> None:
    child.is_active = False
    db.commit()

    response = client.post(
        "/api/auth/login", data={"username": "filho", "password": "senha-do-filho"}
    )

    assert response.status_code == 401


def test_me_devolve_o_papel(client: TestClient, admin: User, child: User) -> None:
    response = client.get("/api/auth/me", headers=as_child(client))

    assert response.status_code == 200
    assert response.json()["role"] == "child"
    assert "password_hash" not in response.json()


def test_sem_token_da_401(client: TestClient) -> None:
    assert client.get("/api/auth/me").status_code == 401


def test_token_invalido_da_401(client: TestClient, admin: User) -> None:
    response = client.get("/api/auth/me", headers={"Authorization": "Bearer nao-e-um-jwt"})

    assert response.status_code == 401
