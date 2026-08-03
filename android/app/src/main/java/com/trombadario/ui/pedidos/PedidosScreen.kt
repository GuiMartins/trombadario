package com.trombadario.ui.pedidos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trombadario.AppContainer
import com.trombadario.R
import com.trombadario.data.remote.PedidoDto
import com.trombadario.data.remote.UserDto
import com.trombadario.ui.components.AdaptiveScreen
import com.trombadario.ui.components.AppTopBar
import com.trombadario.ui.components.LoadingScreen
import com.trombadario.ui.components.MessageScreen
import com.trombadario.ui.components.formatDateTime
import com.trombadario.ui.components.parseInstant
import com.trombadario.ui.viewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PedidosScreen(container: AppContainer, currentUser: UserDto) {
    val viewModel: PedidosViewModel = viewModel(
        factory = viewModelFactory { PedidosViewModel(container, currentUser) }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { AppTopBar(title = stringResource(R.string.pedidos_title), currentUser = currentUser) },
        floatingActionButton = {
            // Aqui é o filho quem cadastra, não o pai - o inverso de toda outra
            // tela. O backend barra de qualquer forma se can_request virar
            // false no meio da sessão; a tela não tenta adivinhar isso.
            if (!currentUser.isAdmin) {
                FloatingActionButton(
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    onClick = viewModel::startCreate,
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.pedidos_new))
                }
            }
        },
    ) { padding ->
        AdaptiveScreen(modifier = Modifier.padding(padding)) {
            when {
                state.loading -> LoadingScreen()
                else -> PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = { viewModel.load(isRefresh = true) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.pedidos.isEmpty()) {
                        MessageScreen(
                            icon = Icons.Default.QuestionAnswer,
                            title = stringResource(
                                if (currentUser.isAdmin) R.string.pedidos_empty_admin
                                else R.string.pedidos_empty
                            ),
                            message = "",
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(
                                start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(state.pedidos, key = { it.id }) { pedido ->
                                PedidoCard(
                                    pedido = pedido,
                                    childName = if (currentUser.isAdmin) {
                                        viewModel.childName(pedido.childId)
                                    } else {
                                        null
                                    },
                                    isAdmin = currentUser.isAdmin,
                                    onDecide = { aprovado, note -> viewModel.decide(pedido, aprovado, note) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    state.editor?.let { editor ->
        PedidoEditorDialog(
            editor = editor,
            submitting = state.submitting,
            error = state.error,
            onChange = viewModel::updateEditor,
            onSave = viewModel::save,
            onDismiss = viewModel::dismissEditor,
        )
    }
}

@Composable
private fun PedidoCard(
    pedido: PedidoDto,
    childName: String?,
    isAdmin: Boolean,
    onDecide: (aprovado: Boolean, note: String) -> Unit,
) {
    var nota by remember(pedido.id) { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(
                    when (pedido.status) {
                        PedidoDto.APROVADO -> R.string.pedidos_status_aprovado
                        PedidoDto.NEGADO -> R.string.pedidos_status_negado
                        else -> R.string.pedidos_status_pendente
                    }
                ),
                style = MaterialTheme.typography.labelMedium,
                color = when (pedido.status) {
                    PedidoDto.APROVADO -> MaterialTheme.colorScheme.primary
                    PedidoDto.NEGADO -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(Modifier.height(4.dp))
            Text(text = pedido.title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = parseInstant(pedido.createdAt).formatDateTime(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = buildString {
                    if (childName != null) append(childName)
                    if (pedido.justification.isNotBlank()) {
                        if (isNotEmpty()) append(" — ")
                        append(pedido.justification)
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (pedido.decisionNote.isNotBlank()) {
                Text(
                    text = stringResource(R.string.pedidos_resposta, pedido.decisionNote),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isAdmin && pedido.status == PedidoDto.PENDENTE) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = nota,
                    onValueChange = { nota = it },
                    placeholder = { Text(stringResource(R.string.pedidos_resposta_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onDecide(true, nota) }) {
                        Text(stringResource(R.string.pedidos_aprovar))
                    }
                    TextButton(onClick = { onDecide(false, nota) }) {
                        Text(stringResource(R.string.pedidos_negar))
                    }
                }
            }
        }
    }
}

@Composable
private fun PedidoEditorDialog(
    editor: PedidoEditor,
    submitting: Boolean,
    error: Int?,
    onChange: ((PedidoEditor) -> PedidoEditor) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pedidos_new)) },
        text = {
            Column {
                OutlinedTextField(
                    value = editor.title,
                    onValueChange = { v -> onChange { it.copy(title = v) } },
                    label = { Text(stringResource(R.string.pedidos_form_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = editor.justification,
                    onValueChange = { v -> onChange { it.copy(justification = v) } },
                    label = { Text(stringResource(R.string.pedidos_form_justification)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(it), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = !submitting) {
                Text(stringResource(R.string.pedidos_send))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
