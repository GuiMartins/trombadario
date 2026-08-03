from fastapi.testclient import TestClient

from app.models import Kind, User
from tests.conftest import as_admin, as_child

QUANDO = "2026-08-01T14:30:00+00:00"


def criar(client: TestClient, child_id: int, **campos):
    corpo = {"title": "algo", "occurred_at": QUANDO, "child_id": child_id, **campos}
    return client.post("/api/trombadices", headers=as_admin(client), json=corpo)


def test_sem_tipo_continua_sendo_trombadice(client: TestClient, admin: User, child: User) -> None:
    # O que já existia não muda de significado por causa da feature nova.
    assert criar(client, child.id).json()["kind"] == "trombadice"


def test_conquista_e_gravada(client: TestClient, admin: User, child: User) -> None:
    resposta = criar(client, child.id, title="Arrumou a casa sozinho", kind="conquista")

    assert resposta.status_code == 201, resposta.text
    assert resposta.json()["kind"] == "conquista"
    # Sem categoria informada, cai na padrão do tipo - não na "Outra" de
    # trombadice, que é de outra lista.
    assert resposta.json()["category"] == "outra_boa"


def test_categoria_de_trombadice_nao_serve_pra_conquista(
    client: TestClient, admin: User, child: User
) -> None:
    resposta = criar(client, child.id, kind="conquista", category="agressao")

    # "Agressão" não descreve coisa boa.
    assert resposta.status_code == 422


def test_categoria_de_conquista_nao_serve_pra_trombadice(
    client: TestClient, admin: User, child: User
) -> None:
    assert criar(client, child.id, kind="trombadice", category="gentileza").status_code == 422


def test_conquista_nao_se_atrela_a_tarefa(client: TestClient, admin: User, child: User) -> None:
    tarefa = client.post(
        "/api/tasks",
        headers=as_admin(client),
        json={"name": "Arrumar a cama", "child_id": child.id, "periodicity": "daily"},
    ).json()

    resposta = criar(client, child.id, kind="conquista", task_id=tarefa["id"])

    # Tarefa serve pra registrar o que **não** foi cumprido; o vínculo diria o
    # contrário do que significa.
    assert resposta.status_code == 422


def test_filtra_por_tipo(client: TestClient, admin: User, child: User) -> None:
    criar(client, child.id, title="Empurrou", kind="trombadice", category="agressao")
    criar(client, child.id, title="Ajudou na louça", kind="conquista", category="ajudou")

    conquistas = client.get(
        "/api/trombadices", headers=as_admin(client), params={"kind": "conquista"}
    ).json()

    assert [t["title"] for t in conquistas] == ["Ajudou na louça"]


def test_editar_conquista_nao_aceita_categoria_de_trombadice(
    client: TestClient, admin: User, child: User
) -> None:
    conquista = criar(client, child.id, kind="conquista").json()

    resposta = client.patch(
        f"/api/trombadices/{conquista['id']}",
        headers=as_admin(client),
        json={"category": "mentira"},
    )

    assert resposta.status_code == 400


def test_castigo_nao_pode_vir_de_conquista(client: TestClient, admin: User, child: User) -> None:
    conquista = criar(client, child.id, kind="conquista").json()

    resposta = client.post(
        "/api/punishments",
        headers=as_admin(client),
        json={
            "child_id": child.id,
            "ends_at": "2026-12-25T18:00:00+00:00",
            "trombadice_ids": [conquista["id"]],
        },
    )

    assert resposta.status_code == 400
    assert resposta.json()["detail"] == "Conquista não causa castigo"


def test_relatorio_conta_as_duas_coisas_separadas(
    client: TestClient, admin: User, child: User
) -> None:
    criar(client, child.id, kind="trombadice", category="mentira")
    criar(client, child.id, kind="conquista", category="ajudou")
    criar(client, child.id, kind="conquista", category="ajudou")

    dados = client.get(
        "/api/reports",
        headers=as_admin(client),
        params={"de": "2026-08-01", "ate": "2026-08-01"},
    ).json()

    # Somar coisa boa com coisa ruim daria um número que não responde nenhuma
    # das duas perguntas.
    assert dados["total"] == 1
    assert dados["conquistas"] == 2
    assert dados["conquistas_por_categoria"] == [{"rotulo": "ajudou", "total": 2}]


def test_filho_ve_a_conquista_dele(client: TestClient, admin: User, child: User) -> None:
    criar(client, child.id, title="Ajudou na louça", kind="conquista")

    dele = client.get("/api/trombadices", headers=as_child(client)).json()

    assert [t["title"] for t in dele] == ["Ajudou na louça"]


def test_filho_continua_sem_poder_cadastrar_conquista(
    client: TestClient, admin: User, child: User
) -> None:
    resposta = client.post(
        "/api/trombadices",
        headers=as_child(client),
        json={
            "title": "Fui otimo hoje",
            "occurred_at": QUANDO,
            "child_id": child.id,
            "kind": "conquista",
        },
    )

    # Conquista é o pai quem reconhece. Deixar o filho cadastrar a própria
    # transformaria o registro em autoelogio.
    assert resposta.status_code == 403


def test_kind_nao_se_edita(client: TestClient, admin: User, child: User) -> None:
    conquista = criar(client, child.id, kind="conquista").json()

    client.patch(
        f"/api/trombadices/{conquista['id']}",
        headers=as_admin(client),
        json={"kind": "trombadice"},
    )

    depois = client.get(f"/api/trombadices/{conquista['id']}", headers=as_admin(client)).json()
    assert depois["kind"] == Kind.CONQUISTA.value
