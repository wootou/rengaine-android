package app.rengaine.data.remote

import app.rengaine.data.repository.AccountRepository
import app.rengaine.di.ApplicationScope
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Point d'entrée HTTP du lecteur (flux) et des pochettes (Coil, notification).
 *
 * Ces adresses sont absolues (`https://serveur/api/v2/tracks/12/stream`) et
 * ne disent pas de quel compte elles viennent : on choisit le client
 * d'après la racine du serveur, **le compte actif d'abord**. Le jeton n'est
 * donc posé que sur une adresse du serveur qui l'a émis, jamais ailleurs ;
 * une adresse inconnue part sans jeton.
 *
 * Le client choisi est celui du compte (`ApiClients.forAccount`) : un `401`
 * sur un flux marque ce compte « à reconnecter », comme pour l'API.
 *
 * [newCall] peut bloquer brièvement (lecture du compte en base, puis
 * mise en cache) : il n'est appelé que hors du thread principal (chargeur
 * de Media3, répartiteur IO de Coil).
 */
@Singleton
class AuthorizedCallFactory @Inject constructor(
    private val baseClient: OkHttpClient,
    private val accounts: AccountRepository,
    @ApplicationScope scope: CoroutineScope,
) : Call.Factory {

    private data class Entry(val accountId: Long, val root: String)

    @Volatile
    private var snapshot: List<Entry>? = null
    private val clients = ConcurrentHashMap<Long, OkHttpClient>()

    init {
        scope.launch {
            combine(accounts.accounts, accounts.activeAccount) { list, active ->
                list.sortedByDescending { it.id == active?.id }
                    .map { Entry(it.id, it.address.root) }
            }.collect {
                // Un compte ajouté, retiré ou reconnecté (nouveau jeton) :
                // on repart de clients frais.
                clients.clear()
                snapshot = it
            }
        }
    }

    override fun newCall(request: Request): Call {
        val url = request.url.toString()
        val entries = snapshot ?: runBlocking { currentEntries() }
        val entry = entries.firstOrNull { url == it.root || url.startsWith(it.root + "/") }
            ?: return baseClient.newCall(request)
        val client = clients[entry.accountId]
            ?: runBlocking { accounts.httpClientFor(entry.accountId) }?.also { clients[entry.accountId] = it }
            ?: baseClient
        return client.newCall(request)
    }

    private suspend fun currentEntries(): List<Entry> {
        val active = accounts.activeAccount.first()
        return accounts.accounts.first()
            .sortedByDescending { it.id == active?.id }
            .map { Entry(it.id, it.address.root) }
    }
}
