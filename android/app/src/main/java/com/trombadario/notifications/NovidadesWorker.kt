package com.trombadario.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.trombadario.R
import com.trombadario.TrombadarioApp
import com.trombadario.data.ApiResult
import com.trombadario.data.HomeState
import com.trombadario.data.remote.UnseenCountsDto

/**
 * Checagem periódica (a cada 15 minutos, o piso do `PeriodicWorkRequest` -
 * agendado em `TrombadarioApp`) que só notifica quando: alguém está logado,
 * o celular está em casa agora (não confia no `HomeNetworkGate.state` já
 * guardado - ver `checkNow()`), e alguma categoria de `/api/unseen` subiu
 * desde a última vez que este worker rodou.
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
        val baseline = store.read()

        if (shouldNotify(baseline, fresh) &&
            NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()
        ) {
            postNotification(applicationContext, fresh)
        }

        store.write(fresh)
        return Result.success()
    }

    private fun postNotification(context: Context, counts: UnseenCountsDto) {
        createChannelIfNeeded(context)

        val body = buildBody(context, counts)
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    /** Junta as categorias que subiram - várias podem disparar no mesmo ciclo
     *  se o app ficou fechado um tempo. */
    private fun buildBody(context: Context, counts: UnseenCountsDto): String = buildList {
        if (counts.pedidosPendentes > 0) add(context.getString(R.string.notification_pedidos_pendentes))
        if (counts.anotacoesNovas > 0) add(context.getString(R.string.notification_anotacoes_novas))
        if (counts.castigosNovos > 0) add(context.getString(R.string.notification_castigos_novos))
        if (counts.decisoesNovas > 0) add(context.getString(R.string.notification_decisoes_novas))
    }.joinToString(" · ")

    private fun createChannelIfNeeded(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_novidades),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_novidades_description)
        }
        manager.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "novidades"
        const val NOTIFICATION_ID = 1
    }
}
