package com.trombadario.ui.splash

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.trombadario.R

/** Quanto tempo a frase fica na tela.
 *
 * Fixo, e não "enquanto carrega": na rede de casa os dados chegam em uns 200ms,
 * então uma barra honesta piscaria e sumiria antes de dar pra ler - e a frase
 * existe justamente pra ser lida. */
const val SPLASH_DURATION_MS = 2500

/**
 * Tela de abertura da conta de filho: a capa do caderno, o nome de quem tá
 * abrindo e uma frase escrita pelo pai, sorteada no servidor. A frase só
 * aparece quando existe uma que se aplique a ele - a capa e a saudação
 * aparecem sempre, mesmo sem frase cadastrada.
 */
@Composable
fun SplashScreen(childName: String, message: String) {
    var started by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = SPLASH_DURATION_MS, easing = LinearEasing),
        label = "splash",
    )

    // Dispara na primeira composição: animateFloatAsState só anima quando o
    // alvo muda, então começar já em 1f não animaria nada.
    LaunchedEffect(Unit) { started = true }

    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        DiaryCover(modifier = Modifier.widthIn(max = 220.dp))
        Spacer(Modifier.height(32.dp))
        // Saudação e frase são dois Text separados, não uma string só com
        // \n\n na mão, pra cada um manter o próprio estilo tipográfico.
        Text(
            text = stringResource(R.string.splash_greeting, childName),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (message.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(48.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
