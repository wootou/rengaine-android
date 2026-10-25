package app.rengaine

import app.rengaine.domain.WaveBars
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class WaveBarsTest {

    @Test
    fun `hauteur lineaire sur 30 dB sous le pic de la piste`() {
        val bars = WaveBars.of(listOf(-6f, -21f, -36f, -60f), count = 4)
        assertArrayEquals(floatArrayOf(1f, 0.5f, 0.06f, 0.06f), bars, 1e-4f)
    }

    @Test
    fun `une barre garde le pic de ses tranches`() {
        // 8 tranches pour 4 barres : deux par barre.
        val bars = WaveBars.of(listOf(-30f, 0f, -15f, -30f, null, -3f, -30f, -30f), count = 4)
        assertArrayEquals(floatArrayOf(1f, 0.5f, 0.9f, 0.06f), bars, 1e-4f)
    }

    @Test
    fun `silence = pas de barre, piste muette = rien`() {
        assertArrayEquals(floatArrayOf(1f, 0f), WaveBars.of(listOf(-10f, null), count = 2), 0f)
        assertEquals(0, WaveBars.of(listOf(null, null)).size)
        assertEquals(0, WaveBars.of(emptyList()).size)
    }

    @Test
    fun `moins de tranches que de barres`() {
        // Comme le site : chaque barre prend au moins une tranche.
        val bars = WaveBars.of(listOf(0f, -15f), count = 4)
        assertArrayEquals(floatArrayOf(1f, 1f, 0.5f, 0.5f), bars, 1e-4f)
    }
}
