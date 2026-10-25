package app.rengaine

import app.rengaine.data.remote.ServerAddress
import app.rengaine.data.remote.idList
import app.rengaine.playback.MediaItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Adresses du lecteur : flux et pochettes résolus contre la racine du serveur (S2). */
class PlaybackUrlsTest {

    @Test
    fun `flux et pochette sous un sous-chemin`() {
        val address = ServerAddress.fromStored("https://exemple.fr/rengaine")
        assertEquals("https://exemple.fr/rengaine/api/v2/tracks/12/stream", address.resolve("/api/v2/tracks/12/stream"))
        val cover = address.resolve("/api/v2/images/abc")
        assertEquals("https://exemple.fr/rengaine/api/v2/images/abc?size=l", MediaItems.withSize(cover, "l"))
    }

    @Test
    fun `la taille remplace celle deja presente`() {
        assertEquals("https://exemple.fr/api/v2/images/abc?size=m", MediaItems.withSize("https://exemple.fr/api/v2/images/abc?size=s", "m"))
    }

    @Test
    fun `identifiants de piste`() {
        assertEquals(42L, MediaItems.trackIdOf(MediaItems.mediaId(42)))
        assertNull(MediaItems.trackIdOf("autre"))
    }

    @Test
    fun `liste d'identifiants du contrat`() {
        assertEquals("1,2,3", idList(listOf(1L, 2L, 3L)))
        assertNull(idList(emptyList()))
    }
}
