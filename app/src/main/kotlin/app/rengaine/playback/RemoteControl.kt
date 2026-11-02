package app.rengaine.playback

import android.content.Context
import android.util.Log
import app.rengaine.R
import app.rengaine.data.local.SessionStore
import app.rengaine.data.remote.ApiError
import app.rengaine.data.remote.ApiJson
import app.rengaine.data.remote.ApiResult
import app.rengaine.data.remote.apiCall
import app.rengaine.data.remote.apiCallUnit
import app.rengaine.data.remote.dto.PlayerReportDto
import app.rengaine.data.remote.dto.RealtimeChannelDto
import app.rengaine.data.remote.dto.RealtimeMessageDto
import app.rengaine.data.remote.dto.RemoteCommandDto
import app.rengaine.data.remote.dto.TrackDto
import app.rengaine.data.repository.AccountRepository
import app.rengaine.data.repository.ServerFeatures
import app.rengaine.data.repository.resolved
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Télécommande partagée, côté lecteur (FUT-02), comme `engine/remote.js`
 * du site. Tant qu'un compte est actif et que le serveur a un hub temps réel,
 * l'app écoute son canal Mercure :
 *  - `command` : une commande d'invité, appliquée seulement si elle vise ce
 *    téléphone (`target` = [deviceKey]) ; la file est enregistrée aussitôt
 *    pour que l'invité voie le résultat ;
 *  - `shares` : un lien a été créé ou révoqué (ici ou sur le site) ;
 *  - `disabled` : l'admin a coupé la télécommande.
 *
 * Tant qu'un lien est valable, l'app donne des nouvelles au serveur
 * (`PUT /me/player` : piste, lecture, version de la file, volume) à chaque
 * changement et toutes les 30 s : c'est ce que voient les invités, et c'est
 * ainsi que le serveur sait quel appareil joue. Le tout vit dans le
 * processus de l'app, que le service de lecture garde en vie pendant la
 * lecture, écran verrouillé compris.
 */
@Singleton
class RemoteControl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accounts: AccountRepository,
    private val features: ServerFeatures,
    private val player: PlayerController,
    private val queueSync: QueueSync,
    private val store: SessionStore,
    baseClient: OkHttpClient,
) {
    /** [sharesActive] : au moins un lien valable. [lastAction] : « Paul : pause ». */
    data class Status(val sharesActive: Boolean = false, val lastAction: String? = null)

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Client du flux SSE : sans délai de lecture (le hub peut se taire
     * longtemps) et sans le jeton du compte — le hub n'accepte que le jeton
     * Mercure de `GET /me/realtime`.
     */
    private val sseClient = baseClient.newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).build()

    private var deviceKey: String? = null
    private var started = false
    private var lastReport = ""
    private var reporting = false
    private var again = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            combine(
                accounts.activeAccount.map { it?.id },
                features.features.map { it?.remoteControl == true },
                ::Pair,
            ).distinctUntilChanged().collectLatest { (id, enabled) ->
                _status.value = Status()
                stopped = false
                if (id == null || !enabled) return@collectLatest
                deviceKey = store.remoteDeviceKey()
                // Nouvelles toutes les 30 s, et à chaque changement de l'état du lecteur.
                val heartbeat = launch {
                    while (isActive) {
                        delay(REPORT_EVERY_MS)
                        report(force = true)
                    }
                }
                val watcher = launch {
                    player.state.collect { report(force = false) }
                }
                try {
                    subscribe()
                } finally {
                    heartbeat.cancel()
                    watcher.cancel()
                }
            }
        }
    }

    /** Un lien vient d'être créé ou révoqué ici. */
    fun setActive(active: Boolean) {
        val was = _status.value.sharesActive
        _status.value = _status.value.copy(sharesActive = active, lastAction = _status.value.lastAction.takeIf { active })
        if (active && !was) scope.launch { report(force = true) }
    }

    // --- Canal temps réel --------------------------------------------------------------

    /**
     * Comme `Subscription` (`sse.js`) : un canal frais à chaque connexion ;
     * le hub ferme le flux à l'expiration du jeton (15 min) et l'on se
     * reconnecte aussitôt ; échec avant ouverture : 1 s, doublé jusqu'à 30 s ;
     * `401`, `501`, `503` : on abandonne.
     */
    private suspend fun subscribe() {
        var wait = 1_000L
        while (coroutineContext.isActive) {
            val session = accounts.activeSession() ?: return
            val channel = when (val r = apiCall { session.api.realtime() }) {
                is ApiResult.Ok -> r.value
                is ApiResult.Err -> {
                    val status = (r.error as? ApiError.Http)?.status
                    if (status == 401 || status == 501 || status == 503) return
                    delay(wait)
                    wait = (wait * 2).coerceAtMost(30_000)
                    continue
                }
            }
            setActive(channel.sharesActive)
            val opened = runCatching { stream(channel) }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                .getOrDefault(false)
            if (stopped) return
            if (opened) {
                wait = 1_000
            } else {
                delay(wait)
                wait = (wait * 2).coerceAtMost(30_000)
            }
        }
    }

    /** L'admin a coupé la télécommande : plus d'abonnement jusqu'au prochain démarrage. */
    private var stopped = false

    /** Lit le flux SSE jusqu'à sa fermeture. Vrai s'il a été ouvert. */
    private suspend fun stream(channel: RealtimeChannelDto): Boolean = withContext(Dispatchers.IO) {
        val url = channel.hubUrl.toHttpUrlOrNull()?.newBuilder()?.apply {
            channel.topics.forEach { addQueryParameter("match", it) }
        }?.build() ?: return@withContext false
        val call = sseClient.newCall(
            Request.Builder().url(url)
                .header("Authorization", "Bearer ${channel.token}")
                .header("Accept", "text/event-stream")
                .build(),
        )
        // Le flux bloque la lecture : on le coupe quand la coroutine s'arrête.
        val handle = coroutineContext.job.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                withContext(Dispatchers.Main) { report(force = true) } // messages manqués pendant la coupure
                val source = response.body?.source() ?: return@withContext true
                val data = StringBuilder()
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    when {
                        line.isEmpty() -> if (data.isNotEmpty()) {
                            val text = data.toString()
                            data.clear()
                            withContext(Dispatchers.Main) { onMessage(text) }
                            if (stopped) break
                        }
                        line.startsWith("data:") -> {
                            if (data.isNotEmpty()) data.append('\n')
                            data.append(line.removePrefix("data:").removePrefix(" "))
                        }
                    }
                }
                true
            }
        } finally {
            handle.dispose()
        }
    }

    private suspend fun onMessage(text: String) {
        val message = runCatching { ApiJson.decodeFromString(RealtimeMessageDto.serializer(), text) }.getOrNull() ?: return
        when (message.type) {
            "disabled" -> {
                stopped = true
                _status.value = Status()
            }
            "shares" -> {
                val session = accounts.activeSession() ?: return
                (apiCall { session.api.realtime() } as? ApiResult.Ok)?.let { setActive(it.value.sharesActive) }
            }
            "command" -> {
                val command = message.command ?: return
                if (message.target != deviceKey) return
                val done = apply(command, message.tracks)
                Log.i(TAG, "Télécommande : ${command.type} → ${if (done == null) "ignorée" else "appliquée"}")
                if (done != null) {
                    val who = message.by?.takeIf { it.isNotBlank() } ?: context.getString(R.string.remote_a_guest)
                    _status.value = _status.value.copy(lastAction = context.getString(R.string.remote_last_action, who, done))
                    queueSync.saveNow()
                }
                // Appliquée ou non, l'invité relit l'état et voit ce qui a changé.
                report(force = true)
            }
        }
    }

    // --- Commandes ---------------------------------------------------------------------

    /** `applyCommand` du site : ce qui a été fait, en clair, ou `null` si la commande est ignorée. */
    private suspend fun apply(c: RemoteCommandDto, tracks: List<TrackDto>): String? {
        val state = player.state.value
        return when (c.type) {
            "add" -> {
                val address = accounts.activeSession()?.account?.address ?: return null
                val items = tracks.filter { it.available }.mapNotNull { MediaItems.fromTrack(it.resolved(address)) }
                if (items.isEmpty()) return null
                when (c.where) {
                    "now" -> {
                        val was = state.count
                        player.playNext(items)
                        // File vide : la piste ajoutée est déjà en cours et se lance.
                        if (was > 0) player.playAt(state.index + 1)
                        context.getString(R.string.remote_started, tracks.first { it.available }.title)
                    }
                    "next" -> {
                        player.playNext(items)
                        context.resources.getQuantityString(R.plurals.remote_added_next, items.size, items.size)
                    }
                    else -> {
                        player.addToQueue(items)
                        context.resources.getQuantityString(R.plurals.remote_added_end, items.size, items.size)
                    }
                }
            }
            "move" -> {
                val from = c.from ?: return null
                val to = c.to ?: return null
                if (player.trackIdAt(from) != c.trackId) return null
                val title = player.titleAt(from).orEmpty()
                player.move(from, to.coerceAtMost(state.count - 1))
                context.getString(R.string.remote_moved, title)
            }
            "remove" -> {
                val index = c.index ?: return null
                if (player.trackIdAt(index) != c.trackId) return null
                val title = player.titleAt(index).orEmpty()
                player.removeAt(index)
                context.getString(R.string.remote_removed, title)
            }
            "jump" -> {
                val index = c.index ?: return null
                if (player.trackIdAt(index) != c.trackId) return null
                val title = player.titleAt(index).orEmpty()
                player.playAt(index)
                context.getString(R.string.remote_started, title)
            }
            "play" -> {
                if (state.trackId == null || state.isPlaying) return null
                player.play()
                context.getString(R.string.remote_play)
            }
            "pause" -> {
                if (!state.isPlaying) return null
                player.pause()
                context.getString(R.string.remote_pause)
            }
            "next" -> {
                if (state.trackId == null) return null
                player.skipNext()
                context.getString(R.string.remote_next)
            }
            "volume" -> {
                val v = c.volume?.coerceIn(0, 100) ?: return null
                player.setVolumePercent(v)
                context.getString(R.string.remote_volume, v)
            }
            else -> null
        }
    }

    // --- État envoyé aux invités ----------------------------------------------------

    /**
     * `PUT /me/player`, seulement si un lien est valable et la file reprise.
     * Sans [force], seulement si piste, lecture, version ou volume ont
     * changé (la position seule ne déclenche rien). Un seul envoi à la fois ;
     * un changement arrivé pendant un envoi repart après.
     */
    private suspend fun report(force: Boolean) {
        if (!_status.value.sharesActive || !queueSync.isReady) return
        val key = deviceKey ?: return
        val s = player.state.value
        val body = PlayerReportDto(
            deviceKey = key,
            trackId = s.trackId,
            playing = s.isPlaying,
            positionMs = s.positionMs.coerceAtLeast(0),
            queueVersion = queueSync.knownVersion(),
            volume = player.volumePercent(),
        )
        val signature = "${body.trackId}|${body.playing}|${body.queueVersion}|${body.volume}"
        if (!force && signature == lastReport) return
        if (reporting) {
            again = true
            return
        }
        lastReport = signature
        reporting = true
        try {
            val session = accounts.activeSession() ?: return
            val r = apiCallUnit { session.api.reportPlayer(body) }
            if (r is ApiResult.Err) Log.w(TAG, "Télécommande : état non envoyé (${r.error.javaClass.simpleName})")
        } finally {
            reporting = false
        }
        if (again) {
            again = false
            report(force = true)
        }
    }

    private companion object {
        const val TAG = "Rengaine/Telecommande"
        const val REPORT_EVERY_MS = 30_000L
    }
}
