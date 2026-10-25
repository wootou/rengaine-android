package app.rengaine.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/** File d'attente enregistrée sur le serveur (schéma `PlayQueue`). */
@Serializable
data class PlayQueueDto(
    val trackIds: List<Long> = emptyList(),
    /** Position dans [trackIds] de la piste en cours ; `null` = rien en cours. */
    val currentIndex: Int? = null,
    val positionMs: Long = 0,
    /** `normal`, `repeat_all`, `repeat_one`, `continuous` : gardé en texte (valeurs futures). */
    val mode: String = QueueModes.NORMAL,
    val version: Long = 0,
    val updatedAt: String? = null,
    /** Nom de l'appareil qui l'a enregistrée en dernier. */
    val updatedBy: String? = null,
    /** Pistes de [trackIds], dans le même ordre. */
    val tracks: List<TrackDto> = emptyList(),
)

/** Corps de `PUT /me/queue` (schéma `PlayQueueInput`). */
@Serializable
data class PlayQueueInputDto(
    val trackIds: List<Long>,
    val currentIndex: Int?,
    val positionMs: Long,
    val mode: String,
) {
    /** `currentIndex` est obligatoire mais peut valoir `null` : on l'écrit explicitement. */
    fun toJson(): JsonObject = buildJsonObject {
        put("trackIds", buildJsonArray { trackIds.forEach { add(JsonPrimitive(it)) } })
        put("currentIndex", currentIndex?.let { JsonPrimitive(it) } ?: JsonNull)
        put("positionMs", JsonPrimitive(positionMs))
        put("mode", JsonPrimitive(mode))
    }
}

object QueueModes {
    const val NORMAL = "normal"
    const val REPEAT_ALL = "repeat_all"
    const val REPEAT_ONE = "repeat_one"
    const val CONTINUOUS = "continuous"

    /** Plafond du contrat (`maxItems` de `trackIds`, `limits.maxQueueLength`). */
    const val MAX_LENGTH = 5000
}
