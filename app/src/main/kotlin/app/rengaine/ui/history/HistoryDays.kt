package app.rengaine.ui.history

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** En-tête d'un jour de l'historique. */
sealed interface DayHeader {
    data object Today : DayHeader
    data object Yesterday : DayHeader
    /** « Vendredi 23 octobre », avec l'année si ce n'est pas l'année en cours. */
    data class Date(val text: String) : DayHeader
}

/** Jours et heures de l'historique, dans le fuseau du téléphone (comme le site). */
object HistoryDays {

    fun dayOf(startedAt: String, zone: ZoneId): LocalDate? =
        runCatching { Instant.parse(startedAt).atZone(zone).toLocalDate() }.getOrNull()

    fun timeOf(startedAt: String, zone: ZoneId): String =
        runCatching { Instant.parse(startedAt).atZone(zone).format(TIME) }.getOrDefault("")

    fun header(day: LocalDate, today: LocalDate, locale: Locale): DayHeader = when (day) {
        today -> DayHeader.Today
        today.minusDays(1) -> DayHeader.Yesterday
        else -> {
            val pattern = if (day.year == today.year) "EEEE d MMMM" else "EEEE d MMMM yyyy"
            // Majuscule à la première lettre seulement (« Vendredi 23 octobre »), comme le site.
            val text = DateTimeFormatter.ofPattern(pattern, locale).format(day)
            DayHeader.Date(text.replaceFirstChar { it.titlecase(locale) })
        }
    }

    private val TIME = DateTimeFormatter.ofPattern("HH:mm")
}
