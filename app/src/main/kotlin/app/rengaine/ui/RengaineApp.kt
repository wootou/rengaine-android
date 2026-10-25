package app.rengaine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.rengaine.ui.accounts.AccountsScreen
import app.rengaine.ui.main.MainShell
import app.rengaine.ui.player.PlayerScreen
import app.rengaine.ui.player.QueueScreen
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import app.rengaine.ui.login.LoginScreen
import app.rengaine.ui.theme.Rengaine
import kotlinx.serialization.Serializable

/** L'app connectée : en-tête, onglets, mini-lecteur (`MainShell`). */
@Serializable
data object MainRoute

@Serializable
data object AccountsRoute

/** Lecteur plein écran (capture `12`). */
@Serializable
data object PlayerRoute

/** File d'attente (capture `13`). */
@Serializable
data object QueueRoute

/** @property reauth reconnexion du compte actif (jeton refusé), sinon ajout d'un compte. */
@Serializable
data class LoginRoute(val reauth: Boolean = false)

/**
 * Racine de l'interface : sans compte, l'écran de connexion (premier
 * écran, décision du mainteneur) ; avec un compte actif, l'app.
 */
@Composable
fun RengaineApp(viewModel: RootViewModel = hiltViewModel()) {
    val root by viewModel.state.collectAsStateWithLifecycle()
    when (val state = root) {
        RootState.Loading -> Box(Modifier.fillMaxSize().background(Rengaine.colors.bg))
        RootState.SignedOut -> LoginScreen(onBack = null, onOtherAccount = {})
        is RootState.SignedIn -> SignedInApp(state, viewModel)
    }
}

@Composable
private fun SignedInApp(state: RootState.SignedIn, viewModel: RootViewModel) {
    val nav = rememberNavController()
    val pendingPairing by viewModel.pendingPairing.collectAsStateWithLifecycle()

    // Nouveau compte actif (connexion, bascule) ou jeton refusé : on repart
    // de l'accueil, ou de la reconnexion. Pas au premier affichage : la
    // destination de départ y pourvoit déjà.
    var shown by remember { mutableStateOf(state) }
    LaunchedEffect(state) {
        if (state == shown) return@LaunchedEffect
        shown = state
        nav.resetTo(if (state.needsReauth) LoginRoute(reauth = true) else MainRoute)
    }
    // Lien d'appairage reçu alors qu'un compte est déjà connecté : ajout.
    LaunchedEffect(pendingPairing) {
        if (pendingPairing != null) nav.navigate(LoginRoute(reauth = false)) { launchSingleTop = true }
    }

    NavHost(
        navController = nav,
        startDestination = if (state.needsReauth) LoginRoute(reauth = true) else MainRoute,
        modifier = Modifier.fillMaxSize().background(Rengaine.colors.bg),
    ) {
        composable<MainRoute> {
            MainShell(
                onOpenAccounts = { nav.navigate(AccountsRoute) },
                onOpenPlayer = { nav.navigate(PlayerRoute) { launchSingleTop = true } },
                onOpenQueue = { nav.navigate(QueueRoute) { launchSingleTop = true } },
            )
        }
        composable<PlayerRoute>(
            enterTransition = { slideInVertically { it } },
            exitTransition = { fadeOut() },
            popExitTransition = { slideOutVertically { it } },
        ) {
            PlayerScreen(
                onClose = { nav.popBackStack() },
                onOpenQueue = { nav.navigate(QueueRoute) { launchSingleTop = true } },
            )
        }
        composable<QueueRoute>(
            enterTransition = { slideInVertically { it } },
            popExitTransition = { slideOutVertically { it } },
        ) {
            QueueScreen(onClose = { nav.popBackStack() })
        }
        composable<AccountsRoute> {
            AccountsScreen(
                onBack = { nav.popBackStack() },
                onAddAccount = { nav.navigate(LoginRoute(reauth = false)) },
            )
        }
        composable<LoginRoute> { entry ->
            val reauth = entry.arguments?.getBoolean("reauth") ?: false
            LoginScreen(
                onBack = if (reauth) null else ({ nav.popBackStack() }),
                onOtherAccount = { nav.navigate(AccountsRoute) },
            )
        }
    }
}

private fun NavHostController.resetTo(route: Any) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
