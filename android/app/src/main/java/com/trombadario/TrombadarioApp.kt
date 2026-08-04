package com.trombadario

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.trombadario.notifications.NovidadesWorker
import java.util.concurrent.TimeUnit

class TrombadarioApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        scheduleNovidades()
    }

    /**
     * Sempre agendado, mesmo sem ninguém logado ainda - o próprio worker sai
     * cedo nesse caso. 15 minutos é o piso do `PeriodicWorkRequest`, não dá
     * pra pedir menos; `NetworkType.CONNECTED` é só um filtro grosseiro antes
     * da checagem de verdade ("está em casa?") lá dentro do worker.
     */
    private fun scheduleNovidades() {
        val request = PeriodicWorkRequestBuilder<NovidadesWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork("novidades", ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
