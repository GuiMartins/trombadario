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

            val all = (container.repository.listPunishments() as? ApiResult.Success)?.data.orEmpty()
            _state.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    // isActive vem calculado do servidor: o relógio do celular
                    // não decide se alguém está de castigo.
                    active = all.filter { p -> p.isActive },
                    history = all.filterNot { p -> p.isActive },
                )
            }
        }
    }

    fun childName(childId: Int): String? =
        _state.value.children.firstOrNull { it.id == childId }?.displayName

    fun trombadiceTitle(id: Int): String? =
        _state.value.trombadices.firstOrNull { it.id == id }?.title

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

    fun startCreate() = _state.update {
        it.copy(editor = PunishmentEditor(childId = it.children.singleOrNull()?.id), error = null)
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
