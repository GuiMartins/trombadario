package com.trombadario.notifications

import com.trombadario.data.remote.UnseenCountsDto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationBaselineTest {

    @Test
    fun nenhumaCategoriaSubiuNaoNotifica() {
        val baseline = NotificationBaseline(pedidos = 1, anotacoes = 2, castigos = 0, decisoes = 3)
        val fresh = UnseenCountsDto(
            pedidosPendentes = 1,
            anotacoesNovas = 2,
            castigosNovos = 0,
            decisoesNovas = 3,
        )

        assertFalse(shouldNotify(baseline, fresh))
    }

    @Test
    fun umaCategoriaSubiuNotifica() {
        val baseline = NotificationBaseline(pedidos = 0, anotacoes = 0, castigos = 0, decisoes = 0)
        val fresh = UnseenCountsDto(pedidosPendentes = 1)

        assertTrue(shouldNotify(baseline, fresh))
    }

    @Test
    fun categoriaCaiuNaoNotifica() {
        // Item foi visto no app entre uma checagem e outra - a contagem caiu,
        // não subiu, então não é novidade.
        val baseline = NotificationBaseline(anotacoes = 3)
        val fresh = UnseenCountsDto(anotacoesNovas = 1)

        assertFalse(shouldNotify(baseline, fresh))
    }

    @Test
    fun variasCategoriasSubindoAoMesmoTempoNotifica() {
        val baseline = NotificationBaseline()
        val fresh = UnseenCountsDto(
            castigosNovos = 1,
            decisoesNovas = 2,
        )

        assertTrue(shouldNotify(baseline, fresh))
    }

    @Test
    fun baselineZeradoEContagemZeradaNaoNotifica() {
        assertFalse(shouldNotify(NotificationBaseline(), UnseenCountsDto()))
    }
}
