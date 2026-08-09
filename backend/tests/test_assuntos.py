"""Assuntos pra conversar - a única coisa do app que os dois lados cadastram.

Por isso quase todo teste aqui existe em par: o mesmo fluxo pelo pai e pelo
filho. O que **não** é simétrico (quem encerra, quem apaga, quem "vê") é o que
esses testes travam.
"""

from fastapi.testclient import TestClient

from app.models import User
from tests.conftest import as_admin, as_child, auth_header


def do_filho(client: TestClient, title: str = "Queria falar do celular", **extra) -> dict:
    response = client.post(
        "/api/assuntos", headers=as_child(client), json={"title": title, **extra}
    )
    assert response.status_code == 201, response.text
    return response.json()


def do_pai(client: TestClient, child_id: int, title: str = "O jeito de falar", **extra) -> dict:
    response = client.post(
        "/api/assuntos",
        headers=as_admin(client),
        json={"title": title, "child_id": child_id, **extra},
    )
    assert response.status_code == 201, response.text
    return response.json()


def test_filho_levanta_um_assunto(client: TestClient, admin: User, child: User) -> None:
    assunto = do_filho(client, "Queria falar do celular", description="acho que mereço mais tempo")

    assert assunto["title"] == "Queria falar do celular"
    assert assunto["description"] == "acho que mereço mais tempo"
    assert assunto["child_id"] == child.id
    assert assunto["author_id"] == child.id
    assert assunto["by_child"] is True
    assert assunto["talked_at"] is None


def test_pai_levanta_um_assunto(client: TestClient, admin: User, child: User) -> None:
    assunto = do_pai(client, child.id)

    assert assunto["child_id"] == child.id
    assert assunto["author_id"] == admin.id
    assert assunto["by_child"] is False


def test_pai_precisa_dizer_de_quem_e_o_assunto(
    client: TestClient, admin: User, child: User
) -> None:
    """Sem filho escolhido não dá pra saber com quem é a conversa - e chutar o
    único filho cadastrado quebraria calado no dia em que aparecesse o segundo."""
    response = client.post("/api/assuntos", headers=as_admin(client), json={"title": "Conversar"})

    assert response.status_code == 400


def test_filho_nao_levanta_assunto_pra_irmao(
    client: TestClient, admin: User, child: User, other_child: User
) -> None:
    """O child_id que o filho manda é ignorado, nunca respeitado."""
    assunto = do_filho(client, child_id=other_child.id)

    assert assunto["child_id"] == child.id


def test_filho_nao_ve_assunto_do_irmao(
    client: TestClient, admin: User, child: User, other_child: User
) -> None:
    da_outra = client.post(
        "/api/assuntos",
        headers=auth_header(client, "filha", "senha-da-filha"),
        json={"title": "Coisa da Maria"},
    ).json()

    assert client.get("/api/assuntos", headers=as_child(client)).json() == []
    assert client.get(
        f"/api/assuntos/{da_outra['id']}", headers=as_child(client)
    ).status_code == 404


def test_os_dois_leem_a_mesma_lista(client: TestClient, admin: User, child: User) -> None:
    """O que o filho traz e o que o pai traz vivem juntos: é uma pauta só."""
    do_filho(client)
    do_pai(client, child.id)

    assert len(client.get("/api/assuntos", headers=as_child(client)).json()) == 2
    assert len(client.get("/api/assuntos", headers=as_admin(client)).json()) == 2


def test_pai_marca_como_conversado(client: TestClient, admin: User, child: User) -> None:
    assunto = do_filho(client)

    response = client.patch(
        f"/api/assuntos/{assunto['id']}/conversado",
        headers=as_admin(client),
        json={"note": "combinamos meia hora por dia"},
    )

    assert response.status_code == 200
    assert response.json()["talked_at"] is not None
    assert response.json()["talked_by_id"] == admin.id
    assert response.json()["talked_note"] == "combinamos meia hora por dia"


def test_pai_desmarca_o_que_marcou_sem_querer(client: TestClient, admin: User, child: User) -> None:
    """Mesmo motivo de desmarcar tarefa: um toque errado não pode encerrar pra
    sempre uma conversa que não aconteceu."""
    assunto = do_filho(client)
    client.patch(f"/api/assuntos/{assunto['id']}/conversado", headers=as_admin(client), json={})

    response = client.patch(
        f"/api/assuntos/{assunto['id']}/conversado",
        headers=as_admin(client),
        json={"talked": False},
    )

    assert response.status_code == 200
    assert response.json()["talked_at"] is None
    assert response.json()["talked_by_id"] is None
    assert response.json()["talked_note"] == ""


def test_filho_nao_marca_como_conversado(client: TestClient, admin: User, child: User) -> None:
    """Riscar da lista o assunto que o pai levantou apagaria a pauta do outro
    sem que ninguém tivesse conversado."""
    assunto = do_pai(client, child.id)

    response = client.patch(
        f"/api/assuntos/{assunto['id']}/conversado", headers=as_child(client), json={}
    )

    assert response.status_code == 403


def test_so_o_autor_corrige_o_que_escreveu(client: TestClient, admin: User, child: User) -> None:
    do_pai_ = do_pai(client, child.id)
    do_filho_ = do_filho(client)

    assert client.patch(
        f"/api/assuntos/{do_pai_['id']}", headers=as_admin(client), json={"title": "Corrigido"}
    ).status_code == 200
    # Nem o pai reescreve a pauta do filho: mudar o texto é mudar o que ele
    # quis dizer.
    assert client.patch(
        f"/api/assuntos/{do_filho_['id']}", headers=as_admin(client), json={"title": "Outra coisa"}
    ).status_code == 403
    assert client.patch(
        f"/api/assuntos/{do_pai_['id']}", headers=as_child(client), json={"title": "Outra coisa"}
    ).status_code == 403


def test_assunto_conversado_nao_se_reescreve(client: TestClient, admin: User, child: User) -> None:
    assunto = do_pai(client, child.id)
    client.patch(f"/api/assuntos/{assunto['id']}/conversado", headers=as_admin(client), json={})

    response = client.patch(
        f"/api/assuntos/{assunto['id']}", headers=as_admin(client), json={"title": "Outra pauta"}
    )

    assert response.status_code == 400


def test_filho_desiste_do_proprio_assunto(client: TestClient, admin: User, child: User) -> None:
    assunto = do_filho(client)

    assert client.delete(f"/api/assuntos/{assunto['id']}", headers=as_child(client)).status_code == 204
    assert client.get("/api/assuntos", headers=as_child(client)).json() == []


def test_filho_nao_apaga_o_que_ja_foi_conversado(
    client: TestClient, admin: User, child: User
) -> None:
    """Desistir antes da conversa é mudar de ideia; apagar depois é sumir com o
    que já foi tratado."""
    assunto = do_filho(client)
    client.patch(f"/api/assuntos/{assunto['id']}/conversado", headers=as_admin(client), json={})

    assert client.delete(f"/api/assuntos/{assunto['id']}", headers=as_child(client)).status_code == 403


def test_filho_nao_apaga_assunto_que_o_pai_levantou(
    client: TestClient, admin: User, child: User
) -> None:
    assunto = do_pai(client, child.id)

    assert client.delete(f"/api/assuntos/{assunto['id']}", headers=as_child(client)).status_code == 404


def test_pai_apaga_qualquer_um(client: TestClient, admin: User, child: User) -> None:
    assunto = do_filho(client)

    assert client.delete(f"/api/assuntos/{assunto['id']}", headers=as_admin(client)).status_code == 204


def test_filtro_de_pendentes(client: TestClient, admin: User, child: User) -> None:
    conversado = do_filho(client, "Já resolvido")
    do_filho(client, "Ainda não")
    client.patch(f"/api/assuntos/{conversado['id']}/conversado", headers=as_admin(client), json={})

    pendentes = client.get("/api/assuntos?pendentes=true", headers=as_admin(client)).json()
    resolvidos = client.get("/api/assuntos?pendentes=false", headers=as_admin(client)).json()

    assert [a["title"] for a in pendentes] == ["Ainda não"]
    assert [a["title"] for a in resolvidos] == ["Já resolvido"]


# --------------------------------------------------------------------------
# can_discuss: o pai desliga o acesso do filho
# --------------------------------------------------------------------------


def bloquear(client: TestClient, child: User) -> None:
    response = client.patch(
        f"/api/users/{child.id}", headers=as_admin(client), json={"can_discuss": False}
    )
    assert response.status_code == 200, response.text


def test_filho_bloqueado_nao_ve_nem_cadastra(client: TestClient, admin: User, child: User) -> None:
    """Desligar tira a funcionalidade inteira, não só o botão de criar: meio
    acesso (ler sem poder responder) seria pior que nenhum."""
    assunto = do_pai(client, child.id)
    bloquear(client, child)

    assert client.get("/api/assuntos", headers=as_child(client)).status_code == 403
    assert client.get(f"/api/assuntos/{assunto['id']}", headers=as_child(client)).status_code == 403
    assert client.post(
        "/api/assuntos", headers=as_child(client), json={"title": "Deixa eu falar"}
    ).status_code == 403
    assert client.delete(f"/api/assuntos/{assunto['id']}", headers=as_child(client)).status_code == 403


def test_bloquear_o_filho_nao_bloqueia_o_pai(client: TestClient, admin: User, child: User) -> None:
    do_filho(client)
    bloquear(client, child)

    resposta = client.get("/api/assuntos", headers=as_admin(client))
    assert resposta.status_code == 200
    assert len(resposta.json()) == 1


def test_bloquear_nao_apaga_nada(client: TestClient, admin: User, child: User) -> None:
    """O que já foi conversado continua registrado - desligar o acesso não é
    apagar a história, e religar tem que devolver a lista inteira."""
    do_filho(client)
    bloquear(client, child)

    client.patch(f"/api/users/{child.id}", headers=as_admin(client), json={"can_discuss": True})

    assert len(client.get("/api/assuntos", headers=as_child(client)).json()) == 1


# --------------------------------------------------------------------------
# Visto: quem vê é sempre quem não escreveu
# --------------------------------------------------------------------------


def test_pai_lendo_marca_so_o_que_o_filho_trouxe(
    client: TestClient, admin: User, child: User
) -> None:
    do_filho(client, "Do filho")
    do_pai(client, child.id, "Do pai")

    por_titulo = {a["title"]: a for a in client.get("/api/assuntos", headers=as_admin(client)).json()}

    assert por_titulo["Do filho"]["seen_by_parent_at"] is not None
    # O pai relendo o próprio assunto não pode fazer "o filho ainda não viu"
    # virar mentira sem ninguém ter aberto o app.
    assert por_titulo["Do pai"]["seen_by_child_at"] is None
    assert por_titulo["Do pai"]["seen_by_parent_at"] is None


def test_filho_lendo_marca_so_o_que_o_pai_trouxe(
    client: TestClient, admin: User, child: User
) -> None:
    do_filho(client, "Do filho")
    do_pai(client, child.id, "Do pai")

    por_titulo = {a["title"]: a for a in client.get("/api/assuntos", headers=as_child(client)).json()}

    assert por_titulo["Do pai"]["seen_by_child_at"] is not None
    assert por_titulo["Do filho"]["seen_by_child_at"] is None


def test_visto_e_idempotente(client: TestClient, admin: User, child: User) -> None:
    """Primeira vez é que vale - senão "visto às 20h" viraria a hora da última
    olhada. Mesma regra do seen_at de trombadice."""
    do_pai(client, child.id)

    primeira = client.get("/api/assuntos", headers=as_child(client)).json()[0]["seen_by_child_at"]
    segunda = client.get("/api/assuntos", headers=as_child(client)).json()[0]["seen_by_child_at"]

    assert primeira == segunda


def test_unseen_conta_dos_dois_lados(client: TestClient, admin: User, child: User) -> None:
    do_filho(client, "Do filho")
    do_pai(client, child.id, "Do pai")

    assert client.get("/api/unseen", headers=as_admin(client)).json()["assuntos_novos"] == 1
    assert client.get("/api/unseen", headers=as_child(client)).json()["assuntos_novos"] == 1

    # E abrir a tela de verdade zera - /api/unseen sozinho não marca nada.
    client.get("/api/assuntos", headers=as_admin(client))
    assert client.get("/api/unseen", headers=as_admin(client)).json()["assuntos_novos"] == 0
    assert client.get("/api/unseen", headers=as_child(client)).json()["assuntos_novos"] == 1


def test_unseen_do_filho_bloqueado_e_zero(client: TestClient, admin: User, child: User) -> None:
    """Notificar sobre uma tela que a criança não consegue abrir só produziria
    um aviso sem destino."""
    do_pai(client, child.id)
    bloquear(client, child)

    assert client.get("/api/unseen", headers=as_child(client)).json()["assuntos_novos"] == 0
