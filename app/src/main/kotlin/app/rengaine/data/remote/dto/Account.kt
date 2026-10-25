package app.rengaine.data.remote.dto

import kotlinx.serialization.Serializable

/** `PATCH /me` : nom affiché (1 à 100 caractères). */
@Serializable
data class UpdateMeRequest(val displayName: String)

/** `POST /me/password` : 10 caractères au moins pour le nouveau. */
@Serializable
data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)

/** `DELETE /me` : le mot de passe est redemandé. */
@Serializable
data class DeleteMeRequest(val password: String)

/** Une session ou un appareil du compte (`Device`). */
@Serializable
data class DeviceDto(
    val id: Long,
    /** `web`, `app`, `agent` : gardé en texte. */
    val kind: String = "app",
    val name: String = "",
    val prefix: String = "",
    val createdAt: String = "",
    val lastUsedAt: String? = null,
    val lastIp: String? = null,
    val userAgent: String? = null,
    val expiresAt: String? = null,
)

@Serializable
data class DeviceListDto(val items: List<DeviceDto> = emptyList())

/** Code d'appairage à montrer en QR (`qrPayload`) et en clair. */
@Serializable
data class PairingCodeDto(
    val code: String,
    val expiresAt: String,
    val qrPayload: String,
)
