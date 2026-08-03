"""Conversões entre o dia que a pessoa vê e o instante que o banco guarda.

Tudo é gravado em UTC (ver `app/types.py`), mas ninguém pergunta "quantas
trombadices entre 03:00Z e 03:00Z". A pergunta é "quantas ontem", e ontem é o
dia de quem pergunta. Estas funções fazem essa tradução num lugar só, para o
fuso não voltar a ser decidido em cinco lugares diferentes.

O fuso usado é o do servidor (`TZ` no compose) - é o mesmo da casa, que é a
premissa do app inteiro.
"""

from datetime import date, datetime, time, timedelta


def hoje_local() -> date:
    return datetime.now().astimezone().date()


def data_local(instante: datetime) -> date:
    """O dia em que aquilo aconteceu, do ponto de vista de quem estava lá."""
    return instante.astimezone().date()


def inicio_do_dia(dia: date) -> datetime:
    """Meia-noite local daquele dia, como instante com fuso."""
    return datetime.combine(dia, time.min).astimezone()


def intervalo(de: date | None, ate: date | None) -> tuple[datetime | None, datetime | None]:
    """Um intervalo de dias locais vira [início, fim) em instantes.

    O fim é **exclusivo**, no começo do dia seguinte, em vez de 23:59:59 do dia
    pedido: assim nada que acontecer no último segundo do dia fica de fora por
    causa de arredondamento de microssegundo.
    """
    return (
        inicio_do_dia(de) if de is not None else None,
        inicio_do_dia(ate + timedelta(days=1)) if ate is not None else None,
    )


def semanas_do_mes(mes: date) -> list[list[date | None]]:
    """O mês em linhas de sete, começando na segunda.

    `None` nos buracos do começo e do fim em vez de datas do mês vizinho: o
    calendário do filtro não deve deixar clicar num dia que não é deste mês, e
    devolver `None` torna isso impossível de esquecer no template."""
    primeiro = mes.replace(day=1)
    proximo = (primeiro + timedelta(days=32)).replace(day=1)

    dias: list[date | None] = [None] * primeiro.weekday()
    dia = primeiro
    while dia < proximo:
        dias.append(dia)
        dia += timedelta(days=1)
    while len(dias) % 7:
        dias.append(None)

    return [dias[i : i + 7] for i in range(0, len(dias), 7)]


def mes_anterior(mes: date) -> date:
    return (mes.replace(day=1) - timedelta(days=1)).replace(day=1)


def mes_seguinte(mes: date) -> date:
    return (mes.replace(day=1) + timedelta(days=32)).replace(day=1)


def semana_de(dia: date) -> str:
    """Rótulo da semana: a segunda-feira que a abre, em ISO.

    Segunda e não domingo porque é o que `date.weekday()` já usa no resto do
    projeto (a periodicidade das tarefas conta 0 = segunda)."""
    return (dia - timedelta(days=dia.weekday())).isoformat()


def mes_de(dia: date) -> str:
    return dia.strftime("%Y-%m")
