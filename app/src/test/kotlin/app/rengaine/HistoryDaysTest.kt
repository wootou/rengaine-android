package app.rengaine

import app.rengaine.ui.history.DayHeader
import app.rengaine.ui.history.HistoryDays
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/** En-têtes de jour de l'historique d'écoute, comme le site. */
class HistoryDaysTest {
    private val today = LocalDate.of(2026, 10, 24)
    private val fr = Locale.FRENCH

    @Test fun `aujourd'hui et hier`() {
        assertEquals(DayHeader.Today, HistoryDays.header(today, today, fr))
        assertEquals(DayHeader.Yesterday, HistoryDays.header(today.minusDays(1), today, fr))
    }

    @Test fun `date de l'année, première lettre seulement en majuscule`() =
        assertEquals(DayHeader.Date("Mercredi 21 octobre"), HistoryDays.header(LocalDate.of(2026, 10, 21), today, fr))

    @Test fun `autre année, avec l'année`() =
        assertEquals(DayHeader.Date("Mercredi 31 décembre 2025"), HistoryDays.header(LocalDate.of(2025, 12, 31), today, fr))

    @Test fun `heure et jour dans le fuseau du téléphone`() {
        val paris = ZoneId.of("Europe/Paris")
        // 22:30 UTC le 23 = 00:30 le 24 à Paris (heure d'été).
        assertEquals(LocalDate.of(2026, 10, 24), HistoryDays.dayOf("2026-10-23T22:30:00Z", paris))
        assertEquals("00:30", HistoryDays.timeOf("2026-10-23T22:30:00Z", paris))
    }
}
