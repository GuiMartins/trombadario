package com.trombadario.ui.splash

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trombadario.AppContainer
import com.trombadario.R
import com.trombadario.data.ApiResult
import com.trombadario.data.remote.SplashMessageCreateDto
import com.trombadario.data.remote.SplashMessageDto
import com.trombadario.data.remote.SplashMessageUpdateDto
import com.trombadario.data.remote.UserDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** null id = criando; childId null = vale pra todos os filhos. */
data class SplashMessageEditor(
    val id: Int? = null,
    val text: String = "",
    val childId: Int? = null,
)

data class SplashMessagesState(
    val loading: Boolean = true,
    val messages: List<SplashMessageDto> = emptyList(),
    val children: List<UserDto> = emptyList(),
    val editor: SplashMessageEditor? = null,
    val confirmingDeleteOf: SplashMessageDto? = null,
    val submitting: Boolean = false,
    @StringRes val error: Int? = null,
)

class SplashMessagesViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(SplashMessagesState())
    val state: StateFlow<SplashMessagesState> = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            (container.repository.listUsers() as? ApiResult.Success)?.let { users ->
                _state.update { c -> c.copy(children = users.data.filter { !it.isAdmin && it.isActive }) }
            }
            val result = container.repository.listSplashMessages()
            _state.update {
                it.copy(loading = false, messages = (result as? ApiResult.Success)?.data.orEmpty())
            }
        }
    }

    fun childName(childId: Int): String? =
        _state.value.children.firstOrNull { it.id == childId }?.displayName

    fun startCreate() = _state.update { it.copy(editor = SplashMessageEditor(), error = null) }

    fun startEdit(message: SplashMessageDto) = _state.update {
        it.copy(
            editor = SplashMessageEditor(
                id = message.id,
                text = message.text,
                childId = message.childId,
            ),
            error = null,
        )
    }

    fun dismissEditor() = _state.update { it.copy(editor = null) }

    fun updateEditor(transform: (SplashMessageEditor) -> SplashMessageEditor) =
        _state.update { current -> current.copy(editor = current.editor?.let(transform), error = null) }

    fun save() {
        val editor = _state.value.editor ?: return
        if (_state.value.submitting) return
        if (editor.text.isBlank()) {
            _state.update { it.copy(error = R.string.splash_error_empty) }
            return
        }

        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val result = if (editor.id == null) {
                container.repository.createSplashMessage(
                    SplashMessageCreateDto(text = editor.text.trim(), childId = editor.childId)
                )
            } else {
                container.repository.updateSplashMessage(
                    editor.id,
                    SplashMessageUpdateDto(text = editor.text.trim(), childId = editor.childId),
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

    fun toggleActive(message: SplashMessageDto) {
        viewModelScope.launch {
            container.repository.updateSplashMessage(
                message.id,
                SplashMessageUpdateDto(isActive = !message.isActive),
            )
            load()
        }
    }

    fun askDelete(message: SplashMessageDto) = _state.update { it.copy(confirmingDeleteOf = message) }

    fun cancelDelete() = _state.update { it.copy(confirmingDeleteOf = null) }

    fun confirmDelete() {
        val message = _state.value.confirmingDeleteOf ?: return
        viewModelScope.launch {
            container.repository.deleteSplashMessage(message.id)
            _state.update { it.copy(confirmingDeleteOf = null) }
            load()
        }
    }
}
