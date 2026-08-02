package com.trombadario.ui.trombadicedetail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SentimentDissatisfied
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
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
import com.trombadario.ui.components.MessageScreen
import com.trombadario.ui.components.formatDateTime
import com.trombadario.ui.components.parseInstant
import com.trombadario.ui.viewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrombadiceDetailScreen(
    container: AppContainer,
    currentUser: UserDto,
    trombadiceId: Int?,
    onBack: () -> Unit,
    onEdit: (Int) -> Unit,
) {
    val viewModel: TrombadiceDetailViewModel = viewModel(
        factory = viewModelFactory { TrombadiceDetailViewModel(container, trombadiceId) }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = transparentTopBar(),
                modifier = Modifier.padding(start = NotebookGutter),
                title = { Text(stringResource(R.string.trombadice_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    // Admin only. The backend refuses these for a child anyway;
                    // this just keeps the UI honest about what is on offer.
                    if (currentUser.isAdmin && state.event != null) {
                        IconButton(onClick = { onEdit(state.event!!.id) }) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = stringResource(R.string.action_edit),
                            )
                        }
                        IconButton(onClick = viewModel::askDelete) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.action_delete),
                            )
                        }
                    }
                },
            )
        }
    ) { padding ->
        AdaptiveScreen(modifier = Modifier.padding(padding)) {
            val event = state.event
            when {
                state.loading -> LoadingScreen()

                event == null -> MessageScreen(
                    icon = Icons.Default.SentimentDissatisfied,
                    title = stringResource(R.string.feed_error),
                    message = "",
                    actionLabel = stringResource(R.string.action_back),
                    onAction = onBack,
                )

                else -> Column(Modifier.fillMaxSize().padding(24.dp)) {
                    Text(
                        text = parseInstant(event.occurredAt).formatDateTime(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(text = event.title, style = MaterialTheme.typography.headlineSmall)
                    if (event.description.isNotBlank()) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = event.description,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    // Nome da tarefa, não o id: "não cumpriu: 7" não diz nada a
                    // quem está lendo.
                    state.taskName?.let { taskName ->
                        Spacer(Modifier.height(24.dp))
                        Text(
                            text = stringResource(R.string.trombadice_form_broke_task, taskName),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }

    if (state.confirmingDelete) {
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text(stringResource(R.string.trombadice_delete_confirm_title)) },
            text = { Text(stringResource(R.string.trombadice_delete_confirm_message)) },
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
