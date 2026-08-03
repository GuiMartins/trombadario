package com.trombadario.ui.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SentimentDissatisfied
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trombadario.AppContainer
import com.trombadario.R
import com.trombadario.data.remote.TrombadiceDto
import com.trombadario.data.remote.UserDto
import com.trombadario.ui.components.AdaptiveScreen
import com.trombadario.ui.components.FiltroBar
import com.trombadario.ui.components.corDaConquista
import com.trombadario.ui.components.ehConquista
import com.trombadario.ui.components.rotuloDaCategoria
import com.trombadario.ui.theme.NotebookGutter
import com.trombadario.ui.components.transparentTopBar
import com.trombadario.ui.components.LoadingScreen
import com.trombadario.ui.components.MessageScreen
import com.trombadario.ui.components.formatDateTime
import com.trombadario.ui.components.parseInstant
import com.trombadario.ui.viewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    container: AppContainer,
    currentUser: UserDto,
    onOpenTrombadice: (Int) -> Unit,
    onNewTrombadice: () -> Unit,
) {
    val viewModel: FeedViewModel = viewModel(
        factory = viewModelFactory { FeedViewModel(container, currentUser) }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Covers both entering the screen and coming back to it - after saving a new
    // event, after editing one, after deleting one from the detail screen.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { TopAppBar(
                colors = transparentTopBar(),
                modifier = Modifier.padding(start = NotebookGutter),
                title = { Text(stringResource(R.string.feed_title)) }) },
        floatingActionButton = {
            if (currentUser.isAdmin) {
                FloatingActionButton(
                    // Círculo, não o squircle padrão do M3 - é o botão redondo
                    // do mockup.
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    onClick = onNewTrombadice) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.feed_new))
                }
            }
        },
    ) { padding ->
        AdaptiveScreen(modifier = Modifier.padding(padding)) {
            when {
                state.loading -> LoadingScreen()

                state.error -> MessageScreen(
                    icon = Icons.Default.SentimentDissatisfied,
                    title = stringResource(R.string.feed_error),
                    message = "",
                    actionLabel = stringResource(R.string.action_retry),
                    onAction = viewModel::load,
                )

                else -> Column(Modifier.fillMaxSize()) {
                    // Visão diária com calendário, filtro de tipo (bom/ruim) e
                    // busca - pro filho também, não só pro pai. A fileira de
                    // "qual filho" dentro do FiltroBar não aparece sozinha:
                    // state.children só é populado no ramo admin de load(), e
                    // o FiltroBar já esconde essa fileira com um filho só.
                    FiltroBar(
                        children = state.children,
                        selectedChildId = state.selectedChildId,
                        onSelectChild = viewModel::selectChild,
                        kind = state.kind,
                        onSelectKind = viewModel::selectKind,
                        category = state.category,
                        onSelectCategory = viewModel::selectCategory,
                        busca = state.busca,
                        onBuscaChange = viewModel::onBuscaChange,
                        onBuscar = viewModel::buscar,
                        dia = state.dia,
                        diasComRegistro = state.diasComRegistro,
                        onSelectDia = viewModel::selectDia,
                        filtrando = state.filtrando,
                        onLimpar = viewModel::limparFiltros,
                    )

                    PullToRefreshBox(
                        isRefreshing = state.refreshing,
                        onRefresh = { viewModel.load(isRefresh = true) },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    ) {
                        if (state.events.isEmpty()) {
                            MessageScreen(
                                icon = Icons.Default.SentimentDissatisfied,
                                title = stringResource(
                                    when {
                                        state.filtrando -> R.string.filtro_nenhum_resultado
                                        currentUser.isAdmin -> R.string.feed_empty_admin
                                        else -> R.string.feed_empty
                                    }
                                ),
                                message = "",
                            )
                        } else {
                            LazyColumn(
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp,
                                ),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(state.events, key = { it.id }) { event ->
                                    EventCard(
                                        event = event,
                                        childName = if (currentUser.isAdmin && state.children.size > 1) {
                                            viewModel.displayNameOf(event.childId)
                                        } else {
                                            null
                                        },
                                        // "Visto" é informação pro pai. Mostrar pro
                                        // filho que o pai sabe que ele viu não
                                        // acrescenta nada e pesa.
                                        mostrarVisto = currentUser.isAdmin,
                                        onClick = { onOpenTrombadice(event.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventCard(
    event: TrombadiceDto,
    childName: String?,
    mostrarVisto: Boolean,
    onClick: () -> Unit,
) {
    val conquista = ehConquista(event.kind)
    // Cor e ícone diferentes, não só texto: numa lista misturada o pai precisa
    // distinguir coisa boa de trombadice sem ler.
    val destaque = if (conquista) corDaConquista() else MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(if (conquista) 2.dp else 1.dp, destaque.takeIf { conquista }
            ?: MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                if (conquista) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = stringResource(R.string.tipo_conquista),
                        tint = destaque,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    text = parseInstant(event.occurredAt).formatDateTime(),
                    style = MaterialTheme.typography.labelMedium,
                    color = destaque,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = event.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (event.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = event.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(rotuloDaCategoria(event.category)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (childName != null) {
                    Text(
                        text = childName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (mostrarVisto) {
                    Text(
                        text = event.seenAt?.let {
                            stringResource(R.string.visto_em, parseInstant(it).formatDateTime())
                        } ?: stringResource(R.string.visto_ainda_nao),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (event.seenAt == null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}
