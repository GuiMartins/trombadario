package com.trombadario.ui.trombadiceform

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trombadario.AppContainer
import com.trombadario.R
import com.trombadario.data.ApiResult
import com.trombadario.data.remote.CategoriaDeConquista
import com.trombadario.data.remote.Tipo
import com.trombadario.data.remote.TrombadiceCategoryDto
import com.trombadario.data.remote.TrombadiceCreateDto
import com.trombadario.data.remote.TrombadiceUpdateDto
import com.trombadario.data.remote.TaskDto
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

data class TrombadiceFormState(
    val loading: Boolean = true,
    val description: String = "",
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime = LocalTime.now().withSecond(0).withNano(0),
    val children: List<UserDto> = emptyList(),
    val selectedChildId: Int? = null,
    val tasks: List<TaskDto> = emptyList(),
    val selectedTaskId: Int? = null,
    val kind: String = Tipo.TROMBADICE,
    /** Os tipos cadastrados pelo pai. Sem nenhum não dá pra registrar
     *  trombadice - é o tipo que diz o que aconteceu. */
    val tipos: List<TrombadiceCategoryDto> = emptyList(),
    val categoryId: Int? = null,
    val conquistaCategory: String = CategoriaDeConquista.PADRAO,
    /**
     * Editando, a tela não promete castigo nenhum: corrigir o tipo ou a data de
     * uma anotação **não** recalcula o castigo que ela já gerou (a criança pode
     * já ter visto). Quem conserta um castigo é a tela de castigo.
     */
    val mostraCusto: Boolean = false,
    val submitting: Boolean = false,
    @StringRes val error: Int? = null,
    val saved: Boolean = false,
)

class TrombadiceFormViewModel(
    private val container: AppContainer,
    private val trombadiceId: Int?,
) : ViewModel() {

    private val _state = MutableStateFlow(TrombadiceFormState())
    val state: StateFlow<TrombadiceFormState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val children = (container.repository.listUsers() as? ApiResult.Success)
                ?.data
                ?.filter { !it.isAdmin && it.isActive }
                .orEmpty()

            val event = trombadiceId?.let {
                (container.repository.getTrombadice(it) as? ApiResult.Success)?.data
            }

            val tasks = (container.repository.listTasks() as? ApiResult.Success)
                ?.data
                ?.filter { it.isActive }
                .orEmpty()

            // O filho tem que ser resolvido **antes** dos tipos: a previsão de
            // castigo é por criança, porque a recorrência é dela.
            val childId = event?.childId ?: children.singleOrNull()?.id
            val tipos = (container.repository.listTrombadiceCategories(childId) as? ApiResult.Success)
                ?.data
                // Só os ativos na hora de cadastrar: o pai tirou os outros da
                // lista de propósito. O tipo da anotação que está sendo
                // corrigida entra mesmo aposentado - corrigir a data não pode
                // trocar o que aconteceu.
                ?.filter { it.isActive || it.id == event?.categoryId }
                .orEmpty()

            _state.update { current ->
                val occurred = event?.let { parseInstant(it.occurredAt).toLocalDateTime() }
                current.copy(
                    loading = false,
                    description = event?.description ?: current.description,
                    date = occurred?.toLocalDate() ?: current.date,
                    time = occurred?.toLocalTime() ?: current.time,
                    children = children,
                    // Editing keeps the event's own child; a new event defaults to
                    // the only child when there is just one, which is the usual case.
                    selectedChildId = childId,
                    tasks = tasks,
                    selectedTaskId = event?.taskId,
                    kind = event?.kind ?: current.kind,
                    tipos = tipos,
                    // Editando, o tipo é o do registro; cadastrando, o primeiro
                    // da lista - a tela sempre abre com um escolhido, porque
                    // escolher é obrigatório.
                    categoryId = event?.categoryId ?: tipos.firstOrNull()?.id,
                    conquistaCategory = event?.conquistaCategory ?: current.conquistaCategory,
                    mostraCusto = trombadiceId == null,
                )
            }
        }
    }

    /**
     * Repete a leitura dos tipos pra outro filho: o custo de cada um é por
     * criança, então trocar "de quem" troca os números.
     *
     * Descarta resposta velha comparando o filho pedido com o escolhido agora -
     * mesma guarda de `PunishmentViewModel.carregarInicio()`. Sem ela, dois
     * toques rápidos deixariam na tela o custo do irmão.
     */
    private fun recarregarPrevisao(childId: Int) {
        viewModelScope.launch {
            val tipos = (container.repository.listTrombadiceCategories(childId) as? ApiResult.Success)
                ?.data
                ?.filter { it.isActive }
                ?: return@launch
            _state.update { current ->
                if (current.selectedChildId != childId) current else current.copy(tipos = tipos)
            }
        }
    }

    fun onDescriptionChange(value: String) = _state.update { it.copy(description = value) }

    fun onDateChange(value: LocalDate) = _state.update { it.copy(date = value) }

    fun onTimeChange(value: LocalTime) = _state.update { it.copy(time = value) }

    fun onChildChange(childId: Int) {
        // Trocar de filho descarta a tarefa marcada: ela era de outro, e o
        // backend recusaria o vínculo.
        _state.update { it.copy(selectedChildId = childId, selectedTaskId = null, error = null) }
        recarregarPrevisao(childId)
    }

    fun onCategoryChange(categoryId: Int) = _state.update { it.copy(categoryId = categoryId) }

    fun onConquistaCategoryChange(valor: String) =
        _state.update { it.copy(conquistaCategory = valor) }

    /**
     * Trocar de tipo troca a lista de categorias inteira: são duas listas
     * diferentes, a cadastrada pelo pai (trombadice) e a fechada (conquista).
     * E conquista não se atrela a tarefa: o vínculo existe para dizer o que
     * **não** foi cumprido, e diria o contrário do que significa.
     */
    fun onKindChange(value: String) = _state.update {
        it.copy(
            kind = value,
            selectedTaskId = if (value == Tipo.CONQUISTA) null else it.selectedTaskId,
            error = null,
        )
    }

    /**
     * Escolher tarefa responde de quem é o registro - ela pertence a um filho
     * só. Por isso o campo de filho some da tela quando há tarefa; ver a mesma
     * regra no painel web.
     */
    fun onTaskChange(taskId: Int?) {
        _state.update { current ->
            val task = current.tasks.firstOrNull { it.id == taskId }
            current.copy(
                selectedTaskId = taskId,
                // A tarefa manda: ela pertence a um filho só.
                selectedChildId = task?.childId ?: current.selectedChildId,
                error = null,
            )
        }
        // A tarefa pode ter trocado o filho, e com ele o custo de cada tipo.
        _state.value.selectedChildId?.let(::recarregarPrevisao)
    }

    fun submit() {
        val current = _state.value
        if (current.submitting) return

        val childId = current.selectedChildId
        if (childId == null) {
            _state.update { it.copy(error = R.string.trombadice_form_error_no_children) }
            return
        }
        val conquista = current.kind == Tipo.CONQUISTA
        // Sem tipo escolhido a anotação não diria o que aconteceu - e desde que
        // o título saiu da tela, não há texto livre para salvá-la.
        if (!conquista && current.categoryId == null) {
            _state.update { it.copy(error = R.string.trombadice_form_error_no_category) }
            return
        }

        _state.update { it.copy(submitting = true, error = null) }
        val occurredAt = localToInstant(current.date, current.time.hour, current.time.minute).toIsoUtc()

        viewModelScope.launch {
            val result = if (trombadiceId == null) {
                container.repository.createTrombadice(
                    TrombadiceCreateDto(
                        description = current.description.trim(),
                        occurredAt = occurredAt,
                        childId = childId,
                        taskId = current.selectedTaskId,
                        kind = current.kind,
                        categoryId = if (conquista) null else current.categoryId,
                        conquistaCategory = if (conquista) current.conquistaCategory else null,
                    )
                )
            } else {
                container.repository.updateTrombadice(
                    trombadiceId,
                    TrombadiceUpdateDto(
                        description = current.description.trim(),
                        occurredAt = occurredAt,
                        taskId = current.selectedTaskId,
                        categoryId = if (conquista) null else current.categoryId,
                        conquistaCategory = if (conquista) current.conquistaCategory else null,
                    )
                )
            }

            _state.update {
                it.copy(
                    submitting = false,
                    saved = result is ApiResult.Success,
                    error = if (result is ApiResult.Success) null else R.string.login_error_network,
                )
            }
        }
    }
}
