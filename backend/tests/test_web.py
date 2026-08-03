from datetime import UTC, datetime, timedelta

from fastapi.testclient import TestClient
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models import Periodicity, Punishment, Role, SplashMessage, Task, Trombadice, User
from app.security import create_access_token
from app.web.deps import SESSION_COOKIE
from tests.conftest import ADMIN_PASSWORD, CHILD_PASSWORD, make_user

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


# --------------------------------------------------------------------------
# Tema claro / escuro
# --------------------------------------------------------------------------


def test_tema_escolhido_vira_atributo_no_html(client: TestClient, admin: User) -> None:
    login_web(client, "pai", ADMIN_PASSWORD)

    client.post("/tema", data={"valor": "escuro", "next": "/trombadices"})

    assert 'data-tema="escuro"' in client.get("/trombadices").text


def test_tema_sistema_nao_escreve_atributo(client: TestClient, admin: User) -> None:
    login_web(client, "pai", ADMIN_PASSWORD)
    client.post("/tema", data={"valor": "escuro", "next": "/"})

    client.post("/tema", data={"valor": "sistema", "next": "/"})

    # Sem atributo, quem manda é o prefers-color-scheme do sistema - inclusive
    # quando ele troca sozinho ao anoitecer.
    assert "data-tema" not in client.get("/").text


def test_tema_desconhecido_no_cookie_nao_entra_no_html(client: TestClient, admin: User) -> None:
    login_web(client, "pai", ADMIN_PASSWORD)
    client.cookies.set("trombadario_tema", '"><script>alert(1)</script>')

    corpo = client.get("/").text

    assert "data-tema" not in corpo
    assert "<script>alert" not in corpo


def test_tema_volta_pra_pagina_onde_estava(client: TestClient, admin: User) -> None:
    login_web(client, "pai", ADMIN_PASSWORD)

    response = client.post(
        "/tema", data={"valor": "claro", "next": "/tarefas"}, follow_redirects=False
    )

    assert response.headers["location"] == "/tarefas"


def test_tema_nao_redireciona_pra_fora_de_casa(client: TestClient, admin: User) -> None:
    login_web(client, "pai", ADMIN_PASSWORD)

    for fora in ["https://exemplo.com", "//exemplo.com", "javascript:alert(1)"]:
        response = client.post(
            "/tema", data={"valor": "claro", "next": fora}, follow_redirects=False
        )
        assert response.headers["location"] == "/", fora


def test_tema_preserva_o_filtro_da_pagina(client: TestClient, admin: User) -> None:
    login_web(client, "pai", ADMIN_PASSWORD)

    corpo = client.get("/trombadices?child_id=7").text

    # O botão de tema tem que voltar pra lista filtrada, não pra lista inteira.
    assert 'name="next" value="/trombadices?child_id=7"' in corpo


# --------------------------------------------------------------------------
# Editar o que já foi cadastrado (só o pai)
# --------------------------------------------------------------------------


def test_pai_corrige_o_texto_de_uma_trombadice(
    client: TestClient, db: Session, admin: User, child: User
) -> None:
    login_web(client, "pai", ADMIN_PASSWORD)
    client.post(
        "/trombadices",
        data={
            "title": "Machou a irma",
            "child_id": child.id,
            "occurred_at": "2026-08-02T11:30",
            "description": "no parquinho",
        },
    )
    registrada = db.scalars(select(Trombadice)).one()

    client.post(
        f"/trombadices/{registrada.id}/editar",
        data={
            "title": "Machucou a irmã",
            "child_id": child.id,
            "occurred_at": "2026-08-02T11:30",
            "description": "no parquinho",
        },
    )

    db.refresh(registrada)
    assert registrada.title == "Machucou a irmã"


def test_editar_nao_troca_quem_cadastrou(
    client: TestClient, db: Session, admin: User, child: User
) -> None:
    outro_pai = make_user(db, "mae", "senha-da-mae", Role.ADMIN, "Mãe")
    trombadice = Trombadice(
        title="errado",
        description="",
        occurred_at=datetime.now(UTC),
        child_id=child.id,
        author_id=outro_pai.id,
    )
    db.add(trombadice)
    db.commit()

    login_web(client, "pai", ADMIN_PASSWORD)
    client.post(
        f"/trombadices/{trombadice.id}/editar",
        data={
            "title": "certo",
            "child_id": child.id,
            "occurred_at": "2026-08-02T11:30",
        },
    )

    db.refresh(trombadice)
    assert trombadice.title == "certo"
    # Quem cadastrou continua sendo quem cadastrou - corrigir não é assumir.
    assert trombadice.author_id == outro_pai.id


def test_formulario_vem_preenchido_pra_editar(
    client: TestClient, db: Session, admin: User, child: User
) -> None:
    trombadice = Trombadice(
        title="Machou a irma",
        description="no parquinho",
        occurred_at=datetime.now(UTC),
        child_id=child.id,
        author_id=admin.id,
    )
    db.add(trombadice)
    db.commit()
    login_web(client, "pai", ADMIN_PASSWORD)

    corpo = client.get(f"/trombadices?editar={trombadice.id}").text

    assert 'value="Machou a irma"' in corpo
    assert f'action="/trombadices/{trombadice.id}/editar"' in corpo
    assert "Salvar correção" in corpo


def test_editar_tarefa_limpa_o_campo_que_a_nova_periodicidade_nao_usa(
    client: TestClient, db: Session, admin: User, child: User
) -> None:
    login_web(client, "pai", ADMIN_PASSWORD)
    client.post(
        "/tarefas",
        data={
            "name": "Arrumar a cama",
            "child_id": child.id,
            "periodicity": "weekly",
            "weekdays": ["0", "2"],
        },
    )
    tarefa = db.scalars(select(Task)).one()
    assert tarefa.weekdays == "0,2"

    client.post(
        f"/tarefas/{tarefa.id}/editar",
        data={"name": "Arrumar a cama", "child_id": child.id, "periodicity": "daily"},
    )

    db.refresh(tarefa)
    # Senão sobraria "segunda e quarta" numa tarefa que agora é de todo dia.
    assert tarefa.periodicity is Periodicity.DAILY
    assert tarefa.weekdays == ""


def test_editar_castigo_nao_mexe_em_quando_comecou(
    client: TestClient, db: Session, admin: User, child: User
) -> None:
    comeco = datetime.now(UTC) - timedelta(days=1)
    castigo = Punishment(
        reason="bagunça",
        starts_at=comeco,
        ends_at=datetime.now(UTC) + timedelta(days=1),
        child_id=child.id,
        author_id=admin.id,
    )
    db.add(castigo)
    db.commit()
    login_web(client, "pai", ADMIN_PASSWORD)

    client.post(
        f"/castigos/{castigo.id}/editar",
        data={"child_id": child.id, "ends_at": "2026-12-25T18:00", "reason": "bagunça grande"},
    )

    db.refresh(castigo)
    assert castigo.reason == "bagunça grande"
    # Quando começou é fato, não opinião.
    assert castigo.starts_at == comeco


def test_editar_conta_muda_o_nome_e_nao_o_login(
    client: TestClient, db: Session, admin: User, child: User
) -> None:
    login_web(client, "pai", ADMIN_PASSWORD)

    client.post(f"/contas/{child.id}/editar", data={"display_name": "João"})

    db.refresh(child)
    assert child.display_name == "João"
    assert child.username == "filho"


def test_editar_frase(client: TestClient, db: Session, admin: User, child: User) -> None:
    login_web(client, "pai", ADMIN_PASSWORD)
    client.post("/frases", data={"text": "Arruma essa cma", "child_id": ""})
    frase = db.scalars(select(SplashMessage)).one()

    client.post(f"/frases/{frase.id}/editar", data={"text": "Arruma essa cama", "child_id": ""})

    db.refresh(frase)
    assert frase.text == "Arruma essa cama"


def test_filho_nao_edita_nada(client: TestClient, db: Session, admin: User, child: User) -> None:
    trombadice = Trombadice(
        title="original",
        description="",
        occurred_at=datetime.now(UTC),
        child_id=child.id,
        author_id=admin.id,
    )
    db.add(trombadice)
    db.commit()

    # Sem sessão de pai, toda rota do painel manda pro login - inclusive as de
    # edição. O painel inteiro é admin-only por decisão de segurança.
    for rota, dados in [
        (f"/trombadices/{trombadice.id}/editar", {"title": "hackeado", "child_id": child.id,
                                                  "occurred_at": "2026-08-02T11:30"}),
        (f"/contas/{child.id}/editar", {"display_name": "hackeado"}),
    ]:
        response = client.post(rota, data=dados, follow_redirects=False)
        assert response.status_code == 303
        assert response.headers["location"] == "/login", rota

    db.refresh(trombadice)
    assert trombadice.title == "original"
