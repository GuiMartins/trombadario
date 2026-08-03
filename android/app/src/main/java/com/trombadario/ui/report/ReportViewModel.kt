package com.trombadario.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trombadario.AppContainer
import com.trombadario.data.ApiResult
import com.trombadario.data.remote.ReportDto
import com.trombadario.data.remote.UserDto
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** As mesmas três janelas do painel: como foi esses dias, como foi o mês, e
 *  está melhorando. */
val JANELAS = listOf(7, 30, 90)

data class ReportState(
    val loading: Boolean = true,
    val dados: ReportDto? = null,
    val children: List<UserDto> = emptyList(),
    val selectedChildId: Int? = null,
    val dias: Int = 30,
    val error: Boolean = false,
)

class ReportViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(ReportState())
    val state: StateFlow<ReportState> = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(loading = true, error = false) }
        viewModelScope.launch {
            val users = container.repository.listUsers()
            if (users is ApiResult.Success) {
                _state.update { atual ->
                    atual.copy(children = users.data.filter { !it.isAdmin })
                }
            }

            val filtros = _state.value
            // A janela termina hoje: relatório é sobre o que já aconteceu.
            val ate = LocalDate.now()
            val resultado = container.repository.report(
                childId = filtros.selectedChildId,
                de = ate.minusDays((filtros.dias - 1).toLong()).toString(),
                ate = ate.toString(),
            )
            when (resultado) {
                is ApiResult.Success -> _state.update {
                    it.copy(loading = false, dados = resultado.data, error = false)
                }
                ApiResult.Unreachable -> _state.update { it.copy(loading = false) }
                else -> _state.update { it.copy(loading = false, error = true) }
            }
        }
    }

    fun selectChild(childId: Int?) {
        _state.update { it.copy(selectedChildId = childId) }
        load()
    }

    fun selectJanela(dias: Int) {
        _state.update { it.copy(dias = dias) }
        load()
    }
}
