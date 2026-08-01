package com.trombadario.ui.eventdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trombadario.AppContainer
import com.trombadario.data.ApiResult
import com.trombadario.data.remote.EventDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EventDetailState(
    val loading: Boolean = true,
    val event: EventDto? = null,
    val confirmingDelete: Boolean = false,
    val deleted: Boolean = false,
)

class EventDetailViewModel(
    private val container: AppContainer,
    private val eventId: Int?,
) : ViewModel() {

    private val _state = MutableStateFlow(EventDetailState())
    val state: StateFlow<EventDetailState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        if (eventId == null) {
            _state.update { it.copy(loading = false) }
            return
        }
        viewModelScope.launch {
            val result = container.repository.getEvent(eventId)
            _state.update {
                it.copy(
                    loading = false,
                    event = (result as? ApiResult.Success)?.data,
                )
            }
        }
    }

    fun askDelete() = _state.update { it.copy(confirmingDelete = true) }

    fun cancelDelete() = _state.update { it.copy(confirmingDelete = false) }

    fun confirmDelete() {
        val id = eventId ?: return
        viewModelScope.launch {
            val result = container.repository.deleteEvent(id)
            _state.update {
                it.copy(
                    confirmingDelete = false,
                    deleted = result is ApiResult.Success,
                )
            }
        }
    }
}
