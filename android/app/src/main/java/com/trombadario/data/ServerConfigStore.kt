package com.trombadario.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.trombadario.ui.theme.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "trombadario_settings")

/**
 * Server address, the paired server id and the theme choice. Plain DataStore on
 * purpose - none of this is a secret (the token lives in [SessionStore]).
 *
 * The address is configurable rather than compiled in because the ZimaOS box
 * gets its IP from DHCP; baking it into the APK would mean a rebuild and a
 * reinstall on every phone whenever the router hands out a different address.
 */
class ServerConfigStore(private val context: Context) {

    val baseUrl: Flow<String?> = context.dataStore.data.map { it[KEY_BASE_URL] }

    val theme: Flow<ThemePreference> = context.dataStore.data.map { prefs ->
        prefs[KEY_THEME]?.let { runCatching { ThemePreference.valueOf(it) }.getOrNull() }
            ?: ThemePreference.SYSTEM
    }

    suspend fun setBaseUrl(baseUrl: String) {
        context.dataStore.edit { it[KEY_BASE_URL] = baseUrl }
    }

    suspend fun clearBaseUrl() {
        context.dataStore.edit { it.remove(KEY_BASE_URL) }
    }

    suspend fun setTheme(preference: ThemePreference) {
        context.dataStore.edit { it[KEY_THEME] = preference.name }
    }

    private companion object {
        val KEY_BASE_URL: Preferences.Key<String> = stringPreferencesKey("base_url")
        val KEY_THEME: Preferences.Key<String> = stringPreferencesKey("theme")
    }
}
