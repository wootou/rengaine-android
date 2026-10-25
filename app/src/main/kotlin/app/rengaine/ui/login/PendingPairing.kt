package app.rengaine.ui.login

import app.rengaine.domain.PairingUri
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lien `rengaine://pair?…` reçu de l'extérieur (appareil photo d'Android,
 * navigateur) et pas encore traité. L'écran de connexion le consomme et
 * **demande confirmation** : un lien venu d'ailleurs ne doit pas connecter
 * l'app à un serveur sans que l'utilisateur l'ait voulu.
 */
@Singleton
class PendingPairing @Inject constructor() {
    private val state = MutableStateFlow<PairingUri.Payload?>(null)
    val payload: StateFlow<PairingUri.Payload?> = state.asStateFlow()

    fun offer(payload: PairingUri.Payload) {
        state.value = payload
    }

    fun consume(): PairingUri.Payload? = state.value.also { state.value = null }
}
