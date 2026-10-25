package app.rengaine.playback

import android.content.Context
import app.rengaine.data.remote.ApiJson
import app.rengaine.data.remote.dto.PlayQueueDto
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/** Dernière file connue du serveur pour un compte, avec sa version (`ETag`). */
@Serializable
data class StoredQueue(val etag: String? = null, val queue: PlayQueueDto)

/**
 * Copie locale de la file (F6), un fichier par compte : elle permet de
 * retrouver sa file sans réseau. C'est la dernière version **confirmée par
 * le serveur** (réponse de `GET` ou `PUT /me/queue`), pistes comprises.
 *
 * Un fichier JSON plutôt qu'une table Room : la file se lit et s'écrit
 * d'un bloc, et cela évite une migration de la base des comptes.
 */
@Singleton
class QueueStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dir = File(context.filesDir, "queues")

    suspend fun load(accountId: Long): StoredQueue? = withContext(Dispatchers.IO) {
        runCatching { ApiJson.decodeFromString(StoredQueue.serializer(), file(accountId).readText()) }.getOrNull()
    }

    suspend fun save(accountId: Long, stored: StoredQueue) = withContext(Dispatchers.IO) {
        runCatching {
            dir.mkdirs()
            // Écriture dans un fichier temporaire puis renommage : jamais de
            // fichier à moitié écrit si le processus est tué.
            val tmp = File(dir, "$accountId.json.tmp")
            tmp.writeText(ApiJson.encodeToString(StoredQueue.serializer(), stored))
            tmp.renameTo(file(accountId))
        }
    }

    /** Efface les files des comptes qui ne sont plus mémorisés (déconnexion, retrait). */
    suspend fun keepOnly(accountIds: Set<Long>) = withContext(Dispatchers.IO) {
        dir.listFiles()?.forEach { f ->
            val id = f.name.substringBefore('.').toLongOrNull()
            if (id == null || id !in accountIds) f.delete()
        }
    }

    private fun file(accountId: Long) = File(dir, "$accountId.json")
}
