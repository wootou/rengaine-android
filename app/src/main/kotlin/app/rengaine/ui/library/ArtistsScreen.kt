package app.rengaine.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.rengaine.R
import app.rengaine.data.remote.ApiResult
import app.rengaine.data.remote.dto.ArtistSummaryDto
import app.rengaine.data.repository.CatalogRepository
import app.rengaine.ui.components.ErrorState
import app.rengaine.ui.components.LinkButton
import app.rengaine.ui.components.LoadingState
import app.rengaine.ui.components.MessageState
import app.rengaine.ui.components.RengaineTextField
import app.rengaine.ui.theme.Rengaine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ArtistsUiState(
    val query: String = "",
    val artists: List<ArtistSummaryDto> = emptyList(),
    val total: Int? = null,
    val loading: Boolean = false,
    val failed: Boolean = false,
    val moreFailed: Boolean = false,
) {
    val endReached: Boolean get() = total != null && artists.size >= total
}

/** Onglet Artistes (F2, capture `08a`) : liste filtrée par le serveur, par pages. */
@HiltViewModel
class ArtistsViewModel @Inject constructor(
    private val catalog: CatalogRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ArtistsUiState())
    val state = _state.asStateFlow()
    private var job: Job? = null

    init {
        reload(debounce = false)
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
        reload(debounce = true)
    }

    fun retry() = if (_state.value.artists.isEmpty()) reload(debounce = false) else loadMore()

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.endReached || s.failed) return
        load(offset = s.artists.size)
    }

    private fun reload(debounce: Boolean) {
        job?.cancel()
        _state.update { it.copy(artists = emptyList(), total = null, failed = false, moreFailed = false, loading = true) }
        job = viewModelScope.launch {
            // On attend la fin de la frappe avant d'interroger le serveur.
            if (debounce) delay(300)
            fetch(0)
        }
    }

    private fun load(offset: Int) {
        _state.update { it.copy(loading = true, moreFailed = false) }
        job = viewModelScope.launch { fetch(offset) }
    }

    private suspend fun fetch(offset: Int) {
        val query = _state.value.query
        when (val r = catalog.artists(query, offset)) {
            is ApiResult.Ok -> _state.update {
                if (it.query != query) return@update it
                it.copy(artists = (it.artists + r.value.items).distinctBy { a -> a.id }, total = r.value.total, loading = false)
            }
            is ApiResult.Err -> _state.update {
                if (offset == 0) it.copy(loading = false, failed = true) else it.copy(loading = false, moreFailed = true)
            }
        }
    }
}

/**
 * Onglet Artistes (capture `08a`) : « Artistes » et le nombre, champ
 * « Filtrer… », lignes à initiale, mention « invité » pour un artiste
 * présent seulement en featuring (aucun album à son nom).
 */
@Composable
fun ArtistsScreen(
    contentPadding: PaddingValues,
    onOpenArtist: (Long) -> Unit,
    viewModel: ArtistsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = Rengaine.colors
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 15
        }
    }
    LaunchedEffect(nearEnd, state.artists.size) { if (nearEnd) viewModel.loadMore() }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.tab_artists), style = Rengaine.type.pageTitle, color = colors.text, modifier = Modifier.semantics { heading() })
                    state.total?.let { Text(it.toString(), style = Rengaine.type.numeric, color = colors.muted, modifier = Modifier.padding(bottom = 4.dp)) }
                }
                RengaineTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    label = stringResource(R.string.artists_filter_label),
                    placeholder = stringResource(R.string.artists_filter),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        when {
            state.failed -> item { ErrorState(onRetry = viewModel::retry) }
            state.artists.isEmpty() && state.loading -> item { LoadingState() }
            state.artists.isEmpty() && state.total == 0 -> item {
                MessageState(stringResource(if (state.query.isBlank()) R.string.artists_none else R.string.artists_none_filtered))
            }
            else -> {
                items(state.artists, key = { it.id }) { artist ->
                    ArtistRow(artist, onClick = { onOpenArtist(artist.id) })
                }
                if (state.moreFailed) {
                    item { LinkButton(text = stringResource(R.string.action_retry), onClick = viewModel::retry) }
                } else if (state.loading) {
                    item { LoadingState() }
                }
            }
        }
    }
}

@Composable
private fun ArtistRow(artist: ArtistSummaryDto, onClick: () -> Unit) {
    val colors = Rengaine.colors
    val initial = artist.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(colors.surface), contentAlignment = Alignment.Center) {
                Text(initial, style = Rengaine.type.bodyStrong, color = colors.muted)
            }
            Text(artist.name, style = Rengaine.type.body, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (artist.albumCount == 0) {
                Text(stringResource(R.string.artist_guest), style = Rengaine.type.secondary, color = colors.muted)
            }
        }
        HorizontalDivider(color = colors.line)
    }
}
