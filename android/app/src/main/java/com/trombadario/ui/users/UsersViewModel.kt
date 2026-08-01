package com.trombadario.ui.users

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trombadario.AppContainer
import com.trombadario.R
import com.trombadario.data.ApiResult
import com.trombadario.data.remote.UserCreateDto
import com.trombadario.data.remote.UserDto
import com.trombadario.data.remote.UserUpdateDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** null id = creating a new account. */
data class UserEditor(
    val id: Int? = null,
    val username: String = "",
    val displayName: String = "",
    val password: String = "",
    val isAdmin: Boolean = false,
    val isActive: Boolean = true,
)

data class UsersState(
    val loading: Boolean = true,
    val users: List<UserDto> = emptyList(),
    val editor: UserEditor? = null,
    val confirmingDeleteOf: UserDto? = null,
    val submitting: Boolean = false,
    @StringRes val validationError: Int? = null,
    val serverError: String? = null,
)

class UsersViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(UsersState())
    val state: StateFlow<UsersState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val result = container.repository.listUsers()
            _state.update {
                it.copy(
                    loading = false,
                    users = (result as? ApiResult.Success)?.data.orEmpty(),
                )
            }
        }
    }

    fun startCreate() = _state.update {
        it.copy(editor = UserEditor(), validationError = null, serverError = null)
    }

    fun startEdit(user: UserDto) = _state.update {
        it.copy(
            editor = UserEditor(
                id = user.id,
                username = user.username,
                displayName = user.displayName,
                isAdmin = user.isAdmin,
                isActive = user.isActive,
            ),
            validationError = null,
            serverError = null,
        )
    }

    fun dismissEditor() = _state.update { it.copy(editor = null) }

    fun updateEditor(transform: (UserEditor) -> UserEditor) = _state.update { current ->
        current.copy(
            editor = current.editor?.let(transform),
            validationError = null,
            serverError = null,
        )
    }

    fun save() {
        val editor = _state.value.editor ?: return
        if (_state.value.submitting) return

        val creating = editor.id == null
        if (creating && editor.username.trim().length < 3) {
            _state.update { it.copy(validationError = R.string.users_error_username_too_short) }
            return
        }
        // On edit an empty password means "keep the current one", so it is only
        // length-checked when actually set.
        if ((creating || editor.password.isNotEmpty()) && editor.password.length < 6) {
            _state.update { it.copy(validationError = R.string.users_error_password_too_short) }
            return
        }

        _state.update { it.copy(submitting = true, validationError = null, serverError = null) }
        viewModelScope.launch {
            val result = if (creating) {
                container.repository.createUser(
                    UserCreateDto(
                        username = editor.username.trim(),
                        password = editor.password,
                        displayName = editor.displayName.ifBlank { editor.username }.trim(),
                        role = if (editor.isAdmin) UserDto.ROLE_ADMIN else UserDto.ROLE_CHILD,
                    )
                )
            } else {
                container.repository.updateUser(
                    editor.id!!,
                    UserUpdateDto(
                        displayName = editor.displayName.trim().ifBlank { null },
                        password = editor.password.ifBlank { null },
                        isActive = editor.isActive,
                    )
                )
            }

            when (result) {
                is ApiResult.Success -> {
                    _state.update { it.copy(submitting = false, editor = null) }
                    load()
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(submitting = false, serverError = result.message)
                }
                else -> _state.update {
                    it.copy(submitting = false, serverError = null, validationError = R.string.login_error_network)
                }
            }
        }
    }

    fun askDelete(user: UserDto) = _state.update { it.copy(confirmingDeleteOf = user) }

    fun cancelDelete() = _state.update { it.copy(confirmingDeleteOf = null) }

    fun confirmDelete() {
        val user = _state.value.confirmingDeleteOf ?: return
        viewModelScope.launch {
            val result = container.repository.deleteUser(user.id)
            _state.update {
                it.copy(
                    confirmingDeleteOf = null,
                    serverError = (result as? ApiResult.Failure)?.message,
                )
            }
            load()
        }
    }
}
