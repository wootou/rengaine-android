package app.rengaine.ui.components

/** Durée d'une piste : « 3:07 », « 1:02:45 » au-delà d'une heure. */
fun formatDuration(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0) + 500) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

/** Format affiché à côté d'un album : « FLAC », « MP3 »… `null` si inconnu. */
fun formatLabel(format: String?): String? = format
    ?.takeIf { it.isNotBlank() && it != "other" }
    ?.uppercase()

/** Durée d'une playlist : « 28 min », « 1 h 05 » au-delà d'une heure. */
fun formatMinutes(ms: Long): String {
    val minutes = ((ms.coerceAtLeast(0) + 30_000) / 60_000).coerceAtLeast(1)
    return if (minutes >= 60) "%d h %02d".format(minutes / 60, minutes % 60) else "$minutes min"
}
