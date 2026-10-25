package app.rengaine.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Album dans une liste (schéma `AlbumSummary`). */
@Serializable
data class AlbumSummaryDto(
    val id: Long,
    val title: String = "",
    val kind: AlbumKind = AlbumKind.ALBUM,
    /** `null` pour une compilation d'artistes divers. */
    val artist: ArtistRefDto? = null,
    /** Nom à afficher (« Artistes divers » pour une compilation). */
    val artistName: String = "",
    val year: Int? = null,
    val trackCount: Int = 0,
    val discCount: Int = 1,
    val durationMs: Long = 0,
    val hasLossless: Boolean = false,
    val coverUrl: String? = null,
    val genres: List<GenreRefDto> = emptyList(),
    val addedAt: String? = null,
    val favorite: Boolean = false,
)

/** `loose` = dossier de pistes en vrac, sans album commun dans les tags. */
@Serializable
enum class AlbumKind {
    @SerialName("album") ALBUM,
    @SerialName("loose") LOOSE,
    UNKNOWN,
}

@Serializable
data class ArtistRefDto(val id: Long, val name: String = "")

@Serializable
data class GenreRefDto(val id: Long, val name: String = "")

@Serializable
data class AlbumPageDto(
    val items: List<AlbumSummaryDto> = emptyList(),
    val total: Int = 0,
    val limit: Int = 0,
    val offset: Int = 0,
)

/** Album et ses pistes, triées par disque puis par numéro (schéma `AlbumDetail`). */
@Serializable
data class AlbumDetailDto(
    val id: Long,
    val title: String = "",
    val kind: AlbumKind = AlbumKind.ALBUM,
    val artist: ArtistRefDto? = null,
    val artistName: String = "",
    val year: Int? = null,
    val trackCount: Int = 0,
    val discCount: Int = 1,
    val durationMs: Long = 0,
    val hasLossless: Boolean = false,
    val coverUrl: String? = null,
    val genres: List<GenreRefDto> = emptyList(),
    val favorite: Boolean = false,
    val isCompilation: Boolean = false,
    val tracks: List<TrackDto> = emptyList(),
)

@Serializable
data class GenreDto(
    val id: Long,
    val name: String = "",
    val parentId: Long? = null,
    val albumCount: Int = 0,
    val trackCount: Int = 0,
)

@Serializable
data class GenreListDto(val items: List<GenreDto> = emptyList())

@Serializable
data class DecadeDto(
    /** Première année : 1980 pour les années 80. */
    val decade: Int,
    val albumCount: Int = 0,
    val trackCount: Int = 0,
)

@Serializable
data class DecadeListDto(val items: List<DecadeDto> = emptyList())

@Serializable
data class ArtistPageDto(
    val items: List<ArtistSummaryDto> = emptyList(),
    val total: Int = 0,
)

/** Artiste, ses albums et ceux où il apparaît (schéma `ArtistDetail`). */
@Serializable
data class ArtistDetailDto(
    val id: Long,
    val name: String = "",
    val albumCount: Int = 0,
    val imageUrl: String? = null,
    val favorite: Boolean = false,
    /** Albums dont il est l'artiste principal. */
    val albums: List<AlbumSummaryDto> = emptyList(),
    /** Autres albums où il apparaît (compilations, featurings). */
    val appearsOn: List<AlbumSummaryDto> = emptyList(),
)

@Serializable
data class TrackPageDto(
    val items: List<TrackDto> = emptyList(),
    val total: Int = 0,
)

/** Ambiance calculée par le serveur (`calme`, `concentration`, `energique`, `danser`…). */
@Serializable
data class MoodDto(
    val id: String,
    val name: String = "",
    val description: String = "",
    val trackCount: Int = 0,
)

@Serializable
data class MoodListDto(val items: List<MoodDto> = emptyList())

@Serializable
data class CollectionDto(
    val id: Long,
    val kind: String = "collection",
    val name: String = "",
    val description: String? = null,
    val albumCount: Int = 0,
    val coverUrls: List<String> = emptyList(),
)

@Serializable
data class CollectionListDto(val items: List<CollectionDto> = emptyList())

/** Résultats de `GET /search`, groupés (F3). */
@Serializable
data class SearchResultsDto(
    val artists: List<ArtistSummaryDto> = emptyList(),
    val albums: List<AlbumSummaryDto> = emptyList(),
    val tracks: List<TrackDto> = emptyList(),
)
