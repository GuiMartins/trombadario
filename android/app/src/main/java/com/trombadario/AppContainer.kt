package com.trombadario

import android.content.Context
import com.trombadario.data.HomeNetworkGate
import com.trombadario.data.ServerConfigStore
import com.trombadario.data.SessionStore
import com.trombadario.data.TrombadarioRepository
import com.trombadario.data.remote.ApiProvider

/**
 * Manual dependency injection. The graph is small enough that a DI framework
 * would be more moving parts than it saves - the official Android guidance
 * starts here and only reaches for Hilt when the graph grows.
 */
class AppContainer(context: Context) {

    val sessionStore = SessionStore(context)
    val serverConfigStore = ServerConfigStore(context)
    private val apiProvider = ApiProvider(sessionStore)

    val homeNetworkGate = HomeNetworkGate(context, apiProvider, serverConfigStore)

    val repository = TrombadarioRepository(apiProvider, serverConfigStore, sessionStore).apply {
        onUnreachable = homeNetworkGate::reportUnreachable
        onSessionExpired = homeNetworkGate::refresh
    }

    /** Health check used by the setup screen, before anything is paired. */
    suspend fun probe(baseUrl: String) = apiProvider.api(baseUrl).health()
}
