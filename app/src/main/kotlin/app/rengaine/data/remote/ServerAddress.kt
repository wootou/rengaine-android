package app.rengaine.data.remote

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Adresse d'un serveur Rengaine, normalisée : schéma et hôte en minuscules,
 * sans `/` final, sous-chemin conservé (`https://exemple.fr/rengaine`).
 * L'API est toujours `<adresse>/api/v2/`.
 */
@JvmInline
value class ServerAddress private constructor(val root: String) {

    /** Base des appels Retrofit : se termine par `/`. */
    val apiBase: String get() = "$root/api/v2/"

    /**
     * Résout un chemin renvoyé par l'API (`streamUrl`, `coverUrl`…), relatif
     * à la racine du serveur. Si le serveur l'a déjà préfixé par son
     * sous-chemin (`/rengaine/api/v2/…`), le préfixe n'est pas doublé.
     */
    fun resolve(path: String): String {
        if (path.startsWith("https://") || path.startsWith("http://")) return path
        val origin = root.toHttpUrlOrNull()?.let { u ->
            val port = if (u.port != HttpUrl.defaultPort(u.scheme)) ":${u.port}" else ""
            "${u.scheme}://${u.host}$port"
        } ?: root
        val prefix = root.removePrefix(origin)
        val clean = "/" + path.trimStart('/')
        return if (prefix.isNotEmpty() && (clean == prefix || clean.startsWith("$prefix/"))) {
            origin + clean
        } else {
            root + clean
        }
    }

    /** Hôte seul, pour l'affichage (« musique.exemple.fr »). */
    val host: String get() = root.toHttpUrlOrNull()?.host ?: root

    /**
     * `http://` : rien n'est chiffré, ni le mot de passe ni la musique.
     * Accepté mais déconseillé (D6, décision du 2026-10-25) :
     * l'app l'affiche par un avertissement.
     */
    val isCleartext: Boolean get() = root.startsWith("http://")

    /** Adresse sans `https://`, pour l'affichage (`http://` reste visible). */
    val display: String get() = root.removePrefix("https://")

    override fun toString(): String = root

    sealed interface ParseResult {
        data class Valid(val address: ServerAddress) : ParseResult
        data object Empty : ParseResult
        data object Invalid : ParseResult
    }

    companion object {
        /**
         * Accepte ce qu'un utilisateur tape ou colle : sans schéma
         * (`https://` est ajouté), avec un `/` final, ou même avec
         * `/api/v2` copié depuis la documentation.
         */
        fun parse(input: String): ParseResult {
            var text = input.trim()
            if (text.isEmpty()) return ParseResult.Empty
            if (text.contains(' ')) return ParseResult.Invalid
            val hasScheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(text)
            if (!hasScheme) text = "https://$text"
            val url = text.toHttpUrlOrNull() ?: return ParseResult.Invalid
            if (!looksLikeServer(url)) return ParseResult.Invalid
            return ParseResult.Valid(ServerAddress(normalize(url)))
        }

        /**
         * Refuse ce qui n'a pas la forme d'une adresse de serveur avant toute
         * requête : une saisie ratée (un mot de passe tapé dans ce champ)
         * ne doit pas partir en requête DNS.
         */
        private fun looksLikeServer(url: HttpUrl): Boolean {
            if (url.username.isNotEmpty() || url.password.isNotEmpty()) return false
            if (url.encodedQuery != null || url.encodedFragment != null) return false
            val pathOk = url.pathSegments.all { it.isEmpty() || PATH_SEGMENT.matches(it) }
            return pathOk && validHost(url.host)
        }

        private fun validHost(host: String): Boolean {
            if (host == "localhost") return true
            if (host.contains(':')) return true // IPv6 entre crochets, déjà vérifiée par OkHttp
            if (IPV4.matches(host)) return host.split('.').all { it.toInt() <= 255 }
            val labels = host.split('.')
            if (labels.size < 2 || host.length > 253) return false
            if (!labels.all { it.length <= 63 && LABEL.matches(it) }) return false
            val tld = labels.last()
            return TLD.matches(tld) || tld.startsWith("xn--")
        }

        private val LABEL = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?$")
        private val TLD = Regex("^[a-z]{2,63}$")
        private val IPV4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
        private val PATH_SEGMENT = Regex("^[A-Za-z0-9._~-]+$")

        /** Pour relire une adresse déjà validée (stockée en base). */
        fun fromStored(root: String): ServerAddress = ServerAddress(root)

        private fun normalize(url: HttpUrl): String {
            val segments = url.encodedPathSegments.filter { it.isNotEmpty() }.toMutableList()
            // Retire un « /api/v2 » (ou « /api ») collé par erreur.
            if (segments.size >= 2 && segments[segments.size - 2] == "api" && segments.last() == "v2") {
                segments.removeAt(segments.size - 1)
                segments.removeAt(segments.size - 1)
            } else if (segments.lastOrNull() == "api") {
                segments.removeAt(segments.size - 1)
            }
            val port = if (url.port != HttpUrl.defaultPort(url.scheme)) ":${url.port}" else ""
            val path = if (segments.isEmpty()) "" else "/" + segments.joinToString("/")
            return "${url.scheme}://${url.host}$port$path"
        }
    }
}
