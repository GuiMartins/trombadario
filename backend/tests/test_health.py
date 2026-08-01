import uuid

from fastapi.testclient import TestClient
from sqlalchemy.orm import Session

from app.server_identity import get_server_id


def test_health_e_publico_e_traz_o_server_id(client: TestClient) -> None:
    response = client.get("/api/health")

    assert response.status_code == 200
    assert response.json()["app"] == "trombadario"
    uuid.UUID(response.json()["server_id"])


def test_server_id_nao_muda_entre_chamadas(client: TestClient, db: Session) -> None:
    # The app pairs with this value on first setup - if it changed, every phone
    # would decide it had left home.
    primeiro = client.get("/api/health").json()["server_id"]

    assert client.get("/api/health").json()["server_id"] == primeiro
    assert get_server_id(db) == primeiro
