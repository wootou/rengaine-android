package app.rengaine.ui.login

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.rengaine.data.remote.ApiResult
import app.rengaine.data.remote.ServerAddress
import app.rengaine.data.repository.AccountRepository
import app.rengaine.data.repository.ServerCheck
import app.rengaine.data.repository.ServerRepository
import app.rengaine.data.repository.SignInError
import app.rengaine.data.repository.SignInResult
import app.rengaine.data.local.SessionStore
import app.rengaine.domain.Account
import app.rengaine.domain.PairingUri
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** État de l'adresse du serveur, affiché sous le champ. */
sealed interface ServerStatus {
    data object Idle : ServerStatus
    data object Checking : ServerStatus
    data class Known(val check: ServerCheck) : ServerStatus
}

sealed interface ForgotState {
    data object Hidden : ForgotState
    data class Editing(val email: String, val sending: Boolean = false, val failed: Boolean = false) : ForgotState
    data class Sent(val email: String) : ForgotState
}

data class LoginUiState(
    val address: String = "",
    val email: String = "",
    val password: String = "",
    val deviceName: String = "",
    val server: ServerStatus = ServerStatus.Idle,
    val submitting: Boolean = false,
    val error: SignInError? = null,
    /** Compte dont le jeton a été refusé : adresse et e-mail sont figés. */
    val reauth: Account? = null,
    /** Lien d'appairage venu de l'extérieur, en attente de confirmation. */
    val pairingToConfirm: PairingUri.Payload? = null,
    val pairing: Boolean = false,
    val forgot: ForgotState = ForgotState.Hidden,
) {
    val ready: ServerCheck.Ready? get() = (server as? ServerStatus.Known)?.check as? ServerCheck.Ready
    val canSubmit: Boolean
        get() = !submitting && !pairing && address.isNotBlank() && email.isNotBlank() && password.isNotEmpty()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    @ApplicationContext context: Context,
    savedState: SavedStateHandle,
    private val accounts: AccountRepository,
    private val servers: ServerRepository,
    private val session: SessionStore,
    private val pendingPairing: PendingPairing,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private var checkJob: Job? = null
    private val fallbackDeviceName = DeviceNames.default(context)

    init {
        viewModelScope.launch {
            val name = session.deviceName() ?: fallbackDeviceName
            _state.update { it.copy(deviceName = name) }
        }
        // Reconnexion seulement si l'écran a été ouvert pour ça : « Ajouter
        // un compte » depuis un compte à reconnecter reste un ajout.
        if (savedState.get<Boolean>(ARG_REAUTH) == true) viewModelScope.launch {
            val active = accounts.activeAccount.first()
            if (active != null && active.needsReauth) {
                _state.update {
                    it.copy(reauth = active, address = active.address.display, email = active.email)
                }
                checkNow()
            }
        }
        viewModelScope.launch {
            pendingPairing.payload.collect { payload ->
                if (payload != null) {
                    pendingPairing.consume()
                    _state.update { it.copy(pairingToConfirm = payload, error = null) }
                }
            }
        }
    }

    fun onAddressChange(value: String) {
        if (_state.value.reauth != null) return
        _state.update { it.copy(address = value, server = ServerStatus.Idle, error = null) }
        checkJob?.cancel()
        if (value.isBlank()) return
        // Vérification dès la saisie (F1), après une courte pause de frappe.
        checkJob = viewModelScope.launch {
            delay(CHECK_DEBOUNCE_MS)
            runCheck(value)
        }
    }

    /** Champ quitté ou touche « Suivant » : vérifier sans attendre. */
    fun checkNow() {
        val address = _state.value.address
        if (address.isBlank()) return
        if (_state.value.server != ServerStatus.Idle) return
        checkJob?.cancel()
        checkJob = viewModelScope.launch { runCheck(address) }
    }

    private suspend fun runCheck(address: String): ServerCheck {
        _state.update { it.copy(server = ServerStatus.Checking) }
        val result = servers.check(address)
        // L'adresse a pu changer pendant l'appel : ne pas afficher un
        // résultat périmé.
        if (_state.value.address == address) {
            _state.update { it.copy(server = ServerStatus.Known(result)) }
        }
        return result
    }

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, error = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun onDeviceNameChange(value: String) {
        val name = value.trim().take(DeviceNames.MAX_LENGTH).ifEmpty { fallbackDeviceName }
        _state.update { it.copy(deviceName = name) }
        viewModelScope.launch { session.setDeviceName(name) }
    }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            checkJob?.cancel()
            val server = current.ready ?: runCheck(current.address)
            if (server !is ServerCheck.Ready) {
                _state.update { it.copy(submitting = false) }
                return@launch
            }
            when (val result = accounts.login(server, current.email, current.password, current.deviceName)) {
                // La navigation suit le compte actif : rien d'autre à faire.
                is SignInResult.Success -> _state.update { it.copy(submitting = false, password = "") }
                is SignInResult.Failure -> _state.update {
                    it.copy(
                        submitting = false,
                        error = result.error,
                        server = if (result.error is SignInError.Server) {
                            ServerStatus.Known(result.error.check)
                        } else {
                            it.server
                        },
                    )
                }
            }
        }
    }

    // --- Appairage -----------------------------------------------------------

    /** QR lu par l'écran de scan de l'app : l'utilisateur l'a voulu, pas de confirmation. */
    fun pairScanned(payload: PairingUri.Payload) = pair(payload)

    fun confirmPendingPairing() {
        val payload = _state.value.pairingToConfirm ?: return
        _state.update { it.copy(pairingToConfirm = null) }
        pair(payload)
    }

    fun dismissPendingPairing() = _state.update { it.copy(pairingToConfirm = null) }

    /** Saisie manuelle de l'adresse et du code, en repli du scan. */
    fun pairManually(address: String, code: String) {
        pair(PairingUri.Payload(server = address, code = code))
    }

    private fun pair(payload: PairingUri.Payload) {
        if (_state.value.pairing) return
        _state.update { it.copy(pairing = true, error = null) }
        viewModelScope.launch {
            val result = accounts.pair(payload, _state.value.deviceName)
            _state.update {
                when (result) {
                    is SignInResult.Success -> it.copy(pairing = false)
                    is SignInResult.Failure -> it.copy(pairing = false, error = result.error)
                }
            }
        }
    }

    // --- Mot de passe oublié --------------------------------------------------

    fun openForgot() = _state.update { it.copy(forgot = ForgotState.Editing(email = it.email)) }

    fun onForgotEmailChange(value: String) = _state.update {
        val f = it.forgot as? ForgotState.Editing ?: return@update it
        it.copy(forgot = f.copy(email = value, failed = false))
    }

    fun sendForgot() {
        val editing = _state.value.forgot as? ForgotState.Editing ?: return
        val server = _state.value.ready ?: return
        if (editing.email.isBlank() || editing.sending) return
        _state.update { it.copy(forgot = editing.copy(sending = true, failed = false)) }
        viewModelScope.launch {
            val result = accounts.forgotPassword(server.address, editing.email)
            _state.update {
                it.copy(
                    forgot = if (result is ApiResult.Ok) {
                        ForgotState.Sent(editing.email.trim())
                    } else {
                        editing.copy(sending = false, failed = true)
                    },
                )
            }
        }
    }

    fun closeForgot() = _state.update { it.copy(forgot = ForgotState.Hidden) }

    /** Adresse à préremplir dans la saisie manuelle du code. */
    fun addressForManualCode(): String =
        _state.value.ready?.address?.display ?: _state.value.address.trim()

    fun serverRootForBrowser(): ServerAddress? = _state.value.ready?.address

    companion object {
        /** Nom de l'argument de navigation (`LoginRoute.reauth`). */
        const val ARG_REAUTH = "reauth"
        private const val CHECK_DEBOUNCE_MS = 700L
    }
}
