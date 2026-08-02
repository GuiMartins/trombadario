package com.trombadario.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trombadario.ui.theme.NotebookGutter

val LocalWindowWidthSizeClass = compositionLocalOf { WindowWidthSizeClass.Compact }

/**
 * Container for list/form screens. Full width on a phone; capped and centred on
 * a tablet or an unfolded foldable, so text lines don't stretch to 10 inches.
 *
 * Uses the official WindowSizeClass rather than a guessed dp breakpoint. Every
 * new screen with a list or a form should sit inside this instead of a bare
 * fillMaxSize.
 */
@Composable
fun AdaptiveScreen(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val widthSizeClass = LocalWindowWidthSizeClass.current
    Box(
        // O recuo da margem entra aqui, num lugar só: toda tela de conteúdo
        // passa por este container, então nenhuma precisa se lembrar de não
        // escrever por cima da linha vermelha.
        modifier = modifier.fillMaxSize().padding(start = NotebookGutter),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = if (widthSizeClass == WindowWidthSizeClass.Compact) {
                Modifier.fillMaxSize()
            } else {
                // Order matters: widthIn before fillMaxWidth. The other way round
                // fillMaxWidth pins min=max to the parent width and the cap is
                // ignored.
                Modifier.widthIn(max = 600.dp).fillMaxWidth().fillMaxSize()
            }
        ) {
            content()
        }
    }
}
