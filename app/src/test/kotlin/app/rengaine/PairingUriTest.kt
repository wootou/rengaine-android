package app.rengaine

import app.rengaine.domain.ApiCompatibility
import app.rengaine.domain.PairingUri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingUriTest {

    @Test
    fun `lit l'exemple du contrat`() {
        val payload = PairingUri.parse("rengaine://pair?server=https%3A%2F%2Fmusique.exemple.fr&code=K7QF-2M9X")
        assertEquals(PairingUri.Payload("https://musique.exemple.fr", "K7QF-2M9X"), payload)
    }

    @Test
    fun `ordre des parametres et sous-chemin`() {
        val payload = PairingUri.parse("rengaine://pair?code=k7qf2m9x&server=https%3A%2F%2Fexemple.fr%2Frengaine")
        assertEquals(PairingUri.Payload("https://exemple.fr/rengaine", "K7QF-2M9X"), payload)
    }

    @Test
    fun `refuse ce qui n'est pas un QR d'appairage`() {
        assertNull(PairingUri.parse("https://exemple.fr"))
        assertNull(PairingUri.parse("musique://pair?server=x&code=y"))
        assertNull(PairingUri.parse("rengaine://autre?server=x&code=y"))
        assertNull(PairingUri.parse("rengaine://pair?server=https%3A%2F%2Fexemple.fr"))
        assertNull(PairingUri.parse("rengaine://pair?code=K7QF-2M9X"))
    }

    @Test
    fun `normalise un code saisi a la main`() {
        assertEquals("K7QF-2M9X", PairingUri.normalizeCode(" k7qf 2m9x "))
        assertEquals("K7QF-2M9X", PairingUri.normalizeCode("K7QF-2M9X"))
    }

    @Test
    fun `compatibilite du contrat`() {
        assertEquals(ApiCompatibility.Verdict.Compatible, ApiCompatibility.check("2.21.0"))
        assertEquals(ApiCompatibility.Verdict.Compatible, ApiCompatibility.check("2.0.0"))
        assertEquals(ApiCompatibility.Verdict.ServerTooOld, ApiCompatibility.check("1.4.0"))
        assertEquals(ApiCompatibility.Verdict.ServerTooNew, ApiCompatibility.check("3.0.0"))
        assertEquals(ApiCompatibility.Verdict.Unknown, ApiCompatibility.check("v2"))
    }
}
