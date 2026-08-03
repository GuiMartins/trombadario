package com.trombadario.ui.components

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.trombadario.data.remote.UserDto
import com.trombadario.ui.theme.TrombadarioTheme
import java.time.LocalDate
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

    private val comRegistro = LocalDate.of(2026, 8, 5)
    private val semRegistro = LocalDate.of(2026, 8, 6)

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
                    category = null,
                    onSelectCategory = {},
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
        rule.onNodeWithText("Falta de respeito").assertExists()
        rule.onNodeWithText("Qualquer tipo").assertExists()
    }

    @Test
    fun so_deixa_escolher_dia_que_tem_registro() {
        montar()

        rule.onNodeWithText("Filtrar por dia").performClick()

        // O DatePicker do Material rotula cada dia por extenso e desabilita os
        // que o SelectableDates recusa.
        val dia6 = rule.onAllNodesWithText("August 6", substring = true)
        assertTrue(
            "o dia 6 devia estar na tela para poder estar apagado",
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
