from datetime import UTC, datetime

from fastapi.testclient import TestClient
from sqlalchemy.orm import Session

from app.models import Trombadice, User
from tests.conftest import as_admin


def test_data_volta_da_api_sempre_com_offset(client: TestClient, admin: User, child: User) -> None:
    created = client.post(
        "/api/trombadices",
        headers=as_admin(client),
        json={"title": "x", "occurred_at": "2026-08-01T14:30:00+00:00", "child_id": child.id},
    ).json()

    # Without the offset the Android client would parse this as local time and
    # show it 3 hours off.
    for field in ("occurred_at", "created_at", "updated_at"):
        assert created[field].endswith("Z") or "+00:00" in created[field], created[field]


def test_horario_com_outro_fuso_e_normalizado_pra_utc(
    client: TestClient, db: Session, admin: User, child: User
) -> None:
    created = client.post(
        "/api/trombadices",
        headers=as_admin(client),
        # 14:30 em Brasília = 17:30 UTC
        json={"title": "x", "occurred_at": "2026-08-01T14:30:00-03:00", "child_id": child.id},
    ).json()

    assert db.get(Trombadice, created["id"]).occurred_at == datetime(2026, 8, 1, 17, 30, tzinfo=UTC)


def test_data_sem_fuso_e_rejeitada_com_422(client: TestClient, admin: User, child: User) -> None:
    response = client.post(
        "/api/trombadices",
        headers=as_admin(client),
        json={"title": "x", "occurred_at": "2026-08-01T14:30:00", "child_id": child.id},
    )

    assert response.status_code == 422


def test_castigo_tambem_devolve_data_com_offset(
    client: TestClient, admin: User, child: User
) -> None:
    created = client.post(
        "/api/punishments",
        headers=as_admin(client),
        json={
            "child_id": child.id,
            "starts_at": "2026-08-01T14:30:00-03:00",
            "ends_at": "2026-08-03T14:30:00-03:00",
        },
    ).json()

    assert created["starts_at"].endswith("Z") or "+00:00" in created["starts_at"]
    assert created["starts_at"].startswith("2026-08-01T17:30")
