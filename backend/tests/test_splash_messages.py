from fastapi.testclient import TestClient

from app.models import User
from tests.conftest import as_admin, as_child


def create(client: TestClient, text: str, child_id: int | None = None) -> dict:
    response = client.post(
        "/api/splash-messages",
        headers=as_admin(client),
        json={"text": text, "child_id": child_id},
    )
    assert response.status_code == 201, response.text
    return response.json()


def test_frase_generica_nao_tem_dono(client: TestClient, admin: User) -> None:
    frase = create(client, "Bom dia!")

    assert frase["child_id"] is None
    assert frase["is_active"] is True


def test_frase_pode_ser_de_um_filho_so(client: TestClient, admin: User, child: User) -> None:
    frase = create(client, "Arruma essa cama, João", child_id=child.id)

    assert frase["child_id"] == child.id


def test_nao_endereca_frase_a_quem_nao_e_filho(client: TestClient, admin: User) -> None:
    response = client.post(
        "/api/splash-messages",
        headers=as_admin(client),
        json={"text": "x", "child_id": admin.id},
    )

    assert response.status_code == 400


def test_filho_sorteia_generica_e_a_dele(
    client: TestClient, admin: User, child: User, other_child: User
) -> None:
    create(client, "Generica")
    create(client, "Do filho", child_id=child.id)
    create(client, "Da filha", child_id=other_child.id)

    # Sorteio, então repete pra cobrir as possibilidades - o que importa é o
    # conjunto do qual ele pode sair, não qual saiu.
    sorteadas = {
        client.get("/api/splash-messages/random", headers=as_child(client)).json()["text"]
        for _ in range(40)
    }

    assert sorteadas <= {"Generica", "Do filho"}
    assert "Da filha" not in sorteadas


def test_sem_frase_nenhuma_devolve_nulo(client: TestClient, admin: User, child: User) -> None:
    response = client.get("/api/splash-messages/random", headers=as_child(client))

    # O app usa isso pra pular a tela em vez de mostrar um vazio.
    assert response.status_code == 200
    assert response.json()["text"] is None


def test_frase_desativada_nao_e_sorteada(client: TestClient, admin: User, child: User) -> None:
    frase = create(client, "Pausada")
    client.patch(
        f"/api/splash-messages/{frase['id']}",
        headers=as_admin(client),
        json={"is_active": False},
    )

    response = client.get("/api/splash-messages/random", headers=as_child(client))

    assert response.json()["text"] is None


def test_filho_nao_ve_a_lista_inteira(
    client: TestClient, admin: User, child: User, other_child: User
) -> None:
    create(client, "Da filha", child_id=other_child.id)

    # A rota de listagem é só do admin: pelo /random o filho recebe uma frase,
    # nunca o conjunto - senão leria o que foi escrito pro irmão.
    assert client.get("/api/splash-messages", headers=as_child(client)).status_code == 403


def test_filho_nao_pode_cadastrar_nem_apagar(client: TestClient, admin: User, child: User) -> None:
    frase = create(client, "Generica")
    headers = as_child(client)

    assert client.post(
        "/api/splash-messages", headers=headers, json={"text": "minha frase"}
    ).status_code == 403
    assert client.patch(
        f"/api/splash-messages/{frase['id']}", headers=headers, json={"text": "outra"}
    ).status_code == 403
    assert client.delete(f"/api/splash-messages/{frase['id']}", headers=headers).status_code == 403


def test_apagar_filho_leva_as_frases_dele_mas_nao_as_genericas(
    client: TestClient, admin: User, child: User
) -> None:
    create(client, "Generica")
    create(client, "Do filho", child_id=child.id)

    assert client.delete(f"/api/users/{child.id}", headers=as_admin(client)).status_code == 204

    restantes = client.get("/api/splash-messages", headers=as_admin(client)).json()
    assert [f["text"] for f in restantes] == ["Generica"]
