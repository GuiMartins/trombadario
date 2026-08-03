package com.trombadario.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.trombadario.R
import com.trombadario.data.remote.Categoria
import com.trombadario.data.remote.UserDto
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val DIA_CURTO = DateTimeFormatter.ofPattern("dd/MM")

/**
 * A barra de filtros de trombadices e castigos: as duas telas fazem as mesmas
 * perguntas, então o bloco é um só - igual ao `_filtros.html` do painel.
 *
 * O calendário **só deixa escolher dia que tem registro**. Os outros ficam
 * apagados de propósito: escolher um dia vazio só levaria a uma lista vazia. E
 * a lista de dias já chega filtrada pelos outros critérios, então com
 * "agressão" ligado só acende dia que teve agressão.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FiltroBar(
    children: List<UserDto>,
    selectedChildId: Int?,
    onSelectChild: (Int?) -> Unit,
    category: String?,
    onSelectCategory: (String?) -> Unit,
    busca: String,
    onBuscaChange: (String) -> Unit,
    onBuscar: () -> Unit,
    dia: LocalDate?,
    diasComRegistro: Set<LocalDate>,
    onSelectDia: (LocalDate?) -> Unit,
    filtrando: Boolean,
    onLimpar: () -> Unit,
    dicaDeBusca: Int = R.string.filtro_busca_dica,
    modifier: Modifier = Modifier,
) {
    var mostrarCalendario by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (children.size > 1) {
            LinhaDeChips {
                FilterChip(
                    selected = selectedChildId == null,
                    onClick = { onSelectChild(null) },
                    label = { Text(stringResource(R.string.feed_filter_all)) },
                )
                children.forEach { child ->
                    FilterChip(
                        selected = selectedChildId == child.id,
                        onClick = { onSelectChild(child.id) },
                        label = { Text(child.displayName) },
                    )
                }
            }
        }

        LinhaDeChips {
            FilterChip(
                selected = category == null,
                onClick = { onSelectCategory(null) },
                label = { Text(stringResource(R.string.categoria_qualquer)) },
            )
            Categoria.TODAS.forEach { valor ->
                FilterChip(
                    selected = category == valor,
                    onClick = { onSelectCategory(valor) },
                    label = { Text(stringResource(rotuloDaCategoria(valor))) },
                )
            }
        }

        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = busca,
                onValueChange = onBuscaChange,
                label = { Text(stringResource(dicaDeBusca)) },
                singleLine = true,
                // Buscar a cada tecla faria uma chamada por letra; o teclado
                // manda buscar quando a pessoa terminou.
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onBuscar() }),
                trailingIcon = {
                    TextButton(onClick = onBuscar) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.filtro_buscar))
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(8.dp))

        LinhaDeChips {
            AssistChip(
                // Sem nenhum dia com registro não há o que escolher, então o
                // botão não abre um calendário todo apagado.
                onClick = { if (diasComRegistro.isNotEmpty()) mostrarCalendario = true },
                enabled = diasComRegistro.isNotEmpty(),
                leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                label = {
                    Text(
                        if (dia != null) {
                            stringResource(R.string.filtro_dia_escolhido, dia.format(DIA_CURTO))
                        } else {
                            stringResource(R.string.filtro_por_dia)
                        }
                    )
                },
            )
            if (dia != null) {
                AssistChip(
                    onClick = { onSelectDia(null) },
                    label = { Text(stringResource(R.string.filtro_ver_todos_os_dias)) },
                )
            }
            if (filtrando) {
                AssistChip(
                    onClick = onLimpar,
                    leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                    label = { Text(stringResource(R.string.filtro_limpar)) },
                )
            }
        }
    }

    if (mostrarCalendario) {
        val selecionaveis = remember(diasComRegistro) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    // O DatePicker fala em milissegundos de meia-noite UTC, não
                    // em data local: converter pelo fuso do aparelho deslocaria
                    // o dia inteiro.
                    diaDeMillisUtc(utcTimeMillis) in diasComRegistro

                override fun isSelectableYear(year: Int): Boolean =
                    diasComRegistro.any { it.year == year }
            }
        }
        val estado = rememberDatePickerState(
            initialSelectedDateMillis = dia?.let { millisUtcDoDia(it) },
            selectableDates = selecionaveis,
        )
        DatePickerDialog(
            onDismissRequest = { mostrarCalendario = false },
            confirmButton = {
                TextButton(onClick = {
                    estado.selectedDateMillis?.let { onSelectDia(diaDeMillisUtc(it)) }
                    mostrarCalendario = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { mostrarCalendario = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = estado)
        }
    }
}

private fun diaDeMillisUtc(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

private fun millisUtcDoDia(dia: LocalDate): Long =
    dia.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

@Composable
private fun LinhaDeChips(conteudo: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        conteudo()
        // Respiro no fim da rolagem, senão o último chip encosta na borda.
        Spacer(Modifier.width(8.dp))
    }
}
