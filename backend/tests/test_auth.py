from fastapi.testclient import TestClient
from sqlalchemy.orm import Session

from app.models import Role, User
from app.seed import seed_admin
from tests.conftest import auth_header


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
    headers = auth_header(client, "filho", "senha-do-filho")

    response = client.get("/api/auth/me", headers=headers)

    assert response.status_code == 200
    assert response.json()["role"] == "child"
    assert "password_hash" not in response.json()


def test_sem_token_da_401(client: TestClient) -> None:
    assert client.get("/api/auth/me").status_code == 401


def test_token_invalido_da_401(client: TestClient, admin: User) -> None:
    response = client.get("/api/auth/me", headers={"Authorization": "Bearer nao-e-um-jwt"})

    assert response.status_code == 401


def test_seed_do_admin_e_idempotente(db: Session) -> None:
    seed_admin(db)
    hash_original = db.query(User).filter(User.username == "pai").one().password_hash

    seed_admin(db)

    admins = db.query(User).filter(User.role == Role.ADMIN).all()
    assert len(admins) == 1
    # Re-seeding must not rewrite the password: after first start it is managed
    # in the app, and the .env would silently undo that on every restart.
    assert admins[0].password_hash == hash_original


def test_seed_nao_cria_segundo_admin_com_outro_nome(db: Session) -> None:
    seed_admin(db)
    db.query(User).filter(User.username == "pai").one().username = "pai-renomeado"
    db.commit()

    seed_admin(db)

    assert db.query(User).filter(User.role == Role.ADMIN).count() == 1
