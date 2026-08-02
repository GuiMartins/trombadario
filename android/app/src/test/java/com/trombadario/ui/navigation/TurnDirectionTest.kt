package com.trombadario.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O sentido da virada sai só do nome das rotas. Como não depende de nada do
 * Android, dá para conferir aqui e deixar o teste instrumentado só com o que
 * precisa mesmo de tela.
 */
class TurnDirectionTest {

    @Test
    fun avancarParaAAbaSeguinteViraParaFrente() {
        val virada = TurnDirection()
        virada.onRoute(Routes.FEED)
        virada.onRoute(Routes.TASKS)
        assertTrue(virada.forward)
    }

    @Test
    fun voltarParaAAbaAnteriorViraParaTras() {
        val virada = TurnDirection()
        virada.onRoute(Routes.PUNISHMENT)
        virada.onRoute(Routes.FEED)
        assertFalse(virada.forward)
    }

    @Test
    fun abrirUmaTelaDeDentroDeUmaAbaSempreAvanca() {
        val virada = TurnDirection()
        virada.onRoute(Routes.SETTINGS)
        // Configurações é a última aba: se o sentido saísse da ordem das abas,
        // abrir Contas a partir dela viraria para trás.
        virada.onRoute(Routes.USERS)
        assertTrue(virada.forward)
    }

    @Test
    fun fecharUmaTelaDeDentroVoltaParaTras() {
        val virada = TurnDirection()
        virada.onRoute(Routes.SETTINGS)
        virada.onRoute(Routes.USERS)
        virada.onRoute(Routes.SETTINGS)
        assertFalse(virada.forward)
    }

    @Test
    fun formularioComArgumentoContaComoATelaDeFormulario() {
        val virada = TurnDirection()
        virada.onRoute(Routes.FEED)
        // A rota chega com o argumento colado: "trombadice_form?trombadiceId=3".
        // Se o "?" não fosse cortado, ela cairia no ramo desconhecido por acaso -
        // e um dia deixaria de cair.
        virada.onRoute(Routes.TROMBADICE_FORM)
        assertTrue(virada.forward)
        assertEquals(10, pageDepth(Routes.trombadiceForm(3)))
    }

    @Test
    fun recomporNaMesmaRotaNaoInverteOSentido() {
        val virada = TurnDirection()
        virada.onRoute(Routes.PUNISHMENT)
        virada.onRoute(Routes.FEED)
        assertFalse(virada.forward)

        // A composição roda de novo sem ninguém navegar: o sentido não pode
        // mudar, senão a folha vira para o lado errado na metade do movimento.
        virada.onRoute(Routes.FEED)
        assertFalse(virada.forward)
    }
}
