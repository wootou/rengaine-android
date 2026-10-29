package app.rengaine.playback

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import app.rengaine.BuildConfig
import app.rengaine.data.local.SessionStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Journal de diagnostic du son (Réglages › Enchaînement), comme `diag.js`
 * du site : il note les attentes du flux, les retards et les passages de
 * l'app au premier plan, pour comprendre des coupures. Rien ne part au
 * serveur : le journal reste en mémoire sur le téléphone, 1 500 lignes au
 * plus, et l'utilisateur le copie s'il veut le transmettre.
 *
 * Lignes au format du site :
 * `14:03:12    35.20  platine 1* piste 812 waiting   pos 12.34 s, 0.0 s d'avance`.
 * Chaque lecteur est une « platine » ; `*` marque celle qu'on entend en
 * premier (l'autre ne sert qu'au fondu enchaîné).
 */
@Singleton
class AudioDiagnostics @Inject constructor(
    @ApplicationContext private val context: Context,
    store: SessionStore,
) {
    private val lines = ArrayDeque<String>()
    private val _count = MutableStateFlow(0)

    /** Nombre de lignes du journal. */
    val count: StateFlow<Int> = _count.asStateFlow()

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val t0 = SystemClock.elapsedRealtime()
    private val handler = Handler(Looper.getMainLooper())
    private val clock = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val decks = mutableListOf<Deck>()

    private fun setEnabled(on: Boolean) {
        if (on == _enabled.value) return
        if (on) {
            _enabled.value = true
            log("journal démarré ; Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}, Rengaine ${BuildConfig.VERSION_NAME}")
            lastWatchdog = SystemClock.elapsedRealtime()
            handler.postDelayed(watchdog, WATCHDOG_MS)
            handler.postDelayed(ticker, TICK_MS)
        } else {
            log("journal arrêté")
            _enabled.value = false
            handler.removeCallbacks(watchdog)
            handler.removeCallbacks(ticker)
        }
    }

    fun log(message: String) {
        if (!_enabled.value) return
        val elapsed = (SystemClock.elapsedRealtime() - t0) / 1000.0
        val line = "${LocalTime.now().format(clock)} ${String.format(Locale.ROOT, "%.2f", elapsed).padStart(8)}  $message"
        synchronized(lines) {
            lines.addLast(line)
            while (lines.size > MAX_LINES) lines.removeFirst()
            _count.value = lines.size
        }
    }

    /** Toutes les lignes, pour « Copier le journal ». */
    fun text(): String = synchronized(lines) { lines.joinToString("\n") }

    fun clear() = synchronized(lines) {
        lines.clear()
        _count.value = 0
    }

    /**
     * Suit un lecteur. [number] est le numéro de la platine ; [active] dit si
     * c'est celle qu'on entend.
     */
    fun watch(player: Player, number: Int, active: () -> Boolean) {
        val deck = Deck(player, number, active)
        decks += deck
        player.addListener(deck)
    }

    private inner class Deck(val player: Player, val number: Int, val active: () -> Boolean) : Player.Listener {
        var lastTick = 0L
        var lastPos = -1L
        var lastPosAt = 0L

        fun label() = "platine $number${if (active()) "*" else " "} piste ${MediaItems.trackIdOf(player.currentMediaItem?.mediaId) ?: "-"}"

        fun event(name: String, extra: String = "") {
            val pos = player.currentPosition.coerceAtLeast(0)
            val ahead = (player.bufferedPosition - pos).coerceAtLeast(0)
            val buffer = if (ahead > 0) String.format(Locale.ROOT, "%.1f s d'avance", ahead / 1000.0) else "rien d’avance"
            log("${label()} ${name.padEnd(9)} pos ${String.format(Locale.ROOT, "%.2f", pos / 1000.0)} s, $buffer$extra")
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = event("loadstart")

        override fun onPlaybackStateChanged(playbackState: Int) = when (playbackState) {
            Player.STATE_BUFFERING -> event("waiting")
            Player.STATE_READY -> event("canplay")
            Player.STATE_ENDED -> event("ended")
            else -> if (player.playerError == null) event("emptied") else Unit
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                lastTick = SystemClock.elapsedRealtime()
                event("playing")
            } else if (!player.playWhenReady) {
                event("pause")
            }
        }

        override fun onIsLoadingChanged(isLoading: Boolean) {
            // Le chargement s'arrête avant la fin du fichier : le lecteur a assez d'avance.
            if (!isLoading && player.playbackState == Player.STATE_READY) event("suspend")
        }

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) event("seeked")
        }

        override fun onPlayerError(error: PlaybackException) = event("error", " (code ${error.errorCode})")

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) =
            log("${label()} vitesse ${String.format(Locale.ROOT, "%.4f", playbackParameters.speed)}")

        /** Comme `timeupdate` du site : position relevée toutes les 250 ms pendant la lecture. */
        fun tick(now: Long) {
            if (!player.isPlaying) {
                lastPos = -1
                return
            }
            if (lastTick > 0 && now - lastTick > LATE_MS) {
                log("${label()} timeupdate en retard : ${now - lastTick} ms (pos ${String.format(Locale.ROOT, "%.2f", player.currentPosition / 1000.0)} s)")
            }
            lastTick = now
            val pos = player.currentPosition
            if (lastPos >= 0 && now - lastPosAt > FROZEN_AFTER_MS && pos - lastPos < FROZEN_MIN_MS) {
                log("${label()} position figée à ${String.format(Locale.ROOT, "%.2f", pos / 1000.0)} s (${now - lastPosAt} ms)")
                lastPos = pos
                lastPosAt = now
            } else if (lastPos < 0 || pos - lastPos >= FROZEN_MIN_MS) {
                lastPos = pos
                lastPosAt = now
            }
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            decks.forEach { it.tick(now) }
            handler.postDelayed(this, TICK_MS)
        }
    }

    /** « Fil principal bloqué » : un minuteur de 100 ms qui part avec plus de 150 ms de retard. */
    private var lastWatchdog = 0L
    private val watchdog = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            val late = now - lastWatchdog - WATCHDOG_MS
            if (late > BLOCKED_MS) log("fil principal bloqué $late ms")
            lastWatchdog = now
            handler.postDelayed(this, WATCHDOG_MS)
        }
    }

    /** « App visible » / « app cachée », comme « onglet visible » / « onglet caché » du site. */
    private val visibility = object : Application.ActivityLifecycleCallbacks {
        private var started = 0
        override fun onActivityStarted(activity: Activity) {
            if (started++ == 0) log("app visible")
        }
        override fun onActivityStopped(activity: Activity) {
            started = (started - 1).coerceAtLeast(0)
            if (started == 0) log("app cachée")
        }
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            store.audioDiagnostics.collect(::setEnabled)
        }
        (context as? Application)?.registerActivityLifecycleCallbacks(visibility)
    }

    private companion object {
        const val MAX_LINES = 1_500
        const val TICK_MS = 250L
        const val LATE_MS = 600L
        const val FROZEN_AFTER_MS = 200L
        const val FROZEN_MIN_MS = 50L
        const val WATCHDOG_MS = 100L
        const val BLOCKED_MS = 150L
    }
}
