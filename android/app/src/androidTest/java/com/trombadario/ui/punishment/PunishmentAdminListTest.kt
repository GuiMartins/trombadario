package com.trombadario.ui.punishment

import androidx.compose.ui.test.assertIsDisplayed
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
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A lista do pai com a fila: castigo dado que ainda não começou é um terceiro
 * estado, e a tela precisa dizer três coisas sobre ele que os outros não têm.
 *
 * Duas delas são fáceis de quebrar sem perceber:
 *
 * - **Não se cobra "ainda não viu" de castigo da fila.** O filho nem recebe
 *   esse castigo do servidor (`_so_o_de_agora`), então a cobrança seria de uma
 *   coisa impossível - e o pai leria como se a criança estivesse ignorando.
 * - **Na fila, "até quando" sozinho não diz nada.** O que o pai precisa ler é
 *   **onde o castigo emenda**, que é o ponto inteiro da funcionalidade.
 */
@RunWith(AndroidJUnit4::class)
class PunishmentAdminListTest {

    @get:Rule
    val rule = createComposeRule()

    private val agora: Instant = Instant.now()

    /** Nada de data fixa no calendário: um teste que depende do mês em que roda
     *  fica vermelho sozinho na virada (foi o que houve com `FiltroBarTest`). */
    private fun castigo(
        id: Int,
        motivo: String,
        comeca: Instant,
        termina: Instant,
        ativo: Boolean = false,
        naFila: Boolean = false,
    ) = PunishmentDto(
        id = id,
        reason = motivo,
        startsAt = comeca.toString(),
        endsAt = termina.toString(),
        childId = 2,
        isActive = ativo,
        isScheduled = naFila,
    )

    private val valendo = castigo(
        id = 1,
        motivo = "sem tablet",
        comeca = agora.minus(2, ChronoUnit.HOURS),
        termina = agora.plus(1, ChronoUnit.DAYS),
        ativo = true,
    )
    // Começa depois do fim do que está valendo, e não exatamente nele: instantes
    // iguais virariam o mesmo texto na tela em dois cartões, e o teste do
    // "onde emenda" acharia dois nós sem que nada estivesse errado.
    private val naFila = castigo(
        id = 2,
        motivo = "e mais um dia",
        comeca = agora.plus(2, ChronoUnit.DAYS),
        termina = agora.plus(3, ChronoUnit.DAYS),
        naFila = true,
    )

    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext

    private fun montar(punicoes: List<PunishmentDto>) {
        val state = PunishmentState(
            loading = false,
            active = punicoes.filter { it.isActive },
            scheduled = punicoes.filter { it.isScheduled },
            history = punicoes.filterNot { it.isActive || it.isScheduled },
        )
        rule.setContent {
            TrombadarioTheme {
                AdminList(
                    state = state,
                    childName = { "Joao" },
                    onEdit = {},
                    onEndNow = {},
                    onReopen = {},
                    onDelete = {},
                )
            }
        }
    }

    @Test
    fun a_fila_aparece_como_bloco_proprio() {
        montar(listOf(valendo, naFila))

        rule.onNodeWithText(contexto.getString(R.string.punishment_queue)).assertIsDisplayed()
        rule.onNodeWithText(naFila.reason).assertIsDisplayed()
        // E não vira "cumprido": o histórico é outro bloco.
        rule.onNodeWithText(contexto.getString(R.string.punishment_served)).assertDoesNotExist()
    }

    @Test
    fun castigo_da_fila_diz_onde_emenda_e_nao_so_o_prazo() {
        montar(listOf(valendo, naFila))

        // Formatado pela mesma função da tela, não escrito à mão: o fuso é o do
        // aparelho, e um literal só passaria na máquina de quem escreveu.
        val comeco = parseInstant(naFila.startsAt).formatDateTime()
        rule.onNodeWithText(comeco, substring = true).assertIsDisplayed()
    }

    @Test
    fun castigo_da_fila_nao_cobra_visto() {
        montar(listOf(valendo, naFila))

        // Uma vez só: a do castigo que está valendo, que o filho de fato recebe.
        assertEquals(
            "só o castigo que vale cobra o visto - o da fila o filho nem recebe",
            1,
            rule.onAllNodesWithText(contexto.getString(R.string.visto_ainda_nao))
                .fetchSemanticsNodes().size,
        )
    }

    @Test
    fun castigo_da_fila_nao_oferece_encerrar_nem_reabrir() {
        montar(listOf(naFila))

        // Encerrar é sobre castigo que está valendo, e Reabrir sobre um que foi
        // encerrado. Cancelar o da fila é o Excluir de sempre.
        rule.onNodeWithText(contexto.getString(R.string.punishment_end_now)).assertDoesNotExist()
        rule.onNodeWithText(contexto.getString(R.string.punishment_reopen)).assertDoesNotExist()
        rule.onNodeWithText(contexto.getString(R.string.action_delete)).assertIsDisplayed()
    }
}
