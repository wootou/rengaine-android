package app.rengaine.ui.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.rengaine.R
import app.rengaine.data.remote.ApiResult
import app.rengaine.data.remote.dto.PlaylistDetailDto
import app.rengaine.data.remote.dto.PlaylistEntryDto
import app.rengaine.data.remote.dto.TrackDto
import app.rengaine.data.repository.PlaylistRepository
import app.rengaine.playback.MediaItems
import app.rengaine.playback.PlayerController
import app.rengaine.ui.components.ErrorState
import app.rengaine.ui.components.LoadingState
import app.rengaine.ui.components.MosaicCover
import app.rengaine.ui.components.TrackAction
import app.rengaine.ui.components.TrackRow
import app.rengaine.ui.components.formatMinutes
import app.rengaine.ui.theme.Rengaine
import app.rengaine.ui.theme.RengaineShapes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PlaylistUiState {
    data object Loading : PlaylistUiState
    data object Failed : PlaylistUiState
    data class Ready(val playlist: PlaylistDetailDto) : PlaylistUiState
}

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: PlaylistRepository,
    private val player: PlayerController,
) : ViewModel() {
    private val id: Long = checkNotNull(savedState[ARG_ID])
    private val _state = MutableStateFlow<PlaylistUiState>(PlaylistUiState.Loading)
    val state = _state.asStateFlow()
    val playerState = player.state
    private val _deleted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val deleted = _deleted.asSharedFlow()

    init {
        load()
    }

    fun load() {
        _state.value = PlaylistUiState.Loading
        viewModelScope.launch { update(repository.playlist(id), failOnError = true) }
    }

    fun play(entry: PlaylistEntryDto? = null) {
        val entries = playable()
        val start = entry?.let { e -> entries.indexOfFirst { it.id == e.id } }?.coerceAtLeast(0) ?: 0
        player.playNow(entries.mapNotNull { MediaItems.fromTrack(it.track) }, start)
    }

    fun shuffle() = player.playNow(playable().shuffled().mapNotNull { MediaItems.fromTrack(it.track) })
    fun playNext(tracks: List<TrackDto> = playable().map { it.track }) = player.playNext(tracks.mapNotNull(MediaItems::fromTrack))
    fun addToQueue(tracks: List<TrackDto> = playable().map { it.track }) = player.addToQueue(tracks.mapNotNull(MediaItems::fromTrack))

    fun rename(name: String) = viewModelScope.launch { update(repository.rename(id, name)) }
    fun setShared(shared: Boolean) = viewModelScope.launch { update(repository.setShared(id, shared)) }
    fun remove(entry: PlaylistEntryDto) = viewModelScope.launch { update(repository.removeEntry(id, entry.id)) }
    fun delete() = viewModelScope.launch {
        if (repository.delete(id) is ApiResult.Ok) _deleted.emit(Unit)
    }

    fun trackIds(): List<Long> = playable().map { it.track.id }

    private fun playable(): List<PlaylistEntryDto> =
        (_state.value as? PlaylistUiState.Ready)?.playlist?.items.orEmpty().filter { it.track.available }

    private fun update(result: ApiResult<PlaylistDetailDto>, failOnError: Boolean = false) {
        when (result) {
            is ApiResult.Ok -> _state.value = PlaylistUiState.Ready(result.value)
            is ApiResult.Err -> if (failOnError) _state.value = PlaylistUiState.Failed
        }
    }

    companion object {
        const val ARG_ID = "id"
    }
}

/**
 * Page d'une playlist (capture `15`) : mosaïque, « PLAYLIST PRIVÉE », titre,
 * « 8 pistes · 28 min » ; actions (dont renommer et supprimer pour son
 * créateur) ; interrupteur « Partager avec tous les comptes » ; pistes avec
 * pochettes.
 */
@Composable
fun PlaylistScreen(
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: PlaylistViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val player by viewModel.playerState.collectAsStateWithLifecycle()
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var addTracks by remember { mutableStateOf<List<Long>?>(null) }

    LaunchedEffect(Unit) { viewModel.deleted.collect { onBack() } }

    when (val s = state) {
        PlaylistUiState.Loading -> Box(Modifier.fillMaxSize().padding(contentPadding)) { LoadingState() }
        PlaylistUiState.Failed -> Box(Modifier.fillMaxSize().padding(contentPadding)) { ErrorState(onRetry = viewModel::load) }
        is PlaylistUiState.Ready -> {
            val playlist = s.playlist
            val playNextLabel = stringResource(R.string.action_play_next)
            val addQueueLabel = stringResource(R.string.action_add_to_queue)
            val addPlaylistLabel = stringResource(R.string.action_add_to_playlist)
            val removeLabel = stringResource(R.string.playlist_remove_track)
            LazyColumn(
                contentPadding = PaddingValues(
                    top = contentPadding.calculateTopPadding(),
                    bottom = contentPadding.calculateBottomPadding() + 16.dp,
                ),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "header") {
                    Header(
                        playlist = playlist,
                        onBack = onBack,
                        onRename = { renaming = true },
                        onDelete = { confirmDelete = true },
                        viewModel = viewModel,
                    )
                }
                if (playlist.items.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.playlist_empty),
                            style = Rengaine.type.body,
                            color = Rengaine.colors.muted,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                }
                items(playlist.items, key = { it.id }) { entry ->
                    val track = entry.track
                    TrackRow(
                        track = track,
                        isCurrent = player.trackId == track.id,
                        isPlaying = player.isPlaying,
                        subtitle = listOfNotNull(track.artistCredit.takeIf { it.isNotBlank() }, track.album?.title).joinToString(" · "),
                        onPlay = { viewModel.play(entry) },
                        showCover = true,
                        actions = buildList {
                            add(TrackAction(playNextLabel, Icons.AutoMirrored.Filled.PlaylistPlay) { viewModel.playNext(listOf(track)) })
                            add(TrackAction(addQueueLabel, Icons.AutoMirrored.Filled.PlaylistAdd) { viewModel.addToQueue(listOf(track)) })
                            add(TrackAction(addPlaylistLabel, Icons.Filled.Add) { addTracks = listOf(track.id) })
                            if (playlist.mine) add(TrackAction(removeLabel, Icons.Filled.RemoveCircleOutline) { viewModel.remove(entry) })
                        },
                    )
                }
            }

            if (renaming) {
                PlaylistNameDialog(
                    title = stringResource(R.string.playlist_rename),
                    initial = playlist.name,
                    onConfirm = viewModel::rename,
                    onDismiss = { renaming = false },
                )
            }
            if (confirmDelete) {
                DeleteDialog(playlist.name, onConfirm = { confirmDelete = false; viewModel.delete() }, onDismiss = { confirmDelete = false })
            }
        }
    }
    addTracks?.let { ids -> AddToPlaylistDialog(trackIds = ids, onDismiss = { addTracks = null }) }
}

@Composable
private fun Header(
    playlist: PlaylistDetailDto,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    viewModel: PlaylistViewModel,
) {
    val colors = Rengaine.colors
    val type = Rengaine.type
    val overline = when {
        !playlist.mine -> stringResource(R.string.playlist_shared_by, playlist.owner.displayName)
        playlist.shared -> stringResource(R.string.playlist_kind_shared)
        else -> stringResource(R.string.playlist_kind_private)
    }
    val meta = buildList {
        add(pluralStringResource(R.plurals.track_count, playlist.trackCount, playlist.trackCount))
        if (playlist.durationMs > 0) add(formatMinutes(playlist.durationMs))
    }.joinToString(" · ")

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), tint = colors.text)
            }
        }
        MosaicCover(playlist.coverUrls, Modifier.widthIn(max = 240.dp).fillMaxWidth(0.6f).aspectRatio(1f))
        Spacer(Modifier.size(20.dp))
        Text(overline.uppercase(), style = type.overline, color = colors.muted, textAlign = TextAlign.Center)
        Text(
            playlist.name,
            style = type.pageTitle,
            color = colors.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp).semantics { heading() },
        )
        Text(meta, style = type.secondary, color = colors.muted)

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { viewModel.playNext() }) {
                Icon(Icons.AutoMirrored.Filled.PlaylistPlay, stringResource(R.string.action_play_next), tint = colors.text)
            }
            IconButton(onClick = { viewModel.addToQueue() }) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(R.string.action_add_to_queue), tint = colors.text)
            }
            if (playlist.mine) {
                IconButton(onClick = onRename) {
                    Icon(Icons.Filled.Edit, stringResource(R.string.playlist_rename), tint = colors.text)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, stringResource(R.string.playlist_delete), tint = colors.text)
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = viewModel::shuffle, enabled = playlist.items.isNotEmpty()) {
                Icon(Icons.Filled.Shuffle, stringResource(R.string.action_shuffle), tint = colors.text)
            }
            Spacer(Modifier.size(8.dp))
            val playLabel = stringResource(R.string.action_play_playlist)
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(colors.accent)
                    .clickable(enabled = playlist.items.isNotEmpty(), onClickLabel = playLabel) { viewModel.play() }
                    .semantics { contentDescription = playLabel },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(32.dp))
            }
        }

        if (playlist.mine) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.setShared(!playlist.shared) }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Switch(
                    checked = playlist.shared,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = colors.accent,
                        checkedThumbColor = colors.onAccent,
                        uncheckedTrackColor = colors.surface,
                        uncheckedThumbColor = colors.muted,
                        uncheckedBorderColor = colors.line,
                    ),
                )
                Text(stringResource(R.string.playlist_share_toggle), style = type.body, color = colors.text)
            }
        }
    }
}

@Composable
private fun DeleteDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = Rengaine.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RengaineShapes.dialog,
        containerColor = colors.surface,
        title = { Text(stringResource(R.string.playlist_delete_title, name), style = Rengaine.type.sectionTitle, color = colors.text) },
        text = { Text(stringResource(R.string.playlist_delete_text), style = Rengaine.type.body, color = colors.muted) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.playlist_delete), color = colors.danger, style = Rengaine.type.button) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = colors.muted, style = Rengaine.type.button) }
        },
    )
}
