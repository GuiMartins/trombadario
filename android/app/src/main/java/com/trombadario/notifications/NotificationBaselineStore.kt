package com.trombadario.notifications

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.trombadario.data.remote.UnseenCountsDto
import kotlinx.coroutines.flow.first

private val Context.notificationBaselineDataStore
        by preferencesDataStore(name = "trombadario_notification_baseline")

/** Guarda a última contagem de `/api/unseen` que o worker viu, pra
 *  [shouldNotify] saber se subiu desde então. Store próprio, separado do
 *  `ServerConfigStore` - não é config de servidor, é estado interno do
 *  poller. */
class NotificationBaselineStore(private val context: Context) {

    suspend fun read(): NotificationBaseline {
        val prefs = context.notificationBaselineDataStore.data.first()
        return NotificationBaseline(
            pedidos = prefs[KEY_PEDIDOS] ?: 0,
            anotacoes = prefs[KEY_ANOTACOES] ?: 0,
            castigos = prefs[KEY_CASTIGOS] ?: 0,
            decisoes = prefs[KEY_DECISOES] ?: 0,
        )
    }

    suspend fun write(counts: UnseenCountsDto) {
        context.notificationBaselineDataStore.edit { prefs ->
            prefs[KEY_PEDIDOS] = counts.pedidosPendentes
            prefs[KEY_ANOTACOES] = counts.anotacoesNovas
            prefs[KEY_CASTIGOS] = counts.castigosNovos
            prefs[KEY_DECISOES] = counts.decisoesNovas
        }
    }

    private companion object {
        val KEY_PEDIDOS: Preferences.Key<Int> = intPreferencesKey("pedidos")
        val KEY_ANOTACOES: Preferences.Key<Int> = intPreferencesKey("anotacoes")
        val KEY_CASTIGOS: Preferences.Key<Int> = intPreferencesKey("castigos")
        val KEY_DECISOES: Preferences.Key<Int> = intPreferencesKey("decisoes")
    }
}
