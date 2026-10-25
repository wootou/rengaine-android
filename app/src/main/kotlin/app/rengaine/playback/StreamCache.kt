package app.rengaine.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.SimpleCache
import app.rengaine.data.local.SessionStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.TreeSet
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Cache disque des flux (S2), repris de Musique. `SimpleCache` n'admet
 * qu'une instance par dossier dans tout le processus : d'où le singleton,
 * partagé par le lecteur et l'écran Réglages (taille, « Vider le cache »).
 *
 * Contrairement à Musique, la taille change **tout de suite** : l'évinceur
 * de Media3 a une limite figée, on en utilise un équivalent réglable.
 */
@Singleton
@OptIn(UnstableApi::class)
class StreamCache @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: SessionStore,
) {
    private val evictor by lazy {
        // Lu une fois, au premier morceau joué : un petit fichier DataStore.
        AdjustableLruEvictor(runBlocking { session.cacheSizeMb.first() } * MB)
    }

    val cache: Cache by lazy {
        SimpleCache(File(context.cacheDir, DIR_NAME), evictor, StandaloneDatabaseProvider(context))
    }

    fun usedBytes(): Long = cache.cacheSpace

    /** Nouvelle limite : le surplus part aussitôt, les plus anciens morceaux d'abord. */
    fun setMaxBytes(bytes: Long) {
        val c = cache
        // Les rappels de l'évinceur se font sous le verrou du cache : on le prend aussi.
        synchronized(c) {
            evictor.maxBytes = bytes
            evictor.evict(c, 0)
        }
    }

    /** « Vider le cache », sauf le morceau en cours ([keepTrackId], toutes qualités) pour ne pas couper la lecture. */
    fun clear(keepTrackId: Long?) {
        val c = cache
        c.keys.filter { keepTrackId == null || MediaItems.trackIdOfKey(it) != keepTrackId }.forEach { c.removeResource(it) }
    }

    companion object {
        private const val DIR_NAME = "streams"
        /** Mo décimal, comme les tailles qu'affiche Android (`Formatter`). */
        const val MB = 1_000_000L
    }
}

/**
 * Le moins récemment écouté part en premier, comme `LeastRecentlyUsedCacheEvictor`
 * de Media3, mais avec une limite modifiable.
 */
@OptIn(UnstableApi::class)
private class AdjustableLruEvictor(@Volatile var maxBytes: Long) : CacheEvictor {
    private val spans = TreeSet<CacheSpan> { a, b ->
        if (a.lastTouchTimestamp == b.lastTouchTimestamp) a.compareTo(b) else a.lastTouchTimestamp.compareTo(b.lastTouchTimestamp)
    }
    private var size = 0L

    override fun requiresCacheSpanTouches() = true
    override fun onCacheInitialized() = Unit

    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        if (length != C.LENGTH_UNSET.toLong()) evict(cache, length)
    }

    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        spans.add(span)
        size += span.length
        evict(cache, 0)
    }

    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        if (spans.remove(span)) size -= span.length
    }

    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        onSpanRemoved(cache, oldSpan)
        onSpanAdded(cache, newSpan)
    }

    fun evict(cache: Cache, required: Long) {
        while (size + required > maxBytes && spans.isNotEmpty()) {
            val oldest = spans.first()
            cache.removeSpan(oldest)
            // Filet : si le cache ne l'a pas signalé, on ne boucle pas sans fin.
            if (spans.remove(oldest)) size -= oldest.length
        }
    }
}
