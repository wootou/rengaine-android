package app.rengaine.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mêmes règles que `engine/playback.js` du site. */
class TransitionTest {

    private fun track(
        id: Long,
        album: Long? = id * 100,
        disc: Int = 1,
        number: Int? = 1,
        duration: Long = 200_000,
        start: Long? = null,
        end: Long? = null,
        intro: BeatGrid? = null,
        outro: BeatGrid? = null,
    ) = TransitionTrack(id, album, disc, number, duration, start, end, intro, outro)

    private val fade6 = CrossfadeSettings(crossfadeMs = 6_000)

    @Test fun noFadeWhenOffOrAtEndOfQueue() {
        val a = track(1)
        assertEquals(0L, Transition.transition(a, track(2), CrossfadeSettings()).crossfadeMs)
        val none = Transition.transition(a, null, fade6)
        assertEquals(0L, none.crossfadeMs)
        assertEquals(200_000L, none.fadeStartMs)
    }

    @Test fun fadeEndsWhenTheSoundStopsAndNextStartsWhereItsSoundBegins() {
        val plan = Transition.transition(track(1, end = 190_000), track(2, start = 1_200), fade6)
        assertEquals(6_000L, plan.crossfadeMs)
        assertEquals(184_000L, plan.fadeStartMs)
        assertEquals(1_200L, plan.nextStartMs)
    }

    @Test fun neverMoreThanAThirdOfTheShortestTrack() {
        val plan = Transition.transition(track(1), track(2, duration = 9_000), fade6)
        assertEquals(3_000L, plan.crossfadeMs)
        // Ramené sous 500 ms : pas de fondu.
        assertEquals(0L, Transition.transition(track(1), track(2, duration = 1_400), fade6).crossfadeMs)
    }

    @Test fun consecutiveTracksOfAnAlbumOnlyWhenAsked() {
        val a = track(1, album = 7, number = 3)
        val b = track(2, album = 7, number = 4)
        assertTrue(Transition.consecutiveInAlbum(a, b))
        assertEquals(0L, Transition.transition(a, b, fade6).crossfadeMs)
        assertEquals(6_000L, Transition.transition(a, b, fade6.copy(withinAlbum = true)).crossfadeMs)
        // Disque suivant, première piste.
        assertTrue(Transition.consecutiveInAlbum(track(1, album = 7, disc = 1, number = 12), track(2, album = 7, disc = 2, number = 1)))
        // Même album dans le désordre : on fond.
        assertFalse(Transition.consecutiveInAlbum(a, track(3, album = 7, number = 9)))
        // Sans numéro : supposées consécutives.
        assertTrue(Transition.consecutiveInAlbum(track(1, album = 7, number = null), b))
    }

    @Test fun tempoMatchAcceptsDoubleAndHalf() {
        assertEquals(1.0, Transition.tempoMatch(120.0, 120.0)!!.rate, 1e-9)
        assertEquals(2.0, Transition.tempoMatch(124.0, 62.5)!!.factor, 1e-9)
        assertEquals(0.5, Transition.tempoMatch(70.0, 138.0)!!.factor, 1e-9)
        assertNull(Transition.tempoMatch(120.0, 100.0))
        assertNull(Transition.tempoMatch(0.0, 120.0))
    }

    @Test fun beatTransitionLandsOnBeats() {
        // 120 BPM : un temps = 500 ms ; fondu de 6 s = 12 temps.
        val a = track(1, outro = BeatGrid(120.0, 100.0))
        val b = track(2, start = 300, intro = BeatGrid(122.0, 40.0))
        val plan = Transition.beatTransition(a, b, fade6)
        val sync = plan.sync!!
        assertEquals(6_000L, plan.crossfadeMs)
        // Dernier temps tel que le fondu finit avant 200 s : 100 + 387 × 500.
        assertEquals(193_600L, plan.fadeStartMs)
        // Premier temps de la suivante après 300 ms : 40 + 1 × 491,8.
        assertEquals(532L, plan.nextStartMs)
        assertEquals(120.0 / 122.0, sync.rate, 1e-9)
        assertEquals(500.0, sync.beatMs, 1e-9)
    }

    @Test fun beatTransitionFallsBackWithoutGridOrWhenOff() {
        val a = track(1, outro = BeatGrid(120.0, 0.0))
        val b = track(2, intro = BeatGrid(90.0, 0.0))
        assertNull(Transition.beatTransition(a, b, fade6).sync) // tempos trop éloignés
        assertNull(Transition.beatTransition(a, track(2), fade6).sync) // pas de grille
        val c = track(3, intro = BeatGrid(120.0, 0.0))
        assertNull(Transition.beatTransition(a, c, fade6.copy(beatSync = false)).sync)
        assertNotNull(Transition.beatTransition(a, c, fade6).sync)
    }

    @Test fun phaseCorrectionIsBounded() {
        val sync = BeatSync(1.0, 500.0, BeatGrid(120.0, 0.0), BeatGrid(120.0, 0.0))
        assertEquals(0.0, Transition.phaseError(1_000.0, 3_000.0, sync), 1e-9)
        assertEquals(0.2, Transition.phaseError(1_000.0, 3_100.0, sync), 1e-9)
        // 0,2 temps × 500 ms / 2000 = 0,05 → plafonné à 3 %.
        assertEquals(0.97, Transition.syncedRate(sync, 0.2), 1e-9)
        assertEquals(1.0, Transition.glideRate(0.98, 12_000), 1e-9)
        assertEquals(0.99, Transition.glideRate(0.98, 6_000), 1e-9)
    }
}
