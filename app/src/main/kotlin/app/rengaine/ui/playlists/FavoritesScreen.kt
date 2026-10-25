package app.rengaine.ui.playlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.style.TextOverflow
import app.rengaine.data.remote.dto.ArtistSummaryDto
import app.rengaine.R
import app.rengaine.data.remote.ApiResult
import app.rengaine.data.remote.dto.AlbumSummaryDto
import app.rengaine.data.remote.dto.TrackDto
import app.rengaine.data.repository.FavoritesRepository
import app.rengaine.data.repository.PlaylistRepository
import app.rengaine.playback.MediaItems
import app.rengaine.playback.PlayerController
import app.rengaine.ui.components.ErrorState
import app.rengaine.ui.components.LinkButton
import app.rengaine.ui.components.LoadingState
import app.rengaine.ui.components.MessageState
import app.rengaine.ui.components.TrackAction
import app.rengaine.ui.components.TrackRow
import app.rengaine.ui.library.AlbumCard
import app.rengaine.ui.theme.Rengaine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FavoritesUiState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val tracks: List<TrackDto> = emptyList(),
    val albums: List<AlbumSummaryDto> = emptyList(),
    val artists: List<ArtistSummaryDto> = emptyList(),
)

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val repository: PlaylistRepository,
    private val player: PlayerController,
    favorites: FavoritesRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(FavoritesUiState())
    val state = _state.asStateFlow()
    val playerState = player.state

    init {
        load()
        // Un cœur touché ailleurs (lecteur, album) : la liste suit.
        viewModelScope.launch { favorites.changed.collect { load(quiet = true) } }
    }

    /** [quiet] : on garde la liste affichée pendant le rechargement. */
    fun load(quiet: Boolean = false) {
        if (!quiet) _state.value = FavoritesUiState()
        viewModelScope.launch {
            val tracks = async { repository.favoriteTracks() }
            val albums = async { repository.favoriteAlbums() }
            val artists = async { repository.favoriteArtists() }
            val t = tracks.await()
            val a = albums.await()
            val r = artists.await()
            _state.value = if (t is ApiResult.Ok && a is ApiResult.Ok && r is ApiResult.Ok) {
                FavoritesUiState(loading = false, tracks = t.value, albums = a.value, artists = r.value)
            } else if (quiet && !_state.value.loading) {
                _state.value
            } else {
                FavoritesUiState(loading = false, failed = true)
            }
        }
    }

    fun play(track: TrackDto) {
        val list = _state.value.tracks.filter { it.available }
        player.playNow(list.mapNotNull(MediaItems::fromTrack), list.indexOfFirst { it.id == track.id }.coerceAtLeast(0))
    }

    fun playNext(track: TrackDto) = player.playNext(listOfNotNull(MediaItems.fromTrack(track)))
    fun addToQueue(track: TrackDto) = player.addToQueue(listOfNotNull(MediaItems.fromTrack(track)))
}

/**
 * Mes favoris (capture `16`) : onglets Pistes / Albums / Artistes, avec
 * le nombre quand il y en a ; états vides du site.
 */
@Composable
fun FavoritesScreen(
    onBack: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
    onOpenHistory: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val player by viewModel.playerState.collectAsStateWithLifecycle()
    val colors = Rengaine.colors
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var addTracks by remember { mutableStateOf<List<Long>?>(null) }

    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), tint = colors.text)
            }
            Text(
                stringResource(R.string.favorites_title),
                style = Rengaine.type.pageTitle,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            // À droite du titre, comme le site (2.26.0).
            LinkButton(text = stringResource(R.string.history_title), onClick = onOpenHistory, modifier = Modifier.padding(end = 8.dp))
        }
        SecondaryTabRow(
            selectedTabIndex = tab,
            containerColor = colors.bg,
            contentColor = colors.text,
            indicator = {
                TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tab), color = colors.accent)
            },
            divider = {},
        ) {
            listOf(
                tabLabel(R.string.search_tracks, R.string.favorites_tracks, state.tracks.size),
                tabLabel(R.string.search_albums, R.string.favorites_albums, state.albums.size),
                tabLabel(R.string.search_artists, R.string.favorites_artists, state.artists.size),
            ).forEachIndexed { i, label ->
                Tab(
                    selected = tab == i,
                    onClick = { tab = i },
                    text = { Text(label, style = Rengaine.type.button, color = if (tab == i) colors.accent else colors.muted) },
                )
            }
        }
        val bottom = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 16.dp)
        when {
            state.loading -> LoadingState()
            state.failed -> ErrorState(onRetry = { viewModel.load() })
            tab == 0 && state.tracks.isEmpty() -> MessageState(stringResource(R.string.favorites_no_tracks))
            tab == 1 && state.albums.isEmpty() -> MessageState(stringResource(R.string.favorites_no_albums))
            tab == 2 && state.artists.isEmpty() -> MessageState(stringResource(R.string.favorites_no_artists))
            tab == 2 -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.artists, key = { it.id }) { artist ->
                    Column {
                        Text(
                            artist.name,
                            style = Rengaine.type.body,
                            color = colors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .clickable { onOpenArtist(artist.id) }
                                .padding(vertical = 14.dp),
                        )
                        HorizontalDivider(color = colors.line)
                    }
                }
            }
            tab == 0 -> {
                val playNextLabel = stringResource(R.string.action_play_next)
                val addQueueLabel = stringResource(R.string.action_add_to_queue)
                val addPlaylistLabel = stringResource(R.string.action_add_to_playlist)
                LazyColumn(contentPadding = bottom, modifier = Modifier.fillMaxSize()) {
                    items(state.tracks, key = { it.id }) { track ->
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
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(state.albums, key = { it.id }) { album -> AlbumCard(album, onClick = { onOpenAlbum(album.id) }) }
            }
        }
    }
    addTracks?.let { ids -> AddToPlaylistDialog(trackIds = ids, onDismiss = { addTracks = null }) }
}

/** « Pistes » seul s'il n'y en a pas, « Albums 3 » sinon (capture `16`). */
@Composable
private fun tabLabel(name: Int, withCount: Int, count: Int): String =
    if (count == 0) stringResource(name) else stringResource(withCount, count)
