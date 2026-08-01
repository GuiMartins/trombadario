from fastapi.testclient import TestClient

from app.models import Trombadice, User
from tests.conftest import as_admin, as_child

OCCURRED_AT = "2026-08-01T14:30:00+00:00"


def create_task(client: TestClient, child_id: int, name: str = "Arrumar a cama", **extra) -> dict:
    response = client.post(
        "/api/tasks",
        headers=as_admin(client),
        json={"name": name, "description": "todo dia de manhã", "child_id": child_id, **extra},
    )
    assert response.status_code == 201, response.text
    return response.json()


def test_admin_cadastra_tarefa_diaria(client: TestClient, admin: User, child: User) -> None:
    task = create_task(client, child.id)

    assert task["periodicity"] == "daily"
    assert task["weekdays"] == []
    assert task["child_id"] == child.id
    assert task["is_active"] is True


def test_tarefa_semanal_guarda_os_dias(client: TestClient, admin: User, child: User) -> None:
    task = create_task(client, child.id, "Levar o lixo", periodicity="weekly", weekdays=[4, 1, 1])

    # Ordenado e sem repetição - a API não devolve o que foi digitado, devolve o
    # que significa.
    assert task["weekdays"] == [1, 4]


def test_tarefa_semanal_exige_pelo_menos_um_dia(
    client: TestClient, admin: User, child: User
) -> None:
    response = client.post(
        "/api/tasks",
        headers=as_admin(client),
        json={"name": "Sem dia", "child_id": child.id, "periodicity": "weekly", "weekdays": []},
    )

    assert response.status_code == 400


def test_tarefa_mensal_exige_dia_do_mes(client: TestClient, admin: User, child: User) -> None:
    response = client.post(
        "/api/tasks",
        headers=as_admin(client),
        json={"name": "Sem dia", "child_id": child.id, "periodicity": "monthly"},
    )

    assert response.status_code == 400


def test_dia_da_semana_invalido_e_recusado(client: TestClient, admin: User, child: User) -> None:
    response = client.post(
        "/api/tasks",
        headers=as_admin(client),
        json={"name": "x", "child_id": child.id, "periodicity": "weekly", "weekdays": [9]},
    )

    assert response.status_code == 422


def test_trocar_pra_diaria_limpa_os_dias_da_semana(
    client: TestClient, admin: User, child: User
) -> None:
    task = create_task(client, child.id, periodicity="weekly", weekdays=[0, 3])

    response = client.patch(
        f"/api/tasks/{task['id']}", headers=as_admin(client), json={"periodicity": "daily"}
    )

    # Senão a tela mostraria "diária, às segundas e quintas" - uma contradição.
    assert response.json()["weekdays"] == []


def test_filho_nao_pode_mexer_em_tarefa(client: TestClient, admin: User, child: User) -> None:
    task = create_task(client, child.id)
    headers = as_child(client)

    assert client.post(
        "/api/tasks", headers=headers, json={"name": "minha", "child_id": child.id}
    ).status_code == 403
    assert client.patch(
        f"/api/tasks/{task['id']}", headers=headers, json={"name": "outra"}
    ).status_code == 403
    assert client.delete(f"/api/tasks/{task['id']}", headers=headers).status_code == 403


def test_filho_so_ve_as_tarefas_dele(
    client: TestClient, admin: User, child: User, other_child: User
) -> None:
    create_task(client, child.id, "Do filho")
    da_outra = create_task(client, other_child.id, "Da filha")

    listed = client.get("/api/tasks", headers=as_child(client)).json()

    assert [t["name"] for t in listed] == ["Do filho"]
    assert client.get(f"/api/tasks/{da_outra['id']}", headers=as_child(client)).status_code == 404


def test_trombadice_pode_apontar_pra_tarefa_nao_cumprida(
    client: TestClient, admin: User, child: User
) -> None:
    task = create_task(client, child.id)

    response = client.post(
        "/api/trombadices",
        headers=as_admin(client),
        json={
            "title": "Não arrumou a cama",
            "occurred_at": OCCURRED_AT,
            "child_id": child.id,
            "task_id": task["id"],
        },
    )

    assert response.status_code == 201
    assert response.json()["task_id"] == task["id"]


def test_nao_da_pra_ligar_trombadice_a_tarefa_de_outro_filho(
    client: TestClient, admin: User, child: User, other_child: User
) -> None:
    da_outra = create_task(client, other_child.id, "Da filha")

    response = client.post(
        "/api/trombadices",
        headers=as_admin(client),
        json={
            "title": "x",
            "occurred_at": OCCURRED_AT,
            "child_id": child.id,
            "task_id": da_outra["id"],
        },
    )

    # O vínculo afirmaria algo falso: esse filho nunca teve essa tarefa.
    assert response.status_code == 400


def test_apagar_tarefa_preserva_a_trombadice(
    client: TestClient, db, admin: User, child: User
) -> None:
    task = create_task(client, child.id)
    trombadice = client.post(
        "/api/trombadices",
        headers=as_admin(client),
        json={
            "title": "Não arrumou a cama",
            "occurred_at": OCCURRED_AT,
            "child_id": child.id,
            "task_id": task["id"],
        },
    ).json()

    assert client.delete(f"/api/tasks/{task['id']}", headers=as_admin(client)).status_code == 204

    # ondelete=SET NULL: o registro do que aconteceu sobrevive à tarefa que o
    # causou. CASCADE aqui apagaria história de verdade.
    db.expire_all()
    stored = db.get(Trombadice, trombadice["id"])
    assert stored is not None
    assert stored.task_id is None
