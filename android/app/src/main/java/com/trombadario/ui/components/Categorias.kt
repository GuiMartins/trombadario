package com.trombadario.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.trombadario.ui.theme.ConquistaTeal
import com.trombadario.ui.theme.ConquistaTealDark
import com.trombadario.R
import com.trombadario.data.remote.Categoria
import com.trombadario.data.remote.Tipo

/**
 * O nome de tela de cada categoria.
 *
 * O backend manda o valor cru (`"nao_fez"`); traduzir aqui e não lá é o mesmo
 * critério do painel web - o modelo guarda o valor, quem mostra escolhe como
 * chamar. Os dois lados usam a mesma lista e a mesma ordem, que é a ordem do
 * enum, do mais comum ao menos.
 *
 * Valor desconhecido cai em "Outra" em vez de estourar: se um dia o servidor
 * ganhar uma categoria nova, o app antigo mostra algo razoável em vez de
 * fechar.
 */
@StringRes
fun rotuloDaCategoria(valor: String): Int = when (valor) {
    Categoria.DESRESPEITO -> R.string.categoria_desrespeito
    Categoria.EDUCACAO -> R.string.categoria_educacao
    Categoria.NAO_FEZ -> R.string.categoria_nao_fez
    Categoria.MENTIRA -> R.string.categoria_mentira
    Categoria.BIRRA -> R.string.categoria_birra
    Categoria.ESCOLA -> R.string.categoria_escola
    Categoria.AGRESSAO -> R.string.categoria_agressao
    Categoria.AJUDOU -> R.string.categoria_ajudou
    Categoria.RESPONSABILIDADE -> R.string.categoria_responsabilidade
    Categoria.ESTUDOU -> R.string.categoria_estudou
    Categoria.GENTILEZA -> R.string.categoria_gentileza
    Categoria.INICIATIVA -> R.string.categoria_iniciativa
    Categoria.SUPEROU -> R.string.categoria_superou
    Categoria.CUIDOU -> R.string.categoria_cuidou
    Categoria.OUTRA_BOA -> R.string.categoria_outra_boa
    else -> R.string.categoria_outra
}

/** O nome de tela do tipo do registro. */
@StringRes
fun rotuloDoTipo(valor: String): Int =
    if (valor == Tipo.CONQUISTA) R.string.tipo_conquista else R.string.tipo_trombadice

/** No plural, para os chips de filtro. */
@StringRes
fun rotuloDoTipoPlural(valor: String): Int =
    if (valor == Tipo.CONQUISTA) R.string.tipo_conquistas else R.string.tipo_trombadices

fun ehConquista(kind: String): Boolean = kind == Tipo.CONQUISTA

/** Teal no claro, teal claro no escuro - contraste medido nos dois papéis. */
@Composable
fun corDaConquista(): Color =
    if (isSystemInDarkTheme()) ConquistaTealDark else ConquistaTeal
