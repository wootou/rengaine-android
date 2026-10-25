package app.rengaine.data.repository

import android.content.Context
import android.util.Log
import app.rengaine.data.remote.ApiError
import app.rengaine.data.remote.ApiJson
import app.rengaine.data.remote.ApiResult
import app.rengaine.data.remote.apiCall
import app.rengaine.data.remote.apiCallUnit
import app.rengaine.data.remote.dto.PlayHistoryPageDto
import app.rengaine.data.remote.dto.PlayInputDto
import app.rengaine.data.remote.dto.RecordPlaysDto
import app.rengaine.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Historique d'écoute (F11) : chaque écoute est d'abord écrite sur le
 * téléphone, puis envoyée par lots de 500 au plus (`POST /me/history`).
 * Hors ligne ou après un échec, elle attend le prochain envoi : rien ne se
 * perd, même si l'app est fermée entre-temps.
 *
 * Une écoute appartient au compte actif **quand elle a commencé** ; celles
 * d'un compte qui n'est plus actif partent quand même (`apiFor`). Celles
 * d'un compte retiré du téléphone sont effacées avec lui.
 *
 * Un fichier JSON par compte plutôt qu'une table Room : comme la file
 * (`QueueStore`), cela évite une migration de la base des comptes.
 */
/** Écoute en cours gardée sur le téléphone, avec son compte. */
@Serializable
private data class InProgressListen(val accountId: Long, val play: PlayInputDto)

@Singleton
class HistoryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accounts: AccountRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val dir = File(context.filesDir, "history")
    private val mutex = Mutex()
    private var pendingFlush: Job? = null
    private var started = false

    /** Compte actif, lu sans attendre par le compteur d'écoutes du lecteur. */
    val activeAccountId: StateFlow<Long?> = accounts.activeAccount.map { it?.id }.stateIn(scope, SharingStarted.Eagerly, null)

    /** Au démarrage : envoie ce qui attend, et oublie les comptes retirés. */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            // Envoi après la récupération : lancé en parallèle, il passait
            // avant et l'écoute retrouvée attendait la suivante.
            recoverInProgress()
            scheduleFlush(0)
            accounts.accounts.collect { list -> keepOnly(list.map { it.id }.toSet()) }
        }
    }

    /** Une écoute terminée (5 s jouées au moins) : gardée, puis envoyée bientôt. */
    fun record(accountId: Long, play: PlayInputDto) {
        scope.launch {
            mutex.withLock {
                write(accountId, read(accountId) + play)
                // Sa copie « en cours » n'a plus lieu d'être (pas celle d'une
                // écoute suivante, enregistrée entre-temps).
                if (readInProgress()?.second?.sameListen(play) == true) writeInProgress(null)
            }
            scheduleFlush(FLUSH_DELAY_MS)
        }
    }

    /**
     * Écoute en cours, gardée toutes les quelques secondes par le lecteur :
     * si Android tue l'app (mémoire, arrêt forcé), elle est retrouvée au
     * démarrage suivant et envoyée comme une écoute interrompue
     * (`completed = false`), au lieu d'être perdue. `null` : aucune.
     */
    fun saveInProgress(listen: Pair<Long, PlayInputDto>?) {
        scope.launch { mutex.withLock { writeInProgress(listen) } }
    }

    /** Écoute laissée en cours par une app tuée : elle rejoint celles à envoyer. */
    private suspend fun recoverInProgress() = mutex.withLock {
        val (accountId, play) = readInProgress() ?: return@withLock
        write(accountId, read(accountId) + play)
        writeInProgress(null)
        Log.i(TAG, "Écoute interrompue retrouvée (${play.playedMs} ms)")
    }

    private fun PlayInputDto.sameListen(other: PlayInputDto) = trackId == other.trackId && startedAt == other.startedAt

    private fun scheduleFlush(delayMs: Long) {
        pendingFlush?.cancel()
        pendingFlush = scope.launch {
            delay(delayMs)
            if (!flush()) {
                // Hors ligne ou serveur indisponible : on réessaiera plus tard.
                delay(RETRY_DELAY_MS)
                scheduleFlush(0)
            }
        }
    }

    /** Envoie tout ce qui attend ; `false` s'il reste des écoutes à cause d'une erreur passagère. */
    suspend fun flush(): Boolean {
        val ids = withContext(Dispatchers.IO) { dir.listFiles()?.mapNotNull { it.name.removeSuffix(".json").toLongOrNull() } }.orEmpty()
        var ok = true
        for (id in ids) ok = flushAccount(id) && ok
        return ok
    }

    private suspend fun flushAccount(accountId: Long): Boolean = mutex.withLock {
        var pending = read(accountId)
        if (pending.isEmpty()) return@withLock true
        // Compte à reconnecter : ses écoutes attendent qu'il le soit.
        val api = accounts.apiFor(accountId) ?: return@withLock true
        while (pending.isNotEmpty()) {
            val batch = pending.take(BATCH)
            when (val r = apiCallUnit { api.recordPlays(RecordPlaysDto(batch)) }) {
                is ApiResult.Ok -> Log.i(TAG, "${batch.size} écoute(s) envoyée(s)")
                is ApiResult.Err -> {
                    val http = r.error as? ApiError.Http
                    // Refusé pour de bon (`400`, historique absent du serveur) :
                    // ce lot ne passera jamais, on ne bloque pas les suivants.
                    if (http == null || http.status == 401 || http.status == 429 || http.status >= 500) {
                        write(accountId, pending)
                        Log.w(TAG, "Écoutes non envoyées : ${r.error.javaClass.simpleName}")
                        return@withLock false
                    }
                    Log.w(TAG, "Lot de ${batch.size} écoute(s) refusé (${http.status} ${http.code}), abandonné")
                }
            }
            pending = pending.drop(batch.size)
            write(accountId, pending)
        }
        true
    }

    // --- Page « Historique d'écoute » (2.26.0) ---------------------------------------

    /** Une page de l'historique du compte actif ; adresses rendues absolues. */
    suspend fun page(offset: Int, limit: Int = PAGE): ApiResult<PlayHistoryPageDto> {
        val session = accounts.activeSession() ?: return ApiResult.Err(ApiError.Network(java.io.IOException("Aucun compte")))
        val address = session.account.address
        return when (val r = apiCall { session.api.listHistory(limit, offset) }) {
            is ApiResult.Ok -> ApiResult.Ok(r.value.copy(items = r.value.items.map { it.copy(track = it.track.resolved(address)) }))
            is ApiResult.Err -> r
        }
    }

    /** Efface une écoute ; une écoute déjà absente (`404`) compte comme effacée. */
    suspend fun delete(id: Long): Boolean {
        val session = accounts.activeSession() ?: return false
        return when (val r = apiCallUnit { session.api.deletePlay(id) }) {
            is ApiResult.Ok -> true
            is ApiResult.Err -> (r.error as? ApiError.Http)?.status == 404
        }
    }

    /** Efface tout l'historique du compte actif ; `null` en cas d'échec. */
    suspend fun clear(): Int? {
        val session = accounts.activeSession() ?: return null
        return (apiCall { session.api.clearHistory() } as? ApiResult.Ok)?.value?.deleted
    }

    // --- Fichiers ------------------------------------------------------------------

    private suspend fun read(accountId: Long): List<PlayInputDto> = withContext(Dispatchers.IO) {
        runCatching { ApiJson.decodeFromString(RecordPlaysDto.serializer(), file(accountId).readText()).plays }.getOrDefault(emptyList())
    }

    private suspend fun write(accountId: Long, plays: List<PlayInputDto>) = withContext(Dispatchers.IO) {
        runCatching {
            if (plays.isEmpty()) {
                file(accountId).delete()
                return@runCatching
            }
            dir.mkdirs()
            // Fichier temporaire puis renommage : jamais d'écoutes à moitié écrites.
            val tmp = File(dir, "$accountId.json.tmp")
            tmp.writeText(ApiJson.encodeToString(RecordPlaysDto.serializer(), RecordPlaysDto(plays.takeLast(MAX_PENDING))))
            tmp.renameTo(file(accountId))
        }
    }

    private suspend fun keepOnly(ids: Set<Long>) = mutex.withLock {
        withContext(Dispatchers.IO) {
            dir.listFiles()?.forEach { f ->
                val id = f.name.substringBefore('.').toLongOrNull()
                if (id == null || id !in ids) f.delete()
            }
        }
    }

    private fun file(accountId: Long) = File(dir, "$accountId.json")

    /** Hors de [dir], dont les fichiers sans numéro de compte sont effacés. */
    private val inProgressFile = File(context.filesDir, "history-in-progress.json")

    private suspend fun readInProgress(): Pair<Long, PlayInputDto>? = withContext(Dispatchers.IO) {
        runCatching {
            val saved = ApiJson.decodeFromString(InProgressListen.serializer(), inProgressFile.readText())
            saved.accountId to saved.play
        }.getOrNull()
    }

    private suspend fun writeInProgress(listen: Pair<Long, PlayInputDto>?) = withContext(Dispatchers.IO) {
        runCatching {
            if (listen == null) {
                inProgressFile.delete()
                return@runCatching
            }
            val tmp = File(inProgressFile.path + ".tmp")
            tmp.writeText(ApiJson.encodeToString(InProgressListen.serializer(), InProgressListen(listen.first, listen.second)))
            tmp.renameTo(inProgressFile)
        }
    }

    /** Pour les tests et le journal : nombre d'écoutes en attente. */
    suspend fun pendingCount(accountId: Long): Int = mutex.withLock { read(accountId).size }

    private companion object {
        const val TAG = "Rengaine/Historique"
        const val BATCH = 500
        const val PAGE = 100
        /** Comme le site : envoi à chaque écoute terminée (le court délai regroupe les rafales). */
        const val FLUSH_DELAY_MS = 1_000L
        const val RETRY_DELAY_MS = 5 * 60_000L
        /** Garde-fou : des mois hors ligne ne doivent pas remplir le téléphone. */
        const val MAX_PENDING = 20_000
    }
}
