package com.trombadario.ui.tipos

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trombadario.AppContainer
import com.trombadario.R
import com.trombadario.data.ApiResult
import com.trombadario.data.remote.TrombadiceCategoryCreateDto
import com.trombadario.data.remote.TrombadiceCategoryDto
import com.trombadario.data.remote.TrombadiceCategoryUpdateDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** null id = criando. */
data class TipoEditor(
    val id: Int? = null,
    val name: String = "",
    /** Vazio ao criar: sem posição pedida o servidor põe no fim da lista. */
    val position: String = "",
    /**
     * Os três números do castigo, como texto porque vêm de campo de texto.
     * Vazio conta como zero, que é o desligado: em `punishmentDays` quer dizer
     * "este tipo não gera castigo", em `maxDays` quer dizer "sem teto".
     */
    val punishmentDays: String = "",
    val escalationDays: String = "",
    val maxDays: String = "",
)

data class TiposState(
    val loading: Boolean = true,
    val tipos: List<TrombadiceCategoryDto> = emptyList(),
    val editor: TipoEditor? = null,
    val confirmingDeleteOf: TrombadiceCategoryDto? = null,
    val submitting: Boolean = false,
    @StringRes val error: Int? = null,
)

/**
 * Os tipos de trombadice, cadastrados pelo pai.
 *
 * Mesma tela do painel web (`/tipos`), porque toda funcionalidade da conta pai
 * nasce nos dois lugares.
 */
class TiposViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(TiposState())
    val state: StateFlow<TiposState> = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val result = container.repository.listTrombadiceCategories()
            _state.update {
                it.copy(loading = false, tipos = (result as? ApiResult.Success)?.data.orEmpty())
            }
        }
    }

    fun startCreate() = _state.update { it.copy(editor = TipoEditor(), error = null) }

    fun startEdit(tipo: TrombadiceCategoryDto) = _state.update {
        it.copy(
            editor = TipoEditor(
                id = tipo.id,
                name = tipo.name,
                position = tipo.position.toString(),
                punishmentDays = tipo.punishmentDays.toString(),
                escalationDays = tipo.escalationDays.toString(),
                maxDays = tipo.maxDays.toString(),
            ),
            error = null,
        )
    }

    fun dismissEditor() = _state.update { it.copy(editor = null) }

    fun updateEditor(transform: (TipoEditor) -> TipoEditor) =
        _state.update { current -> current.copy(editor = current.editor?.let(transform), error = null) }

    fun save() {
        val editor = _state.value.editor ?: return
        if (_state.value.submitting) return
        if (editor.name.isBlank()) {
            _state.update { it.copy(error = R.string.tipos_error_empty) }
            return
        }

        val base = editor.punishmentDays.numero()
        val teto = editor.maxDays.numero()
        if (teto > 0 && teto < base) {
            // Os dois números se contradizem. O servidor também recusa (400);
            // barrar aqui evita a ida e volta pra dizer o óbvio.
            _state.update { it.copy(error = R.string.tipos_error_teto_menor) }
            return
        }

        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val posicao = editor.position.trim().toIntOrNull()
            val aumento = editor.escalationDays.numero()
            val result = if (editor.id == null) {
                container.repository.createTrombadiceCategory(
                    TrombadiceCategoryCreateDto(
                        name = editor.name.trim(),
                        position = posicao,
                        punishmentDays = base,
                        escalationDays = aumento,
                        maxDays = teto,
                    )
                )
            } else {
                container.repository.updateTrombadiceCategory(
                    editor.id,
                    TrombadiceCategoryUpdateDto(
                        name = editor.name.trim(),
                        position = posicao,
                        punishmentDays = base,
                        escalationDays = aumento,
                        maxDays = teto,
                    ),
                )
            }

            when {
                result is ApiResult.Success -> {
                    _state.update { it.copy(submitting = false, editor = null) }
                    load()
                }
                // 409: dois tipos com o mesmo nome partiriam o relatório ao
                // meio sem ninguém perceber.
                result is ApiResult.Failure && result.code == 409 ->
                    _state.update { it.copy(submitting = false, error = R.string.tipos_error_repetido) }
                result is ApiResult.Failure && result.code == 400 ->
                    _state.update { it.copy(submitting = false, error = R.string.tipos_error_teto_menor) }
                else ->
                    _state.update { it.copy(submitting = false, error = R.string.login_error_network) }
            }
        }
    }

    /** Aposentar sem mexer no que passou: some da hora de registrar e continua
     *  nomeando o que já foi registrado com ele. */
    fun toggleActive(tipo: TrombadiceCategoryDto) {
        viewModelScope.launch {
            container.repository.updateTrombadiceCategory(
                tipo.id,
                TrombadiceCategoryUpdateDto(isActive = !tipo.isActive),
            )
            load()
        }
    }

    fun askDelete(tipo: TrombadiceCategoryDto) = _state.update { it.copy(confirmingDeleteOf = tipo) }

    fun cancelDelete() = _state.update { it.copy(confirmingDeleteOf = null) }

    fun confirmDelete() {
        val tipo = _state.value.confirmingDeleteOf ?: return
        viewModelScope.launch {
            container.repository.deleteTrombadiceCategory(tipo.id)
            _state.update { it.copy(confirmingDeleteOf = null) }
            load()
        }
    }
}

/** Campo de número vazio conta como zero - é o desligado, não um erro. */
private fun String.numero(): Int = trim().toIntOrNull()?.coerceAtLeast(0) ?: 0
