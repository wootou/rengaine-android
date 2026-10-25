package app.rengaine.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import android.graphics.Canvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import app.rengaine.data.remote.dto.PlayHistoryItemDto
import app.rengaine.data.remote.dto.TrackDto
import app.rengaine.playback.QueueEntry
import app.rengaine.ui.components.TrackAction
import app.rengaine.ui.components.TrackRow
import app.rengaine.ui.history.PlayRow
import app.rengaine.ui.login.LoginContent
import app.rengaine.ui.login.LoginUiState
import app.rengaine.ui.main.BottomTabs
import app.rengaine.ui.main.TopBar
import app.rengaine.ui.player.Controls
import app.rengaine.ui.player.QueueRow
import app.rengaine.ui.player.VolumeRow
import app.rengaine.ui.settings.LicencesScreen
import app.rengaine.ui.settings.ToggleRow
import app.rengaine.ui.theme.Rengaine
import app.rengaine.ui.theme.RengaineTheme
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Captures de mise en page (Q1) : chaque élément serré, sur un écran de
 * 360 dp, avec le texte à 100 % (à gauche) et à 200 % (à droite).
 * À la demande seulement :
 * `./gradlew testDebugUnitTest -Pcaptures --tests '*LayoutCaptures*'`,
 * images dans `app/build/captures/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "fr-w760dp-h1800dp-xhdpi", application = android.app.Application::class)
class LayoutCapturesTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun onlyOnDemand() = assumeTrue(System.getProperty("rengaine.captures") == "true")

    private val longTitle = "I Still Miss You (feat. Mzansi Soulful Ones) [Café Del Mar Edit [Mixed]]"
    private val track = TrackDto(id = 1, title = longTitle, artistCredit = "Roque & Café del Mar", durationMs = 278_524)

    @Test fun controls() = capture("01-controles-lecteur", 140.dp) {
        Controls(
            playing = true, repeatMode = 0, hasNext = true, onRepeat = {}, onPrevious = {},
            onPlayPause = {}, onNext = {}, infinite = true, onInfinite = {},
        )
    }

    @Test fun volume() = capture("02-volume", 80.dp) {
        VolumeRow(volume = 14, min = 0, max = 30, muted = false, onVolume = {}, onMute = {})
    }

    @Test fun queueRow() = capture("03-ligne-file", 200.dp) {
        Column {
            QueueRow(QueueEntry(0, 1, longTitle, "Roque", null, 278_524), isCurrent = true, isPlaying = true, dragging = false, onPlay = {}, onRemove = {})
            QueueRow(QueueEntry(1, 2, "Juice", "Lizzo", null, 195_145), isCurrent = false, isPlaying = false, dragging = false, onPlay = {}, onRemove = {})
        }
    }

    @Test fun historyRow() = capture("04-ligne-historique", 200.dp) {
        PlayRow(
            PlayHistoryItemDto(id = 1, playedMs = 18_000, skipped = true, device = "OnePlus 7 Pro", track = track),
            time = "02:51", showDevice = true, isCurrent = false, onPlay = {}, onDelete = {},
        )
    }

    @Test fun trackRow() = capture("05-ligne-piste", 200.dp) {
        TrackRow(
            track = track, isCurrent = false, isPlaying = false, subtitle = "Roque & Café del Mar · 4:38",
            onPlay = {}, actions = listOf(TrackAction("Lire ensuite", Icons.AutoMirrored.Filled.PlaylistPlay) {}, TrackAction("Plus", Icons.Filled.MoreVert) {}),
            showCover = true,
        )
    }

    @Test fun topBar() = capture("06-barre-haut", 120.dp) {
        TopBar(query = "", onQueryChange = {}, onSubmit = {}, onFocusChange = {}, onOpenSettings = {})
    }

    @Test fun bottomTabs() = capture("07-onglets", 140.dp) {
        BottomTabs(rememberNavController())
    }

    @Test fun settingsToggle() = capture("08-reglage", 200.dp) {
        ToggleRow("Curseur de volume dans le lecteur sur cet appareil", checked = true, enabled = true, onChange = {})
    }

    @Test fun login() = capture("09-connexion", 900.dp) {
        LoginContent(
            state = LoginUiState(deviceName = "OnePlus 7 Pro"),
            onBack = null, onOtherAccount = {}, onAddressChange = {}, onAddressDone = {}, onEmailChange = {},
            onPasswordChange = {}, onSubmit = {}, onForgot = {}, onScan = {}, onRename = {},
        )
    }

    @Test fun loginCleartext() = capture("10-connexion-http", 900.dp) {
        LoginContent(
            state = LoginUiState(address = "http://musique.exemple.fr", deviceName = "OnePlus 7 Pro"),
            onBack = null, onOtherAccount = {}, onAddressChange = {}, onAddressDone = {}, onEmailChange = {},
            onPasswordChange = {}, onSubmit = {}, onForgot = {}, onScan = {}, onRename = {},
        )
    }

    @Test fun licences() = capture("11-licences", 900.dp) {
        LicencesScreen(onBack = {}, contentPadding = PaddingValues())
    }

    /** Même contenu à 100 % et à 200 %, côte à côte, sur 360 dp chacun. */
    private fun capture(name: String, height: Dp, content: @Composable () -> Unit) {
        compose.setContent {
            RengaineTheme(darkTheme = true) {
                Row(Modifier.background(Rengaine.colors.line), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (scale in listOf(1f, 2f)) {
                        val density = LocalDensity.current
                        CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                            Box(Modifier.width(360.dp).height(height).background(Rengaine.colors.bg)) { content() }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        // Dessin direct de la vue : la capture de Compose attend un
        // rafraîchissement d'écran que Robolectric ne produit pas.
        val view = compose.activity.window.decorView.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val dir = File(System.getProperty("rengaine.capturesDir") ?: "build/captures").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
