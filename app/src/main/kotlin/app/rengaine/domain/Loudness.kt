package app.rengaine.domain

import kotlin.math.min
import kotlin.math.roundToInt

/** Mesure EBU R128 d'une piste ou de son album ; `null` tant que l'analyse n'a pas tourné. */
data class LoudnessMeasure(val lufs: Double?, val truePeakDbtp: Double?)

/** Ce qu'il faut savoir d'une piste de la file pour choisir son gain. */
data class GainTrack(
    val albumId: Long?,
    val disc: Int?,
    val trackNumber: Int?,
    val track: LoudnessMeasure,
    val album: LoudnessMeasure,
)

/** Réglages `playback` du compte utiles à la normalisation. */
data class GainSettings(val normalization: String, val targetLufs: Double, val preventClipping: Boolean)

/**
 * Normalisation du volume (F8), copie de `playback.js` du site (réponse de
 * l'équipe du serveur du 2026-10-25) : `gain = targetLufs − lufs`, plafonné à
 * `−truePeak` (sans marge) si « Éviter la saturation », arrondi au
 * centième de dB. L'amplification est permise. Mesure absente : 0 dB.
 */
object Loudness {

    fun gainDb(settings: GainSettings, track: GainTrack, prev: GainTrack?, next: GainTrack?): Double {
        if (settings.normalization == "off") return 0.0
        val m = if (useAlbumGain(settings.normalization, track, prev, next) && track.album.lufs != null) track.album else track.track
        val lufs = m.lufs ?: return 0.0
        var gain = settings.targetLufs - lufs
        if (settings.preventClipping && m.truePeakDbtp != null) gain = min(gain, -m.truePeakDbtp)
        return (gain * 100).roundToInt() / 100.0
    }

    /**
     * `album` : toujours ; `auto` : si la piste suit ou précède dans l'ordre
     * une piste du même album **dans la file** (le contexte et le mélange ne
     * comptent pas) ; sinon gain de piste.
     */
    fun useAlbumGain(mode: String, track: GainTrack, prev: GainTrack?, next: GainTrack?): Boolean = when (mode) {
        "album" -> true
        "auto" -> consecutiveInAlbum(prev, track) || consecutiveInAlbum(track, next)
        else -> false
    }

    /** [b] vient juste après [a] sur le même album ; numéros inconnus : même album suffit. */
    fun consecutiveInAlbum(a: GainTrack?, b: GainTrack?): Boolean {
        if (a == null || b == null || a.albumId == null || a.albumId != b.albumId) return false
        val an = a.trackNumber ?: return true
        val bn = b.trackNumber ?: return true
        val ad = a.disc ?: 1
        val bd = b.disc ?: 1
        return (bd == ad && bn == an + 1) || (bd == ad + 1 && bn <= 1)
    }
}
