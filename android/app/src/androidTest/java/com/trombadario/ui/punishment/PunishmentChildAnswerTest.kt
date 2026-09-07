package com.trombadario.ui.punishment

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.trombadario.R
import com.trombadario.data.remote.PunishmentDto
import com.trombadario.ui.components.formatDateTime
import com.trombadario.ui.components.parseInstant
import com.trombadario.ui.theme.TrombadarioTheme
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A regra que este teste protege: **com mais de um castigo valendo, a tela do
 * filho anuncia o prazo mais distante e mostra todos**.
 *
 * Antes ela lia só `active.first()`, e as duas metades disso eram mentira: o
 * prazo do castigo que acaba antes dizia à criança que ela ficaria livre num
 * dia em que ainda estaria de castigo, e o outro castigo nunca aparecia - mas
 * `/current` carimba `seen_at` em todos os ativos, então o pai lia "visto" de
 * um castigo que nunca chegou na tela.
 */
@RunWith(AndroidJUnit4::class)
class PunishmentChildAnswerTest {

    @get:Rule
    val rule = createComposeRule()

    private val agora: Instant = Instant.now()

    /** Nada de data fixa no calendário: um teste que depende do mês em que roda
     *  fica vermelho sozinho na virada (foi o que houve com `FiltroBarTest`). */
    private fun castigo(id: Int, motivo: String, terminaEm: Instant) = PunishmentDto(
        id = id,
        reason = motivo,
        startsAt = agora.minus(1, ChronoUnit.DAYS).toString(),
        endsAt = terminaEm.toString(),
        childId = 2,
        isActive = true,
    )

    private val curto = castigo(1, "sem tablet", agora.plus(2, ChronoUnit.DAYS))
    private val longo = castigo(2, "sem bicicleta", agora.plus(9, ChronoUnit.DAYS))

    private fun montar(ativos: List<PunishmentDto>) {
        rule.setContent {
            TrombadarioTheme { ChildAnswer(ativos = ativos, onReact = { _, _ -> }) }
        }
    }

    @Test
    fun anuncia_o_prazo_mais_distante_dos_castigos_que_valem() {
        // Na ordem que a API devolve (mais recente primeiro), o que acaba antes
        // pode vir na frente - é exatamente o caso que quebrava.
        montar(listOf(curto, longo))

        // A data formatada pela mesma função da tela, e não escrita à mão: o
        // fuso é o do aparelho, e um literal só passaria na máquina de quem
        // escreveu o teste.
        val livreEm = parseInstant(longo.endsAt).formatDateTime()
        // onAll, não onNode: com dois castigos o prazo mais distante aparece
        // duas vezes - no anúncio de cima e no bloco do próprio castigo.
        assertTrue(
            "o prazo anunciado devia ser $livreEm, o mais distante dos dois",
            rule.onAllNodesWithText(livreEm, substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        // E o do castigo curto não pode ser o anúncio: ele diria que a criança
        // fica livre num dia em que ainda está de castigo.
        val prazoCurto = parseInstant(curto.endsAt).formatDateTime()
        assertTrue(
            "$prazoCurto não pode aparecer mais vezes que o prazo real",
            rule.onAllNodesWithText(prazoCurto, substring = true)
                .fetchSemanticsNodes().size == 1,
        )
    }

    @Test
    fun mostra_todos_os_castigos_que_valem_nao_so_o_primeiro() {
        montar(listOf(curto, longo))

        rule.onNodeWithText(curto.reason).assertExists()
        rule.onNodeWithText(longo.reason).assertExists()
    }

    @Test
    fun sem_castigo_nenhum_a_resposta_e_a_boa_noticia() {
        montar(emptyList())

        // Do strings.xml, não escrito à mão: reescrever o texto da tela não é
        // quebrar a regra que este teste protege.
        val contexto = InstrumentationRegistry.getInstrumentation().targetContext
        rule.onNodeWithText(contexto.getString(R.string.punishment_free)).assertExists()
    }
}
