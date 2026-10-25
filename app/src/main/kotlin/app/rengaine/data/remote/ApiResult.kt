package app.rengaine.data.remote

import app.rengaine.data.remote.dto.ErrorEnvelope
import java.io.IOException
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import retrofit2.Response

/**
 * Résultat d'un appel à l'API. Les dépôts exposent ce type : les exceptions
 * de Retrofit et d'OkHttp ne remontent jamais jusqu'à l'interface.
 */
sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T, val etag: String? = null) : ApiResult<T>
    data class Err(val error: ApiError) : ApiResult<Nothing>
}

/** Erreur typée (S3). Le [Http.code] fait foi, jamais le message. */
sealed interface ApiError {
    /** Réponse reçue en erreur, au format commun de l'API. */
    data class Http(
        val status: Int,
        val code: String,
        val message: String,
        val requestId: String?,
        val details: JsonObject?,
        /** `Retry-After` en secondes (`429`, `423`, `503`), s'il est fourni. */
        val retryAfterSeconds: Long?,
    ) : ApiError

    /** Aucune réponse : pas de réseau, hôte inconnu, délai dépassé. */
    data class Network(val cause: IOException) : ApiError

    /** Le certificat du serveur n'est pas reconnu par Android (D6). */
    data class Tls(val cause: SSLException) : ApiError

    /**
     * Réponse reçue mais illisible : ce n'est probablement pas un serveur
     * Rengaine (page d'accueil d'un autre site, proxy mal réglé…).
     */
    data class Malformed(val status: Int, val cause: Throwable?) : ApiError
}

fun <T> ApiResult<T>.valueOrNull(): T? = (this as? ApiResult.Ok)?.value

/** Codes d'erreur stables du contrat, ceux que l'app teste. */
object ErrorCodes {
    const val INVALID_CREDENTIALS = "invalid_credentials"
    const val INVALID_CODE = "invalid_code"
    const val ACCOUNT_PENDING = "account_pending"
    const val ACCOUNT_SUSPENDED = "account_suspended"
    const val ACCOUNT_LOCKED = "account_locked"
    const val RATE_LIMITED = "rate_limited"
    const val UNAUTHORIZED = "unauthorized"
    const val VERSION_CONFLICT = "version_conflict"
    const val NOT_IMPLEMENTED = "not_implemented"
    const val STORAGE_UNAVAILABLE = "storage_unavailable"
}

/**
 * Exécute un appel Retrofit et le convertit en [ApiResult]. [emptyBody]
 * fournit la valeur d'une réponse sans corps (`204`) : Retrofit renvoie
 * alors `null`, qu'on ne peut pas distinguer d'un corps manquant.
 */
suspend fun <T : Any> apiCall(
    emptyBody: (() -> T)? = null,
    block: suspend () -> Response<T>,
): ApiResult<T> {
    val response = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: SSLException) {
        return ApiResult.Err(ApiError.Tls(e))
    } catch (e: IOException) {
        return ApiResult.Err(ApiError.Network(e))
    } catch (e: SerializationException) {
        // Corps 2xx non conforme au contrat.
        return ApiResult.Err(ApiError.Malformed(200, e))
    } catch (e: IllegalArgumentException) {
        // kotlinx.serialization lève aussi IllegalArgumentException
        // (champ obligatoire absent, par exemple).
        return ApiResult.Err(ApiError.Malformed(200, e))
    }
    val etag = response.headers()["ETag"]
    if (response.isSuccessful) {
        val body = response.body()
        return when {
            body != null -> ApiResult.Ok(body, etag)
            emptyBody != null -> ApiResult.Ok(emptyBody(), etag)
            else -> ApiResult.Err(ApiError.Malformed(response.code(), null))
        }
    }
    return ApiResult.Err(parseHttpError(response))
}

/** Raccourci pour les opérations qui répondent `204`. */
suspend fun apiCallUnit(block: suspend () -> Response<Unit>): ApiResult<Unit> =
    apiCall(emptyBody = { }, block = block)

private fun parseHttpError(response: Response<*>): ApiError {
    val status = response.code()
    val raw = runCatching { response.errorBody()?.string() }.getOrNull()
    val body = raw?.let { runCatching { ApiJson.decodeFromString(ErrorEnvelope.serializer(), it).error }.getOrNull() }
    val retryAfter = response.headers()["Retry-After"]?.trim()?.toLongOrNull()
    if (body == null || body.code.isEmpty()) {
        // Pas le format d'erreur de l'API : un autre logiciel répond à
        // cette adresse (ou un proxy). On garde le statut pour le message.
        return ApiError.Malformed(status, null)
    }
    return ApiError.Http(
        status = status,
        code = body.code,
        message = body.message,
        requestId = body.requestId ?: response.headers()["X-Request-Id"],
        details = body.details,
        retryAfterSeconds = retryAfter,
    )
}
