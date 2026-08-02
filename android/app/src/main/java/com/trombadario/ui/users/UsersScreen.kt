package com.trombadario.ui.users

import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trombadario.AppContainer
import com.trombadario.R
import com.trombadario.data.remote.UserDto
import com.trombadario.ui.components.AdaptiveScreen
import com.trombadario.ui.theme.NotebookGutter
import com.trombadario.ui.components.transparentTopBar
import com.trombadario.ui.components.LoadingScreen
import com.trombadario.ui.viewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsersScreen(container: AppContainer, currentUser: UserDto, onBack: () -> Unit) {
    val viewModel: UsersViewModel = viewModel(factory = viewModelFactory { UsersViewModel(container) })
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = transparentTopBar(),
                modifier = Modifier.padding(start = NotebookGutter),
                title = { Text(stringResource(R.string.users_title)) },
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
                    // Círculo, não o squircle padrão do M3 - é o botão redondo
                    // do mockup.
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    onClick = viewModel::startCreate) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.users_new))
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
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.users, key = { it.id }) { user ->
                    UserCard(
                        user = user,
                        // Removing the account you are logged in as would leave
                        // the app in a state with no way back.
                        canDelete = user.id != currentUser.id,
                        onClick = { viewModel.startEdit(user) },
                        onDelete = { viewModel.askDelete(user) },
                    )
                }
            }
        }
    }

    state.editor?.let { editor ->
        UserEditorDialog(
            editor = editor,
            submitting = state.submitting,
            validationError = state.validationError,
            serverError = state.serverError,
            onChange = viewModel::updateEditor,
            onSave = viewModel::save,
            onDismiss = viewModel::dismissEditor,
        )
    }

    state.confirmingDeleteOf?.let { user ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text(stringResource(R.string.users_delete_confirm_title)) },
            text = { Text("${user.displayName} — ${stringResource(R.string.users_delete_confirm_message)}") },
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
private fun UserCard(
    user: UserDto,
    canDelete: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // weight(1f) so a long display name can't push the delete icon out
            // and get clipped by the card.
            Column(Modifier.weight(1f)) {
                Text(
                    text = user.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(user.username)
                        append(" · ")
                        append(
                            stringResource(
                                if (user.isAdmin) R.string.users_role_admin else R.string.users_role_child
                            )
                        )
                        if (!user.isActive) {
                            append(" · ")
                            append(stringResource(R.string.users_inactive))
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (canDelete) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                    )
                }
            }
        }
    }
}

@Composable
private fun UserEditorDialog(
    editor: UserEditor,
    submitting: Boolean,
    validationError: Int?,
    serverError: String?,
    onChange: ((UserEditor) -> UserEditor) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val creating = editor.id == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (creating) R.string.users_new else R.string.action_edit))
        },
        text = {
            Column {
                if (creating) {
                    OutlinedTextField(
                        value = editor.username,
                        onValueChange = { value -> onChange { it.copy(username = value) } },
                        label = { Text(stringResource(R.string.users_form_username)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                }

                OutlinedTextField(
                    value = editor.displayName,
                    onValueChange = { value -> onChange { it.copy(displayName = value) } },
                    label = { Text(stringResource(R.string.users_form_display_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = editor.password,
                    onValueChange = { value -> onChange { it.copy(password = value) } },
                    label = {
                        Text(
                            stringResource(
                                if (creating) R.string.users_form_password
                                else R.string.users_form_new_password
                            )
                        )
                    },
                    supportingText = if (creating) null else {
                        { Text(stringResource(R.string.users_form_new_password_hint)) }
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (creating) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = editor.isAdmin,
                            onCheckedChange = { value -> onChange { it.copy(isAdmin = value) } },
                        )
                        Text(stringResource(R.string.users_role_admin))
                    }
                } else {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = editor.isActive,
                            onCheckedChange = { value -> onChange { it.copy(isActive = value) } },
                        )
                        Text(stringResource(R.string.users_form_active))
                    }
                }

                val message = validationError?.let { stringResource(it) } ?: serverError
                if (message != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = message,
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
