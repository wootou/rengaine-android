package app.rengaine.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rengaine.R
import app.rengaine.playback.InfiniteMode
import app.rengaine.playback.MediaItems
import app.rengaine.playback.PlayerController
import app.rengaine.playback.PlayerUiState
import app.rengaine.playback.QueueEntry
import app.rengaine.ui.theme.Rengaine
import app.rengaine.ui.theme.RengaineShapes
import coil3.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import app.rengaine.data.local.SessionStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val controller: PlayerController,
    private val infinite: InfiniteMode,
    session: SessionStore,
) : ViewModel() {
    val state: StateFlow<PlayerUiState> = controller.state
    /** Curseur de volume affiché (Réglages › Sur cet appareil). */
    val volumeBarShown: StateFlow<Boolean> = session.volumeBarHidden.map { !it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    /** Réglage « mode infini » du compte (bouton ∞ du lecteur). */
    val infiniteActive: StateFlow<Boolean> = infinite.enabled
    private val _notSaved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Le réglage n'a pas pu être enregistré (hors ligne) : on le dit. */
    val notSaved = _notSaved.asSharedFlow()
    fun toggleInfinite() {
        viewModelScope.launch { if (!infinite.toggle()) _notSaved.emit(Unit) }
    }
    val queue: StateFlow<List<QueueEntry>> = controller.queue
    fun togglePlayPause() = controller.togglePlayPause()
    fun skipNext() = controller.skipNext()
    fun skipPrevious() = controller.skipPrevious()
    fun seekTo(positionMs: Long) = controller.seekTo(positionMs)
    fun cycleRepeat() = controller.cycleRepeat()
    fun setVolume(volume: Int) = controller.setVolume(volume)
    fun toggleMute() = controller.toggleMute()
    fun playAt(index: Int) = controller.playAt(index)
    fun removeAt(index: Int) = controller.removeAt(index)
    fun move(from: Int, to: Int) = controller.move(from, to)
    fun clearUpcoming() = controller.clearUpcoming()
}

/**
 * Mini-lecteur (capture `11`) : carte flottante, pochette, titre, artiste,
 * lecture/pause et bouton de la file avec son compteur ; fine barre de
 * progression braise en bas. Toucher la carte ouvre le lecteur plein écran.
 * Masqué tant que la file est vide.
 */
@Composable
fun MiniPlayer(
    onOpenPlayer: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.hasQueue) return
    val colors = Rengaine.colors
    val type = Rengaine.type
    val playing = state.isPlaying
    val progress = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RengaineShapes.miniPlayer)
            .background(colors.raised)
            .border(1.dp, colors.line, RengaineShapes.miniPlayer)
            // Toute la carte réagit : rien ne doit « traverser » vers l'écran dessous.
            .clickable(onClickLabel = stringResource(R.string.player_open), onClick = onOpenPlayer),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, top = 10.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Cover(state.coverUrl, modifier = Modifier.size(48.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    state.title.orEmpty(),
                    style = type.bodyStrong,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    state.artist.orEmpty(),
                    style = type.secondary,
                    color = colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val playLabel = stringResource(if (playing) R.string.player_pause else R.string.player_play)
            IconButton(onClick = viewModel::togglePlayPause) {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = playLabel,
                    tint = colors.text,
                    modifier = Modifier.size(30.dp),
                )
            }
            QueueButton(count = state.count, onClick = onOpenQueue)
        }
        Box(Modifier.fillMaxWidth().height(3.dp)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(colors.accent))
        }
    }
}

/** Bouton « file d'attente » avec la pastille du nombre de pistes (captures `11`, `12`). */
@Composable
fun QueueButton(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Rengaine.colors
    val label = pluralStringResource(R.plurals.queue_open, count, count)
    IconButton(onClick = onClick, modifier = modifier.semantics { contentDescription = label }) {
        BadgedBox(
            badge = {
                if (count > 0) {
                    Badge(containerColor = colors.accent, contentColor = colors.onAccent) {
                        Text(if (count > 999) "999+" else count.toString(), style = Rengaine.type.small)
                    }
                }
            },
        ) {
            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = colors.text)
        }
    }
}

/**
 * Pochette carrée ; repli sur une note quand la piste n'a pas d'image. La
 * taille demandée au serveur suit la place réelle en pixels : `s` (96),
 * `m` (300) ou `l` (600), jamais `original` (F2).
 */
@Composable
fun Cover(coverUrl: String?, modifier: Modifier = Modifier) {
    val colors = Rengaine.colors
    BoxWithConstraints(
        modifier = modifier
            .clip(RengaineShapes.cover)
            .background(colors.surface),
        contentAlignment = Alignment.Center,
    ) {
        if (coverUrl == null) {
            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = colors.faint)
        } else {
            val px = with(LocalDensity.current) { maxWidth.toPx() }
            val size = when {
                px <= 96f -> "s"
                px <= 300f -> "m"
                else -> "l"
            }
            AsyncImage(
                model = MediaItems.withSize(coverUrl, size),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}
