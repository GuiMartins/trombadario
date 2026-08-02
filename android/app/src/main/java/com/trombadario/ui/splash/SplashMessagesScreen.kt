package com.trombadario.ui.splash

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trombadario.AppContainer
import com.trombadario.R
import com.trombadario.data.remote.SplashMessageDto
import com.trombadario.data.remote.UserDto
import com.trombadario.ui.components.AdaptiveScreen
import com.trombadario.ui.components.LoadingScreen
import com.trombadario.ui.viewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplashMessagesScreen(container: AppContainer, onBack: () -> Unit) {
    val viewModel: SplashMessagesViewModel = viewModel(
        factory = viewModelFactory { SplashMessagesViewModel(container) }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.splash_title)) },
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
            FloatingActionButton(onClick = viewModel::startCreate) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.splash_new))
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
                        text = stringResource(R.string.splash_intro),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }

                if (state.messages.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.splash_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                } else {
                    items(state.messages, key = { it.id }) { message ->
                        MessageCard(
                            message = message,
                            target = message.childId?.let(viewModel::childName)
                                ?: stringResource(R.string.splash_target_all),
                            onClick = { viewModel.startEdit(message) },
                            onToggle = { viewModel.toggleActive(message) },
                            onDelete = { viewModel.askDelete(message) },
                        )
                    }
                }
            }
        }
    }

    state.editor?.let { editor ->
        EditorDialog(
            editor = editor,
            children = state.children,
            submitting = state.submitting,
            error = state.error,
            onChange = viewModel::updateEditor,
            onSave = viewModel::save,
            onDismiss = viewModel::dismissEditor,
        )
    }

    state.confirmingDeleteOf?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text(stringResource(R.string.splash_delete_confirm_title)) },
            text = { Text(message.text) },
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
private fun MessageCard(
    message: SplashMessageDto,
    target: String,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = target,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (!message.isActive) {
                    Text(
                        text = stringResource(R.string.splash_paused),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(text = message.text, style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onToggle) {
                    Text(
                        stringResource(
                            if (message.isActive) R.string.tasks_pause else R.string.tasks_resume
                        )
                    )
                }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete)) }
            }
        }
    }
}

@Composable
private fun EditorDialog(
    editor: SplashMessageEditor,
    children: List<UserDto>,
    submitting: Boolean,
    error: Int?,
    onChange: ((SplashMessageEditor) -> SplashMessageEditor) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (editor.id == null) R.string.splash_new else R.string.splash_edit))
        },
        text = {
            Column {
                OutlinedTextField(
                    value = editor.text,
                    onValueChange = { v -> onChange { it.copy(text = v) } },
                    label = { Text(stringResource(R.string.splash_text)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.splash_target),
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // "Todos" é o default e vive junto dos filhos, não num
                    // checkbox separado: a escolha é uma só.
                    FilterChip(
                        selected = editor.childId == null,
                        onClick = { onChange { it.copy(childId = null) } },
                        label = { Text(stringResource(R.string.splash_target_all)) },
                    )
                    children.forEach { child ->
                        FilterChip(
                            selected = editor.childId == child.id,
                            onClick = { onChange { it.copy(childId = child.id) } },
                            label = { Text(child.displayName) },
                        )
                    }
                }

                if (error != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(error),
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
