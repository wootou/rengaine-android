package app.rengaine.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.rengaine.data.local.SessionStore
import app.rengaine.data.repository.ServerFeatures
import app.rengaine.data.repository.WaveformRepository
import app.rengaine.playback.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Ce que la barre de position du lecteur plein écran doit montrer. */
sealed interface WaveformState {
    /** Barre simple : serveur sans forme d'onde, réglage, piste pas mesurée ou erreur. */
    data object Plain : WaveformState
    /** Forme d'onde en route : la place est gardée, rien n'est dessiné (comme le site). */
    data object Loading : WaveformState
    class Bars(val heights: FloatArray) : WaveformState
}

/**
 * Forme d'onde de la piste en cours (U12), pour le seul lecteur plein
 * écran : le mini-lecteur garde son trait fin et ne la demande pas.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WaveformViewModel @Inject constructor(
    controller: PlayerController,
    features: ServerFeatures,
    session: SessionStore,
    private val waveforms: WaveformRepository,
) : ViewModel() {

    val state: StateFlow<WaveformState> = combine(
        controller.state.map { it.trackId }.distinctUntilChanged(),
        features.features.map { it?.analysis?.waveform == true }.distinctUntilChanged(),
        session.waveformHidden,
    ) { trackId, available, hidden -> trackId.takeIf { available && !hidden } }
        .distinctUntilChanged()
        .flatMapLatest { trackId ->
            if (trackId == null) {
                flowOf<WaveformState>(WaveformState.Plain)
            } else {
                flow<WaveformState> {
                    emit(WaveformState.Loading)
                    val bars = waveforms.bars(trackId)
                    emit(if (bars != null && bars.isNotEmpty()) WaveformState.Bars(bars) else WaveformState.Plain)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WaveformState.Plain)
}
