package app.rengaine.domain

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * Fondu enchaîné (F8), repris à l'identique de `engine/playback.js` du site
 * (fonctions `consecutiveInAlbum`, `transition`, `tempoMatch`,
 * `beatTransition`, `beatPhase`, `phaseError`, `syncedRate`, `glideRate`,
 * transmises par dev-musique le 2026-10-29). Fonctions pures : le moteur
 * ([app.rengaine.playback.Crossfader]) les applique.
 */

/** Grille régulière : un temps tombe à `offsetMs + n × 60000 / bpm`. */
data class BeatGrid(val bpm: Double, val offsetMs: Double)

/** Ce que le fondu lit d'une piste de la file. */
data class TransitionTrack(
    val id: Long,
    val albumId: Long?,
    val discNumber: Int?,
    val trackNumber: Int?,
    val durationMs: Long,
    val audioStartMs: Long? = null,
    val audioEndMs: Long? = null,
    val intro: BeatGrid? = null,
    val outro: BeatGrid? = null,
)

/** Réglages `playback` du compte qui décident du fondu. */
data class CrossfadeSettings(
    val crossfadeMs: Long = 0,
    val withinAlbum: Boolean = false,
    val beatSync: Boolean = true,
)

/** Calage sur le tempo : vitesse de la suivante et grilles des deux pistes. */
data class BeatSync(val rate: Double, val beatMs: Double, val current: BeatGrid, val next: BeatGrid)

/**
 * Le passage d'une piste à la suivante : fondu de [crossfadeMs] (0 = aucun)
 * qui commence à [fadeStartMs] de la piste en cours, la suivante partant
 * de [nextStartMs].
 */
data class TransitionPlan(
    val crossfadeMs: Long,
    val fadeStartMs: Long,
    val nextStartMs: Long,
    val sync: BeatSync? = null,
)

object Transition {

    const val BEAT_SYNC_MAX_DELTA = 0.06
    const val PHASE_MAX_CORRECTION = 0.03
    const val GLIDE_MS = 12_000L

    /** Préchargement de la suivante, avant le début du fondu (site : 15 s). */
    const val PRELOAD_MS = 15_000L

    /** Les deux pistes se suivent dans le même album. Sans numéro de piste, on le suppose. */
    fun consecutiveInAlbum(a: TransitionTrack?, b: TransitionTrack?): Boolean {
        if (a == null || b == null || a.albumId == null || a.albumId != b.albumId) return false
        if (a.trackNumber == null || b.trackNumber == null) return true
        return (b.discNumber == a.discNumber && b.trackNumber == a.trackNumber + 1) ||
            (a.discNumber != null && b.discNumber == a.discNumber + 1 && b.trackNumber <= 1)
    }

    fun transition(current: TransitionTrack, next: TransitionTrack?, s: CrossfadeSettings): TransitionPlan {
        val end = current.audioEndMs ?: current.durationMs
        val none = TransitionPlan(crossfadeMs = 0, fadeStartMs = current.durationMs, nextStartMs = 0)
        if (next == null || s.crossfadeMs <= 0) return none
        // Albums live, albums sans blanc : on ne fond pas deux pistes qui se suivent, sauf demande.
        if (!s.withinAlbum && consecutiveInAlbum(current, next)) return none
        // Jamais plus que le tiers de l'une ou l'autre piste.
        val shortest = min(end, next.durationMs)
        val fade = max(0L, min(s.crossfadeMs, floor(shortest / 3.0).toLong()))
        if (fade < 500) return none
        return TransitionPlan(
            crossfadeMs = fade,
            fadeStartMs = max(0L, end - fade), // le fondu finit quand le son s'arrête
            nextStartMs = next.audioStartMs ?: 0, // la suivante démarre où son son commence
        )
    }

    data class TempoMatch(val rate: Double, val factor: Double)

    fun tempoMatch(currentBpm: Double, nextBpm: Double): TempoMatch? {
        if (!(currentBpm > 0) || !(nextBpm > 0)) return null
        var best: TempoMatch? = null
        for (factor in doubleArrayOf(1.0, 2.0, 0.5)) {
            val rate = currentBpm / (nextBpm * factor)
            if (abs(rate - 1) <= BEAT_SYNC_MAX_DELTA && (best == null || abs(rate - 1) < abs(best.rate - 1))) {
                best = TempoMatch(rate, factor)
            }
        }
        return best
    }

    fun beatTransition(current: TransitionTrack, next: TransitionTrack?, s: CrossfadeSettings): TransitionPlan {
        val base = transition(current, next, s)
        if (!s.beatSync || base.crossfadeMs == 0L || next == null) return base
        val out = current.outro ?: return base
        val intro = next.intro ?: return base
        val match = tempoMatch(out.bpm, intro.bpm) ?: return base
        val beatMs = 60_000.0 / out.bpm
        val end = (current.audioEndMs ?: current.durationMs).toDouble()
        val limit = min(end, next.durationMs.toDouble()) / 3
        var beats = max(4L, jsRound(base.crossfadeMs / beatMs / 4) * 4)
        while (beats > 4 && beats * beatMs > limit) beats -= 4
        val fadeMs = beats * beatMs
        if (fadeMs > limit) return base
        val n = floor((end - fadeMs - out.offsetMs) / beatMs)
        if (n < 0) return base
        val fadeStartMs = out.offsetMs + n * beatMs
        val nextBeat = 60_000.0 / intro.bpm
        val startMs = (next.audioStartMs ?: 0).toDouble()
        val k = max(0.0, ceil((startMs - intro.offsetMs) / nextBeat - 1e-6))
        return TransitionPlan(
            crossfadeMs = jsRound(fadeMs),
            fadeStartMs = jsRound(fadeStartMs),
            nextStartMs = jsRound(intro.offsetMs + k * nextBeat),
            sync = BeatSync(
                rate = match.rate,
                beatMs = beatMs,
                current = out,
                next = BeatGrid(intro.bpm * match.factor, intro.offsetMs),
            ),
        )
    }

    /** Position dans le temps, entre 0 et 1. */
    fun beatPhase(posMs: Double, grid: BeatGrid): Double {
        val p = (posMs - grid.offsetMs) * grid.bpm / 60_000
        return p - floor(p)
    }

    /** Décalage entre les temps des deux pistes, entre −0,5 et 0,5 temps. */
    fun phaseError(currentPosMs: Double, nextPosMs: Double, sync: BeatSync): Double {
        val e = beatPhase(nextPosMs, sync.next) - beatPhase(currentPosMs, sync.current)
        return e - jsRound(e)
    }

    fun syncedRate(sync: BeatSync, error: Double): Double {
        val correction = max(-PHASE_MAX_CORRECTION, min(PHASE_MAX_CORRECTION, error * sync.beatMs / 2000))
        return sync.rate * (1 - correction)
    }

    /** Retour linéaire à la vitesse normale en [GLIDE_MS] après le fondu. */
    fun glideRate(from: Double, elapsedMs: Long): Double {
        val t = min(1.0, max(0.0, elapsedMs.toDouble() / GLIDE_MS))
        return if (t >= 1) 1.0 else from + (1 - from) * t
    }

    /** `Math.round` de JavaScript : les demis vont vers +∞ (−2,5 → −2). */
    private fun jsRound(x: Double): Long = floor(x + 0.5).roundToLong()
}
