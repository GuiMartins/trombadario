package com.trombadario.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ChecklistRtl
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.trombadario.AppContainer
import com.trombadario.R
import com.trombadario.data.remote.UserDto
import com.trombadario.ui.trombadicedetail.TrombadiceDetailScreen
import com.trombadario.ui.trombadiceform.TrombadiceFormScreen
import com.trombadario.ui.assuntos.AssuntosScreen
import com.trombadario.ui.feed.FeedScreen
import com.trombadario.ui.pedidos.PedidosScreen
import com.trombadario.ui.punishment.PunishmentScreen
import com.trombadario.ui.report.ReportScreen
import com.trombadario.ui.tasks.TasksScreen
import com.trombadario.ui.settings.SettingsScreen
import com.trombadario.ui.splash.SplashMessagesScreen
import com.trombadario.ui.users.UsersScreen

object Routes {
    const val FEED = "feed"
    const val TASKS = "tasks"
    const val PUNISHMENT = "punishment"
    const val PEDIDOS = "pedidos"
    const val ASSUNTOS = "assuntos"
    const val USERS = "users"
    const val SPLASH_MESSAGES = "splash_messages"
    const val REPORT = "report"
    const val SETTINGS = "settings"
    const val TROMBADICE_DETAIL = "trombadice/{trombadiceId}"
    const val TROMBADICE_FORM = "trombadice_form?trombadiceId={trombadiceId}"

    fun trombadiceDetail(trombadiceId: Int) = "trombadice/$trombadiceId"
    fun trombadiceForm(trombadiceId: Int? = null) =
        if (trombadiceId == null) "trombadice_form" else "trombadice_form?trombadiceId=$trombadiceId"
}

private data class Tab(val route: String, val icon: ImageVector, val labelRes: Int)

@Composable
fun MainNavHost(container: AppContainer, currentUser: UserDto) {
    val navController = rememberNavController()

    // Cinco abas - o teto do NavigationBar do Material3, decisão consciente do
    // usuário (o padrão anterior era 4, com Contas dentro de Configurações).
    // Pedidos quis ficar sempre visível em vez de escondido, mesmo sabendo que
    // a barra fica mais apertada num celular estreito.
    val tabs = listOf(
        Tab(Routes.FEED, Icons.AutoMirrored.Filled.List, R.string.nav_feed),
        Tab(Routes.TASKS, Icons.Default.ChecklistRtl, R.string.nav_tasks),
        Tab(Routes.PUNISHMENT, Icons.Default.Gavel, R.string.nav_punishment),
        Tab(Routes.PEDIDOS, Icons.Default.QuestionAnswer, R.string.nav_pedidos),
        Tab(Routes.SETTINGS, Icons.Default.Settings, R.string.nav_settings),
    )

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            // Only on the tab destinations; child screens (detail, form) keep the
            // full height and rely on the back arrow.
            if (currentRoute?.route in tabs.map { it.route }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute?.hierarchy?.any { it.route == tab.route } == true,
                            onClick = { navController.switchTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.labelRes)) },
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.FEED,
            modifier = Modifier.padding(innerPadding),
            // Transição padrão oficial do Compose - fade simples de 220ms, a
            // mesma nos quatro sentidos. A virada de página em 3D que vivia aqui
            // saiu: era cara demais de verificar de verdade (a tela bloqueia
            // print, só dava pra olhar via teste instrumentado) e ninguém tinha
            // visto o resultado real antes de ir pro ar.
            enterTransition = { fadeIn(tween(220)) },
            exitTransition = { fadeOut(tween(220)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(220)) },
        ) {
            composable(Routes.FEED) {
                FeedScreen(
                    container = container,
                    currentUser = currentUser,
                    onOpenTrombadice = { navController.navigate(Routes.trombadiceDetail(it)) },
                    onNewTrombadice = { navController.navigate(Routes.trombadiceForm()) },
                )
            }
            composable(Routes.TASKS) {
                TasksScreen(container = container, currentUser = currentUser)
            }
            composable(Routes.PUNISHMENT) {
                PunishmentScreen(container = container, currentUser = currentUser)
            }
            composable(Routes.PEDIDOS) {
                PedidosScreen(container = container, currentUser = currentUser)
            }
            // Fora da nav bar: cinco abas já é o teto do NavigationBar do
            // Material3. É a única tela de aba que os dois papéis alcançam por
            // Configurações, e não por uma aba própria.
            composable(Routes.ASSUNTOS) {
                AssuntosScreen(
                    container = container,
                    currentUser = currentUser,
                    onBack = navController::popBackStack,
                )
            }
            composable(Routes.SPLASH_MESSAGES) {
                SplashMessagesScreen(
                    container = container,
                    currentUser = currentUser,
                    onBack = navController::popBackStack,
                )
            }
            composable(Routes.USERS) {
                UsersScreen(
                    container = container,
                    currentUser = currentUser,
                    onBack = navController::popBackStack,
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    container = container,
                    currentUser = currentUser,
                    onOpenUsers = { navController.navigate(Routes.USERS) },
                    onOpenSplashMessages = { navController.navigate(Routes.SPLASH_MESSAGES) },
                    onOpenReport = { navController.navigate(Routes.REPORT) },
                    onOpenAssuntos = { navController.navigate(Routes.ASSUNTOS) },
                )
            }
            composable(Routes.REPORT) {
                ReportScreen(container = container, currentUser = currentUser)
            }
            composable(Routes.TROMBADICE_DETAIL) { entry ->
                val trombadiceId = entry.arguments?.getString("trombadiceId")?.toIntOrNull()
                TrombadiceDetailScreen(
                    container = container,
                    currentUser = currentUser,
                    trombadiceId = trombadiceId,
                    onBack = navController::popBackStack,
                    onEdit = { navController.navigate(Routes.trombadiceForm(it)) },
                )
            }
            composable(
                route = Routes.TROMBADICE_FORM,
                // Without declaring it optional, navigating to plain "trombadice_form"
                // (the new-event case) would not match this destination at all.
                arguments = listOf(
                    navArgument("trombadiceId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                ),
            ) { entry ->
                val trombadiceId = entry.arguments?.getString("trombadiceId")?.toIntOrNull()
                TrombadiceFormScreen(
                    container = container,
                    currentUser = currentUser,
                    trombadiceId = trombadiceId,
                    onDone = {
                        // Back to the feed, dropping the detail screen of an
                        // event that may no longer look the same.
                        navController.popBackStack(Routes.FEED, inclusive = false)
                    },
                    onBack = navController::popBackStack,
                )
            }
        }
    }
}

/** Tapping a tab always lands on its root, with no saved state restored -
 *  restoring would re-open a child screen the user had left, which is confusing
 *  without a visible breadcrumb. */
private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = false }
        launchSingleTop = true
        restoreState = false
    }
}
