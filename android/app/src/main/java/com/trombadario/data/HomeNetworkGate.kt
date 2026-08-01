package com.trombadario.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import com.trombadario.data.remote.ApiProvider
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface HomeState {
    /** No server configured yet - the setup screen owns this case. */
    data object Unpaired : HomeState

    data object Checking : HomeState

    data object AtHome : HomeState

    data object AwayFromHome : HomeState

    /** Something answered, but it is not the server this app was paired with. */
    data object WrongServer : HomeState
}

/**
 * Decides whether the phone is on the home network, by asking the home server
 * itself. See CLAUDE.md for why this is reachability and not GPS.
 *
 * Fails closed: anything other than a healthy answer from the paired server
 * blocks the app.
 */
class HomeNetworkGate(
    context: Context,
    private val apiProvider: ApiProvider,
    private val serverConfigStore: ServerConfigStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val checkLock = Mutex()

    private val _state = MutableStateFlow<HomeState>(HomeState.Checking)
    val state: StateFlow<HomeState> = _state.asStateFlow()

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    init {
        // Without this the app would only notice a network change on the next
        // request timeout - walking out the door would leave the feed on screen.
        connectivityManager.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = refresh()
                override fun onLost(network: Network) = refresh()
            }
        )
        refresh()
    }

    fun refresh() {
        scope.launch { check() }
    }

    /** Called by the repository when a request failed on the network layer, so
     *  the gate flips immediately instead of on the next poll. */
    fun reportUnreachable() {
        if (_state.value == HomeState.AtHome) refresh()
    }

    private suspend fun check() = checkLock.withLock {
        val baseUrl = serverConfigStore.baseUrl.first()
        val pairedServerId = serverConfigStore.pairedServerId.first()
        if (baseUrl == null || pairedServerId == null) {
            _state.value = HomeState.Unpaired
            return@withLock
        }

        _state.value = HomeState.Checking
        _state.value = try {
            val health = apiProvider.api(baseUrl).health()
            when {
                health.serverId == pairedServerId -> HomeState.AtHome
                else -> HomeState.WrongServer
            }
        } catch (e: IOException) {
            HomeState.AwayFromHome
        } catch (e: Exception) {
            // A non-IO failure means something answered but not with the health
            // payload - still not the home server.
            Log.w(TAG, "Resposta inesperada do /api/health", e)
            HomeState.WrongServer
        }
    }

    private companion object {
        const val TAG = "HomeNetworkGate"
    }
}
