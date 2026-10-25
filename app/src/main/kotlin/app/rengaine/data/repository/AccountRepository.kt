package app.rengaine.data.repository

import app.rengaine.data.local.AccountDao
import app.rengaine.data.local.AccountEntity
import app.rengaine.data.local.SessionStore
import app.rengaine.data.local.TokenCipher
import app.rengaine.data.remote.ApiClients
import app.rengaine.data.remote.ApiError
import app.rengaine.data.remote.ApiResult
import app.rengaine.data.remote.ErrorCodes
import app.rengaine.data.remote.RengaineApi
import app.rengaine.data.remote.ServerAddress
import app.rengaine.data.remote.apiCall
import app.rengaine.data.remote.apiCallUnit
import app.rengaine.data.remote.dto.ForgotPasswordRequest
import app.rengaine.data.remote.dto.LoginRequest
import app.rengaine.data.remote.dto.PairRequest
import app.rengaine.data.remote.dto.Role
import app.rengaine.data.remote.dto.ServerInfoDto
import app.rengaine.data.remote.dto.UserDto
import app.rengaine.di.ApplicationScope
import app.rengaine.domain.Account
import app.rengaine.domain.PairingUri
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Pourquoi une connexion a échoué, du point de vue de l'utilisateur. */
sealed interface SignInError {
    data object InvalidCredentials : SignInError
    data object InvalidCode : SignInError
    data object AccountPending : SignInError
    data object AccountSuspended : SignInError

    /** Trop d'échecs : le compte est verrouillé quelques minutes (`423`). Ne pas relancer. */
    data class AccountLocked(val retryAfterSeconds: Long?) : SignInError
    data class RateLimited(val retryAfterSeconds: Long?) : SignInError
    data class Server(val check: ServerCheck) : SignInError
    data object Unreachable : SignInError
    data object CertificateRejected : SignInError

    /** Erreur imprévue : le `requestId` permet de la retrouver côté serveur. */
    data class Unexpected(val status: Int?, val requestId: String?) : SignInError
}

sealed interface SignInResult {
    data class Success(val account: Account) : SignInResult
    data class Failure(val error: SignInError) : SignInResult
}

/** Compte actif et son client authentifié, pour les fonctions de l'app. */
class ActiveSession(val account: Account, val api: RengaineApi, val http: okhttp3.OkHttpClient)

/**
 * Comptes mémorisés et compte actif (F1). Plusieurs comptes, un seul actif ;
 * chaque compte garde son jeton d'appareil, chiffré.
 */
@Singleton
class AccountRepository @Inject constructor(
    private val dao: AccountDao,
    private val session: SessionStore,
    private val cipher: TokenCipher,
    private val clients: ApiClients,
    private val servers: ServerRepository,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    val accounts: Flow<List<Account>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    /** Compte actif, `null` si aucun (premier lancement, dernier compte retiré). */
    val activeAccount: Flow<Account?> =
        combine(dao.observeAll(), session.activeAccountId) { list, activeId ->
            list.firstOrNull { it.id == activeId }?.toDomain()
        }.distinctUntilChanged()

    /** Client du compte actif ; `null` sans compte ou sans jeton lisible. */
    suspend fun activeSession(): ActiveSession? {
        val id = session.activeAccountIdNow() ?: return null
        val entity = dao.byId(id) ?: return null
        val token = cipher.decrypt(entity.encryptedToken) ?: run {
            dao.setNeedsReauth(id, true)
            return null
        }
        val client = clientFor(entity, token)
        return ActiveSession(entity.toDomain(), client.api, client.http)
    }

    /**
     * Client HTTP authentifié d'un compte, pour le lecteur et les pochettes
     * (voir `AuthorizedCallFactory`). `null` si le compte a disparu ou si
     * son jeton est illisible.
     */
    suspend fun httpClientFor(accountId: Long): okhttp3.OkHttpClient? {
        val entity = dao.byId(accountId) ?: return null
        val token = cipher.decrypt(entity.encryptedToken) ?: return null
        return clientFor(entity, token).http
    }

    /**
     * API d'un compte mémorisé, actif ou non (écoutes en attente d'un autre
     * compte, F11). `null` si le compte a disparu, doit se reconnecter, ou
     * si son jeton est illisible.
     */
    suspend fun apiFor(accountId: Long): RengaineApi? {
        val entity = dao.byId(accountId)?.takeUnless { it.needsReauth } ?: return null
        val token = cipher.decrypt(entity.encryptedToken) ?: return null
        return clientFor(entity, token).api
    }

    suspend fun login(
        server: ServerCheck.Ready,
        email: String,
        password: String,
        deviceName: String,
    ): SignInResult {
        val api = clients.anonymous(server.address)
        val result = apiCall { api.login(LoginRequest(email.trim(), password, deviceName)) }
        return when (result) {
            is ApiResult.Ok -> {
                val token = result.value.token
                    ?: return SignInResult.Failure(SignInError.Unexpected(200, null))
                SignInResult.Success(store(server, result.value.user, token, result.value.tokenId, deviceName))
            }
            is ApiResult.Err -> SignInResult.Failure(result.error.toSignInError())
        }
    }

    /**
     * Connexion par le QR du site. L'adresse vient du QR : elle est vérifiée
     * comme une adresse saisie avant d'envoyer le code.
     */
    suspend fun pair(payload: PairingUri.Payload, deviceName: String): SignInResult {
        val server = when (val check = servers.check(payload.server)) {
            is ServerCheck.Ready -> check
            else -> return SignInResult.Failure(SignInError.Server(check))
        }
        return pair(server, payload.code, deviceName)
    }

    suspend fun pair(server: ServerCheck.Ready, code: String, deviceName: String): SignInResult {
        val api = clients.anonymous(server.address)
        return when (val result = apiCall { api.pair(PairRequest(PairingUri.normalizeCode(code), deviceName)) }) {
            is ApiResult.Ok -> SignInResult.Success(
                store(server, result.value.user, result.value.token, result.value.tokenId, deviceName),
            )
            is ApiResult.Err -> SignInResult.Failure(result.error.toSignInError())
        }
    }

    /** `POST /auth/password/forgot` : même réponse que l'adresse existe ou non. */
    suspend fun forgotPassword(address: ServerAddress, email: String): ApiResult<Unit> =
        when (val r = apiCall { clients.anonymous(address).forgotPassword(ForgotPasswordRequest(email.trim())) }) {
            is ApiResult.Ok -> ApiResult.Ok(Unit)
            is ApiResult.Err -> r
        }

    suspend fun switchTo(accountId: Long) {
        dao.touch(accountId, System.currentTimeMillis())
        session.setActiveAccount(accountId)
    }

    /**
     * Déconnexion : révoque le jeton côté serveur (au mieux : hors ligne, on
     * retire quand même le compte de l'app), puis l'oublie. Si c'était le
     * compte actif, le plus récemment utilisé des autres prend sa place ;
     * sans autre compte, l'app revient à l'écran de connexion.
     */
    suspend fun signOut(accountId: Long) {
        val entity = dao.byId(accountId) ?: return
        if (!entity.needsReauth) {
            cipher.decrypt(entity.encryptedToken)?.let { token ->
                withTimeoutOrNull(REVOKE_TIMEOUT_MS) {
                    apiCallUnit { clientFor(entity, token).api.logout() }
                }
            }
        }
        forget(accountId)
    }

    /** Retire un compte de l'app sans contacter le serveur (compte supprimé, par exemple). */
    suspend fun forget(accountId: Long) {
        clients.forget(accountId)
        dao.delete(accountId)
        if (session.activeAccountIdNow() == accountId) {
            session.setActiveAccount(dao.all().firstOrNull()?.id)
        }
    }

    private suspend fun store(
        server: ServerCheck.Ready,
        user: UserDto,
        token: String,
        tokenId: Long?,
        deviceName: String,
    ): Account {
        val now = System.currentTimeMillis()
        val existing = dao.find(server.address.root, user.id)
        // Même compte reconnecté : l'ancien jeton, s'il marche encore,
        // resterait orphelin dans la liste des appareils du site.
        if (existing != null && !existing.needsReauth) {
            cipher.decrypt(existing.encryptedToken)?.let { old ->
                if (old != token) revokeQuietly(existing, old)
            }
        }
        val entity = AccountEntity(
            id = existing?.id ?: 0,
            serverRoot = server.address.root,
            serverName = server.info.name,
            apiVersion = server.info.apiVersion,
            userId = user.id,
            email = user.email,
            displayName = user.displayName,
            role = user.role.name,
            tokenId = tokenId,
            encryptedToken = cipher.encrypt(token),
            deviceName = deviceName,
            needsReauth = false,
            addedAt = existing?.addedAt ?: now,
            lastUsedAt = now,
        )
        val id = dao.upsert(entity).let { if (it > 0) it else entity.id }
        clients.forget(id)
        session.setActiveAccount(id)
        session.setDeviceName(deviceName)
        return entity.copy(id = id).toDomain()
    }

    private fun revokeQuietly(entity: AccountEntity, token: String) {
        val client = clients.forAccount(-entity.id, ServerAddress.fromStored(entity.serverRoot), token) {}
        appScope.launch {
            withTimeoutOrNull(REVOKE_TIMEOUT_MS) { apiCallUnit { client.api.logout() } }
            clients.forget(-entity.id)
        }
    }

    private fun clientFor(entity: AccountEntity, token: String): ApiClients.AccountClient =
        clients.forAccount(entity.id, ServerAddress.fromStored(entity.serverRoot), token) {
            // Jeton refusé : seul ce compte doit se reconnecter.
            appScope.launch { dao.setNeedsReauth(entity.id, true) }
        }

    private fun AccountEntity.toDomain() = Account(
        id = id,
        address = ServerAddress.fromStored(serverRoot),
        serverName = serverName,
        apiVersion = apiVersion,
        userId = userId,
        email = email,
        displayName = displayName,
        isAdmin = role == Role.ADMIN.name,
        deviceName = deviceName,
        needsReauth = needsReauth,
    )

    private fun ApiError.toSignInError(): SignInError = when (this) {
        is ApiError.Network -> SignInError.Unreachable
        is ApiError.Tls -> SignInError.CertificateRejected
        is ApiError.Malformed -> SignInError.Server(ServerCheck.NotRengaine)
        is ApiError.Http -> when (code) {
            ErrorCodes.INVALID_CREDENTIALS -> SignInError.InvalidCredentials
            ErrorCodes.INVALID_CODE -> SignInError.InvalidCode
            ErrorCodes.ACCOUNT_PENDING -> SignInError.AccountPending
            ErrorCodes.ACCOUNT_SUSPENDED -> SignInError.AccountSuspended
            ErrorCodes.ACCOUNT_LOCKED -> SignInError.AccountLocked(retryAfterSeconds)
            ErrorCodes.RATE_LIMITED -> SignInError.RateLimited(retryAfterSeconds)
            else -> when (status) {
                423 -> SignInError.AccountLocked(retryAfterSeconds)
                429 -> SignInError.RateLimited(retryAfterSeconds)
                // Un 401 sans code connu reste un refus des identifiants.
                401 -> SignInError.InvalidCredentials
                else -> SignInError.Unexpected(status, requestId)
            }
        }
    }

    private companion object {
        const val REVOKE_TIMEOUT_MS = 5_000L
    }
}
