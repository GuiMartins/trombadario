package com.trombadario.ui.trombadiceform

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trombadario.AppContainer
import com.trombadario.R
import com.trombadario.data.ApiResult
import com.trombadario.data.remote.Categoria
import com.trombadario.data.remote.TrombadiceCreateDto
import com.trombadario.data.remote.TrombadiceUpdateDto
import com.trombadario.data.remote.TaskDto
import com.trombadario.data.remote.UserDto
import com.trombadario.ui.components.localToInstant
import com.trombadario.ui.components.parseInstant
import com.trombadario.ui.components.toIsoUtc
import com.trombadario.ui.components.toLocalDateTime
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TrombadiceFormState(
    val loading: Boolean = true,
    val title: String = "",
    val description: String = "",
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime = LocalTime.now().withSecond(0).withNano(0),
    val children: List<UserDto> = emptyList(),
    val selectedChildId: Int? = null,
    val tasks: List<TaskDto> = emptyList(),
    val selectedTaskId: Int? = null,
    val category: String = Categoria.OUTRA,
    val submitting: Boolean = false,
    @StringRes val error: Int? = null,
    val saved: Boolean = false,
)

class TrombadiceFormViewModel(
    private val container: AppContainer,
    private val trombadiceId: Int?,
) : ViewModel() {

    private val _state = MutableStateFlow(TrombadiceFormState())
    val state: StateFlow<TrombadiceFormState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val children = (container.repository.listUsers() as? ApiResult.Success)
                ?.data
                ?.filter { !it.isAdmin && it.isActive }
                .orEmpty()

            val event = trombadiceId?.let {
                (container.repository.getTrombadice(it) as? ApiResult.Success)?.data
            }

            val tasks = (container.repository.listTasks() as? ApiResult.Success)
                ?.data
                ?.filter { it.isActive }
                .orEmpty()

            _state.update { current ->
                val occurred = event?.let { parseInstant(it.occurredAt).toLocalDateTime() }
                current.copy(
                    loading = false,
                    title = event?.title ?: current.title,
                    description = event?.description ?: current.description,
                    date = occurred?.toLocalDate() ?: current.date,
                    time = occurred?.toLocalTime() ?: current.time,
                    children = children,
                    // Editing keeps the event's own child; a new event defaults to
                    // the only child when there is just one, which is the usual case.
                    selectedChildId = event?.childId ?: children.singleOrNull()?.id,
                    tasks = tasks,
                    selectedTaskId = event?.taskId,
                    category = event?.category ?: current.category,
                )
            }
        }
    }

    fun onTitleChange(value: String) = _state.update { it.copy(title = value, error = null) }

    fun onDescriptionChange(value: String) = _state.update { it.copy(description = value) }

    fun onDateChange(value: LocalDate) = _state.update { it.copy(date = value) }

    fun onTimeChange(value: LocalTime) = _state.update { it.copy(time = value) }

    fun onChildChange(childId: Int) = _state.update {
        // Trocar de filho descarta a tarefa marcada: ela era de outro, e o
        // backend recusaria o vínculo.
        it.copy(selectedChildId = childId, selectedTaskId = null, error = null)
    }

    fun onCategoryChange(value: String) = _state.update { it.copy(category = value) }

    /**
     * Escolher tarefa responde de quem é e, se ninguém escreveu título, qual é
     * o título - o que aconteceu foi não ter feito aquilo. Por isso esses dois
     * campos somem da tela quando há tarefa; ver a mesma regra no painel web.
     */
    fun onTaskChange(taskId: Int?) = _state.update { current ->
        val task = current.tasks.firstOrNull { it.id == taskId }
        current.copy(
            selectedTaskId = taskId,
            // A tarefa manda: ela pertence a um filho só.
            selectedChildId = task?.childId ?: current.selectedChildId,
            error = null,
        )
    }

    fun submit() {
        val current = _state.value
        if (current.submitting) return

        val tarefa = current.tasks.firstOrNull { it.id == current.selectedTaskId }
        // Sem tarefa, o título é obrigatório. Com tarefa, ele vem do nome dela.
        val titulo = current.title.trim().ifBlank { tarefa?.name.orEmpty() }
        if (titulo.isBlank()) {
            _state.update { it.copy(error = R.string.trombadice_form_error_title_required) }
            return
        }
        val childId = current.selectedChildId
        if (childId == null) {
            _state.update { it.copy(error = R.string.trombadice_form_error_no_children) }
            return
        }

        _state.update { it.copy(submitting = true, error = null) }
        val occurredAt = localToInstant(current.date, current.time.hour, current.time.minute).toIsoUtc()

        viewModelScope.launch {
            val result = if (trombadiceId == null) {
                container.repository.createTrombadice(
                    TrombadiceCreateDto(
                        title = titulo,
                        description = current.description.trim(),
                        occurredAt = occurredAt,
                        childId = childId,
                        taskId = current.selectedTaskId,
                        category = current.category,
                    )
                )
            } else {
                container.repository.updateTrombadice(
                    trombadiceId,
                    TrombadiceUpdateDto(
                        title = titulo,
                        description = current.description.trim(),
                        occurredAt = occurredAt,
                        taskId = current.selectedTaskId,
                        category = current.category,
                    )
                )
            }

            _state.update {
                it.copy(
                    submitting = false,
                    saved = result is ApiResult.Success,
                    error = if (result is ApiResult.Success) null else R.string.login_error_network,
                )
            }
        }
    }
}
