package com.trombadario.ui.tipos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trombadario.AppContainer
import com.trombadario.R
import com.trombadario.data.remote.TrombadiceCategoryDto
import com.trombadario.data.remote.UserDto
import com.trombadario.ui.components.AdaptiveScreen
import com.trombadario.ui.components.AppTopBar
import com.trombadario.ui.components.LoadingScreen
import com.trombadario.ui.viewModelFactory

/**
 * A lista de tipos de trombadice - a mesma do painel web, porque toda
 * funcionalidade da conta pai nasce nos dois lugares.
 *
 * Cadastrar aqui é o que faz a tela de anotação ficar curta: lá o pai escolhe um
 * tipo e, se quiser, conta os detalhes. Por isso a tela avisa quando a lista
 * está vazia em vez de deixar descobrir na hora de registrar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TiposScreen(container: AppContainer, currentUser: UserDto, onBack: () -> Unit) {
    val viewModel: TiposViewModel = viewModel(factory = viewModelFactory { TiposViewModel(container) })
    val state by viewModel.state.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            AppTopBar(
                title = stringResource(R.string.tipos_title),
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
            FloatingActionButton(
                // Círculo, não o squircle padrão do M3 - é o botão redondo do
                // mockup, como nas outras telas de cadastro.
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                onClick = viewModel::startCreate,
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.tipos_new))
            }
        },
    ) { padding ->
        AdaptiveScreen(modifier = Modifier.padding(padding)) {
            if (state.loading) {
                LoadingScreen()
                return@AdaptiveScreen
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text(
                        text = stringResource(R.string.tipos_intro),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }

                if (state.tipos.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.tipos_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                } else {
                    items(state.tipos, key = { it.id }) { tipo ->
                        TipoCard(
                            tipo = tipo,
                            onClick = { viewModel.startEdit(tipo) },
                            onToggle = { viewModel.toggleActive(tipo) },
                            onDelete = { viewModel.askDelete(tipo) },
                        )
                    }
                }
            }
        }
    }

    state.editor?.let { editor ->
        AlertDialog(
            onDismissRequest = viewModel::dismissEditor,
            title = {
                Text(
                    stringResource(
                        if (editor.id == null) R.string.tipos_new else R.string.tipos_edit
                    )
                )
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = editor.name,
                        onValueChange = { v -> viewModel.updateEditor { it.copy(name = v) } },
                        label = { Text(stringResource(R.string.tipos_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editor.position,
                        onValueChange = { v ->
                            viewModel.updateEditor { it.copy(position = v.filter(Char::isDigit)) }
                        },
                        label = { Text(stringResource(R.string.tipos_position)) },
                        supportingText = { Text(stringResource(R.string.tipos_position_dica)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    state.error?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(it), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::save, enabled = !state.submitting) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissEditor) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    state.confirmingDeleteOf?.let { tipo ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text(stringResource(R.string.tipos_delete_confirm_title)) },
            text = { Text(tipo.name) },
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
private fun TipoCard(
    tipo: TrombadiceCategoryDto,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(text = tipo.name, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = pluralStringResource(R.plurals.tipos_em_uso, tipo.emUso, tipo.emUso),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!tipo.isActive) {
                    Text(
                        text = stringResource(R.string.tipos_desativado),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onToggle) {
                    Text(
                        stringResource(
                            if (tipo.isActive) R.string.tipos_desativar else R.string.tipos_reativar
                        )
                    )
                }
                // Sem botão de excluir no que está em uso: o servidor recusaria
                // (a anotação ficaria sem dizer o que aconteceu), então oferecer
                // só entregaria um erro. O caminho é desativar.
                if (tipo.emUso == 0) {
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete)) }
                }
            }
        }
    }
}
