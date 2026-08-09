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
 *  [novidades] saber quais subiram desde então. Store próprio, separado do
 *  `ServerConfigStore` - não é config de servidor, é estado interno do
 *  poller. */
class NotificationBaselineStore(private val context: Context) {

    suspend fun read(): NotificationBaseline {
        val prefs = context.notificationBaselineDataStore.data.first()
        return NotificationBaseline(
            pedidos = prefs[KEY_PEDIDOS] ?: 0,
            trombadices = prefs[KEY_TROMBADICES] ?: 0,
            conquistas = prefs[KEY_CONQUISTAS] ?: 0,
            castigos = prefs[KEY_CASTIGOS] ?: 0,
            decisoes = prefs[KEY_DECISOES] ?: 0,
            assuntos = prefs[KEY_ASSUNTOS] ?: 0,
        )
    }

    suspend fun write(counts: UnseenCountsDto) {
        context.notificationBaselineDataStore.edit { prefs ->
            prefs[KEY_PEDIDOS] = counts.pedidosPendentes
            prefs[KEY_TROMBADICES] = counts.trombadicesNovas
            prefs[KEY_CONQUISTAS] = counts.conquistasNovas
            prefs[KEY_CASTIGOS] = counts.castigosNovos
            prefs[KEY_DECISOES] = counts.decisoesNovas
            prefs[KEY_ASSUNTOS] = counts.assuntosNovos
        }
    }

    private companion object {
        val KEY_PEDIDOS: Preferences.Key<Int> = intPreferencesKey("pedidos")
        // A chave antiga "anotacoes" juntava trombadice e conquista e não é mais
        // lida. Quem atualizar começa com os dois em 0, então a primeira passada
        // do worker pode avisar de coisa que já estava lá - uma vez só, e é
        // preferível a calar uma novidade de verdade.
        val KEY_TROMBADICES: Preferences.Key<Int> = intPreferencesKey("trombadices")
        val KEY_CONQUISTAS: Preferences.Key<Int> = intPreferencesKey("conquistas")
        val KEY_CASTIGOS: Preferences.Key<Int> = intPreferencesKey("castigos")
        val KEY_DECISOES: Preferences.Key<Int> = intPreferencesKey("decisoes")
        val KEY_ASSUNTOS: Preferences.Key<Int> = intPreferencesKey("assuntos")
    }
}
