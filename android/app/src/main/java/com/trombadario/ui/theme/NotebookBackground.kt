package com.trombadario.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp

private val RULE_SPACING = 32.dp

/** Onde a margem vermelha é desenhada, contando da borda esquerda da folha. */
val NotebookMarginX = 24.dp

/**
 * Quanto o conteúdo recua pra começar **depois** da margem, e não por cima dela
 * - em caderno de verdade ninguém escreve em cima da linha vermelha.
 *
 * Soma com o padding que cada tela já tem: 24 aqui + 16 da lista = 40dp, o que
 * deixa 16dp de respiro depois da linha.
 */
val NotebookGutter = 24.dp

/**
 * Largura máxima da folha. Casa com o teto do [com.trombadario.ui.components
 * .AdaptiveScreen] (600dp) mais as duas margens laterais, pra folha e conteúdo
 * terminarem no mesmo lugar em tela larga.
 */
private val PAGE_MAX_WIDTH = 648.dp

/**
 * A folha: fundo creme, linhas pautadas e a margem vermelha na lateral.
 *
 * Desenhado em [drawBehind] em vez de imagem de fundo porque as linhas precisam
 * acompanhar qualquer altura de tela sem esticar nem repetir cortado, e porque
 * assim o espaçamento acompanha a densidade do aparelho.
 *
 * Em tela larga (tablet, dobrável aberto) a **folha** é que limita e centraliza,
 * não só o conteúdo: senão a margem ficaria colada na borda esquerda enquanto o
 * texto estaria no meio, uma linha vermelha solta longe de tudo. Sobra dos lados
 * fica com um tom de mesa, como uma folha apoiada.
 */
@Composable
fun NotebookBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val rule = MaterialTheme.colorScheme.outlineVariant
    val margin = MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
    val paper = MaterialTheme.colorScheme.background

    BoxWithConstraints(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val wide = maxWidth > PAGE_MAX_WIDTH

        Box(
            modifier = Modifier
                // Ordem importa, e essa é a mesma pegadinha que o AdaptiveScreen
                // já documenta: fillMaxSize() antes do widthIn fixa min=max na
                // largura do pai e o teto é ignorado. Limita primeiro, preenche
                // até o limite depois.
                .then(if (wide) Modifier.widthIn(max = PAGE_MAX_WIDTH) else Modifier)
                .fillMaxWidth()
                .fillMaxHeight()
                .drawBehind {
                    drawRect(paper)

                    val spacing = RULE_SPACING.toPx()
                    var y = spacing
                    while (y < size.height) {
                        drawLine(
                            color = rule,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1f,
                        )
                        y += spacing
                    }

                    val x = NotebookMarginX.toPx()
                    drawLine(
                        color = margin,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 2f,
                    )
                }
        ) {
            content()
        }
    }
}

/** A mesa sob a folha, visível só quando a tela é mais larga que a página. */
@Composable
fun DeskBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    // Não sai do colorScheme porque não é uma superfície do Material - é o que
    // fica atrás da folha, e reusar surfaceVariant deixava a "mesa" amarelo-gema
    // competindo com o papel.
    val desk = if (isSystemInDarkTheme()) DeskDark else DeskLight
    Box(modifier = modifier.fillMaxSize().drawBehind { drawRect(desk) }) { content() }
}
