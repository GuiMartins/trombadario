from fastapi.testclient import TestClient
from sqlalchemy.orm import Session

from app.models import Punishment, Role, User
from app.security import create_access_token
from app.web.deps import SESSION_COOKIE
from tests.conftest import ADMIN_PASSWORD, CHILD_PASSWORD

PAGINAS_ADMIN = ["/", "/trombadices", "/tarefas", "/castigos", "/frases", "/contas"]


def login_web(client: TestClient, username: str, password: str):
    return client.post(
        "/login", data={"username": username, "password": password}, follow_redirects=False
    )


def test_servidor_virgem_manda_todo_mundo_pro_setup(client: TestClient) -> None:
    for path in [*PAGINAS_ADMIN, "/login"]:
        response = client.get(path, follow_redirects=False)
        assert response.status_code == 303, path
        assert response.headers["location"] == "/setup", path


def test_setup_cria_a_conta_e_some(client: TestClient, db: Session) -> None:
    response = client.post(
        "/setup",
        data={
            "username": "pai",
            "display_name": "Pai",
            "password": "senha-do-pai",
            "password_confirm": "senha-do-pai",
        },
        follow_redirects=False,
    )

    assert response.status_code == 303
    assert response.headers["location"] == "/login"
    assert db.query(User).filter(User.role == Role.ADMIN).count() == 1
    # Depois de configurado, a tela de setup não existe mais.
    assert client.get("/setup", follow_redirects=False).headers["location"] == "/login"


def test_setup_recusa_senhas_diferentes(client: TestClient, db: Session) -> None:
    response = client.post(
        "/setup",
        data={
            "username": "pai",
            "display_name": "Pai",
            "password": "senha-do-pai",
            "password_confirm": "outra-coisa",
        },
    )

    assert "As senhas não conferem" in response.text
    assert db.query(User).count() == 0


def test_pai_loga_e_recebe_cookie_httponly(client: TestClient, admin: User) -> None:
    response = login_web(client, "pai", ADMIN_PASSWORD)

    assert response.status_code == 303
    assert response.headers["location"] == "/"
    cookie = response.headers["set-cookie"]
    # HttpOnly pra JavaScript nenhum conseguir ler o token; SameSite=Strict é a
    # defesa de CSRF deste painel (ver CLAUDE.md).
    assert "httponly" in cookie.lower()
    assert "samesite=strict" in cookie.lower()


def test_sem_sessao_toda_pagina_manda_pro_login(client: TestClient, admin: User) -> None:
    for path in PAGINAS_ADMIN:
        response = client.get(path, follow_redirects=False)
        assert response.status_code == 303, path
        assert response.headers["location"] == "/login", path


def test_pai_abre_todas_as_paginas(client: TestClient, admin: User) -> None:
    login_web(client, "pai", ADMIN_PASSWORD)

    for path in PAGINAS_ADMIN:
        assert client.get(path).status_code == 200, path


def test_filho_nao_loga_no_painel_web(client: TestClient, admin: User, child: User) -> None:
    response = login_web(client, "filho", CHILD_PASSWORD)

    # Sem cookie e sem redirect: o navegador printa à vontade, então deixar o
    # filho entrar aqui anularia o FLAG_SECURE do app.
    assert response.status_code == 200
    assert "use o aplicativo no celular" in response.text
    assert SESSION_COOKIE not in response.headers.get("set-cookie", "")


def test_filho_com_token_valido_ainda_leva_403_no_web(
    client: TestClient, admin: User, child: User
) -> None:
    # O token é legítimo - o filho consegue um pela API, que ele precisa usar.
    # O que não pode é ele valer no painel.
    token = create_access_token(child.username, child.role.value)
    client.cookies.set(SESSION_COOKIE, token)

    for path in PAGINAS_ADMIN:
        assert client.get(path).status_code == 403, path


def test_api_do_filho_continua_funcionando(client: TestClient, admin: User, child: User) -> None:
    token = create_access_token(child.username, child.role.value)

    response = client.get("/api/trombadices", headers={"Authorization": f"Bearer {token}"})

    assert response.status_code == 200


def test_logout_apaga_a_sessao(client: TestClient, admin: User) -> None:
    login_web(client, "pai", ADMIN_PASSWORD)
    assert client.get("/").status_code == 200

    client.post("/logout", follow_redirects=False)

    assert client.get("/", follow_redirects=False).headers["location"] == "/login"


def test_pai_nao_consegue_desativar_a_propria_conta(client: TestClient, admin: User) -> None:
    login_web(client, "pai", ADMIN_PASSWORD)

    client.post(f"/contas/{admin.id}/toggle", follow_redirects=False)

    # Desativar a conta que está logada trancaria todo mundo do lado de fora,
    # sem volta que não fosse SQL na mão.
    assert admin.is_active is True


def test_castigo_ignora_trombadice_de_outro_filho(
    client: TestClient, db: Session, admin: User, child: User, other_child: User
) -> None:
    login_web(client, "pai", ADMIN_PASSWORD)
    da_outra = client.post(
        "/api/trombadices",
        headers={"Authorization": f"Bearer {create_access_token('pai', 'admin')}"},
        json={
            "title": "Da filha",
            "occurred_at": "2026-08-01T14:30:00+00:00",
            "child_id": other_child.id,
        },
    ).json()

    client.post(
        "/castigos",
        data={
            "child_id": child.id,
            "ends_at": "2026-12-31T23:59",
            "reason": "x",
            "trombadice_ids": [da_outra["id"]],
        },
        follow_redirects=False,
    )

    # A trombadice da irmã foi descartada em silêncio: o castigo existe, mas sem
    # o vínculo que colocaria o nome dela no registro do irmão.
    punishment = db.query(Punishment).one()
    assert punishment.child_id == child.id
    assert punishment.trombadices == []
