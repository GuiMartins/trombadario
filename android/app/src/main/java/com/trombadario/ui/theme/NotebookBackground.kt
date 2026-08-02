package com.trombadario.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp

private val RULE_SPACING = 32.dp
private val MARGIN_X = 28.dp

/**
 * A folha: fundo creme, linhas pautadas e a margem vermelha na lateral.
 *
 * Desenhado em [drawBehind] em vez de uma imagem de fundo porque as linhas
 * precisam acompanhar qualquer altura de tela sem esticar nem repetir cortado -
 * e porque assim o espaçamento acompanha a densidade do aparelho.
 */
@Composable
fun NotebookBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val rule = MaterialTheme.colorScheme.outlineVariant
    val margin = MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
    val paper = MaterialTheme.colorScheme.background

    Box(
        modifier = modifier
            .fillMaxSize()
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

                val x = MARGIN_X.toPx()
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
