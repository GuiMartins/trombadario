package com.trombadario.ui.splash

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.trombadario.R
import com.trombadario.ui.theme.MarkerRed
import com.trombadario.ui.theme.MarkerRedDark

/**
 * A capa fechada do caderno, pra tela de abertura. Desenhada, não é imagem -
 * mesmo motivo do [com.trombadario.ui.theme.NotebookBackground]: a lombada
 * precisa acompanhar qualquer tamanho de tela sem esticar nem cortar, e o app
 * não vendoriza raster.
 */
@Composable
fun DiaryCover(modifier: Modifier = Modifier) {
    val card = MaterialTheme.colorScheme.surface
    val border = MaterialTheme.colorScheme.outline
    // Assinatura da marca é texto grande, então usa o vermelho exato do
    // mockup - mesma regra do LoginScreen, não a tinta funda de leitura.
    val spine = if (isSystemInDarkTheme()) MarkerRedDark else MarkerRed

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(20.dp))
            .drawBehind {
                drawRect(card)
                // A lombada: uma faixa na borda esquerda, como a margem
                // vermelha do caderno aberto.
                drawRect(color = spine, size = Size(size.width * 0.06f, size.height))
                drawRect(color = border, size = size, style = Stroke(width = 2f))
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            // displaySmall não tem a letra de mão (só display/headline maiores
            // ganharam Handwriting em Type.kt) - headlineMedium é o maior
            // tamanho de letra de mão que cabe na capa sem estourar.
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            color = spine,
            textAlign = TextAlign.Center,
        )
    }
}
