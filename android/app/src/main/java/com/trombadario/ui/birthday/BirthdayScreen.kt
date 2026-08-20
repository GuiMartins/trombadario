package com.trombadario.ui.birthday

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trombadario.R
import com.trombadario.ui.theme.FestaCores
import com.trombadario.ui.theme.MarkerRed
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * O dia do filho. **A tela é o app inteiro**: nem nav bar, nem trombadice, nem
 * tarefa, nem castigo - foi o pedido literal ("no dia de aniversário não existe
 * Trombadário, o dia é especial"). Quem decide se é hoje é o servidor
 * (`GET /api/birthday`), não o relógio do aparelho.
 *
 * Não tem nenhum toque: não é uma tela que se fecha. A festa dura o dia.
 *
 * Tudo é **desenhado**, nada é imagem nem GIF - mesma decisão da folha de
 * caderno e da capa do diário. Fogos, confete e balões acompanham qualquer
 * tamanho de tela sem esticar nem cortar, e o APK não engorda.
 */
@Composable
fun BirthdayScreen(childName: String, age: Int?) {
    // Um relógio só, compartilhado: cada partícula tira a própria posição da
    // fase dela dentro deste ciclo. Uma animação por partícula seriam dezenas
    // de animações independentes brigando pelo mesmo quadro.
    //
    // O ciclo volta a zero e tudo tem que continuar de onde parou, senão a
    // festa "pisca" a cada volta. Por isso toda conta aqui embaixo é periódica
    // no ciclo: velocidade e número de voltas são inteiros, e todo fogo termina
    // antes do fim.
    val transicao = rememberInfiniteTransition(label = "festa")
    val ciclo by transicao.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = CICLO_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ciclo",
    )
    // Os balões sobem no dobro do tempo dos outros: no ritmo do confete
    // pareceriam bolinhas disparando pra cima.
    val cicloLento by transicao.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = CICLO_MS * 2, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ciclo-lento",
    )
    // Pulsação do bolo. Reverse em vez de Restart pra ele crescer e voltar, não
    // crescer e estourar de volta ao tamanho pequeno.
    val pulso by transicao.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulso",
    )

    // Sorteadas uma vez, com semente fixa: a festa é sempre a mesma, e uma
    // recomposição no meio não teleporta partícula nenhuma.
    val fogos = remember { sortearFogos() }
    val confetes = remember { sortearConfetes() }
    val baloes = remember { sortearBaloes() }

    val papel = MaterialTheme.colorScheme.background

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            baloes.forEach { desenharBalao(it, cicloLento) }
            fogos.forEach { desenharFogo(it, ciclo, papel) }
            confetes.forEach { desenharConfete(it, ciclo) }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                // Um clarão da cor do papel no meio da tela: o texto continua
                // legível com confete passando por trás, e a festa fica em
                // volta dele em vez de por cima.
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(papel.copy(alpha = 0.88f), papel.copy(alpha = 0f)),
                            center = center,
                            radius = size.minDimension * 0.8f,
                        )
                    )
                }
                // Celular deitado não tem altura pra tudo isto - mesmo recurso
                // da tela de castigo do filho.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "🎂",
                fontSize = 96.sp,
                modifier = Modifier.graphicsLayer {
                    scaleX = pulso
                    scaleY = pulso
                    // O balanço acompanha a mesma pulsação, só que fora de
                    // fase - o bolo inclina enquanto cresce.
                    rotationZ = (pulso - 1f) * 60f
                },
            )
            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.birthday_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Text(
                text = childName,
                style = MaterialTheme.typography.displayMedium,
                // O vermelho claro do mockup, e não a tinta funda de leitura:
                // aqui é assinatura em letra grande, o mesmo caso do nome do
                // app na capa (ver MarkerRed em Color.kt). Um valor só nos dois
                // temas porque aqui ele passa nos dois - 3,3:1 no papel claro
                // (o mínimo de texto grande é 3:1) e 4,6:1 no escuro.
                color = MarkerRed,
                textAlign = TextAlign.Center,
            )

            if (age != null) {
                Spacer(Modifier.height(20.dp))
                Recado(text = pluralStringResource(R.plurals.birthday_age, age, age))
            }

            Spacer(Modifier.height(24.dp))
            EmojisPulando(ciclo)

            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.birthday_no_trombadario),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** O papelzinho de recado da identidade visual: fundo amarelo, borda tracejada. */
@Composable
private fun Recado(text: String) {
    val fundo = MaterialTheme.colorScheme.surfaceVariant
    val borda = MaterialTheme.colorScheme.outline

    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .drawBehind {
                val raio = CornerRadius(16.dp.toPx())
                drawRoundRect(color = fundo, cornerRadius = raio)
                drawRoundRect(
                    color = borda,
                    cornerRadius = raio,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(10.dp.toPx(), 7.dp.toPx())
                        ),
                    ),
                )
            }
            .padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

/** Fileira de emoji subindo e descendo, cada um fora de fase do vizinho. */
@Composable
private fun EmojisPulando(ciclo: Float) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        EMOJIS.forEachIndexed { indice, emoji ->
            val fase = indice.toFloat() / EMOJIS.size
            Text(
                text = emoji,
                fontSize = 34.sp,
                modifier = Modifier.graphicsLayer {
                    // Três pulos por ciclo, número inteiro pra emendar na volta.
                    val onda = sin(2.0 * PI * ((ciclo * 3f + fase).toDouble())).toFloat()
                    translationY = -onda * 10.dp.toPx()
                    rotationZ = onda * 8f
                },
            )
        }
    }
}

// --------------------------------------------------------------------------
// As partículas
// --------------------------------------------------------------------------

private const val CICLO_MS = 9000

private val EMOJIS = listOf("🎉", "🎈", "🎁", "🥳", "🍬")

/** Semente fixa: a festa é a mesma toda vez, e é a mesma pra todo mundo. */
private const val SEMENTE = 20260820L

/**
 * Um fogo de artifício: sobe de baixo, estoura e se apaga.
 *
 * Posições em fração da tela (0..1), não em pixel: a mesma festa cabe no
 * celular estreito e no tablet.
 */
private data class Fogo(
    val x: Float,
    val altura: Float,
    val inicio: Float,
    val duracao: Float,
    val faiscas: Int,
    val raio: Float,
    val cor: Color,
)

private data class Confete(
    val x: Float,
    val fase: Float,
    val quedas: Int,
    val tamanho: Float,
    val voltas: Int,
    val balanco: Float,
    val cor: Color,
    val redondo: Boolean,
)

private data class Balao(
    val x: Float,
    val fase: Float,
    val tamanho: Float,
    val balanco: Float,
    val cor: Color,
)

private fun sortearFogos(): List<Fogo> {
    val sorteio = Random(SEMENTE)
    return List(7) { indice ->
        // Os inícios espalhados pelo ciclo em vez de sorteados soltos: sorteados,
        // três podiam cair juntos e deixar metade do ciclo sem nada no céu.
        val inicio = (indice + sorteio.nextFloat() * 0.6f) / 7f
        Fogo(
            x = 0.12f + sorteio.nextFloat() * 0.76f,
            altura = 0.14f + sorteio.nextFloat() * 0.30f,
            inicio = inicio,
            duracao = 0.34f + sorteio.nextFloat() * 0.12f,
            faiscas = 26 + sorteio.nextInt(14),
            raio = 0.17f + sorteio.nextFloat() * 0.13f,
            cor = FestaCores[sorteio.nextInt(FestaCores.size)],
        )
    }
}

private fun sortearConfetes(): List<Confete> {
    val sorteio = Random(SEMENTE + 1)
    return List(46) {
        Confete(
            x = sorteio.nextFloat(),
            fase = sorteio.nextFloat(),
            // Inteiro de propósito: em uma volta do ciclo o papelzinho cai uma
            // ou duas vezes inteiras, e a emenda não aparece.
            quedas = 1 + sorteio.nextInt(2),
            tamanho = 5f + sorteio.nextFloat() * 5f,
            voltas = (1 + sorteio.nextInt(3)) * (if (sorteio.nextBoolean()) 1 else -1),
            balanco = 0.01f + sorteio.nextFloat() * 0.03f,
            cor = FestaCores[sorteio.nextInt(FestaCores.size)],
            redondo = sorteio.nextInt(4) == 0,
        )
    }
}

private fun sortearBaloes(): List<Balao> {
    val sorteio = Random(SEMENTE + 2)
    return List(6) {
        Balao(
            x = 0.08f + sorteio.nextFloat() * 0.84f,
            fase = sorteio.nextFloat(),
            tamanho = 0.055f + sorteio.nextFloat() * 0.035f,
            balanco = 0.015f + sorteio.nextFloat() * 0.025f,
            cor = FestaCores[sorteio.nextInt(FestaCores.size)],
        )
    }
}

/** A parte do ciclo em que o foguete ainda está subindo. */
private const val SUBIDA = 0.3f

private fun DrawScope.desenharFogo(fogo: Fogo, ciclo: Float, papel: Color) {
    val vida = ((ciclo - fogo.inicio) % 1f + 1f) % 1f
    if (vida >= fogo.duracao) return

    val p = vida / fogo.duracao
    val x = fogo.x * size.width
    val topo = fogo.altura * size.height
    val menor = min(size.width, size.height)

    if (p < SUBIDA) {
        val q = p / SUBIDA
        // Desacelera enquanto sobe, como foguete de verdade perdendo impulso.
        val y = size.height - (size.height - topo) * (1f - (1f - q) * (1f - q))
        drawLine(
            color = fogo.cor.copy(alpha = 0.4f * (1f - q)),
            start = Offset(x, y + menor * 0.07f),
            end = Offset(x, y),
            strokeWidth = 3f,
            cap = StrokeCap.Round,
        )
        drawCircle(color = fogo.cor, radius = 4f, center = Offset(x, y))
        return
    }

    val q = (p - SUBIDA) / (1f - SUBIDA)
    // Abre rápido e vai parando; some no fim.
    val raio = fogo.raio * menor * (1f - (1f - q) * (1f - q) * (1f - q))
    val alpha = (1f - q) * (1f - q)
    // O que sobra do estouro cai: sem isto o fogo vira um círculo perfeito
    // parado no ar.
    val queda = q * q * menor * 0.12f
    val centro = Offset(x, topo + queda * 0.5f)

    // O clarão do estouro, atrás das faíscas: forte no primeiro instante e some
    // rápido, senão vira uma bola colorida parada no céu.
    drawCircle(
        color = fogo.cor.copy(alpha = alpha * 0.18f),
        radius = raio * 0.9f,
        center = centro,
    )
    if (q < 0.2f) {
        drawCircle(
            color = fogo.cor.copy(alpha = (1f - q / 0.2f) * 0.5f),
            radius = raio * 0.35f,
            center = centro,
        )
    }

    repeat(fogo.faiscas) { indice ->
        val angulo = 2.0 * PI * indice / fogo.faiscas
        // Duas coroas: a de dentro é o miolo do estouro, e é o que dá volume.
        val escala = if (indice % 3 == 0) 0.62f else 1f
        val cosseno = cos(angulo).toFloat()
        val seno = sin(angulo).toFloat()
        val ponta = Offset(x + cosseno * raio * escala, topo + seno * raio * escala + queda)
        // A faísca é um risco, não um ponto: bolinha isolada num círculo lê
        // como pontilhado, o risco na direção do estouro lê como fogo.
        val rastro = raio * escala * 0.28f * (1f - q)
        drawLine(
            color = fogo.cor.copy(alpha = alpha * 0.8f),
            start = Offset(ponta.x - cosseno * rastro, ponta.y - seno * rastro),
            end = ponta,
            strokeWidth = 3f,
            cap = StrokeCap.Round,
        )
        drawCircle(color = fogo.cor.copy(alpha = alpha), radius = 3f, center = ponta)
        // Miolo claro na ponta, pra ela brilhar em vez de só existir. Usa a cor
        // do papel: no tema escuro clarear com branco estouraria.
        drawCircle(color = papel.copy(alpha = alpha * 0.6f), radius = 1.2f, center = ponta)
    }
}

private fun DrawScope.desenharConfete(confete: Confete, ciclo: Float) {
    val t = (ciclo * confete.quedas + confete.fase) % 1f
    // Nasce acima do topo e sai por baixo da tela: assim ninguém vê aparecer
    // nem sumir.
    val y = -0.1f * size.height + t * 1.2f * size.height
    val x = (confete.x + sin(2.0 * PI * (t * 2 + confete.fase)).toFloat() * confete.balanco) *
        size.width
    val lado = confete.tamanho * density

    if (confete.redondo) {
        drawCircle(color = confete.cor, radius = lado * 0.5f, center = Offset(x, y))
        return
    }
    rotate(degrees = t * 360f * confete.voltas, pivot = Offset(x, y)) {
        drawRect(
            color = confete.cor,
            topLeft = Offset(x - lado * 0.5f, y - lado * 0.75f),
            size = Size(lado, lado * 1.5f),
        )
    }
}

private fun DrawScope.desenharBalao(balao: Balao, ciclo: Float) {
    val t = (ciclo + balao.fase) % 1f
    // Sobe: entra por baixo e sai por cima.
    val y = size.height * (1.15f - t * 1.35f)
    val x = (balao.x + sin(2.0 * PI * (t * 2 + balao.fase)).toFloat() * balao.balanco) * size.width
    val raio = balao.tamanho * min(size.width, size.height)

    // O barbante, dado por uma curva - reto ele parece antena.
    val caminho = Path().apply {
        moveTo(x, y + raio * 1.25f)
        quadraticTo(
            x + raio * 0.5f, y + raio * 2f,
            x, y + raio * 2.8f,
        )
    }
    drawPath(
        path = caminho,
        color = balao.cor.copy(alpha = 0.5f),
        style = Stroke(width = 2f),
    )

    // O balão é oval, não redondo, e tem o biquinho embaixo.
    drawOval(
        color = balao.cor.copy(alpha = 0.85f),
        topLeft = Offset(x - raio * 0.8f, y - raio),
        size = Size(raio * 1.6f, raio * 2.1f),
    )
    drawCircle(
        color = balao.cor.copy(alpha = 0.85f),
        radius = raio * 0.12f,
        center = Offset(x, y + raio * 1.2f),
    )
    // O brilho da luz na lateral, que é o que faz parecer inflado.
    drawOval(
        color = Color.White.copy(alpha = 0.25f),
        topLeft = Offset(x - raio * 0.5f, y - raio * 0.7f),
        size = Size(raio * 0.35f, raio * 0.6f),
    )
}
