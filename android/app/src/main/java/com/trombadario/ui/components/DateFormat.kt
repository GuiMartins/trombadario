package com.trombadario.ui.components

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PT_BR = Locale("pt", "BR")
private val DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", PT_BR)
private val DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", PT_BR)
private val DAY_HEADER = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", PT_BR)
private val TIME = DateTimeFormatter.ofPattern("HH:mm", PT_BR)

/** The API always sends UTC with an explicit offset; everything the user sees is
 *  in the phone's own timezone. */
fun parseInstant(iso: String): Instant = Instant.parse(iso)

fun Instant.toLocalDateTime(): LocalDateTime = LocalDateTime.ofInstant(this, ZoneId.systemDefault())

fun Instant.formatDateTime(): String = toLocalDateTime().format(DATE_TIME)

fun Instant.formatDate(): String = toLocalDateTime().format(DATE)

/** Só a hora: "feito hoje, às 08:12" já disse o dia. */
fun Instant.formatTime(): String = toLocalDateTime().format(TIME)

fun Instant.formatDayHeader(): String =
    toLocalDateTime().format(DAY_HEADER).replaceFirstChar { it.titlecase(PT_BR) }

fun Instant.toLocalDate(): LocalDate = toLocalDateTime().toLocalDate()

/** Local wall-clock date+time back to the instant the API expects. */
fun localToInstant(date: LocalDate, hour: Int, minute: Int): Instant =
    date.atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant()

fun Instant.toIsoUtc(): String = DateTimeFormatter.ISO_INSTANT.format(this)

/**
 * "2014-03-25" (como a API manda o aniversário) para "25/03/2014".
 *
 * Dia sem hora, então **nada de fuso aqui**, ao contrário de todo o resto deste
 * arquivo: converter um dia para instante e de volta é como ele vira o dia
 * anterior em algum lugar do mundo.
 *
 * Data que não parseia volta como veio, em vez de estourar: uma tela de contas
 * não pode cair porque um servidor mais novo mudou o formato de um campo.
 */
fun formatIsoDate(iso: String): String =
    runCatching { LocalDate.parse(iso).format(DATE) }.getOrDefault(iso)
