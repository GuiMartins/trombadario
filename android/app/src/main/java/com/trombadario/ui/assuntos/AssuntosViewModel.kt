package com.trombadario.ui.assuntos

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trombadario.AppContainer
import com.trombadario.R
import com.trombadario.data.ApiResult
import com.trombadario.data.remote.AssuntoConversaDto
import com.trombadario.data.remote.AssuntoCreateDto
import com.trombadario.data.remote.AssuntoDto
import com.trombadario.data.remote.AssuntoUpdateDto
import com.trombadario.data.remote.UserDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** null id = criando. `childId` só é escolhido pelo pai; do lado do filho o
 *  servidor ignora o que vier e usa a conta dele. */
data class AssuntoEditor(
    val id: Int? = null,
    val title: String = "",
    val description: String = "",
    val childId: Int? = null,
)

data class AssuntosState(
    val loading: Boolean = true,
    /** Puxar-pra-atualizar: diferente de `loading`, que é a tela cheia da
     *  primeira entrada. Os dois nunca ficam `true` ao mesmo tempo. */
    val refreshing: Boolean = false,
    val assuntos: List<AssuntoDto> = emptyList(),
    /** Só populado pro pai, pra rotular de quem é cada assunto e pra escolher
     *  com quem é a conversa ao cadastrar. */
    val children: List<UserDto> = emptyList(),
    /** A aba de agora. Filtrado aqui e não numa segunda chamada de API: a lista
     *  de uma casa cabe inteira na memória, e assim trocar de aba é instantâneo. */
    val mostrandoPendentes: Boolean = true,
    val editor: AssuntoEditor? = null,
    val confirmingDeleteOf: AssuntoDto? = null,
    val submitting: Boolean = false,
    /** Trava a tela inteira quando o pai desligou o acesso: o servidor responde
     *  403 e não existe o que mostrar. */
    val bloqueado: Boolean = false,
    @StringRes val error: Int? = null,
) {
    val assuntosDaAba: List<AssuntoDto>
        get() = assuntos.filter { it.pendente == mostrandoPendentes }
}

class AssuntosViewModel(
    private val container: AppContainer,
    private val currentUser: UserDto,
) : ViewModel() {

    private val _state = MutableStateFlow(AssuntosState())
    val state: StateFlow<AssuntosState> = _state.asStateFlow()

    fun load(isRefresh: Boolean = false) {
        _state.update { if (isRefresh) it.copy(refreshing = true) else it.copy(loading = true) }
        viewModelScope.launch {
            if (currentUser.isAdmin) {
                (container.repository.listUsers() as? ApiResult.Success)?.let { users ->
                    _state.update { c -> c.copy(children = users.data.filter { !it.isAdmin && it.isActive }) }
                }
            }
            val result = container.repository.listAssuntos()
            _state.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    // 403 aqui é `can_discuss` desligado no meio da sessão - a
                    // tela não tenta adivinhar isso pelo UserDto guardado.
                    bloqueado = result is ApiResult.Forbidden,
                    assuntos = (result as? ApiResult.Success)?.data.orEmpty(),
                )
            }
        }
    }

    fun childName(childId: Int): String? =
        _state.value.children.firstOrNull { it.id == childId }?.displayName

    fun selecionarAba(pendentes: Boolean) =
        _state.update { it.copy(mostrandoPendentes = pendentes) }

    fun startCreate() = _state.update {
        it.copy(
            editor = AssuntoEditor(childId = it.children.singleOrNull()?.id),
            error = null,
        )
    }

    /** Só o autor corrige, e só antes da conversa - as duas regras são do
     *  servidor; aqui a tela só não oferece o que ele recusaria. */
    fun podeEditar(assunto: AssuntoDto): Boolean =
        assunto.pendente && assunto.authorId == currentUser.id

    fun startEdit(assunto: AssuntoDto) = _state.update {
        it.copy(
            editor = AssuntoEditor(
                id = assunto.id,
                title = assunto.title,
                description = assunto.description,
                childId = assunto.childId,
            ),
            error = null,
        )
    }

    fun dismissEditor() = _state.update { it.copy(editor = null, error = null) }

    fun updateEditor(transform: (AssuntoEditor) -> AssuntoEditor) = _state.update { current ->
        current.copy(editor = current.editor?.let(transform), error = null)
    }

    fun save() {
        val editor = _state.value.editor ?: return
        if (_state.value.submitting) return
        if (editor.title.isBlank()) {
            _state.update { it.copy(error = R.string.assuntos_error_title_required) }
            return
        }
        if (currentUser.isAdmin && editor.childId == null) {
            _state.update { it.copy(error = R.string.assuntos_error_child_required) }
            return
        }

        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val result = if (editor.id == null) {
                container.repository.createAssunto(
                    AssuntoCreateDto(
                        title = editor.title.trim(),
                        description = editor.description.trim(),
                        childId = editor.childId,
                    )
                )
            } else {
                container.repository.updateAssunto(
                    editor.id,
                    AssuntoUpdateDto(
                        title = editor.title.trim(),
                        description = editor.description.trim(),
                    ),
                )
            }

            when (result) {
                is ApiResult.Success -> {
                    _state.update { it.copy(submitting = false, editor = null) }
                    load()
                }
                ApiResult.Forbidden -> _state.update {
                    it.copy(submitting = false, error = R.string.assuntos_bloqueado)
                }
                else -> _state.update {
                    it.copy(submitting = false, error = R.string.login_error_network)
                }
            }
        }
    }

    /** Só o pai chama - a tela nem oferece o botão pro filho, e o servidor
     *  responde 403 de qualquer forma. */
    fun marcarConversado(assunto: AssuntoDto, note: String) = conversa(assunto, true, note)

    fun reabrir(assunto: AssuntoDto) = conversa(assunto, false, "")

    private fun conversa(assunto: AssuntoDto, talked: Boolean, note: String) {
        viewModelScope.launch {
            val result = container.repository.markAssuntoTalked(
                assunto.id,
                AssuntoConversaDto(talked = talked, note = note.trim()),
            )
            if (result is ApiResult.Success) {
                _state.update { current ->
                    current.copy(
                        assuntos = current.assuntos.map {
                            if (it.id == assunto.id) result.data else it
                        }
                    )
                }
            }
        }
    }

    /** O pai apaga qualquer um; o filho, só o que ele mesmo trouxe e ninguém
     *  conversou ainda. */
    fun podeApagar(assunto: AssuntoDto): Boolean =
        currentUser.isAdmin || (assunto.authorId == currentUser.id && assunto.pendente)

    fun askDelete(assunto: AssuntoDto) = _state.update { it.copy(confirmingDeleteOf = assunto) }

    fun cancelDelete() = _state.update { it.copy(confirmingDeleteOf = null) }

    fun confirmDelete() {
        val assunto = _state.value.confirmingDeleteOf ?: return
        viewModelScope.launch {
            container.repository.deleteAssunto(assunto.id)
            _state.update { it.copy(confirmingDeleteOf = null) }
            load()
        }
    }
}
