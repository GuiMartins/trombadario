package com.trombadario.ui.tipos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * As regras que a tela aplica antes de mandar o tipo pro servidor.
 *
 * Elas existem porque o que passa dos limites da API volta 422 - uma recusa que
 * a tela não sabia nomear e que aparecia como "não consegui falar com o
 * servidor", mandando procurar problema na rede com o servidor respondendo.
 */
class ValidarTipoTest {

    @Test
    fun tipoComumPassa() {
        assertNull(validar(TipoEditor(name = "Mentira", punishmentDays = "1", maxDays = "5")))
    }

    @Test
    fun camposDeNumeroVaziosSaoZeroENaoErro() {
        // Vazio é o desligado: não gera castigo, sem teto, e vai pro fim da fila.
        assertNull(validar(TipoEditor(name = "Birra")))
    }

    @Test
    fun nomeSoComEspacoContaComoVazio() {
        assertEquals(ErroDoTipo.NOME_VAZIO, validar(TipoEditor(name = "   ")))
    }

    @Test
    fun nomeNoLimiteAindaPassa() {
        assertNull(validar(TipoEditor(name = "a".repeat(LimitesDoTipo.NOME))))
    }

    @Test
    fun nomeAcimaDoLimiteERecusadoAqui() {
        assertEquals(
            ErroDoTipo.NOME_LONGO,
            validar(TipoEditor(name = "a".repeat(LimitesDoTipo.NOME + 1))),
        )
    }

    @Test
    fun ordemAcimaDoLimiteERecusadaAqui() {
        assertEquals(
            ErroDoTipo.ORDEM_ALTA,
            validar(TipoEditor(name = "Mentira", position = "1000")),
        )
    }

    @Test
    fun diasAcimaDoLimiteSaoRecusadosEmQualquerDosTresCampos() {
        val demais = (LimitesDoTipo.DIAS + 1).toString()
        assertEquals(
            ErroDoTipo.DIAS_DEMAIS,
            validar(TipoEditor(name = "Mentira", punishmentDays = demais)),
        )
        assertEquals(
            ErroDoTipo.DIAS_DEMAIS,
            validar(TipoEditor(name = "Mentira", escalationDays = demais)),
        )
        assertEquals(
            ErroDoTipo.DIAS_DEMAIS,
            validar(TipoEditor(name = "Mentira", maxDays = demais)),
        )
    }

    @Test
    fun tetoMenorQueABaseContinuaSendoRecusado() {
        assertEquals(
            ErroDoTipo.TETO_MENOR,
            validar(TipoEditor(name = "Mentira", punishmentDays = "5", maxDays = "2")),
        )
    }

    @Test
    fun tetoZeroNaoContradizNada() {
        // Zero em teto é "sem teto", não um teto de zero dia.
        assertNull(validar(TipoEditor(name = "Mentira", punishmentDays = "5", maxDays = "0")))
    }
}
