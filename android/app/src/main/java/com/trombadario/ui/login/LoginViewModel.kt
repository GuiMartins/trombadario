package com.trombadario.ui.login

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trombadario.AppContainer
import com.trombadario.R
import com.trombadario.data.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginState(
    val username: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val submitting: Boolean = false,
    @StringRes val error: Int? = null,
)

class LoginViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    fun onUsernameChange(value: String) = _state.update { it.copy(username = value, error = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun togglePasswordVisibility() = _state.update { it.copy(passwordVisible = !it.passwordVisible) }

    fun submit() {
        val current = _state.value
        if (current.username.isBlank() || current.password.isBlank() || current.submitting) return

        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val result = container.repository.login(current.username.trim(), current.password)
            _state.update {
                when (result) {
                    // Success needs no state change: saving the token flips the
                    // root of the navigation graph and this screen goes away.
                    is ApiResult.Success -> it.copy(submitting = false, password = "")
                    ApiResult.Unauthorized -> it.copy(submitting = false, error = R.string.login_error_invalid)
                    else -> it.copy(submitting = false, error = R.string.login_error_network)
                }
            }
        }
    }
}
