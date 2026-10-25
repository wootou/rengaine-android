package app.rengaine.data.remote

import kotlinx.serialization.json.Json

/**
 * Configuration JSON unique de l'app (S3).
 *
 * Le contrat 2.x n'est qu'additif : un serveur plus récent peut ajouter un
 * champ ou une valeur d'énumération à tout moment.
 * - `ignoreUnknownKeys` : un champ inconnu est ignoré.
 * - `coerceInputValues` : une valeur d'énumération inconnue (ou un `null`
 *   inattendu) prend la valeur par défaut du champ, `UNKNOWN` pour les
 *   énumérations.
 *
 * Exception : `/me/settings`, dont les champs inconnus doivent être
 * renvoyés tels quels. Ce document se manipule en `JsonObject` brut, jamais
 * à travers un DTO (F12).
 */
val ApiJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    encodeDefaults = false
}
