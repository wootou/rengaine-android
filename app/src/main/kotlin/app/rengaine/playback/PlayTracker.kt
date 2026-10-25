package app.rengaine.playback

import app.rengaine.data.remote.dto.PlayInputDto
import java.time.Instant

/**
 * Compte une écoute (F11), comme `controller.js` du site (précisé par
 * l'équipe du serveur le 2026-10-24) : le temps **réellement joué** d'une piste,
 * pauses exclues ; un passage rejoué après un retour en arrière compte à
 * nouveau. Retenue à partir de [MIN_PLAYED_MS]. `completed` seulement au
 * passage automatique à la suivante (ou au bout de la file, ou à chaque
 * tour en répétition d'une piste) ; sinon `skipped` (`skipped = !completed`),
 * y compris quand une autre liste remplace la file. `context` : toujours
 * `queue`, comme le site.
 *
 * Indépendant de Media3, pour être testé avec une horloge simulée : le
 * service de lecture lui passe les événements du lecteur.
 *
 * @param elapsed horloge monotone en ms (`SystemClock.elapsedRealtime`)
 * @param now date réelle, pour `startedAt`
 * @param accountId compte actif au début de l'écoute
 * @param onPlay écoute retenue, à envoyer
 */
class PlayTracker(
    private val elapsed: () -> Long,
    private val now: () -> Instant,
    private val accountId: () -> Long?,
    private val onPlay: (accountId: Long, play: PlayInputDto) -> Unit,
) {
    private class Current(val trackId: Long, val accountId: Long) {
        var startedAt: Instant? = null
        var playedMs = 0L
        var resumedAt: Long? = null
    }

    private var current: Current? = null

    /** Piste dont l'écoute est en cours de comptage. */
    val currentTrackId: Long? get() = current?.trackId

    /** La lecture démarre ou reprend sur [trackId]. */
    fun playing(trackId: Long?) {
        if (trackId == null) return
        val c = current?.takeIf { it.trackId == trackId } ?: run {
            finish(completed = false)
            val account = accountId() ?: return
            // `startedAt` : quand la piste est lancée (site), pas au premier son.
            Current(trackId, account).also { it.startedAt = now(); current = it }
        }
        if (c.resumedAt == null) c.resumedAt = elapsed()
    }

    /** Pause, mise en mémoire tampon, perte du focus audio : le temps s'arrête. */
    fun paused() {
        val c = current ?: return
        c.resumedAt?.let { c.playedMs += (elapsed() - it).coerceAtLeast(0) }
        c.resumedAt = null
    }

    /**
     * L'écoute en cours telle qu'elle serait envoyée si elle s'arrêtait
     * maintenant (`completed = false`) ; `null` sous [MIN_PLAYED_MS]. Gardée
     * régulièrement sur le téléphone : si Android tue l'app, elle n'est pas
     * perdue (`HistoryRepository.saveInProgress`).
     */
    fun snapshot(): Pair<Long, PlayInputDto>? {
        val c = current ?: return null
        val startedAt = c.startedAt ?: return null
        val played = c.playedMs + (c.resumedAt?.let { (elapsed() - it).coerceAtLeast(0) } ?: 0)
        if (played < MIN_PLAYED_MS) return null
        return c.accountId to play(c.trackId, startedAt, played, completed = false)
    }

    /** L'écoute en cours se termine ; [completed] : passage automatique, arrivée au bout. */
    fun finish(completed: Boolean) {
        paused()
        val c = current ?: return
        current = null
        val startedAt = c.startedAt ?: return
        if (c.playedMs < MIN_PLAYED_MS) return
        onPlay(c.accountId, play(c.trackId, startedAt, c.playedMs, completed))
    }

    private fun play(trackId: Long, startedAt: Instant, playedMs: Long, completed: Boolean) = PlayInputDto(
        trackId = trackId,
        startedAt = startedAt.toString(),
        playedMs = playedMs,
        completed = completed,
        skipped = !completed,
        context = CONTEXT,
    )

    companion object {
        /** Seuil du site : en dessous, ce n'est pas une écoute. */
        const val MIN_PLAYED_MS = 5_000L
        const val CONTEXT = "queue"
    }
}
