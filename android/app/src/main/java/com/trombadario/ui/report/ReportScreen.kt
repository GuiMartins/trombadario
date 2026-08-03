package com.trombadario.ui.report

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SentimentDissatisfied
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.trombadario.data.remote.ContagemDto
import com.trombadario.ui.components.AdaptiveScreen
import com.trombadario.ui.components.LoadingScreen
import com.trombadario.ui.components.MessageScreen
import com.trombadario.ui.components.rotuloDaCategoria
import com.trombadario.ui.components.transparentTopBar
import com.trombadario.ui.theme.NotebookGutter
import com.trombadario.ui.viewModelFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DIA_CURTO = DateTimeFormatter.ofPattern("dd/MM")

/**
 * O mesmo relatório do painel, com os mesmos números - eles vêm do mesmo
 * endpoint, não de uma segunda contagem feita aqui.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(container: AppContainer) {
    val viewModel: ReportViewModel = viewModel(
        factory = viewModelFactory { ReportViewModel(container) }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = transparentTopBar(),
                modifier = Modifier.padding(start = NotebookGutter),
                title = { Text(stringResource(R.string.report_title)) },
            )
        },
    ) { padding ->
        AdaptiveScreen(modifier = Modifier.padding(padding)) {
            val dados = state.dados
            when {
                state.loading -> LoadingScreen()

                state.error || dados == null -> MessageScreen(
                    icon = Icons.Default.SentimentDissatisfied,
                    title = stringResource(R.string.feed_error),
                    message = "",
                    actionLabel = stringResource(R.string.action_retry),
                    onAction = viewModel::load,
                )

                else -> Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                ) {
                    if (state.children.size > 1) {
                        Linha {
                            FilterChip(
                                selected = state.selectedChildId == null,
                                onClick = { viewModel.selectChild(null) },
                                label = { Text(stringResource(R.string.feed_filter_all)) },
                            )
                            state.children.forEach { child ->
                                FilterChip(
                                    selected = state.selectedChildId == child.id,
                                    onClick = { viewModel.selectChild(child.id) },
                                    label = { Text(child.displayName) },
                                )
                            }
                        }
                    }

                    Linha {
                        JANELAS.forEach { n ->
                            FilterChip(
                                selected = state.dias == n,
                                onClick = { viewModel.selectJanela(n) },
                                label = { Text(stringResource(janelaLabel(n))) },
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(
                            R.string.report_periodo,
                            formatarDia(dados.de),
                            formatarDia(dados.ate),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(16.dp))
                    Numerao(
                        listOf(
                            dados.total.toString() to R.string.report_total,
                            dados.maiorSequenciaLimpa.toString() to R.string.report_sequencia_limpa,
                            dados.diasComRegistro.toString() to R.string.report_dias_com_registro,
                            dados.mediaPorDiaComRegistro.toString() to R.string.report_media,
                            dados.diasDeCastigo.toString() to R.string.report_dias_castigo,
                            dados.naoVistas.toString() to R.string.report_nao_vistas,
                        )
                    )

                    if (dados.total == 0) {
                        Spacer(Modifier.height(24.dp))
                        Text(
                            text = stringResource(R.string.report_vazio),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(48.dp))
                        return@Column
                    }

                    Secao(R.string.report_por_dia)
                    SerieDiaria(dados.porDia)
                    dados.diaMaisPesado?.let {
                        Text(
                            text = stringResource(
                                R.string.report_dia_mais_pesado,
                                formatarDia(it.rotulo),
                                it.total,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Secao(R.string.report_por_tipo)
                    Barras(
                        dados.porCategoria.map { ContagemDto(rotuloLegivel(it.rotulo), it.total) }
                    )

                    if (dados.porFilho.size > 1) {
                        Secao(R.string.report_por_filho)
                        Barras(dados.porFilho)
                    }

                    if (dados.porTarefa.isNotEmpty()) {
                        Secao(R.string.report_por_tarefa)
                        Barras(dados.porTarefa)
                    }

                    Spacer(Modifier.height(48.dp))
                }
            }
        }
    }
}

@Composable
private fun rotuloLegivel(categoria: String): String = stringResource(rotuloDaCategoria(categoria))

private fun janelaLabel(dias: Int): Int = when (dias) {
    7 -> R.string.report_janela_7
    90 -> R.string.report_janela_90
    else -> R.string.report_janela_30
}

/** O backend manda data ISO; a tela mostra do jeito daqui. */
private fun formatarDia(iso: String): String =
    runCatching { LocalDate.parse(iso).format(DIA_CURTO) }.getOrDefault(iso)

@Composable
private fun Linha(conteudo: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        conteudo()
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
private fun Secao(titulo: Int) {
    Spacer(Modifier.height(24.dp))
    Text(text = stringResource(titulo), style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Numerao(itens: List<Pair<String, Int>>) {
    // Duas colunas: numa tela de celular, três já espremem o número grande.
    itens.chunked(2).forEach { par ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            par.forEach { (valor, rotulo) ->
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            text = valor,
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(rotulo),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            // Ímpar: a última fica sozinha e não pode esticar até a borda.
            if (par.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun Barras(itens: List<ContagemDto>) {
    val maior = itens.maxOfOrNull { it.total } ?: 0
    itens.forEach { item ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = item.rotulo,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(120.dp),
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(if (maior > 0) item.total / maior.toFloat() else 0f)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
            Text(
                text = item.total.toString(),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.width(28.dp),
            )
        }
    }
}

/**
 * Uma coluna por dia, **inclusive os zerados**: é o vazio que mostra a
 * sequência limpa, que é a notícia boa. Rola na horizontal em vez de espremer
 * noventa dias na largura do celular.
 */
@Composable
private fun SerieDiaria(dias: List<ContagemDto>) {
    val pico = dias.maxOfOrNull { it.total } ?: 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        dias.forEach { dia ->
            Box(
                Modifier
                    .width(6.dp)
                    .fillMaxHeight(
                        if (pico > 0) (dia.total / pico.toFloat()).coerceAtLeast(0.02f) else 0.02f
                    )
                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    .background(
                        if (dia.total > 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        }
                    )
            )
        }
    }
}
