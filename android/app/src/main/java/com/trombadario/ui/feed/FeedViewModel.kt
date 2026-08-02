package com.trombadario.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trombadario.AppContainer
import com.trombadario.data.ApiResult
import com.trombadario.data.remote.TrombadiceDto
import com.trombadario.data.remote.UserDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FeedState(
    val loading: Boolean = true,
    val events: List<TrombadiceDto> = emptyList(),
    /** Only populated for the admin, to label and filter the feed. */
    val children: List<UserDto> = emptyList(),
    val selectedChildId: Int? = null,
    val error: Boolean = false,
)

class FeedViewModel(
    private val container: AppContainer,
    private val currentUser: UserDto,
) : ViewModel() {

    private val _state = MutableStateFlow(FeedState())
    val state: StateFlow<FeedState> = _state.asStateFlow()

    // No init { load() } on purpose: the screen loads on every ON_RESUME
    // instead. The ViewModel survives navigating to the form or the detail, so
    // loading once at construction would leave the feed stale after creating,
    // editing or deleting an event.
    fun load() {
        _state.update { it.copy(loading = true, error = false) }
        viewModelScope.launch {
            if (currentUser.isAdmin) {
                val users = container.repository.listUsers()
                if (users is ApiResult.Success) {
                    _state.update { current ->
                        current.copy(children = users.data.filter { !it.isAdmin })
                    }
                }
            }

            when (val result = container.repository.listTrombadices(_state.value.selectedChildId)) {
                is ApiResult.Success -> _state.update {
                    it.copy(loading = false, events = result.data, error = false)
                }
                // Unreachable already flipped the gate, which replaces this whole
                // screen - showing an error here too would just flash.
                ApiResult.Unreachable -> _state.update { it.copy(loading = false) }
                else -> _state.update { it.copy(loading = false, error = true) }
            }
        }
    }

    fun selectChild(childId: Int?) {
        _state.update { it.copy(selectedChildId = childId) }
        load()
    }

    fun displayNameOf(childId: Int): String? =
        _state.value.children.firstOrNull { it.id == childId }?.displayName
}
