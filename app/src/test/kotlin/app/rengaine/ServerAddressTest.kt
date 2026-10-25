package app.rengaine

import app.rengaine.data.remote.ServerAddress
import app.rengaine.data.remote.ServerAddress.ParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerAddressTest {

    private fun root(input: String): String =
        (ServerAddress.parse(input) as ParseResult.Valid).address.root

    @Test
    fun `ajoute https et retire le slash final`() {
        assertEquals("https://musique.exemple.fr", root("musique.exemple.fr"))
        assertEquals("https://musique.exemple.fr", root("  https://Musique.Exemple.fr/  "))
    }

    @Test
    fun `garde le sous-chemin et retire un api v2 colle`() {
        assertEquals("https://exemple.fr/rengaine", root("exemple.fr/rengaine/"))
        assertEquals("https://exemple.fr/rengaine", root("https://exemple.fr/rengaine/api/v2"))
        assertEquals("https://exemple.fr", root("https://exemple.fr/api/v2/"))
    }

    @Test
    fun `garde un port non standard`() {
        assertEquals("https://exemple.fr:8443", root("exemple.fr:8443"))
        assertEquals("https://exemple.fr", root("exemple.fr:443"))
    }

    @Test
    fun `accepte http en le signalant, refuse les adresses invalides`() {
        val http = ServerAddress.parse("http://exemple.fr") as ParseResult.Valid
        assertEquals("http://exemple.fr", http.address.toString())
        assertTrue(http.address.isCleartext)
        assertTrue(!(ServerAddress.parse("exemple.fr") as ParseResult.Valid).address.isCleartext)
        assertTrue(ServerAddress.parse("") is ParseResult.Empty)
        assertTrue(ServerAddress.parse("pas une adresse") is ParseResult.Invalid)
        assertTrue(ServerAddress.parse("exemple") is ParseResult.Invalid)
    }

    @Test
    fun `refuse une saisie qui n'a pas la forme d'une adresse`() {
        // Ce qu'un mot de passe tapé au mauvais endroit peut donner.
        listOf(
            "Motdepasse.2026",
            "abc.de/f!g",
            "x@exemple.fr",
            "exemple.fr?a=1",
            "exemple.fr#a",
            "-abc.exemple.fr",
            "exemple.f",
            "exemple.123",
            "999.1.1.1",
            "exemple.fr/sous chemin",
        ).forEach { assertTrue(it, ServerAddress.parse(it) is ParseResult.Invalid) }
    }

    @Test
    fun `accepte les adresses de serveur ordinaires`() {
        assertEquals("https://192.168.0.10:8443", root("192.168.0.10:8443"))
        assertEquals("https://localhost:8443", root("localhost:8443"))
        assertEquals("https://xn--msique-pya.fr", root("músique.fr"))
        assertEquals("https://mon-serveur.exemple.fr/ma_musique", root("mon-serveur.exemple.fr/ma_musique"))
    }

    @Test
    fun `base de l'API et resolution des chemins`() {
        val plain = ServerAddress.fromStored("https://exemple.fr")
        assertEquals("https://exemple.fr/api/v2/", plain.apiBase)
        assertEquals("https://exemple.fr/api/v2/tracks/1/stream", plain.resolve("/api/v2/tracks/1/stream"))

        val sub = ServerAddress.fromStored("https://exemple.fr/rengaine")
        assertEquals("https://exemple.fr/rengaine/api/v2/", sub.apiBase)
        // Chemin relatif à la racine du serveur…
        assertEquals("https://exemple.fr/rengaine/api/v2/images/ab", sub.resolve("/api/v2/images/ab"))
        // …ou déjà préfixé par le sous-chemin : pas de doublon.
        assertEquals("https://exemple.fr/rengaine/api/v2/images/ab", sub.resolve("/rengaine/api/v2/images/ab"))
    }
}
