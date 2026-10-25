package app.rengaine.data.remote.dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/** `POST /auth/login` avec `client: app` : `deviceName` obligatoire. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
    val deviceName: String,
    @EncodeDefault val client: String = "app",
)

/** Le jeton n'apparaît qu'ici, une seule fois : le serveur n'en garde que l'empreinte. */
@Serializable
data class LoginResponse(
    val user: UserDto,
    val token: String? = null,
    val tokenId: Long? = null,
)

/** `POST /auth/pair` : le **code seul**, jamais l'URI du QR. */
@Serializable
data class PairRequest(
    val code: String,
    val deviceName: String,
)

@Serializable
data class TokenResponse(
    val token: String,
    val tokenId: Long,
    val user: UserDto,
)

@Serializable
data class ForgotPasswordRequest(val email: String)

@Serializable
data class MeResponse(
    val user: UserDto,
    val currentTokenId: Long? = null,
)
