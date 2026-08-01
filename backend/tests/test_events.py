from datetime import UTC, datetime

from fastapi.testclient import TestClient
from sqlalchemy.orm import Session

from app.models import Event, Role, User
from app.security import hash_password
from tests.conftest import auth_header

OCCURRED_AT = "2026-08-01T14:30:00+00:00"


def _create_event(client: TestClient, headers: dict[str, str], child_id: int, title: str) -> dict:
    response = client.post(
        "/api/events",
        headers=headers,
        json={"title": title, "description": "quebrou o vaso", "occurred_at": OCCURRED_AT,
              "child_id": child_id},
    )
    assert response.status_code == 201, response.text
    return response.json()


def test_admin_cadastra_acontecimento(client: TestClient, admin: User, child: User) -> None:
    headers = auth_header(client, "pai", "senha-do-pai")

    event = _create_event(client, headers, child.id, "Bagunça na sala")

    assert event["title"] == "Bagunça na sala"
    assert event["child_id"] == child.id
    assert event["author_id"] == admin.id


def test_filho_nao_pode_cadastrar(client: TestClient, admin: User, child: User) -> None:
    headers = auth_header(client, "filho", "senha-do-filho")

    response = client.post(
        "/api/events",
        headers=headers,
        json={"title": "eu fui bonzinho", "occurred_at": OCCURRED_AT, "child_id": child.id},
    )

    assert response.status_code == 403


def test_filho_nao_pode_editar_nem_apagar(
    client: TestClient, admin: User, child: User
) -> None:
    admin_headers = auth_header(client, "pai", "senha-do-pai")
    event = _create_event(client, admin_headers, child.id, "Bagunça na sala")
    child_headers = auth_header(client, "filho", "senha-do-filho")

    assert client.patch(
        f"/api/events/{event['id']}", headers=child_headers, json={"title": "nada demais"}
    ).status_code == 403
    assert client.delete(f"/api/events/{event['id']}", headers=child_headers).status_code == 403


def test_filho_so_ve_os_eventos_dele(client: TestClient, db: Session, admin: User, child: User) -> None:
    outro = User(
        username="outro-filho",
        password_hash=hash_password("senha-do-outro"),
        display_name="Outro",
        role=Role.CHILD,
    )
    db.add(outro)
    db.commit()
    db.refresh(outro)

    admin_headers = auth_header(client, "pai", "senha-do-pai")
    _create_event(client, admin_headers, child.id, "Do filho")
    do_outro = _create_event(client, admin_headers, outro.id, "Do outro")

    child_headers = auth_header(client, "filho", "senha-do-filho")
    listed = client.get("/api/events", headers=child_headers).json()

    assert [e["title"] for e in listed] == ["Do filho"]
    # 404, not 403 - probing ids must not confirm that someone else's event
    # exists.
    assert client.get(f"/api/events/{do_outro['id']}", headers=child_headers).status_code == 404


def test_filho_nao_burla_o_filtro_por_child_id(
    client: TestClient, db: Session, admin: User, child: User
) -> None:
    outro = User(
        username="outro-filho",
        password_hash=hash_password("senha-do-outro"),
        display_name="Outro",
        role=Role.CHILD,
    )
    db.add(outro)
    db.commit()
    db.refresh(outro)

    admin_headers = auth_header(client, "pai", "senha-do-pai")
    _create_event(client, admin_headers, outro.id, "Do outro")

    child_headers = auth_header(client, "filho", "senha-do-filho")
    listed = client.get(f"/api/events?child_id={outro.id}", headers=child_headers).json()

    assert listed == []


def test_admin_ve_tudo_e_filtra(client: TestClient, admin: User, child: User) -> None:
    headers = auth_header(client, "pai", "senha-do-pai")
    _create_event(client, headers, child.id, "Bagunça na sala")

    assert len(client.get("/api/events", headers=headers).json()) == 1
    assert len(client.get(f"/api/events?child_id={child.id}", headers=headers).json()) == 1
    assert client.get(f"/api/events?child_id={child.id + 999}", headers=headers).json() == []


def test_feed_vem_do_mais_recente_pro_mais_antigo(
    client: TestClient, admin: User, child: User
) -> None:
    headers = auth_header(client, "pai", "senha-do-pai")
    for occurred_at, title in [
        ("2026-07-01T10:00:00+00:00", "antigo"),
        ("2026-08-01T10:00:00+00:00", "recente"),
        ("2026-07-15T10:00:00+00:00", "meio"),
    ]:
        client.post(
            "/api/events",
            headers=headers,
            json={"title": title, "occurred_at": occurred_at, "child_id": child.id},
        )

    listed = client.get("/api/events", headers=headers).json()

    assert [e["title"] for e in listed] == ["recente", "meio", "antigo"]


def test_nao_cadastra_evento_pra_quem_nao_e_filho(
    client: TestClient, admin: User, child: User
) -> None:
    headers = auth_header(client, "pai", "senha-do-pai")

    response = client.post(
        "/api/events",
        headers=headers,
        json={"title": "teste", "occurred_at": OCCURRED_AT, "child_id": admin.id},
    )

    assert response.status_code == 400


def test_editar_altera_so_o_que_foi_mandado(client: TestClient, admin: User, child: User) -> None:
    headers = auth_header(client, "pai", "senha-do-pai")
    event = _create_event(client, headers, child.id, "Bagunça na sala")

    response = client.patch(
        f"/api/events/{event['id']}", headers=headers, json={"title": "Bagunça no quarto"}
    )

    assert response.status_code == 200
    assert response.json()["title"] == "Bagunça no quarto"
    assert response.json()["description"] == "quebrou o vaso"


def test_apagar_remove_do_feed(client: TestClient, admin: User, child: User) -> None:
    headers = auth_header(client, "pai", "senha-do-pai")
    event = _create_event(client, headers, child.id, "Bagunça na sala")

    assert client.delete(f"/api/events/{event['id']}", headers=headers).status_code == 204
    assert client.get("/api/events", headers=headers).json() == []


def test_occurred_at_e_independente_do_created_at(
    client: TestClient, db: Session, admin: User, child: User
) -> None:
    headers = auth_header(client, "pai", "senha-do-pai")

    event = _create_event(client, headers, child.id, "Aconteceu semana passada")

    stored = db.get(Event, event["id"])
    assert stored.occurred_at == datetime(2026, 8, 1, 14, 30, tzinfo=UTC)
    assert stored.created_at != stored.occurred_at
