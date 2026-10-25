package app.rengaine.playback

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/** État de lecture vu par l'interface (mini-lecteur, écran de lecture, file). */
data class PlayerUiState(
    val isConnected: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val trackId: Long? = null,
    val index: Int = -1,
    val count: Int = 0,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    /** Adresse de la pochette sans taille : l'interface ajoute la sienne. */
    val coverUrl: String? = null,
    val format: String? = null,
    val albumId: Long? = null,
    val bpm: Double? = null,
    /** Tonalité en notation anglaise (« Am », « F# »). */
    val musicalKey: String? = null,
    val camelot: String? = null,
    /** Favori selon le serveur au chargement de la piste. */
    val favorite: Boolean = false,
    @Player.RepeatMode val repeatMode: Int = Player.REPEAT_MODE_OFF,
    /** Durée de ce qui reste à écouter, piste en cours comprise. */
    val remainingMs: Long = 0,
    /** Volume média d'Android (D12), de [minVolume] à [maxVolume]. */
    val volume: Int = 0,
    val minVolume: Int = 0,
    val maxVolume: Int = 0,
    val muted: Boolean = false,
) {
    val hasQueue: Boolean get() = count > 0
    val hasNext: Boolean get() = index in 0 until count - 1
}

/** Une piste de la file, pour l'écran « File d'attente ». */
data class QueueEntry(
    val index: Int,
    val trackId: Long?,
    val title: String,
    val artist: String,
    val coverUrl: String?,
    val durationMs: Long,
)

/** La file telle que le lecteur la tient, pour la synchroniser avec le serveur (F6). */
data class QueueSnapshot(
    val trackIds: List<Long>,
    /** `null` si la file est vide. */
    val currentIndex: Int?,
    val positionMs: Long,
    @Player.RepeatMode val repeatMode: Int,
    val isPlaying: Boolean,
)

/**
 * Pont entre l'interface et [PlaybackService] par un `MediaController`,
 * repris de Musique : un seul contrôleur pour toute l'app.
 *
 * Le changement de compte et la synchronisation de la file avec le serveur
 * sont du ressort de `QueueSync`.
 */
@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audio = context.getSystemService(AudioManager::class.java)
    private var controller: MediaController? = null
    private var ticker: Job? = null

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _queue = MutableStateFlow<List<QueueEntry>>(emptyList())
    /** Toute la file, dans l'ordre de lecture. */
    val queue: StateFlow<List<QueueEntry>> = _queue.asStateFlow()

    private val _connected = MutableStateFlow(false)
    /** Vrai une fois le contrôleur relié au service. */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _queueChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    /** La file, la piste en cours, le mode ou la position ont changé (hors simple écoulement du temps). */
    val queueChanges: SharedFlow<Unit> = _queueChanges.asSharedFlow()

    init {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                controller = runCatching { future.get() }.getOrNull()?.also { it.addListener(listener) }
                syncQueue()
                sync()
                _connected.value = controller != null
            },
            MoreExecutors.directExecutor(),
        )
    }

    /** Charge une file sans la lancer (reprise au démarrage, F6). */
    fun load(items: List<MediaItem>, index: Int, positionMs: Long, @Player.RepeatMode repeatMode: Int) {
        if (items.isEmpty()) return
        controller?.run {
            setMediaItems(items, index.coerceIn(0, items.lastIndex), positionMs.coerceAtLeast(0))
            this.repeatMode = repeatMode
            playWhenReady = false
            prepare()
        }
    }

    /** État actuel de la file ; `null` tant que le contrôleur n'est pas relié. */
    /** Genres de la piste en cours, pour le mode infini « normal ». */
    fun currentGenres(): List<Long> =
        controller?.currentMediaItem?.mediaMetadata?.extras?.getLongArray(MediaItems.KEY_GENRES)?.toList().orEmpty()

    fun snapshot(): QueueSnapshot? {
        val c = controller ?: return null
        val ids = (0 until c.mediaItemCount).mapNotNull { MediaItems.trackIdOf(c.getMediaItemAt(it).mediaId) }
        return QueueSnapshot(
            trackIds = ids,
            currentIndex = c.currentMediaItemIndex.takeIf { ids.isNotEmpty() && it in ids.indices },
            positionMs = c.currentPosition.coerceAtLeast(0),
            repeatMode = c.repeatMode,
            isPlaying = c.isPlaying,
        )
    }

    private val _replaced = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** La file a été remplacée par un choix de l'utilisateur : le mode infini ciblé s'arrête. */
    val replaced: SharedFlow<Unit> = _replaced.asSharedFlow()

    /**
     * Remplace la file et lance la lecture. [fromInfinite] : appel du mode
     * infini lui-même, qui ne doit pas s'arrêter pour autant.
     */
    fun playNow(items: List<MediaItem>, startIndex: Int = 0, fromInfinite: Boolean = false) {
        if (items.isEmpty()) return
        if (!fromInfinite) _replaced.tryEmit(Unit)
        controller?.run {
            setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), 0L)
            prepare()
            play()
        }
    }

    /** « Lire ensuite » : juste après la piste en cours ; lance la lecture si la file est vide. */
    fun playNext(items: List<MediaItem>) {
        val c = controller ?: return
        if (items.isEmpty()) return
        if (c.mediaItemCount == 0) return playNow(items)
        c.addMediaItems((c.currentMediaItemIndex + 1).coerceAtMost(c.mediaItemCount), items)
    }

    /** « Ajouter à la file » : à la fin ; lance la lecture si la file est vide. */
    fun addToQueue(items: List<MediaItem>) {
        val c = controller ?: return
        if (items.isEmpty()) return
        if (c.mediaItemCount == 0) return playNow(items)
        c.addMediaItems(items)
    }

    fun togglePlayPause() {
        controller?.run {
            // Après une erreur (réseau coupé), `play()` seul ne relance pas.
            if (playbackState == Player.STATE_IDLE) prepare()
            if (isPlaying) pause() else play()
        }
    }

    fun skipNext() {
        controller?.seekToNextMediaItem()
    }

    fun skipPrevious() {
        controller?.seekToPrevious()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    /** Saute à une piste de la file et la lance. */
    fun playAt(index: Int) {
        controller?.run {
            if (index !in 0 until mediaItemCount) return
            seekTo(index, 0L)
            if (playbackState == Player.STATE_IDLE) prepare()
            play()
        }
    }

    fun removeAt(index: Int) {
        controller?.run { if (index in 0 until mediaItemCount) removeMediaItem(index) }
    }

    /** Déplace une piste de la file (glisser, ou « Monter »/« Descendre »). */
    fun move(from: Int, to: Int) {
        controller?.run {
            if (from != to && from in 0 until mediaItemCount && to in 0 until mediaItemCount) moveMediaItem(from, to)
        }
    }

    /** Vide la suite : seule la piste en cours reste. */
    fun clearUpcoming() {
        controller?.run {
            val next = currentMediaItemIndex + 1
            if (next < mediaItemCount) removeMediaItems(next, mediaItemCount)
        }
    }

    /** Répétition : aucune → toute la file → la piste → aucune (comme le site). */
    fun cycleRepeat() {
        controller?.run {
            repeatMode = when (repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    /**
     * Volume média d'Android (D12), sans afficher le panneau du système.
     * Par `AudioManager` et non par la session : la commande de volume n'est
     * pas transmise aux contrôleurs, seule la lecture l'est. Le curseur suit
     * ensuite le volume par la session (`onDeviceVolumeChanged`).
     */
    fun setVolume(volume: Int) {
        val a = audio ?: return
        val max = a.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val min = a.getStreamMinVolume(AudioManager.STREAM_MUSIC)
        a.setStreamVolume(AudioManager.STREAM_MUSIC, volume.coerceIn(min, max), 0)
        if (a.isStreamMute(AudioManager.STREAM_MUSIC) && volume > min) {
            a.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
        }
        sync()
    }

    fun toggleMute() {
        val a = audio ?: return
        val mute = !a.isStreamMute(AudioManager.STREAM_MUSIC)
        a.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE, 0)
        sync()
    }

    fun stop() {
        controller?.run {
            stop()
            clearMediaItems()
        }
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_TIMELINE_CHANGED)) syncQueue()
            sync()
            if (events.containsAny(*QUEUE_EVENTS)) _queueChanges.tryEmit(Unit)
            ticker?.cancel()
            if (player.isPlaying) {
                ticker = scope.launch {
                    while (true) {
                        delay(500)
                        sync()
                    }
                }
            }
        }
    }

    private fun sync() {
        val c = controller ?: return
        // Métadonnées de la piste d'abord : celles du lecteur se vident quand
        // un flux converti est relancé en cours de piste (F7).
        val itemMeta = c.currentMediaItem?.mediaMetadata
        val meta = if (itemMeta?.title != null) itemMeta else c.mediaMetadata
        val extras = itemMeta?.extras
        _state.value = PlayerUiState(
            isConnected = true,
            isPlaying = c.isPlaying,
            isBuffering = c.playbackState == Player.STATE_BUFFERING,
            trackId = MediaItems.trackIdOf(c.currentMediaItem?.mediaId),
            index = c.currentMediaItemIndex,
            count = c.mediaItemCount,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = c.duration.takeIf { it > 0 } ?: (meta.durationMs ?: 0),
            title = meta.title?.toString(),
            artist = meta.artist?.toString(),
            album = meta.albumTitle?.toString(),
            coverUrl = extras?.getString(MediaItems.KEY_COVER),
            format = extras?.getString(MediaItems.KEY_FORMAT),
            albumId = extras?.getLong(MediaItems.KEY_ALBUM_ID)?.takeIf { it > 0 },
            bpm = extras?.getDouble(MediaItems.KEY_BPM)?.takeIf { it > 0 },
            musicalKey = extras?.getString(MediaItems.KEY_MUSICAL_KEY),
            camelot = extras?.getString(MediaItems.KEY_CAMELOT),
            favorite = extras?.getBoolean(MediaItems.KEY_FAVORITE) ?: false,
            repeatMode = c.repeatMode,
            remainingMs = remaining(c),
            // Lu à la source : la session ne prévient qu'après coup.
            volume = audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: c.deviceVolume,
            minVolume = audio?.getStreamMinVolume(AudioManager.STREAM_MUSIC) ?: c.deviceInfo.minVolume,
            maxVolume = audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: c.deviceInfo.maxVolume,
            muted = audio?.isStreamMute(AudioManager.STREAM_MUSIC) ?: c.isDeviceMuted,
        )
    }

    private fun remaining(c: MediaController): Long {
        val entries = _queue.value
        val index = c.currentMediaItemIndex
        if (index !in entries.indices) return 0
        val after = entries.drop(index + 1).sumOf { it.durationMs }
        val current = (entries[index].durationMs - c.currentPosition).coerceAtLeast(0)
        return current + after
    }

    private fun syncQueue() {
        val c = controller ?: return
        _queue.value = (0 until c.mediaItemCount).map { i ->
            val item = c.getMediaItemAt(i)
            val meta = item.mediaMetadata
            QueueEntry(
                index = i,
                trackId = MediaItems.trackIdOf(item.mediaId),
                title = meta.title?.toString().orEmpty(),
                artist = meta.artist?.toString().orEmpty(),
                coverUrl = meta.extras?.getString(MediaItems.KEY_COVER),
                durationMs = meta.durationMs ?: 0,
            )
        }
    }

    private companion object {
        /** Ce qui change la file enregistrée : contenu, piste en cours, mode, saut dans la piste, lecture/pause. */
        val QUEUE_EVENTS = intArrayOf(
            Player.EVENT_TIMELINE_CHANGED,
            Player.EVENT_MEDIA_ITEM_TRANSITION,
            Player.EVENT_REPEAT_MODE_CHANGED,
            Player.EVENT_POSITION_DISCONTINUITY,
            Player.EVENT_IS_PLAYING_CHANGED,
        )
    }
}
