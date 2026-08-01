package com.trombadario.ui.serversetup

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trombadario.AppContainer
import com.trombadario.R
import com.trombadario.data.ServerUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ServerSetupState(
    val url: String = "http://192.168.",
    val checking: Boolean = false,
    @StringRes val error: Int? = null,
)

class ServerSetupViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(ServerSetupState())
    val state: StateFlow<ServerSetupState> = _state.asStateFlow()

    fun onUrlChange(url: String) {
        _state.update { it.copy(url = url, error = null) }
    }

    fun connect() {
        val url = normalize(_state.value.url)
        if (url == null) {
            _state.update { it.copy(error = R.string.server_setup_error_invalid_url) }
            return
        }

        _state.update { it.copy(checking = true, error = null) }
        viewModelScope.launch {
            val result = runCatching { container.probe(url) }
            _state.update { current ->
                result.fold(
                    onSuccess = { health ->
                        if (health.app == EXPECTED_APP) {
                            current.copy(checking = false)
                        } else {
                            current.copy(
                                checking = false,
                                error = R.string.server_setup_error_not_trombadario,
                            )
                        }
                    },
                    onFailure = {
                        current.copy(
                            checking = false,
                            error = R.string.server_setup_error_unreachable,
                        )
                    },
                )
            }
            result.getOrNull()?.takeIf { it.app == EXPECTED_APP }?.let { health ->
                // Pairing is the last step on purpose: the gate watches this
                // value, so writing it flips the whole app out of setup.
                container.serverConfigStore.pair(url, health.serverId)
                container.homeNetworkGate.refresh()
            }
        }
    }

    private fun normalize(raw: String): String? = ServerUrl.normalize(raw)

    private companion object {
        const val EXPECTED_APP = "trombadario"
    }
}
