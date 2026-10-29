package app.rengaine.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Piste (schéma `Track`). Valeurs par défaut partout : une piste incomplète
 * se lit quand même, et `ignoreUnknownKeys` absorbe ce que les versions 2.x
 * suivantes ajouteront.
 */
@Serializable
data class TrackDto(
    val id: Long,
    val title: String = "",
    /** Texte affiché (« Gorillaz feat. Kali Uchis »). */
    val artistCredit: String = "",
    val artists: List<TrackArtistDto> = emptyList(),
    val album: TrackAlbumDto? = null,
    /** Genres effectifs : ceux de la piste, sinon ceux de l'album. */
    val genres: List<TrackGenreDto> = emptyList(),
    val discNumber: Int = 1,
    val trackNumber: Int? = null,
    val durationMs: Long = 0,
    /** Chemin de l'image sans taille (`?size=s|m|l` ajouté par le client) ; `null` = pas d'image. */
    val coverUrl: String? = null,
    /** Chemin du flux, relatif à la racine du serveur (`/api/v2/tracks/1234/stream`). */
    val streamUrl: String = "",
    /** Fichiers disponibles, le meilleur en premier (celui que sert `original`). */
    val formats: List<AudioFormatDto> = emptyList(),
    val playback: PlaybackInfoDto? = null,
    val favorite: Boolean = false,
    /** `false` si le fichier a disparu du stockage : ne pas la mettre dans la file. */
    val available: Boolean = true,
)

@Serializable
data class TrackArtistDto(
    val id: Long,
    val name: String = "",
    val role: ArtistRole = ArtistRole.UNKNOWN,
)

@Serializable
enum class ArtistRole {
    @SerialName("main") MAIN,
    @SerialName("featured") FEATURED,
    @SerialName("remixer") REMIXER,
    UNKNOWN,
}

@Serializable
data class TrackAlbumDto(
    val id: Long,
    val title: String = "",
    val artistName: String? = null,
    val year: Int? = null,
)

@Serializable
data class TrackGenreDto(
    val id: Long,
    val name: String = "",
    val inherited: Boolean = false,
)

@Serializable
data class AudioFormatDto(
    /** `mp3`, `flac`, `opus`, `vorbis`, `aac`, `alac`, `wav`, `other` : gardé en texte. */
    val format: String = "other",
    val lossless: Boolean = false,
    val bitrateKbps: Int? = null,
    val sampleRateHz: Int? = null,
    val bitDepth: Int? = null,
    val channels: Int? = null,
    val sizeBytes: Long = 0,
)

/**
 * Mesures pour la normalisation et le fondu (F8). Tout est `null` tant que
 * l'analyse n'a pas tourné : toujours prévoir un repli.
 */
@Serializable
data class PlaybackInfoDto(
    val track: LoudnessDto? = null,
    val album: LoudnessDto? = null,
    val audioStartMs: Long? = null,
    val audioEndMs: Long? = null,
    val bpm: Double? = null,
    val key: String? = null,
    /** Code Camelot (« 8A »), pour les enchaînements harmoniques. */
    val camelot: String? = null,
    val energy: Double? = null,
    /** Grilles des temps du début et de la fin (LEC-06), pour caler le fondu sur le tempo. */
    val beatGrid: BeatGridsDto? = null,
)

@Serializable
data class BeatGridsDto(val intro: BeatGridDto? = null, val outro: BeatGridDto? = null)

/** Un temps tombe à `offsetMs + n × 60000 / bpm`. */
@Serializable
data class BeatGridDto(val bpm: Double = 0.0, val offsetMs: Double = 0.0)

@Serializable
data class LoudnessDto(
    val lufs: Double? = null,
    val truePeakDbtp: Double? = null,
)

@Serializable
data class TrackListDto(val items: List<TrackDto> = emptyList())

/**
 * `GET /tracks/{id}/waveform` : niveau RMS par tranche régulière, en dBFS
 * (de -63,75 à 0) ; `null` = silence. Environ 800 tranches par piste.
 */
@Serializable
data class WaveformDto(
    val trackId: Long,
    val durationMs: Long = 0,
    val stepMs: Double = 0.0,
    val levels: List<Float?> = emptyList(),
)

/**
 * `POST /playback/next` (lecture continue, LEC-03) : [currentTrackId] sert
 * de référence (clé, tempo, énergie) ; [recentTrackIds] ne sont pas proposées.
 */
@Serializable
data class NextTracksRequest(
    val count: Int,
    val currentTrackId: Long? = null,
    val recentTrackIds: List<Long> = emptyList(),
    val overrides: NextTracksOverrides? = null,
)

/** Règles `continuous` remplacées pour une demande : le ciblage d'Explorer. */
@Serializable
data class NextTracksOverrides(
    val genres: List<Long>? = null,
    val mood: String? = null,
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    val collection: Long? = null,
)
