package app.rengaine.playback

import androidx.media3.common.util.UnstableApi
import androidx.annotation.OptIn
import android.media.audiofx.LoudnessEnhancer
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import app.rengaine.domain.GainSettings
import app.rengaine.domain.Loudness
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Volume égalisé (F8) appliqué au lecteur, avec les règles du site
 * ([Loudness]).
 *
 * Le gain de chaque piste est fixé quand elle commence (y compris en
 * enchaînement sans blanc) et recalculé pour la piste en cours seulement
 * quand les réglages changent, avec une rampe de 200 ms, comme le site.
 *
 * Un gain négatif passe par le volume du lecteur (simple multiplication,
 * appliquée par le mélangeur d'Android au moment où le son sort). ExoPlayer
 * ne sait pas amplifier : un gain positif passe par un `LoudnessEnhancer`
 * sur la session audio du lecteur. « Éviter la saturation » le plafonne déjà
 * sous 0 dBTP. Sans cet effet (refusé par le téléphone), on n'amplifie pas.
 */
@OptIn(UnstableApi::class)
class VolumeNormalizer(private val player: ExoPlayer) {

    private val handler = Handler(Looper.getMainLooper())
    private var settings: GainSettings? = null
    private var enhancer: LoudnessEnhancer? = null
    private var enhancerSession = C.AUDIO_SESSION_ID_UNSET

    /** Réglages du compte (`null` tant qu'ils ne sont pas connus : volume d'origine). */
    fun onSettings(value: GainSettings?) {
        if (value == settings) return
        settings = value
        apply(ramp = true)
    }

    /** Nouvelle piste en cours. */
    fun onTrackStarted() = apply(ramp = false)

    fun onAudioSessionId(id: Int) {
        if (id == enhancerSession) return
        releaseEnhancer()
        enhancerSession = id
        apply(ramp = false)
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        releaseEnhancer()
    }

    private fun apply(ramp: Boolean) {
        val gain = currentGainDb()
        val boost = if (gain > 0) enhancer()?.let { gain } ?: 0.0 else 0.0
        enhancer?.runCatching {
            setTargetGain((boost * 100).roundToInt())
            enabled = boost > 0
        }
        val volume = if (gain < 0) 10.0.pow(gain / 20).toFloat() else 1f
        if (ramp) rampTo(volume) else setNow(volume)
        Log.d(TAG, "Gain ${gain} dB (amplification ${boost} dB)")
    }

    private fun currentGainDb(): Double {
        val s = settings ?: return 0.0
        val index = player.currentMediaItemIndex
        if (index !in 0 until player.mediaItemCount) return 0.0
        fun at(i: Int) = if (i in 0 until player.mediaItemCount) MediaItems.gainTrack(player.getMediaItemAt(i)) else null
        val track = at(index) ?: return 0.0
        return Loudness.gainDb(s, track, at(index - 1), at(index + 1))
    }

    private fun enhancer(): LoudnessEnhancer? {
        enhancer?.let { return it }
        val session = player.audioSessionId.takeIf { it != C.AUDIO_SESSION_ID_UNSET } ?: return null
        enhancer = runCatching { LoudnessEnhancer(session) }
            .onFailure { Log.w(TAG, "LoudnessEnhancer refusé : pas d'amplification", it) }
            .getOrNull()
        enhancerSession = session
        return enhancer
    }

    private fun releaseEnhancer() {
        enhancer?.runCatching { release() }
        enhancer = null
    }

    private fun setNow(volume: Float) {
        handler.removeCallbacksAndMessages(null)
        player.volume = volume
    }

    /** Rampe linéaire de 200 ms (`linearRampToValueAtTime` du site). */
    private fun rampTo(volume: Float) {
        handler.removeCallbacksAndMessages(null)
        val from = player.volume
        for (step in 1..RAMP_STEPS) {
            handler.postDelayed({ player.volume = from + (volume - from) * step / RAMP_STEPS }, RAMP_MS * step / RAMP_STEPS)
        }
    }

    private companion object {
        const val TAG = "Rengaine/Volume"
        const val RAMP_MS = 200L
        const val RAMP_STEPS = 10
    }
}
