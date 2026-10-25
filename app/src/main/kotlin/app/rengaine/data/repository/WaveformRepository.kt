package app.rengaine.data.repository

import app.rengaine.data.remote.ApiResult
import app.rengaine.data.remote.apiCall
import app.rengaine.domain.WaveBars
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Forme d'onde des pistes (U12), déjà réduite aux barres du lecteur.
 * Comme le site : les réponses reçues restent en mémoire (30 pistes, les
 * plus anciennes partent d'abord) ; un `404` (piste pas encore mesurée) ou
 * une erreur n'est pas retenu, on retente au prochain passage sur la piste.
 */
@Singleton
class WaveformRepository @Inject constructor(
    private val accounts: AccountRepository,
) {
    private val cache = object : LinkedHashMap<String, FloatArray>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray>?) = size > MAX_TRACKS
    }

    /** Barres de la piste, ou `null` (pas de forme d'onde : barre simple). */
    suspend fun bars(trackId: Long): FloatArray? {
        val session = accounts.activeSession() ?: return null
        val key = "${session.account.id}:$trackId"
        synchronized(cache) { cache[key] }?.let { return it }
        val result = apiCall { session.api.getWaveform(trackId) }
        val bars = (result as? ApiResult.Ok)?.value?.levels?.let { WaveBars.of(it) } ?: return null
        synchronized(cache) { cache[key] = bars }
        return bars
    }

    private companion object {
        const val MAX_TRACKS = 30
    }
}
