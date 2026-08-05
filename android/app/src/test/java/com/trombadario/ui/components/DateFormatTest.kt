package com.trombadario.ui.components

import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * O app manda e recebe sempre UTC e mostra sempre em hora local. Errar isso
 * significa um acontecimento aparecer 3 horas fora do lugar no Brasil.
 */
class DateFormatTest {

    private val original = TimeZone.getDefault()

    @Before
    fun useBrasilia() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"))
    }

    @After
    fun restore() {
        TimeZone.setDefault(original)
    }

    @Test
    fun `utc do servidor vira hora local na tela`() {
        // 17:30Z = 14:30 em Brasília (UTC-3)
        assertEquals("01/08/2026 às 14:30", parseInstant("2026-08-01T17:30:00Z").formatDateTime())
    }

    @Test
    fun `hora local escolhida no formulario volta pra utc`() {
        val instant = localToInstant(LocalDate.of(2026, 8, 1), 14, 30)

        assertEquals("2026-08-01T17:30:00Z", instant.toIsoUtc())
    }

    @Test
    fun `ida e volta preserva o horario`() {
        val instant = localToInstant(LocalDate.of(2026, 12, 25), 23, 59)

        assertEquals("25/12/2026 às 23:59", parseInstant(instant.toIsoUtc()).formatDateTime())
    }

    @Test
    fun `evento na virada do dia nao troca de data`() {
        // 02:00Z de 2 de agosto ainda é dia 1 em Brasília - o caso clássico de
        // um acontecimento da noite aparecer no dia seguinte.
        val instant = parseInstant("2026-08-02T02:00:00Z")

        assertEquals(LocalDate.of(2026, 8, 1), instant.toLocalDate())
        assertEquals("01/08/2026", instant.formatDate())
    }

    @Test
    fun `so a hora, pro feito de hoje`() {
        // "feito hoje, às 14:30" - repetir a data ao lado de "hoje" seria ruído.
        assertEquals("14:30", parseInstant("2026-08-01T17:30:00Z").formatTime())
    }

    @Test
    fun `formatacao usa o fuso do aparelho, nao um fixo`() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

        assertEquals("01/08/2026 às 17:30", parseInstant("2026-08-01T17:30:00Z").formatDateTime())
        assertEquals(ZoneId.of("UTC"), ZoneId.systemDefault())
    }
}
