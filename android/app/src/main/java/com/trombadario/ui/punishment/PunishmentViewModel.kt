package com.trombadario.ui.punishment

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trombadario.AppContainer
import com.trombadario.R
import com.trombadario.data.ApiResult
import com.trombadario.data.remote.PunishmentCreateDto
import com.trombadario.data.remote.PunishmentDto
import com.trombadario.data.remote.PunishmentReactionDto
import com.trombadario.data.remote.PunishmentUpdateDto
import com.trombadario.data.remote.TrombadiceDto
import com.trombadario.data.remote.UserDto
import com.trombadario.ui.components.localToInstant
import com.trombadario.ui.components.parseInstant
import com.trombadario.ui.components.toIsoUtc
import com.trombadario.ui.components.toLocalDateTime
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * O mesmo editor serve pra aplicar e pra corrigir - `id` nulo é castigo novo.
 * Formulário separado seria um segundo lugar pra lembrar de mexer.
 */
data class PunishmentEditor(
    val id: Int? = null,
    val childId: Int? = null,
    val reason: String = "",
    // Quando começou passou a ser editável: cadastrado com a data errada, o
    // registro já nasce errado, e a única saída antes disto era encerrar - o
    // que deixava no histórico um castigo "cumprido em parte" que nunca houve.
    val startDate: LocalDate = LocalDate.now(),
    val startTime: LocalTime = LocalTime.now().withSecond(0).withNano(0),
    val endDate: LocalDate = LocalDate.now().plusDays(1),
    val endTime: LocalTime = LocalTime.of(20, 0),
    val trombadiceIds: Set<Int> = emptySet(),
    /** Quando o começo já vem preenchido com o fim de um castigo que existe -
     *  é a fila. Só serve pra tela dizer isso; quem emenda de verdade é o
     *  servidor, e o pai pode mudar a data à vontade. */
    val emFila: Boolean = false,
    /** Se o pai já escolheu o começo na mão. A resposta da fila só preenche o
     *  campo enquanto ninguém mexeu nele - chegando depois, sobrescreveria uma
     *  escolha deliberada. */
    val startTouched: Boolean = false,
) {
    val isEditing: Boolean get() = id != null
}

data class PunishmentState(
    val loading: Boolean = true,
    /** Puxar-pra-atualizar: diferente de `loading`, que é a tela cheia da
     *  primeira entrada. Os dois nunca ficam `true` ao mesmo tempo. */
    val refreshing: Boolean = false,
    /** Ativos agora - é a resposta que a tela do filho existe pra dar. */
    val active: List<PunishmentDto> = emptyList(),
    /** A fila: dados, mas ainda não começaram. Só o pai tem esta lista - o
     *  servidor nem manda castigo da fila pro filho. */
    val scheduled: List<PunishmentDto> = emptyList(),
    /** Só do pai, pelo mesmo motivo: o filho não vê o que já cumpriu. */
    val history: List<PunishmentDto> = emptyList(),
    val children: List<UserDto> = emptyList(),
    val trombadices: List<TrombadiceDto> = emptyList(),
    val editor: PunishmentEditor? = null,
    /** Excluir não tem volta, então passa por confirmação - mesmo padrão de
     *  tarefa, anotação e conta. */
    val confirmingDeleteOf: PunishmentDto? = null,
    val submitting: Boolean = false,
    @StringRes val error: Int? = null,
)

class PunishmentViewModel(
    private val container: AppContainer,
    private val currentUser: UserDto,
) : ViewModel() {

    private val _state = MutableStateFlow(PunishmentState())
    val state: StateFlow<PunishmentState> = _state.asStateFlow()

    fun load(isRefresh: Boolean = false) {
        _state.update { if (isRefresh) it.copy(refreshing = true) else it.copy(loading = true) }
        viewModelScope.launch {
            if (currentUser.isAdmin) {
                (container.repository.listUsers() as? ApiResult.Success)?.let { users ->
                    _state.update { c -> c.copy(children = users.data.filter { !it.isAdmin && it.isActive }) }
                }
                (container.repository.listTrombadices() as? ApiResult.Success)?.let { list ->
                    _state.update { it.copy(trombadices = list.data) }
                }
            }

            // O filho pergunta uma coisa só, e é `/current` que responde: estou
            // de castigo agora? A lista inteira é do pai - o servidor já corta
            // o resto pra ele, então pedir a lista aqui traria o mesmo castigo
            // por um caminho mais comprido.
            val all = if (currentUser.isAdmin) {
                (container.repository.listPunishments() as? ApiResult.Success)?.data.orEmpty()
            } else {
                (container.repository.currentPunishments() as? ApiResult.Success)?.data.orEmpty()
            }
            _state.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    // isActive e isScheduled vêm calculados do servidor: o
                    // relógio do celular não decide se alguém está de castigo
                    // nem quando o próximo começa.
                    active = all.filter { p -> p.isActive },
                    scheduled = all.filter { p -> p.isScheduled },
                    history = all.filterNot { p -> p.isActive || p.isScheduled },
                )
            }
        }
    }

    fun childName(childId: Int): String? =
        _state.value.children.firstOrNull { it.id == childId }?.displayName

    /** Só o filho chama isto - o backend também barra, mas a tela nem oferece
     *  o campo pro pai (ver ChildAnswer). */
    fun react(punishmentId: Int, text: String) {
        viewModelScope.launch {
            val result = container.repository.reactToPunishment(
                punishmentId,
                PunishmentReactionDto(reactionText = text.ifBlank { null }),
            )
            if (result is ApiResult.Success) {
                _state.update { current ->
                    current.copy(
                        active = current.active.map { if (it.id == punishmentId) result.data else it },
                        history = current.history.map { if (it.id == punishmentId) result.data else it },
                    )
                }
            }
        }
    }

    fun startCreate() {
        val childId = _state.value.children.singleOrNull()?.id
        _state.update { it.copy(editor = PunishmentEditor(childId = childId), error = null) }
        if (childId != null) carregarInicio(childId)
    }

    /** Trocar de filho troca a fila: cada um tem a sua, e as trombadices
     *  marcadas eram de outro. */
    fun chooseChild(childId: Int) {
        val editando = _state.value.editor?.isEditing ?: return
        updateEditor { it.copy(childId = childId, trombadiceIds = emptySet(), emFila = false) }
        // Corrigindo um castigo antigo não: a fila é sobre castigo novo, e
        // repreencher a data apagaria a que está sendo corrigida.
        if (!editando) carregarInicio(childId)
    }

    /**
     * Pergunta ao servidor quando o castigo começaria e já preenche o
     * formulário com isso: agora, ou emendado no fim do que este filho está
     * cumprindo. É o que faz "mais um dia" ser aplicar outro castigo em vez de
     * fazer conta de calendário.
     *
     * O prazo sugerido é um dia depois **desse começo**, não depois de agora:
     * emendando num castigo que termina amanhã, "amanhã" daria um castigo que
     * acaba antes de começar.
     */
    private fun carregarInicio(childId: Int) {
        viewModelScope.launch {
            val result = container.repository.nextPunishmentStart(childId)
            if (result is ApiResult.Success) {
                val inicio = parseInstant(result.data.startsAt).toLocalDateTime()
                _state.update { current ->
                    val editor = current.editor ?: return@update current
                    // A resposta pode chegar depois de o pai trocar de filho ou
                    // mexer na data: aí ela não vale mais.
                    if (editor.childId != childId || editor.startTouched) return@update current
                    current.copy(
                        editor = editor.copy(
                            startDate = inicio.toLocalDate(),
                            startTime = inicio.toLocalTime(),
                            endDate = inicio.toLocalDate().plusDays(1),
                            // Emendando, o prazo novo guarda a hora do castigo
                            // anterior: "mais um dia" acaba no mesmo horário
                            // que o de agora acabaria, não às 20:00. Sem fila,
                            // o padrão da tela (20:00) continua valendo - ali o
                            // começo é agora, e a hora de agora não sugere nada.
                            endTime = if (result.data.emFila) {
                                inicio.toLocalTime()
                            } else {
                                editor.endTime
                            },
                            emFila = result.data.emFila,
                        )
                    )
                }
            }
        }
    }

    fun startEdit(punishment: PunishmentDto) {
        val inicio = parseInstant(punishment.startsAt).toLocalDateTime()
        val fim = parseInstant(punishment.endsAt).toLocalDateTime()
        _state.update {
            it.copy(
                editor = PunishmentEditor(
                    id = punishment.id,
                    childId = punishment.childId,
                    reason = punishment.reason,
                    startDate = inicio.toLocalDate(),
                    startTime = inicio.toLocalTime(),
                    endDate = fim.toLocalDate(),
                    endTime = fim.toLocalTime(),
                    trombadiceIds = punishment.trombadiceIds.toSet(),
                ),
                error = null,
            )
        }
    }

    fun dismissEditor() = _state.update { it.copy(editor = null) }

    fun updateEditor(transform: (PunishmentEditor) -> PunishmentEditor) = _state.update { current ->
        current.copy(editor = current.editor?.let(transform), error = null)
    }

    fun save() {
        val editor = _state.value.editor ?: return
        val childId = editor.childId
        if (childId == null || _state.value.submitting) return

        val startsAt =
            localToInstant(editor.startDate, editor.startTime.hour, editor.startTime.minute)
        val endsAt = localToInstant(editor.endDate, editor.endTime.hour, editor.endTime.minute)
        // Contra o início escolhido, não contra o relógio: um castigo pode ser
        // registrado depois do fato, e nesse caso os dois já estão no passado.
        if (!endsAt.isAfter(startsAt)) {
            _state.update { it.copy(error = R.string.punishment_error_end_before_start) }
            return
        }

        // Só as do filho escolhido: o backend recusa o resto, e mandar o que ele
        // vai recusar só produziria erro.
        val causas = editor.trombadiceIds.filter { id ->
            _state.value.trombadices.any { it.id == id && it.childId == childId }
        }
        // Castigo vem de alguma coisa que aconteceu, e essa coisa já está
        // registrada - o servidor recusa castigo solto (400).
        if (causas.isEmpty()) {
            _state.update { it.copy(error = R.string.punishment_error_needs_trombadice) }
            return
        }

        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val result = if (editor.id == null) {
                container.repository.createPunishment(
                    PunishmentCreateDto(
                        childId = childId,
                        startsAt = startsAt.toIsoUtc(),
                        endsAt = endsAt.toIsoUtc(),
                        reason = editor.reason.trim(),
                        trombadiceIds = causas,
                    )
                )
            } else {
                container.repository.updatePunishment(
                    editor.id,
                    PunishmentUpdateDto(
                        childId = childId,
                        startsAt = startsAt.toIsoUtc(),
                        endsAt = endsAt.toIsoUtc(),
                        reason = editor.reason.trim(),
                        trombadiceIds = causas,
                    ),
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

    fun endNow(punishment: PunishmentDto) {
        viewModelScope.launch {
            container.repository.updatePunishment(punishment.id, PunishmentUpdateDto(endNow = true))
            load()
        }
    }

    /** Desfaz o Encerrar. O prazo original nunca foi apagado, então voltar
     *  atrás é só limpar o carimbo - mesmo espírito de desmarcar tarefa. */
    fun reopen(punishment: PunishmentDto) {
        viewModelScope.launch {
            container.repository.updatePunishment(punishment.id, PunishmentUpdateDto(endNow = false))
            load()
        }
    }

    fun askDelete(punishment: PunishmentDto) =
        _state.update { it.copy(confirmingDeleteOf = punishment) }

    fun cancelDelete() = _state.update { it.copy(confirmingDeleteOf = null) }

    fun confirmDelete() {
        val alvo = _state.value.confirmingDeleteOf ?: return
        _state.update { it.copy(confirmingDeleteOf = null) }
        viewModelScope.launch {
            container.repository.deletePunishment(alvo.id)
            load()
        }
    }
}
