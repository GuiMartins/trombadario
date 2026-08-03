package com.trombadario.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trombadario.AppContainer
import com.trombadario.data.ApiResult
import com.trombadario.data.remote.TrombadiceDto
import com.trombadario.data.remote.UserDto
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FeedState(
    val loading: Boolean = true,
    val events: List<TrombadiceDto> = emptyList(),
    /** Only populated for the admin, to label and filter the feed. */
    val children: List<UserDto> = emptyList(),
    val selectedChildId: Int? = null,
    val category: String? = null,
    val busca: String = "",
    val dia: LocalDate? = null,
    /**
     * Os dias que têm registro, para o calendário só deixar clicar neles. Vem
     * do servidor já com os outros filtros aplicados - acender um dia sem
     * nenhuma agressão enquanto o filtro é "agressão" seria mentira.
     */
    val diasComRegistro: Set<LocalDate> = emptySet(),
    val error: Boolean = false,
) {
    /** Se há algum filtro além do padrão, para oferecer o "limpar". */
    val filtrando: Boolean
        get() = selectedChildId != null || category != null || busca.isNotBlank() || dia != null
}

class FeedViewModel(
    private val container: AppContainer,
    private val currentUser: UserDto,
) : ViewModel() {

    private val _state = MutableStateFlow(FeedState())
    val state: StateFlow<FeedState> = _state.asStateFlow()

    // No init { load() } on purpose: the screen loads on every ON_RESUME
    // instead. The ViewModel survives navigating to the form or the detail, so
    // loading once at construction would leave the feed stale after creating,
    // editing or deleting an event.
    fun load() {
        _state.update { it.copy(loading = true, error = false) }
        viewModelScope.launch {
            if (currentUser.isAdmin) {
                val users = container.repository.listUsers()
                if (users is ApiResult.Success) {
                    _state.update { current ->
                        current.copy(children = users.data.filter { !it.isAdmin })
                    }
                }
            }

            val filtros = _state.value
            val termo = filtros.busca.trim().ifBlank { null }
            val dia = filtros.dia?.toString()

            val datas = container.repository.trombadiceDates(
                childId = filtros.selectedChildId,
                category = filtros.category,
                q = termo,
            )
            if (datas is ApiResult.Success) {
                _state.update { current ->
                    current.copy(
                        diasComRegistro = datas.data.datas.mapNotNull(::diaOuNulo).toSet()
                    )
                }
            }

            val result = container.repository.listTrombadices(
                childId = filtros.selectedChildId,
                category = filtros.category,
                // Um dia só: o mesmo valor nas duas pontas, que é o que o
                // calendário oferece.
                de = dia,
                ate = dia,
                q = termo,
            )
            when (result) {
                is ApiResult.Success -> _state.update {
                    it.copy(loading = false, events = result.data, error = false)
                }
                // Unreachable already flipped the gate, which replaces this whole
                // screen - showing an error here too would just flash.
                ApiResult.Unreachable -> _state.update { it.copy(loading = false) }
                else -> _state.update { it.copy(loading = false, error = true) }
            }
        }
    }

    /** Data malformada vinda do servidor não pode derrubar o calendário. */
    private fun diaOuNulo(texto: String): LocalDate? =
        runCatching { LocalDate.parse(texto) }.getOrNull()

    fun selectChild(childId: Int?) = trocarFiltro { it.copy(selectedChildId = childId) }

    fun selectCategory(category: String?) = trocarFiltro { it.copy(category = category) }

    fun selectDia(dia: LocalDate?) = trocarFiltro { it.copy(dia = dia) }

    /** O texto muda a cada tecla; buscar só quando mandarem buscar. */
    fun onBuscaChange(value: String) = _state.update { it.copy(busca = value) }

    fun buscar() = load()

    fun limparFiltros() = trocarFiltro {
        it.copy(selectedChildId = null, category = null, busca = "", dia = null)
    }

    private fun trocarFiltro(mudanca: (FeedState) -> FeedState) {
        _state.update(mudanca)
        load()
    }

    fun displayNameOf(childId: Int): String? =
        _state.value.children.firstOrNull { it.id == childId }?.displayName
}
