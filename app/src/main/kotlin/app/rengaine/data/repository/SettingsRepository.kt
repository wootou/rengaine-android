package app.rengaine.data.repository

import android.content.Context
import android.util.Log
import app.rengaine.data.remote.ApiError
import app.rengaine.data.remote.ApiJson
import app.rengaine.data.remote.ApiResult
import app.rengaine.data.remote.apiCall
import app.rengaine.data.remote.dto.AccountSettings
import app.rengaine.data.remote.dto.etagOf
import app.rengaine.data.remote.dto.with
import app.rengaine.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Réglages du compte actif (F12), partagés avec le site.
 *
 * - **Lus** au changement de compte et à l'ouverture de l'écran Réglages.
 *   Une copie locale par compte (`filesDir/settings`) donne le thème dès
 *   le démarrage, même sans réseau.
 * - **Écrits** un par un ([set]) : la valeur s'affiche tout de suite, puis
 *   part au serveur avec `If-Match`.
 * - **Sur `412`, l'inverse de la file** (règle du site) : on repart de la
 *   version du serveur (`details.current`) et on y réapplique sa seule
 *   modification, pour ne pas écraser ce qu'un autre appareil a changé.
 * - En cas d'échec, la valeur affichée revient à la dernière confirmée.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val accounts: AccountRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val dir = File(context.filesDir, "settings")
    private val mutex = Mutex()
    private var accountId: Long? = null

    private val _settings = MutableStateFlow<AccountSettings?>(null)
    /** Réglages du compte actif ; `null` sans compte ou avant la première lecture. */
    val settings: StateFlow<AccountSettings?> = _settings.asStateFlow()

    private val _ready = MutableStateFlow(false)
    /** Copie locale lue : le thème est connu (l'écran de démarrage attend ce moment). */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    init {
        scope.launch {
            accounts.activeAccount.map { it?.id }.distinctUntilChanged().collect { switchTo(it) }
        }
        scope.launch {
            accounts.accounts.collect { list -> keepOnly(list.map { it.id }.toSet()) }
        }
    }

    private suspend fun switchTo(id: Long?) {
        mutex.withLock {
            accountId = id
            _settings.value = id?.let { load(it) }
            _ready.value = true
        }
        if (id != null) refresh()
    }

    /** Relit les réglages sur le serveur (sans effet hors ligne). */
    suspend fun refresh() = mutex.withLock {
        val id = accountId ?: return@withLock
        val session = sessionFor(id) ?: return@withLock
        val fresh = fetch(session) ?: return@withLock
        if (accountId == id) accept(id, fresh)
    }

    /**
     * Change un réglage. Renvoie `false` s'il n'a pas pu être enregistré
     * (hors ligne, serveur indisponible) : l'affichage est alors rétabli.
     */
    suspend fun set(path: List<String>, value: JsonElement): Boolean = mutex.withLock {
        val id = accountId ?: return@withLock false
        val confirmed = _settings.value
        confirmed?.let { _settings.value = it.copy(raw = it.raw.with(path, value)) }

        val saved = sessionFor(id)?.let { session -> save(session, confirmed, path, value) }
        if (saved == null) {
            if (accountId == id) _settings.value = confirmed
            return@withLock false
        }
        if (accountId == id) accept(id, saved)
        true
    }

    suspend fun set(path: List<String>, value: Boolean) = set(path, JsonPrimitive(value))
    suspend fun set(path: List<String>, value: String) = set(path, JsonPrimitive(value))
    /** Nombre ; `null` s'écrit `null` (« pas de contrainte »). */
    suspend fun set(path: List<String>, value: Number?) = set(path, value?.let(::JsonPrimitive) ?: JsonNull)

    private suspend fun save(session: ActiveSession, known: AccountSettings?, path: List<String>, value: JsonElement): AccountSettings? {
        val base = known ?: fetch(session) ?: return null
        val first = put(session, base, path, value)
        val error = (first as? ApiResult.Err)?.error as? ApiError.Http
        val result = if (error?.status == 412) {
            // Changés ailleurs : on repart de leur version et on n'y remet que ce réglage-ci.
            val current = AccountSettings.fromDocument(error.details?.get("current") as? JsonObject)
                ?: fetch(session) ?: return null
            put(session, current, path, value)
        } else {
            first
        }
        return when (result) {
            is ApiResult.Ok -> AccountSettings.fromDocument(result.value)
            is ApiResult.Err -> {
                Log.w(TAG, "Réglage non enregistré : ${result.error.javaClass.simpleName}")
                null
            }
        }
    }

    private suspend fun put(session: ActiveSession, base: AccountSettings, path: List<String>, value: JsonElement) =
        apiCall { session.api.putSettings(etagOf(base.version), base.raw.with(path, value)) }

    private suspend fun fetch(session: ActiveSession): AccountSettings? =
        when (val r = apiCall { session.api.getSettings() }) {
            is ApiResult.Ok -> AccountSettings.fromDocument(r.value)
            is ApiResult.Err -> null
        }

    private suspend fun sessionFor(id: Long): ActiveSession? =
        accounts.activeSession()?.takeIf { it.account.id == id && !it.account.needsReauth }

    private suspend fun accept(id: Long, settings: AccountSettings) {
        _settings.value = settings
        store(id, settings)
    }

    // --- Copie locale ----------------------------------------------------------

    private suspend fun load(id: Long): AccountSettings? = withContext(Dispatchers.IO) {
        runCatching {
            AccountSettings.fromDocument(ApiJson.decodeFromString(JsonObject.serializer(), file(id).readText()))
        }.getOrNull()
    }

    private suspend fun store(id: Long, settings: AccountSettings) = withContext(Dispatchers.IO) {
        runCatching {
            dir.mkdirs()
            val doc = buildJsonObject {
                put("version", settings.version)
                put("settings", settings.raw)
            }
            // Fichier temporaire puis renommage : jamais de copie à moitié écrite.
            val tmp = File(dir, "$id.json.tmp")
            tmp.writeText(ApiJson.encodeToString(JsonObject.serializer(), doc))
            tmp.renameTo(file(id))
        }
    }

    /** Les réglages d'un compte retiré ou déconnecté ne restent pas sur le téléphone. */
    private suspend fun keepOnly(ids: Set<Long>) = withContext(Dispatchers.IO) {
        dir.listFiles()?.forEach { f ->
            val id = f.name.substringBefore('.').toLongOrNull()
            if (id == null || id !in ids) f.delete()
        }
    }

    private fun file(id: Long) = File(dir, "$id.json")

    private companion object {
        const val TAG = "Rengaine/Réglages"
    }
}
