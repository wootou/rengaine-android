package app.rengaine.data.repository

import android.content.ContentResolver
import android.net.Uri
import app.rengaine.data.remote.ApiError
import app.rengaine.data.remote.ApiResult
import app.rengaine.data.remote.apiCall
import app.rengaine.data.remote.apiCallUnit
import app.rengaine.data.remote.dto.DeleteMeRequest
import app.rengaine.data.remote.dto.DeviceDto
import app.rengaine.data.remote.dto.PairingCodeDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** Appareils du compte, et le jeton de ce téléphone parmi eux. */
data class Devices(val items: List<DeviceDto>, val currentTokenId: Long?)

/**
 * Compte et appareils (F13), pour le compte actif, comme Réglages du site :
 * appareils connectés, code d'appairage, export des données, suppression
 * du compte. Le nom affiché et le mot de passe (`PATCH /me`,
 * `/me/password`) attendent que le site les propose aussi.
 */
@Singleton
class MeRepository @Inject constructor(
    private val accounts: AccountRepository,
) {
    suspend fun devices(): ApiResult<Devices> = withSession { s ->
        coroutineScope {
            val me = async { apiCall { s.api.getMe() } }
            val list = async { apiCall { s.api.listDevices() } }
            when (val l = list.await()) {
                is ApiResult.Ok -> ApiResult.Ok(Devices(l.value.items, (me.await() as? ApiResult.Ok)?.value?.currentTokenId))
                is ApiResult.Err -> l
            }
        }
    }

    suspend fun revoke(tokenId: Long): ApiResult<Unit> = withSession { s ->
        apiCallUnit { s.api.revokeDevice(tokenId) }
    }

    suspend fun pairingCode(): ApiResult<PairingCodeDto> = withSession { s ->
        apiCall { s.api.createPairingCode() }
    }

    /**
     * Enregistre l'export **tel quel** dans [target] (choisi par l'utilisateur
     * via le sélecteur du système) : copié octet par octet, jamais décodé ni
     * journalisé — il contient des données personnelles.
     */
    suspend fun export(resolver: ContentResolver, target: Uri): ApiResult<Unit> = withSession { s ->
        val response = try {
            s.api.exportMyData()
        } catch (e: java.io.IOException) {
            return@withSession ApiResult.Err(ApiError.Network(e))
        }
        val body = response.body()
        if (!response.isSuccessful || body == null) {
            response.errorBody()?.close()
            return@withSession ApiResult.Err(
                ApiError.Http(response.code(), if (response.code() == 429) "rate_limited" else "", "", null, null, response.headers()["Retry-After"]?.toLongOrNull()),
            )
        }
        withContext(Dispatchers.IO) {
            try {
                body.use { b ->
                    resolver.openOutputStream(target, "wt")?.use { out -> b.byteStream().copyTo(out) }
                        ?: return@withContext ApiResult.Err(ApiError.Network(java.io.IOException("fichier inaccessible")))
                }
                ApiResult.Ok(Unit)
            } catch (e: java.io.IOException) {
                ApiResult.Err(ApiError.Network(e))
            }
        }
    }

    /**
     * Supprime le compte (après le délai d'annulation, côté écran). Renvoie
     * l'identifiant local du compte supprimé : l'appelant le retire ensuite
     * de l'app avec [forget], une fois le reste rangé (l'app quitte alors
     * l'écran en cours).
     */
    suspend fun deleteAccount(password: String): ApiResult<Long> = withSession { s ->
        when (val r = apiCallUnit { s.api.deleteMe(DeleteMeRequest(password)) }) {
            is ApiResult.Ok -> ApiResult.Ok(s.account.id)
            is ApiResult.Err -> r
        }
    }

    /** Retire de l'app un compte supprimé sur le serveur (jeton déjà invalide : rien à révoquer). */
    suspend fun forget(accountId: Long) = accounts.forget(accountId)

    private suspend fun <T> withSession(block: suspend (ActiveSession) -> ApiResult<T>): ApiResult<T> {
        val session = accounts.activeSession()
            ?: return ApiResult.Err(ApiError.Http(401, "unauthorized", "", null, null, null))
        return block(session)
    }
}
