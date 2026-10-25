package app.rengaine.data.repository

import app.rengaine.data.remote.dto.FeaturesDto
import app.rengaine.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Capacités du serveur du compte actif (`GET /server`, `features`) : ce que
 * l'app propose en dépend (qualité du flux, appairage…). Relues à chaque
 * changement de compte ; `null` tant qu'on ne les connaît pas.
 */
@Singleton
class ServerFeatures @Inject constructor(
    servers: ServerRepository,
    accounts: AccountRepository,
    @ApplicationScope scope: CoroutineScope,
) {
    private val _features = MutableStateFlow<FeaturesDto?>(null)
    val features: StateFlow<FeaturesDto?> = _features.asStateFlow()

    init {
        scope.launch {
            accounts.activeAccount.map { it?.address }.distinctUntilChanged().collect { address ->
                _features.value = null
                if (address == null) return@collect
                _features.value = (servers.check(address) as? ServerCheck.Ready)?.info?.features
            }
        }
    }
}
