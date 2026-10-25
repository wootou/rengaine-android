package app.rengaine.data.repository

import app.rengaine.data.remote.ApiResult
import app.rengaine.data.remote.apiCallUnit
import app.rengaine.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Ce qu'on peut mettre en favori (`FavoriteType` du contrat). */
enum class FavoriteKind(val api: String) { TRACK("track"), ALBUM("album"), ARTIST("artist") }

data class FavoriteKey(val kind: FavoriteKind, val id: Long)

/**
 * Cœurs (F9). Le serveur dit dans chaque piste, album ou artiste s'il est
 * en favori (`favorite`) ; ce dépôt garde **les changements faits depuis**
 * dans l'app, pour que tous les écrans s'accordent tout de suite (cœur du
 * lecteur et de la page album, page « Mes favoris »).
 *
 * Le cœur change aussitôt ; si l'enregistrement échoue, il revient.
 */
@Singleton
class FavoritesRepository @Inject constructor(
    private val accounts: AccountRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _known = MutableStateFlow<Map<FavoriteKey, Boolean>>(emptyMap())
    /** Valeurs changées dans l'app ; les autres suivent ce qu'a dit le serveur. */
    val known: StateFlow<Map<FavoriteKey, Boolean>> = _known.asStateFlow()

    private val _changed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Un favori a été ajouté ou retiré : la page « Mes favoris » se recharge. */
    val changed: SharedFlow<Unit> = _changed.asSharedFlow()

    init {
        // Les favoris appartiennent au compte.
        scope.launch {
            accounts.activeAccount.map { it?.id }.distinctUntilChanged().collect { _known.value = emptyMap() }
        }
    }

    fun isFavorite(key: FavoriteKey, fromServer: Boolean): Boolean = _known.value[key] ?: fromServer

    /**
     * Comme [set], dans la portée de l'app : quitter l'écran pendant
     * l'envoi n'interrompt ni l'enregistrement ni le retour en arrière.
     */
    fun setAsync(key: FavoriteKey, favorite: Boolean): Deferred<Boolean> = scope.async { set(key, favorite) }

    /** Ajoute ou retire ; `false` si le serveur n'a pas pu l'enregistrer. */
    suspend fun set(key: FavoriteKey, favorite: Boolean): Boolean {
        val before = _known.value[key]
        _known.update { it + (key to favorite) }
        val session = accounts.activeSession()
        val result = session?.let { s ->
            apiCallUnit {
                if (favorite) s.api.addFavorite(key.kind.api, key.id) else s.api.removeFavorite(key.kind.api, key.id)
            }
        }
        if (result is ApiResult.Ok) {
            _changed.tryEmit(Unit)
            return true
        }
        _known.update { if (before == null) it - key else it + (key to before) }
        return false
    }
}
