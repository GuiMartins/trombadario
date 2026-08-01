from fastapi.testclient import TestClient
from sqlalchemy.orm import Session

from app.models import Role, User
from app.setup_state import setup_required

PAYLOAD = {"username": "pai", "password": "senha-do-pai", "display_name": "Pai"}


def test_servidor_novo_pede_configuracao(client: TestClient) -> None:
    assert client.get("/api/health").json()["setup_required"] is True


def test_setup_cria_a_conta_do_pai_e_ela_loga(client: TestClient) -> None:
    response = client.post("/api/setup", json=PAYLOAD)

    assert response.status_code == 201
    assert response.json()["role"] == "admin"
    assert client.post(
        "/api/auth/login", data={"username": "pai", "password": "senha-do-pai"}
    ).status_code == 200


def test_setup_se_fecha_depois_da_primeira_conta(client: TestClient) -> None:
    client.post("/api/setup", json=PAYLOAD)

    segunda = client.post(
        "/api/setup",
        json={"username": "invasor", "password": "senha-boa", "display_name": "Invasor"},
    )

    # A única rota de escrita sem autenticação do sistema - ela precisa se
    # fechar sozinha, senão qualquer um na rede vira admin.
    assert segunda.status_code == 403
    assert client.get("/api/health").json()["setup_required"] is False


def test_servidor_ja_configurado_nao_pede_configuracao(client: TestClient, admin: User) -> None:
    assert client.get("/api/health").json()["setup_required"] is False


def test_so_filho_cadastrado_ainda_conta_como_nao_configurado(
    client: TestClient, child: User
) -> None:
    # Um filho não administra nada - sem admin, o servidor continua inutilizável.
    assert client.get("/api/health").json()["setup_required"] is True


def test_admin_desativado_reabre_a_configuracao(
    client: TestClient, db: Session, admin: User
) -> None:
    admin.is_active = False
    db.commit()

    assert setup_required(db) is True


def test_setup_recusa_username_ja_existente(client: TestClient, db: Session, child: User) -> None:
    response = client.post(
        "/api/setup",
        json={"username": "filho", "password": "senha-nova", "display_name": "Pai"},
    )

    assert response.status_code == 409
    assert db.query(User).filter(User.role == Role.ADMIN).count() == 0
