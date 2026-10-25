package app.rengaine.playback

import android.util.Log
import androidx.media3.common.Player
import app.rengaine.data.remote.ApiResult
import app.rengaine.data.remote.dto.TrackDto
import app.rengaine.data.repository.AccountRepository
import app.rengaine.data.repository.CatalogRepository
import app.rengaine.data.repository.RandomFilter
import app.rengaine.data.repository.SettingsRepository
import app.rengaine.data.repository.ServerFeatures
import app.rengaine.data.remote.dto.AccountSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Mode infini (F5), comme le lecteur du site (`controller.js`, réponses de
 * l'équipe du serveur du 2026-10-24) :
 *
 * - **Réglage du compte** `infinite.enabled` (actif par défaut) : s'il est
 *   vrai, **toute** file (album, playlist…) se complète quand il reste moins
 *   de 3 pistes après celle en cours, par lots de 20. Le bouton ∞ du
 *   lecteur change ce réglage, pas seulement la session ; un changement
 *   fait ailleurs (Réglages, site) est suivi.
 * - **Tirage depuis Explorer** (ambiance, genre, décennie, collection,
 *   « Tout, au hasard ») : le premier lot **remplace** la file, active le
 *   réglage, et son filtre sert aux extensions suivantes. Ce filtre ne vit
 *   qu'en mémoire ; choisir autre chose à écouter l'efface.
 *
 * - **Lecture continue** (`continuous.enabled`, et `features.continuousPlay`
 *   du serveur) : elle ne fait que choisir **comment** compléter, jamais
 *   si on complète (le mode infini doit être actif, comme sur le site).
 *   `POST /playback/next` d'abord, avec le ciblage d'Explorer en
 *   `overrides` ; sur une erreur (501 compris) ou une liste vide, le tirage
 *   au hasard prend le relais.
 *
 * Les pistes ajoutées n'ont aucune marque dans la file, comme sur le site.
 */
@Singleton
class InfiniteMode @Inject constructor(
    private val catalog: CatalogRepository,
    private val player: PlayerController,
    private val settings: SettingsRepository,
    private val server: ServerFeatures,
    accounts: AccountRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var extending: Job? = null

    /** Réglage du compte ; vrai tant qu'il n'est pas connu (valeur par défaut du contrat). */
    val enabled: StateFlow<Boolean> = settings.settings
        .map { it?.infiniteEnabled ?: true }
        .stateIn(scope, SharingStarted.Eagerly, true)

    /** Filtre du dernier tirage d'Explorer ; `null` = d'après la piste en cours. */
    private var target: RandomFilter? = null

    init {
        scope.launch { player.replaced.collect { target = null } }
        scope.launch { accounts.activeAccount.map { it?.id }.distinctUntilChanged().drop(1).collect { target = null } }
        scope.launch { player.state.collect { maybeExtend() } }
        scope.launch { enabled.collect { if (it) maybeExtend() else extending?.cancel() } }
    }

    /** Lance un tirage qui remplace la file ; `false` si rien ne correspond au filtre. */
    suspend fun start(filter: RandomFilter): Boolean {
        val tracks = (catalog.randomTracks(BATCH, filter) as? ApiResult.Ok)?.value.orEmpty()
        val items = tracks.mapNotNull(MediaItems::fromTrack)
        if (items.isEmpty()) return false
        player.playNow(items, fromInfinite = true)
        target = filter
        if (!enabled.value) settings.set(AccountSettings.PATH_INFINITE, true)
        return true
    }

    /** Bouton ∞ : change le réglage du compte ; `false` s'il n'a pas pu être enregistré. */
    suspend fun toggle(): Boolean = settings.set(AccountSettings.PATH_INFINITE, !enabled.value)

    private fun maybeExtend() {
        if (!enabled.value) return
        if (extending?.isActive == true) return
        val s = player.state.value
        if (!s.hasQueue || s.repeatMode != Player.REPEAT_MODE_OFF) return
        if (s.count - s.index - 1 >= THRESHOLD) return
        val filter = target ?: RandomFilter()
        extending = scope.launch {
            val recent = player.queue.value.mapNotNull { it.trackId }.takeLast(RECENT)
            val tracks = continuous(filter, recent).ifEmpty { draw(filter, recent) }
            val items = tracks.mapNotNull(MediaItems::fromTrack)
            // Réglage coupé ou autre écoute choisie pendant le tirage : on ne mélange pas les files.
            if (!enabled.value || (target ?: RandomFilter()) != filter || items.isEmpty()) return@launch
            player.addToQueue(items)
            Log.i(TAG, "File prolongée de ${items.size} pistes")
        }
    }

    /**
     * Lecture continue, si le compte l'a activée et que le serveur la
     * propose. Référence : la **dernière** piste de la file (pas celle en
     * cours), comme le site. Vide = on passe au hasard.
     */
    private suspend fun continuous(filter: RandomFilter, recent: List<Long>): List<TrackDto> {
        val on = settings.settings.value?.continuousEnabled == true && server.features.value?.continuousPlay == true
        if (!on) return emptyList()
        return when (val r = catalog.nextTracks(BATCH, recent.lastOrNull(), recent, filter)) {
            is ApiResult.Ok -> r.value
            is ApiResult.Err -> {
                Log.w(TAG, "Lecture continue indisponible (${r.error}) : tirage au hasard")
                emptyList()
            }
        }
    }

    /**
     * Genres : ceux choisis ; sinon, avec un ciblage, tous ; sinon ceux de
     * la piste en cours. Si rien ne sort, on élargit pas à pas : toute la
     * bibliothèque (seulement quand le genre venait de la piste), puis les
     * répétitions sauf les toutes dernières, puis n'importe lesquelles.
     */
    private suspend fun draw(filter: RandomFilter, recent: List<Long>): List<TrackDto> {
        val fromCurrent = filter.genreIds.isEmpty() && !filter.hasTarget
        val first = if (fromCurrent) filter.copy(genreIds = player.currentGenres()) else filter
        val attempts = buildList {
            add(first to recent)
            if (fromCurrent && first.genreIds.isNotEmpty()) add(filter to recent)
            val widest = if (fromCurrent) filter else first
            add(widest to recent.takeLast(RECENT_STRICT))
            add(widest to emptyList())
        }
        for ((f, exclude) in attempts) {
            val tracks = (catalog.randomTracks(BATCH, f, exclude) as? ApiResult.Ok)?.value.orEmpty()
            if (tracks.isNotEmpty()) return tracks
        }
        return emptyList()
    }

    private companion object {
        const val TAG = "Rengaine/Infini"
        const val BATCH = 20
        /** On complète quand il reste moins de ce nombre de pistes après celle en cours. */
        const val THRESHOLD = 3
        /** Pistes récentes à ne pas reprendre (300, comme le site). */
        const val RECENT = 300
        /** Dernier recours : accepter les répétitions, sauf les 10 dernières (comme le site). */
        const val RECENT_STRICT = 10
    }
}
