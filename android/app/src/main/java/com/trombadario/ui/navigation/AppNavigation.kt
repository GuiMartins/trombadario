package com.trombadario.ui.navigation

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trombadario.AppContainer
import com.trombadario.R
import com.trombadario.data.ApiResult
import com.trombadario.data.HomeState
import com.trombadario.data.remote.BirthdayDto
import com.trombadario.data.remote.UserDto
import com.trombadario.ui.awayfromhome.AwayFromHomeScreen
import com.trombadario.ui.birthday.BirthdayScreen
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import com.trombadario.ui.components.LoadingScreen
import com.trombadario.ui.components.MessageScreen
import com.trombadario.ui.login.LoginScreen
import com.trombadario.ui.serversetup.ServerSetupScreen
import com.trombadario.ui.splash.SPLASH_DURATION_MS
import com.trombadario.ui.splash.SplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Root of the UI. The home-network gate is checked *before* the login screen, so
 * away from home not even the password field is reachable - and every non-AtHome
 * state replaces the graph entirely rather than covering it.
 */
@Composable
fun AppNavigation(container: AppContainer) {
    val homeState by container.homeNetworkGate.state.collectAsStateWithLifecycle()
    val token by container.sessionStore.token.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val unpair: () -> Unit = {
        scope.launch {
            container.repository.logout()
            container.serverConfigStore.clearBaseUrl()
            container.homeNetworkGate.refresh()
        }
    }

    when (homeState) {
        HomeState.Unpaired -> ServerSetupScreen(container)

        HomeState.Checking -> LoadingScreen(stringResource(R.string.away_checking))

        HomeState.AwayFromHome -> AwayFromHomeScreen(
            wrongServer = false,
            onRetry = container.homeNetworkGate::refresh,
            onChangeServer = unpair,
        )

        HomeState.WrongServer -> AwayFromHomeScreen(
            wrongServer = true,
            onRetry = container.homeNetworkGate::refresh,
            onChangeServer = unpair,
        )

        is HomeState.SetupRequired -> MessageScreen(
            icon = Icons.Default.Dns,
            title = stringResource(R.string.setup_needed_title),
            message = stringResource(
                R.string.setup_needed_message,
                (homeState as HomeState.SetupRequired).baseUrl,
            ),
            actionLabel = stringResource(R.string.action_retry),
            onAction = container.homeNetworkGate::refresh,
        )

        HomeState.AtHome -> if (token == null) {
            LoginScreen(container)
        } else {
            AuthenticatedApp(container)
        }
    }
}

/**
 * Resolves who is logged in before showing anything. The role decides which
 * destinations exist at all - the backend enforces it too, this is only so the
 * UI doesn't offer what would be refused.
 */
@Composable
private fun AuthenticatedApp(container: AppContainer) {
    var currentUser by remember { mutableStateOf<UserDto?>(null) }
    var splashMessage by remember { mutableStateOf<String?>(null) }
    var splashDone by remember { mutableStateOf(false) }
    var birthday by remember { mutableStateOf<BirthdayDto?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        // A 401 here clears the token, which sends the root back to login on its
        // own - no navigation needed.
        val user = (container.repository.me() as? ApiResult.Success)?.data

        if (user != null && !user.isAdmin) {
            // A festa é decidida antes da frase de abertura: no dia dele o app
            // não abre nem a tela de carregamento, vai direto pro aniversário.
            val hoje = (container.repository.birthday() as? ApiResult.Success)?.data
            birthday = hoje

            // Only the child gets the phrase - it is the parent talking to them,
            // and the parent seeing their own message on every open would just
            // be noise.
            if (hoje?.isBirthday != true) {
                splashMessage = (container.repository.randomSplashMessage() as? ApiResult.Success)
                    ?.data
                    ?.text
            }
        }
        currentUser = user
    }

    // Reconfere ao voltar do background, e serve pros dois sentidos: o dia virou
    // com o app aberto, ou o pai corrigiu uma data digitada errada - e nesse
    // caso o filho não pode ficar trancado numa festa que não é dele até amanhã.
    //
    // Só sobrescreve quando a resposta chega: uma falha de rede não pode
    // desligar a festa (nem ligar), e quem trata servidor fora do ar é o gate.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val user = currentUser
        if (user != null && !user.isAdmin) {
            scope.launch {
                (container.repository.birthday() as? ApiResult.Success)?.let { birthday = it.data }
            }
        }
    }

    // Pede a permissão de notificação (a "novidades" do NovidadesWorker) toda
    // vez que alguém autentica. Seguro chamar sempre: se já concedida, o
    // sistema não mostra nada; se negada de vez, o próprio Android suprime o
    // diálogo - não precisa de estado próprio pra lembrar "já pedi".
    val notificationPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(currentUser) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && currentUser != null) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val user = currentUser
    val festa = birthday
    when {
        user == null -> LoadingScreen()

        // O dia do filho: a tela de festa **substitui** o app, não fica por
        // cima dele. Não existe trombadice, tarefa nem castigo hoje - foi o
        // pedido, e meia medida (uma faixa no topo do feed) seria o
        // Trombadário aparecendo mesmo assim.
        festa?.isBirthday == true -> BirthdayScreen(
            childName = festa.displayName.ifBlank { user.displayName },
            age = festa.age,
        )

        // No phrase registered that applies to them: skip the screen entirely
        // rather than show an empty one.
        splashMessage != null && !splashDone -> {
            SplashScreen(childName = user.displayName, message = splashMessage!!)
            LaunchedEffect(Unit) {
                delay(SPLASH_DURATION_MS.toLong())
                splashDone = true
            }
        }

        else -> MainNavHost(container = container, currentUser = user)
    }
}
