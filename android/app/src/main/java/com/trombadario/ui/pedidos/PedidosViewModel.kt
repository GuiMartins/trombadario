package com.trombadario.ui.pedidos

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trombadario.AppContainer
import com.trombadario.R
import com.trombadario.data.ApiResult
import com.trombadario.data.remote.PedidoCreateDto
import com.trombadario.data.remote.PedidoDecisionDto
import com.trombadario.data.remote.PedidoDto
import com.trombadario.data.remote.UserDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** null = fechado. Só o filho abre. */
data class PedidoEditor(val title: String = "", val justification: String = "")

data class PedidosState(
    val loading: Boolean = true,
    /** Puxar-pra-atualizar: diferente de `loading`, que é a tela cheia da
     *  primeira entrada. Os dois nunca ficam `true` ao mesmo tempo. */
    val refreshing: Boolean = false,
    val pedidos: List<PedidoDto> = emptyList(),
    /** Só populado pro pai, pra rotular de quem é cada pedido. */
    val children: List<UserDto> = emptyList(),
    val editor: PedidoEditor? = null,
    val submitting: Boolean = false,
    @StringRes val error: Int? = null,
)

class PedidosViewModel(
    private val container: AppContainer,
    private val currentUser: UserDto,
) : ViewModel() {

    private val _state = MutableStateFlow(PedidosState())
    val state: StateFlow<PedidosState> = _state.asStateFlow()

    fun load(isRefresh: Boolean = false) {
        _state.update { if (isRefresh) it.copy(refreshing = true) else it.copy(loading = true) }
        viewModelScope.launch {
            if (currentUser.isAdmin) {
                (container.repository.listUsers() as? ApiResult.Success)?.let { users ->
                    _state.update { current ->
                        current.copy(children = users.data.filter { !it.isAdmin })
                    }
                }
            }
            val result = container.repository.listPedidos()
            _state.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    pedidos = (result as? ApiResult.Success)?.data.orEmpty(),
                )
            }
        }
    }

    fun childName(childId: Int): String? =
        _state.value.children.firstOrNull { it.id == childId }?.displayName

    fun startCreate() = _state.update { it.copy(editor = PedidoEditor(), error = null) }

    fun dismissEditor() = _state.update { it.copy(editor = null, error = null) }

    fun updateEditor(transform: (PedidoEditor) -> PedidoEditor) = _state.update { current ->
        current.copy(editor = current.editor?.let(transform), error = null)
    }

    fun save() {
        val editor = _state.value.editor ?: return
        if (editor.title.isBlank() || _state.value.submitting) {
            _state.update { it.copy(error = R.string.trombadice_form_error_title_required) }
            return
        }
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val result = container.repository.createPedido(
                PedidoCreateDto(title = editor.title.trim(), justification = editor.justification.trim())
            )
            when (result) {
                is ApiResult.Success -> {
                    _state.update { it.copy(submitting = false, editor = null) }
                    load()
                }
                ApiResult.Forbidden -> _state.update {
                    it.copy(submitting = false, error = R.string.pedidos_bloqueado)
                }
                else -> _state.update { it.copy(submitting = false, error = R.string.login_error_network) }
            }
        }
    }

    fun decide(pedido: PedidoDto, aprovado: Boolean, note: String) {
        viewModelScope.launch {
            val status = if (aprovado) PedidoDto.APROVADO else PedidoDto.NEGADO
            val result = container.repository.decidePedido(
                pedido.id, PedidoDecisionDto(status = status, decisionNote = note.trim())
            )
            if (result is ApiResult.Success) {
                _state.update { current ->
                    current.copy(pedidos = current.pedidos.map { if (it.id == pedido.id) result.data else it })
                }
            }
        }
    }
}
