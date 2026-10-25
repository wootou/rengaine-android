package app.rengaine.data.remote

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Fabrique les clients de l'API.
 *
 * - [anonymous] : pour une adresse pas encore liée à un compte (découverte,
 *   connexion, appairage, mot de passe oublié). Jamais de jeton.
 * - [forAccount] : client authentifié d'un compte, mis en cache tant que
 *   son jeton ne change pas. Son `OkHttpClient` sert aussi au lecteur et
 *   aux pochettes (S2).
 *
 * Tous dérivent du même [baseClient] : même pool de connexions, mêmes
 * délais, même journal (sans jeton).
 */
@Singleton
class ApiClients @Inject constructor(
    private val baseClient: OkHttpClient,
) {
    class AccountClient(val api: RengaineApi, val http: OkHttpClient, internal val token: String)

    private val accounts = ConcurrentHashMap<Long, AccountClient>()

    fun anonymous(address: ServerAddress): RengaineApi = retrofit(address, baseClient)

    fun forAccount(
        accountId: Long,
        address: ServerAddress,
        token: String,
        onUnauthorized: () -> Unit,
    ): AccountClient {
        accounts[accountId]?.takeIf { it.token == token }?.let { return it }
        val http = baseClient.newBuilder()
            .addInterceptor(AuthInterceptor(token, onUnauthorized))
            .build()
        val client = AccountClient(retrofit(address, http), http, token)
        accounts[accountId] = client
        return client
    }

    fun forget(accountId: Long) {
        accounts.remove(accountId)
    }

    private fun retrofit(address: ServerAddress, http: OkHttpClient): RengaineApi =
        Retrofit.Builder()
            .baseUrl(address.apiBase)
            .client(http)
            .addConverterFactory(ApiJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(RengaineApi::class.java)
}
