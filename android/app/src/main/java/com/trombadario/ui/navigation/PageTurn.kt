package com.trombadario.ui.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.trombadario.ui.theme.NotebookBackground
import kotlin.math.abs

/**
 * Quanto dura uma virada. Curto o bastante para não atrasar quem só quer trocar
 * de aba, longo o bastante para o movimento ser lido como uma folha e não como
 * um corte seco.
 *
 * Quem desliga animação no sistema não vê nada disso: o Compose lê o
 * `ANIMATOR_DURATION_SCALE` do Android e zera a duração sozinho.
 */
const val PAGE_TURN_MS = 360

/** Ângulo em que a folha já está de perfil e sumiu da vista. */
private const val LIFTED = 92f

/**
 * A folha que fica por baixo não é estática: ela assenta desse ângulo até o
 * plano. É pouco de propósito - é o papel acomodando, não outra virada.
 */
private const val SETTLE = -5f

/** Sombra da folha levantada, no auge do movimento. */
private val LIFT_SHADOW = 14.dp

/**
 * Uma tela como folha de caderno que vira presa pela lombada.
 *
 * O papel é desenhado **aqui dentro**, e não só na raiz: se a pauta e a margem
 * ficassem paradas no fundo, o que se veria era o texto deslizando sobre um
 * papel imóvel. A folha só parece virar se ela vira inteira.
 *
 * A raiz continua com o seu [NotebookBackground] e é isso que aparece como
 * "próxima página" enquanto a de cima está levantada.
 *
 * Só precisa saber se o movimento é de avançar ou de voltar ([forward]); qual
 * das duas folhas ela é, descobre sozinha pelo estado da transição.
 */
// `transition` ainda é experimental, mas é a única porta para animar a entrada
// e a saída com algo além do que fadeIn/slideIn sabem fazer - e é justamente
// uma rotação em 3D que eles não sabem.
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AnimatedVisibilityScope.PageSheet(
    forward: Boolean,
    content: @Composable () -> Unit,
) {
    val angle by transition.animateFloat(
        transitionSpec = { tween(PAGE_TURN_MS, easing = FastOutSlowInEasing) },
        label = "anguloDaFolha",
    ) { state ->
        when (state) {
            EnterExitState.Visible -> 0f
            // Avançando, a folha nova já estava embaixo e só assenta; voltando,
            // ela é a que desce de volta sobre a pilha.
            EnterExitState.PreEnter -> if (forward) SETTLE else LIFTED
            EnterExitState.PostExit -> if (forward) LIFTED else SETTLE
        }
    }

    val entering = transition.targetState == EnterExitState.Visible

    Box(
        modifier = Modifier
            // Quem está por cima muda com o sentido: avançando é a folha que
            // levanta, voltando é a que desce. Sem isso, metade das viradas
            // aconteceria escondida atrás da outra tela.
            .zIndex(if (if (forward) !entering else entering) 1f else 0f)
            .graphicsLayer {
                rotationY = angle
                // A lombada é a esquerda, do lado da margem vermelha: a folha
                // gira presa nela, não pelo meio da tela.
                transformOrigin = TransformOrigin(0f, 0.5f)
                // O padrão (8) exagera a perspectiva numa superfície do tamanho
                // da tela inteira - a folha entorta em vez de virar.
                cameraDistance = 24f * density
                shadowElevation = abs(angle) / LIFTED * LIFT_SHADOW.toPx()
            }
            .fillMaxSize()
    ) {
        NotebookBackground { content() }
    }
}

/**
 * Fundo da pilha para cada destino, para saber de que lado a folha vira.
 *
 * Sai do nome da rota em vez de olhar se foi ida ou volta na pilha: assim o
 * botão "voltar" do sistema e o toque na aba anterior viram para o mesmo lado,
 * que é o que a pessoa espera de um caderno.
 */
internal fun pageDepth(route: String?): Int = when (route?.substringBefore('?')) {
    Routes.FEED -> 0
    Routes.TASKS -> 1
    Routes.PUNISHMENT -> 2
    Routes.PEDIDOS -> 3
    Routes.SETTINGS -> 4
    // Detalhe, formulário, contas e frases: tudo que se abre a partir de uma aba
    // está mais fundo que qualquer aba, e volta virando para trás.
    else -> 10
}

/**
 * Lembra a rota anterior só para dizer o sentido da virada.
 *
 * De propósito **não** é `MutableState`: ninguém precisa recompor por causa
 * dela, e escrever num estado observável durante a composição que o lê é como
 * se entra em laço de recomposição.
 */
internal class TurnDirection {
    private var previous: String? = null
    var forward: Boolean = true
        private set

    fun onRoute(route: String?) {
        if (route == null || route == previous) return
        forward = pageDepth(route) > pageDepth(previous)
        previous = route
    }
}
