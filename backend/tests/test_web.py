from datetime import UTC, datetime, timedelta

from fastapi.testclient import TestClient
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models import (
    Category,
    Kind,
    Periodicity,
    Punishment,
    Role,
    SplashMessage,
    Task,
    TaskCompletion,
    Trombadice,
    User,
)
from app.periodo import chave_do_periodo, hoje_local
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


# --------------------------------------------------------------------------
# Categoria, filtros, calendário e relatório no painel
# --------------------------------------------------------------------------


def _cria_trombadice(client: TestClient, child_id: int, **campos) -> None:
    # O formulário manda as duas listas de categoria; o servidor lê só a do
    # tipo escolhido. Aqui vão as duas, como o navegador faz.
    if "category" in campos:
        campos["categoria_trombadice"] = campos.pop("category")
    dados = {
        "child_id": child_id,
        "occurred_at": "2026-08-02T11:30",
        "title": "algo",
        "kind": "trombadice",
        "categoria_trombadice": "outra",
        "categoria_conquista": "outra_boa",
        **campos,
    }
    resposta = client.post("/trombadices", data=dados, follow_redirects=False)
    assert resposta.status_code == 303, resposta.text


def test_categoria_escolhida_e_gravada(
    client: TestClient, db: Session, admin: User, child: User
) -> None:
    login_web(client, "pai", ADMIN_PASSWORD)

    _cria_trombadice(client, child.id, title="Empurrou", category="agressao")

    assert db.scalars(select(Trombadice)).one().category is Category.AGRESSAO


def test_categoria_invalida_vira_outra(
    client: TestClient, db: Session, admin: User, child: User
) -> None:
    login_web(client, "pai", ADMIN_PASSWORD)

    _cria_trombadice(client, child.id, category="coisa-inventada")

    # Nada de 500 e nada de gravar lixo: cai no valor neutro.
    assert db.scalars(select(Trombadice)).one().category is Category.OUTRA


def test_com_tarefa_o_titulo_e_o_filho_vem_da_tarefa(
    client: TestClient, db: Session, admin: User, child: User, other_child: User
) -> None:
    login_web(client, "pai", ADMIN_PASSWORD)
    client.post(
        "/tarefas",
        data={"name": "Arrumar a cama", "child_id": child.id, "periodicity": "daily"},
    )
    tarefa = db.scalars(select(Task)).one()

    # O formulário manda o outro filho no campo escondido; a tarefa tem que
    # ganhar, senão o registro afirmaria uma coisa falsa.
    _cria_trombadice(client, other_child.id, title="", task_id=str(tarefa.id))

    registrada = db.scalars(select(Trombadice)).one()
    assert registrada.title == "Arrumar a cama"
    assert registrada.child_id == child.id


def test_sem_titulo_e_sem_tarefa_nao_grava_nada(
    client: TestClient, db: Session, admin: User, child: User
) -> None:
    login_web(client, "pai", ADMIN_PASSWORD)

    _cria_trombadice(client, child.id, title="   ")

    assert db.scalars(select(Trombadice)).all() == []


def test_filtro_de_categoria_no_painel(
    client: TestClient, db: Session, admin: User, child: User
) -> None:
    login_web(client, "pai", ADMIN_PASSWORD)
    _cria_trombadice(client, child.id, title="Empurrou", category="agressao")
    _cria_trombadice(client, child.id, title="Mentiu", category="mentira")

    corpo = client.get("/trombadices?category=agressao").text

    assert "Empurrou" in corpo
    assert "Mentiu" not in corpo


def test_busca_por_palavra_no_painel(
    client: TestClient, db: Session, admin: User, child: User
) -> None:
    login_web(client, "pai", ADMIN_PASSWORD)
    _cria_trombadice(client, child.id, title="Bagunca na sala")
    _cria_trombadice(client, child.id, title="Nada a ver")

    corpo = client.get("/trombadices?q=sala").text

    assert "Bagunca na sala" in corpo
    assert "Nada a ver" not in corpo


def test_calendario_so_deixa_clicar_em_dia_com_registro(
    client: TestClient, db: Session, admin: User, child: User
) -> None:
    login_web(client, "pai", ADMIN_PASSWORD)
    _cria_trombadice(client, child.id, occurred_at="2026-08-05T10:00")

    corpo = client.get("/trombadices?mes=2026-08").text

    # O dia 5 vira link; o 6, que não tem nada, não.
    assert "dia=2026-08-05" in corpo
    assert "dia=2026-08-06" not in corpo


def test_calendario_respeita_o_filtro_de_categoria(
    client: TestClient, db: Session, admin: User, child: User
) -> None:
    login_web(client, "pai", ADMIN_PASSWORD)
    _cria_trombadice(client, child.id, occurred_at="2026-08-05T10:00", category="agressao")
    _cria_trombadice(client, child.id, occurred_at="2026-08-07T10:00", category="mentira")

    corpo = client.get("/trombadices?mes=2026-08&category=agressao").text

    # Acender um dia sem nenhuma agressão seria mentira.
    assert "dia=2026-08-05" in corpo
    assert "dia=2026-08-07" not in corpo


def test_filtrar_por_dia_mostra_so_aquele_dia(
    client: TestClient, db: Session, admin: User, child: User
) -> None:
    login_web(client, "pai", ADMIN_PASSWORD)
    _cria_trombadice(client, child.id, title="Do dia 5", occurred_at="2026-08-05T10:00")
    _cria_trombadice(client, child.id, title="Do dia 7", occurred_at="2026-08-07T10:00")

    corpo = client.get("/trombadices?dia=2026-08-05").text

    assert "Do dia 5" in corpo
    assert "Do dia 7" not in corpo


def test_painel_mostra_se_o_filho_ja_viu(
    client: TestClient, db: Session, admin: User, child: User
) -> None:
    login_web(client, "pai", ADMIN_PASSWORD)
    _cria_trombadice(client, child.id)

    assert "ainda não viu" in client.get("/trombadices").text


def test_relatorio_e_so_do_pai(client: TestClient, admin: User, child: User) -> None:
    resposta = client.get("/relatorio", follow_redirects=False)

    assert resposta.status_code == 303
    assert resposta.headers["location"] == "/login"


def test_relatorio_conta_o_que_foi_cadastrado(
    client: TestClient, db: Session, admin: User, child: User
) -> None:
    login_web(client, "pai", ADMIN_PASSWORD)
    hoje = datetime.now().astimezone().strftime("%Y-%m-%dT%H:%M")
    _cria_trombadice(client, child.id, occurred_at=hoje, category="mentira")

    corpo = client.get("/relatorio?dias=7").text

    assert "Mentira" in corpo
    assert "Relatório" in corpo


def test_relatorio_ignora_janela_inventada(
    client: TestClient, admin: User, child: User
) -> None:
    login_web(client, "pai", ADMIN_PASSWORD)

    # Cai na janela padrão em vez de aceitar qualquer número da barra de
    # endereços.
    assert client.get("/relatorio?dias=99999").status_code == 200


def test_painel_cadastra_conquista(
    client: TestClient, db: Session, admin: User, child: User
) -> None:
    login_web(client, "pai", ADMIN_PASSWORD)

    _cria_trombadice(
        client,
        child.id,
        title="Ajudou a arrumar a casa",
        kind="conquista",
        categoria_conquista="ajudou",
    )

    registrada = db.scalars(select(Trombadice)).one()
    assert registrada.kind is Kind.CONQUISTA
    assert registrada.category is Category.AJUDOU


def test_a_lista_do_tipo_errado_e_ignorada(
    client: TestClient, db: Session, admin: User, child: User
) -> None:
    login_web(client, "pai", ADMIN_PASSWORD)

    # O navegador manda as duas listas; a do tipo que não foi escolhido não
    # pode vazar para o registro.
    _cria_trombadice(
        client,
        child.id,
        kind="conquista",
        categoria_trombadice="agressao",
        categoria_conquista="gentileza",
    )

    assert db.scalars(select(Trombadice)).one().category is Category.GENTILEZA


def test_conquista_pelo_painel_ignora_a_tarefa(
    client: TestClient, db: Session, admin: User, child: User
) -> None:
    login_web(client, "pai", ADMIN_PASSWORD)
    client.post(
        "/tarefas",
        data={"name": "Arrumar a cama", "child_id": child.id, "periodicity": "daily"},
    )
    tarefa = db.scalars(select(Task)).one()

    _cria_trombadice(client, child.id, kind="conquista", task_id=str(tarefa.id))

    # Tarefa registra o que **não** foi cumprido; numa conquista o vínculo diria
    # o contrário. O campo nem aparece na tela, mas o servidor não confia nisso.
    assert db.scalars(select(Trombadice)).one().task_id is None


def test_filtro_de_tipo_no_painel(
    client: TestClient, db: Session, admin: User, child: User
) -> None:
    login_web(client, "pai", ADMIN_PASSWORD)
    _cria_trombadice(client, child.id, title="Empurrou", category="agressao")
    _cria_trombadice(client, child.id, title="Ajudou muito", kind="conquista")

    corpo = client.get("/trombadices?kind=conquista").text

    assert "Ajudou muito" in corpo
    assert "Empurrou" not in corpo
    # Com conquistas na tela, oferecer "falta de respeito" como **filtro** seria
    # oferecer um filtro que nunca acha nada. (O formulário de cadastro lá em
    # cima continua com as duas listas, e é assim que tem que ser.)
    assert "category=ajudou" in corpo
    assert "category=agressao" not in corpo


def test_tipo_nao_muda_na_edicao_pelo_painel(
    client: TestClient, db: Session, admin: User, child: User
) -> None:
    login_web(client, "pai", ADMIN_PASSWORD)
    _cria_trombadice(client, child.id, kind="conquista")
    registrada = db.scalars(select(Trombadice)).one()

    client.post(
        f"/trombadices/{registrada.id}/editar",
        data={
            "child_id": child.id,
            "occurred_at": "2026-08-02T11:30",
            "title": "Ajudou mesmo",
            "kind": "trombadice",
            "categoria_trombadice": "mentira",
            "categoria_conquista": "ajudou",
        },
    )

    db.refresh(registrada)
    assert registrada.kind is Kind.CONQUISTA
    assert registrada.category is Category.AJUDOU


def test_painel_mostra_se_a_tarefa_ja_foi_feita_no_periodo(
    client: TestClient, db: Session, admin: User, child: User
) -> None:
    login_web(client, "pai", ADMIN_PASSWORD)
    client.post("/tarefas", data={"name": "Arrumar a cama", "child_id": child.id, "periodicity": "daily"})
    tarefa = db.scalars(select(Task)).one()

    pendente = client.get("/tarefas").text
    assert "ainda não fez hoje" in pendente

    db.add(
        TaskCompletion(
            task_id=tarefa.id,
            child_id=child.id,
            note="antes do café",
            period_key=chave_do_periodo(Periodicity.DAILY, hoje_local()),
            completed_at=datetime.now(UTC),
        )
    )
    db.commit()
    # O teste divide a sessão com o app (e ela não expira no commit), então a
    # tarefa continuaria com a lista de conclusões que já tinha em memória. Em
    # produção cada requisição abre a sua.
    db.expire_all()

    feito = client.get("/tarefas").text
    assert "feito hoje" in feito
    assert "antes do café" in feito


def test_pai_desmarca_um_feito_pelo_painel(
    client: TestClient, db: Session, admin: User, child: User
) -> None:
    """Mesma correção que o filho faz pelo app - o pai precisa dela nos dois
    lugares, e sem limite de período."""
    login_web(client, "pai", ADMIN_PASSWORD)
    client.post("/tarefas", data={"name": "Arrumar a cama", "child_id": child.id, "periodicity": "daily"})
    tarefa = db.scalars(select(Task)).one()
    conclusao = TaskCompletion(
        task_id=tarefa.id,
        child_id=child.id,
        note="semana passada",
        period_key=chave_do_periodo(Periodicity.DAILY, hoje_local() - timedelta(days=7)),
        completed_at=datetime.now(UTC) - timedelta(days=7),
    )
    db.add(conclusao)
    db.commit()

    response = client.post(
        f"/tarefas/{tarefa.id}/conclusoes/{conclusao.id}/apagar", follow_redirects=False
    )

    assert response.status_code == 303
    assert db.scalars(select(TaskCompletion)).all() == []


def test_conclusao_de_outra_tarefa_nao_se_apaga_pelo_id(
    client: TestClient, db: Session, admin: User, child: User
) -> None:
    login_web(client, "pai", ADMIN_PASSWORD)
    client.post("/tarefas", data={"name": "Arrumar a cama", "child_id": child.id, "periodicity": "daily"})
    client.post("/tarefas", data={"name": "Levar o lixo", "child_id": child.id, "periodicity": "daily"})
    uma, outra = db.scalars(select(Task).order_by(Task.id)).all()
    conclusao = TaskCompletion(
        task_id=uma.id,
        child_id=child.id,
        note="",
        period_key=chave_do_periodo(Periodicity.DAILY, hoje_local()),
        completed_at=datetime.now(UTC),
    )
    db.add(conclusao)
    db.commit()

    client.post(f"/tarefas/{outra.id}/conclusoes/{conclusao.id}/apagar", follow_redirects=False)

    assert db.scalars(select(TaskCompletion)).one().id == conclusao.id
