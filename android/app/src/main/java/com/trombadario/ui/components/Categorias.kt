package com.trombadario.ui.components

import androidx.annotation.StringRes
import com.trombadario.R
import com.trombadario.data.remote.Categoria

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
    else -> R.string.categoria_outra
}
