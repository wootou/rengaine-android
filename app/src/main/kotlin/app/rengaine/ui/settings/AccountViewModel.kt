package app.rengaine.ui.settings

import android.content.ContentResolver
import android.content.Context
import android.widget.Toast
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.rengaine.R
import app.rengaine.data.remote.ApiError
import app.rengaine.data.remote.ApiResult
import app.rengaine.data.remote.ErrorCodes
import app.rengaine.data.remote.dto.DeviceDto
import app.rengaine.data.remote.dto.PairingCodeDto
import app.rengaine.data.repository.MeRepository
import app.rengaine.data.repository.ServerFeatures
import app.rengaine.playback.PlayerController
import app.rengaine.playback.StreamCache
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Carte « Appairer un appareil ». */
sealed interface PairingState {
    data object Closed : PairingState
    data object Creating : PairingState
    data class Shown(val code: PairingCodeDto, val remainingS: Long) : PairingState
    data object Expired : PairingState
    data class Paired(val name: String) : PairingState
    data class Failed(val tooMany: Boolean) : PairingState
}

/** Bloc « Supprimer mon compte », étape par étape (`DeleteAccount.jsx`). */
sealed interface DeleteState {
    data object Idle : DeleteState
    /** Étape 2 : liste de ce qui sera effacé, mot de passe ; [error] après un échec. */
    data class Confirming(val error: DeleteError? = null) : DeleteState
    data class Countdown(val seconds: Int) : DeleteState
    data object Deleting : DeleteState
}

sealed interface DeleteError {
    data object WrongPassword : DeleteError
    data object LastAdmin : DeleteError
    /** Message de l'API, tel quel (429 compris). */
    data class Api(val message: String) : DeleteError
    data object Offline : DeleteError
}

enum class ExportState { IDLE, PREPARING }

enum class AccountMessage { EXPORTED, EXPORT_TOO_MANY, EXPORT_FAILED, REVOKE_FAILED }

data class DevicesState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val items: List<DeviceDto> = emptyList(),
    val currentTokenId: Long? = null,
)

/**
 * Réglages › Appareils connectés, Compte, Mes données, Supprimer mon compte
 * (F13), comme `Settings.jsx`, `PairDevice.jsx`, `ExportData.jsx` et
 * `DeleteAccount.jsx` du site (textes et règles transmis par l'équipe du serveur).
 */
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val me: MeRepository,
    private val player: PlayerController,
    private val cache: StreamCache,
    server: ServerFeatures,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    /** Appairage proposé seulement si le serveur le permet (`features.pairing`). */
    val pairingAvailable = server.features.map { it?.pairing == true }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _devices = MutableStateFlow(DevicesState())
    val devices = _devices.asStateFlow()
    private val _pairing = MutableStateFlow<PairingState>(PairingState.Closed)
    val pairing = _pairing.asStateFlow()
    private val _delete = MutableStateFlow<DeleteState>(DeleteState.Idle)
    val delete = _delete.asStateFlow()
    private val _export = MutableStateFlow(ExportState.IDLE)
    val export = _export.asStateFlow()
    private val _messages = MutableSharedFlow<AccountMessage>(extraBufferCapacity = 2)
    val messages = _messages.asSharedFlow()

    private var pairingJob: Job? = null
    private var deleteJob: Job? = null

    init {
        loadDevices()
    }

    // --- Appareils connectés ---------------------------------------------------------

    fun loadDevices() {
        viewModelScope.launch {
            _devices.value = when (val r = me.devices()) {
                is ApiResult.Ok -> DevicesState(loading = false, items = r.value.items, currentTokenId = r.value.currentTokenId)
                is ApiResult.Err -> _devices.value.copy(loading = false, failed = true)
            }
        }
    }

    /** « Déconnecter » un autre appareil : sans confirmation, comme le site, puis la liste est relue. */
    fun revoke(device: DeviceDto) {
        viewModelScope.launch {
            if (me.revoke(device.id) !is ApiResult.Ok) _messages.emit(AccountMessage.REVOKE_FAILED)
            loadDevices()
        }
    }

    // --- Appairer un appareil --------------------------------------------------------

    fun createPairingCode() {
        pairingJob?.cancel()
        _pairing.value = PairingState.Creating
        pairingJob = viewModelScope.launch {
            val known = _devices.value.items.map { it.id }.toSet()
            val code = when (val r = me.pairingCode()) {
                is ApiResult.Ok -> r.value
                is ApiResult.Err -> {
                    _pairing.value = PairingState.Failed(tooMany = (r.error as? ApiError.Http)?.status == 429)
                    return@launch
                }
            }
            val expires = runCatching { Instant.parse(code.expiresAt) }.getOrNull() ?: Instant.now().plusSeconds(300)
            var tick = 0
            while (isActive) {
                val remaining = expires.epochSecond - Instant.now().epochSecond
                if (remaining <= 0) {
                    _pairing.value = PairingState.Expired
                    return@launch
                }
                _pairing.value = PairingState.Shown(code, remaining)
                // Comme le site : la liste des appareils est relue toutes les 4 s pour voir arriver le nouveau.
                if (tick % 4 == 3) {
                    val fresh = (me.devices() as? ApiResult.Ok)?.value
                    val added = fresh?.items?.firstOrNull { it.id !in known }
                    if (fresh != null && added != null) {
                        _devices.value = DevicesState(loading = false, items = fresh.items, currentTokenId = fresh.currentTokenId)
                        _pairing.value = PairingState.Paired(added.name)
                        return@launch
                    }
                }
                tick++
                delay(1_000)
            }
        }
    }

    fun closePairing() {
        pairingJob?.cancel()
        _pairing.value = PairingState.Closed
    }

    // --- Mes données -------------------------------------------------------------------

    fun export(resolver: ContentResolver, target: Uri) {
        if (_export.value == ExportState.PREPARING) return
        _export.value = ExportState.PREPARING
        viewModelScope.launch {
            val r = me.export(resolver, target)
            _export.value = ExportState.IDLE
            _messages.emit(
                when {
                    r is ApiResult.Ok -> AccountMessage.EXPORTED
                    ((r as ApiResult.Err).error as? ApiError.Http)?.status == 429 -> AccountMessage.EXPORT_TOO_MANY
                    else -> AccountMessage.EXPORT_FAILED
                },
            )
        }
    }

    // --- Supprimer mon compte -------------------------------------------------------

    fun startDelete() {
        _delete.value = DeleteState.Confirming()
    }

    /** « Garder mon compte », « Annuler », ou écran quitté : rien ne part. */
    fun cancelDelete() {
        deleteJob?.cancel()
        if (_delete.value !is DeleteState.Deleting) _delete.value = DeleteState.Idle
    }

    /**
     * « Supprimer définitivement » : 15 s pour annuler (exigé par le
     * contrat), puis seulement la requête.
     */
    fun confirmDelete(password: String) {
        if (password.isEmpty()) return
        deleteJob?.cancel()
        deleteJob = viewModelScope.launch {
            for (s in COUNTDOWN_S downTo 1) {
                _delete.value = DeleteState.Countdown(s)
                delay(1_000)
            }
            _delete.value = DeleteState.Deleting
            when (val r = me.deleteAccount(password)) {
                is ApiResult.Ok -> {
                    // Tout ranger avant d'oublier le compte : l'app quitte alors
                    // cet écran (retour à la connexion) et ce ViewModel disparaît.
                    player.stop()
                    withContext(Dispatchers.IO) { cache.clear(keepTrackId = null) }
                    // Message montré par l'app elle-même : il survit au changement d'écran.
                    Toast.makeText(context, context.getString(R.string.delete_done), Toast.LENGTH_LONG).show()
                    me.forget(r.value)
                }
                is ApiResult.Err -> _delete.value = DeleteState.Confirming(r.error.toDeleteError())
            }
        }
    }

    private fun ApiError.toDeleteError(): DeleteError = when (this) {
        is ApiError.Http -> when (code) {
            ErrorCodes.INVALID_CREDENTIALS -> DeleteError.WrongPassword
            "last_admin" -> DeleteError.LastAdmin
            else -> DeleteError.Api(message)
        }
        else -> DeleteError.Offline
    }

    private companion object {
        const val COUNTDOWN_S = 15
    }
}
