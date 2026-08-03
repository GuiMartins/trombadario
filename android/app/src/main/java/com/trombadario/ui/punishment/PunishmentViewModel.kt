package com.trombadario.ui.punishment

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trombadario.AppContainer
import com.trombadario.R
import com.trombadario.data.ApiResult
import com.trombadario.data.remote.PunishmentCreateDto
import com.trombadario.data.remote.PunishmentDto
import com.trombadario.data.remote.PunishmentUpdateDto
import com.trombadario.data.remote.TrombadiceDto
import com.trombadario.data.remote.UserDto
import com.trombadario.ui.components.localToInstant
import com.trombadario.ui.components.toIsoUtc
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PunishmentEditor(
    val childId: Int? = null,
    val reason: String = "",
    val date: LocalDate = LocalDate.now().plusDays(1),
    val time: LocalTime = LocalTime.of(20, 0),
    val trombadiceIds: Set<Int> = emptySet(),
)

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

    fun startCreate() = _state.update {
        it.copy(editor = PunishmentEditor(childId = it.children.singleOrNull()?.id), error = null)
    }

    fun dismissEditor() = _state.update { it.copy(editor = null) }

    fun updateEditor(transform: (PunishmentEditor) -> PunishmentEditor) = _state.update { current ->
        current.copy(editor = current.editor?.let(transform), error = null)
    }

    fun save() {
        val editor = _state.value.editor ?: return
        val childId = editor.childId
        if (childId == null || _state.value.submitting) return

        val endsAt = localToInstant(editor.date, editor.time.hour, editor.time.minute)
        if (!endsAt.isAfter(java.time.Instant.now())) {
            _state.update { it.copy(error = R.string.punishment_error_end_before_start) }
            return
        }

        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val result = container.repository.createPunishment(
                PunishmentCreateDto(
                    childId = childId,
                    endsAt = endsAt.toIsoUtc(),
                    reason = editor.reason.trim(),
                    // Só as do filho escolhido: o backend recusa o resto, e
                    // mandar o que ele vai recusar só produziria erro.
                    trombadiceIds = editor.trombadiceIds
                        .filter { id ->
                            _state.value.trombadices.any { it.id == id && it.childId == childId }
                        },
                )
            )
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
}
