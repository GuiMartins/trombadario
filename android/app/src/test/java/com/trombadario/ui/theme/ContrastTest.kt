package com.trombadario.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A paleta do painel web ganhou um teste assim (`test_contraste.py`) depois que
 * o teal das conquistas entrou com o mesmo tom nos dois temas e reprovava no
 * claro - 2,9:1 contra os 4,5:1 exigidos. O Android tinha o bug irmão:
 * `AccentTeal`/`AccentAmber` (Color.kt) eram o mesmo hex em claro e escuro
 * porque um comentário dizia "são identidade, não superfície" - a mesma
 * desculpa que quebrou o teal da web. Este teste refaz a conta nos dois temas
 * pra cada par cor-de-fundo/cor-de-texto que o Material realmente empareja,
 * pra um acento novo com um valor só não voltar a passar despercebido.
 */
class ContrastTest {

    private val minimoTexto = 4.5
    private val minimoComponente = 3.0

    private fun luminancia(cor: Color): Double {
        fun canal(v: Float): Double =
            if (v <= 0.04045f) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        return 0.2126 * canal(cor.red) + 0.7152 * canal(cor.green) + 0.0722 * canal(cor.blue)
    }

    private fun contraste(a: Color, b: Color): Double {
        val (clara, escura) = luminancia(a).let { la ->
            val lb = luminancia(b)
            if (la >= lb) la to lb else lb to la
        }
        return (clara + 0.05) / (escura + 0.05)
    }

    private fun checar(nome: String, frente: Color, fundo: Color, minimo: Double) {
        val razao = contraste(frente, fundo)
        assertTrue(
            "$nome: $frente sobre $fundo dá %.2f:1, abaixo do mínimo %.1f:1"
                .format(razao, minimo),
            razao >= minimo,
        )
    }

    /** Os pares que o Material realmente pinta um em cima do outro. */
    private fun checarEsquema(tema: String, esquema: ColorScheme) {
        checar("$tema background/onBackground", esquema.onBackground, esquema.background, minimoTexto)
        checar("$tema surface/onSurface", esquema.onSurface, esquema.surface, minimoTexto)
        checar(
            "$tema surfaceVariant/onSurfaceVariant",
            esquema.onSurfaceVariant, esquema.surfaceVariant, minimoTexto,
        )
        checar("$tema primary/onPrimary", esquema.onPrimary, esquema.primary, minimoTexto)
        checar(
            "$tema primaryContainer/onPrimaryContainer",
            esquema.onPrimaryContainer, esquema.primaryContainer, minimoTexto,
        )
        checar("$tema secondary/onSecondary", esquema.onSecondary, esquema.secondary, minimoTexto)
        checar(
            "$tema secondaryContainer/onSecondaryContainer",
            esquema.onSecondaryContainer, esquema.secondaryContainer, minimoTexto,
        )
        checar("$tema tertiary/onTertiary", esquema.onTertiary, esquema.tertiary, minimoTexto)
        checar("$tema error/onError", esquema.onError, esquema.error, minimoTexto)
    }

    @Test
    fun `esquema claro tem contraste suficiente em todo par`() {
        checarEsquema("claro", LightColors)
    }

    @Test
    fun `esquema escuro tem contraste suficiente em todo par`() {
        checarEsquema("escuro", DarkColors)
    }

    @Test
    fun `teal da conquista continua legivel nos dois temas`() {
        checar("conquista claro", ConquistaTeal, CardLight, minimoTexto)
        checar("conquista escuro", ConquistaTealDark, CardDark, minimoTexto)
    }

    @Test
    fun `assinatura da marca e a tinta de leitura continuam legiveis`() {
        checar("marca claro", MarkerRed, PaperLight, minimoComponente)
        checar("marca escuro", MarkerRedDark, PaperDark, minimoComponente)
        checar("tinta de leitura claro", MarkerInk, PaperLight, minimoTexto)
    }
}
