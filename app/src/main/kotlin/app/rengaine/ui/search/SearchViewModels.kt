package app.rengaine.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.rengaine.data.remote.ApiResult
import app.rengaine.data.remote.dto.SearchResultsDto
import app.rengaine.data.remote.dto.TrackDto
import app.rengaine.data.repository.CatalogRepository
import app.rengaine.playback.MediaItems
import app.rengaine.playback.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data object Failed : SearchUiState
    data class Ready(val results: SearchResultsDto) : SearchUiState {
        val isEmpty get() = results.artists.isEmpty() && results.albums.isEmpty() && results.tracks.isEmpty()
    }
}

/**
 * Champ de l'en-tête et sa liste déroulante (F3), comme `SearchBox.jsx` du
 * site : 200 ms d'attente après la dernière frappe, texte sans espaces
 * autour, une lettre suffit, 6 résultats par groupe.
 */
@HiltViewModel
class SearchBoxViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val player: PlayerController,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()
    private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val state = _state.asStateFlow()
    private var job: Job? = null

    fun setQuery(text: String) {
        _query.value = text
        job?.cancel()
        val q = text.trim()
        if (q.isEmpty()) {
            _state.value = SearchUiState.Idle
            return
        }
        job = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            if (_state.value !is SearchUiState.Ready) _state.value = SearchUiState.Loading
            _state.value = when (val r = catalog.search(q, SUGGESTIONS)) {
                is ApiResult.Ok -> SearchUiState.Ready(r.value)
                is ApiResult.Err -> SearchUiState.Failed
            }
        }
    }

    /** Un résultat choisi ou Entrée : le champ se vide, comme sur le site. */
    fun clear() = setQuery("")

    fun play(tracks: List<TrackDto>, track: TrackDto) = playFrom(player, tracks, track)

    companion object {
        const val DEBOUNCE_MS = 200L
        const val SUGGESTIONS = 6
    }
}

/** Page « Tous les résultats » (`/recherche?q=…` du site) : 50 par groupe. */
@HiltViewModel
class SearchResultsViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val catalog: CatalogRepository,
    private val player: PlayerController,
) : ViewModel() {
    val query: String = savedState.get<String>(ARG_QUERY).orEmpty()
    private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Loading)
    val state = _state.asStateFlow()
    val playerState = player.state

    init {
        load()
    }

    fun load() {
        _state.value = SearchUiState.Loading
        viewModelScope.launch {
            _state.value = when (val r = catalog.search(query.trim(), PAGE)) {
                is ApiResult.Ok -> SearchUiState.Ready(r.value)
                is ApiResult.Err -> SearchUiState.Failed
            }
        }
    }

    fun play(track: TrackDto) {
        val tracks = (_state.value as? SearchUiState.Ready)?.results?.tracks.orEmpty()
        playFrom(player, tracks, track)
    }

    fun playNext(track: TrackDto) = player.playNext(listOfNotNull(MediaItems.fromTrack(track)))
    fun addToQueue(track: TrackDto) = player.addToQueue(listOfNotNull(MediaItems.fromTrack(track)))

    companion object {
        const val ARG_QUERY = "query"
        const val PAGE = 50
    }
}

/**
 * Toucher une piste joue **toutes les pistes trouvées** à partir de
 * celle-ci et remplace la file (`playTracks(résultats, index)` du site),
 * sans les pistes indisponibles.
 */
private fun playFrom(player: PlayerController, tracks: List<TrackDto>, track: TrackDto) {
    val playable = tracks.filter { it.available }
    val items = playable.mapNotNull(MediaItems::fromTrack)
    if (items.isEmpty()) return
    player.playNow(items, playable.indexOfFirst { it.id == track.id }.coerceAtLeast(0))
}
