package com.trombadario.ui.punishment

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trombadario.AppContainer
import com.trombadario.R
import com.trombadario.data.remote.PunishmentDto
import com.trombadario.data.remote.UserDto
import com.trombadario.ui.components.AdaptiveScreen
import com.trombadario.ui.components.AppTopBar
import com.trombadario.ui.components.LoadingScreen
import com.trombadario.ui.components.ehConquista
import com.trombadario.ui.components.formatDateTime
import com.trombadario.ui.components.parseInstant
import com.trombadario.ui.viewModelFactory
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PunishmentScreen(container: AppContainer, currentUser: UserDto) {
    val viewModel: PunishmentViewModel = viewModel(
        factory = viewModelFactory { PunishmentViewModel(container, currentUser) }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { AppTopBar(title = stringResource(R.string.punishment_title), currentUser = currentUser) },
        floatingActionButton = {
            if (currentUser.isAdmin) {
                FloatingActionButton(
                    // Círculo, não o squircle padrão do M3 - é o botão redondo
                    // do mockup.
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    onClick = viewModel::startCreate) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.punishment_new))
                }
            }
        },
    ) { padding ->
        AdaptiveScreen(modifier = Modifier.padding(padding)) {
            when {
                state.loading -> LoadingScreen()
                else -> PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = { viewModel.load(isRefresh = true) },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (currentUser.isAdmin) {
                        AdminList(state, viewModel)
                    } else {
                        ChildAnswer(
                            ativos = state.active,
                            onReact = viewModel::react,
                        )
                    }
                }
            }
        }
    }

    state.editor?.let { editor ->
        PunishmentEditorDialog(
            editor = editor,
            state = state,
            viewModel = viewModel,
        )
    }

    // Excluir apaga de vez, do histórico e da tela do filho - por isso passa
    // por confirmação, como tarefa, anotação e conta.
    state.confirmingDeleteOf?.let { alvo ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text(stringResource(R.string.punishment_delete_confirm_title)) },
            text = {
                val quem = viewModel.childName(alvo.childId)
                Text(
                    listOfNotNull(quem, stringResource(R.string.punishment_delete_confirm_message))
                        .joinToString(" — ")
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDelete) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/**
 * Para o filho a tela existe pra responder uma coisa só, e a resposta tem que
 * ser legível de longe.
 *
 * Recebe os castigos e um jeito de reagir, não o ViewModel: a regra do prazo
 * mais distante é fácil de quebrar sem perceber, e assim dá pra medir
 * (`PunishmentChildAnswerTest`).
 */
@Composable
internal fun ChildAnswer(ativos: List<PunishmentDto>, onReact: (Int, String) -> Unit) {
    val livre = ativos.isEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = if (livre) Icons.Default.SentimentSatisfiedAlt else Icons.Default.Gavel,
            contentDescription = null,
            modifier = Modifier.size(88.dp),
            tint = if (livre) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(
                if (livre) R.string.punishment_free else R.string.punishment_grounded
            ),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))

        if (livre) {
            Text(
                text = stringResource(R.string.punishment_free_sub),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            // O prazo que a tela anuncia é o **mais distante** dos castigos
            // valendo, não o do primeiro da lista: com dois ao mesmo tempo, o
            // que acaba antes dizia à criança que ela ficaria livre num dia em
            // que ainda estaria de castigo.
            Text(
                text = stringResource(
                    R.string.punishment_until,
                    ativos.maxOf { parseInstant(it.endsAt) }.formatDateTime(),
                ),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            // Todos, não só o primeiro. Além de esconder metade do motivo, a
            // tela antiga fazia o servidor mentir: `/current` carimba
            // `seen_at` em todo castigo ativo, então o pai lia "visto" de um
            // castigo que nunca chegou a aparecer pra criança.
            ativos.forEach { castigo ->
                CastigoAtivo(
                    castigo = castigo,
                    mostrarPrazo = ativos.size > 1,
                    onReact = { texto -> onReact(castigo.id, texto) },
                )
            }
        }
    }
}

/**
 * Um castigo valendo agora, na tela do filho. Com um só, é o que a tela sempre
 * mostrou; com mais de um, cada bloco repete o próprio prazo, senão não dá pra
 * saber qual motivo pertence a qual data.
 */
@Composable
private fun CastigoAtivo(
    castigo: PunishmentDto,
    mostrarPrazo: Boolean,
    onReact: (String) -> Unit,
) {
    if (mostrarPrazo) {
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(
                R.string.punishment_until,
                parseInstant(castigo.endsAt).formatDateTime(),
            ),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
    }
    if (castigo.reason.isNotBlank()) {
        Spacer(Modifier.height(24.dp))
        Text(
            text = castigo.reason,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
    // O servidor já manda as anotações completas, não só o id - o filho não
    // precisa de uma segunda busca só pra saber o título de cada uma. Mesmo bug
    // do painel web (PR #21 tratou outro; este é um bug de tela, não de dado: a
    // API sempre mandou isso).
    if (castigo.trombadices.isNotEmpty()) {
        Spacer(Modifier.height(24.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            castigo.trombadices.forEach { t ->
                Text(
                    text = "• ${t.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
    Spacer(Modifier.height(32.dp))
    ReactionSection(reactionText = castigo.reactionText, onSend = onReact)
}

/**
 * Fileira de emoji prontos pra toque único + campo de texto livre - o pedido
 * foi explicitamente os dois, não só um. Tocar num emoji insere no campo em
 * vez de mandar na hora, pra dar pra combinar emoji com palavras.
 */
@Composable
private fun ReactionSection(reactionText: String?, onSend: (String) -> Unit) {
    var texto by remember(reactionText) { mutableStateOf(reactionText.orEmpty()) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.punishment_reaction_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            REACTION_EMOJIS.forEach { emoji ->
                TextButton(onClick = { texto += emoji }) {
                    Text(emoji, style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = texto,
            onValueChange = { if (it.length <= REACTION_MAX_LENGTH) texto = it },
            placeholder = { Text(stringResource(R.string.punishment_reaction_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { onSend(texto) }) {
            Text(stringResource(R.string.punishment_reaction_send))
        }
    }
}

// Mesmo limite do backend (String(256)) - ver PunishmentReaction em schemas.py.
private const val REACTION_MAX_LENGTH = 256
private val REACTION_EMOJIS = listOf("😢", "😠", "😐", "😔", "😳")

@Composable
private fun AdminList(state: PunishmentState, viewModel: PunishmentViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.active.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Text(
                        text = stringResource(R.string.punishment_admin_empty),
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(state.active, key = { it.id }) { p ->
                PunishmentCard(p, viewModel, ativo = true)
            }
        }

        if (state.history.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.punishment_history),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
            }
            items(state.history, key = { it.id }) { p ->
                PunishmentCard(p, viewModel, ativo = false)
            }
        }
    }
}

@Composable
private fun PunishmentCard(p: PunishmentDto, viewModel: PunishmentViewModel, ativo: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(
                    when {
                        ativo -> R.string.punishment_active
                        p.endedEarlyAt != null -> R.string.punishment_ended_early
                        else -> R.string.punishment_served
                    }
                ),
                style = MaterialTheme.typography.labelMedium,
                color = if (ativo) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = viewModel.childName(p.childId) ?: "",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    R.string.punishment_until,
                    parseInstant(p.endsAt).formatDateTime(),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (p.reason.isNotBlank()) {
                Text(
                    text = p.reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Do próprio castigo, não de uma busca à parte: procurar o título
            // na lista de anotações fazia a causa sumir em silêncio quando ela
            // não estava lá (`mapNotNull`) - e a tela do filho já fazia certo.
            p.trombadices.forEach { t ->
                Text(
                    text = "• ${t.title}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // O filho escreveu, o pai só lê - mesmo peso visual da lista de
            // anotações acima, não escondido.
            if (!p.reactionText.isNullOrBlank()) {
                Text(
                    text = "💬 ${p.reactionText}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Só na lista do pai, que é onde este card aparece: "visto" é
            // informação pra ele, não pro filho.
            Text(
                text = p.seenAt?.let {
                    stringResource(R.string.visto_em, parseInstant(it).formatDateTime())
                } ?: stringResource(R.string.visto_ainda_nao),
                style = MaterialTheme.typography.labelSmall,
                color = if (p.seenAt == null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            // Rola de lado: três botões de texto não cabem lado a lado num
            // celular estreito, e "Encerrar agora" é o mais largo deles.
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = { viewModel.startEdit(p) }) {
                    Text(stringResource(R.string.action_edit))
                }
                if (ativo) {
                    TextButton(onClick = { viewModel.endNow(p) }) {
                        Text(stringResource(R.string.punishment_end_now))
                    }
                } else if (p.endedEarlyAt != null) {
                    // Encerrar por engano marcava o castigo como "encerrado
                    // antes" pra sempre; o prazo original nunca foi apagado,
                    // então voltar atrás é só limpar o carimbo.
                    TextButton(onClick = { viewModel.reopen(p) }) {
                        Text(stringResource(R.string.punishment_reopen))
                    }
                }
                TextButton(onClick = { viewModel.askDelete(p) }) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PunishmentEditorDialog(
    editor: PunishmentEditor,
    state: PunishmentState,
    viewModel: PunishmentViewModel,
) {
    var escolhendo by remember { mutableStateOf<Campo?>(null) }
    // Só as trombadices do filho escolhido aparecem: marcar a de um irmão
    // colocaria o nome dele no castigo deste. Conquista fica de fora: o
    // servidor recusa ("conquista não causa castigo") e oferecer o que ele vai
    // recusar só produz erro.
    val doFilho = state.trombadices.filter {
        it.childId == editor.childId && !ehConquista(it.kind)
    }
    // As já marcadas entram sempre, mesmo fora das 20 mais recentes: corrigir
    // um castigo antigo não pode perder em silêncio a causa dele.
    val candidatas = (doFilho.take(20) + doFilho.filter { it.id in editor.trombadiceIds })
        .distinctBy { it.id }

    AlertDialog(
        onDismissRequest = viewModel::dismissEditor,
        title = {
            Text(
                stringResource(
                    if (editor.isEditing) R.string.punishment_edit else R.string.punishment_new
                )
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (state.children.size > 1) {
                    Text(
                        stringResource(R.string.punishment_who),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.children.forEach { child ->
                            FilterChip(
                                selected = editor.childId == child.id,
                                onClick = {
                                    viewModel.updateEditor {
                                        // Troca de filho limpa a seleção: as
                                        // trombadices marcadas eram de outro.
                                        it.copy(childId = child.id, trombadiceIds = emptySet())
                                    }
                                },
                                label = { Text(child.displayName) },
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // Quando começou é editável: cadastrado com a data errada, o
                // registro já nasce errado, e antes disto a única saída era
                // encerrar - o que deixava no histórico um castigo "cumprido em
                // parte" que nunca houve.
                Text(
                    stringResource(R.string.punishment_starts_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = { escolhendo = Campo.INICIO_DATA }) {
                        Text(editor.startDate.format(DATA))
                    }
                    TextButton(onClick = { escolhendo = Campo.INICIO_HORA }) {
                        Text(editor.startTime.format(HORA))
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.punishment_until_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = { escolhendo = Campo.FIM_DATA }) {
                        Text(editor.endDate.format(DATA))
                    }
                    TextButton(onClick = { escolhendo = Campo.FIM_HORA }) {
                        Text(editor.endTime.format(HORA))
                    }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = editor.reason,
                    onValueChange = { v -> viewModel.updateEditor { it.copy(reason = v) } },
                    label = { Text(stringResource(R.string.punishment_reason)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(16.dp))
                if (doFilho.isEmpty()) {
                    // Sem nenhuma trombadice deste filho não há castigo a
                    // aplicar - melhor dizer isso do que deixar salvar e levar
                    // um erro do servidor.
                    Text(
                        stringResource(R.string.punishment_no_trombadices),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        stringResource(R.string.punishment_pick_trombadices),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    candidatas.forEach { t ->
                        FilterChip(
                            selected = t.id in editor.trombadiceIds,
                            onClick = {
                                viewModel.updateEditor {
                                    it.copy(
                                        trombadiceIds = if (t.id in it.trombadiceIds) {
                                            it.trombadiceIds - t.id
                                        } else {
                                            it.trombadiceIds + t.id
                                        }
                                    )
                                }
                            },
                            label = { Text(t.title, maxLines = 1) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        )
                    }
                }

                if (state.error != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(state.error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::save, enabled = !state.submitting) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::dismissEditor) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )

    val alvo = escolhendo
    if (alvo == Campo.INICIO_DATA || alvo == Campo.FIM_DATA) {
        val inicio = alvo == Campo.INICIO_DATA
        // DatePicker fala em millis de meia-noite UTC, não LocalDate; passar
        // pelo fuso do sistema aqui deslocaria a data em um dia.
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (if (inicio) editor.startDate else editor.endDate)
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { escolhendo = null },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val dia = LocalDate.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC)
                        viewModel.updateEditor {
                            if (inicio) it.copy(startDate = dia) else it.copy(endDate = dia)
                        }
                    }
                    escolhendo = null
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { escolhendo = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) { DatePicker(state = pickerState) }
    }

    if (alvo == Campo.INICIO_HORA || alvo == Campo.FIM_HORA) {
        val inicio = alvo == Campo.INICIO_HORA
        val atual = if (inicio) editor.startTime else editor.endTime
        val pickerState = rememberTimePickerState(
            initialHour = atual.hour,
            initialMinute = atual.minute,
            is24Hour = true,
        )
        DatePickerDialog(
            onDismissRequest = { escolhendo = null },
            confirmButton = {
                TextButton(onClick = {
                    val hora = LocalTime.of(pickerState.hour, pickerState.minute)
                    viewModel.updateEditor {
                        if (inicio) it.copy(startTime = hora) else it.copy(endTime = hora)
                    }
                    escolhendo = null
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { escolhendo = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { TimePicker(state = pickerState) }
        }
    }
}

/** Qual dos quatro botões de data/hora abriu o seletor. */
private enum class Campo { INICIO_DATA, INICIO_HORA, FIM_DATA, FIM_HORA }

private val DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val HORA = DateTimeFormatter.ofPattern("HH:mm")
