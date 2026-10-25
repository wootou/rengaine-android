package app.rengaine.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OwnerDto(val id: Long = 0, val displayName: String = "")

/** Playlist dans une liste (schéma `PlaylistSummary`). */
@Serializable
data class PlaylistSummaryDto(
    val id: Long,
    val name: String = "",
    val description: String? = null,
    val owner: OwnerDto = OwnerDto(),
    /** Créée par le compte connecté : lui seul la modifie. */
    val mine: Boolean = false,
    val shared: Boolean = false,
    val trackCount: Int = 0,
    val durationMs: Long = 0,
    /** Pochettes des premiers albums différents (4 au plus), pour une mosaïque. */
    val coverUrls: List<String> = emptyList(),
    val updatedAt: String? = null,
)

@Serializable
data class PlaylistListDto(val items: List<PlaylistSummaryDto> = emptyList())

/** Une entrée : une piste peut figurer deux fois, d'où l'identifiant propre. */
@Serializable
data class PlaylistEntryDto(
    val id: Long,
    val position: Int = 0,
    val addedAt: String? = null,
    val track: TrackDto,
)

@Serializable
data class PlaylistDetailDto(
    val id: Long,
    val name: String = "",
    val description: String? = null,
    val owner: OwnerDto = OwnerDto(),
    val mine: Boolean = false,
    val shared: Boolean = false,
    val trackCount: Int = 0,
    val durationMs: Long = 0,
    val coverUrls: List<String> = emptyList(),
    val items: List<PlaylistEntryDto> = emptyList(),
)

/** Corps de `POST /playlists`. */
@Serializable
data class PlaylistInputDto(
    val name: String,
    val shared: Boolean = false,
    val trackIds: List<Long> = emptyList(),
)

/** Corps de `PATCH /playlists/{id}` : seuls les champs présents changent. */
@Serializable
data class PlaylistPatchDto(
    val name: String? = null,
    val shared: Boolean? = null,
)

@Serializable
data class PlaylistAddDto(val trackIds: List<Long>)

@Serializable
enum class FavoriteType {
    @SerialName("track") TRACK,
    @SerialName("album") ALBUM,
    @SerialName("artist") ARTIST,
    UNKNOWN,
}

@Serializable
data class ArtistSummaryDto(
    val id: Long,
    val name: String = "",
    val albumCount: Int = 0,
    val imageUrl: String? = null,
)

@Serializable
data class FavoriteDto(
    val type: FavoriteType = FavoriteType.UNKNOWN,
    val addedAt: String? = null,
    val track: TrackDto? = null,
    val album: AlbumSummaryDto? = null,
    val artist: ArtistSummaryDto? = null,
)

@Serializable
data class FavoritePageDto(
    val items: List<FavoriteDto> = emptyList(),
    val total: Int = 0,
)
