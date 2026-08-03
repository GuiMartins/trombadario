package com.trombadario.ui.tasks

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trombadario.AppContainer
import com.trombadario.R
import com.trombadario.data.ApiResult
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
    val tasks: List<TaskDto> = emptyList(),
    val children: List<UserDto> = emptyList(),
    val selectedChildId: Int? = null,
    val editor: TaskEditor? = null,
    val confirmingDeleteOf: TaskDto? = null,
    val submitting: Boolean = false,
    @StringRes val error: Int? = null,
)

class TasksViewModel(
    private val container: AppContainer,
    private val currentUser: UserDto,
) : ViewModel() {

    private val _state = MutableStateFlow(TasksState())
    val state: StateFlow<TasksState> = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            if (currentUser.isAdmin) {
                (container.repository.listUsers() as? ApiResult.Success)?.let { users ->
                    _state.update { current ->
                        current.copy(children = users.data.filter { !it.isAdmin && it.isActive })
                    }
                }
            }
            val result = container.repository.listTasks(_state.value.selectedChildId)
            _state.update {
                it.copy(loading = false, tasks = (result as? ApiResult.Success)?.data.orEmpty())
            }
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
}
