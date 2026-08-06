package com.trombadario.ui.tasks

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trombadario.AppContainer
import com.trombadario.R
import com.trombadario.data.ApiResult
import com.trombadario.data.remote.TaskCompletionCreateDto
import com.trombadario.data.remote.TaskCompletionDto
import com.trombadario.data.remote.TaskCreateDto
import com.trombadario.data.remote.TaskDto
import com.trombadario.data.remote.TaskUpdateDto
import com.trombadario.data.remote.UserDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** null id = criando. */
data class TaskEditor(
    val id: Int? = null,
    val name: String = "",
    val description: String = "",
    val periodicity: String = TaskDto.DAILY,
    val weekdays: Set<Int> = emptySet(),
    val dayOfMonth: String = "",
    val childId: Int? = null,
)

data class TasksState(
    val loading: Boolean = true,
    /** Puxar-pra-atualizar: diferente de `loading`, que é a tela cheia da
     *  primeira entrada. Os dois nunca ficam `true` ao mesmo tempo. */
    val refreshing: Boolean = false,
    val tasks: List<TaskDto> = emptyList(),
    val children: List<UserDto> = emptyList(),
    val selectedChildId: Int? = null,
    val editor: TaskEditor? = null,
    val confirmingDeleteOf: TaskDto? = null,
    val submitting: Boolean = false,
    @StringRes val error: Int? = null,
    /** Só do filho: qual tarefa está com o diálogo de "Feito" aberto. */
    val markingDoneFor: TaskDto? = null,
    val markDoneNote: String = "",
    /** Texto que já vem pronto do backend (ex.: "já marcado nesse período") -
     *  não precisa de string própria pra cada erro possível da rota. */
    val markDoneError: String? = null,
    /** Pro que o servidor não explica em texto (sessão caída, tarefa que sumiu):
     *  sem isto essas respostas caíam num `else` mudo e o toque em "Feito" não
     *  fazia nada nem dizia nada. */
    @StringRes val markDoneErrorRes: Int? = null,
    /** Qual "feito" está esperando confirmação pra ser desmarcado. */
    val confirmingUndoOf: UndoTarget? = null,
)

data class UndoTarget(val task: TaskDto, val completion: TaskCompletionDto)

class TasksViewModel(
    private val container: AppContainer,
    private val currentUser: UserDto,
) : ViewModel() {

    private val _state = MutableStateFlow(TasksState())
    val state: StateFlow<TasksState> = _state.asStateFlow()

    fun load(isRefresh: Boolean = false) {
        _state.update { if (isRefresh) it.copy(refreshing = true) else it.copy(loading = true) }
        viewModelScope.launch {
            if (currentUser.isAdmin) {
                (container.repository.listUsers() as? ApiResult.Success)?.let { users ->
                    _state.update { current ->
                        current.copy(children = users.data.filter { !it.isAdmin && it.isActive })
                    }
                }
            }
            // Uma requisição só: se hoje é dia da tarefa, se o período de agora
            // já foi cumprido e as últimas vezes vêm junto de cada tarefa.
            val result = container.repository.listTasks(_state.value.selectedChildId)
            val tasks = (result as? ApiResult.Success)?.data.orEmpty()
            _state.update { it.copy(loading = false, refreshing = false, tasks = tasks) }
        }
    }

    fun selectChild(childId: Int?) {
        _state.update { it.copy(selectedChildId = childId) }
        load()
    }

    fun childName(childId: Int): String? =
        _state.value.children.firstOrNull { it.id == childId }?.displayName

    fun startCreate() = _state.update {
        it.copy(
            editor = TaskEditor(childId = it.selectedChildId ?: it.children.singleOrNull()?.id),
            error = null,
        )
    }

    fun startEdit(task: TaskDto) = _state.update {
        it.copy(
            editor = TaskEditor(
                id = task.id,
                name = task.name,
                description = task.description,
                periodicity = task.periodicity,
                weekdays = task.weekdays.toSet(),
                dayOfMonth = task.dayOfMonth?.toString().orEmpty(),
                childId = task.childId,
            ),
            error = null,
        )
    }

    fun dismissEditor() = _state.update { it.copy(editor = null) }

    fun updateEditor(transform: (TaskEditor) -> TaskEditor) = _state.update { current ->
        current.copy(editor = current.editor?.let(transform), error = null)
    }

    fun save() {
        val editor = _state.value.editor ?: return
        if (_state.value.submitting) return

        val childId = editor.childId
        if (editor.name.isBlank() || childId == null) {
            _state.update { it.copy(error = R.string.trombadice_form_error_title_required) }
            return
        }
        // Mesma regra do backend, checada aqui só pra dar erro antes de ir na
        // rede: uma tarefa semanal sem dia não significa nada.
        if (editor.periodicity == TaskDto.WEEKLY && editor.weekdays.isEmpty()) {
            _state.update { it.copy(error = R.string.tasks_error_weekday_required) }
            return
        }
        if (editor.periodicity == TaskDto.MONTHLY && editor.dayOfMonth.toIntOrNull() == null) {
            _state.update { it.copy(error = R.string.tasks_error_day_required) }
            return
        }

        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val weekdays = if (editor.periodicity == TaskDto.WEEKLY) editor.weekdays.sorted() else emptyList()
            val dayOfMonth = if (editor.periodicity == TaskDto.MONTHLY) editor.dayOfMonth.toIntOrNull() else null

            val result = if (editor.id == null) {
                container.repository.createTask(
                    TaskCreateDto(
                        name = editor.name.trim(),
                        description = editor.description.trim(),
                        periodicity = editor.periodicity,
                        weekdays = weekdays,
                        dayOfMonth = dayOfMonth,
                        childId = childId,
                    )
                )
            } else {
                container.repository.updateTask(
                    editor.id,
                    TaskUpdateDto(
                        name = editor.name.trim(),
                        description = editor.description.trim(),
                        periodicity = editor.periodicity,
                        weekdays = weekdays,
                        dayOfMonth = dayOfMonth,
                    )
                )
            }

            if (result is ApiResult.Success) {
                _state.update { it.copy(submitting = false, editor = null) }
                load()
            } else {
                _state.update { it.copy(submitting = false, error = R.string.login_error_network) }
            }
        }
    }

    fun toggleActive(task: TaskDto) {
        viewModelScope.launch {
            container.repository.updateTask(task.id, TaskUpdateDto(isActive = !task.isActive))
            load()
        }
    }

    fun askDelete(task: TaskDto) = _state.update { it.copy(confirmingDeleteOf = task) }

    fun cancelDelete() = _state.update { it.copy(confirmingDeleteOf = null) }

    fun confirmDelete() {
        val task = _state.value.confirmingDeleteOf ?: return
        viewModelScope.launch {
            container.repository.deleteTask(task.id)
            _state.update { it.copy(confirmingDeleteOf = null) }
            load()
        }
    }

    fun startMarkDone(task: TaskDto) = _state.update {
        it.copy(markingDoneFor = task, markDoneNote = "", markDoneError = null, markDoneErrorRes = null)
    }

    fun cancelMarkDone() = _state.update {
        it.copy(markingDoneFor = null, markDoneNote = "", markDoneError = null, markDoneErrorRes = null)
    }

    fun onMarkDoneNoteChange(value: String) = _state.update { it.copy(markDoneNote = value) }

    fun confirmMarkDone() {
        val task = _state.value.markingDoneFor ?: return
        viewModelScope.launch {
            val result = container.repository.markTaskDone(
                task.id,
                TaskCompletionCreateDto(note = _state.value.markDoneNote.trim()),
            )
            when (result) {
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(markingDoneFor = null, markDoneNote = "", markDoneError = null)
                    }
                    // Recarrega em vez de remendar a lista em memória: quem diz
                    // se o período está cumprido é o servidor.
                    load()
                }
                // 400/409 vêm com o motivo escrito ("essa tarefa não é hoje",
                // "já marcado nesse período") - é melhor texto do que qualquer
                // um que o app inventasse.
                is ApiResult.Failure -> _state.update { it.copy(markDoneError = result.message) }
                // A tarefa sumiu enquanto a tela estava aberta (o pai apagou).
                // Antes isto não dizia nada e o botão parecia não funcionar.
                is ApiResult.NotFound -> {
                    _state.update {
                        it.copy(markingDoneFor = null, markDoneErrorRes = R.string.tasks_gone)
                    }
                    load()
                }
                else -> _state.update { it.copy(markDoneErrorRes = R.string.login_error_network) }
            }
        }
    }

    fun askUndo(task: TaskDto, completion: TaskCompletionDto) =
        _state.update { it.copy(confirmingUndoOf = UndoTarget(task, completion)) }

    fun cancelUndo() = _state.update { it.copy(confirmingUndoOf = null) }

    fun confirmUndo() {
        val alvo = _state.value.confirmingUndoOf ?: return
        viewModelScope.launch {
            val result = container.repository.undoTaskCompletion(alvo.task.id, alvo.completion.id)
            _state.update {
                it.copy(
                    confirmingUndoOf = null,
                    markDoneError = (result as? ApiResult.Failure)?.message,
                )
            }
            load()
        }
    }

    fun dismissMarkDoneError() =
        _state.update { it.copy(markDoneError = null, markDoneErrorRes = null) }
}
