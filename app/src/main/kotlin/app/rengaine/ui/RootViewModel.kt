package app.rengaine.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.rengaine.data.repository.AccountRepository
import app.rengaine.domain.PairingUri
import app.rengaine.ui.login.PendingPairing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface RootState {
    data object Loading : RootState
    data object SignedOut : RootState
    data class SignedIn(val accountId: Long, val needsReauth: Boolean) : RootState
}

@HiltViewModel
class RootViewModel @Inject constructor(
    accounts: AccountRepository,
    pending: PendingPairing,
) : ViewModel() {

    val state: StateFlow<RootState> = accounts.activeAccount
        .map { active ->
            if (active == null) RootState.SignedOut else RootState.SignedIn(active.id, active.needsReauth)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, RootState.Loading)

    val pendingPairing: StateFlow<PairingUri.Payload?> = pending.payload
}
