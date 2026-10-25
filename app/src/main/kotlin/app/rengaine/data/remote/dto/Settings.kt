package app.rengaine.data.remote.dto

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/** Thème choisi dans Réglages › Apparence (`interface.theme`). */
enum class ThemeChoice(val wire: String) {
    AUTO("auto"), LIGHT("light"), DARK("dark");

    companion object {
        fun of(wire: String?): ThemeChoice = entries.firstOrNull { it.wire == wire } ?: AUTO
    }
}

/**
 * Réglages du compte (`/me/settings`, F12), gardés en **JSON brut** : le
 * contrat demande de renvoyer tels quels les champs qu'on ne connaît pas,
 * pour ne pas effacer ceux d'un client plus récent (le site, par exemple).
 * Les propriétés typées ne sont que des lectures, avec les valeurs par
 * défaut du contrat si un champ manque.
 */
data class AccountSettings(val version: Long, val raw: JsonObject) {

    val theme: ThemeChoice get() = ThemeChoice.of(string(PATH_THEME))

    /** Mode infini (WEB-05) : actif par défaut. */
    val infiniteEnabled: Boolean get() = boolean(PATH_INFINITE) ?: true

    /** Qualité du flux (`playback.streamQuality`) : `original`, `high`, `medium`, `low`. */
    val streamQualityWifi: String get() = string(PATH_QUALITY_WIFI) ?: "original"
    val streamQualityMobile: String get() = string(PATH_QUALITY_MOBILE) ?: "original"

    /** Normalisation (`playback.normalization`) : `off`, `track`, `album`, `auto` (défaut). */
    val normalization: String get() = string(PATH_NORMALIZATION) ?: "auto"
    /** Volume visé, de −23 à −8 LUFS (−16 par défaut). */
    val targetLufs: Double get() = (at(PATH_TARGET_LUFS) as? JsonPrimitive)?.doubleOrNull?.coerceIn(-23.0, -8.0) ?: -16.0
    val preventClipping: Boolean get() = boolean(PATH_PREVENT_CLIPPING) ?: true

    /** Lecture continue (`continuous.enabled`, LEC-03) : inactive par défaut. */
    val continuousEnabled: Boolean get() = boolean(PATH_CONTINUOUS) ?: false
    val harmonicMixing: Boolean get() = boolean(PATH_HARMONIC) ?: false
    /** Écart de tempo accepté, en % ; `null` = pas de contrainte. */
    val bpmTolerancePercent: Int? get() = (at(PATH_BPM_TOLERANCE) as? JsonPrimitive)?.intOrNull
    /** `any`, `steady`, `rising`, `falling`. */
    val energyTrend: String get() = string(PATH_ENERGY) ?: "any"
    val favoritesWeight: Double get() = (at(PATH_FAVORITES_WEIGHT) as? JsonPrimitive)?.doubleOrNull ?: 0.3
    val noRepeatHours: Int get() = (at(PATH_NO_REPEAT) as? JsonPrimitive)?.intOrNull ?: 24
    val artistSpacing: Int get() = (at(PATH_ARTIST_SPACING) as? JsonPrimitive)?.intOrNull ?: 3
    val excludedGenres: List<Long> get() = longs(PATH_EXCLUDED_GENRES)

    /** Dernier tirage de la page Aléatoire (`random`), repris d'un appareil à l'autre. */
    val randomGenreIds: List<Long> get() = longs(PATH_RANDOM_GENRES)
    val randomExcludedAlbumIds: List<Long> get() = longs(PATH_RANDOM_EXCLUDED_ALBUMS)
    val randomCount: Int get() = (at(PATH_RANDOM_COUNT) as? JsonPrimitive)?.intOrNull?.coerceIn(1, 200) ?: 100

    private fun longs(path: List<String>): List<Long> =
        (at(path) as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.longOrNull }

    fun string(path: List<String>): String? = (at(path) as? JsonPrimitive)?.contentOrNull
    fun boolean(path: List<String>): Boolean? = (at(path) as? JsonPrimitive)?.booleanOrNull

    private fun at(path: List<String>): JsonElement? =
        path.fold(raw as JsonElement?) { node, key -> (node as? JsonObject)?.get(key) }

    companion object {
        val PATH_THEME = listOf("interface", "theme")
        val PATH_INFINITE = listOf("infinite", "enabled")
        val PATH_QUALITY_WIFI = listOf("playback", "streamQuality", "wifi")
        val PATH_QUALITY_MOBILE = listOf("playback", "streamQuality", "mobile")
        val PATH_NORMALIZATION = listOf("playback", "normalization")
        val PATH_TARGET_LUFS = listOf("playback", "targetLufs")
        val PATH_PREVENT_CLIPPING = listOf("playback", "preventClipping")
        val PATH_CONTINUOUS = listOf("continuous", "enabled")
        val PATH_HARMONIC = listOf("continuous", "harmonicMixing")
        val PATH_BPM_TOLERANCE = listOf("continuous", "bpmTolerancePercent")
        val PATH_ENERGY = listOf("continuous", "energyTrend")
        val PATH_FAVORITES_WEIGHT = listOf("continuous", "favoritesWeight")
        val PATH_NO_REPEAT = listOf("continuous", "noRepeatHours")
        val PATH_ARTIST_SPACING = listOf("continuous", "artistSpacing")
        val PATH_EXCLUDED_GENRES = listOf("continuous", "excludedGenres")
        val PATH_RANDOM = listOf("random")
        val PATH_RANDOM_GENRES = listOf("random", "genreIds")
        val PATH_RANDOM_EXCLUDED_ALBUMS = listOf("random", "excludedAlbumIds")
        val PATH_RANDOM_COUNT = listOf("random", "count")

        /**
         * Lit un document `{version, updatedAt, settings}` (réponse de
         * `GET`/`PUT`, ou `details.current` d'un `412`). `null` s'il n'en a
         * pas la forme.
         */
        fun fromDocument(doc: JsonObject?): AccountSettings? {
            val settings = doc?.get("settings") as? JsonObject ?: return null
            val version = (doc["version"] as? JsonPrimitive)?.longOrNull ?: return null
            return AccountSettings(version, settings)
        }
    }
}

/** Copie de l'objet où la valeur au bout de [path] est remplacée ; les objets manquants sont créés. */
fun JsonObject.with(path: List<String>, value: JsonElement): JsonObject {
    require(path.isNotEmpty())
    val key = path.first()
    if (path.size == 1) return JsonObject(this + (key to value))
    val child = this[key] as? JsonObject ?: JsonObject(emptyMap())
    return JsonObject(this + (key to child.with(path.drop(1), value)))
}

/** Raccourci pour l'`ETag` d'une version, au format du serveur (`"12"`). */
fun etagOf(version: Long): String = "\"$version\""

