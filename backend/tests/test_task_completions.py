from fastapi.testclient import TestClient

from app.models import User
from app.periodo import hoje_local
from tests.conftest import as_admin, as_child


def create_task(client: TestClient, child_id: int, name: str = "Arrumar a cama", **extra) -> dict:
    response = client.post(
        "/api/tasks",
        headers=as_admin(client),
        json={"name": name, "description": "", "child_id": child_id, **extra},
    )
    assert response.status_code == 201, response.text
    return response.json()


def mark_done(client: TestClient, task_id: int, **extra):
    return client.post(f"/api/tasks/{task_id}/completions", headers=as_child(client), json=extra)


def test_filho_marca_tarefa_diaria_como_feita(client: TestClient, admin: User, child: User) -> None:
    task = create_task(client, child.id)

    response = mark_done(client, task["id"], note="feito antes do café")

    assert response.status_code == 201, response.text
    assert response.json()["note"] == "feito antes do café"
    assert response.json()["task_id"] == task["id"]


def test_filho_nao_marca_duas_vezes_no_mesmo_dia(client: TestClient, admin: User, child: User) -> None:
    task = create_task(client, child.id)
    mark_done(client, task["id"])

    segunda = mark_done(client, task["id"])

    assert segunda.status_code == 409


def test_filho_marca_tarefa_semanal_no_dia_agendado(client: TestClient, admin: User, child: User) -> None:
    hoje = hoje_local().weekday()
    task = create_task(client, child.id, periodicity="weekly", weekdays=[hoje])

    response = mark_done(client, task["id"])

    assert response.status_code == 201, response.text


def test_filho_nao_marca_tarefa_semanal_fora_do_dia_agendado(
    client: TestClient, admin: User, child: User
) -> None:
    outro_dia = (hoje_local().weekday() + 1) % 7
    task = create_task(client, child.id, periodicity="weekly", weekdays=[outro_dia])

    response = mark_done(client, task["id"])

    assert response.status_code == 400


def test_filho_marca_tarefa_avulsa_so_uma_vez_pra_sempre(
    client: TestClient, admin: User, child: User
) -> None:
    task = create_task(client, child.id, periodicity="once")
    primeira = mark_done(client, task["id"])

    segunda = mark_done(client, task["id"])

    assert primeira.status_code == 201
    assert segunda.status_code == 409


def test_filho_nao_marca_tarefa_pausada(client: TestClient, admin: User, child: User) -> None:
    task = create_task(client, child.id)
    client.patch(f"/api/tasks/{task['id']}", headers=as_admin(client), json={"is_active": False})

    response = mark_done(client, task["id"])

    assert response.status_code == 400


def test_filho_nao_marca_tarefa_do_irmao(
    client: TestClient, admin: User, child: User, other_child: User
) -> None:
    da_outra = create_task(client, other_child.id)

    response = mark_done(client, da_outra["id"])

    assert response.status_code == 404


def test_pai_ve_as_conclusoes(client: TestClient, admin: User, child: User) -> None:
    task = create_task(client, child.id)
    mark_done(client, task["id"], note="prontinho")

    response = client.get(f"/api/tasks/{task['id']}/completions", headers=as_admin(client))

    assert response.status_code == 200
    assert len(response.json()) == 1
    assert response.json()[0]["note"] == "prontinho"


def test_filho_nao_ve_conclusoes_da_tarefa_do_irmao(
    client: TestClient, admin: User, child: User, other_child: User
) -> None:
    da_outra = create_task(client, other_child.id)

    response = client.get(f"/api/tasks/{da_outra['id']}/completions", headers=as_child(client))

    assert response.status_code == 404
