"""O dia de aniversário do filho: quem cadastra, quem pergunta e quem decide.

O que este arquivo protege é a decisão de **onde** a resposta é calculada. O app
troca a interface inteira por uma tela de festa quando `/api/birthday` diz que é
hoje - se essa conta fosse feita no celular, adiantar a data do aparelho daria
festa em qualquer terça-feira, e o Trombadário sumiria no dia em que o pai mais
precisa dele.
"""

from datetime import date, timedelta

from fastapi.testclient import TestClient
from sqlalchemy.orm import Session

from app.models import User
from app.periodo import e_aniversario, hoje_local, idade_em
from app.security import create_access_token
from app.web.deps import SESSION_COOKIE
from tests.conftest import as_admin, as_child


def nasceu_em(db: Session, user: User, nascimento: date) -> None:
    user.birth_date = nascimento
    db.commit()


def aniversario_hoje(anos_atras: int = 10) -> date:
    """Uma data de nascimento cujo aniversário cai hoje, seja qual for o dia em
    que a suíte rodar. 29 de fevereiro nunca sai daqui - ele tem teste próprio,
    de unidade."""
    hoje = hoje_local()
    try:
        return hoje.replace(year=hoje.year - anos_atras)
    except ValueError:  # nasceu em 29/02 e este ano não tem 29
        return hoje.replace(year=hoje.year - anos_atras, day=28)


# --------------------------------------------------------------------------
# A regra, sem HTTP no meio
# --------------------------------------------------------------------------


def test_aniversario_e_dia_e_mes_nao_a_data_inteira() -> None:
    nascimento = date(2014, 3, 25)

    assert e_aniversario(nascimento, date(2026, 3, 25))
    assert not e_aniversario(nascimento, date(2026, 3, 24))
    assert not e_aniversario(nascimento, date(2026, 4, 25))
    # O próprio dia em que nasceu também é aniversário - zero ano.
    assert e_aniversario(nascimento, nascimento)


def test_quem_nasceu_em_29_de_fevereiro_faz_anos_todo_ano() -> None:
    nascimento = date(2012, 2, 29)

    # Ano bissexto: no dia dele mesmo, e não no 1º de março.
    assert e_aniversario(nascimento, date(2024, 2, 29))
    assert not e_aniversario(nascimento, date(2024, 3, 1))

    # Ano sem 29: cai no 1º de março. Sem isso a criança não faria aniversário
    # em três de cada quatro anos.
    assert e_aniversario(nascimento, date(2025, 3, 1))
    assert not e_aniversario(nascimento, date(2025, 2, 28))


def test_idade_conta_o_ano_so_depois_do_aniversario() -> None:
    nascimento = date(2014, 3, 25)

    assert idade_em(nascimento, date(2026, 3, 25)) == 12
    assert idade_em(nascimento, date(2026, 3, 24)) == 11
    assert idade_em(nascimento, date(2026, 3, 26)) == 12
    assert idade_em(nascimento, date(2026, 12, 31)) == 12


def test_idade_de_quem_nasceu_em_29_vira_no_dia_da_festa() -> None:
    nascimento = date(2012, 2, 29)

    assert idade_em(nascimento, date(2025, 2, 28)) == 12
    # A festa é dia 1º, e a idade tem que virar junto - não no dia seguinte.
    assert idade_em(nascimento, date(2025, 3, 1)) == 13
    assert idade_em(nascimento, date(2024, 2, 29)) == 12


# --------------------------------------------------------------------------
# Cadastro pela API
# --------------------------------------------------------------------------


def test_pai_cadastra_e_apaga_a_data(client: TestClient, admin: User, child: User) -> None:
    resposta = client.patch(
        f"/api/users/{child.id}", headers=as_admin(client), json={"birth_date": "2014-03-25"}
    )
    assert resposta.status_code == 200, resposta.text
    assert resposta.json()["birth_date"] == "2014-03-25"

    # Nulo apaga, ao contrário de `password`, onde nulo é "mantém a que está":
    # é como se desfaz uma data digitada errada, que faria festa no dia errado.
    resposta = client.patch(
        f"/api/users/{child.id}", headers=as_admin(client), json={"birth_date": None}
    )
    assert resposta.json()["birth_date"] is None


def test_editar_outra_coisa_nao_encosta_no_aniversario(
    client: TestClient, admin: User, child: User, db: Session
) -> None:
    nasceu_em(db, child, date(2014, 3, 25))

    resposta = client.patch(
        f"/api/users/{child.id}", headers=as_admin(client), json={"display_name": "João"}
    )

    # `exclude_unset` na rota: quem não manda o campo não mexe nele.
    assert resposta.json()["birth_date"] == "2014-03-25"


def test_conta_nova_ja_nasce_com_aniversario(client: TestClient, admin: User) -> None:
    resposta = client.post(
        "/api/users",
        headers=as_admin(client),
        json={
            "username": "filho2",
            "password": "senha-do-filho",
            "display_name": "Pedro",
            "birth_date": "2016-07-08",
        },
    )

    assert resposta.status_code == 201, resposta.text
    assert resposta.json()["birth_date"] == "2016-07-08"


def test_filho_nao_cadastra_o_proprio_aniversario(
    client: TestClient, admin: User, child: User
) -> None:
    # Ele tem o APK na mão: esconder o campo na tela nunca é a checagem.
    resposta = client.patch(
        f"/api/users/{child.id}", headers=as_child(client), json={"birth_date": "2026-01-01"}
    )

    assert resposta.status_code == 403


# --------------------------------------------------------------------------
# /api/birthday
# --------------------------------------------------------------------------


def test_sem_data_cadastrada_nao_e_aniversario(
    client: TestClient, admin: User, child: User
) -> None:
    resposta = client.get("/api/birthday", headers=as_child(client))

    assert resposta.status_code == 200, resposta.text
    corpo = resposta.json()
    assert corpo["is_birthday"] is False
    assert corpo["birth_date"] is None
    assert corpo["age"] is None
    # O nome vem sempre: a tela de festa chama o filho pelo nome, e ela não faz
    # uma segunda chamada só pra isso.
    assert corpo["display_name"] == child.display_name


def test_no_dia_responde_com_a_idade(
    client: TestClient, admin: User, child: User, db: Session
) -> None:
    nasceu_em(db, child, aniversario_hoje(anos_atras=10))

    corpo = client.get("/api/birthday", headers=as_child(client)).json()

    assert corpo["is_birthday"] is True
    assert corpo["age"] == 10


def test_fora_do_dia_nao_tem_festa_nem_idade(
    client: TestClient, admin: User, child: User, db: Session
) -> None:
    nasceu_em(db, child, aniversario_hoje(anos_atras=10) + timedelta(days=1))

    corpo = client.get("/api/birthday", headers=as_child(client)).json()

    assert corpo["is_birthday"] is False
    # A idade nos outros dias não é pergunta que este endpoint responde.
    assert corpo["age"] is None


def test_cada_um_pergunta_pelo_proprio(
    client: TestClient, admin: User, child: User, db: Session
) -> None:
    nasceu_em(db, child, aniversario_hoje())

    # O pai não herda a festa do filho: a coluna é de pessoa.
    assert client.get("/api/birthday", headers=as_admin(client)).json()["is_birthday"] is False
    assert client.get("/api/birthday", headers=as_child(client)).json()["is_birthday"] is True


def test_sem_token_ninguem_pergunta(client: TestClient, admin: User) -> None:
    assert client.get("/api/birthday").status_code == 401


# --------------------------------------------------------------------------
# Painel web - a mesma coisa do pai nos dois lugares
# --------------------------------------------------------------------------


def logado(client: TestClient, admin: User) -> None:
    client.cookies.set(SESSION_COOKIE, create_access_token(admin.username, admin.role.value))


def test_painel_cadastra_e_limpa_a_data(
    client: TestClient, admin: User, child: User, db: Session
) -> None:
    logado(client, admin)

    client.post(f"/contas/{child.id}/aniversario", data={"birth_date": "2014-03-25"})
    db.refresh(child)
    assert child.birth_date == date(2014, 3, 25)

    # Campo vazio apaga - o <input type="date"> manda string vazia quando a
    # pessoa limpa, e é a única forma de desfazer pelo navegador.
    client.post(f"/contas/{child.id}/aniversario", data={"birth_date": ""})
    db.refresh(child)
    assert child.birth_date is None


def test_painel_ignora_data_impossivel(
    client: TestClient, admin: User, child: User, db: Session
) -> None:
    nasceu_em(db, child, date(2014, 3, 25))
    logado(client, admin)

    # Navegador antigo renderiza <input type="date"> como campo de texto, e aí
    # quem digita "25/03/2014" mandaria isto. Não pode virar 500 nem, pior,
    # apagar a data que já estava certa.
    resposta = client.post(f"/contas/{child.id}/aniversario", data={"birth_date": "25/03/2014"})

    assert resposta.status_code == 200
    db.refresh(child)
    assert child.birth_date == date(2014, 3, 25)


def test_painel_marca_quem_faz_aniversario_hoje(
    client: TestClient, admin: User, child: User, db: Session
) -> None:
    logado(client, admin)
    assert "é hoje!" not in client.get("/contas").text

    nasceu_em(db, child, aniversario_hoje())

    assert "é hoje!" in client.get("/contas").text


def test_painel_so_oferece_aniversario_pra_conta_de_filho(
    client: TestClient, admin: User, child: User
) -> None:
    logado(client, admin)
    pagina = client.get("/contas").text

    # Um campo na conta do pai só ofereceria uma data que não faz nada.
    assert f"/contas/{child.id}/aniversario" in pagina
    assert f"/contas/{admin.id}/aniversario" not in pagina
