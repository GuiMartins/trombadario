package com.trombadario.ui.trombadicedetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trombadario.AppContainer
import com.trombadario.data.ApiResult
import com.trombadario.data.remote.TrombadiceDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TrombadiceDetailState(
    val loading: Boolean = true,
    val event: TrombadiceDto? = null,
    val confirmingDelete: Boolean = false,
    val deleted: Boolean = false,
    /** Resolvido a partir do task_id, pra tela mostrar nome e não número. */
    val taskName: String? = null,
)

class TrombadiceDetailViewModel(
    private val container: AppContainer,
    private val trombadiceId: Int?,
) : ViewModel() {

    private val _state = MutableStateFlow(TrombadiceDetailState())
    val state: StateFlow<TrombadiceDetailState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        if (trombadiceId == null) {
            _state.update { it.copy(loading = false) }
            return
        }
        viewModelScope.launch {
            val trombadice = (container.repository.getTrombadice(trombadiceId) as? ApiResult.Success)
                ?.data

            val taskName = trombadice?.taskId?.let { taskId ->
                (container.repository.listTasks() as? ApiResult.Success)
                    ?.data
                    ?.firstOrNull { it.id == taskId }
                    ?.name
            }

            _state.update {
                it.copy(loading = false, event = trombadice, taskName = taskName)
            }
        }
    }

    fun askDelete() = _state.update { it.copy(confirmingDelete = true) }

    fun cancelDelete() = _state.update { it.copy(confirmingDelete = false) }

    fun confirmDelete() {
        val id = trombadiceId ?: return
        viewModelScope.launch {
            val result = container.repository.deleteTrombadice(id)
            _state.update {
                it.copy(
                    confirmingDelete = false,
                    deleted = result is ApiResult.Success,
                )
            }
        }
    }
}
