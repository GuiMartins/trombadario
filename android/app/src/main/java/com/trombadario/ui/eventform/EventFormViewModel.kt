package com.trombadario.ui.eventform

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trombadario.AppContainer
import com.trombadario.R
import com.trombadario.data.ApiResult
import com.trombadario.data.remote.EventCreateDto
import com.trombadario.data.remote.EventUpdateDto
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

data class EventFormState(
    val loading: Boolean = true,
    val title: String = "",
    val description: String = "",
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime = LocalTime.now().withSecond(0).withNano(0),
    val children: List<UserDto> = emptyList(),
    val selectedChildId: Int? = null,
    val submitting: Boolean = false,
    @StringRes val error: Int? = null,
    val saved: Boolean = false,
)

class EventFormViewModel(
    private val container: AppContainer,
    private val eventId: Int?,
) : ViewModel() {

    private val _state = MutableStateFlow(EventFormState())
    val state: StateFlow<EventFormState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val children = (container.repository.listUsers() as? ApiResult.Success)
                ?.data
                ?.filter { !it.isAdmin && it.isActive }
                .orEmpty()

            val event = eventId?.let {
                (container.repository.getEvent(it) as? ApiResult.Success)?.data
            }

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
                )
            }
        }
    }

    fun onTitleChange(value: String) = _state.update { it.copy(title = value, error = null) }

    fun onDescriptionChange(value: String) = _state.update { it.copy(description = value) }

    fun onDateChange(value: LocalDate) = _state.update { it.copy(date = value) }

    fun onTimeChange(value: LocalTime) = _state.update { it.copy(time = value) }

    fun onChildChange(childId: Int) = _state.update { it.copy(selectedChildId = childId, error = null) }

    fun submit() {
        val current = _state.value
        if (current.submitting) return
        if (current.title.isBlank()) {
            _state.update { it.copy(error = R.string.event_form_error_title_required) }
            return
        }
        val childId = current.selectedChildId
        if (childId == null) {
            _state.update { it.copy(error = R.string.event_form_error_no_children) }
            return
        }

        _state.update { it.copy(submitting = true, error = null) }
        val occurredAt = localToInstant(current.date, current.time.hour, current.time.minute).toIsoUtc()

        viewModelScope.launch {
            val result = if (eventId == null) {
                container.repository.createEvent(
                    EventCreateDto(
                        title = current.title.trim(),
                        description = current.description.trim(),
                        occurredAt = occurredAt,
                        childId = childId,
                    )
                )
            } else {
                container.repository.updateEvent(
                    eventId,
                    EventUpdateDto(
                        title = current.title.trim(),
                        description = current.description.trim(),
                        occurredAt = occurredAt,
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
