from datetime import date, datetime, timedelta

from fastapi.testclient import TestClient
from sqlalchemy.orm import Session

from app.models import Punishment, User
from app.periodo import inicio_do_dia
from tests.conftest import as_admin, as_child


def em(dia: str, hora: int = 12) -> str:
    """Um instante daquele dia local, com fuso - para o teste não depender de
    onde a máquina que roda ele está."""
    return (inicio_do_dia(date.fromisoformat(dia)) + timedelta(hours=hora)).isoformat()


def criar(client: TestClient, child_id: int, dia: str, hora: int = 12, **extra) -> dict:
    corpo = {
        "title": extra.pop("title", "algo"),
        "occurred_at": em(dia, hora),
        "child_id": child_id,
        **extra,
    }
    response = client.post("/api/trombadices", headers=as_admin(client), json=corpo)
    assert response.status_code == 201, response.text
    return response.json()


def relatorio(client: TestClient, **params) -> dict:
    response = client.get("/api/reports", headers=as_admin(client), params=params)
    assert response.status_code == 200, response.text
    return response.json()


def test_filho_nao_abre_relatorio(client: TestClient, admin: User, child: User) -> None:
    assert client.get("/api/reports", headers=as_child(client)).status_code == 403


def test_conta_por_dia_com_os_dias_zerados(client: TestClient, admin: User, child: User) -> None:
    criar(client, child.id, "2026-08-01")
    criar(client, child.id, "2026-08-01", hora=18)
    criar(client, child.id, "2026-08-03")

    dados = relatorio(client, de="2026-08-01", ate="2026-08-03")

    # O dia 2 tem que aparecer com zero: uma lista só dos dias que tiveram
    # trombadice esconde justamente a sequência limpa.
    assert dados["por_dia"] == [
        {"rotulo": "2026-08-01", "total": 2},
        {"rotulo": "2026-08-02", "total": 0},
        {"rotulo": "2026-08-03", "total": 1},
    ]
    assert dados["total"] == 3


def test_trombadice_da_noite_conta_no_dia_certo(
    client: TestClient, admin: User, child: User
) -> None:
    # 23h local. Em UTC isso já é o dia seguinte no Brasil - agrupar pelo campo
    # cru jogaria esta trombadice para o dia 6.
    criar(client, child.id, "2026-08-05", hora=23)

    dados = relatorio(client, de="2026-08-05", ate="2026-08-06")

    assert dados["por_dia"] == [
        {"rotulo": "2026-08-05", "total": 1},
        {"rotulo": "2026-08-06", "total": 0},
    ]


def test_agrupa_por_semana_e_por_mes(client: TestClient, admin: User, child: User) -> None:
    criar(client, child.id, "2026-07-29")  # quarta
    criar(client, child.id, "2026-07-31")  # sexta, mesma semana
    criar(client, child.id, "2026-08-04")  # terça, semana seguinte

    dados = relatorio(client, de="2026-07-01", ate="2026-08-31")

    assert dados["por_semana"] == [
        {"rotulo": "2026-07-27", "total": 2},
        {"rotulo": "2026-08-03", "total": 1},
    ]
    assert dados["por_mes"] == [
        {"rotulo": "2026-07", "total": 2},
        {"rotulo": "2026-08", "total": 1},
    ]


def test_por_categoria_vem_do_mais_comum_pro_menos(
    client: TestClient, admin: User, child: User
) -> None:
    criar(client, child.id, "2026-08-01", category="agressao")
    criar(client, child.id, "2026-08-02", category="mentira")
    criar(client, child.id, "2026-08-03", category="mentira")

    dados = relatorio(client, de="2026-08-01", ate="2026-08-03")

    assert dados["por_categoria"][0] == {"rotulo": "mentira", "total": 2}


def test_por_tarefa_so_conta_o_que_foi_atrelado(
    client: TestClient, admin: User, child: User
) -> None:
    tarefa = client.post(
        "/api/tasks",
        headers=as_admin(client),
        json={"name": "Arrumar a cama", "child_id": child.id, "periodicity": "daily"},
    ).json()
    criar(client, child.id, "2026-08-01", task_id=tarefa["id"])
    criar(client, child.id, "2026-08-02")

    dados = relatorio(client, de="2026-08-01", ate="2026-08-02")

    assert dados["por_tarefa"] == [{"rotulo": "Arrumar a cama", "total": 1}]


def test_maior_sequencia_limpa(client: TestClient, admin: User, child: User) -> None:
    criar(client, child.id, "2026-08-01")
    criar(client, child.id, "2026-08-06")

    dados = relatorio(client, de="2026-08-01", ate="2026-08-07")

    # Dias 2, 3, 4 e 5 limpos = 4. O dia 7 sozinho no fim não bate isso.
    assert dados["maior_sequencia_limpa"] == 4


def test_media_e_sobre_os_dias_que_tiveram_algo(
    client: TestClient, admin: User, child: User
) -> None:
    criar(client, child.id, "2026-08-01")
    criar(client, child.id, "2026-08-01", hora=18)

    dados = relatorio(client, de="2026-08-01", ate="2026-08-30")

    # Dividir por 30 daria 0,07 e não diria nada. "No dia em que aconteceu,
    # aconteceu 2x" diz.
    assert dados["dias_com_registro"] == 1
    assert dados["media_por_dia_com_registro"] == 2.0


def test_periodo_vazio_nao_quebra(client: TestClient, admin: User, child: User) -> None:
    dados = relatorio(client, de="2026-01-01", ate="2026-01-31")

    assert dados["total"] == 0
    assert dados["dia_mais_pesado"] is None
    assert dados["media_por_dia_com_registro"] == 0.0
    assert dados["maior_sequencia_limpa"] == 31


def test_conta_so_o_castigo_que_cai_dentro_do_periodo(
    client: TestClient, db: Session, admin: User, child: User
) -> None:
    # Dez dias de castigo, mas só três dentro da janela pedida.
    db.add(
        Punishment(
            reason="",
            starts_at=datetime.fromisoformat(em("2026-07-28", 0)),
            ends_at=datetime.fromisoformat(em("2026-08-07", 0)),
            child_id=child.id,
            author_id=admin.id,
        )
    )
    db.commit()

    dados = relatorio(client, de="2026-08-01", ate="2026-08-03")

    assert dados["castigos_no_periodo"] == 1
    assert dados["dias_de_castigo"] == 3.0


def test_castigo_perdoado_conta_so_o_que_foi_cumprido(
    client: TestClient, db: Session, admin: User, child: User
) -> None:
    db.add(
        Punishment(
            reason="",
            starts_at=datetime.fromisoformat(em("2026-08-01", 0)),
            ends_at=datetime.fromisoformat(em("2026-08-08", 0)),
            ended_early_at=datetime.fromisoformat(em("2026-08-03", 0)),
            child_id=child.id,
            author_id=admin.id,
        )
    )
    db.commit()

    dados = relatorio(client, de="2026-08-01", ate="2026-08-31")

    assert dados["dias_de_castigo"] == 2.0


def test_conta_as_nao_vistas(client: TestClient, admin: User, child: User) -> None:
    vista = criar(client, child.id, "2026-08-01")
    criar(client, child.id, "2026-08-02")
    client.post(f"/api/trombadices/{vista['id']}/visto", headers=as_child(client))

    dados = relatorio(client, de="2026-08-01", ate="2026-08-02")

    assert dados["nao_vistas"] == 1


def test_filtra_por_filho(
    client: TestClient, admin: User, child: User, other_child: User
) -> None:
    criar(client, child.id, "2026-08-01")
    criar(client, other_child.id, "2026-08-01")
    criar(client, other_child.id, "2026-08-02")

    dados = relatorio(client, de="2026-08-01", ate="2026-08-02", child_id=other_child.id)

    assert dados["total"] == 2
    assert dados["por_filho"] == [{"rotulo": "Maria", "total": 2}]


def test_datas_invertidas_sao_endireitadas(client: TestClient, admin: User, child: User) -> None:
    criar(client, child.id, "2026-08-02")

    dados = relatorio(client, de="2026-08-03", ate="2026-08-01")

    assert dados["de"] == "2026-08-01"
    assert dados["ate"] == "2026-08-03"
    assert dados["total"] == 1
