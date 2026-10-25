package app.rengaine.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.rengaine.data.repository.AccountRepository
import app.rengaine.domain.Account
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AccountsUiState(
    val accounts: List<Account> = emptyList(),
    val activeId: Long? = null,
    val loaded: Boolean = false,
)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val repository: AccountRepository,
) : ViewModel() {

    val state: StateFlow<AccountsUiState> =
        combine(repository.accounts, repository.activeAccount) { list, active ->
            AccountsUiState(accounts = list, activeId = active?.id, loaded = true)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState())

    /** Bascule de compte. Le lecteur s'arrête et vide sa file de lui-même (`PlayerController`). */
    fun switchTo(account: Account) {
        viewModelScope.launch { repository.switchTo(account.id) }
    }

    fun signOut(account: Account) {
        viewModelScope.launch { repository.signOut(account.id) }
    }
}
