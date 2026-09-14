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

/**
 * Os limites que a API impõe em cada campo (os `Field(...)` de
 * `TrombadiceCategoryCreate`, no backend).
 *
 * Estão aqui porque a tela tem que recusar **antes** de mandar: o que passa
 * deles volta 422, e 422 é uma recusa que nenhuma tela sabia nomear - virava
 * "não consegui falar com o servidor", que mandava procurar problema no Wi-Fi
 * enquanto o servidor estava ali, respondendo. O painel web já barrava isso no
 * próprio formulário (`maxlength`/`max` em tipos.html); o app é que não.
 */
object LimitesDoTipo {
    const val NOME = 60
    const val ORDEM = 999
    const val DIAS = 365
}

/** O que a própria tela recusa, antes de qualquer requisição. */
enum class ErroDoTipo {
    NOME_VAZIO,
    NOME_LONGO,
    ORDEM_ALTA,
    DIAS_DEMAIS,
    TETO_MENOR,
}

/**
 * O que impede este tipo de ser salvo, ou `null` quando está tudo certo.
 *
 * Função pura e fora do ViewModel para o teste poder chamá-la direto - as
 * mesmas regras valem no servidor, que é quem decide de verdade (o filho tem o
 * APK na mão), mas repetidas aqui elas viram recado em português em vez de um
 * 422 em inglês.
 */
fun validar(editor: TipoEditor): ErroDoTipo? {
    val nome = editor.name.trim()
    if (nome.isEmpty()) return ErroDoTipo.NOME_VAZIO
    if (nome.length > LimitesDoTipo.NOME) return ErroDoTipo.NOME_LONGO

    val ordem = editor.position.trim().toIntOrNull()
    if (ordem != null && ordem > LimitesDoTipo.ORDEM) return ErroDoTipo.ORDEM_ALTA

    val base = editor.punishmentDays.numero()
    val aumento = editor.escalationDays.numero()
    val teto = editor.maxDays.numero()
    if (maxOf(base, aumento, teto) > LimitesDoTipo.DIAS) return ErroDoTipo.DIAS_DEMAIS
    // Os dois números se contradizem. O servidor também recusa (400); barrar
    // aqui evita a ida e volta pra dizer o óbvio.
    if (teto > 0 && teto < base) return ErroDoTipo.TETO_MENOR

    return null
}

data class TiposState(
    val loading: Boolean = true,
    /**
     * A lista não carregou. Diferente de lista vazia, e por isso um campo
     * próprio: dizer "nenhum tipo cadastrado" quando a leitura falhou manda o
     * pai cadastrar de novo o que já existe.
     */
    val loadFailed: Boolean = false,
    val tipos: List<TrombadiceCategoryDto> = emptyList(),
    val editor: TipoEditor? = null,
    val confirmingDeleteOf: TrombadiceCategoryDto? = null,
    val submitting: Boolean = false,
    /** O que a tela recusou, ou o motivo que ela sabe nomear. */
    @StringRes val validationError: Int? = null,
    /** O que o servidor respondeu quando recusou com algo que a tela não
     *  nomeia. Mesmo par de campos da tela de Contas. */
    val serverError: String? = null,
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
                when (result) {
                    is ApiResult.Success ->
                        it.copy(loading = false, loadFailed = false, tipos = result.data)
                    else -> it.copy(loading = false, loadFailed = true, tipos = emptyList())
                }
            }
        }
    }

    fun startCreate() = _state.update {
        it.copy(editor = TipoEditor(), validationError = null, serverError = null)
    }

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
            validationError = null,
            serverError = null,
        )
    }

    fun dismissEditor() = _state.update { it.copy(editor = null) }

    fun updateEditor(transform: (TipoEditor) -> TipoEditor) = _state.update { current ->
        current.copy(
            editor = current.editor?.let(transform),
            validationError = null,
            serverError = null,
        )
    }

    fun save() {
        val editor = _state.value.editor ?: return
        if (_state.value.submitting) return

        validar(editor)?.let { erro ->
            _state.update { it.copy(validationError = erro.mensagem(), serverError = null) }
            return
        }

        _state.update { it.copy(submitting = true, validationError = null, serverError = null) }
        viewModelScope.launch {
            val posicao = editor.position.trim().toIntOrNull()
            val base = editor.punishmentDays.numero()
            val aumento = editor.escalationDays.numero()
            val teto = editor.maxDays.numero()
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

            if (result is ApiResult.Success) {
                _state.update { it.copy(submitting = false, editor = null) }
                load()
                return@launch
            }
            _state.update { it.copy(submitting = false).comFalha(result) }
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

/**
 * A recusa do servidor virando recado de tela.
 *
 * Antes tudo o que não era 409 nem 400 caía em "não consegui falar com o
 * servidor" - inclusive o 422, que é o servidor respondendo, e inclusive o 404
 * de um servidor velho demais pra conhecer os tipos. Os dois mandavam o pai
 * olhar a rede, que estava boa.
 */
private fun TiposState.comFalha(result: ApiResult<*>): TiposState = when {
    // 409: dois tipos com o mesmo nome partiriam o relatório ao meio sem
    // ninguém perceber.
    result is ApiResult.Failure && result.code == 409 ->
        copy(validationError = R.string.tipos_error_repetido)
    result is ApiResult.Failure && result.code == 400 ->
        copy(validationError = R.string.tipos_error_teto_menor)
    // O servidor recusou com algo que esta tela não sabe nomear. Dizer o que
    // ele disse é melhor que inventar um motivo - e sem mensagem sobra o
    // recado genérico, que ao menos aponta pro lugar certo.
    result is ApiResult.Failure ->
        copy(
            serverError = result.message,
            validationError = R.string.tipos_error_recusado.takeIf { result.message == null },
        )
    // A rota não existe nesse servidor: o Trombadário do ZimaOS é anterior aos
    // tipos cadastrados. É o app conversando com um servidor velho, não falta
    // de rede.
    result is ApiResult.NotFound -> copy(validationError = R.string.tipos_error_servidor_antigo)
    result is ApiResult.Forbidden -> copy(validationError = R.string.tipos_error_sem_permissao)
    // Sobrou o que é de rede mesmo (e o 401, que já derruba a sessão sozinho).
    else -> copy(validationError = R.string.login_error_network)
}

@StringRes
private fun ErroDoTipo.mensagem(): Int = when (this) {
    ErroDoTipo.NOME_VAZIO -> R.string.tipos_error_empty
    ErroDoTipo.NOME_LONGO -> R.string.tipos_error_nome_longo
    ErroDoTipo.ORDEM_ALTA -> R.string.tipos_error_ordem_alta
    ErroDoTipo.DIAS_DEMAIS -> R.string.tipos_error_dias_demais
    ErroDoTipo.TETO_MENOR -> R.string.tipos_error_teto_menor
}

/** Campo de número vazio conta como zero - é o desligado, não um erro. */
private fun String.numero(): Int = trim().toIntOrNull()?.coerceAtLeast(0) ?: 0
