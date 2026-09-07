package com.trombadario.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.trombadario.ui.theme.ConquistaTeal
import com.trombadario.ui.theme.ConquistaTealDark
import com.trombadario.R
import androidx.compose.ui.res.stringResource
import com.trombadario.data.remote.CategoriaDeConquista
import com.trombadario.data.remote.Tipo
import com.trombadario.data.remote.TrombadiceDto

/**
 * O nome de tela de cada categoria **de conquista**.
 *
 * O backend manda o valor cru (`"ajudou"`); traduzir aqui e não lá é o mesmo
 * critério do painel web - o modelo guarda o valor, quem mostra escolhe como
 * chamar.
 *
 * A lista de trombadice não passa por aqui: ela é cadastrada pelo pai, então o
 * nome vem do servidor junto do registro (`TrombadiceDto.categoryName`) - não
 * existe `strings.xml` que saiba o que ele vai escrever.
 *
 * Valor desconhecido cai em "Outra coisa boa" em vez de estourar: se um dia o
 * servidor ganhar uma categoria nova, o app antigo mostra algo razoável em vez
 * de fechar.
 */
@StringRes
fun rotuloDaConquista(valor: String): Int = when (valor) {
    CategoriaDeConquista.AJUDOU -> R.string.categoria_ajudou
    CategoriaDeConquista.RESPONSABILIDADE -> R.string.categoria_responsabilidade
    CategoriaDeConquista.ESTUDOU -> R.string.categoria_estudou
    CategoriaDeConquista.GENTILEZA -> R.string.categoria_gentileza
    CategoriaDeConquista.INICIATIVA -> R.string.categoria_iniciativa
    CategoriaDeConquista.SUPEROU -> R.string.categoria_superou
    CategoriaDeConquista.CUIDOU -> R.string.categoria_cuidou
    else -> R.string.categoria_outra_boa
}

/**
 * O nome do tipo de um registro, venha ele da lista que vier: a cadastrada pelo
 * pai (trombadice, com o nome já no próprio registro) ou a fechada (conquista,
 * traduzida aqui). Nulo no que foi cadastrado sem tipo nenhum.
 */
@Composable
fun nomeDoTipo(trombadice: TrombadiceDto): String? {
    val conquista = trombadice.conquistaCategory
    return when {
        trombadice.categoryName != null -> trombadice.categoryName
        conquista != null -> stringResource(rotuloDaConquista(conquista))
        else -> null
    }
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
