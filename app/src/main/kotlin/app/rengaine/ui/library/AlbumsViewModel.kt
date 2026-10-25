package app.rengaine.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.rengaine.data.local.SessionStore
import app.rengaine.data.remote.ApiResult
import app.rengaine.data.remote.dto.AlbumSummaryDto
import app.rengaine.data.remote.dto.DecadeDto
import app.rengaine.data.remote.dto.GenreDto
import app.rengaine.data.remote.valueOrNull
import app.rengaine.data.repository.AlbumFilter
import app.rengaine.data.repository.AlbumSort
import app.rengaine.data.repository.CatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AlbumsUiState(
    val filter: AlbumFilter = AlbumFilter(),
    val albums: List<AlbumSummaryDto> = emptyList(),
    /** Nombre total d'albums correspondant aux filtres (titre « Albums 10 »). */
    val total: Int? = null,
    val loading: Boolean = false,
    /** Échec de la première page : écran d'erreur. */
    val failed: Boolean = false,
    /** Échec d'une page suivante : on garde la liste, on propose de réessayer en bas. */
    val moreFailed: Boolean = false,
    val genres: List<GenreDto> = emptyList(),
    val decades: List<DecadeDto> = emptyList(),
) {
    val endReached: Boolean get() = total != null && albums.size >= total
}

/**
 * Onglet Albums (F2, capture `05`) : grille paginée par `limit`/`offset`,
 * chargée au fil du défilement. Changer un filtre repart de la première page.
 */
@HiltViewModel
class AlbumsViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val catalog: CatalogRepository,
    private val session: SessionStore,
) : ViewModel() {

    /** Affichage en liste plutôt qu'en grille, gardé sur ce téléphone. */
    val asList: StateFlow<Boolean> = session.albumsAsList
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setAsList(asList: Boolean) {
        viewModelScope.launch { session.setAlbumsAsList(asList) }
    }

    // Liste ouverte depuis Explorer : genre, décennie ou collection imposés
    // (-1 = aucun, la navigation ne transporte pas les nombres nuls).
    private val initialFilter = AlbumFilter(
        genreId = savedState.get<Long>(ARG_GENRE)?.takeIf { it >= 0 },
        decade = savedState.get<Int>(ARG_DECADE)?.takeIf { it >= 0 },
        collectionId = savedState.get<Long>(ARG_COLLECTION)?.takeIf { it >= 0 },
    )

    private val _state = MutableStateFlow(AlbumsUiState(filter = initialFilter))
    val state: StateFlow<AlbumsUiState> = _state.asStateFlow()
    private var pageJob: Job? = null

    init {
        reload()
        loadFilters()
    }

    fun setSort(sort: AlbumSort) = applyFilter(_state.value.filter.copy(sort = sort))
    fun setGenre(genreId: Long?) = applyFilter(_state.value.filter.copy(genreId = genreId))
    fun setDecade(decade: Int?) = applyFilter(_state.value.filter.copy(decade = decade))

    fun retry() {
        if (_state.value.albums.isEmpty()) reload() else loadMore()
        if (_state.value.genres.isEmpty()) loadFilters()
    }

    /** Appelé par la grille quand on approche de la fin. */
    fun loadMore() {
        val s = _state.value
        if (s.loading || s.endReached || s.failed) return
        loadPage(offset = s.albums.size)
    }

    private fun applyFilter(filter: AlbumFilter) {
        if (filter == _state.value.filter) return
        _state.update { it.copy(filter = filter) }
        reload()
    }

    private fun reload() {
        pageJob?.cancel()
        _state.update { it.copy(albums = emptyList(), total = null, failed = false, moreFailed = false, loading = false) }
        loadPage(offset = 0)
    }

    private fun loadPage(offset: Int) {
        val filter = _state.value.filter
        _state.update { it.copy(loading = true, moreFailed = false) }
        pageJob = viewModelScope.launch {
            when (val result = catalog.albums(filter, offset)) {
                is ApiResult.Ok -> _state.update {
                    // Un filtre changé pendant le chargement : réponse périmée.
                    if (it.filter != filter) return@update it
                    val page = result.value
                    it.copy(
                        albums = (it.albums + page.items).distinctBy { a -> a.id },
                        total = page.total,
                        loading = false,
                    )
                }
                is ApiResult.Err -> _state.update {
                    if (offset == 0) it.copy(loading = false, failed = true) else it.copy(loading = false, moreFailed = true)
                }
            }
        }
    }

    companion object {
        const val ARG_GENRE = "genreId"
        const val ARG_DECADE = "decade"
        const val ARG_COLLECTION = "collectionId"
    }

    private fun loadFilters() {
        viewModelScope.launch {
            val genres = catalog.genres().valueOrNull().orEmpty()
                // Seuls les genres qui ont des albums ; les sous-genres
                // restent proposés (un genre parent comprend les siens).
                .filter { it.albumCount > 0 }
                .sortedBy { it.name.lowercase() }
            val decades = catalog.decades().valueOrNull().orEmpty().filter { it.albumCount > 0 }
            _state.update { it.copy(genres = genres, decades = decades) }
        }
    }
}
