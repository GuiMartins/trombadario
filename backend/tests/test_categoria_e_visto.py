from datetime import UTC, datetime, timedelta

from fastapi.testclient import TestClient
from sqlalchemy.orm import Session

from app.models import Punishment, User
from app.periodo import inicio_do_dia
from tests.conftest import as_admin, as_child


def criar(client: TestClient, child_id: int, **extra) -> dict:
    corpo = {
        "title": "Bagunça na sala",
        "occurred_at": "2026-08-01T14:30:00+00:00",
        "child_id": child_id,
        **extra,
    }
    response = client.post("/api/trombadices", headers=as_admin(client), json=corpo)
    assert response.status_code == 201, response.text
    return response.json()


def criar_tarefa(client: TestClient, child_id: int, nome: str) -> dict:
    response = client.post(
        "/api/tasks",
        headers=as_admin(client),
        json={"name": nome, "child_id": child_id, "periodicity": "daily"},
    )
    assert response.status_code == 201, response.text
    return response.json()


# --------------------------------------------------------------------------
# Categoria
# --------------------------------------------------------------------------


def test_sem_categoria_cai_em_outra(client: TestClient, admin: User, child: User) -> None:
    assert criar(client, child.id)["category"] == "outra"


def test_categoria_desconhecida_e_recusada(client: TestClient, admin: User, child: User) -> None:
    response = client.post(
        "/api/trombadices",
        headers=as_admin(client),
        json={
            "title": "x",
            "occurred_at": "2026-08-01T14:30:00+00:00",
            "child_id": child.id,
            "category": "coisa-que-nao-existe",
        },
    )

    # Lista fechada: campo livre viraria dez jeitos de escrever a mesma coisa.
    assert response.status_code == 422


def test_filtra_por_categoria(client: TestClient, admin: User, child: User) -> None:
    criar(client, child.id, title="Empurrou", category="agressao")
    criar(client, child.id, title="Mentiu", category="mentira")

    achadas = client.get(
        "/api/trombadices", headers=as_admin(client), params={"category": "agressao"}
    ).json()

    assert [t["title"] for t in achadas] == ["Empurrou"]


# --------------------------------------------------------------------------
# Título vem da tarefa
# --------------------------------------------------------------------------


def test_titulo_vazio_com_tarefa_vira_o_nome_da_tarefa(
    client: TestClient, admin: User, child: User
) -> None:
    tarefa = criar_tarefa(client, child.id, "Arrumar a cama")

    trombadice = criar(client, child.id, title="", task_id=tarefa["id"])

    assert trombadice["title"] == "Arrumar a cama"


def test_titulo_vazio_sem_tarefa_e_recusado(client: TestClient, admin: User, child: User) -> None:
    response = client.post(
        "/api/trombadices",
        headers=as_admin(client),
        json={
            "title": "   ",
            "occurred_at": "2026-08-01T14:30:00+00:00",
            "child_id": child.id,
        },
    )

    assert response.status_code == 422


def test_titulo_escrito_ganha_da_tarefa(client: TestClient, admin: User, child: User) -> None:
    tarefa = criar_tarefa(client, child.id, "Arrumar a cama")

    trombadice = criar(client, child.id, title="Deixou tudo jogado", task_id=tarefa["id"])

    assert trombadice["title"] == "Deixou tudo jogado"


# --------------------------------------------------------------------------
# Visto pelo filho
# --------------------------------------------------------------------------


def test_nasce_nao_vista(client: TestClient, admin: User, child: User) -> None:
    assert criar(client, child.id)["seen_at"] is None


def test_filho_marca_como_vista_e_o_pai_enxerga(
    client: TestClient, admin: User, child: User
) -> None:
    trombadice = criar(client, child.id)

    client.post(f"/api/trombadices/{trombadice['id']}/visto", headers=as_child(client))

    do_pai = client.get(f"/api/trombadices/{trombadice['id']}", headers=as_admin(client)).json()
    assert do_pai["seen_at"] is not None


def test_marcar_de_novo_nao_muda_a_hora(client: TestClient, admin: User, child: User) -> None:
    trombadice = criar(client, child.id)
    headers = as_child(client)

    primeira = client.post(f"/api/trombadices/{trombadice['id']}/visto", headers=headers).json()
    segunda = client.post(f"/api/trombadices/{trombadice['id']}/visto", headers=headers).json()

    # Reabrir a tela não pode reescrever o carimbo: "visto às 20h" viraria a
    # hora da última olhada e o pai perderia o que queria saber.
    assert primeira["seen_at"] == segunda["seen_at"]


def test_filho_nao_marca_trombadice_de_irmao(
    client: TestClient, admin: User, child: User, other_child: User
) -> None:
    da_irma = criar(client, other_child.id)

    response = client.post(f"/api/trombadices/{da_irma['id']}/visto", headers=as_child(client))

    # 404 e não 403: sondar id não pode confirmar que existe.
    assert response.status_code == 404
    assert response.json()["detail"] == "Trombadice não encontrada"


def test_pai_nao_marca_como_visto_pelo_filho(
    client: TestClient, admin: User, child: User
) -> None:
    trombadice = criar(client, child.id)

    response = client.post(
        f"/api/trombadices/{trombadice['id']}/visto", headers=as_admin(client)
    )

    # Quem "vê" é o filho de quem é a trombadice. Se o pai pudesse marcar, o
    # campo deixaria de responder a pergunta que ele faz.
    assert response.status_code == 404


def test_castigo_visto_pelo_filho(
    client: TestClient, db: Session, admin: User, child: User
) -> None:
    agora = datetime.now(UTC)
    castigo = Punishment(
        reason="uma semana",
        starts_at=agora - timedelta(hours=1),
        ends_at=agora + timedelta(days=7),
        child_id=child.id,
        author_id=admin.id,
    )
    db.add(castigo)
    db.commit()

    client.post(f"/api/punishments/{castigo.id}/visto", headers=as_child(client))

    do_pai = client.get(f"/api/punishments/{castigo.id}", headers=as_admin(client)).json()
    assert do_pai["seen_at"] is not None


# --------------------------------------------------------------------------
# Filtros de data e palavra
# --------------------------------------------------------------------------


def test_filtra_por_intervalo_de_datas(client: TestClient, admin: User, child: User) -> None:
    # Meio-dia local em cada dia, para o teste não depender do fuso da máquina.
    for dia, titulo in [("2026-07-10", "Julho"), ("2026-08-01", "Agosto")]:
        momento = inicio_do_dia(datetime.fromisoformat(dia).date()) + timedelta(hours=12)
        criar(client, child.id, title=titulo, occurred_at=momento.isoformat())

    achadas = client.get(
        "/api/trombadices",
        headers=as_admin(client),
        params={"de": "2026-08-01", "ate": "2026-08-31"},
    ).json()

    assert [t["title"] for t in achadas] == ["Agosto"]


def test_o_ultimo_dia_do_intervalo_entra_inteiro(
    client: TestClient, admin: User, child: User
) -> None:
    quase_meia_noite = inicio_do_dia(datetime.fromisoformat("2026-08-05").date()) + timedelta(
        hours=23, minutes=59, seconds=59
    )
    criar(client, child.id, title="Tarde da noite", occurred_at=quase_meia_noite.isoformat())

    achadas = client.get(
        "/api/trombadices",
        headers=as_admin(client),
        params={"de": "2026-08-05", "ate": "2026-08-05"},
    ).json()

    assert [t["title"] for t in achadas] == ["Tarde da noite"]


def test_busca_por_palavra_no_titulo_e_na_descricao(
    client: TestClient, admin: User, child: User
) -> None:
    criar(client, child.id, title="Bagunça na sala", description="")
    criar(client, child.id, title="Outra coisa", description="deixou a mochila na sala")
    criar(client, child.id, title="Nada a ver", description="")

    achadas = client.get(
        "/api/trombadices", headers=as_admin(client), params={"q": "SALA"}
    ).json()

    assert {t["title"] for t in achadas} == {"Bagunça na sala", "Outra coisa"}


def test_datas_com_registro_saem_sem_repetir(client: TestClient, admin: User, child: User) -> None:
    dia = inicio_do_dia(datetime.fromisoformat("2026-08-05").date())
    criar(client, child.id, title="manhã", occurred_at=(dia + timedelta(hours=9)).isoformat())
    criar(client, child.id, title="noite", occurred_at=(dia + timedelta(hours=22)).isoformat())
    criar(client, child.id, title="outro dia", occurred_at=(dia + timedelta(days=3)).isoformat())

    datas = client.get("/api/trombadices/datas", headers=as_admin(client)).json()["datas"]

    assert datas == ["2026-08-05", "2026-08-08"]


def test_filho_so_ve_as_datas_dele(
    client: TestClient, admin: User, child: User, other_child: User
) -> None:
    dia = inicio_do_dia(datetime.fromisoformat("2026-08-05").date())
    criar(client, child.id, title="dele", occurred_at=(dia + timedelta(hours=9)).isoformat())
    criar(
        client,
        other_child.id,
        title="da irmã",
        occurred_at=(dia + timedelta(days=2, hours=9)).isoformat(),
    )

    datas = client.get("/api/trombadices/datas", headers=as_child(client)).json()["datas"]

    assert datas == ["2026-08-05"]
