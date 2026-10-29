package app.rengaine.playback

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import app.rengaine.domain.BeatSync
import app.rengaine.domain.CrossfadeSettings
import app.rengaine.domain.Transition
import app.rengaine.domain.TransitionPlan
import kotlin.math.max

/**
 * Fondu enchaîné (F8) avec deux lecteurs, comme les deux « platines » du
 * site (`player.js`, `controller.js`).
 *
 * - [Deck.player] de la platine **active** porte la file et c'est lui que
 *   voit la session Media3 (notification, interface, file synchronisée).
 * - [Transition.PRELOAD_MS] avant le début du fondu, l'autre platine reçoit
 *   la même file, calée sur la piste suivante à l'endroit où son son
 *   commence (ou sur un temps, si le fondu est calé sur le tempo), et se
 *   prépare en silence.
 * - Au début du fondu, elle démarre et devient l'active ([onSwap]) : la
 *   sortante continue seule jusqu'au bout du fondu, puis s'arrête. Aucune
 *   des deux pistes n'est interrompue ni relancée.
 * - Volume : rampe **linéaire sur le gain**, comme le site ; chaque platine
 *   garde son gain de normalisation ([VolumeNormalizer.fade]).
 * - Tempo (`beatSync`) : la suivante part à la vitesse qui aligne son tempo
 *   sur la sortante (hauteur conservée), corrigée toutes les 100 ms pour
 *   garder les temps ensemble, puis revient à 1 en 12 s.
 *
 * Pas de fondu (comme le site) : fondu à 0, fin de file, répétition de la
 * piste, deux pistes consécutives d'un même album (sauf réglage), fondu
 * ramené sous 500 ms, suivante pas prête à temps. Tout geste (pause, saut,
 * suivant, piste choisie) coupe net un fondu en cours.
 */
@OptIn(UnstableApi::class)
class Crossfader(
    private val decks: List<Deck>,
    /** La platine [index] devient l'active : la session passe sur elle. */
    private val onSwap: (from: Int, to: Int) -> Unit,
    private val diag: AudioDiagnostics,
) {
    /** Un lecteur, son volume égalisé et la vue qu'en a la session. */
    class Deck(val player: ExoPlayer, val normalizer: VolumeNormalizer, val session: ConversionSeekPlayer)

    var settings = CrossfadeSettings()

    /** Indice de la platine active. */
    var active = 0
        private set

    private val handler = Handler(Looper.getMainLooper())
    private val current get() = decks[active]
    private val spare get() = decks[1 - active]

    /** Ce que la platine libre a préparé : la suivante, à tel endroit, pour telle file. */
    private data class Prepared(val fromId: String, val nextIndex: Int, val nextId: String, val startMs: Long)
    private var prepared: Prepared? = null
    private var plan: TransitionPlan? = null
    private var startScheduled = false

    /** Fondu en cours : la platine sortante, le début et la durée. */
    private var fading: Deck? = null
    private var fadeStartedAt = 0L
    private var fadeMs = 0L
    private var sync: BeatSync? = null
    private var glideFrom = 1.0
    private var glideStartedAt = 0L

    private val listeners = decks.map { deck -> DeckListener(deck) }

    init {
        decks.forEachIndexed { i, deck -> deck.player.addListener(listeners[i]) }
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        decks.forEachIndexed { i, deck -> deck.player.removeListener(listeners[i]) }
    }

    /** Une platine a changé d'état : on ne s'occupe que de l'active. */
    private inner class DeckListener(private val deck: Deck) : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (deck !== current) return
            // Geste de l'utilisateur sur l'active : fondu et calage abandonnés.
            val gesture = events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED) && !player.playWhenReady ||
                events.contains(Player.EVENT_POSITION_DISCONTINUITY) && lastDiscontinuityWasSeek ||
                events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) && lastTransitionWasManual
            lastDiscontinuityWasSeek = false
            lastTransitionWasManual = false
            if (gesture) cancel()
            if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_REPEAT_MODE_CHANGED)) {
                plan = null
            }
            // Seulement sur un changement d'état : le volume et la vitesse,
            // que le fondu change lui-même, relanceraient la boucle sans fin.
            if (events.containsAny(*WAKE_EVENTS)) scheduleTick(0)
        }

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            if (deck === current && reason == Player.DISCONTINUITY_REASON_SEEK) lastDiscontinuityWasSeek = true
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (deck === current && (reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK || reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)) {
                lastTransitionWasManual = true
            }
        }
    }
    private var lastDiscontinuityWasSeek = false
    private var lastTransitionWasManual = false

    private fun scheduleTick(delayMs: Long) {
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, delayMs)
    }

    /** Toutes les 100 ms pendant la lecture, ou pendant un fondu ou un retour de vitesse. */
    private val tick = object : Runnable {
        override fun run() {
            stepFade()
            stepGlide()
            if (fading == null && !startScheduled) watchEnd()
            val busy = current.player.isPlaying || fading != null || glideStartedAt > 0
            if (busy) handler.postDelayed(this, TICK_MS)
        }
    }

    // --- Avant le fondu ---------------------------------------------------------------

    private fun watchEnd() {
        val player = current.player
        if (!player.isPlaying) return
        val plan = planFor(player) ?: return dropSpare()
        if (plan.crossfadeMs == 0L) return dropSpare()
        val pos = current.session.currentPosition
        if (pos >= plan.fadeStartMs - Transition.PRELOAD_MS) prepareSpare(player, plan)
        if (pos >= plan.fadeStartMs - SCHEDULE_AHEAD_MS) {
            // Départ précis, entre deux relevés de position.
            val wait = ((plan.fadeStartMs - pos) / player.playbackParameters.speed).toLong().coerceAtLeast(0)
            startScheduled = true
            handler.postDelayed({ startScheduled = false; start(plan) }, wait)
        }
    }

    private fun planFor(player: ExoPlayer): TransitionPlan? {
        plan?.let { return it }
        val next = player.nextMediaItemIndex.takeIf { it != C.INDEX_UNSET } ?: return null
        val from = MediaItems.transitionTrack(player.currentMediaItem) ?: return null
        val to = MediaItems.transitionTrack(player.getMediaItemAt(next)) ?: return null
        // Répéter la piste : la « suivante » est la même, on la recharge à 0 (site).
        if (from.id == to.id) return null
        return Transition.beatTransition(from, to, settings).also {
            plan = it
            Log.d(TAG, "Plan ${from.id} → ${to.id} : fondu ${it.crossfadeMs} ms à ${it.fadeStartMs} ms, suivante à ${it.nextStartMs} ms ; grilles ${from.outro} / ${to.intro}")
        }
    }

    /** Charge la suite de la file dans la platine libre, calée sur la suivante. */
    private fun prepareSpare(player: ExoPlayer, plan: TransitionPlan) {
        val nextIndex = player.nextMediaItemIndex
        val want = Prepared(
            fromId = player.currentMediaItem?.mediaId ?: return,
            nextIndex = nextIndex,
            nextId = player.getMediaItemAt(nextIndex).mediaId,
            startMs = plan.nextStartMs,
        )
        if (want == prepared) return
        prepared = want
        val items = (0 until player.mediaItemCount).map { plainItem(player.getMediaItemAt(it)) }
        spare.normalizer.fade = 0f
        spare.player.repeatMode = player.repeatMode
        spare.player.shuffleModeEnabled = player.shuffleModeEnabled
        spare.player.playbackParameters = PlaybackParameters.DEFAULT
        spare.player.playWhenReady = false
        // Par la vue de la session : une piste convertie est demandée
        // directement à partir de `startMs` (F7).
        spare.session.setMediaItems(items, nextIndex, plan.nextStartMs)
        spare.player.prepare()
        Log.d(TAG, "Suivante préparée : ${want.nextId} à ${plan.nextStartMs} ms")
    }

    /** Piste sans point de reprise (`track:12@45000` → `track:12`) : la platine libre repart de sa position. */
    private fun plainItem(item: MediaItem): MediaItem {
        if (MediaItems.startMsOf(item) == 0L) return item
        val id = MediaItems.trackIdOf(item.mediaId) ?: return item
        return item.buildUpon().setCustomCacheKey(MediaItems.itemKey(id)).build()
    }

    private fun dropSpare() {
        if (prepared == null) return
        prepared = null
        spare.player.stop()
        spare.player.clearMediaItems()
    }

    // --- Le fondu --------------------------------------------------------------------

    private fun start(plan: TransitionPlan) {
        val out = current
        val player = out.player
        val ready = prepared
        // Rien n'a changé depuis : même piste, même suivante, lecture en cours, suivante prête.
        val nextIndex = player.nextMediaItemIndex
        if (!player.isPlaying || ready == null || nextIndex == C.INDEX_UNSET ||
            ready.fromId != player.currentMediaItem?.mediaId ||
            ready.nextId != player.getMediaItemAt(nextIndex).mediaId ||
            spare.player.playbackState != Player.STATE_READY
        ) {
            diag.log("fondu abandonné : suivante pas prête")
            Log.d(TAG, "Fondu abandonné : suivante pas prête")
            return
        }
        val incoming = spare
        syncQueue(player, incoming.player, nextIndex)
        sync = plan.sync
        incoming.player.playbackParameters = PlaybackParameters(sync?.rate?.toFloat() ?: 1f)
        incoming.normalizer.fade = 0f
        fading = out
        fadeStartedAt = SystemClock.elapsedRealtime()
        fadeMs = plan.crossfadeMs
        prepared = null
        this.plan = null
        val from = active
        active = 1 - active
        onSwap(from, active)
        incoming.player.play()
        Log.d(TAG, "Fondu de ${plan.crossfadeMs} ms vers ${incoming.player.currentMediaItem?.mediaId} (tempo : ${sync?.rate})")
        diag.log("fondu de ${plan.crossfadeMs} ms${sync?.let { " calé sur le tempo (vitesse ${"%.4f".format(java.util.Locale.ROOT, it.rate)})" } ?: ""}")
        scheduleTick(0)
    }

    /**
     * La file a pu changer depuis la préparation (pistes ajoutées par le
     * mode infini…) : on reporte les autres pistes sans toucher à celle qui
     * joue.
     */
    private fun syncQueue(from: ExoPlayer, to: ExoPlayer, nextIndex: Int) {
        val wanted = (0 until from.mediaItemCount).map { plainItem(from.getMediaItemAt(it)) }
        val have = (0 until to.mediaItemCount).map { to.getMediaItemAt(it).mediaId }
        if (wanted.map { it.mediaId } == have && to.currentMediaItemIndex == nextIndex) return
        val cur = to.currentMediaItemIndex
        if (cur + 1 < to.mediaItemCount) to.removeMediaItems(cur + 1, to.mediaItemCount)
        if (cur > 0) to.removeMediaItems(0, cur)
        if (nextIndex > 0) to.addMediaItems(0, wanted.subList(0, nextIndex))
        if (nextIndex + 1 < wanted.size) to.addMediaItems(wanted.subList(nextIndex + 1, wanted.size))
        to.repeatMode = from.repeatMode
    }

    private fun stepFade() {
        val out = fading ?: return
        val incoming = current
        val elapsed = SystemClock.elapsedRealtime() - fadeStartedAt
        val t = (elapsed.toFloat() / max(1L, fadeMs)).coerceIn(0f, 1f)
        out.normalizer.fade = 1f - t
        incoming.normalizer.fade = t
        sync?.let { s ->
            val error = Transition.phaseError(
                out.session.currentPosition.toDouble(),
                incoming.session.currentPosition.toDouble(),
                s,
            )
            setSpeed(incoming.player, Transition.syncedRate(s, error))
        }
        if (t >= 1f) finishFade(out)
    }

    private fun finishFade(out: Deck) {
        fading = null
        out.player.pause()
        out.player.stop()
        out.player.clearMediaItems()
        out.normalizer.fade = 1f
        current.normalizer.fade = 1f
        sync?.let {
            glideFrom = current.player.playbackParameters.speed.toDouble()
            glideStartedAt = SystemClock.elapsedRealtime()
        }
        sync = null
        Log.d(TAG, "Fondu terminé")
        diag.log("fondu terminé")
    }

    /** Après un fondu calé sur le tempo : retour à la vitesse normale en 12 s. */
    private fun stepGlide() {
        if (glideStartedAt == 0L || fading != null) return
        val rate = Transition.glideRate(glideFrom, SystemClock.elapsedRealtime() - glideStartedAt)
        setSpeed(current.player, rate)
        if (rate == 1.0) glideStartedAt = 0
    }

    /** Vitesse changée seulement si elle bouge vraiment : chaque changement coûte au lecteur. */
    private fun setSpeed(player: ExoPlayer, rate: Double) {
        if (kotlin.math.abs(player.playbackParameters.speed - rate) < 0.0005 && rate != 1.0) return
        if (rate == 1.0 && player.playbackParameters.speed == 1f) return
        player.playbackParameters = PlaybackParameters(rate.toFloat())
    }

    /** Coupure nette : la sortante s'arrête, la vitesse revient à 1. */
    fun cancel() {
        handler.removeCallbacksAndMessages(null)
        startScheduled = false
        fading?.let { out ->
            out.player.stop()
            out.player.clearMediaItems()
            out.normalizer.fade = 1f
            Log.d(TAG, "Fondu interrompu")
            diag.log("fondu interrompu")
        }
        fading = null
        sync = null
        glideStartedAt = 0
        current.normalizer.fade = 1f
        if (current.player.playbackParameters.speed != 1f) current.player.playbackParameters = PlaybackParameters.DEFAULT
        plan = null
        dropSpare()
    }

    private companion object {
        const val TAG = "Rengaine/Fondu"
        const val TICK_MS = 100L
        /** Comme le site : à moins de 600 ms du début, on programme le départ exact. */
        const val SCHEDULE_AHEAD_MS = 600L
        val WAKE_EVENTS = intArrayOf(
            Player.EVENT_IS_PLAYING_CHANGED,
            Player.EVENT_PLAY_WHEN_READY_CHANGED,
            Player.EVENT_PLAYBACK_STATE_CHANGED,
            Player.EVENT_POSITION_DISCONTINUITY,
            Player.EVENT_MEDIA_ITEM_TRANSITION,
            Player.EVENT_TIMELINE_CHANGED,
            Player.EVENT_REPEAT_MODE_CHANGED,
        )
    }
}
