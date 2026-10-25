package app.rengaine.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rengaine.R
import app.rengaine.ui.components.ErrorState
import app.rengaine.ui.components.LoadingState
import app.rengaine.ui.components.MessageState
import app.rengaine.ui.components.TrackAction
import app.rengaine.ui.components.TrackRow
import app.rengaine.ui.library.AlbumCard
import app.rengaine.ui.playlists.AddToPlaylistDialog
import app.rengaine.ui.theme.Rengaine

/**
 * Tous les résultats (capture `09`, page `/recherche` du site) : titre
 * « « ma » », puis Artistes (lignes), Albums (grille de deux), Pistes
 * (pochette, artiste · album, durée, menu ⋮). 50 par groupe.
 */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenArtist: (Long) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    contentPadding: PaddingValues,
    viewModel: SearchResultsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val player by viewModel.playerState.collectAsStateWithLifecycle()
    val colors = Rengaine.colors
    var addTracks by remember { mutableStateOf<List<Long>?>(null) }
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
                Text(
                    stringResource(R.string.search_title, viewModel.query.trim()),
                    style = Rengaine.type.pageTitle,
                    color = colors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
            }
        }
        when (val s = state) {
            SearchUiState.Idle, SearchUiState.Loading -> item { LoadingState() }
            SearchUiState.Failed -> item { ErrorState(onRetry = viewModel::load) }
            is SearchUiState.Ready -> {
                val r = s.results
                if (s.isEmpty) item { MessageState(stringResource(R.string.search_nothing)) }
                if (r.artists.isNotEmpty()) {
                    item(key = "artists") { SectionTitle(stringResource(R.string.search_artists)) }
                    items(r.artists, key = { "a-${it.id}" }) { a ->
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            Text(
                                a.name,
                                style = Rengaine.type.body,
                                color = colors.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .clickable { onOpenArtist(a.id) }
                                    .padding(vertical = 14.dp),
                            )
                            HorizontalDivider(color = colors.line)
                        }
                    }
                }
                if (r.albums.isNotEmpty()) {
                    item(key = "albums") { SectionTitle(stringResource(R.string.search_albums)) }
                    // Grille de deux, comme la capture : rangées fixes, pas de défilement horizontal.
                    items(r.albums.chunked(2), key = { row -> "b-${row.first().id}" }) { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            row.forEach { album ->
                                AlbumCard(album, onClick = { onOpenAlbum(album.id) }, modifier = Modifier.weight(1f))
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                if (r.tracks.isNotEmpty()) {
                    item(key = "tracks") { SectionTitle(stringResource(R.string.search_tracks)) }
                    items(r.tracks, key = { "t-${it.id}" }) { track ->
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

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = Rengaine.type.sectionTitle,
        color = Rengaine.colors.text,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp).semantics { heading() },
    )
}
