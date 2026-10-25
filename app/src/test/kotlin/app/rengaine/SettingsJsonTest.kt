package app.rengaine

import app.rengaine.data.remote.ApiJson
import app.rengaine.data.remote.dto.AccountSettings
import app.rengaine.data.remote.dto.ThemeChoice
import app.rengaine.data.remote.dto.with
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Réglages du compte (F12) : les champs inconnus doivent repartir tels quels. */
class SettingsJsonTest {

    private val doc = ApiJson.decodeFromString(
        JsonObject.serializer(),
        """
        {"version": 7, "updatedAt": "2026-10-24T10:00:00Z", "settings": {
          "playback": {"normalization": "auto", "crossfadeMs": 0, "futur": {"x": 1}},
          "interface": {"theme": "light", "background": true},
          "infinite": {"enabled": false},
          "champInconnu": [1, 2, 3]
        }}
        """,
    )

    @Test
    fun `lit la version et les valeurs typées`() {
        val s = AccountSettings.fromDocument(doc)!!
        assertEquals(7L, s.version)
        assertEquals(ThemeChoice.LIGHT, s.theme)
        assertFalse(s.infiniteEnabled)
    }

    @Test
    fun `valeurs par défaut du contrat si un champ manque`() {
        val s = AccountSettings(1, JsonObject(emptyMap()))
        assertEquals(ThemeChoice.AUTO, s.theme)
        assertTrue(s.infiniteEnabled)
        assertEquals(ThemeChoice.AUTO, ThemeChoice.of("violet"))
    }

    @Test
    fun `modifier un réglage garde tout le reste, inconnus compris`() {
        val s = AccountSettings.fromDocument(doc)!!
        val changed = s.raw.with(AccountSettings.PATH_INFINITE, JsonPrimitive(true))
        assertTrue(AccountSettings(7, changed).infiniteEnabled)
        assertEquals(s.raw["champInconnu"], changed["champInconnu"])
        assertEquals(s.raw["playback"], changed["playback"])
        assertEquals(s.raw["interface"], changed["interface"])
    }

    @Test
    fun `crée les objets manquants sur le chemin`() {
        val changed = JsonObject(emptyMap()).with(AccountSettings.PATH_THEME, JsonPrimitive("dark"))
        assertEquals(ThemeChoice.DARK, AccountSettings(0, changed).theme)
        assertEquals(JsonPrimitive("dark"), changed["interface"]!!.jsonObject["theme"])
    }

    @Test
    fun `refuse ce qui n'est pas un document de réglages`() {
        assertNull(AccountSettings.fromDocument(null))
        assertNull(AccountSettings.fromDocument(doc["settings"]!!.jsonObject))
    }
}
