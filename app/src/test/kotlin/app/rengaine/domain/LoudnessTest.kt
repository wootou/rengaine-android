package app.rengaine.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoudnessTest {
    private val auto = GainSettings("auto", -16.0, preventClipping = true)

    private fun t(album: Long?, n: Int?, disc: Int? = 1, lufs: Double? = -10.0, peak: Double? = -1.0, albumLufs: Double? = -12.0, albumPeak: Double? = -0.5) =
        GainTrack(album, disc, n, LoudnessMeasure(lufs, peak), LoudnessMeasure(albumLufs, albumPeak))

    @Test fun offGivesZero() = assertEquals(0.0, Loudness.gainDb(auto.copy(normalization = "off"), t(1, 1), null, null), 0.0)

    @Test fun isolatedTrackUsesTrackGain() = assertEquals(-6.0, Loudness.gainDb(auto, t(1, 3), t(2, 1), null), 0.0)

    @Test fun albumInOrderUsesAlbumGain() {
        // −16 − (−12) = −4, sous le plafond de 0,5.
        assertEquals(-4.0, Loudness.gainDb(auto, t(1, 3), t(1, 2), null), 0.0)
        assertEquals(-4.0, Loudness.gainDb(auto, t(1, 3), null, t(1, 4)), 0.0)
    }

    @Test fun clippingCapsAmplification() {
        // −16 − (−20) = +4, plafonné à −(−1,5) = 1,5.
        assertEquals(1.5, Loudness.gainDb(auto, t(1, 1, lufs = -20.0, peak = -1.5), null, null), 0.0)
        assertEquals(4.0, Loudness.gainDb(auto.copy(preventClipping = false), t(1, 1, lufs = -20.0, peak = -1.5), null, null), 0.0)
    }

    @Test fun missingMeasures() {
        assertEquals(-6.0, Loudness.gainDb(auto.copy(normalization = "album"), t(1, 1, albumLufs = null), null, null), 0.0)
        assertEquals(0.0, Loudness.gainDb(auto.copy(normalization = "track"), t(1, 1, lufs = null), null, null), 0.0)
    }

    @Test fun consecutiveRules() {
        assertTrue(Loudness.consecutiveInAlbum(t(1, 12, disc = 1), t(1, 1, disc = 2)))
        assertTrue(Loudness.consecutiveInAlbum(t(1, null), t(1, 7)))
        assertFalse(Loudness.consecutiveInAlbum(t(1, 2), t(1, 4)))
        assertFalse(Loudness.consecutiveInAlbum(t(1, 2), t(2, 3)))
        assertFalse(Loudness.consecutiveInAlbum(t(null, 2), t(null, 3)))
    }

    @Test fun roundsToHundredth() = assertEquals(-5.67, Loudness.gainDb(auto.copy(normalization = "track"), t(1, 1, lufs = -10.333), null, null), 0.0)
}
