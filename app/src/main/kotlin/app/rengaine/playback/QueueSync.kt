package app.rengaine.playback

import android.util.Log
import androidx.media3.common.Player
import app.rengaine.data.remote.ApiError
import app.rengaine.data.remote.ApiResult
import app.rengaine.data.remote.ServerAddress
import app.rengaine.data.remote.apiCall
import app.rengaine.data.remote.dto.PlayQueueDto
import app.rengaine.data.remote.dto.PlayQueueInputDto
import app.rengaine.data.remote.dto.QueueModes
import app.rengaine.data.repository.AccountRepository
import app.rengaine.data.repository.ActiveSession
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * File d'attente synchronisée avec le serveur (F6), sur les règles du site :
 *
 * - **Lue à l'ouverture seulement** (et au changement de compte) : la file
 *   du compte revient dans le lecteur, en pause, sur la bonne piste et à la
 *   bonne position. Sans réseau, on reprend la copie locale ([QueueStore]).
 * - **Envoyée à chaque changement** (après un court délai, pour regrouper
 *   les gestes) et **toutes les 30 s** pendant la lecture, pour la position.
 * - **Sur `412`, l'appareil qui joue a raison** : on reprend la version
 *   donnée par le serveur et on renvoie sa propre file.
 *
 * Rien n'est envoyé avant la fin de la reprise : une file encore vide au
 * démarrage n'écrase jamais celle du serveur.
 */
@Singleton
class QueueSync @Inject constructor(
    private val accounts: AccountRepository,
    private val player: PlayerController,
    private val store: QueueStore,
) {
    // Thread principal : le contrôleur du lecteur n'accepte que lui.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private var pendingSave: Job? = null

    /** Compte dont le lecteur tient la file. */
    private var accountId: Long? = null
    /** Reprise terminée pour [accountId] : les envois sont permis. */
    private var ready = false
    private var etag: String? = null
    /** Dernier état connu du serveur, pour ne rien renvoyer d'identique. */
    private var lastSent: PlayQueueInputDto? = null
    /** `continuous` vient de la lecture continue, que le lecteur ne connaît pas encore (F5) : on le garde. */
    private var serverMode: String = QueueModes.NORMAL
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            player.connected.first { it }
            accounts.activeAccount
                .map { account -> account?.takeUnless { it.needsReauth }?.id }
                .distinctUntilChanged()
                .collect { switchTo(it) }
        }
        scope.launch { player.queueChanges.collect { scheduleSave(CHANGE_DELAY_MS) } }
        scope.launch {
            while (true) {
                delay(POSITION_INTERVAL_MS)
                if (player.snapshot()?.isPlaying == true) scheduleSave(0)
            }
        }
        // Une file locale ne survit pas au compte : déconnexion ou retrait l'effacent.
        scope.launch { accounts.accounts.collect { list -> store.keepOnly(list.map { it.id }.toSet()) } }
    }

    private suspend fun switchTo(id: Long?) {
        pendingSave?.cancel()
        mutex.withLock {
            val previous = accountId
            ready = false
            accountId = id
            etag = null
            lastSent = null
            serverMode = QueueModes.NORMAL
            // La file appartient au compte : on ne la laisse pas jouer sous un autre.
            if (previous != null && previous != id) player.stop()
        }
        if (id != null) restore(id)
    }

    private suspend fun restore(id: Long) {
        val session = accounts.activeSession()?.takeIf { it.account.id == id }
        val fromServer = session?.let { s ->
            when (val result = apiCall { s.api.getQueue() }) {
                is ApiResult.Ok -> StoredQueue(result.etag, result.value.resolved(s.account.address))
                is ApiResult.Err -> null
            }
        }
        if (fromServer != null) store.save(id, fromServer)
        val stored = fromServer ?: store.load(id)

        mutex.withLock {
            if (accountId != id) return
            if (stored != null) {
                etag = stored.etag
                serverMode = stored.queue.mode
                lastSent = stored.queue.toInput()
                // Si l'on a déjà lancé autre chose pendant la reprise, c'est
                // ce choix qui compte : il partira au prochain envoi.
                if (player.snapshot()?.trackIds.isNullOrEmpty()) apply(stored.queue)
            }
            ready = true
            Log.i(TAG, "File reprise : ${stored?.queue?.trackIds?.size ?: 0} pistes (${if (fromServer != null) "serveur" else "copie locale"})")
        }
        scheduleSave(CHANGE_DELAY_MS)
    }

    /** Remet la file dans le lecteur, sans les pistes devenues illisibles. */
    private fun apply(queue: PlayQueueDto) {
        val current = queue.currentIndex ?: 0
        val items = ArrayList<androidx.media3.common.MediaItem>()
        var startIndex = 0
        queue.tracks.forEachIndexed { i, track ->
            val item = MediaItems.fromTrack(track) ?: return@forEachIndexed
            if (i < current) startIndex++
            items += item
        }
        if (items.isEmpty()) return
        val sameTrack = queue.tracks.getOrNull(current)?.available == true
        player.load(
            items = items,
            index = startIndex.coerceAtMost(items.lastIndex),
            positionMs = if (sameTrack) queue.positionMs else 0,
            repeatMode = repeatModeOf(queue.mode),
        )
    }

    private fun scheduleSave(delayMs: Long) {
        pendingSave?.cancel()
        pendingSave = scope.launch {
            delay(delayMs)
            save()
        }
    }

    private suspend fun save() = mutex.withLock {
        val id = accountId ?: return@withLock
        if (!ready) return@withLock
        val snapshot = player.snapshot() ?: return@withLock
        val input = snapshot.toInput()
        if (lastSent?.sameAs(input) == true) return@withLock
        // Une file vide ne part que si elle a été vidée ici : sans état
        // connu du serveur (reprise ratée), elle effacerait la sienne.
        if (input.trackIds.isEmpty() && lastSent == null) return@withLock
        val session = accounts.activeSession()?.takeIf { it.account.id == id } ?: return@withLock

        val tag = etag ?: fetchEtag(session) ?: return@withLock
        var result = apiCall { session.api.putQueue(tag, input.toJson()) }
        val error = (result as? ApiResult.Err)?.error as? ApiError.Http
        if (error?.status == 412) {
            // Changée ailleurs entre-temps : l'appareil qui joue a raison.
            val current = error.details?.get("current")?.jsonObject?.get("version")?.jsonPrimitive?.longOrNull
            val retryTag = current?.let { "\"$it\"" } ?: fetchEtag(session) ?: return@withLock
            result = apiCall { session.api.putQueue(retryTag, input.toJson()) }
        }
        when (result) {
            is ApiResult.Ok -> {
                etag = result.etag ?: "\"${result.value.version}\""
                lastSent = input
                store.save(id, StoredQueue(etag, result.value.resolved(session.account.address)))
            }
            // Hors ligne ou serveur indisponible : on réessaiera au prochain
            // changement ou au prochain envoi périodique.
            is ApiResult.Err -> Log.w(TAG, "File non envoyée : ${result.error.javaClass.simpleName}")
        }
    }

    private suspend fun fetchEtag(session: ActiveSession): String? =
        when (val result = apiCall { session.api.getQueue() }) {
            is ApiResult.Ok -> result.etag ?: "\"${result.value.version}\""
            is ApiResult.Err -> null
        }

    private fun QueueSnapshot.toInput(): PlayQueueInputDto {
        // Plafond du contrat : on garde une fenêtre autour de la piste en cours.
        var ids = trackIds
        var index = currentIndex
        if (ids.size > QueueModes.MAX_LENGTH) {
            val from = ((index ?: 0) - 100).coerceIn(0, ids.size - QueueModes.MAX_LENGTH)
            ids = ids.subList(from, from + QueueModes.MAX_LENGTH)
            index = index?.minus(from)
        }
        return PlayQueueInputDto(
            trackIds = ids,
            currentIndex = index,
            positionMs = if (index == null) 0 else positionMs,
            mode = modeOf(repeatMode),
        )
    }

    private fun modeOf(@Player.RepeatMode repeatMode: Int): String = when (repeatMode) {
        Player.REPEAT_MODE_ALL -> QueueModes.REPEAT_ALL
        Player.REPEAT_MODE_ONE -> QueueModes.REPEAT_ONE
        else -> if (serverMode == QueueModes.CONTINUOUS) QueueModes.CONTINUOUS else QueueModes.NORMAL
    }

    private fun repeatModeOf(mode: String): Int = when (mode) {
        QueueModes.REPEAT_ALL -> Player.REPEAT_MODE_ALL
        QueueModes.REPEAT_ONE -> Player.REPEAT_MODE_ONE
        else -> Player.REPEAT_MODE_OFF
    }

    private fun PlayQueueDto.toInput() = PlayQueueInputDto(trackIds, currentIndex, positionMs, mode)

    /** Même file, même piste, même mode, et une position à moins de 2 s près. */
    private fun PlayQueueInputDto.sameAs(other: PlayQueueInputDto) =
        trackIds == other.trackIds && currentIndex == other.currentIndex && mode == other.mode &&
            abs(positionMs - other.positionMs) < POSITION_TOLERANCE_MS

    private fun PlayQueueDto.resolved(address: ServerAddress) = copy(
        tracks = tracks.map { t ->
            t.copy(
                coverUrl = t.coverUrl?.let(address::resolve),
                streamUrl = t.streamUrl.takeIf { it.isNotBlank() }?.let(address::resolve).orEmpty(),
            )
        },
    )

    private companion object {
        const val TAG = "Rengaine/File"
        const val CHANGE_DELAY_MS = 2_000L
        const val POSITION_INTERVAL_MS = 30_000L
        const val POSITION_TOLERANCE_MS = 2_000L
    }
}
