package com.trombadario.ui.assuntos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import com.trombadario.data.remote.AssuntoDto
import com.trombadario.data.remote.UserDto
import com.trombadario.ui.components.AdaptiveScreen
import com.trombadario.ui.components.AppTopBar
import com.trombadario.ui.components.LoadingScreen
import com.trombadario.ui.components.MessageScreen
import com.trombadario.ui.components.formatDateTime
import com.trombadario.ui.components.parseInstant
import com.trombadario.ui.viewModelFactory

/**
 * A pauta da conversa que vai acontecer pessoalmente.
 *
 * É a única tela do app em que os dois papéis fazem a mesma coisa - por isso o
 * botão de cadastrar aparece para ambos, ao contrário de todas as outras. O que
 * o pai tem a mais é "já conversamos"; o que o filho tem a menos é isso.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssuntosScreen(container: AppContainer, currentUser: UserDto, onBack: () -> Unit) {
    val viewModel: AssuntosViewModel = viewModel(
        factory = viewModelFactory { AssuntosViewModel(container, currentUser) }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            AppTopBar(
                title = stringResource(R.string.assuntos_title),
                currentUser = currentUser,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (!state.bloqueado) {
                FloatingActionButton(
                    // Círculo, não o squircle padrão do M3 - é o botão redondo
                    // do mockup.
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    onClick = viewModel::startCreate,
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.assuntos_new))
                }
            }
        },
    ) { padding ->
        AdaptiveScreen(modifier = Modifier.padding(padding)) {
            when {
                state.loading -> LoadingScreen()

                // O pai desligou o acesso: nada de lista vazia dando a entender
                // que ninguém anotou nada.
                state.bloqueado -> MessageScreen(
                    icon = Icons.Default.Lock,
                    title = stringResource(R.string.assuntos_bloqueado),
                    message = stringResource(R.string.assuntos_bloqueado_sub),
                )

                else -> Column {
                    val abas = listOf(
                        true to stringResource(R.string.assuntos_tab_pendentes),
                        false to stringResource(R.string.assuntos_tab_conversados),
                    )
                    TabRow(
                        selectedTabIndex = abas.indexOfFirst { it.first == state.mostrandoPendentes }
                    ) {
                        abas.forEach { (pendentes, rotulo) ->
                            Tab(
                                selected = state.mostrandoPendentes == pendentes,
                                onClick = { viewModel.selecionarAba(pendentes) },
                                text = { Text(rotulo) },
                            )
                        }
                    }

                    PullToRefreshBox(
                        isRefreshing = state.refreshing,
                        onRefresh = { viewModel.load(isRefresh = true) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val daAba = state.assuntosDaAba
                        if (daAba.isEmpty()) {
                            MessageScreen(
                                icon = Icons.Default.Forum,
                                title = stringResource(
                                    if (state.mostrandoPendentes) {
                                        R.string.assuntos_empty
                                    } else {
                                        R.string.assuntos_empty_conversados
                                    }
                                ),
                                message = if (state.mostrandoPendentes) {
                                    stringResource(R.string.assuntos_intro)
                                } else {
                                    ""
                                },
                            )
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(
                                    start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(daAba, key = { it.id }) { assunto ->
                                    AssuntoCard(
                                        assunto = assunto,
                                        childName = if (currentUser.isAdmin) {
                                            viewModel.childName(assunto.childId)
                                        } else {
                                            null
                                        },
                                        isAdmin = currentUser.isAdmin,
                                        podeEditar = viewModel.podeEditar(assunto),
                                        podeApagar = viewModel.podeApagar(assunto),
                                        onEdit = { viewModel.startEdit(assunto) },
                                        onTalked = { nota -> viewModel.marcarConversado(assunto, nota) },
                                        onReopen = { viewModel.reabrir(assunto) },
                                        onDelete = { viewModel.askDelete(assunto) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    state.editor?.let { editor ->
        AssuntoEditorDialog(
            editor = editor,
            children = state.children,
            isAdmin = currentUser.isAdmin,
            submitting = state.submitting,
            error = state.error,
            onChange = viewModel::updateEditor,
            onSave = viewModel::save,
            onDismiss = viewModel::dismissEditor,
        )
    }

    state.confirmingDeleteOf?.let { assunto ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text(stringResource(R.string.assuntos_delete_confirm_title)) },
            text = { Text(assunto.title) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDelete) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun AssuntoCard(
    assunto: AssuntoDto,
    childName: String?,
    isAdmin: Boolean,
    podeEditar: Boolean,
    podeApagar: Boolean,
    onEdit: () -> Unit,
    onTalked: (String) -> Unit,
    onReopen: () -> Unit,
    onDelete: () -> Unit,
) {
    var nota by remember(assunto.id) { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(16.dp)) {
            // Quem trouxe o assunto muda o tom da leitura, então vem antes do
            // título. Do lado do pai o rótulo diz o nome; do lado do filho,
            // "você" ou "seu pai".
            Text(
                text = if (isAdmin) {
                    if (assunto.byChild) {
                        stringResource(R.string.assuntos_trazido_por, childName.orEmpty())
                    } else {
                        stringResource(R.string.assuntos_trazido_por_voce)
                    }
                } else {
                    stringResource(
                        if (assunto.byChild) {
                            R.string.assuntos_trazido_por_voce
                        } else {
                            R.string.assuntos_trazido_pelo_pai
                        }
                    )
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(text = assunto.title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = parseInstant(assunto.createdAt).formatDateTime(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (assunto.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = assunto.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!assunto.pendente) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.assuntos_conversado_em,
                        parseInstant(assunto.talkedAt!!).formatDateTime(),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (assunto.talkedNote.isNotBlank()) {
                    Text(
                        text = assunto.talkedNote,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Só o pai encerra, mesmo quando foi o filho quem trouxe: riscar da
            // lista a pauta do outro sem conversa é o que a tela existe pra
            // evitar. O servidor barra de qualquer forma.
            if (isAdmin && assunto.pendente) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = nota,
                    onValueChange = { nota = it },
                    placeholder = { Text(stringResource(R.string.assuntos_nota_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isAdmin) {
                    if (assunto.pendente) {
                        TextButton(onClick = { onTalked(nota) }) {
                            Text(stringResource(R.string.assuntos_conversamos))
                        }
                    } else {
                        TextButton(onClick = onReopen) {
                            Text(stringResource(R.string.assuntos_reabrir))
                        }
                    }
                }
                if (podeEditar) {
                    TextButton(onClick = onEdit) { Text(stringResource(R.string.action_edit)) }
                }
                if (podeApagar) {
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete)) }
                }
            }
        }
    }
}

@Composable
private fun AssuntoEditorDialog(
    editor: AssuntoEditor,
    children: List<UserDto>,
    isAdmin: Boolean,
    submitting: Boolean,
    error: Int?,
    onChange: ((AssuntoEditor) -> AssuntoEditor) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (editor.id == null) R.string.assuntos_new else R.string.assuntos_edit
                )
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = editor.title,
                    onValueChange = { v -> onChange { it.copy(title = v) } },
                    label = { Text(stringResource(R.string.assuntos_form_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = editor.description,
                    onValueChange = { v -> onChange { it.copy(description = v) } },
                    label = { Text(stringResource(R.string.assuntos_form_description)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Só o pai escolhe com quem é a conversa; do lado do filho é
                // sempre ele, e o servidor ignora o que vier.
                if (isAdmin && editor.id == null && children.size > 1) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.assuntos_form_child),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        children.forEach { child ->
                            FilterChip(
                                selected = editor.childId == child.id,
                                onClick = { onChange { it.copy(childId = child.id) } },
                                label = { Text(child.displayName) },
                            )
                        }
                    }
                }

                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = !submitting) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
