package app.rengaine.ui.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.rengaine.R
import app.rengaine.data.remote.ApiResult
import app.rengaine.data.remote.dto.PlaylistSummaryDto
import app.rengaine.data.repository.PlaylistRepository
import app.rengaine.ui.components.ErrorState
import app.rengaine.ui.components.LoadingState
import app.rengaine.ui.components.MosaicCover
import app.rengaine.ui.components.RengaineButton
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

sealed interface PlaylistsUiState {
    data object Loading : PlaylistsUiState
    data object Failed : PlaylistsUiState
    data class Ready(val mine: List<PlaylistSummaryDto>, val shared: List<PlaylistSummaryDto>) : PlaylistsUiState
}

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val repository: PlaylistRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<PlaylistsUiState>(PlaylistsUiState.Loading)
    val state = _state.asStateFlow()
    private val _opened = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    /** Playlist tout juste créée : on l'ouvre. */
    val opened = _opened.asSharedFlow()

    init {
        load()
        viewModelScope.launch { repository.changed.collect { load(quiet = true) } }
    }

    fun load(quiet: Boolean = false) {
        if (!quiet) _state.value = PlaylistsUiState.Loading
        viewModelScope.launch {
            when (val r = repository.playlists()) {
                is ApiResult.Ok -> _state.value = PlaylistsUiState.Ready(
                    mine = r.value.filter { it.mine },
                    shared = r.value.filter { !it.mine },
                )
                is ApiResult.Err -> if (!quiet) _state.value = PlaylistsUiState.Failed
            }
        }
    }

    fun create(name: String) {
        viewModelScope.launch {
            (repository.create(name) as? ApiResult.Ok)?.let { _opened.emit(it.value.id) }
        }
    }
}

/**
 * Onglet Playlists (capture `14`) : « Playlists » et « + Nouvelle
 * playlist » ; « Mes playlists » en grille, **« Mes favoris » en premier** ;
 * puis celles que les autres comptes partagent.
 */
@Composable
fun PlaylistsScreen(
    contentPadding: PaddingValues,
    onOpenPlaylist: (Long) -> Unit,
    onOpenFavorites: () -> Unit,
    viewModel: PlaylistsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = Rengaine.colors
    val type = Rengaine.type
    var creating by rememberSaveable { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.opened.collect(onOpenPlaylist) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.tab_playlists),
                    style = type.pageTitle,
                    color = colors.text,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                RengaineButton(text = stringResource(R.string.playlist_new), onClick = { creating = true }, icon = Icons.Filled.Add)
            }
        }
        when (val s = state) {
            PlaylistsUiState.Loading -> item(span = { GridItemSpan(maxLineSpan) }) { LoadingState() }
            PlaylistsUiState.Failed -> item(span = { GridItemSpan(maxLineSpan) }) { ErrorState(onRetry = { viewModel.load() }) }
            is PlaylistsUiState.Ready -> {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle(stringResource(R.string.playlists_mine)) }
                item(key = "favoris") { FavoritesCard(onClick = onOpenFavorites) }
                items(s.mine, key = { it.id }) { PlaylistCard(it, onClick = { onOpenPlaylist(it.id) }) }
                if (s.shared.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle(stringResource(R.string.playlists_shared)) }
                    items(s.shared, key = { it.id }) { PlaylistCard(it, onClick = { onOpenPlaylist(it.id) }) }
                }
            }
        }
    }

    if (creating) {
        PlaylistNameDialog(
            title = stringResource(R.string.playlist_new),
            initial = "",
            onConfirm = viewModel::create,
            onDismiss = { creating = false },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = Rengaine.type.sectionTitle,
        color = Rengaine.colors.text,
        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
    )
}

/** Carte « Mes favoris » : dégradé braise et cœur (capture `14`). */
@Composable
private fun FavoritesCard(onClick: () -> Unit) {
    val colors = Rengaine.colors
    Column(Modifier.clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RengaineShapes.cover)
                .background(Brush.linearGradient(listOf(colors.accent, colors.accentHover))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Favorite, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(64.dp))
        }
        Text(
            stringResource(R.string.favorites_title),
            style = Rengaine.type.cardTitle,
            color = colors.text,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(stringResource(R.string.favorites_subtitle), style = Rengaine.type.secondary, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PlaylistCard(playlist: PlaylistSummaryDto, onClick: () -> Unit) {
    val colors = Rengaine.colors
    val count = pluralStringResource(R.plurals.track_count, playlist.trackCount, playlist.trackCount)
    val subtitle = buildList {
        add(count)
        if (playlist.durationMs > 0) add(formatMinutes(playlist.durationMs))
        if (!playlist.mine) add(playlist.owner.displayName)
    }.joinToString(" · ")
    Column(Modifier.clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        MosaicCover(playlist.coverUrls, Modifier.fillMaxWidth().aspectRatio(1f))
        Text(
            playlist.name,
            style = Rengaine.type.cardTitle,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(subtitle, style = Rengaine.type.secondary, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
