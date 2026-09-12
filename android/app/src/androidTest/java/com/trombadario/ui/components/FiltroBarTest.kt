package com.trombadario.ui.components

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.trombadario.data.remote.TrombadiceCategoryDto
import com.trombadario.data.remote.UserDto
import com.trombadario.ui.theme.TrombadarioTheme
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A regra que este teste existe para proteger: **o calendário só deixa escolher
 * dia que tem registro**. É fácil de quebrar sem perceber - basta alguém trocar
 * o `SelectableDates` por um `rememberDatePickerState()` padrão, e aí todo dia
 * fica clicável e leva a uma lista vazia.
 */
@RunWith(AndroidJUnit4::class)
class FiltroBarTest {

    @get:Rule
    val rule = createComposeRule()

    // Sem dia escolhido, o DatePicker abre no **mês de hoje**. Com as datas
    // fixas em agosto de 2026, este teste passava só enquanto era agosto de
    // 2026 e virou vermelho sozinho quando o calendário virou - não por
    // mudança nenhuma no código. Acompanhando o mês corrente, ele volta a
    // medir o que existe pra medir: que o dia sem registro fica apagado.
    //
    // Dia 5 e 6 não são escolha à toa: todo mês tem os dois, e a busca é por
    // substring - "September 1" casaria também com o dia 11 e o 19.
    private val comRegistro: LocalDate = LocalDate.now().withDayOfMonth(5)
    private val semRegistro: LocalDate = LocalDate.now().withDayOfMonth(6)

    /** Como o DatePicker do Material rotula o dia ("August 6"). Inglês porque é
     *  o idioma do emulador, não o do app. */
    private val rotuloDoDiaSemRegistro: String =
        semRegistro.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) +
            " " + semRegistro.dayOfMonth

    private fun montar(dias: Set<LocalDate> = setOf(comRegistro)) {
        rule.setContent {
            TrombadarioTheme {
                FiltroBar(
                    children = listOf(
                        UserDto(2, "joao", "Joao", "child", true),
                        UserDto(3, "maria", "Maria", "child", true),
                    ),
                    selectedChildId = null,
                    onSelectChild = {},
                    kind = null,
                    onSelectKind = {},
                    tipos = listOf(
                        TrombadiceCategoryDto(1, "Falta de respeito"),
                        TrombadiceCategoryDto(2, "Mentira"),
                    ),
                    categoryId = null,
                    onSelectCategoryId = {},
                    conquistaCategory = null,
                    onSelectConquistaCategory = {},
                    busca = "",
                    onBuscaChange = {},
                    onBuscar = {},
                    dia = null,
                    diasComRegistro = dias,
                    onSelectDia = {},
                    filtrando = false,
                    onLimpar = {},
                )
            }
        }
    }

    @Test
    fun mostraOsFilhosEAsCategorias() {
        montar()

        rule.onNodeWithText("Joao").assertExists()
        // O tipo de trombadice vem da lista cadastrada pelo pai, que chega de
        // fora - não existe mais enum no app pra isso.
        rule.onNodeWithText("Falta de respeito").assertExists()
        rule.onNodeWithText("Qualquer tipo").assertExists()
        // Sem tipo de registro escolhido valem as duas listas.
        rule.onNodeWithText("Ajudou sem pedir").assertExists()
        rule.onNodeWithText("Conquistas").assertExists()
    }

    @Test
    fun so_deixa_escolher_dia_que_tem_registro() {
        montar()

        rule.onNodeWithText("Filtrar por dia").performClick()

        // O DatePicker do Material rotula cada dia por extenso e desabilita os
        // que o SelectableDates recusa.
        val dia6 = rule.onAllNodesWithText(rotuloDoDiaSemRegistro, substring = true)
        assertTrue(
            "$rotuloDoDiaSemRegistro devia estar na tela para poder estar apagado",
            dia6.fetchSemanticsNodes().isNotEmpty(),
        )
        dia6.onFirst().assertIsNotEnabled()
    }

    @Test
    fun sem_nenhum_dia_com_registro_o_botao_nao_abre_nada() {
        montar(dias = emptySet())

        // Abrir um calendário inteiro apagado seria pior que não abrir.
        rule.onNodeWithText("Filtrar por dia").assertIsNotEnabled()
    }
}
