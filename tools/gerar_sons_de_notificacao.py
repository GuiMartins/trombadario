"""Gera os sons de notificação do app, um por tipo de novidade.

Os sons são **sintetizados**, não baixados - mesma escolha que o caderno ser
desenhado em vez de imagem importada (ver CLAUDE.md). Ninguém precisa rastrear
licença de áudio, os arquivos são minúsculos, e o timbre fica ajustável aqui em
vez de num binário que ninguém sabe de onde veio.

Uso:

    python tools/gerar_sons_de_notificacao.py

Regrava os `.wav` em `android/app/src/main/res/raw/`. Rodar de novo com os
mesmos parâmetros produz exatamente os mesmos bytes (é determinístico), então
regenerar sem querer não suja o diff.

> **Trocar o som de um canal que já foi criado no celular não funciona.** No
> Android o som é propriedade do `NotificationChannel` e é imutável depois de
> criado - mudar exige apagar e recriar o canal, o que descarta os ajustes que a
> pessoa tenha feito à mão nele. Se mexer aqui depois de uma release, mudar
> também o id do canal em `NovidadesWorker`.
"""

from __future__ import annotations

import math
import struct
import wave
from pathlib import Path

TAXA = 22050  # Nyquist em 11 kHz - sobra pro 3º harmônico do tom mais agudo.
DESTINO = Path(__file__).resolve().parent.parent / "android/app/src/main/res/raw"

# Timbre: amplitude de cada parcial (fundamental, 2º, 3º harmônico). Mais peso
# nos harmônicos = mais brilhante/sineta; só fundamental = mais grave e abafado.
SINETA = (1.0, 0.45, 0.22)
SUAVE = (1.0, 0.22, 0.06)
GRAVE = (1.0, 0.30, 0.0)

# Cada som é uma sequência de (frequência em Hz, duração em segundos).
# Notas: C5=523.25 E5=659.25 G5=784 A4=440 C6=1046.5 E6=1318.5 G6=1568
#        A3=220 E3=164.81
SONS: dict[str, dict] = {
    # O aviso neutro: chama atenção sem soar punitivo. Dois toques descendo de
    # pouco - quem julga o que aconteceu é o pai, não o som.
    "som_trombadice": {"notas": [(659.25, 0.16), (523.25, 0.26)], "timbre": SUAVE},
    # Tríade maior subindo, brilhante: é a única notícia boa do app e tem que
    # soar diferente de tudo à primeira escuta.
    "som_conquista": {
        "notas": [(1046.5, 0.13), (1318.5, 0.13), (1568.0, 0.34)],
        "timbre": SINETA,
    },
    # Grave e descendo, mais longo: sério sem ser assustador - quem ouve é uma
    # criança.
    "som_castigo": {"notas": [(220.0, 0.30), (164.81, 0.46)], "timbre": GRAVE},
    # Pro pai: alguém está esperando resposta. Duas batidas iguais, tipo bater
    # na porta.
    "som_pedido": {"notas": [(440.0, 0.14), (440.0, 0.30)], "timbre": SUAVE},
    # Pro filho: saiu decisão. Sobe (chegou resposta) mas discreto - a resposta
    # pode ter sido "não", então não pode soar comemorativo.
    "som_decisao": {"notas": [(784.0, 0.14), (1046.5, 0.32)], "timbre": SUAVE},
}

ATAQUE = 0.006  # rampa de entrada; sem ela cada nota começa com um clique.
SAIDA = 0.010  # rampa de saída: o decaimento exponencial nunca chega a zero
# sozinho, e cortar a nota com ~5% de amplitude estala na emenda pra próxima.
PICO = 0.72  # deixa margem pra não clipar depois da soma dos parciais.


def _nota(frequencia: float, duracao: float, timbre: tuple[float, ...]) -> list[float]:
    total = int(TAXA * duracao)
    # Harmônico mais alto decai mais rápido: é o que faz soar sineta em vez de
    # órgão.
    taus = [duracao / (2.6 + 1.7 * i) for i in range(len(timbre))]

    amostras = []
    for n in range(total):
        t = n / TAXA
        valor = sum(
            amplitude * math.sin(2 * math.pi * frequencia * (i + 1) * t) * math.exp(-t / taus[i])
            for i, amplitude in enumerate(timbre)
            if amplitude
        )
        if t < ATAQUE:
            valor *= t / ATAQUE
        restante = duracao - t
        if restante < SAIDA:
            valor *= restante / SAIDA
        amostras.append(valor)
    return amostras


def _gerar(nome: str, receita: dict) -> Path:
    amostras: list[float] = []
    for frequencia, duracao in receita["notas"]:
        amostras.extend(_nota(frequencia, duracao, receita["timbre"]))

    maior = max(abs(a) for a in amostras)
    escala = PICO / maior

    caminho = DESTINO / f"{nome}.wav"
    with wave.open(str(caminho), "wb") as arquivo:
        arquivo.setnchannels(1)
        arquivo.setsampwidth(2)
        arquivo.setframerate(TAXA)
        arquivo.writeframes(
            b"".join(struct.pack("<h", int(a * escala * 32767)) for a in amostras)
        )
    return caminho


def main() -> None:
    DESTINO.mkdir(parents=True, exist_ok=True)
    for nome, receita in SONS.items():
        caminho = _gerar(nome, receita)
        duracao = sum(d for _, d in receita["notas"])
        print(f"{caminho.name}: {duracao:.2f}s, {caminho.stat().st_size / 1024:.0f} KB")


if __name__ == "__main__":
    main()
