package app.rengaine.data.remote.dto

import kotlinx.serialization.Serializable

/** Une écoute (`PlayInput`, F11). */
@Serializable
data class PlayInputDto(
    val trackId: Long,
    /** Date ISO 8601 (UTC) du début d'écoute. */
    val startedAt: String,
    val playedMs: Long,
    val completed: Boolean = false,
    val skipped: Boolean = false,
    /** `album`, `artist`, `search`, `random`, `continuous`, `queue`, `other`. */
    val context: String = "queue",
)

/** Corps de `POST /me/history` : 1 à 500 écoutes. */
@Serializable
data class RecordPlaysDto(val plays: List<PlayInputDto>)

/** Une écoute de l'historique (`GET /me/history`, `id` depuis 2.26.0). */
@Serializable
data class PlayHistoryItemDto(
    val id: Long,
    val startedAt: String = "",
    val playedMs: Long = 0,
    val completed: Boolean = false,
    val skipped: Boolean = false,
    val context: String = "queue",
    /** Nom de l'appareil qui a joué, s'il est connu. */
    val device: String? = null,
    val track: TrackDto,
)

@Serializable
data class PlayHistoryPageDto(
    val items: List<PlayHistoryItemDto> = emptyList(),
    val total: Int = 0,
)

/** Réponse de `DELETE /me/history`. */
@Serializable
data class HistoryClearedDto(val deleted: Int = 0)
