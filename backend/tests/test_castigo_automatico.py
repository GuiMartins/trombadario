"""O tipo de trombadice diz quantos dias de castigo a coisa custa.

O que se testa aqui é a conta - `app/castigos.py` - e o fato de que registrar a
anotação **já cria o castigo**, no tamanho certo e emendado na fila. Era a conta
que o pai mais fazia, e fazia no olho.

Datas relativas a agora, nunca fixas de calendário: teste com data fixa fica
vermelho na virada do mês, e já ficou.
"""

from datetime import UTC, datetime, timedelta

from fastapi.testclient import TestClient
from sqlalchemy.orm import Session

from app.castigos import dias_de_castigo
from app.models import Punishment, TrombadiceCategory, User
from app.periodo import hoje_local
from tests.conftest import as_admin, as_child


def cria_tipo(client: TestClient, nome: str, **config) -> int:
    resposta = client.post(
        "/api/trombadice-categories", headers=as_admin(client), json={"name": nome, **config}
    )
    assert resposta.status_code == 201, resposta.text
    return resposta.json()["id"]


def anota(client: TestClient, child_id: int, category_id: int, quando: timedelta) -> dict:
    resposta = client.post(
        "/api/trombadices",
        headers=as_admin(client),
        json={
            "child_id": child_id,
            "category_id": category_id,
            "occurred_at": (datetime.now(UTC) + quando).isoformat(),
        },
    )
    assert resposta.status_code == 201, resposta.text
    return resposta.json()


def castigos(client: TestClient, child_id: int) -> list[dict]:
    return client.get(
        "/api/punishments", headers=as_admin(client), params={"child_id": child_id}
    ).json()


def dias(castigo: dict) -> float:
    inicio = datetime.fromisoformat(castigo["starts_at"])
    fim = datetime.fromisoformat(castigo["ends_at"])
    return (fim - inicio) / timedelta(days=1)


# --------------------------------------------------------------------------
# A conta, direto na função: nível, aumento, teto e decaimento.
# --------------------------------------------------------------------------


def tipo(base: int, aumento: int = 0, teto: int = 0) -> TrombadiceCategory:
    return TrombadiceCategory(
        name="Xingou", punishment_days=base, escalation_days=aumento, max_days=teto
    )


def test_tipo_que_custa_zero_nao_gera_castigo(db: Session, child: User) -> None:
    """Como toda instalação existente chega aqui: a migration põe zero nos três
    campos e nada muda até o pai preencher."""
    assert dias_de_castigo(db, child.id, tipo(0), hoje_local()) == (0, 0)


def test_primeira_vez_custa_o_base(db: Session, child: User) -> None:
    assert dias_de_castigo(db, child.id, tipo(2, aumento=1), hoje_local()) == (2, 0)


def test_sem_aumento_a_recorrencia_nao_muda_nada(
    client: TestClient, db: Session, admin: User, child: User
) -> None:
    """`escalation_days = 0` é "custa sempre o mesmo", e é uma escolha, não um
    tipo mal configurado."""
    id_tipo = cria_tipo(client, "Gritou", punishment_days=2)
    anota(client, child.id, id_tipo, timedelta(0))

    assert [dias(c) for c in castigos(client, child.id)] == [2.0]

    anota(client, child.id, id_tipo, timedelta(0))
    assert sorted(dias(c) for c in castigos(client, child.id)) == [2.0, 2.0]


def test_duas_no_mesmo_dia_sobem_a_recorrencia(
    client: TestClient, admin: User, child: User
) -> None:
    """Dia da ocorrência não é dia limpo: a segunda mentira de hoje já custa mais
    que a primeira."""
    id_tipo = cria_tipo(client, "Xingou", punishment_days=1, escalation_days=1)

    anota(client, child.id, id_tipo, timedelta(0))
    anota(client, child.id, id_tipo, timedelta(0))

    assert sorted(dias(c) for c in castigos(client, child.id)) == [1.0, 2.0]


def test_dias_seguidos_escalam(db: Session, client: TestClient, admin: User, child: User) -> None:
    """Ontem e hoje não têm dia limpo entre eles, então a recorrência cresce -
    é o "e se já aumento dos dias por recorrência" do pedido."""
    id_tipo = cria_tipo(client, "Xingou", punishment_days=1, escalation_days=1)
    anota(client, child.id, id_tipo, timedelta(days=-2))
    anota(client, child.id, id_tipo, timedelta(days=-1))

    categoria = db.get(TrombadiceCategory, id_tipo)
    assert dias_de_castigo(db, child.id, categoria, hoje_local()) == (3, 2)


def test_teto_para_o_crescimento(db: Session, client: TestClient, admin: User, child: User) -> None:
    id_tipo = cria_tipo(client, "Xingou", punishment_days=1, escalation_days=1, max_days=2)
    for quando in (-3, -2, -1):
        anota(client, child.id, id_tipo, timedelta(days=quando))

    categoria = db.get(TrombadiceCategory, id_tipo)
    dias_previstos, nivel = dias_de_castigo(db, child.id, categoria, hoje_local())
    # O nível continua subindo; o que o teto limita é o castigo.
    assert nivel == 3
    assert dias_previstos == 2


def test_dia_limpo_derruba_um_nivel(
    db: Session, client: TestClient, admin: User, child: User
) -> None:
    """O "descresce a cada dia sem ter ocorrido" do pedido: três ocorrências
    seguidas levam o nível a 3, e dois dias limpos devolvem dois deles."""
    id_tipo = cria_tipo(client, "Xingou", punishment_days=1, escalation_days=1)
    for quando in (-5, -4, -3):
        anota(client, child.id, id_tipo, timedelta(days=quando))

    categoria = db.get(TrombadiceCategory, id_tipo)
    # Entre a última (-3) e hoje há dois dias limpos: -2 e -1.
    assert dias_de_castigo(db, child.id, categoria, hoje_local()) == (2, 1)


def test_muitos_dias_limpos_zeram_a_recorrencia(
    db: Session, client: TestClient, admin: User, child: User
) -> None:
    """O nível não vai abaixo de zero - quem ficou um mês limpo volta ao base,
    não a um crédito."""
    id_tipo = cria_tipo(client, "Xingou", punishment_days=1, escalation_days=1)
    anota(client, child.id, id_tipo, timedelta(days=-30))

    categoria = db.get(TrombadiceCategory, id_tipo)
    assert dias_de_castigo(db, child.id, categoria, hoje_local()) == (1, 0)


def test_recorrencia_e_por_tipo_e_por_filho(
    db: Session, client: TestClient, admin: User, child: User, other_child: User
) -> None:
    """Mentira do irmão não encarece a birra desta criança, nem a mentira dela."""
    mentira = cria_tipo(client, "Xingou", punishment_days=1, escalation_days=1)
    birra = cria_tipo(client, "Gritou", punishment_days=1, escalation_days=1)
    anota(client, other_child.id, mentira, timedelta(days=-1))
    anota(client, child.id, birra, timedelta(days=-1))

    categoria = db.get(TrombadiceCategory, mentira)
    assert dias_de_castigo(db, child.id, categoria, hoje_local()) == (1, 0)


def test_anotacao_futura_nao_cobra_recorrencia(
    db: Session, client: TestClient, admin: User, child: User
) -> None:
    """O pai registra depois do fato. O que ainda não aconteceu não pode encarecer
    o que acontece hoje."""
    id_tipo = cria_tipo(client, "Xingou", punishment_days=1, escalation_days=1)
    anota(client, child.id, id_tipo, timedelta(days=5))

    categoria = db.get(TrombadiceCategory, id_tipo)
    assert dias_de_castigo(db, child.id, categoria, hoje_local()) == (1, 0)


# --------------------------------------------------------------------------
# Registrar a anotação já cria o castigo.
# --------------------------------------------------------------------------


def test_anotar_cria_o_castigo(client: TestClient, admin: User, child: User) -> None:
    """O pedido inteiro em um teste: o pai diz que aconteceu e o castigo já está
    lá, no tempo certo."""
    id_tipo = cria_tipo(client, "Xingou", punishment_days=2)

    trombadice = anota(client, child.id, id_tipo, timedelta(0))

    (castigo,) = castigos(client, child.id)
    assert dias(castigo) == 2.0
    assert castigo["is_active"] is True
    assert castigo["origin_trombadice_id"] == trombadice["id"]
    assert castigo["recurrence_level"] == 0
    # Nasce com a causa: "castigo sem trombadice não existe" fica satisfeito por
    # construção, não por validação.
    assert castigo["trombadice_ids"] == [trombadice["id"]]
    assert castigo["reason"] == "Xingou"


def test_castigo_cadastrado_a_mao_nao_finge_ser_automatico(
    client: TestClient, admin: User, child: User
) -> None:
    """O rastro é o que deixa a tela dizer "automático". Nulo nos dois é a
    verdade sobre um castigo que o pai digitou."""
    id_tipo = cria_tipo(client, "Xingou", punishment_days=0)
    trombadice = anota(client, child.id, id_tipo, timedelta(0))
    assert castigos(client, child.id) == []

    resposta = client.post(
        "/api/punishments",
        headers=as_admin(client),
        json={
            "child_id": child.id,
            "ends_at": (datetime.now(UTC) + timedelta(days=1)).isoformat(),
            "trombadice_ids": [trombadice["id"]],
        },
    )

    assert resposta.json()["origin_trombadice_id"] is None
    assert resposta.json()["recurrence_level"] is None


def test_conquista_nunca_gera_castigo(client: TestClient, admin: User, child: User) -> None:
    """Conquista não usa a lista de tipos, então não tem preço nenhum pra pagar -
    e se tivesse, seria elogio virando punição."""
    resposta = client.post(
        "/api/trombadices",
        headers=as_admin(client),
        json={
            "child_id": child.id,
            "kind": "conquista",
            "conquista_category": "ajudou",
            "occurred_at": datetime.now(UTC).isoformat(),
        },
    )

    assert resposta.status_code == 201, resposta.text
    assert castigos(client, child.id) == []


def test_trombadice_durante_castigo_emenda_no_fim(
    client: TestClient, admin: User, child: User
) -> None:
    """O outro pedaço do pedido: "se acontecer trombadices enquanto de castigo o
    app precisa ir colocando os castigos pra frente" - nunca dois ao mesmo tempo,
    nunca na mesma data."""
    id_tipo = cria_tipo(client, "Xingou", punishment_days=1, escalation_days=1)

    anota(client, child.id, id_tipo, timedelta(0))
    anota(client, child.id, id_tipo, timedelta(0))

    primeiro, segundo = sorted(castigos(client, child.id), key=lambda c: c["starts_at"])
    assert segundo["starts_at"] == primeiro["ends_at"]
    assert primeiro["is_active"] is True
    assert segundo["is_scheduled"] is True


def test_filho_so_ve_o_que_esta_valendo_da_fila_automatica(
    client: TestClient, admin: User, child: User
) -> None:
    """A fila é do pai. A criança continua vendo um castigo só - o de agora."""
    id_tipo = cria_tipo(client, "Xingou", punishment_days=1, escalation_days=1)
    anota(client, child.id, id_tipo, timedelta(0))
    anota(client, child.id, id_tipo, timedelta(0))

    do_filho = client.get("/api/punishments/current", headers=as_child(client)).json()

    assert len(do_filho) == 1
    assert do_filho[0]["is_active"] is True


def test_apagar_a_anotacao_apaga_o_castigo_dela(
    client: TestClient, admin: User, child: User
) -> None:
    """Sem a causa, o castigo viraria punição que a criança lê sem saber de onde
    veio. Antes desta mudança sobrava o órfão."""
    id_tipo = cria_tipo(client, "Xingou", punishment_days=1)
    trombadice = anota(client, child.id, id_tipo, timedelta(0))
    assert len(castigos(client, child.id)) == 1

    apagou = client.delete(f"/api/trombadices/{trombadice['id']}", headers=as_admin(client))

    assert apagou.status_code == 204
    assert castigos(client, child.id) == []


def test_apagar_uma_causa_de_varias_nao_apaga_o_castigo(
    client: TestClient, db: Session, admin: User, child: User
) -> None:
    """Só cai o castigo que ficaria **sem causa nenhuma**: com outra trombadice
    apontando pra ele, ele continua dizendo de onde veio."""
    id_tipo = cria_tipo(client, "Xingou", punishment_days=1)
    primeira = anota(client, child.id, id_tipo, timedelta(0))
    segunda = anota(client, child.id, id_tipo, timedelta(0))
    castigo = next(c for c in castigos(client, child.id) if c["trombadice_ids"] == [primeira["id"]])
    client.patch(
        f"/api/punishments/{castigo['id']}",
        headers=as_admin(client),
        json={"trombadice_ids": [primeira["id"], segunda["id"]]},
    )

    client.delete(f"/api/trombadices/{primeira['id']}", headers=as_admin(client))

    sobrou = db.get(Punishment, castigo["id"])
    assert sobrou is not None
    assert [t.id for t in sobrou.trombadices] == [segunda["id"]]
    # E para de dizer que nasceu de uma anotação que não existe mais.
    assert sobrou.origin_trombadice_id is None


def test_corrigir_a_anotacao_nao_reescreve_o_castigo(
    client: TestClient, admin: User, child: User
) -> None:
    """Corrigir o tipo não pode mudar um castigo que a criança já pode ter visto.
    Pra isso existe Corrigir o castigo - mesmo espírito de `ends_at` sobreviver a
    Encerrar."""
    barato = cria_tipo(client, "Gritou", punishment_days=1)
    caro = cria_tipo(client, "Xingou", punishment_days=9)
    trombadice = anota(client, child.id, barato, timedelta(0))

    client.patch(
        f"/api/trombadices/{trombadice['id']}",
        headers=as_admin(client),
        json={"category_id": caro},
    )

    (castigo,) = castigos(client, child.id)
    assert dias(castigo) == 1.0


# --------------------------------------------------------------------------
# A previsão que o formulário mostra antes de salvar.
# --------------------------------------------------------------------------


def test_previsao_diz_o_que_a_anotacao_vai_custar(
    client: TestClient, admin: User, child: User
) -> None:
    """O formulário pergunta na mesma requisição que já faz - e a conta é do
    servidor, como toda data aqui."""
    id_tipo = cria_tipo(client, "Xingou", punishment_days=1, escalation_days=1)
    anota(client, child.id, id_tipo, timedelta(0))

    tipos = client.get(
        "/api/trombadice-categories", headers=as_admin(client), params={"child_id": child.id}
    ).json()

    mentira = next(t for t in tipos if t["id"] == id_tipo)
    assert mentira["previsao_dias"] == 2
    assert mentira["previsao_nivel"] == 1


def test_sem_child_id_a_previsao_e_nula(client: TestClient, admin: User, child: User) -> None:
    """Nulo diz "não perguntei", não "custa zero" - recorrência é por criança e
    sem saber de quem não há resposta honesta."""
    id_tipo = cria_tipo(client, "Xingou", punishment_days=1)

    tipos = client.get("/api/trombadice-categories", headers=as_admin(client)).json()

    mentira = next(t for t in tipos if t["id"] == id_tipo)
    assert mentira["previsao_dias"] is None
    assert mentira["punishment_days"] == 1


def test_filho_nao_recebe_previsao(client: TestClient, admin: User, child: User) -> None:
    """O filho lê a lista só pelos chips de filtro do feed. Quanto custa cada
    coisa é conversa do pai."""
    cria_tipo(client, "Xingou", punishment_days=1)

    tipos = client.get(
        "/api/trombadice-categories", headers=as_child(client), params={"child_id": child.id}
    ).json()

    assert all(t["previsao_dias"] is None for t in tipos)


def test_teto_menor_que_o_base_e_recusado(client: TestClient, admin: User) -> None:
    """Os dois números se contradizem, e clampar em silêncio esconderia isso do
    pai, que digitou ambos."""
    resposta = client.post(
        "/api/trombadice-categories",
        headers=as_admin(client),
        json={"name": "Xingou", "punishment_days": 5, "max_days": 2},
    )

    assert resposta.status_code == 400


def test_baixar_o_teto_sozinho_tambem_e_recusado(client: TestClient, admin: User) -> None:
    """A checagem é contra o valor que vai ficar, não só contra o que veio no
    corpo: nenhum dos dois campos, sozinho, parece errado."""
    id_tipo = cria_tipo(client, "Xingou", punishment_days=5, max_days=10)

    resposta = client.patch(
        f"/api/trombadice-categories/{id_tipo}", headers=as_admin(client), json={"max_days": 2}
    )

    assert resposta.status_code == 400


def test_config_do_tipo_vai_e_volta(client: TestClient, admin: User) -> None:
    id_tipo = cria_tipo(client, "Xingou", punishment_days=1, escalation_days=2, max_days=7)

    tipos = client.get("/api/trombadice-categories", headers=as_admin(client)).json()

    mentira = next(t for t in tipos if t["id"] == id_tipo)
    assert (mentira["punishment_days"], mentira["escalation_days"], mentira["max_days"]) == (1, 2, 7)


def test_tipo_novo_nasce_sem_castigo(client: TestClient, admin: User) -> None:
    """Zero nos três é o padrão: o app não decide pela casa dos outros quantos
    dias uma mentira vale."""
    id_tipo = cria_tipo(client, "Coisa nova")

    tipos = client.get("/api/trombadice-categories", headers=as_admin(client)).json()

    novo = next(t for t in tipos if t["id"] == id_tipo)
    assert (novo["punishment_days"], novo["escalation_days"], novo["max_days"]) == (0, 0, 0)


def test_lista_inicial_nao_gera_castigo(client: TestClient, admin: User, child: User) -> None:
    """Quem já usava o app não é surpreendido com castigo automático depois do
    update - a migration põe zero e quem liga é o pai."""
    tipos = client.get("/api/trombadice-categories", headers=as_admin(client)).json()

    assert all(t["punishment_days"] == 0 for t in tipos)


def test_previsao_usa_a_ordem_da_lista(client: TestClient, admin: User, child: User) -> None:
    """A previsão não muda a ordem nem o resto da resposta: é campo a mais na
    mesma lista, e `em_uso` continua respondendo o que respondia."""
    id_tipo = cria_tipo(client, "Xingou", punishment_days=1)
    anota(client, child.id, id_tipo, timedelta(0))

    tipos = client.get(
        "/api/trombadice-categories", headers=as_admin(client), params={"child_id": child.id}
    ).json()

    mentira = next(t for t in tipos if t["id"] == id_tipo)
    assert mentira["em_uso"] == 1
