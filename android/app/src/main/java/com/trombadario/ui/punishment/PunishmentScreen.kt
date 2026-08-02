package com.trombadario.ui.punishment

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trombadario.AppContainer
import com.trombadario.R
import com.trombadario.data.remote.PunishmentDto
import com.trombadario.data.remote.UserDto
import com.trombadario.ui.components.AdaptiveScreen
import com.trombadario.ui.components.transparentTopBar
import com.trombadario.ui.components.LoadingScreen
import com.trombadario.ui.components.formatDateTime
import com.trombadario.ui.components.parseInstant
import com.trombadario.ui.viewModelFactory
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PunishmentScreen(container: AppContainer, currentUser: UserDto) {
    val viewModel: PunishmentViewModel = viewModel(
        factory = viewModelFactory { PunishmentViewModel(container, currentUser) }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { TopAppBar(
                colors = transparentTopBar(),
                title = { Text(stringResource(R.string.punishment_title)) }) },
        floatingActionButton = {
            if (currentUser.isAdmin) {
                FloatingActionButton(
                    // Círculo, não o squircle padrão do M3 - é o botão redondo
                    // do mockup.
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    onClick = viewModel::startCreate) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.punishment_new))
                }
            }
        },
    ) { padding ->
        AdaptiveScreen(modifier = Modifier.padding(padding)) {
            when {
                state.loading -> LoadingScreen()
                currentUser.isAdmin -> AdminList(state, viewModel)
                else -> ChildAnswer(state, viewModel)
            }
        }
    }

    state.editor?.let { editor ->
        PunishmentEditorDialog(
            editor = editor,
            state = state,
            viewModel = viewModel,
        )
    }
}

/**
 * Para o filho a tela existe pra responder uma coisa só, e a resposta tem que
 * ser legível de longe.
 */
@Composable
private fun ChildAnswer(state: PunishmentState, viewModel: PunishmentViewModel) {
    val atual = state.active.firstOrNull()

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = if (atual == null) Icons.Default.SentimentSatisfiedAlt else Icons.Default.Gavel,
            contentDescription = null,
            modifier = Modifier.size(88.dp),
            tint = if (atual == null) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(
                if (atual == null) R.string.punishment_free else R.string.punishment_grounded
            ),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))

        if (atual == null) {
            Text(
                text = stringResource(R.string.punishment_free_sub),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = stringResource(
                    R.string.punishment_until,
                    parseInstant(atual.endsAt).formatDateTime(),
                ),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            if (atual.reason.isNotBlank()) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = atual.reason,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun AdminList(state: PunishmentState, viewModel: PunishmentViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.active.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Text(
                        text = stringResource(R.string.punishment_admin_empty),
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(state.active, key = { it.id }) { p ->
                PunishmentCard(p, viewModel, ativo = true)
            }
        }

        if (state.history.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.punishment_history),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
            }
            items(state.history, key = { it.id }) { p ->
                PunishmentCard(p, viewModel, ativo = false)
            }
        }
    }
}

@Composable
private fun PunishmentCard(p: PunishmentDto, viewModel: PunishmentViewModel, ativo: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(
                    when {
                        ativo -> R.string.punishment_active
                        p.endedEarlyAt != null -> R.string.punishment_ended_early
                        else -> R.string.punishment_served
                    }
                ),
                style = MaterialTheme.typography.labelMedium,
                color = if (ativo) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = viewModel.childName(p.childId) ?: "",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    R.string.punishment_until,
                    parseInstant(p.endsAt).formatDateTime(),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (p.reason.isNotBlank()) {
                Text(
                    text = p.reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            p.trombadiceIds.mapNotNull(viewModel::trombadiceTitle).forEach { titulo ->
                Text(
                    text = "• $titulo",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (ativo) {
                TextButton(onClick = { viewModel.endNow(p) }) {
                    Text(stringResource(R.string.punishment_end_now))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PunishmentEditorDialog(
    editor: PunishmentEditor,
    state: PunishmentState,
    viewModel: PunishmentViewModel,
) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    // Só as trombadices do filho escolhido aparecem: marcar a de um irmão
    // colocaria o nome dele no castigo deste.
    val doFilho = state.trombadices.filter { it.childId == editor.childId }

    AlertDialog(
        onDismissRequest = viewModel::dismissEditor,
        title = { Text(stringResource(R.string.punishment_new)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (state.children.size > 1) {
                    Text(
                        stringResource(R.string.punishment_who),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.children.forEach { child ->
                            FilterChip(
                                selected = editor.childId == child.id,
                                onClick = {
                                    viewModel.updateEditor {
                                        // Troca de filho limpa a seleção: as
                                        // trombadices marcadas eram de outro.
                                        it.copy(childId = child.id, trombadiceIds = emptySet())
                                    }
                                },
                                label = { Text(child.displayName) },
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                Text(
                    stringResource(R.string.punishment_until_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = { showDate = true }) {
                        Text(editor.date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                    }
                    TextButton(onClick = { showTime = true }) {
                        Text(editor.time.format(DateTimeFormatter.ofPattern("HH:mm")))
                    }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = editor.reason,
                    onValueChange = { v -> viewModel.updateEditor { it.copy(reason = v) } },
                    label = { Text(stringResource(R.string.punishment_reason)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (doFilho.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.punishment_pick_trombadices),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    doFilho.take(20).forEach { t ->
                        FilterChip(
                            selected = t.id in editor.trombadiceIds,
                            onClick = {
                                viewModel.updateEditor {
                                    it.copy(
                                        trombadiceIds = if (t.id in it.trombadiceIds) {
                                            it.trombadiceIds - t.id
                                        } else {
                                            it.trombadiceIds + t.id
                                        }
                                    )
                                }
                            },
                            label = { Text(t.title, maxLines = 1) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        )
                    }
                }

                if (state.error != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(state.error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
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

    if (showDate) {
        // DatePicker fala em millis de meia-noite UTC, não LocalDate; passar
        // pelo fuso do sistema aqui deslocaria a data em um dia.
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = editor.date.atStartOfDay(ZoneOffset.UTC)
                .toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        viewModel.updateEditor {
                            it.copy(
                                date = LocalDate.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC)
                            )
                        }
                    }
                    showDate = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showDate = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) { DatePicker(state = pickerState) }
    }

    if (showTime) {
        val pickerState = rememberTimePickerState(
            initialHour = editor.time.hour,
            initialMinute = editor.time.minute,
            is24Hour = true,
        )
        DatePickerDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateEditor {
                        it.copy(time = LocalTime.of(pickerState.hour, pickerState.minute))
                    }
                    showTime = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showTime = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { TimePicker(state = pickerState) }
        }
    }
}
