package com.trombadario.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trombadario.data.remote.UserDto
import com.trombadario.ui.theme.NotebookGutter

/**
 * A barra de topo repetida em toda tela, agora com o nome de quem está usando
 * o app à direita do título ("Tarefas          Paulo") - pedido do usuário,
 * pra nunca ficar em dúvida em qual conta está aberta.
 *
 * Extraído porque o mesmo bloco (`TopAppBar` + `transparentTopBar()` +
 * `NotebookGutter`) se repetia em ~9 telas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    currentUser: UserDto,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        colors = transparentTopBar(),
        modifier = Modifier.padding(start = NotebookGutter),
        navigationIcon = navigationIcon,
        actions = actions,
        title = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title)
                Spacer(Modifier.weight(1f))
                Text(
                    text = currentUser.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        },
    )
}
