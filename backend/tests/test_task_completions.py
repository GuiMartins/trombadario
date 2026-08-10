from datetime import UTC, datetime, timedelta

from fastapi.testclient import TestClient
from sqlalchemy.orm import Session

from app.models import Periodicity, Task, TaskCompletion, User
from app.periodo import chave_do_periodo, hoje_local, ultimo_dia_do_mes
from app.routers.tasks import _dia_agendado
from tests.conftest import as_admin, as_child, auth_header


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


# --------------------------------------------------------------------------
# Dia agendado: a chave do período sozinha não dá conta
# --------------------------------------------------------------------------


def test_semanal_de_dois_dias_pode_ser_marcada_nos_dois(
    client: TestClient, db: Session, admin: User, child: User
) -> None:
    """Regressão: "segunda e quinta" é uma tarefa com duas ocorrências na
    semana. Com a chave sendo a semana, marcar na segunda devolvia 409 na
    quinta - a tarefa era cobrada duas vezes e só podia ser cumprida uma."""
    hoje = hoje_local()
    # Outro dia agendado da mesma semana; o relógio não anda no teste, então a
    # conclusão do outro dia entra direto pelo banco.
    outro = hoje - timedelta(days=1) if hoje.weekday() > 0 else hoje + timedelta(days=1)
    task = create_task(
        client, child.id, periodicity="weekly", weekdays=sorted({hoje.weekday(), outro.weekday()})
    )
    db.add(
        TaskCompletion(
            task_id=task["id"],
            child_id=child.id,
            note="",
            period_key=chave_do_periodo(Periodicity.WEEKLY, outro),
            completed_at=datetime.now(UTC) - timedelta(days=1),
        )
    )
    db.commit()

    response = mark_done(client, task["id"])

    assert response.status_code == 201, response.text


def test_mensal_nao_pode_ser_marcada_em_dia_que_nao_e_o_dela(
    client: TestClient, admin: User, child: User
) -> None:
    """Marcar antes do dia gastava o mês inteiro: no dia de verdade a resposta
    virava "já marcado nesse período"."""
    outro_dia = (hoje_local().day % 28) + 1
    task = create_task(client, child.id, periodicity="monthly", day_of_month=outro_dia)

    response = mark_done(client, task["id"])

    assert response.status_code == 400
    assert "hoje" in response.json()["detail"]


def test_mensal_no_dia_dela(client: TestClient, admin: User, child: User) -> None:
    task = create_task(client, child.id, periodicity="monthly", day_of_month=hoje_local().day)

    response = mark_done(client, task["id"])

    assert response.status_code == 201, response.text


def test_dia_do_mes_alem_do_fim_do_mes_cai_no_ultimo_dia() -> None:
    """"Todo dia 31" precisa acontecer em fevereiro, senão a tarefa some do
    calendário em quatro meses do ano."""
    from datetime import date

    task = Task(periodicity=Periodicity.MONTHLY, day_of_month=31, weekdays="")

    assert ultimo_dia_do_mes(date(2026, 2, 10)) == 28
    assert _dia_agendado(task, date(2026, 2, 28)) is True
    assert _dia_agendado(task, date(2026, 2, 27)) is False
    assert _dia_agendado(task, date(2026, 1, 31)) is True
    assert _dia_agendado(task, date(2026, 1, 30)) is False


# --------------------------------------------------------------------------
# O estado que a tela precisa vem calculado do servidor
# --------------------------------------------------------------------------


def test_tarefa_diz_se_hoje_e_dia_dela_e_se_ja_foi_feita(
    client: TestClient, admin: User, child: User
) -> None:
    task = create_task(client, child.id)

    antes = client.get("/api/tasks", headers=as_child(client)).json()[0]
    assert antes["due_today"] is True
    assert antes["current_completion"] is None

    mark_done(client, task["id"], note="antes do café")

    depois = client.get("/api/tasks", headers=as_child(client)).json()[0]
    assert depois["current_completion"]["note"] == "antes do café"
    assert depois["recent_completions"][0]["note"] == "antes do café"


def test_tarefa_de_outro_dia_da_semana_nao_e_de_hoje(
    client: TestClient, admin: User, child: User
) -> None:
    outro_dia = (hoje_local().weekday() + 1) % 7
    create_task(client, child.id, periodicity="weekly", weekdays=[outro_dia])

    task = client.get("/api/tasks", headers=as_child(client)).json()[0]

    assert task["due_today"] is False
    assert task["current_completion"] is None


def test_conclusao_de_outro_periodo_nao_conta_como_a_de_agora(
    client: TestClient, db: Session, admin: User, child: User
) -> None:
    task = create_task(client, child.id)
    db.add(
        TaskCompletion(
            task_id=task["id"],
            child_id=child.id,
            note="ontem",
            period_key=chave_do_periodo(Periodicity.DAILY, hoje_local() - timedelta(days=1)),
            completed_at=datetime.now(UTC) - timedelta(days=1),
        )
    )
    db.commit()

    de_hoje = client.get("/api/tasks", headers=as_child(client)).json()[0]

    assert de_hoje["current_completion"] is None
    assert de_hoje["recent_completions"][0]["note"] == "ontem"


def test_filho_nao_ve_tarefa_pausada(client: TestClient, admin: User, child: User) -> None:
    task = create_task(client, child.id)
    client.patch(f"/api/tasks/{task['id']}", headers=as_admin(client), json={"is_active": False})

    do_filho = client.get("/api/tasks", headers=as_child(client)).json()
    do_pai = client.get("/api/tasks", headers=as_admin(client)).json()

    assert do_filho == []
    assert len(do_pai) == 1


def test_historico_e_paginado(client: TestClient, db: Session, admin: User, child: User) -> None:
    """Uma tarefa diária acumula uma linha por dia, pra sempre."""
    task = create_task(client, child.id)
    for dias in range(10):
        dia = hoje_local() - timedelta(days=dias)
        db.add(
            TaskCompletion(
                task_id=task["id"],
                child_id=child.id,
                note=f"dia -{dias}",
                period_key=chave_do_periodo(Periodicity.DAILY, dia),
                completed_at=datetime.now(UTC) - timedelta(days=dias),
            )
        )
    db.commit()

    pagina = client.get(f"/api/tasks/{task['id']}/completions?limit=3", headers=as_admin(client))
    segunda = client.get(
        f"/api/tasks/{task['id']}/completions?limit=3&offset=3", headers=as_admin(client)
    )

    assert [c["note"] for c in pagina.json()] == ["dia -0", "dia -1", "dia -2"]
    assert [c["note"] for c in segunda.json()] == ["dia -3", "dia -4", "dia -5"]
    # O cartão não carrega o histórico inteiro junto da tarefa.
    tarefa = client.get("/api/tasks", headers=as_admin(client)).json()[0]
    assert len(tarefa["recent_completions"]) == 5


# --------------------------------------------------------------------------
# Desmarcar
# --------------------------------------------------------------------------


def undo(client: TestClient, task_id: int, completion_id: int, headers: dict):
    return client.delete(f"/api/tasks/{task_id}/completions/{completion_id}", headers=headers)


def test_filho_desmarca_o_que_marcou_sem_querer(client: TestClient, admin: User, child: User) -> None:
    """Sem isto, um toque errado numa tarefa avulsa travava "feito" pra sempre."""
    task = create_task(client, child.id, periodicity="once")
    completion = mark_done(client, task["id"]).json()

    apagou = undo(client, task["id"], completion["id"], as_child(client))

    assert apagou.status_code == 204
    assert mark_done(client, task["id"]).status_code == 201


def test_filho_nao_desmarca_periodo_que_ja_passou(
    client: TestClient, db: Session, admin: User, child: User
) -> None:
    """Desmarcar a semana passada não é corrigir engano, é reescrever o que o
    pai já leu."""
    task = create_task(client, child.id)
    antiga = TaskCompletion(
        task_id=task["id"],
        child_id=child.id,
        note="ontem",
        period_key=chave_do_periodo(Periodicity.DAILY, hoje_local() - timedelta(days=1)),
        completed_at=datetime.now(UTC) - timedelta(days=1),
    )
    db.add(antiga)
    db.commit()

    response = undo(client, task["id"], antiga.id, as_child(client))

    assert response.status_code == 400


def test_pai_desmarca_qualquer_periodo(
    client: TestClient, db: Session, admin: User, child: User
) -> None:
    task = create_task(client, child.id)
    antiga = TaskCompletion(
        task_id=task["id"],
        child_id=child.id,
        note="ontem",
        period_key=chave_do_periodo(Periodicity.DAILY, hoje_local() - timedelta(days=1)),
        completed_at=datetime.now(UTC) - timedelta(days=1),
    )
    db.add(antiga)
    db.commit()

    response = undo(client, task["id"], antiga.id, as_admin(client))

    assert response.status_code == 204
    assert client.get(f"/api/tasks/{task['id']}/completions", headers=as_admin(client)).json() == []


def test_filho_nao_desmarca_tarefa_do_irmao(
    client: TestClient, admin: User, child: User, other_child: User
) -> None:
    da_outra = create_task(client, other_child.id)
    completion = client.post(
        f"/api/tasks/{da_outra['id']}/completions",
        headers=auth_header(client, "filha", "senha-da-filha"),
        json={},
    ).json()

    response = undo(client, da_outra["id"], completion["id"], as_child(client))

    assert response.status_code == 404


def test_conclusao_de_outra_tarefa_nao_se_apaga_pelo_id(
    client: TestClient, admin: User, child: User
) -> None:
    """O id da conclusão só vale dentro da tarefa dela - senão bastaria trocar
    o número do caminho pra apagar carimbo de qualquer outra."""
    uma = create_task(client, child.id, "Arrumar a cama")
    outra = create_task(client, child.id, "Levar o lixo")
    completion = mark_done(client, uma["id"]).json()

    response = undo(client, outra["id"], completion["id"], as_child(client))

    assert response.status_code == 404
