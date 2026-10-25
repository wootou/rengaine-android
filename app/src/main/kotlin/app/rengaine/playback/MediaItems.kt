package app.rengaine.playback

import androidx.media3.common.util.UnstableApi
import androidx.annotation.OptIn
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.rengaine.data.remote.dto.AudioFormatDto
import app.rengaine.data.remote.dto.TrackDto
import app.rengaine.domain.GainTrack
import app.rengaine.domain.LoudnessMeasure
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Construit les [MediaItem] du lecteur à partir des pistes de l'API.
 *
 * Les adresses (flux, pochette) sont absolues : `AuthorizedCallFactory`
 * reconnaît le serveur et pose le jeton du bon compte. Les métadonnées
 * alimentent la notification et l'écran verrouillé de Media3.
 */
@OptIn(UnstableApi::class)
object MediaItems {

    const val KEY_TRACK_ID = "app.rengaine.trackId"
    const val KEY_COVER = "app.rengaine.cover"
    const val KEY_FORMAT = "app.rengaine.format"
    const val KEY_BPM = "app.rengaine.bpm"
    const val KEY_MUSICAL_KEY = "app.rengaine.key"
    const val KEY_CAMELOT = "app.rengaine.camelot"
    const val KEY_ALBUM_ID = "app.rengaine.albumId"
    /** Genres effectifs de la piste : le mode infini « normal » les reprend (F5). */
    const val KEY_GENRES = "app.rengaine.genres"
    /** Mesures de volume (F8) : absentes tant que l'analyse n'a pas tourné. */
    const val KEY_TRACK_LUFS = "app.rengaine.trackLufs"
    const val KEY_TRACK_PEAK = "app.rengaine.trackPeak"
    const val KEY_ALBUM_LUFS = "app.rengaine.albumLufs"
    const val KEY_ALBUM_PEAK = "app.rengaine.albumPeak"
    /** Favori au moment du chargement ; `FavoritesRepository` garde les changements faits depuis. */
    const val KEY_FAVORITE = "app.rengaine.favorite"

    /**
     * [track] doit porter des adresses **absolues** (`CatalogRepository`
     * les résout). `null` pour une piste dont le fichier a disparu.
     */
    fun fromTrack(track: TrackDto): MediaItem? {
        if (!track.available || track.streamUrl.isBlank()) return null
        track.formats.firstOrNull()?.let { rememberSource(track.id, it) }
        val cover = track.coverUrl
        val format = track.formats.firstOrNull()?.format

        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artistCredit.ifBlank { track.artists.joinToString { it.name } })
            .setAlbumTitle(track.album?.title)
            .setAlbumArtist(track.album?.artistName)
            .setTrackNumber(track.trackNumber)
            .setDiscNumber(track.discNumber)
            .setDurationMs(track.durationMs.takeIf { it > 0 })
            .setIsPlayable(true)
            .setIsBrowsable(false)
            // Pochette en `l` (600 px) : la notification et l'écran
            // verrouillé l'affichent en grand.
            .setArtworkUri(cover?.let { Uri.parse(withSize(it, "l")) })
            .setExtras(
                Bundle().apply {
                    putLong(KEY_TRACK_ID, track.id)
                    putBoolean(KEY_FAVORITE, track.favorite)
                    cover?.let { putString(KEY_COVER, it) }
                    format?.let { putString(KEY_FORMAT, it) }
                    track.album?.id?.let { putLong(KEY_ALBUM_ID, it) }
                    if (track.genres.isNotEmpty()) putLongArray(KEY_GENRES, track.genres.map { it.id }.toLongArray())
                    track.playback?.bpm?.let { putDouble(KEY_BPM, it) }
                    track.playback?.key?.let { putString(KEY_MUSICAL_KEY, it) }
                    track.playback?.camelot?.let { putString(KEY_CAMELOT, it) }
                    track.playback?.track?.lufs?.let { putDouble(KEY_TRACK_LUFS, it) }
                    track.playback?.track?.truePeakDbtp?.let { putDouble(KEY_TRACK_PEAK, it) }
                    track.playback?.album?.lufs?.let { putDouble(KEY_ALBUM_LUFS, it) }
                    track.playback?.album?.truePeakDbtp?.let { putDouble(KEY_ALBUM_PEAK, it) }
                },
            )
            .build()

        return MediaItem.Builder()
            .setMediaId(mediaId(track.id))
            .setUri(track.streamUrl)
            // Identité de la piste pour le cache ; la qualité s'y ajoute au
            // chargement (`PlaybackService`, F7) : une entrée par format.
            .setCustomCacheKey(itemKey(track.id))
            .setMediaMetadata(metadata)
            .build()
    }

    /** Ce que la normalisation lit d'une piste de la file. */
    fun gainTrack(item: MediaItem?): GainTrack? {
        val meta = item?.mediaMetadata ?: return null
        val x = meta.extras ?: return null
        fun d(key: String) = if (x.containsKey(key)) x.getDouble(key) else null
        return GainTrack(
            albumId = x.getLong(KEY_ALBUM_ID).takeIf { it > 0 },
            disc = meta.discNumber,
            trackNumber = meta.trackNumber,
            track = LoudnessMeasure(d(KEY_TRACK_LUFS), d(KEY_TRACK_PEAK)),
            album = LoudnessMeasure(d(KEY_ALBUM_LUFS), d(KEY_ALBUM_PEAK)),
        )
    }

    fun mediaId(trackId: Long): String = "track:$trackId"

    /**
     * Fichier d'origine de chaque piste mise dans le lecteur, pour décider
     * d'une conversion au chargement (F7). Borné : les plus anciennes
     * s'effacent au-delà de [MAX_SOURCES].
     */
    private val sources = object : java.util.LinkedHashMap<Long, AudioFormatDto>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, AudioFormatDto>?) = size > MAX_SOURCES
    }
    private const val MAX_SOURCES = 6_000

    fun sourceOf(trackId: Long): AudioFormatDto? = synchronized(sources) { sources[trackId] }

    private fun rememberSource(trackId: Long, format: AudioFormatDto) = synchronized(sources) { sources[trackId] = format }

    /**
     * Clé posée sur le `MediaItem` : la piste, et la position de départ
     * d'un flux converti relancé après un saut (`track:12@45000`, F7).
     */
    fun itemKey(trackId: Long, startMs: Long = 0): String = if (startMs > 0) "track:$trackId@$startMs" else "track:$trackId"

    /** Position de départ portée par la clé de l'élément ; 0 sinon. */
    fun startMsOf(item: MediaItem?): Long = startMsOfKey(item?.localConfiguration?.customCacheKey)

    fun startMsOfKey(key: String?): Long = key?.substringAfter('@', "")?.toLongOrNull() ?: 0

    /** Clé du cache disque : piste **et** qualité (F7). */
    fun cacheKey(trackId: Long, quality: StreamQuality): String = "track:$trackId:${quality.cacheSuffix}"

    /** Piste d'une clé de cache ou d'élément (`track:12` ou `track:12:opus160`). */
    fun trackIdOfKey(key: String?): Long? = key?.removePrefix("track:")?.substringBefore(':')?.substringBefore('@')?.toLongOrNull()

    fun trackIdOf(mediaId: String?): Long? = mediaId?.removePrefix("track:")?.toLongOrNull()

    /** Ajoute `?size=s|m|l|original` à l'adresse d'une image (contrat : le client choisit). */
    fun withSize(coverUrl: String, size: String): String =
        coverUrl.toHttpUrlOrNull()?.newBuilder()?.setQueryParameter("size", size)?.build()?.toString()
            ?: coverUrl
}
