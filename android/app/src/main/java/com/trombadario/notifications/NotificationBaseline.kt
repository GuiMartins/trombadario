package com.trombadario.notifications

import com.trombadario.data.remote.UnseenCountsDto

/** Última contagem conhecida por categoria - guardada localmente pra decidir
 *  se vale notificar de novo. Sem Android nenhum aqui de propósito: é a parte
 *  pura e testável em JVM da lógica do worker. */
data class NotificationBaseline(
    val pedidos: Int = 0,
    val anotacoes: Int = 0,
    val castigos: Int = 0,
    val decisoes: Int = 0,
)

/**
 * Só notifica quando alguma categoria **subiu** em relação ao que já foi
 * visto na última vez que o worker rodou.
 *
 * Sem essa comparação, o mesmo item ainda não lido geraria uma notificação
 * nova a cada 15 minutos - a contagem em si não zera sozinha, só quando a
 * pessoa abre a tela de verdade no app (é o "visto" de sempre, do lado do
 * servidor, que faz a próxima leitura de `/api/unseen` cair).
 */
fun shouldNotify(baseline: NotificationBaseline, fresh: UnseenCountsDto): Boolean =
    fresh.pedidosPendentes > baseline.pedidos ||
        fresh.anotacoesNovas > baseline.anotacoes ||
        fresh.castigosNovos > baseline.castigos ||
        fresh.decisoesNovas > baseline.decisoes
