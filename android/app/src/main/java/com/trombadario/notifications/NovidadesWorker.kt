package com.trombadario.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.trombadario.R
import com.trombadario.TrombadarioApp
import com.trombadario.data.ApiResult
import com.trombadario.data.HomeState

/**
 * Checagem periódica (a cada 15 minutos, o piso do `PeriodicWorkRequest` -
 * agendado em `TrombadarioApp`) que só notifica quando: alguém está logado,
 * o celular está em casa agora (não confia no `HomeNetworkGate.state` já
 * guardado - ver `checkNow()`), e alguma categoria de `/api/unseen` subiu
 * desde a última vez que este worker rodou.
 *
 * Cada categoria vira uma notificação separada, no canal dela, com o som dela.
 *
 * Sem `WorkerFactory` customizado: o `AppContainer` é alcançado do mesmo jeito
 * que `MainActivity` já faz, via `applicationContext as TrombadarioApp`.
 */
class NovidadesWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as TrombadarioApp).container

        if (container.sessionStore.token.value == null) return Result.success()
        if (container.homeNetworkGate.checkNow() != HomeState.AtHome) return Result.success()

        val fresh = (container.repository.unseenCounts() as? ApiResult.Success)?.data
            ?: return Result.success()

        val store = NotificationBaselineStore(applicationContext)
        val novas = novidades(store.read(), fresh)

        if (novas.isNotEmpty()) {
            // Sem permissão de notificação ninguém ficou sabendo da novidade.
            // Avançar o baseline aqui a perderia pra sempre: quando a permissão
            // voltasse, a contagem já não estaria "subindo" em relação ao que
            // ficou guardado, e o item nunca mais geraria notificação. Melhor
            // deixar o baseline como está e tentar de novo no próximo ciclo.
            if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
                return Result.success()
            }
            novas.forEach { postNotification(applicationContext, it, fresh.contagemDe(it)) }
        }

        store.write(fresh)
        return Result.success()
    }

    private fun postNotification(context: Context, novidade: Novidade, quantidade: Int) {
        val canal = Canal.de(novidade)
        criarCanal(context, canal)

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = PendingIntent.getActivity(
            context,
            novidade.ordinal,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, canal.id)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(
                context.resources.getQuantityString(canal.texto, quantidade, quantidade)
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        // Um id por categoria: castigo novo não pode substituir a conquista que
        // ainda estava na barra.
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_BASE + novidade.ordinal, notification)
    }

    /**
     * Idempotente pro nome e a descrição, mas **não pro som**: o Android trata
     * som e importância como imutáveis depois que o canal existe, e ignora
     * silenciosamente qualquer tentativa de mudá-los. Por isso o id carrega
     * `_v1` - trocar o som de um tipo exige um id novo, senão quem já tem o app
     * instalado continua ouvindo o antigo pra sempre.
     */
    private fun criarCanal(context: Context, canal: Canal) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // A v1.1.0 criou um canal único "novidades", sem som próprio. Ele não
        // serve mais e ficaria órfão nas configurações do sistema.
        manager.deleteNotificationChannel(CANAL_ANTIGO)

        manager.createNotificationChannel(
            NotificationChannel(
                canal.id,
                context.getString(canal.nome),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(canal.descricao)
                setSound(
                    Uri.parse("android.resource://${context.packageName}/${canal.som}"),
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build(),
                )
            }
        )
    }

    /** O que cada tipo de novidade tem de próprio: canal, som e texto. */
    private data class Canal(
        val id: String,
        val nome: Int,
        val descricao: Int,
        val som: Int,
        val texto: Int,
    ) {
        companion object {
            fun de(novidade: Novidade): Canal = when (novidade) {
                Novidade.PEDIDO -> Canal(
                    id = "pedido_v1",
                    nome = R.string.canal_pedido,
                    descricao = R.string.canal_pedido_descricao,
                    som = R.raw.som_pedido,
                    texto = R.plurals.notificacao_pedido,
                )

                Novidade.TROMBADICE -> Canal(
                    id = "trombadice_v1",
                    nome = R.string.canal_trombadice,
                    descricao = R.string.canal_trombadice_descricao,
                    som = R.raw.som_trombadice,
                    texto = R.plurals.notificacao_trombadice,
                )

                Novidade.CONQUISTA -> Canal(
                    id = "conquista_v1",
                    nome = R.string.canal_conquista,
                    descricao = R.string.canal_conquista_descricao,
                    som = R.raw.som_conquista,
                    texto = R.plurals.notificacao_conquista,
                )

                Novidade.CASTIGO -> Canal(
                    id = "castigo_v1",
                    nome = R.string.canal_castigo,
                    descricao = R.string.canal_castigo_descricao,
                    som = R.raw.som_castigo,
                    texto = R.plurals.notificacao_castigo,
                )

                Novidade.DECISAO -> Canal(
                    id = "decisao_v1",
                    nome = R.string.canal_decisao,
                    descricao = R.string.canal_decisao_descricao,
                    som = R.raw.som_decisao,
                    texto = R.plurals.notificacao_decisao,
                )

                // O único canal que existe nos dois papéis - o texto serve pros
                // dois porque o fato é o mesmo dos dois lados ("tem assunto
                // esperando conversa"), e dizer quem trouxe exigiria buscar a
                // lista, que é justamente o que este poller não faz.
                Novidade.ASSUNTO -> Canal(
                    id = "assunto_v1",
                    nome = R.string.canal_assunto,
                    descricao = R.string.canal_assunto_descricao,
                    som = R.raw.som_assunto,
                    texto = R.plurals.notificacao_assunto,
                )
            }
        }
    }

    private companion object {
        const val CANAL_ANTIGO = "novidades"
        const val NOTIFICATION_ID_BASE = 100
    }
}
