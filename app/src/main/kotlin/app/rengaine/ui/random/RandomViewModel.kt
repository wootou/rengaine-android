package app.rengaine.ui.random

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.rengaine.data.remote.ApiResult
import app.rengaine.data.remote.dto.AccountSettings
import app.rengaine.data.remote.dto.AlbumSummaryDto
import app.rengaine.data.remote.dto.GenreDto
import app.rengaine.data.remote.dto.TrackDto
import app.rengaine.data.repository.AlbumFilter
import app.rengaine.data.repository.CatalogRepository
import app.rengaine.data.repository.RandomFilter
import app.rengaine.data.repository.SettingsRepository
import app.rengaine.domain.RandomChoice
import app.rengaine.playback.MediaItems
import app.rengaine.playback.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Albums d'un genre déplié : chargés à la demande. */
sealed interface GenreAlbums {
    data object Loading : GenreAlbums
    data object Failed : GenreAlbums
    data class Ready(val albums: List<AlbumSummaryDto>) : GenreAlbums
}

data class RandomUiState(
    val genresLoading: Boolean = true,
    val genresFailed: Boolean = false,
    val genres: List<GenreDto> = emptyList(),
    val choice: RandomChoice = RandomChoice(emptyList(), emptySet(), emptySet()),
    val albums: Map<Long, GenreAlbums> = emptyMap(),
    val expanded: Set<Long> = emptySet(),
    val panelOpen: Boolean = false,
    val count: Int = DEFAULT_COUNT,
    val drawing: Boolean = false,
    val drawFailed: Boolean = false,
    val tracks: List<TrackDto> = emptyList(),
    /** Le dernier tirage n'a rien donné. */
    val empty: Boolean = false,
    /** « Tout décocher » puis tirage : bloqué. */
    val noGenre: Boolean = false,
    /** Plafond de 500 albums mis de côté atteint. */
    val excludedFull: Boolean = false,
) {
    val durationMs: Long get() = tracks.sumOf { it.durationMs }

    companion object {
        const val DEFAULT_COUNT = 100
        val COUNTS = listOf(25, 50, 100, 200)
    }
}

/**
 * Page Aléatoire (F4, capture `10`), comme `Random.jsx` du site (précisé
 * par l'équipe du serveur le 2026-10-24) :
 *
 * - à l'ouverture, un tirage avec le réglage `random` du compte ; la liste
 *   tirée n'est gardée nulle part ;
 * - cocher ne fait que préparer : le réglage s'enregistre **au tirage**,
 *   et seulement s'il a changé ;
 * - le tirage envoie genres, albums mis de côté et nombre, **pas** les
 *   pistes de la file.
 */
@HiltViewModel
class RandomViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val settings: SettingsRepository,
    private val player: PlayerController,
) : ViewModel() {

    private val _state = MutableStateFlow(RandomUiState())
    val state: StateFlow<RandomUiState> = _state.asStateFlow()
    val playerState = player.state

    init {
        loadGenres(drawAfter = true)
    }

    fun retry() = loadGenres(drawAfter = _state.value.tracks.isEmpty())

    private fun loadGenres(drawAfter: Boolean) {
        _state.update { it.copy(genresLoading = true, genresFailed = false) }
        viewModelScope.launch {
            // La copie locale des réglages suffit ; le serveur les rafraîchit à part.
            val saved = withTimeoutOrNull(SETTINGS_WAIT_MS) { settings.settings.filterNotNull().first() }
            val result = catalog.genres()
            if (result !is ApiResult.Ok) {
                _state.update { it.copy(genresLoading = false, genresFailed = true) }
                return@launch
            }
            // Ceux qui ont des pistes, à plat, par nom.
            val genres = result.value.filter { it.trackCount > 0 }.sortedBy { it.name.lowercase() }
            val choice = RandomChoice.fromSetting(
                genreIds = genres.map { it.id },
                savedGenres = saved?.randomGenreIds.orEmpty(),
                savedExcluded = saved?.randomExcludedAlbumIds.orEmpty(),
            )
            _state.update {
                it.copy(
                    genresLoading = false,
                    genres = genres,
                    choice = choice,
                    count = saved?.randomCount?.takeIf { c -> c in RandomUiState.COUNTS } ?: it.count,
                )
            }
            if (drawAfter) draw(save = false)
        }
    }

    // --- Sélection -------------------------------------------------------------

    fun togglePanel() = _state.update { it.copy(panelOpen = !it.panelOpen) }

    fun toggleExpanded(genreId: Long) {
        val open = genreId !in _state.value.expanded
        _state.update { it.copy(expanded = if (open) it.expanded + genreId else it.expanded - genreId) }
        if (open && _state.value.albums[genreId] !is GenreAlbums.Ready) loadAlbums(genreId)
    }

    private fun loadAlbums(genreId: Long) {
        _state.update { it.copy(albums = it.albums + (genreId to GenreAlbums.Loading)) }
        viewModelScope.launch {
            val r = catalog.albums(AlbumFilter(genreId = genreId), offset = 0, limit = 500)
            val value = if (r is ApiResult.Ok) GenreAlbums.Ready(r.value.items) else GenreAlbums.Failed
            _state.update { it.copy(albums = it.albums + (genreId to value)) }
        }
    }

    fun retryAlbums(genreId: Long) = loadAlbums(genreId)

    fun toggleGenre(id: Long) = _state.update { it.copy(choice = it.choice.toggleGenre(id), noGenre = false) }

    fun toggleAlbum(id: Long) = _state.update {
        val full = id !in it.choice.excludedAlbums && !it.choice.canExclude()
        it.copy(choice = it.choice.toggleAlbum(id), excludedFull = full)
    }

    fun checkAll() = _state.update { it.copy(choice = it.choice.checkAll(), noGenre = false, excludedFull = false) }
    fun uncheckAll() = _state.update { it.copy(choice = it.choice.uncheckAll()) }

    fun setCount(count: Int) = _state.update { it.copy(count = count) }

    // --- Tirage ----------------------------------------------------------------

    /** « Générer une nouvelle sélection » : tire, puis enregistre la sélection si elle a changé. */
    fun draw() = draw(save = true)

    private fun draw(save: Boolean) {
        val s = _state.value
        if (s.drawing || s.genres.isEmpty()) return
        if (s.choice.noneChecked) {
            _state.update { it.copy(noGenre = true) }
            return
        }
        val genreIds = s.choice.genreFilter
        val excluded = s.choice.excludedAlbums.toList()
        _state.update { it.copy(drawing = true, drawFailed = false, empty = false, noGenre = false) }
        viewModelScope.launch {
            val result = catalog.randomTracks(
                count = s.count,
                filter = RandomFilter(genreIds = genreIds),
                excludeAlbums = excluded,
            )
            _state.update {
                when (result) {
                    is ApiResult.Ok -> it.copy(drawing = false, tracks = result.value, empty = result.value.isEmpty())
                    is ApiResult.Err -> it.copy(drawing = false, drawFailed = true)
                }
            }
            if (save) saveSelection(genreIds, excluded, s.count)
        }
    }

    /** `random` du compte, en gardant les champs qu'on ne connaît pas. */
    private suspend fun saveSelection(genreIds: List<Long>, excluded: List<Long>, count: Int) {
        val current = settings.settings.value
        if (current != null &&
            current.randomGenreIds.toSet() == genreIds.toSet() &&
            current.randomExcludedAlbumIds.toSet() == excluded.toSet() &&
            current.randomCount == count
        ) {
            return
        }
        val base = current?.raw?.get("random") as? JsonObject ?: JsonObject(emptyMap())
        val value = JsonObject(
            base + mapOf(
                "genreIds" to JsonArray(genreIds.map(::JsonPrimitive)),
                "excludedAlbumIds" to JsonArray(excluded.map(::JsonPrimitive)),
                "count" to JsonPrimitive(count),
            ),
        )
        settings.set(AccountSettings.PATH_RANDOM, value)
    }

    // --- Écoute ----------------------------------------------------------------

    /** « Tout écouter » : la sélection remplace la file (le mode infini garde son état). */
    fun playAll() = play(null)

    fun play(track: TrackDto?) {
        val list = _state.value.tracks.filter { it.available }
        val items = list.mapNotNull(MediaItems::fromTrack)
        if (items.isEmpty()) return
        player.playNow(items, track?.let { t -> list.indexOfFirst { it.id == t.id }.coerceAtLeast(0) } ?: 0)
    }

    /** « Ajouter à la file » : à la fin. */
    fun addAll() = player.addToQueue(_state.value.tracks.filter { it.available }.mapNotNull(MediaItems::fromTrack))

    fun playNext(track: TrackDto) = player.playNext(listOfNotNull(MediaItems.fromTrack(track)))
    fun addToQueue(track: TrackDto) = player.addToQueue(listOfNotNull(MediaItems.fromTrack(track)))

    private companion object {
        const val SETTINGS_WAIT_MS = 3_000L
    }
}
