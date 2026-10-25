package app.rengaine.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.rengaine.R
import app.rengaine.data.repository.FavoriteKey
import app.rengaine.data.repository.FavoriteKind
import app.rengaine.ui.components.FavoriteButton
import app.rengaine.data.remote.ApiResult
import app.rengaine.data.remote.dto.AlbumSummaryDto
import app.rengaine.data.remote.dto.ArtistDetailDto
import app.rengaine.data.remote.dto.TrackDto
import app.rengaine.data.repository.CatalogRepository
import app.rengaine.playback.MediaItems
import app.rengaine.playback.PlayerController
import app.rengaine.ui.components.ErrorState
import app.rengaine.ui.components.LoadingState
import app.rengaine.ui.components.RengaineButton
import app.rengaine.ui.components.TrackAction
import app.rengaine.ui.components.TrackRow
import app.rengaine.ui.playlists.AddToPlaylistDialog
import app.rengaine.ui.theme.Rengaine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ArtistUiState {
    data object Loading : ArtistUiState
    data object Failed : ArtistUiState
    data class Ready(val artist: ArtistDetailDto, val tracks: List<TrackDto>) : ArtistUiState
}

@HiltViewModel
class ArtistViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val catalog: CatalogRepository,
    private val player: PlayerController,
) : ViewModel() {
    private val id: Long = checkNotNull(savedState[ARG_ID])
    private val _state = MutableStateFlow<ArtistUiState>(ArtistUiState.Loading)
    val state = _state.asStateFlow()
    val playerState = player.state

    init {
        load()
    }

    fun load() {
        _state.value = ArtistUiState.Loading
        viewModelScope.launch {
            val artist = async { catalog.artist(id) }
            val tracks = async { catalog.artistTracks(id) }
            val a = artist.await()
            val t = tracks.await()
            _state.value = if (a is ApiResult.Ok && t is ApiResult.Ok) ArtistUiState.Ready(a.value, t.value) else ArtistUiState.Failed
        }
    }

    /** « Écouter au hasard » : toutes ses pistes, mélangées. */
    fun shuffle() = player.playNow(playable().shuffled().mapNotNull(MediaItems::fromTrack))

    fun play(track: TrackDto) {
        val list = playable()
        player.playNow(list.mapNotNull(MediaItems::fromTrack), list.indexOfFirst { it.id == track.id }.coerceAtLeast(0))
    }

    fun playNext(track: TrackDto) = player.playNext(listOfNotNull(MediaItems.fromTrack(track)))
    fun addToQueue(track: TrackDto) = player.addToQueue(listOfNotNull(MediaItems.fromTrack(track)))

    private fun playable() = (_state.value as? ArtistUiState.Ready)?.tracks.orEmpty().filter { it.available }

    companion object {
        const val ARG_ID = "id"
    }
}

/**
 * Fiche artiste (capture `08`) : nom, « Écouter au hasard », section Albums
 * (et « Apparaît sur »), section Pistes avec pochettes ; cœur à côté de
 * « Écouter au hasard » (F9).
 */
@Composable
fun ArtistScreen(
    onBack: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
    contentPadding: PaddingValues,
    viewModel: ArtistViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val player by viewModel.playerState.collectAsStateWithLifecycle()
    val colors = Rengaine.colors
    val type = Rengaine.type
    var addTracks by remember { mutableStateOf<List<Long>?>(null) }

    when (val s = state) {
        ArtistUiState.Loading -> Box(Modifier.fillMaxSize().padding(contentPadding)) { LoadingState() }
        ArtistUiState.Failed -> Box(Modifier.fillMaxSize().padding(contentPadding)) { ErrorState(onRetry = viewModel::load) }
        is ArtistUiState.Ready -> {
            val artist = s.artist
            val playNextLabel = stringResource(R.string.action_play_next)
            val addQueueLabel = stringResource(R.string.action_add_to_queue)
            val addPlaylistLabel = stringResource(R.string.action_add_to_playlist)
            LazyColumn(
                contentPadding = PaddingValues(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding() + 16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "header") {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), tint = colors.text)
                        }
                        Text(artist.name, style = type.pageTitle, color = colors.text, modifier = Modifier.weight(1f).semantics { heading() })
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp),
                    ) {
                        if (s.tracks.any { it.available }) {
                            RengaineButton(
                                text = stringResource(R.string.home_play_random),
                                onClick = viewModel::shuffle,
                                icon = Icons.Filled.Shuffle,
                            )
                        }
                        FavoriteButton(FavoriteKey(FavoriteKind.ARTIST, artist.id), fromServer = artist.favorite)
                    }
                }
                albumSection("albums", R.string.artist_albums, artist.albums, onOpenAlbum)
                albumSection("appears", R.string.artist_appears_on, artist.appearsOn, onOpenAlbum)
                if (s.tracks.isNotEmpty()) {
                    item(key = "tracks-title") { SectionTitle(stringResource(R.string.artist_tracks)) }
                    items(s.tracks, key = { "t-${it.id}" }) { track ->
                        TrackRow(
                            track = track,
                            isCurrent = player.trackId == track.id,
                            isPlaying = player.isPlaying,
                            subtitle = listOfNotNull(track.artistCredit.takeIf { it.isNotBlank() }, track.album?.title).joinToString(" · "),
                            onPlay = { viewModel.play(track) },
                            showCover = true,
                            actions = listOf(
                                TrackAction(playNextLabel, Icons.AutoMirrored.Filled.PlaylistPlay) { viewModel.playNext(track) },
                                TrackAction(addQueueLabel, Icons.AutoMirrored.Filled.PlaylistAdd) { viewModel.addToQueue(track) },
                                TrackAction(addPlaylistLabel, Icons.Filled.Add) { addTracks = listOf(track.id) },
                            ),
                        )
                    }
                }
            }
        }
    }
    addTracks?.let { ids -> AddToPlaylistDialog(trackIds = ids, onDismiss = { addTracks = null }) }
}

/** Albums en rangée horizontale : jamais un `Row` nu (convention du projet). */
private fun androidx.compose.foundation.lazy.LazyListScope.albumSection(
    key: String,
    title: Int,
    albums: List<AlbumSummaryDto>,
    onOpenAlbum: (Long) -> Unit,
) {
    if (albums.isEmpty()) return
    item(key = "$key-title") { SectionTitle(stringResource(title)) }
    item(key = "$key-row") {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(albums, key = { it.id }) { album ->
                AlbumCard(album, onClick = { onOpenAlbum(album.id) }, modifier = Modifier.width(160.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = Rengaine.type.sectionTitle,
        color = Rengaine.colors.text,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 10.dp).semantics { heading() },
    )
}
