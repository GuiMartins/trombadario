package com.trombadario.notifications

import com.trombadario.data.remote.UnseenCountsDto
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationBaselineTest {

    @Test
    fun nenhumaCategoriaSubiuNaoAvisaNada() {
        val baseline = NotificationBaseline(pedidos = 1, trombadices = 2, decisoes = 3)
        val fresh = UnseenCountsDto(
            pedidosPendentes = 1,
            trombadicesNovas = 2,
            decisoesNovas = 3,
        )

        assertEquals(emptyList<Novidade>(), novidades(baseline, fresh))
    }

    @Test
    fun soAcategoriaQueSubiuEntraNaLista() {
        val baseline = NotificationBaseline(pedidos = 1, castigos = 1)
        val fresh = UnseenCountsDto(pedidosPendentes = 1, castigosNovos = 2)

        assertEquals(listOf(Novidade.CASTIGO), novidades(baseline, fresh))
    }

    @Test
    fun categoriaQueCaiuNaoEntra() {
        // Item foi visto no app entre uma checagem e outra - a contagem caiu,
        // não subiu, então não é novidade.
        val baseline = NotificationBaseline(trombadices = 3)
        val fresh = UnseenCountsDto(trombadicesNovas = 1)

        assertEquals(emptyList<Novidade>(), novidades(baseline, fresh))
    }

    @Test
    fun variasCategoriasPodemSubirNoMesmoCiclo() {
        val fresh = UnseenCountsDto(castigosNovos = 1, decisoesNovas = 2)

        assertEquals(
            listOf(Novidade.CASTIGO, Novidade.DECISAO),
            novidades(NotificationBaseline(), fresh),
        )
    }

    @Test
    fun trombadiceEconquistaSaoIndependentes() {
        // É a razão de `/api/unseen` separar as duas: elas são a mesma tabela no
        // servidor, mas tocam sons diferentes, então uma subir não pode fazer a
        // outra tocar.
        val baseline = NotificationBaseline(trombadices = 1, conquistas = 1)
        val fresh = UnseenCountsDto(trombadicesNovas = 1, conquistasNovas = 2)

        assertEquals(listOf(Novidade.CONQUISTA), novidades(baseline, fresh))
    }

    @Test
    fun baselineZeradoEContagemZeradaNaoAvisaNada() {
        assertEquals(
            emptyList<Novidade>(),
            novidades(NotificationBaseline(), UnseenCountsDto()),
        )
    }

    @Test
    fun assuntoSobeIgualAsOutras() {
        // Assunto é o único aviso que chega pros dois papéis, mas a comparação
        // com o baseline é a mesma - do lado de cá não existe "quem sou eu".
        val baseline = NotificationBaseline(assuntos = 1)
        val fresh = UnseenCountsDto(assuntosNovos = 2)

        assertEquals(listOf(Novidade.ASSUNTO), novidades(baseline, fresh))
    }

    @Test
    fun contagemDeDevolveONumeroDaCategoria() {
        val fresh = UnseenCountsDto(
            pedidosPendentes = 1,
            trombadicesNovas = 2,
            conquistasNovas = 3,
            castigosNovos = 4,
            decisoesNovas = 5,
            assuntosNovos = 6,
        )

        assertEquals(1, fresh.contagemDe(Novidade.PEDIDO))
        assertEquals(2, fresh.contagemDe(Novidade.TROMBADICE))
        assertEquals(3, fresh.contagemDe(Novidade.CONQUISTA))
        assertEquals(4, fresh.contagemDe(Novidade.CASTIGO))
        assertEquals(5, fresh.contagemDe(Novidade.DECISAO))
        assertEquals(6, fresh.contagemDe(Novidade.ASSUNTO))
    }
}
