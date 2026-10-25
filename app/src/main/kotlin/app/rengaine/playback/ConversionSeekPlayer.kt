package app.rengaine.playback

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/**
 * Avancer dans une piste **en cours de conversion** (F7), comme le site
 * (`player.js`, précisé par l'équipe du serveur) : le flux converti n'a ni longueur
 * ni plages (`Accept-Ranges: none`), le lecteur ne sait donc pas s'y
 * déplacer. On redemande la piste à partir de la position voulue
 * (`startMs`) et on décale l'affichage : position = départ + temps du
 * nouveau flux ; la durée reste celle de la piste.
 *
 * Le départ est porté par l'élément lui-même (clé `track:12@45000`), que
 * l'on remplace dans la file : rien à oublier quand on change de piste.
 * Une conversion déjà en cache sur le serveur se comporte comme un fichier
 * et se parcourt normalement.
 *
 * C'est ce lecteur que voit la session Media3 : notification, écran
 * verrouillé et interface affichent donc les positions décalées.
 */
@OptIn(UnstableApi::class)
class ConversionSeekPlayer(
    private val exo: ExoPlayer,
    private val quality: StreamQualityPolicy,
) : ForwardingPlayer(exo) {

    private fun offset(): Long = MediaItems.startMsOf(exo.currentMediaItem)

    /** La piste en cours est servie convertie (et non le fichier d'origine). */
    private fun converted(): Boolean {
        val id = MediaItems.trackIdOf(exo.currentMediaItem?.mediaId) ?: return false
        return quality.pinned(id)?.format != null
    }

    /** Il faut redemander le flux pour s'y déplacer. */
    private fun mustRestart(): Boolean = converted() && (offset() > 0 || !exo.isCurrentMediaItemSeekable)

    private fun trackDurationMs(): Long? =
        exo.currentMediaItem?.mediaMetadata?.durationMs?.takeIf { it > 0 }

    // --- Positions vues de l'extérieur ---------------------------------------------

    override fun getCurrentPosition(): Long = exo.currentPosition + offset()
    override fun getContentPosition(): Long = exo.contentPosition + offset()
    override fun getBufferedPosition(): Long = exo.bufferedPosition + offset()
    override fun getContentBufferedPosition(): Long = exo.contentBufferedPosition + offset()

    override fun getDuration(): Long = if (converted()) trackDurationMs() ?: exo.duration else exo.duration
    override fun getContentDuration(): Long = if (converted()) trackDurationMs() ?: exo.contentDuration else exo.contentDuration

    override fun isCurrentMediaItemSeekable(): Boolean = exo.isCurrentMediaItemSeekable || converted()

    override fun getAvailableCommands(): Player.Commands {
        val base = exo.availableCommands
        if (!converted()) return base
        return base.buildUpon().add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM).build()
    }

    /**
     * Les commandes annoncées par ExoPlayer excluent le déplacement dans un
     * flux sans longueur ; la session les relaie telles quelles aux
     * contrôleurs (l'interface, la notification), qui renonceraient alors à
     * tout saut. On leur annonce les nôtres.
     */
    private val wrapped = HashMap<Player.Listener, Player.Listener>()

    override fun addListener(listener: Player.Listener) {
        val forwarding = CommandsListener(listener)
        synchronized(wrapped) { wrapped[listener] = forwarding }
        super.addListener(forwarding)
    }

    override fun removeListener(listener: Player.Listener) {
        val forwarding = synchronized(wrapped) { wrapped.remove(listener) } ?: listener
        super.removeListener(forwarding)
    }

    override fun isCommandAvailable(command: Int): Boolean =
        exo.isCommandAvailable(command) || (command == Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM && converted())

    // --- Déplacements -----------------------------------------------------------------

    override fun seekTo(positionMs: Long) = seekInCurrent(positionMs)

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        if (mediaItemIndex == exo.currentMediaItemIndex) seekInCurrent(positionMs) else exo.seekTo(mediaItemIndex, positionMs)
    }

    override fun seekBack() = seekInCurrent((currentPosition - exo.seekBackIncrement).coerceAtLeast(0))
    override fun seekForward() = seekInCurrent(currentPosition + exo.seekForwardIncrement)

    /** « Précédent » : au-delà des premières secondes, retour au début de la piste (règle de Media3). */
    override fun seekToPrevious() {
        if (offset() == 0L) return exo.seekToPrevious()
        if (exo.hasPreviousMediaItem() && currentPosition <= exo.maxSeekToPreviousPosition) exo.seekToPreviousMediaItem() else seekInCurrent(0)
    }

    private fun seekInCurrent(positionMs: Long) {
        // Pas au-delà de la fin : le serveur n'aurait rien à envoyer.
        val end = trackDurationMs()?.minus(1_000)?.coerceAtLeast(0) ?: Long.MAX_VALUE
        val target = positionMs.coerceIn(0, end)
        if (!mustRestart()) {
            exo.seekTo(target)
            return
        }
        val item = exo.currentMediaItem ?: return
        val trackId = MediaItems.trackIdOf(item.mediaId) ?: return
        restartAt(exo.currentMediaItemIndex, item, trackId, target)
    }

    /**
     * Relance la piste en cours à [startMs]. On recharge la file entière,
     * la piste relancée à sa place : remplacer seulement l'élément en cours
     * laissait ExoPlayer bloqué en chargement (essai du 2026-10-24), alors
     * que ce chemin est celui de la reprise de la file, éprouvé.
     */
    private fun restartAt(index: Int, item: MediaItem, trackId: Long, startMs: Long) {
        val restarted = item.buildUpon().setCustomCacheKey(MediaItems.itemKey(trackId, startMs)).build()
        val items = (0 until exo.mediaItemCount).map { i -> if (i == index) restarted else exo.getMediaItemAt(i) }
        val playWhenReady = exo.playWhenReady
        // Au tour suivant de la boucle du lecteur, hors du traitement de la
        // commande « saut » par la session : fait pendant, la session cessait
        // de suivre l'état du lecteur (essai du 2026-10-24).
        handler.post {
            exo.setMediaItems(items, index, 0)
            exo.prepare()
            exo.playWhenReady = playWhenReady
        }
    }

    private val handler = android.os.Handler(exo.applicationLooper)

    /**
     * Reprise de la file à une position (ouverture de l'app) : si la piste
     * sera convertie, on demande directement le flux à partir de là.
     */
    override fun setMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long) {
        val start = mediaItems.getOrNull(startIndex)
        val trackId = MediaItems.trackIdOf(start?.mediaId)
        if (start == null || trackId == null || startPositionMs <= 0 || quality.pin(trackId).format == null) {
            return exo.setMediaItems(mediaItems, startIndex, startPositionMs)
        }
        val items = mediaItems.toMutableList()
        items[startIndex] = start.buildUpon().setCustomCacheKey(MediaItems.itemKey(trackId, startPositionMs)).build()
        exo.setMediaItems(items, startIndex, 0)
    }

    /**
     * Relaie **chaque** rappel, explicitement. La délégation Kotlin (`by`)
     * ne relaie pas les méthodes par défaut des interfaces Java — et tous
     * les rappels de `Player.Listener` en sont : la session ne recevait plus
     * rien (plus de notification). Seuls changent les commandes annoncées
     * et le lecteur passé à `onEvents`.
     */
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    private inner class CommandsListener(private val l: Player.Listener) : Player.Listener {
        override fun onAvailableCommandsChanged(availableCommands: Player.Commands) = l.onAvailableCommandsChanged(this@ConversionSeekPlayer.availableCommands)
        override fun onEvents(player: Player, events: Player.Events) = l.onEvents(this@ConversionSeekPlayer, events)
        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) = l.onTimelineChanged(timeline, reason)
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = l.onMediaItemTransition(mediaItem, reason)
        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) = l.onTracksChanged(tracks)
        override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) = l.onMediaMetadataChanged(mediaMetadata)
        override fun onPlaylistMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) = l.onPlaylistMetadataChanged(mediaMetadata)
        override fun onIsLoadingChanged(isLoading: Boolean) = l.onIsLoadingChanged(isLoading)
        override fun onLoadingChanged(isLoading: Boolean) = l.onLoadingChanged(isLoading)
        override fun onTrackSelectionParametersChanged(parameters: androidx.media3.common.TrackSelectionParameters) = l.onTrackSelectionParametersChanged(parameters)
        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) = l.onPlayerStateChanged(playWhenReady, playbackState)
        override fun onPlaybackStateChanged(playbackState: Int) = l.onPlaybackStateChanged(playbackState)
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = l.onPlayWhenReadyChanged(playWhenReady, reason)
        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) = l.onPlaybackSuppressionReasonChanged(playbackSuppressionReason)
        override fun onIsPlayingChanged(isPlaying: Boolean) = l.onIsPlayingChanged(isPlaying)
        override fun onRepeatModeChanged(repeatMode: Int) = l.onRepeatModeChanged(repeatMode)
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = l.onShuffleModeEnabledChanged(shuffleModeEnabled)
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) = l.onPlayerError(error)
        override fun onPlayerErrorChanged(error: androidx.media3.common.PlaybackException?) = l.onPlayerErrorChanged(error)
        override fun onPositionDiscontinuity(reason: Int) = l.onPositionDiscontinuity(reason)
        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) =
            l.onPositionDiscontinuity(oldPosition, newPosition, reason)
        override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) = l.onPlaybackParametersChanged(playbackParameters)
        override fun onSeekBackIncrementChanged(seekBackIncrementMs: Long) = l.onSeekBackIncrementChanged(seekBackIncrementMs)
        override fun onSeekForwardIncrementChanged(seekForwardIncrementMs: Long) = l.onSeekForwardIncrementChanged(seekForwardIncrementMs)
        override fun onMaxSeekToPreviousPositionChanged(maxSeekToPreviousPositionMs: Long) = l.onMaxSeekToPreviousPositionChanged(maxSeekToPreviousPositionMs)
        override fun onAudioSessionIdChanged(audioSessionId: Int) = l.onAudioSessionIdChanged(audioSessionId)
        override fun onAudioAttributesChanged(audioAttributes: androidx.media3.common.AudioAttributes) = l.onAudioAttributesChanged(audioAttributes)
        override fun onVolumeChanged(volume: Float) = l.onVolumeChanged(volume)
        override fun onSkipSilenceEnabledChanged(skipSilenceEnabled: Boolean) = l.onSkipSilenceEnabledChanged(skipSilenceEnabled)
        override fun onDeviceInfoChanged(deviceInfo: androidx.media3.common.DeviceInfo) = l.onDeviceInfoChanged(deviceInfo)
        override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) = l.onDeviceVolumeChanged(volume, muted)
        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) = l.onVideoSizeChanged(videoSize)
        override fun onSurfaceSizeChanged(width: Int, height: Int) = l.onSurfaceSizeChanged(width, height)
        override fun onRenderedFirstFrame() = l.onRenderedFirstFrame()
        override fun onCues(cues: List<androidx.media3.common.text.Cue>) = l.onCues(cues)
        override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) = l.onCues(cueGroup)
        override fun onMetadata(metadata: androidx.media3.common.Metadata) = l.onMetadata(metadata)
    }
}
