package app.rengaine.ui.library

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import app.rengaine.data.remote.dto.CollectionDto
import app.rengaine.data.remote.dto.DecadeDto
import app.rengaine.data.remote.dto.GenreDto
import app.rengaine.data.remote.dto.MoodDto
import app.rengaine.data.repository.CatalogRepository
import app.rengaine.data.repository.RandomFilter
import app.rengaine.playback.InfiniteMode
import app.rengaine.ui.components.ErrorState
import app.rengaine.ui.components.LoadingState
import app.rengaine.ui.components.RengaineButton
import app.rengaine.ui.theme.Rengaine
import app.rengaine.ui.theme.RengaineShapes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ExploreUiState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val moods: List<MoodDto> = emptyList(),
    val collections: List<CollectionDto> = emptyList(),
    val genres: List<GenreDto> = emptyList(),
    val decades: List<DecadeDto> = emptyList(),
)

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val infinite: InfiniteMode,
) : ViewModel() {
    private val _state = MutableStateFlow(ExploreUiState())
    val state = _state.asStateFlow()
    private val _nothing = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Le tirage n'a rien donné (filtre vide) : on le dit. */
    val nothing = _nothing.asSharedFlow()

    init {
        load()
    }

    fun load() {
        _state.value = ExploreUiState()
        viewModelScope.launch {
            val moods = async { catalog.moods() }
            val collections = async { catalog.collections() }
            val genres = async { catalog.genres() }
            val decades = async { catalog.decades() }
            val g = genres.await()
            val d = decades.await()
            // Ambiances et collections sont facultatives (P2) : leur échec masque la section.
            val m = (moods.await() as? ApiResult.Ok)?.value.orEmpty()
            val c = (collections.await() as? ApiResult.Ok)?.value.orEmpty()
            _state.value = if (g is ApiResult.Ok && d is ApiResult.Ok) {
                ExploreUiState(
                    loading = false,
                    moods = m.filter { it.trackCount > 0 },
                    collections = c.filter { it.albumCount > 0 },
                    genres = g.value.filter { it.albumCount > 0 || it.trackCount > 0 }.sortedBy { it.name.lowercase() },
                    decades = d.value.filter { it.albumCount > 0 },
                )
            } else {
                ExploreUiState(loading = false, failed = true)
            }
        }
    }

    /** ∞ : mode infini ciblé ; le premier lot remplace la file (F5). */
    fun startInfinite(filter: RandomFilter) {
        viewModelScope.launch { if (!infinite.start(filter)) _nothing.emit(Unit) }
    }
}

/**
 * Explorer (captures `18-explorer-1`, `-2`) : « Explorer » et « ∞ Tout, au
 * hasard » ; Ambiances (cartes), Collections (masquée si vide), Genres,
 * Décennies. Chaque entrée se parcourt (albums) ou se lance en mode infini.
 * Les ambiances n'ont pas d'albums propres : elles se lancent seulement.
 */
@Composable
fun ExploreScreen(
    contentPadding: PaddingValues,
    onBrowse: (title: String, genreId: Long?, decade: Int?, collectionId: Long?) -> Unit,
    viewModel: ExploreViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = Rengaine.colors
    val context = LocalContext.current
    val nothingText = stringResource(R.string.explore_nothing)
    LaunchedEffect(Unit) { viewModel.nothing.collect { Toast.makeText(context, nothingText, Toast.LENGTH_SHORT).show() } }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.tab_explore), style = Rengaine.type.pageTitle, color = colors.text, modifier = Modifier.weight(1f).semantics { heading() })
                RengaineButton(
                    text = stringResource(R.string.explore_everything),
                    onClick = { viewModel.startInfinite(RandomFilter()) },
                    icon = Icons.Filled.AllInclusive,
                )
            }
        }
        when {
            state.loading -> item { LoadingState() }
            state.failed -> item { ErrorState(onRetry = viewModel::load) }
            else -> {
                if (state.moods.isNotEmpty()) {
                    item(key = "moods") { Section(stringResource(R.string.explore_moods)) }
                    item(key = "mood-grid") {
                        MoodGrid(state.moods, onStart = { mood -> viewModel.startInfinite(RandomFilter(mood = mood.id)) })
                    }
                }
                if (state.collections.isNotEmpty()) {
                    item(key = "collections") { Section(stringResource(R.string.explore_collections)) }
                    items(state.collections, key = { "c-${it.id}" }) { c ->
                        EntryRow(
                            title = c.name,
                            meta = pluralStringResource(R.plurals.album_count, c.albumCount, c.albumCount),
                            onBrowse = { onBrowse(c.name, null, null, c.id) },
                            onStart = { viewModel.startInfinite(RandomFilter(collectionId = c.id)) },
                        )
                    }
                }
                if (state.genres.isNotEmpty()) {
                    item(key = "genres") { Section(stringResource(R.string.explore_genres)) }
                    items(state.genres, key = { "g-${it.id}" }) { g ->
                        EntryRow(
                            title = g.name,
                            meta = countLine(g.albumCount, g.trackCount),
                            onBrowse = { onBrowse(g.name, g.id, null, null) },
                            onStart = { viewModel.startInfinite(RandomFilter(genreIds = listOf(g.id))) },
                        )
                    }
                }
                if (state.decades.isNotEmpty()) {
                    item(key = "decades") { Section(stringResource(R.string.explore_decades)) }
                    items(state.decades, key = { "d-${it.decade}" }) { d ->
                        val title = stringResource(R.string.decade_label, d.decade)
                        EntryRow(
                            title = title,
                            meta = countLine(d.albumCount, d.trackCount),
                            onBrowse = { onBrowse(title, null, d.decade, null) },
                            onStart = { viewModel.startInfinite(RandomFilter(yearFrom = d.decade, yearTo = d.decade + 9)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun countLine(albums: Int, tracks: Int): String =
    pluralStringResource(R.plurals.album_count, albums, albums) + " · " + pluralStringResource(R.plurals.track_count, tracks, tracks)

@Composable
private fun Section(text: String) {
    Text(
        text,
        style = Rengaine.type.sectionTitle,
        color = Rengaine.colors.text,
        modifier = Modifier.padding(top = 16.dp).semantics { heading() },
    )
}

/** Ligne de genre, décennie ou collection : toucher la ligne parcourt les albums, ∞ lance le mode infini. */
@Composable
private fun EntryRow(title: String, meta: String, onBrowse: () -> Unit, onStart: () -> Unit) {
    val colors = Rengaine.colors
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onBrowse).padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = Rengaine.type.cardTitle, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(meta, style = Rengaine.type.secondary, color = colors.muted, modifier = Modifier.padding(horizontal = 8.dp))
            IconButton(onClick = onStart) {
                Icon(Icons.Filled.AllInclusive, stringResource(R.string.explore_start_infinite, title), tint = colors.text)
            }
        }
        HorizontalDivider(color = colors.line)
    }
}
