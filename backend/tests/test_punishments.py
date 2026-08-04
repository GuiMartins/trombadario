from datetime import UTC, datetime, timedelta

from fastapi.testclient import TestClient

from app.models import Punishment, User
from tests.conftest import as_admin, as_child

OCCURRED_AT = "2026-08-01T14:30:00+00:00"


def iso(delta: timedelta) -> str:
    return (datetime.now(UTC) + delta).isoformat()


def create_trombadice(client: TestClient, child_id: int, title: str = "Bagunça") -> dict:
    return client.post(
        "/api/trombadices",
        headers=as_admin(client),
        json={"title": title, "occurred_at": OCCURRED_AT, "child_id": child_id},
    ).json()


def punish(client: TestClient, child_id: int, **extra) -> dict:
    payload = {"child_id": child_id, "ends_at": iso(timedelta(days=2)), **extra}
    response = client.post("/api/punishments", headers=as_admin(client), json=payload)
    assert response.status_code == 201, response.text
    return response.json()


def test_pai_poe_de_castigo(client: TestClient, admin: User, child: User) -> None:
    punishment = punish(client, child.id, reason="quebrou o vaso")

    assert punishment["child_id"] == child.id
    assert punishment["is_active"] is True
    assert punishment["ended_early_at"] is None


def test_castigo_aponta_as_trombadices_que_o_causaram(
    client: TestClient, admin: User, child: User
) -> None:
    uma = create_trombadice(client, child.id, "Bagunça")
    outra = create_trombadice(client, child.id, "Nota baixa")

    punishment = punish(client, child.id, trombadice_ids=[uma["id"], outra["id"]])

    assert sorted(punishment["trombadice_ids"]) == sorted([uma["id"], outra["id"]])


def test_nao_liga_castigo_a_trombadice_de_outro_filho(
    client: TestClient, admin: User, child: User, other_child: User
) -> None:
    da_outra = create_trombadice(client, other_child.id, "Da filha")

    response = client.post(
        "/api/punishments",
        headers=as_admin(client),
        json={
            "child_id": child.id,
            "ends_at": iso(timedelta(days=1)),
            "trombadice_ids": [da_outra["id"]],
        },
    )

    # Colocaria o nome de um irmão no castigo do outro.
    assert response.status_code == 400


def test_castigo_precisa_terminar_depois_de_comecar(
    client: TestClient, admin: User, child: User
) -> None:
    response = client.post(
        "/api/punishments",
        headers=as_admin(client),
        json={
            "child_id": child.id,
            "starts_at": iso(timedelta(days=1)),
            "ends_at": iso(timedelta(hours=1)),
        },
    )

    assert response.status_code == 400


def test_castigo_futuro_ainda_nao_esta_ativo(client: TestClient, admin: User, child: User) -> None:
    punishment = punish(
        client, child.id, starts_at=iso(timedelta(days=1)), ends_at=iso(timedelta(days=2))
    )

    assert punishment["is_active"] is False


def test_castigo_vencido_nao_esta_ativo(client: TestClient, db, admin: User, child: User) -> None:
    punishment = punish(client, child.id)
    stored = db.get(Punishment, punishment["id"])
    stored.starts_at = datetime.now(UTC) - timedelta(days=5)
    stored.ends_at = datetime.now(UTC) - timedelta(days=1)
    db.commit()

    # Expira sozinho pela passagem do tempo - não existe nada rodando pra virar
    # um booleano, e é por isso que "ativo" é calculado e não guardado.
    listed = client.get(f"/api/punishments/{punishment['id']}", headers=as_admin(client)).json()
    assert listed["is_active"] is False


def test_pai_encerra_o_castigo_antes_da_hora(client: TestClient, admin: User, child: User) -> None:
    punishment = punish(client, child.id)

    response = client.patch(
        f"/api/punishments/{punishment['id']}", headers=as_admin(client), json={"end_now": True}
    )

    assert response.status_code == 200
    assert response.json()["is_active"] is False
    assert response.json()["ended_early_at"] is not None
    # O ends_at original fica intacto: a história mostra o que foi dado e o que
    # foi cumprido.
    assert response.json()["ends_at"] == punishment["ends_at"]


def test_current_responde_a_pergunta_do_filho(client: TestClient, admin: User, child: User) -> None:
    assert client.get("/api/punishments/current", headers=as_child(client)).json() == []

    punish(client, child.id)

    ativos = client.get("/api/punishments/current", headers=as_child(client)).json()
    assert len(ativos) == 1
    assert ativos[0]["is_active"] is True


def test_filho_ve_as_trombadices_completas_do_proprio_castigo(
    client: TestClient, admin: User, child: User
) -> None:
    """Bug de tela, não de dado: a API sempre mandou `trombadice_ids`, mas o
    filho precisa dos objetos inteiros pra não fazer uma segunda busca."""
    uma = create_trombadice(client, child.id, "Bagunça")
    outra = create_trombadice(client, child.id, "Nota baixa")
    punish(client, child.id, trombadice_ids=[uma["id"], outra["id"]])

    ativos = client.get("/api/punishments/current", headers=as_child(client)).json()

    assert len(ativos) == 1
    titulos = sorted(t["title"] for t in ativos[0]["trombadices"])
    assert titulos == sorted([uma["title"], outra["title"]])


def test_filho_nao_ve_castigo_de_outro_filho(
    client: TestClient, admin: User, child: User, other_child: User
) -> None:
    da_outra = punish(client, other_child.id)

    assert client.get("/api/punishments", headers=as_child(client)).json() == []
    assert client.get("/api/punishments/current", headers=as_child(client)).json() == []
    assert client.get(
        f"/api/punishments/{da_outra['id']}", headers=as_child(client)
    ).status_code == 404


def test_filho_nao_pode_mexer_em_castigo(client: TestClient, admin: User, child: User) -> None:
    punishment = punish(client, child.id)
    headers = as_child(client)

    assert client.post(
        "/api/punishments",
        headers=headers,
        json={"child_id": child.id, "ends_at": iso(timedelta(days=1))},
    ).status_code == 403
    # O teste que mais importa: o filho não pode se soltar do castigo.
    assert client.patch(
        f"/api/punishments/{punishment['id']}", headers=headers, json={"end_now": True}
    ).status_code == 403
    assert client.delete(f"/api/punishments/{punishment['id']}", headers=headers).status_code == 403


def test_pai_ve_castigo_ativo_de_todos_os_filhos(
    client: TestClient, admin: User, child: User, other_child: User
) -> None:
    punish(client, child.id)
    punish(client, other_child.id)

    ativos = client.get("/api/punishments/current", headers=as_admin(client)).json()

    assert len(ativos) == 2


def test_filho_reage_ao_proprio_castigo(client: TestClient, admin: User, child: User) -> None:
    punishment = punish(client, child.id)

    response = client.patch(
        f"/api/punishments/{punishment['id']}/reaction",
        headers=as_child(client),
        json={"reaction_text": "😢 não foi justo"},
    )

    assert response.status_code == 200
    assert response.json()["reaction_text"] == "😢 não foi justo"
    assert response.json()["reaction_at"] is not None


def test_filho_apaga_a_propria_reacao(client: TestClient, admin: User, child: User) -> None:
    punishment = punish(client, child.id)
    headers = as_child(client)
    client.patch(
        f"/api/punishments/{punishment['id']}/reaction", headers=headers, json={"reaction_text": "😢"}
    )

    response = client.patch(
        f"/api/punishments/{punishment['id']}/reaction", headers=headers, json={"reaction_text": "   "}
    )

    assert response.status_code == 200
    assert response.json()["reaction_text"] is None
    assert response.json()["reaction_at"] is None


def test_filho_nao_reage_ao_castigo_do_irmao(
    client: TestClient, admin: User, child: User, other_child: User
) -> None:
    da_outra = punish(client, other_child.id)

    response = client.patch(
        f"/api/punishments/{da_outra['id']}/reaction",
        headers=as_child(client),
        json={"reaction_text": "😢"},
    )

    assert response.status_code == 404


def test_pai_nao_pode_reagir_a_castigo(client: TestClient, admin: User, child: User) -> None:
    punishment = punish(client, child.id)

    response = client.patch(
        f"/api/punishments/{punishment['id']}/reaction",
        headers=as_admin(client),
        json={"reaction_text": "😢"},
    )

    assert response.status_code == 403


def test_reacao_do_filho_aparece_pro_pai(client: TestClient, admin: User, child: User) -> None:
    punishment = punish(client, child.id)
    client.patch(
        f"/api/punishments/{punishment['id']}/reaction",
        headers=as_child(client),
        json={"reaction_text": "😠 injusto"},
    )

    response = client.get(f"/api/punishments/{punishment['id']}", headers=as_admin(client))

    assert response.json()["reaction_text"] == "😠 injusto"
