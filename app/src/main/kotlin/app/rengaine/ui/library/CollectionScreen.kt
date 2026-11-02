package app.rengaine.ui.library

import android.widget.Toast
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import app.rengaine.data.local.SessionStore
import app.rengaine.data.remote.ApiError
import app.rengaine.data.remote.ApiResult
import app.rengaine.data.remote.dto.CollectionDetailDto
import app.rengaine.data.repository.CatalogRepository
import app.rengaine.data.repository.RandomFilter
import app.rengaine.playback.InfiniteMode
import app.rengaine.playback.MediaItems
import app.rengaine.playback.PlayerController
import app.rengaine.ui.components.ErrorState
import app.rengaine.ui.components.LoadingState
import app.rengaine.ui.components.MessageState
import app.rengaine.ui.components.MosaicCover
import app.rengaine.ui.theme.Rengaine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface CollectionUiState {
    data object Loading : CollectionUiState
    data object Failed : CollectionUiState
    data object NotFound : CollectionUiState
    data class Ready(val collection: CollectionDetailDto) : CollectionUiState
}

@HiltViewModel
class CollectionViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val catalog: CatalogRepository,
    private val player: PlayerController,
    private val infinite: InfiniteMode,
    private val session: SessionStore,
) : ViewModel() {
    private val id: Long = checkNotNull(savedState[ARG_ID])
    private val _state = MutableStateFlow<CollectionUiState>(CollectionUiState.Loading)
    val state = _state.asStateFlow()
    private val _nothing = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Le tirage n'a rien donné. */
    val nothing = _nothing.asSharedFlow()

    /** Grille ou liste : le même réglage que l'onglet Albums, comme le site. */
    val asList: StateFlow<Boolean> = session.albumsAsList.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setAsList(asList: Boolean) {
        viewModelScope.launch { session.setAlbumsAsList(asList) }
    }

    init {
        load()
    }

    fun load() {
        _state.value = CollectionUiState.Loading
        viewModelScope.launch {
            _state.value = when (val r = catalog.collection(id)) {
                is ApiResult.Ok -> CollectionUiState.Ready(r.value)
                is ApiResult.Err -> if ((r.error as? ApiError.Http)?.status == 404) CollectionUiState.NotFound else CollectionUiState.Failed
            }
        }
    }

    /** « Au hasard » : 100 pistes de la collection remplacent la file (`/random/tracks?collection=…&count=100`). */
    fun shuffle() = viewModelScope.launch {
        val tracks = (catalog.randomTracks(SHUFFLE_COUNT, RandomFilter(collectionId = id)) as? ApiResult.Ok)?.value.orEmpty()
        val items = tracks.mapNotNull(MediaItems::fromTrack)
        if (items.isEmpty()) _nothing.emit(Unit) else player.playNow(items)
    }

    /** « Écoute infinie dans la collection » : mode infini ciblé (F5). */
    fun startInfinite() = viewModelScope.launch {
        if (!infinite.start(RandomFilter(collectionId = id))) _nothing.emit(Unit)
    }

    companion object {
        const val ARG_ID = "id"
        const val SHUFFLE_COUNT = 100
    }
}

/**
 * Page d'une collection (`Collection.jsx` du site) : mosaïque, surtitre
 * « Collection », nom, description, « 12 albums » ; « Au hasard » (100
 * pistes) et « Écoute infinie dans la collection » ; albums dans l'ordre
 * choisi par l'admin, en grille ou en liste. « Modifier la collection »
 * (admin) reste sur le site.
 */
@Composable
fun CollectionScreen(
    onBack: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
    contentPadding: PaddingValues,
    viewModel: CollectionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val asList by viewModel.asList.collectAsStateWithLifecycle()
    val colors = Rengaine.colors
    val type = Rengaine.type
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.nothing.collect { Toast.makeText(context, R.string.explore_nothing, Toast.LENGTH_SHORT).show() }
    }

    LazyVerticalGrid(
        columns = if (asList) GridCells.Adaptive(minSize = 360.dp) else GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(if (asList) 4.dp else 18.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), tint = colors.text)
                }
            }
        }
        when (val s = state) {
            CollectionUiState.Loading -> item(span = { GridItemSpan(maxLineSpan) }) { LoadingState() }
            CollectionUiState.Failed -> item(span = { GridItemSpan(maxLineSpan) }) { ErrorState(onRetry = viewModel::load) }
            CollectionUiState.NotFound -> item(span = { GridItemSpan(maxLineSpan) }) {
                MessageState(stringResource(R.string.collection_not_found))
            }
            is CollectionUiState.Ready -> {
                val c = s.collection
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        MosaicCover(c.coverUrls, Modifier.widthIn(max = 240.dp).fillMaxWidth(0.6f).aspectRatio(1f))
                        Spacer(Modifier.size(20.dp))
                        Text(stringResource(R.string.collection_kind).uppercase(), style = type.overline, color = colors.muted)
                        Text(
                            c.name,
                            style = type.pageTitle,
                            color = colors.text,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).semantics { heading() },
                        )
                        c.description?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = type.body, color = colors.muted, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 6.dp))
                        }
                        val count = maxOf(c.albumCount, c.albums.size)
                        Text(pluralStringResource(R.plurals.album_count, count, count), style = type.secondary, color = colors.muted)

                        Row(
                            Modifier.fillMaxWidth().padding(top = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = { viewModel.setAsList(!asList) }) {
                                Icon(
                                    if (asList) Icons.Filled.GridView else Icons.AutoMirrored.Filled.ViewList,
                                    stringResource(if (asList) R.string.albums_show_grid else R.string.albums_show_list),
                                    tint = colors.text,
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { viewModel.startInfinite() }, enabled = c.albums.isNotEmpty()) {
                                Icon(Icons.Filled.AllInclusive, stringResource(R.string.collection_infinite), tint = colors.text)
                            }
                            Spacer(Modifier.size(8.dp))
                            // Bouton principal « Au hasard », comme le site (pas de « Écouter » : l'ordre de l'admin n'est pas une file).
                            val shuffleLabel = stringResource(R.string.collection_shuffle)
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .background(if (c.albums.isNotEmpty()) colors.accent else colors.faint)
                                    .clickable(enabled = c.albums.isNotEmpty(), role = Role.Button, onClickLabel = shuffleLabel) { viewModel.shuffle() },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.Shuffle, shuffleLabel, tint = colors.onAccent, modifier = Modifier.size(30.dp))
                            }
                        }
                    }
                }
                if (c.albums.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { MessageState(stringResource(R.string.collection_empty)) }
                }
                items(c.albums, key = { it.id }) { album ->
                    if (asList) AlbumRow(album, onClick = { onOpenAlbum(album.id) }) else AlbumCard(album, onClick = { onOpenAlbum(album.id) })
                }
            }
        }
    }
}
