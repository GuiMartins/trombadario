package com.trombadario.ui.navigation

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.trombadario.ui.theme.TrombadarioTheme
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * A virada de página é a única parte da interface que não dá para conferir
 * rodando o app: a `MainActivity` é `FLAG_SECURE`, então captura de tela e
 * gravação saem em branco. Aqui a activity é a do teste, que não é secreta, e o
 * relógio da animação pode ser parado no meio do movimento.
 *
 * O que estes testes protegem é o arranjo incomum do [PageSheet]: o `NavHost`
 * declara `EnterTransition.None`, e quem segura a tela viva durante a virada é a
 * animação de ângulo lá dentro. Se um dia isso deixar de valer, a troca de tela
 * volta a ser um corte seco - sem quebrar compilação nenhuma.
 */
@RunWith(AndroidJUnit4::class)
class PageTurnTest {

    @get:Rule
    val rule = createComposeRule()

    private val meio = PAGE_TURN_MS / 2L

    @Test
    fun folhaContinuaNaTelaNoMeioDaVirada() {
        var visivel by montarFolha()

        val deitada = rule.onRoot().captureToImage()

        visivel = false
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(meio)

        // Com transição `None` no NavHost, o padrão seria a tela sumir no
        // primeiro quadro. Se este assert cair, a virada virou corte.
        rule.onNodeWithTag(FOLHA).assertExists()

        val virando = rule.onRoot().captureToImage()
        salvar(deitada, "folha_deitada.png")
        salvar(virando, "folha_virando.png")

        assertNotEquals(
            "a folha não se mexeu na metade da virada",
            deitada.assinatura(),
            virando.assinatura(),
        )
    }

    @Test
    fun aViradaTerminaESaiDaComposicao() {
        var visivel by montarFolha()

        visivel = false
        rule.mainClock.advanceTimeByFrame()
        // Uma folga pequena: o que importa é acabar logo depois do previsto, não
        // ficar pendurada.
        rule.mainClock.advanceTimeBy(PAGE_TURN_MS + 120L)

        rule.onNodeWithTag(FOLHA).assertDoesNotExist()
    }

    @Test
    fun folhaDeBaixoAparecePelaDireitaEnquantoADeCimaGira() {
        var visivel by montarFolha()

        visivel = false
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(meio)

        val pixels = rule.onRoot().captureToImage().toPixelMap()
        val y = pixels.height / 2

        // A lombada é a esquerda: lá a folha ainda cobre. Na borda direita ela
        // já saiu do caminho e o que se vê é a mesa (o fundo do teste).
        assertNotEquals(
            "a beirada da lombada devia continuar coberta pela folha",
            FUNDO,
            pixels[2, y],
        )
        assertNotEquals(
            "a folha não encolheu na direita - ou não girou, ou girou pelo meio",
            FOLHA_COR,
            pixels[pixels.width - 3, y],
        )
    }

    /**
     * Monta uma folha sobre um fundo de cor chapada e devolve o controle da
     * visibilidade. O relógio fica parado: cada teste anda com ele na mão.
     */
    private fun montarFolha(): androidx.compose.runtime.MutableState<Boolean> {
        val visivel = mutableStateOf(true)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            TrombadarioTheme {
                Box(Modifier.fillMaxSize().background(FUNDO)) {
                    AnimatedVisibility(
                        visible = visivel.value,
                        enter = EnterTransition.None,
                        exit = ExitTransition.None,
                    ) {
                        PageSheet(forward = true) {
                            Box(Modifier.fillMaxSize().background(FOLHA_COR).testTag(FOLHA))
                        }
                    }
                }
            }
        }
        rule.mainClock.advanceTimeByFrame()
        return visivel
    }

    /** Uma linha de pixels do meio da tela, o bastante para dizer se mudou. */
    private fun ImageBitmap.assinatura(): List<Color> {
        val pixels = toPixelMap()
        val y = pixels.height / 2
        return (0 until pixels.width step 16).map { pixels[it, y] }
    }

    /**
     * Grava o quadro para poder ser olhado depois com `adb pull`. É a única
     * maneira de conferir com o olho se aquilo parece mesmo uma folha virando -
     * o assert só sabe dizer que mudou.
     */
    private fun salvar(imagem: ImageBitmap, nome: String) {
        val destino = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            nome,
        )
        destino.outputStream().use {
            imagem.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private companion object {
        const val FOLHA = "folha"
        val FUNDO = Color(0xFF0000FF)
        val FOLHA_COR = Color(0xFFFF0000)
    }
}
