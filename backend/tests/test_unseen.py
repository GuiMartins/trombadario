from datetime import UTC, datetime, timedelta

from fastapi.testclient import TestClient

from app.models import User
from tests.conftest import as_admin, as_child, auth_header


def test_pai_ve_pedido_pendente_sem_efeito_colateral(
    client: TestClient, admin: User, child: User
) -> None:
    client.post("/api/pedidos", headers=as_child(client), json={"title": "Ir na casa do amigo"})

    primeira = client.get("/api/unseen", headers=as_admin(client))
    assert primeira.status_code == 200
    assert primeira.json()["pedidos_pendentes"] == 1

    # Chamar de novo não muda nada - /api/unseen não marca visto.
    segunda = client.get("/api/unseen", headers=as_admin(client))
    assert segunda.json()["pedidos_pendentes"] == 1


def test_visitar_pedidos_de_verdade_zera_a_contagem(
    client: TestClient, admin: User, child: User
) -> None:
    client.post("/api/pedidos", headers=as_child(client), json={"title": "Ir na casa do amigo"})
    assert client.get("/api/unseen", headers=as_admin(client)).json()["pedidos_pendentes"] == 1

    client.get("/api/pedidos", headers=as_admin(client))

    assert client.get("/api/unseen", headers=as_admin(client)).json()["pedidos_pendentes"] == 0


def test_filho_ve_anotacao_nova_sem_efeito_colateral(
    client: TestClient, admin: User, child: User
) -> None:
    client.post(
        "/api/trombadices",
        headers=as_admin(client),
        json={
            "title": "Machou a irmã",
            "occurred_at": datetime.now(UTC).isoformat(),
            "child_id": child.id,
        },
    )

    assert client.get("/api/unseen", headers=as_child(client)).json()["anotacoes_novas"] == 1
    assert client.get("/api/unseen", headers=as_child(client)).json()["anotacoes_novas"] == 1

    client.get("/api/trombadices", headers=as_child(client))

    assert client.get("/api/unseen", headers=as_child(client)).json()["anotacoes_novas"] == 0


def test_filho_ve_castigo_novo(client: TestClient, admin: User, child: User) -> None:
    client.post(
        "/api/punishments",
        headers=as_admin(client),
        json={
            "child_id": child.id,
            "ends_at": (datetime.now(UTC) + timedelta(days=1)).isoformat(),
        },
    )

    assert client.get("/api/unseen", headers=as_child(client)).json()["castigos_novos"] == 1

    client.get("/api/punishments", headers=as_child(client))

    assert client.get("/api/unseen", headers=as_child(client)).json()["castigos_novos"] == 0


def test_filho_ve_decisao_nova_so_depois_de_decidido(
    client: TestClient, admin: User, child: User
) -> None:
    pedido = client.post(
        "/api/pedidos", headers=as_child(client), json={"title": "Ir na casa do amigo"}
    ).json()

    # Ainda pendente - não conta como decisão nova.
    assert client.get("/api/unseen", headers=as_child(client)).json()["decisoes_novas"] == 0

    client.patch(
        f"/api/pedidos/{pedido['id']}/decidir",
        headers=as_admin(client),
        json={"status": "aprovado"},
    )

    assert client.get("/api/unseen", headers=as_child(client)).json()["decisoes_novas"] == 1

    client.get("/api/pedidos", headers=as_child(client))

    assert client.get("/api/unseen", headers=as_child(client)).json()["decisoes_novas"] == 0


def test_filho_nao_ve_novidade_do_irmao(
    client: TestClient, admin: User, child: User, other_child: User
) -> None:
    client.post(
        "/api/trombadices",
        headers=as_admin(client),
        json={
            "title": "Bagunça",
            "occurred_at": datetime.now(UTC).isoformat(),
            "child_id": other_child.id,
        },
    )

    assert client.get("/api/unseen", headers=as_child(client)).json()["anotacoes_novas"] == 0
    irma = auth_header(client, "filha", "senha-da-filha")
    assert client.get("/api/unseen", headers=irma).json()["anotacoes_novas"] == 1


def test_unseen_exige_autenticacao(client: TestClient) -> None:
    assert client.get("/api/unseen").status_code == 401
