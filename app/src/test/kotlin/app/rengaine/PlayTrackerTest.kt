package app.rengaine

import app.rengaine.data.remote.dto.PlayInputDto
import app.rengaine.playback.PlayTracker
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Comptage des écoutes (F11), selon les règles du lecteur du site. */
class PlayTrackerTest {
    private var clock = 0L
    private var account: Long? = 1L
    private val plays = mutableListOf<Pair<Long, PlayInputDto>>()
    private val tracker = PlayTracker(
        elapsed = { clock },
        now = { Instant.parse("2026-10-24T18:00:00Z").plusMillis(clock) },
        accountId = { account },
        onPlay = { a, p -> plays += a to p },
    )

    @Test
    fun `sous 5 s rien n'est retenu`() {
        tracker.playing(10)
        clock += 4_999
        tracker.finish(completed = false)
        assertTrue(plays.isEmpty())
    }

    @Test
    fun `écoute en cours - copie interrompue, pauses exclues`() {
        tracker.playing(10)
        clock += 4_000
        assertEquals(null, tracker.snapshot())
        clock += 3_000
        val (accountId, p) = tracker.snapshot()!!
        assertEquals(1L, accountId)
        assertEquals(7_000, p.playedMs)
        assertFalse(p.completed)
        assertTrue(p.skipped)
        tracker.paused()
        clock += 60_000
        assertEquals(7_000, tracker.snapshot()!!.second.playedMs)
        // La copie ne termine pas l'écoute : la fin normale l'envoie toujours.
        assertTrue(plays.isEmpty())
        tracker.finish(completed = true)
        assertEquals(null, tracker.snapshot())
        assertEquals(p.startedAt, plays.single().second.startedAt)
    }

    @Test
    fun `les pauses ne comptent pas`() {
        tracker.playing(10)
        clock += 3_000
        tracker.paused()
        clock += 60_000
        tracker.playing(10)
        clock += 3_000
        tracker.finish(completed = true)
        val p = plays.single().second
        assertEquals(6_000, p.playedMs)
        assertTrue(p.completed)
        assertFalse(p.skipped)
        assertEquals("2026-10-24T18:00:00Z", p.startedAt)
        assertEquals("queue", p.context)
    }

    @Test
    fun `quitter avant la fin - passée`() {
        tracker.playing(10)
        clock += 30_000
        tracker.playing(11) // autre piste : la précédente se termine
        clock += 10_000
        tracker.finish(completed = false)
        assertEquals(listOf(10L, 11L), plays.map { it.second.trackId })
        assertTrue(plays.all { it.second.skipped && !it.second.completed })
    }

    @Test
    fun `répétition d'une piste - une écoute par tour`() {
        repeat(3) {
            tracker.playing(10)
            clock += 6_000
            tracker.finish(completed = true)
        }
        assertEquals(3, plays.size)
    }

    @Test
    fun `l'écoute reste au compte du début`() {
        tracker.playing(10)
        clock += 10_000
        account = 2L
        tracker.finish(completed = false)
        assertEquals(1L, plays.single().first)
    }

    @Test
    fun `sans compte actif, rien n'est compté`() {
        account = null
        tracker.playing(10)
        clock += 10_000
        tracker.finish(completed = true)
        assertTrue(plays.isEmpty())
    }
}
