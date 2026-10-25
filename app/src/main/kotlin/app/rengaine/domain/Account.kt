package app.rengaine.domain

import app.rengaine.data.remote.ServerAddress

/** Un compte mémorisé, tel que l'interface le voit (sans jeton). */
data class Account(
    val id: Long,
    val address: ServerAddress,
    val serverName: String,
    val apiVersion: String,
    val userId: Long,
    val email: String,
    val displayName: String,
    val isAdmin: Boolean,
    val deviceName: String,
    /** Jeton refusé par le serveur : ce compte doit se reconnecter. */
    val needsReauth: Boolean,
)

/**
 * Versions du contrat que l'app sait lire. Depuis la 2.0 le contrat
 * n'ajoute que : toute 2.x convient. Une 3.x serait cassante, une 1.x est
 * l'ancienne API (l'app Musique).
 */
object ApiCompatibility {
    const val SUPPORTED_MAJOR = 2

    sealed interface Verdict {
        data object Compatible : Verdict
        data object ServerTooOld : Verdict
        data object ServerTooNew : Verdict
        data object Unknown : Verdict
    }

    fun check(apiVersion: String): Verdict {
        val major = apiVersion.trim().substringBefore('.').toIntOrNull() ?: return Verdict.Unknown
        return when {
            major == SUPPORTED_MAJOR -> Verdict.Compatible
            major < SUPPORTED_MAJOR -> Verdict.ServerTooOld
            else -> Verdict.ServerTooNew
        }
    }
}
