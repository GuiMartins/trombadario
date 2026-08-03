from datetime import UTC, datetime

from fastapi.testclient import TestClient
from sqlalchemy.orm import Session

from app.models import Trombadice, User
from tests.conftest import as_admin, as_child

OCCURRED_AT = "2026-08-01T14:30:00+00:00"


def create(client: TestClient, headers: dict, child_id: int, title: str, **extra) -> dict:
    response = client.post(
        "/api/trombadices",
        headers=headers,
        json={
            "title": title,
            "description": "quebrou o vaso",
            "occurred_at": OCCURRED_AT,
            "child_id": child_id,
            **extra,
        },
    )
    assert response.status_code == 201, response.text
    return response.json()


def test_admin_cadastra(client: TestClient, admin: User, child: User) -> None:
    trombadice = create(client, as_admin(client), child.id, "Bagunça na sala")

    assert trombadice["title"] == "Bagunça na sala"
    assert trombadice["child_id"] == child.id
    assert trombadice["author_id"] == admin.id
    assert trombadice["task_id"] is None


def test_filho_nao_pode_cadastrar(client: TestClient, admin: User, child: User) -> None:
    response = client.post(
        "/api/trombadices",
        headers=as_child(client),
        json={"title": "eu fui bonzinho", "occurred_at": OCCURRED_AT, "child_id": child.id},
    )

    assert response.status_code == 403


def test_filho_nao_pode_editar_nem_apagar(client: TestClient, admin: User, child: User) -> None:
    trombadice = create(client, as_admin(client), child.id, "Bagunça na sala")
    headers = as_child(client)

    assert client.patch(
        f"/api/trombadices/{trombadice['id']}", headers=headers, json={"title": "nada demais"}
    ).status_code == 403
    assert client.delete(f"/api/trombadices/{trombadice['id']}", headers=headers).status_code == 403


def test_filho_so_ve_as_dele(
    client: TestClient, admin: User, child: User, other_child: User
) -> None:
    admin_headers = as_admin(client)
    create(client, admin_headers, child.id, "Do filho")
    da_outra = create(client, admin_headers, other_child.id, "Da filha")

    listed = client.get("/api/trombadices", headers=as_child(client)).json()

    assert [t["title"] for t in listed] == ["Do filho"]
    # 404, not 403 - probing ids must not confirm a sibling's record exists.
    assert client.get(
        f"/api/trombadices/{da_outra['id']}", headers=as_child(client)
    ).status_code == 404


def test_filho_nao_burla_o_filtro_por_child_id(
    client: TestClient, admin: User, child: User, other_child: User
) -> None:
    create(client, as_admin(client), other_child.id, "Da filha")

    listed = client.get(
        f"/api/trombadices?child_id={other_child.id}", headers=as_child(client)
    ).json()

    assert listed == []


def test_admin_ve_tudo_e_filtra_por_filho(
    client: TestClient, admin: User, child: User, other_child: User
) -> None:
    headers = as_admin(client)
    create(client, headers, child.id, "Do filho")
    create(client, headers, other_child.id, "Da filha")

    assert len(client.get("/api/trombadices", headers=headers).json()) == 2
    filtradas = client.get(f"/api/trombadices?child_id={child.id}", headers=headers).json()
    assert [t["title"] for t in filtradas] == ["Do filho"]


def test_feed_vem_do_mais_recente_pro_mais_antigo(
    client: TestClient, admin: User, child: User
) -> None:
    headers = as_admin(client)
    for occurred_at, title in [
        ("2026-07-01T10:00:00+00:00", "antigo"),
        ("2026-08-01T10:00:00+00:00", "recente"),
        ("2026-07-15T10:00:00+00:00", "meio"),
    ]:
        client.post(
            "/api/trombadices",
            headers=headers,
            json={"title": title, "occurred_at": occurred_at, "child_id": child.id},
        )

    listed = client.get("/api/trombadices", headers=headers).json()

    assert [t["title"] for t in listed] == ["recente", "meio", "antigo"]


def test_nao_cadastra_pra_quem_nao_e_filho(client: TestClient, admin: User, child: User) -> None:
    response = client.post(
        "/api/trombadices",
        headers=as_admin(client),
        json={"title": "teste", "occurred_at": OCCURRED_AT, "child_id": admin.id},
    )

    assert response.status_code == 400


def test_editar_altera_so_o_que_foi_mandado(client: TestClient, admin: User, child: User) -> None:
    trombadice = create(client, as_admin(client), child.id, "Bagunça na sala")

    response = client.patch(
        f"/api/trombadices/{trombadice['id']}",
        headers=as_admin(client),
        json={"title": "Bagunça no quarto"},
    )

    assert response.status_code == 200
    assert response.json()["title"] == "Bagunça no quarto"
    assert response.json()["description"] == "quebrou o vaso"


def test_apagar_remove_do_feed(client: TestClient, admin: User, child: User) -> None:
    trombadice = create(client, as_admin(client), child.id, "Bagunça na sala")

    assert (
        client.delete(
            f"/api/trombadices/{trombadice['id']}", headers=as_admin(client)
        ).status_code
        == 204
    )
    assert client.get("/api/trombadices", headers=as_admin(client)).json() == []


def test_occurred_at_e_independente_do_created_at(
    client: TestClient, db: Session, admin: User, child: User
) -> None:
    trombadice = create(client, as_admin(client), child.id, "Aconteceu semana passada")

    stored = db.get(Trombadice, trombadice["id"])
    assert stored.occurred_at == datetime(2026, 8, 1, 14, 30, tzinfo=UTC)
    assert stored.created_at != stored.occurred_at
