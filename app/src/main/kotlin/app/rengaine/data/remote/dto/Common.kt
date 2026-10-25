package app.rengaine.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Corps d'erreur commun à toute l'API : `{error: {status, code, message,
 * requestId, details?}}`. Le **`code` fait foi** (stable, testable) ; le
 * `message` est un texte en français destiné à un humain, jamais testé.
 */
@Serializable
data class ErrorEnvelope(val error: ErrorBody)

@Serializable
data class ErrorBody(
    val status: Int = 0,
    val code: String = "",
    val message: String = "",
    val requestId: String? = null,
    val details: JsonObject? = null,
)

@Serializable
data class AcceptedResponse(val message: String = "")

/**
 * Rôle d'un compte. Contrat additif : une valeur inconnue (ajoutée par une
 * version 2.x plus récente) devient [UNKNOWN] grâce à `coerceInputValues`
 * et à la valeur par défaut des champs qui portent ce type.
 */
@Serializable
enum class Role {
    @SerialName("admin") ADMIN,
    @SerialName("user") USER,
    @SerialName("agent") AGENT,
    UNKNOWN,
}

@Serializable
data class UserDto(
    val id: Long,
    val email: String = "",
    val displayName: String = "",
    val role: Role = Role.UNKNOWN,
)
