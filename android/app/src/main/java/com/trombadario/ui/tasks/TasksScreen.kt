package com.trombadario.ui.tasks

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChecklistRtl
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trombadario.AppContainer
import com.trombadario.R
import com.trombadario.data.remote.TaskDto
import com.trombadario.data.remote.UserDto
import com.trombadario.ui.components.AdaptiveScreen
import com.trombadario.ui.components.LoadingScreen
import com.trombadario.ui.components.MessageScreen
import com.trombadario.ui.viewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(container: AppContainer, currentUser: UserDto) {
    val viewModel: TasksViewModel = viewModel(
        factory = viewModelFactory { TasksViewModel(container, currentUser) }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val weekdayShort = stringArrayResource(R.array.weekday_short)

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tasks_title)) }) },
        floatingActionButton = {
            if (currentUser.isAdmin) {
                FloatingActionButton(onClick = viewModel::startCreate) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.tasks_new))
                }
            }
        },
    ) { padding ->
        AdaptiveScreen(modifier = Modifier.padding(padding)) {
            if (state.loading) {
                LoadingScreen()
                return@AdaptiveScreen
            }

            Column(Modifier.fillMaxSize()) {
                if (currentUser.isAdmin && state.children.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = state.selectedChildId == null,
                            onClick = { viewModel.selectChild(null) },
                            label = { Text(stringResource(R.string.feed_filter_all)) },
                        )
                        state.children.forEach { child ->
                            FilterChip(
                                selected = state.selectedChildId == child.id,
                                onClick = { viewModel.selectChild(child.id) },
                                label = { Text(child.displayName) },
                            )
                        }
                    }
                }

                if (state.tasks.isEmpty()) {
                    MessageScreen(
                        icon = Icons.Default.ChecklistRtl,
                        title = stringResource(
                            if (currentUser.isAdmin) R.string.tasks_empty_admin else R.string.tasks_empty
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
                        items(state.tasks, key = { it.id }) { task ->
                            TaskCard(
                                task = task,
                                schedule = describeSchedule(task, weekdayShort),
                                childName = if (currentUser.isAdmin && state.children.size > 1) {
                                    viewModel.childName(task.childId)
                                } else {
                                    null
                                },
                                isAdmin = currentUser.isAdmin,
                                onClick = { if (currentUser.isAdmin) viewModel.startEdit(task) },
                                onToggle = { viewModel.toggleActive(task) },
                                onDelete = { viewModel.askDelete(task) },
                            )
                        }
                    }
                }
            }
        }
    }

    state.editor?.let { editor ->
        TaskEditorDialog(
            editor = editor,
            children = state.children,
            submitting = state.submitting,
            error = state.error,
            onChange = viewModel::updateEditor,
            onSave = viewModel::save,
            onDismiss = viewModel::dismissEditor,
        )
    }

    state.confirmingDeleteOf?.let { task ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text(stringResource(R.string.tasks_delete_confirm_title)) },
            text = { Text("${task.name} — ${stringResource(R.string.tasks_delete_confirm_message)}") },
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
private fun describeSchedule(task: TaskDto, weekdayShort: Array<String>): String = when (task.periodicity) {
    TaskDto.DAILY -> stringResource(R.string.tasks_period_daily)
    TaskDto.WEEKLY -> task.weekdays.joinToString(", ") { weekdayShort.getOrElse(it) { "?" } }
    TaskDto.MONTHLY -> stringResource(R.string.tasks_period_monthly) + " (${task.dayOfMonth})"
    else -> stringResource(R.string.tasks_period_once)
}

@Composable
private fun TaskCard(
    task: TaskDto,
    schedule: String,
    childName: String?,
    isAdmin: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = isAdmin, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = schedule,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = task.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (task.description.isNotBlank()) {
                Text(
                    text = task.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (childName != null) {
                    Text(
                        text = childName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!task.isActive) {
                    Text(
                        text = stringResource(R.string.tasks_paused),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (isAdmin) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onToggle) {
                        Text(
                            stringResource(
                                if (task.isActive) R.string.tasks_pause else R.string.tasks_resume
                            )
                        )
                    }
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete)) }
                }
            }
        }
    }
}

@Composable
private fun TaskEditorDialog(
    editor: TaskEditor,
    children: List<UserDto>,
    submitting: Boolean,
    error: Int?,
    onChange: ((TaskEditor) -> TaskEditor) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val weekdayNames = stringArrayResource(R.array.weekday_names)
    val periods = listOf(
        TaskDto.DAILY to R.string.tasks_period_daily,
        TaskDto.WEEKLY to R.string.tasks_period_weekly,
        TaskDto.MONTHLY to R.string.tasks_period_monthly,
        TaskDto.ONCE to R.string.tasks_period_once,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (editor.id == null) R.string.tasks_new else R.string.tasks_edit))
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = editor.name,
                    onValueChange = { v -> onChange { it.copy(name = v) } },
                    label = { Text(stringResource(R.string.tasks_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = editor.description,
                    onValueChange = { v -> onChange { it.copy(description = v) } },
                    label = { Text(stringResource(R.string.tasks_description)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (children.size > 1) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.trombadice_form_child_label),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
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

                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.tasks_periodicity),
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    periods.forEach { (value, label) ->
                        FilterChip(
                            selected = editor.periodicity == value,
                            onClick = { onChange { it.copy(periodicity = value) } },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }

                // Só o campo que faz sentido pra periodicidade escolhida - mostrar
                // os dois deixaria a tela dizendo "todo dia, às segundas".
                if (editor.periodicity == TaskDto.WEEKLY) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.tasks_weekdays),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        weekdayNames.forEachIndexed { index, nome ->
                            FilterChip(
                                selected = index in editor.weekdays,
                                onClick = {
                                    onChange {
                                        it.copy(
                                            weekdays = if (index in it.weekdays) {
                                                it.weekdays - index
                                            } else {
                                                it.weekdays + index
                                            }
                                        )
                                    }
                                },
                                label = { Text(nome.take(3)) },
                            )
                        }
                    }
                }

                if (editor.periodicity == TaskDto.MONTHLY) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editor.dayOfMonth,
                        onValueChange = { v ->
                            onChange { it.copy(dayOfMonth = v.filter(Char::isDigit).take(2)) }
                        },
                        label = { Text(stringResource(R.string.tasks_day_of_month)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
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
