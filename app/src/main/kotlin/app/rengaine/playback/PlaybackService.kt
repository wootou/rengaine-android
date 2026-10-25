package app.rengaine.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import java.time.Instant
import app.rengaine.data.repository.HistoryRepository
import androidx.media3.common.MediaItem
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.DefaultMediaNotificationProvider
import app.rengaine.R
import app.rengaine.data.repository.FavoritesRepository
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.rengaine.MainActivity
import app.rengaine.data.remote.AuthorizedCallFactory
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import app.rengaine.data.repository.SettingsRepository
import app.rengaine.domain.GainSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Cœur de la lecture (S2), repris de Musique : un seul `ExoPlayer` et une
 * seule `MediaSession` pour toute l'app. Media3 fournit la notification,
 * l'écran verrouillé, les commandes Bluetooth et casque ; le focus audio et
 * la pause au débranchement du casque sont confiés à ExoPlayer.
 *
 * Flux et pochettes passent par [AuthorizedCallFactory] : le jeton du bon
 * compte, et un `401` qui marque ce compte « à reconnecter ».
 *
 * Compte aussi les écoutes (F11, [PlayTracker]) et égalise le volume
 * (F8, [VolumeNormalizer]). La notification porte l'icône de Rengaine
 * et le cœur de la piste en cours ([NotificationFavorite]).
 */
@AndroidEntryPoint
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    @Inject lateinit var callFactory: AuthorizedCallFactory
    @Inject lateinit var streamCache: StreamCache
    @Inject lateinit var history: HistoryRepository
    @Inject lateinit var qualityPolicy: StreamQualityPolicy
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var favorites: FavoritesRepository

    private lateinit var notificationFavorite: NotificationFavorite

    /** Volume égalisé (F8), d'après les réglages `playback` du compte. */
    private lateinit var normalizer: VolumeNormalizer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Écoutes (F11) : temps réellement joué, envoyé au serveur en fin de piste. */
    private lateinit var tracker: PlayTracker

    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null

    /** Échecs de suite (pistes illisibles) : au-delà de [MAX_SKIPS], on s'arrête au lieu de tout parcourir. */
    private var consecutiveErrors = 0

    /**
     * Reprise quand le réseau revient (repris de Musique) : après une
     * coupure, ExoPlayer reste en `STATE_IDLE` avec une erreur ; on relance
     * `prepare()` dès qu'une connexion est de nouveau disponible.
     */
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (::player.isInitialized && player.playbackState == Player.STATE_IDLE && player.playerError != null) {
                player.prepare()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        val httpFactory = OkHttpDataSource.Factory(callFactory)
        // Fichier d'origine demandé depuis le début : `Range: bytes=0-`, comme
        // le navigateur. Sur une réponse complète (200), Apache retire le
        // `Content-Length` posé par PHP (CVE-2024-24795) ; sans longueur ni
        // SEEKTABLE, Media3 ne peut pas se déplacer dans un FLAC. Le 206 donne
        // la taille dans `Content-Range`. Les autres requêtes ont déjà leur
        // `Range` ; une conversion à la volée n'en accepte pas (`startMs`).
        val rangedFactory = ResolvingDataSource.Factory(httpFactory) { spec ->
            val whole = spec.position == 0L && spec.length == C.LENGTH_UNSET.toLong()
            val original = spec.uri.lastPathSegment == "stream" && spec.uri.getQueryParameter("format") == null
            if (whole && original) spec.withAdditionalHeaders(mapOf("Range" to "bytes=0-")) else spec
        }
        val cachedFactory = CacheDataSource.Factory()
            .setCache(streamCache.cache)
            .setUpstreamDataSourceFactory(rangedFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        // Qualité du flux (F7), décidée au chargement de chaque piste et
        // fixée pour elle : `format`/`bitrate` dans l'adresse, et une clé de
        // cache par piste et par qualité.
        val streamFactory = ResolvingDataSource.Factory(cachedFactory) { spec ->
            val trackId = MediaItems.trackIdOfKey(spec.key) ?: return@Factory spec
            val quality = qualityPolicy.pin(trackId)
            if (quality.format == null) {
                return@Factory spec.buildUpon().setKey(MediaItems.cacheKey(trackId, quality)).build()
            }
            val startMs = MediaItems.startMsOfKey(spec.key)
            val uri = spec.uri.buildUpon()
                .appendQueryParameter("format", quality.format)
                .appendQueryParameter("bitrate", quality.bitrate.toString())
                .apply { if (startMs > 0) appendQueryParameter("startMs", startMs.toString()) }
                .build()
            val builder = spec.buildUpon().setUri(uri)
            if (startMs > 0) {
                // Flux relancé en cours de piste : un morceau, pas la piste entière ;
                // il n'a rien à faire dans le cache disque.
                builder.setKey(MediaItems.cacheKey(trackId, quality) + "@" + startMs)
                    .setFlags(spec.flags or DataSpec.FLAG_DONT_CACHE_IF_LENGTH_UNKNOWN)
            } else {
                builder.setKey(MediaItems.cacheKey(trackId, quality))
            }
            builder.build()
        }

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                ConversionEndFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(streamFactory), qualityPolicy),
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            // Le curseur de volume du lecteur (D12) suit le volume média
            // d'Android ; il le règle par `AudioManager` (`PlayerController`).
            .setDeviceVolumeControlEnabled(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(playerListener)
        normalizer = VolumeNormalizer(player)
        scope.launch {
            settingsRepository.settings
                .map { s -> s?.let { GainSettings(it.normalization, it.targetLufs, it.preventClipping) } }
                .distinctUntilChanged()
                .collect(normalizer::onSettings)
        }
        // Journal détaillé du lecteur, en debug seulement (diagnostic F7).
        if (app.rengaine.BuildConfig.DEBUG) player.addAnalyticsListener(androidx.media3.exoplayer.util.EventLogger("Rengaine/Lecteur"))
        tracker = PlayTracker(
            elapsed = SystemClock::elapsedRealtime,
            now = Instant::now,
            accountId = { history.activeAccountId.value },
            onPlay = history::record,
        )

        // Les pochettes de la notification demandent le jeton, comme les
        // flux : le chargeur d'images de Media3 passe par le même client.
        val bitmapLoader = CacheBitmapLoader(
            DataSourceBitmapLoader.Builder(this)
                .setDataSourceFactory(DefaultDataSource.Factory(this, httpFactory))
                .build(),
        )

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply { setSmallIcon(R.drawable.ic_notification) },
        )
        notificationFavorite = NotificationFavorite(this, favorites)

        // La session voit le lecteur qui sait avancer dans une conversion (F7).
        mediaSession = MediaSession.Builder(this, ConversionSeekPlayer(player, qualityPolicy))
            .setBitmapLoader(bitmapLoader)
            .setSessionActivity(openApp)
            .setCallback(notificationFavorite)
            .build()
        notificationFavorite.session = mediaSession
        scope.launch { favorites.known.collect { notificationFavorite.refresh() } }

        // Handler du thread principal : ExoPlayer refuse tout appel depuis
        // un autre thread que le sien (piège rencontré dans Musique).
        runCatching {
            getSystemService(ConnectivityManager::class.java)?.registerNetworkCallback(
                NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
                networkCallback,
                Handler(Looper.getMainLooper()),
            )
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /** App fermée depuis les tâches récentes : on continue si ça joue, sinon on s'arrête (pas de notification fantôme). */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val current = mediaSession?.player
        if (current == null || !current.playWhenReady || current.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        // Lecture arrêtée pour de bon : l'écoute en cours compte si elle a duré.
        if (::tracker.isInitialized) tracker.finish(completed = false)
        scope.cancel()
        handler.removeCallbacks(saveListenLoop)
        if (::normalizer.isInitialized) normalizer.release()
        runCatching { getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(networkCallback) }
        mediaSession?.run {
            player.removeListener(playerListener)
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) consecutiveErrors = 0
            if (isPlaying) tracker.playing(MediaItems.trackIdOf(player.currentMediaItem?.mediaId)) else tracker.paused()
            saveListen()
        }

        /**
         * Changement de piste : fin de l'écoute précédente. `completed` au
         * passage automatique (fin naturelle, tour de répétition) ; toute autre
         * raison (suivant, précédent, piste choisie, nouvelle liste) la compte
         * comme passée, comme le site.
         */
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val automatic = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
            val trackId = MediaItems.trackIdOf(mediaItem?.mediaId)
            // Même piste relancée plus loin (saut dans une conversion) : la même écoute continue.
            val restarted = reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED &&
                trackId != null && trackId == tracker.currentTrackId
            if (!restarted) tracker.finish(completed = automatic)
            if (player.isPlaying) tracker.playing(trackId)
            forgetRestartPoints()
            normalizer.onTrackStarted()
            notificationFavorite.refresh()
            // Les pistes passées pourront prendre la qualité du moment si on y revient.
            val index = player.currentMediaItemIndex
            val keep = listOf(index, index + 1).filter { it in 0 until player.mediaItemCount }
                .mapNotNull { MediaItems.trackIdOf(player.getMediaItemAt(it).mediaId) }
            qualityPolicy.keepOnly(keep.toSet())
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) = normalizer.onAudioSessionId(audioSessionId)

        override fun onPlaybackStateChanged(playbackState: Int) {
            // Bout de la file atteint : la dernière piste est allée au bout.
            if (playbackState == Player.STATE_ENDED) tracker.finish(completed = true)
        }

        override fun onPlayerError(error: PlaybackException) {
            val status = (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode
            Log.w(TAG, "Erreur de lecture ${error.errorCodeName} (HTTP $status)")
            when {
                // Jeton refusé : le compte est déjà marqué « à reconnecter »
                // par son client ; inutile d'essayer les pistes suivantes.
                status == 401 -> Unit
                // Coupure réseau : la piste n'y est pour rien, on la garde ;
                // [networkCallback] relancera la lecture.
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> Unit
                // Piste illisible (supprimée, format inconnu…) : on la
                // retire et on passe à la suivante, sans boucler sans fin.
                isTrackError(error) -> skipBrokenTrack()
                // Autre panne (sortie audio, erreur interne) : la piste n'y
                // est pour rien, on s'arrête sans toucher à la file.
                else -> Unit
            }
        }
    }

    /**
     * Copie de l'écoute en cours sur le téléphone, toutes les
     * [SAVE_LISTEN_MS] pendant la lecture et à chaque pause : si Android tue
     * l'app, au plus ces quelques secondes manquent à l'historique.
     */
    private val saveListenLoop = object : Runnable {
        override fun run() {
            history.saveInProgress(tracker.snapshot())
            if (player.isPlaying) handler.postDelayed(this, SAVE_LISTEN_MS)
        }
    }
    private val handler = Handler(Looper.getMainLooper())

    private fun saveListen() {
        handler.removeCallbacks(saveListenLoop)
        saveListenLoop.run()
    }

    /**
     * Une piste relancée en cours de route (`track:12@45000`) redevient
     * normale une fois quittée : si on y revient, elle repart du début.
     */
    private fun forgetRestartPoints() {
        val current = player.currentMediaItemIndex
        for (i in 0 until player.mediaItemCount) {
            if (i == current) continue
            val item = player.getMediaItemAt(i)
            if (MediaItems.startMsOf(item) == 0L) continue
            val trackId = MediaItems.trackIdOf(item.mediaId) ?: continue
            player.replaceMediaItem(i, item.buildUpon().setCustomCacheKey(MediaItems.itemKey(trackId)).build())
        }
    }

    /** L'erreur vient-elle de la piste elle-même (absente, refusée, illisible) ? */
    private fun isTrackError(error: PlaybackException): Boolean = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        -> true
        else -> false
    }

    private fun skipBrokenTrack() {
        consecutiveErrors++
        val index = player.currentMediaItemIndex
        if (index in 0 until player.mediaItemCount) player.removeMediaItem(index)
        if (player.mediaItemCount > 0 && consecutiveErrors <= MAX_SKIPS) {
            player.prepare()
            player.play()
        }
    }

    private companion object {
        const val TAG = "Rengaine/Lecture"
        const val MAX_SKIPS = 5
        const val SAVE_LISTEN_MS = 10_000L
    }
}
