package com.trombadario.notifications

import com.trombadario.data.remote.UnseenCountsDto

/**
 * Os tipos de novidade que o app avisa. Cada um tem **canal e som próprios** -
 * no Android o som é propriedade do `NotificationChannel`, não da notificação,
 * então tipos com sons diferentes são obrigatoriamente canais diferentes. De
 * brinde, quem usa ganha controle por tipo nas configurações do sistema (dá pra
 * silenciar conquista e manter castigo) sem nada de código.
 *
 * `PEDIDO` é o único que chega pro pai; os outros quatro são do filho.
 */
enum class Novidade {
    PEDIDO,
    TROMBADICE,
    CONQUISTA,
    CASTIGO,
    DECISAO,
}

/** Última contagem conhecida por categoria - guardada localmente pra decidir
 *  se vale notificar de novo. Sem Android nenhum aqui de propósito: é a parte
 *  pura e testável em JVM da lógica do worker. */
data class NotificationBaseline(
    val pedidos: Int = 0,
    val trombadices: Int = 0,
    val conquistas: Int = 0,
    val castigos: Int = 0,
    val decisoes: Int = 0,
)

/**
 * Quais categorias **subiram** em relação à última passada do worker.
 *
 * Sem essa comparação, o mesmo item ainda não lido geraria uma notificação nova
 * a cada 15 minutos - a contagem em si não zera sozinha, só quando a pessoa abre
 * a tela de verdade no app (é o "visto" de sempre, do lado do servidor, que faz
 * a próxima leitura de `/api/unseen` cair).
 *
 * Devolve a lista, e não um sim/não, porque cada categoria vira uma notificação
 * separada no seu próprio canal - é o que faz cada tipo tocar o som dele.
 */
fun novidades(baseline: NotificationBaseline, fresh: UnseenCountsDto): List<Novidade> = buildList {
    if (fresh.pedidosPendentes > baseline.pedidos) add(Novidade.PEDIDO)
    if (fresh.trombadicesNovas > baseline.trombadices) add(Novidade.TROMBADICE)
    if (fresh.conquistasNovas > baseline.conquistas) add(Novidade.CONQUISTA)
    if (fresh.castigosNovos > baseline.castigos) add(Novidade.CASTIGO)
    if (fresh.decisoesNovas > baseline.decisoes) add(Novidade.DECISAO)
}

/** Quantos ainda não vistos naquela categoria - é o número que vai no texto da
 *  notificação ("2 conquistas novas"). */
fun UnseenCountsDto.contagemDe(novidade: Novidade): Int = when (novidade) {
    Novidade.PEDIDO -> pedidosPendentes
    Novidade.TROMBADICE -> trombadicesNovas
    Novidade.CONQUISTA -> conquistasNovas
    Novidade.CASTIGO -> castigosNovos
    Novidade.DECISAO -> decisoesNovas
}
