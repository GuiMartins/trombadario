"""A paleta do painel, medida — nos dois temas, não só naquele em que se olhou.

O CSS já traz o número no comentário de cada cor escolhida, mas o número era
conferido na mão e só do lado que estava aberto no navegador. Deu no que tinha
que dar: o teal das conquistas entrou com o tom cru do mockup, passava folgado
no escuro (8,3:1) e reprovava no claro (2,9:1) — como texto **e** como borda do
cartão. Ninguém viu porque quem cadastrou a primeira conquista estava no escuro.

Este teste lê o `:root` do `app.css`, resolve os `light-dark()` e refaz a conta
nos dois temas. Ele não olha a página renderizada: trava a **paleta**, que é
onde esse tipo de erro nasce. Par de cor novo que vá para a tela entra na tabela
abaixo — se não entrar, ninguém mede.

Os mínimos são os da WCAG 2.1: 4,5:1 para texto normal, 3:1 para texto grande
(>=24px, ou >=18,7px em negrito) e para borda que delimita componente.
"""

import re
from pathlib import Path

import pytest

CSS = Path(__file__).resolve().parent.parent / "app" / "web" / "static" / "app.css"

TEXTO = 4.5
GRANDE = 3.0


def _luminancia(hexa: str) -> float:
    canais = [int(hexa[i : i + 2], 16) / 255 for i in (1, 3, 5)]
    linear = [c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4 for c in canais]
    return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2]


def _contraste(frente: str, fundo: str) -> float:
    a, b = _luminancia(frente), _luminancia(fundo)
    claro, escuro = max(a, b), min(a, b)
    return (claro + 0.05) / (escuro + 0.05)


def _paleta(tema: str) -> dict[str, str]:
    """As variáveis do `:root`, resolvidas para um tema.

    Cada cor é declarada duas vezes no CSS (o valor claro puro e depois o
    `light-dark()`, que é o fallback para navegador sem suporte), então vale a
    última — a mesma regra da cascata.
    """
    raiz = re.search(r":root\s*\{(.*?)\n\}", CSS.read_text(encoding="utf-8"), re.S)
    assert raiz, "não achei o bloco :root em app.css"

    cores: dict[str, str] = {}
    for nome, valor in re.findall(r"(--[\w-]+)\s*:\s*([^;]+);", raiz.group(1)):
        valor = valor.strip()
        if par := re.fullmatch(r"light-dark\(\s*(#\w{6})\s*,\s*(#\w{6})\s*\)", valor):
            cores[nome] = par.group(1 if tema == "claro" else 2)
        elif re.fullmatch(r"#\w{6}", valor):
            cores[nome] = valor
    return cores


# frente, fundo, mínimo, onde isso aparece na tela.
PARES = [
    ("--ink", "--paper", TEXTO, "texto corrido sobre a folha"),
    ("--ink", "--card", TEXTO, "texto do cartão"),
    ("--ink", "--note", TEXTO, "texto dentro do bloco de formulário"),
    ("--ink-soft", "--paper", TEXTO, "texto secundário sobre a folha"),
    ("--ink-soft", "--card", TEXTO, "sub do cartão, dia vazio do calendário, placeholder"),
    ("--ink-soft", "--note", TEXTO, "texto secundário dentro do formulário"),
    ("--marker-ink", "--paper", TEXTO, "data, aba ativa, botão secundário"),
    ("--marker-ink", "--card", TEXTO, "'quando' do cartão"),
    ("--marker-ink", "--note", TEXTO, "dia com registro no calendário"),
    ("--marker", "--paper", GRANDE, "h1 e a marca — só letra grande"),
    # O teal é escrito e desenhado sempre sobre o cartão (o 'quando' da
    # conquista, o número do relatório, a borda do cartão e da etiqueta de
    # visto), nunca direto sobre a folha.
    ("--teal", "--card", TEXTO, "conquista: 'quando', número do relatório e bordas"),
    ("--on-marker", "--marker-ink", TEXTO, "letra dentro do botão cheio"),
    ("--on-amber", "--amber", TEXTO, "dia do calendário sob o mouse"),
    ("--danger", "--paper", TEXTO, "mensagem de erro"),
    ("--danger", "--card", TEXTO, "botão Excluir"),
]


@pytest.mark.parametrize("tema", ["claro", "escuro"])
@pytest.mark.parametrize(("frente", "fundo", "minimo", "onde"), PARES)
def test_contraste_minimo(tema: str, frente: str, fundo: str, minimo: float, onde: str) -> None:
    cores = _paleta(tema)
    for nome in (frente, fundo):
        assert nome in cores, f"{nome} sumiu do :root ou deixou de ser uma cor literal"

    razao = _contraste(cores[frente], cores[fundo])
    assert razao >= minimo, (
        f"no tema {tema}, {frente} ({cores[frente]}) sobre {fundo} ({cores[fundo]}) "
        f"dá {razao:.2f}:1, abaixo dos {minimo}:1 exigidos — {onde}"
    )


@pytest.mark.parametrize("nome", ["--paper", "--card", "--note", "--ink", "--ink-soft", "--teal"])
def test_cor_muda_com_o_tema(nome: str) -> None:
    """Cor de superfície ou de texto tem que ter os dois valores.

    Um valor só significa que ela foi pensada num tema e herdada no outro — que
    é exatamente como o teal entrou. Acento puramente decorativo (`--amber`,
    `--coral`) fica de fora: ele não carrega texto nem delimita campo.
    """
    assert _paleta("claro")[nome] != _paleta("escuro")[nome], (
        f"{nome} tem o mesmo valor nos dois temas — ou é `light-dark()`, "
        f"ou é decorativa e não devia estar nesta lista"
    )
