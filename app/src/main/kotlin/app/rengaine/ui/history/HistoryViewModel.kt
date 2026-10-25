package app.rengaine.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.rengaine.data.remote.ApiResult
import app.rengaine.data.remote.dto.PlayHistoryItemDto
import app.rengaine.data.repository.HistoryRepository
import app.rengaine.playback.MediaItems
import app.rengaine.playback.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryUiState(
    val items: List<PlayHistoryItemDto> = emptyList(),
    /** Nombre d'écoutes du compte ; diminue à chaque effacement. */
    val total: Int? = null,
    val loading: Boolean = false,
    val failed: Boolean = false,
    val moreFailed: Boolean = false,
    val clearing: Boolean = false,
) {
    val endReached: Boolean get() = total != null && items.size >= total
}

/** Message bref affiché après une action. */
enum class HistoryMessage { DELETE_FAILED, CLEARED, CLEAR_FAILED }

/**
 * Historique d'écoute (contrat 2.26.0), comme `History.jsx` du site :
 * pages de 100 chargées au défilement, une écoute s'efface sans
 * confirmation (un `404` la retire aussi : déjà effacée ailleurs), « Tout
 * effacer » après confirmation. Toucher une écoute joue cette piste seule.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val history: HistoryRepository,
    private val player: PlayerController,
) : ViewModel() {
    private val _state = MutableStateFlow(HistoryUiState())
    val state = _state.asStateFlow()
    val playerState = player.state
    private val _messages = MutableSharedFlow<HistoryMessage>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()

    init {
        reload()
    }

    fun reload() {
        _state.value = HistoryUiState()
        load(offset = 0)
    }

    fun retry() = if (_state.value.items.isEmpty()) reload() else loadMore()

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.endReached || s.failed) return
        load(offset = s.items.size)
    }

    private fun load(offset: Int) {
        _state.update { it.copy(loading = true, moreFailed = false) }
        viewModelScope.launch {
            when (val r = history.page(offset)) {
                is ApiResult.Ok -> _state.update {
                    it.copy(items = (it.items + r.value.items).distinctBy { p -> p.id }, total = r.value.total, loading = false)
                }
                is ApiResult.Err -> _state.update {
                    if (offset == 0) it.copy(loading = false, failed = true) else it.copy(loading = false, moreFailed = true)
                }
            }
        }
    }

    /** Croix : retirée tout de suite de la liste, remise si l'effacement échoue. */
    fun delete(play: PlayHistoryItemDto) {
        val before = _state.value
        _state.update { s -> s.copy(items = s.items.filterNot { it.id == play.id }, total = s.total?.minus(1)) }
        viewModelScope.launch {
            if (!history.delete(play.id)) {
                _state.update { s -> s.copy(items = before.items, total = before.total) }
                _messages.emit(HistoryMessage.DELETE_FAILED)
            }
        }
    }

    fun clearAll() {
        if (_state.value.clearing) return
        _state.update { it.copy(clearing = true) }
        viewModelScope.launch {
            val deleted = history.clear()
            if (deleted != null) {
                _state.value = HistoryUiState(total = 0)
                _messages.emit(HistoryMessage.CLEARED)
            } else {
                _state.update { it.copy(clearing = false) }
                _messages.emit(HistoryMessage.CLEAR_FAILED)
            }
        }
    }

    /** Joue cette piste seule (la file est remplacée), comme le site. */
    fun play(play: PlayHistoryItemDto) {
        val item = MediaItems.fromTrack(play.track) ?: return
        player.playNow(listOf(item))
    }
}
