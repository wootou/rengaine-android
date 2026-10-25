package app.rengaine

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.rengaine.data.remote.dto.ThemeChoice
import app.rengaine.data.repository.SettingsRepository
import app.rengaine.domain.PairingUri
import app.rengaine.ui.RengaineApp
import app.rengaine.ui.login.PendingPairing
import app.rengaine.ui.theme.RengaineTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Activité unique ; navigation en Compose (`ui/RengaineApp.kt`). */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var pendingPairing: PendingPairing
    @Inject lateinit var settings: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        // L'écran de démarrage reste le temps de lire la copie locale des
        // réglages : sans cela, un thème clair choisi s'afficherait sombre
        // une fraction de seconde.
        installSplashScreen().setKeepOnScreenCondition { !settings.ready.value }
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            // Réglages › Apparence (`interface.theme`), partagé avec le site ;
            // sans compte, le thème du système.
            val accountSettings by settings.settings.collectAsStateWithLifecycle()
            val dark = when (accountSettings?.theme ?: ThemeChoice.AUTO) {
                ThemeChoice.AUTO -> isSystemInDarkTheme()
                ThemeChoice.LIGHT -> false
                ThemeChoice.DARK -> true
            }
            DisposableEffect(dark) {
                // Bord à bord, icônes des barres système selon le thème (U1).
                val style = if (dark) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            RengaineTheme(darkTheme = dark) {
                RengaineApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** `rengaine://pair?…` ouvert depuis l'appareil photo ou un navigateur. */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val payload = intent.dataString?.let(PairingUri::parse) ?: return
        pendingPairing.offer(payload)
    }
}
