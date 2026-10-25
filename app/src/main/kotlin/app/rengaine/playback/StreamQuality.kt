package app.rengaine.playback

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import app.rengaine.data.remote.dto.AudioFormatDto
import app.rengaine.data.repository.ServerFeatures
import app.rengaine.data.repository.SettingsRepository
import app.rengaine.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Qualité du flux (`StreamQuality` du contrat) : `original` = le fichier tel
 * quel ; sinon une conversion à la volée en Opus, que Media3 lit sur toutes
 * les versions d'Android visées (minSdk 31).
 */
enum class StreamQuality(val wire: String, val format: String?, val bitrate: Int?) {
    ORIGINAL("original", null, null),
    HIGH("high", "opus", 256),
    MEDIUM("medium", "opus", 160),
    LOW("low", "opus", 96);

    /** Morceau de la clé du cache disque : une piste a une entrée par qualité. */
    val cacheSuffix: String get() = if (format == null) "original" else "$format$bitrate"

    companion object {
        fun of(wire: String?): StreamQuality = entries.firstOrNull { it.wire == wire } ?: ORIGINAL

        /**
         * Ce qu'il faut demander pour une piste dont le premier fichier est
         * [source], comme `Transcoder::plan()` du serveur (et le site) :
         * une source sans perte se convertit toujours ; une source avec
         * perte jamais vers son propre format, ni vers un débit égal ou
         * supérieur au sien. Sinon, le fichier tel quel. Source inconnue :
         * on demande, le serveur tranchera.
         */
        fun plan(wanted: StreamQuality, source: AudioFormatDto?): StreamQuality {
            if (wanted.format == null || source == null || source.lossless) return wanted
            if (source.format == wanted.format) return ORIGINAL
            val sourceKbps = source.bitrateKbps ?: return wanted
            return if (wanted.bitrate != null && wanted.bitrate < sourceKbps) wanted else ORIGINAL
        }
    }
}

/**
 * Choix de la qualité (F7) : réglage du compte (`playback.streamQuality`,
 * en wifi ou filaire / avec les données mobiles), réseau du moment, et
 * capacités du serveur (`features.transcoding`, `transcodingFormats`) —
 * tout en original s'il ne convertit pas.
 *
 * La qualité est **fixée pour chaque piste à son premier chargement** ([pin]) :
 * un changement de réglage ou de réseau vaut à partir de la piste suivante,
 * comme sur le site, sans couper celle en cours (un retour en arrière dans
 * la piste relit le même format).
 */
@Singleton
class StreamQualityPolicy @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val server: ServerFeatures,
    @ApplicationScope scope: CoroutineScope,
) {
    private val pins = ConcurrentHashMap<Long, StreamQuality>()

    /** Formats que le serveur du compte actif sait produire ; vide = aucune conversion. */
    private val serverFormats: Set<String>
        get() = server.features.value?.takeIf { it.transcoding }?.transcodingFormats?.toSet().orEmpty()

    /** Le serveur convertit en Opus : Réglages propose alors la qualité du flux. */
    val serverConverts: StateFlow<Boolean> = server.features
        .map { it?.transcoding == true && "opus" in it.transcodingFormats }
        .stateIn(scope, SharingStarted.Eagerly, false)

    init {
        // Nouveau compte (ou serveur) : les choix des pistes repartent de zéro.
        scope.launch { server.features.collect { pins.clear() } }
    }

    /** Qualité de [trackId] : celle déjà choisie pour elle, sinon celle du moment. */
    fun pin(trackId: Long): StreamQuality = pins.getOrPut(trackId) { StreamQuality.plan(current(), MediaItems.sourceOf(trackId)) }

    /**
     * Qualité que [trackId] aura : celle déjà fixée, sinon celle du moment,
     * sans la fixer (le lecteur prépare toute la file d'un coup).
     */
    fun planned(trackId: Long): StreamQuality = pins[trackId] ?: StreamQuality.plan(current(), MediaItems.sourceOf(trackId))

    /** Qualité déjà fixée pour [trackId], sans en choisir une. */
    fun pinned(trackId: Long): StreamQuality? = pins[trackId]

    /** Oublie le choix des pistes qui ne sont plus en cours ni suivantes. */
    fun keepOnly(trackIds: Set<Long>) {
        pins.keys.retainAll(trackIds)
    }

    fun current(): StreamQuality {
        val s = settings.settings.value ?: return StreamQuality.ORIGINAL
        val wanted = StreamQuality.of(if (onMobileData()) s.streamQualityMobile else s.streamQualityWifi)
        return if (wanted.format == null || wanted.format in serverFormats) wanted else StreamQuality.ORIGINAL
    }

    /**
     * Réglage « avec les données mobiles » sur un réseau cellulaire **ou
     * mesuré** (partage de connexion, forfait limité) ; sinon, ou si l'on ne
     * sait pas, « en wifi ou filaire », comme le site. Choix approuvé par
     * l'équipe du serveur (le navigateur, lui, ne voit que le cellulaire).
     */
    private fun onMobileData(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}
