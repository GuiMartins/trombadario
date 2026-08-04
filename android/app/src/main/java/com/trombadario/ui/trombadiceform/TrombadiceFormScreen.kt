package com.trombadario.ui.trombadiceform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trombadario.AppContainer
import com.trombadario.R
import com.trombadario.data.remote.Categoria
import com.trombadario.data.remote.UserDto
import com.trombadario.ui.components.AdaptiveScreen
import com.trombadario.data.remote.Tipo
import com.trombadario.ui.components.AppTopBar
import com.trombadario.ui.components.ehConquista
import com.trombadario.ui.components.rotuloDaCategoria
import com.trombadario.ui.components.rotuloDoTipo
import com.trombadario.ui.components.LoadingScreen
import com.trombadario.ui.viewModelFactory
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrombadiceFormScreen(
    container: AppContainer,
    currentUser: UserDto,
    trombadiceId: Int?,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: TrombadiceFormViewModel = viewModel(
        factory = viewModelFactory { TrombadiceFormViewModel(container, trombadiceId) }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) {
        if (state.saved) onDone()
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            AppTopBar(
                title = stringResource(
                    if (trombadiceId == null) R.string.trombadice_form_new_title
                    else R.string.trombadice_form_edit_title
                ),
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
        }
    ) { padding ->
        AdaptiveScreen(modifier = Modifier.padding(padding)) {
            if (state.loading) {
                LoadingScreen()
                return@AdaptiveScreen
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            ) {
                // O tipo vem antes de tudo: muda a lista de categorias e some
                // com o campo de tarefa. Na edição não aparece - trombadice não
                // vira conquista, e reescrever isso mudaria o significado de um
                // registro que a criança já pode ter visto.
                if (trombadiceId == null) {
                    Text(
                        text = stringResource(R.string.trombadice_form_kind_label),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tipo.TODOS.forEach { valor ->
                            FilterChip(
                                selected = state.kind == valor,
                                onClick = { viewModel.onKindChange(valor) },
                                label = { Text(stringResource(rotuloDoTipo(valor))) },
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }

                // A tarefa responde duas das perguntas de baixo - de quem é e
                // qual é o título - e some com elas da tela. Só para trombadice.
                if (state.tasks.isNotEmpty() && !ehConquista(state.kind)) {
                    Text(
                        text = stringResource(R.string.trombadice_form_task_label),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = state.selectedTaskId == null,
                            onClick = { viewModel.onTaskChange(null) },
                            label = { Text(stringResource(R.string.trombadice_form_task_none)) },
                        )
                        // Todas as tarefas, não só as do filho escolhido:
                        // escolher a tarefa é que escolhe o filho agora.
                        state.tasks.forEach { task ->
                            FilterChip(
                                selected = state.selectedTaskId == task.id,
                                onClick = { viewModel.onTaskChange(task.id) },
                                label = { Text(task.name) },
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }

                if (state.selectedTaskId == null) {
                    OutlinedTextField(
                        value = state.title,
                        onValueChange = viewModel::onTitleChange,
                        label = { Text(stringResource(R.string.trombadice_form_title_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Sempre visível, mesmo com um filho só (requisito 3): quem
                    // lê a tela precisa ver de quem é sem ter que deduzir.
                    if (state.children.isNotEmpty()) {
                        Spacer(Modifier.height(24.dp))
                        Text(
                            text = stringResource(R.string.trombadice_form_child_label),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            state.children.forEach { child ->
                                FilterChip(
                                    selected = state.selectedChildId == child.id,
                                    onClick = { viewModel.onChildChange(child.id) },
                                    label = { Text(child.displayName) },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }

                Text(
                    text = stringResource(R.string.trombadice_form_category_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Categoria.de(state.kind).forEach { valor ->
                        FilterChip(
                            selected = state.category == valor,
                            onClick = { viewModel.onCategoryChange(valor) },
                            label = { Text(stringResource(rotuloDaCategoria(valor))) },
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AssistChip(
                        onClick = { showDatePicker = true },
                        label = {
                            Text(state.date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                        },
                    )
                    AssistChip(
                        onClick = { showTimePicker = true },
                        label = { Text(state.time.format(DateTimeFormatter.ofPattern("HH:mm"))) },
                    )
                }
                Spacer(Modifier.height(24.dp))

                OutlinedTextField(
                    value = state.description,
                    onValueChange = viewModel::onDescriptionChange,
                    label = { Text(stringResource(R.string.trombadice_form_description_label)) },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (state.error != null) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(state.error!!),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = viewModel::submit,
                    enabled = !state.submitting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.submitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        // DatePicker speaks epoch millis at UTC midnight, not LocalDate - going
        // through the system zone here would shift the date by a day.
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        viewModel.onDateChange(
                            LocalDate.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC)
                        )
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showTimePicker) {
        val pickerState = rememberTimePickerState(
            initialHour = state.time.hour,
            initialMinute = state.time.minute,
            is24Hour = true,
        )
        DatePickerDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onTimeChange(LocalTime.of(pickerState.hour, pickerState.minute))
                    showTimePicker = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            ) {
                TimePicker(state = pickerState)
            }
        }
    }
}
