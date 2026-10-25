package app.rengaine.playback

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ClippingMediaSource
import androidx.media3.exoplayer.source.MediaSource

/**
 * Enchaînement sans blanc des conversions (F8).
 *
 * Une conversion Opus se termine par le remplissage silencieux de
 * l'encodeur, que Media3 ne retire pas dans un flux Ogg : environ 26 ms de
 * trop mesurés en fin de piste, un petit trou à chaque changement. On arrête
 * donc une piste convertie à sa durée exacte (`durationMs` du serveur, moins
 * le départ d'un flux relancé en cours de piste) : il reste moins d'un
 * paquet Opus d'écart (7 ms mesurés).
 *
 * Les fichiers d'origine ne sont pas touchés : leur fin est exacte, et une
 * durée annoncée un peu courte (MP3 mal étiqueté) couperait la musique.
 */
@OptIn(UnstableApi::class)
class ConversionEndFactory(
    private val base: MediaSource.Factory,
    private val quality: StreamQualityPolicy,
) : MediaSource.Factory by base {

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val source = base.createMediaSource(mediaItem)
        val trackId = MediaItems.trackIdOfKey(mediaItem.localConfiguration?.customCacheKey) ?: return source
        if (quality.planned(trackId).format == null) return source
        val durationMs = mediaItem.mediaMetadata.durationMs ?: return source
        val endMs = durationMs - MediaItems.startMsOf(mediaItem)
        if (endMs <= 0) return source
        return ClippingMediaSource.Builder(source).setEndPositionMs(endMs).build()
    }
}
