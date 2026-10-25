package app.rengaine.data.remote.dto

import kotlinx.serialization.Serializable

/** `GET /server` : public, appelé dès que l'adresse est saisie (F1). */
@Serializable
data class ServerInfoDto(
    val name: String = "",
    val apiVersion: String,
    val registrationOpen: Boolean = false,
    val features: FeaturesDto = FeaturesDto(),
    val limits: LimitsDto = LimitsDto(),
    val library: LibraryDto? = null,
)

/**
 * Fonctions annoncées par le serveur. Tout ce qui n'est pas annoncé est
 * masqué dans l'app : les valeurs par défaut sont donc « absent ».
 */
@Serializable
data class FeaturesDto(
    val transcoding: Boolean = false,
    val transcodingFormats: List<String> = emptyList(),
    val continuousPlay: Boolean = false,
    val analysis: AnalysisDto = AnalysisDto(),
    val remoteControl: Boolean = false,
    val favorites: Boolean = false,
    val history: Boolean = false,
    val pairing: Boolean = false,
    val passwordReset: Boolean = false,
)

@Serializable
data class AnalysisDto(
    val loudness: Boolean = false,
    val silence: Boolean = false,
    val bpm: Boolean = false,
    val key: Boolean = false,
    val energy: Boolean = false,
    val waveform: Boolean = false,
    val beatGrid: Boolean = false,
    val fingerprint: Boolean = false,
)

@Serializable
data class LimitsDto(
    val maxPageSize: Int = 500,
    val maxQueueLength: Int = 5000,
)

@Serializable
data class LibraryDto(
    val albums: Int = 0,
    val artists: Int = 0,
    val tracks: Int = 0,
    val durationMs: Long = 0,
    val updatedAt: String? = null,
)
