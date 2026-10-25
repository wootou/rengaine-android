package app.rengaine.playback

import android.content.Context
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import app.rengaine.R
import app.rengaine.data.repository.FavoriteKey
import app.rengaine.data.repository.FavoriteKind
import app.rengaine.data.repository.FavoritesRepository
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Cœur de la piste en cours dans la notification et l'écran verrouillé
 * (S2). Il suit le même état que le cœur du lecteur ([FavoritesRepository]) :
 * changer l'un change l'autre aussitôt.
 */
@OptIn(UnstableApi::class)
class NotificationFavorite(
    private val context: Context,
    private val favorites: FavoritesRepository,
) : MediaSession.Callback {

    var session: MediaSession? = null

    override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
        MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailableSessionCommands(
                MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().add(TOGGLE).build(),
            )
            .setMediaButtonPreferences(buttons(session.player))
            .build()

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle,
    ): ListenableFuture<SessionResult> {
        if (customCommand.customAction != TOGGLE.customAction) {
            return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
        }
        val (key, favorite) = current(session.player) ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_INVALID_STATE))
        favorites.setAsync(key, !favorite)
        // Le bouton suit `favorites.known` ([refresh]), y compris le retour
        // en arrière si l'enregistrement échoue.
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }

    /** Piste changée ou favori changé : redessine le bouton. */
    fun refresh() {
        val s = session ?: return
        s.setMediaButtonPreferences(buttons(s.player))
    }

    private fun current(player: Player): Pair<FavoriteKey, Boolean>? {
        val extras = player.currentMediaItem?.mediaMetadata?.extras ?: return null
        val id = extras.getLong(MediaItems.KEY_TRACK_ID).takeIf { it > 0 } ?: return null
        val key = FavoriteKey(FavoriteKind.TRACK, id)
        return key to favorites.isFavorite(key, extras.getBoolean(MediaItems.KEY_FAVORITE))
    }

    private fun buttons(player: Player): List<CommandButton> {
        val (_, favorite) = current(player) ?: return emptyList()
        val button = CommandButton.Builder(if (favorite) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED)
            .setDisplayName(context.getString(if (favorite) R.string.favorite_remove else R.string.favorite_add))
            .setSessionCommand(TOGGLE)
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build()
        return listOf(button)
    }

    private companion object {
        val TOGGLE = SessionCommand("app.rengaine.TOGGLE_FAVORITE", Bundle.EMPTY)
    }
}
