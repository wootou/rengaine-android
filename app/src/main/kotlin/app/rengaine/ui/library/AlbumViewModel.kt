package app.rengaine.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import app.rengaine.data.remote.ApiResult
import app.rengaine.data.remote.dto.AlbumDetailDto
import app.rengaine.data.remote.dto.TrackDto
import app.rengaine.data.repository.CatalogRepository
import app.rengaine.playback.MediaItems
import app.rengaine.playback.PlayerController
import app.rengaine.playback.PlayerUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AlbumUiState {
    data object Loading : AlbumUiState
    data object Failed : AlbumUiState
    data class Ready(val album: AlbumDetailDto) : AlbumUiState
}

/** Page d'un album (F2, capture `07`). */
@HiltViewModel
class AlbumViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val catalog: CatalogRepository,
    private val player: PlayerController,
) : ViewModel() {

    private val albumId: Long = checkNotNull(savedState[ARG_ID])

    private val _state = MutableStateFlow<AlbumUiState>(AlbumUiState.Loading)
    val state: StateFlow<AlbumUiState> = _state.asStateFlow()
    val playerState: StateFlow<PlayerUiState> = player.state

    init {
        load()
    }

    fun load() {
        _state.value = AlbumUiState.Loading
        viewModelScope.launch {
            _state.value = when (val result = catalog.album(albumId)) {
                is ApiResult.Ok -> AlbumUiState.Ready(result.value)
                is ApiResult.Err -> AlbumUiState.Failed
            }
        }
    }

    /** Tout l'album, à partir de [track] si elle est donnée. */
    fun play(track: TrackDto? = null) {
        val tracks = playable()
        val start = track?.let { t -> tracks.indexOfFirst { it.id == t.id } }?.coerceAtLeast(0) ?: 0
        player.playNow(tracks.toItems(), start)
    }

    fun shuffle() = player.playNow(playable().shuffled().toItems())
    fun playNext(tracks: List<TrackDto> = playable()) = player.playNext(tracks.toItems())
    fun addToQueue(tracks: List<TrackDto> = playable()) = player.addToQueue(tracks.toItems())

    fun trackIds(): List<Long> = playable().map { it.id }

    private fun playable(): List<TrackDto> =
        (_state.value as? AlbumUiState.Ready)?.album?.tracks.orEmpty().filter { it.available }

    private fun List<TrackDto>.toItems(): List<MediaItem> = mapNotNull(MediaItems::fromTrack)

    companion object {
        const val ARG_ID = "id"
    }
}
