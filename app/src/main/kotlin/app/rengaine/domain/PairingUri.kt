package app.rengaine.domain

import java.net.URI
import java.net.URLDecoder

/**
 * Contenu du QR d'appairage affiché par le site (schéma figé depuis le
 * contrat 2.19.1) :
 *
 *     rengaine://pair?server=<adresse encodée>&code=XXXX-XXXX
 *
 * Le code est à usage unique et expire en quelques minutes ; il ne donne
 * accès à rien tant qu'il n'est pas échangé contre un jeton. On ne le
 * journalise pas pour autant.
 *
 * Écrit sans `android.net.Uri` pour être testable sur la JVM.
 */
object PairingUri {
    const val SCHEME = "rengaine"
    const val HOST = "pair"

    data class Payload(val server: String, val code: String)

    /** `null` si [raw] n'est pas un QR d'appairage Rengaine. */
    fun parse(raw: String): Payload? {
        val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return null
        if (!uri.scheme.equals(SCHEME, ignoreCase = true)) return null
        if (!uri.host.equals(HOST, ignoreCase = true)) return null
        val params = uri.rawQuery.orEmpty().split('&')
            .mapNotNull { part ->
                val key = part.substringBefore('=', "")
                if (key.isEmpty()) return@mapNotNull null
                key to runCatching { URLDecoder.decode(part.substringAfter('='), "UTF-8") }.getOrNull()
            }
            .toMap()
        val server = params["server"]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val code = params["code"]?.let(::normalizeCode)?.takeIf { it.isNotEmpty() } ?: return null
        return Payload(server, code)
    }

    /** Code saisi à la main : majuscules, sans espaces (« k7qf 2m9x » → « K7QF-2M9X »). */
    fun normalizeCode(input: String): String {
        val compact = input.uppercase().filter { it.isLetterOrDigit() }
        return if (compact.length == 8) compact.substring(0, 4) + "-" + compact.substring(4) else input.trim().uppercase()
    }
}
