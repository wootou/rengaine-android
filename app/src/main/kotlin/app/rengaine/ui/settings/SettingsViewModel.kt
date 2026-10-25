package app.rengaine.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.rengaine.data.local.SessionStore
import app.rengaine.data.remote.dto.AccountSettings
import app.rengaine.data.remote.dto.ThemeChoice
import app.rengaine.data.repository.AccountRepository
import app.rengaine.data.repository.ServerFeatures
import app.rengaine.data.repository.SettingsRepository
import app.rengaine.data.repository.CatalogRepository
import app.rengaine.data.remote.ApiResult
import app.rengaine.data.remote.dto.GenreDto
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import app.rengaine.domain.Account
import app.rengaine.playback.PlayerController
import app.rengaine.playback.StreamCache
import app.rengaine.playback.StreamQuality
import app.rengaine.playback.StreamQualityPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    /** Réglages du compte lus au moins une fois (copie locale ou serveur). */
    val loaded: Boolean = false,
    val infiniteEnabled: Boolean = true,
    val theme: ThemeChoice = ThemeChoice.AUTO,
    /** Section « Qualité du flux » : seulement si le serveur convertit. */
    val showQuality: Boolean = false,
    val qualityWifi: StreamQuality = StreamQuality.ORIGINAL,
    val qualityMobile: StreamQuality = StreamQuality.ORIGINAL,
    val cacheSizeMb: Int = SessionStore.DEFAULT_CACHE_SIZE_MB,
    /** Place prise par le cache ; `null` pendant le calcul. */
    val cacheUsedBytes: Long? = null,
    val account: Account? = null,
    /** Volume (`playback`). */
    val normalization: String = "auto",
    val targetLufs: Int = -16,
    val preventClipping: Boolean = true,
    /** Lecture continue : bloc affiché seulement si le serveur la propose. */
    val showContinuous: Boolean = false,
    val continuous: ContinuousRules = ContinuousRules(),
)

/** Règles `continuous` et ce que l'analyse du serveur permet d'en régler. */
data class ContinuousRules(
    val enabled: Boolean = false,
    val harmonicMixing: Boolean = false,
    val bpmTolerancePercent: Int? = null,
    val energyTrend: String = "any",
    val favoritesWeight: Double = 0.3,
    val noRepeatHours: Int = 24,
    val artistSpacing: Int = 3,
    val excludedGenres: Set<Long> = emptySet(),
    val keyAvailable: Boolean = false,
    val bpmAvailable: Boolean = false,
    val energyAvailable: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val accounts: AccountRepository,
    private val session: SessionStore,
    private val cache: StreamCache,
    private val player: PlayerController,
    private val catalog: CatalogRepository,
    quality: StreamQualityPolicy,
    server: ServerFeatures,
) : ViewModel() {

    /**
     * Case « Forme d'onde… » (U12) : `null` si le serveur n'en fournit pas
     * (la case est alors masquée, comme sur le site), sinon cochée ou non.
     */
    val waveformShown: StateFlow<Boolean?> = combine(
        server.features.map { it?.analysis?.waveform == true },
        session.waveformHidden,
    ) { available, hidden -> if (available) !hidden else null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setWaveformShown(shown: Boolean) {
        viewModelScope.launch { session.setWaveformHidden(!shown) }
    }

    /** Case « Curseur de volume… » : propre à ce téléphone, cochée par défaut. */
    val volumeBarShown: StateFlow<Boolean> = session.volumeBarHidden.map { !it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setVolumeBarShown(shown: Boolean) {
        viewModelScope.launch { session.setVolumeBarHidden(!shown) }
    }

    private val cacheUsed = MutableStateFlow<Long?>(null)

    val state: StateFlow<SettingsUiState> = combine(
        combine(settings.settings, server.features, ::Pair),
        accounts.activeAccount,
        session.cacheSizeMb,
        cacheUsed,
        quality.serverConverts,
    ) { (s, features), account, cacheMb, used, converts ->
        SettingsUiState(
            loaded = s != null,
            infiniteEnabled = s?.infiniteEnabled ?: true,
            theme = s?.theme ?: ThemeChoice.AUTO,
            showQuality = converts,
            qualityWifi = StreamQuality.of(s?.streamQualityWifi),
            qualityMobile = StreamQuality.of(s?.streamQualityMobile),
            cacheSizeMb = cacheMb,
            cacheUsedBytes = used,
            account = account,
            normalization = s?.normalization ?: "auto",
            targetLufs = s?.targetLufs?.let { Math.round(it).toInt() } ?: -16,
            preventClipping = s?.preventClipping ?: true,
            showContinuous = features?.continuousPlay == true,
            continuous = ContinuousRules(
                enabled = s?.continuousEnabled ?: false,
                harmonicMixing = s?.harmonicMixing ?: false,
                bpmTolerancePercent = s?.bpmTolerancePercent,
                energyTrend = s?.energyTrend ?: "any",
                favoritesWeight = s?.favoritesWeight ?: 0.3,
                noRepeatHours = s?.noRepeatHours ?: 24,
                artistSpacing = s?.artistSpacing ?: 3,
                excludedGenres = s?.excludedGenres.orEmpty().toSet(),
                keyAvailable = features?.analysis?.key == true,
                bpmAvailable = features?.analysis?.bpm == true,
                energyAvailable = features?.analysis?.energy == true,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private val _notSaved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Un réglage du compte n'a pas pu être enregistré (hors ligne) : on le dit. */
    val notSaved = _notSaved.asSharedFlow()

    init {
        // Ce qu'un autre appareil a changé depuis le démarrage.
        viewModelScope.launch { settings.refresh() }
        refreshCacheUsage()
    }

    fun setInfinite(enabled: Boolean) = save { settings.set(AccountSettings.PATH_INFINITE, enabled) }
    fun setTheme(theme: ThemeChoice) = save { settings.set(AccountSettings.PATH_THEME, theme.wire) }
    fun setQualityWifi(q: StreamQuality) = save { settings.set(AccountSettings.PATH_QUALITY_WIFI, q.wire) }
    fun setQualityMobile(q: StreamQuality) = save { settings.set(AccountSettings.PATH_QUALITY_MOBILE, q.wire) }

    fun setNormalization(mode: String) = save { settings.set(AccountSettings.PATH_NORMALIZATION, mode) }
    fun setTargetLufs(lufs: Int) = save { settings.set(AccountSettings.PATH_TARGET_LUFS, lufs) }
    fun setPreventClipping(on: Boolean) = save { settings.set(AccountSettings.PATH_PREVENT_CLIPPING, on) }
    fun setContinuous(on: Boolean) = save { settings.set(AccountSettings.PATH_CONTINUOUS, on) }
    fun setHarmonicMixing(on: Boolean) = save { settings.set(AccountSettings.PATH_HARMONIC, on) }
    fun setBpmTolerance(percent: Int?) = save { settings.set(AccountSettings.PATH_BPM_TOLERANCE, percent) }
    fun setEnergyTrend(trend: String) = save { settings.set(AccountSettings.PATH_ENERGY, trend) }
    fun setFavoritesWeight(weight: Double) = save { settings.set(AccountSettings.PATH_FAVORITES_WEIGHT, weight) }
    fun setNoRepeatHours(hours: Int) = save { settings.set(AccountSettings.PATH_NO_REPEAT, hours) }
    fun setArtistSpacing(tracks: Int) = save { settings.set(AccountSettings.PATH_ARTIST_SPACING, tracks) }

    /** Une case de « Genres à ne jamais proposer » ; repart de la valeur du moment. */
    fun toggleExcludedGenre(id: Long) = save {
        val current = settings.settings.value?.excludedGenres.orEmpty()
        val next = if (id in current) current - id else current + id
        settings.set(AccountSettings.PATH_EXCLUDED_GENRES, JsonArray(next.map(::JsonPrimitive)))
    }

    private val _genres = MutableStateFlow<List<GenreDto>?>(null)
    /** Genres de la bibliothèque, chargés à la première ouverture du bloc des exclusions. */
    val genres: StateFlow<List<GenreDto>?> = _genres

    fun loadGenres() {
        if (_genres.value != null) return
        viewModelScope.launch { _genres.value = (catalog.genres() as? ApiResult.Ok)?.value.orEmpty().sortedBy { it.name.lowercase() } }
    }

    fun setCacheSize(mb: Int) {
        viewModelScope.launch {
            session.setCacheSizeMb(mb)
            withContext(Dispatchers.IO) { cache.setMaxBytes(mb * StreamCache.MB) }
            refreshCacheUsage()
        }
    }

    fun clearCache() {
        val playing = player.state.value.trackId
        viewModelScope.launch {
            withContext(Dispatchers.IO) { cache.clear(keepTrackId = playing) }
            refreshCacheUsage()
        }
    }

    fun signOut(account: Account) {
        viewModelScope.launch { accounts.signOut(account.id) }
    }

    private fun save(block: suspend () -> Boolean) {
        viewModelScope.launch { if (!block()) _notSaved.emit(Unit) }
    }

    private fun refreshCacheUsage() {
        viewModelScope.launch {
            cacheUsed.value = withContext(Dispatchers.IO) { cache.usedBytes() }
        }
    }

    companion object {
        val CACHE_SIZES_MB = listOf(250, 500, 1000, 2000, 5000)
    }
}
