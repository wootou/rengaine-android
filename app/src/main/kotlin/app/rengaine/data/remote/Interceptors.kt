package app.rengaine.data.remote

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Pose `Authorization: Bearer <jeton>` pour un compte. Le même client sert
 * aux appels d'API, au lecteur (flux) et aux pochettes : une seule source
 * d'authentification.
 *
 * Un `401` signale le jeton comme refusé ([onUnauthorized]) : il a été
 * révoqué ailleurs (liste des appareils sur le site, nouveau mot de passe).
 * Seul ce compte doit être reconnecté (F1). **Sauf** `invalid_credentials` :
 * un mauvais mot de passe saisi pour changer de mot de passe ou supprimer
 * son compte (F13), qui ne dit rien du jeton.
 */
class AuthInterceptor(
    private val token: String,
    private val onUnauthorized: () -> Unit,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        val response = chain.proceed(request)
        if (response.code == 401 && !isWrongPassword(response)) onUnauthorized()
        return response
    }
}

/** Lit le code d'erreur sans consommer la réponse (elle reste lisible par Retrofit). */
private fun isWrongPassword(response: Response): Boolean =
    runCatching { response.peekBody(4_096).string().contains("\"invalid_credentials\"") }.getOrDefault(false)

/**
 * Nouvelle tentative sur coupure réseau transitoire, au plus [maxRetries]
 * fois. Seules les requêtes **idempotentes** (GET, HEAD, PUT, DELETE) sont
 * rejouées : rejouer un `POST /auth/login` ou `/auth/pair` à l'aveugle
 * pourrait créer deux jetons, ou brûler un code d'appairage à usage unique.
 * Une réponse HTTP en erreur n'est jamais rejouée ici.
 */
class RetryInterceptor(private val maxRetries: Int = 2) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method !in IDEMPOTENT) return chain.proceed(request)
        // Suppression du compte : jamais rejouée à l'aveugle. Le serveur a pu
        // supprimer avant la coupure ; une seconde requête recevrait un `401`.
        if (request.method == "DELETE" && request.url.encodedPath.trimEnd('/').endsWith("/me")) return chain.proceed(request)
        var attempt = 0
        while (true) {
            try {
                return chain.proceed(request)
            } catch (e: IOException) {
                if (attempt >= maxRetries || chain.call().isCanceled()) throw e
                attempt++
            }
        }
    }

    private companion object {
        val IDEMPOTENT = setOf("GET", "HEAD", "PUT", "DELETE")
    }
}

/** En-tête `User-Agent` explicite : utile dans la liste des appareils du site. */
class UserAgentInterceptor(private val userAgent: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
}
