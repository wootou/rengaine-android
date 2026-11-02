package app.rengaine.data

import app.rengaine.data.remote.ApiJson
import app.rengaine.data.remote.dto.QueueShareCreatedDto
import app.rengaine.data.remote.dto.RealtimeChannelDto
import app.rengaine.data.remote.dto.RealtimeMessageDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Messages de la télécommande partagée (FUT-02), au format du contrat 2.28.0. */
class RemoteJsonTest {

    @Test fun commandWithTracks() {
        val m = ApiJson.decodeFromString(
            RealtimeMessageDto.serializer(),
            """{"type":"command","target":"abc123def456","by":"Paul","command":{"type":"add","trackIds":[12],"where":"next","name":"Paul"},
               "tracks":[{"id":12,"title":"Lonely","streamUrl":"/api/v2/tracks/12/stream","durationMs":190000,"unknown":1}]}""",
        )
        assertEquals("command", m.type)
        assertEquals("abc123def456", m.target)
        assertEquals("next", m.command!!.where)
        assertEquals(listOf(12L), m.command!!.trackIds)
        assertEquals("Lonely", m.tracks.single().title)
    }

    @Test fun moveAndVolumeCommands() {
        val move = ApiJson.decodeFromString(RealtimeMessageDto.serializer(), """{"type":"command","target":"k","command":{"type":"move","from":3,"to":1,"trackId":44}}""")
        assertEquals(3, move.command!!.from)
        assertEquals(44L, move.command!!.trackId)
        val volume = ApiJson.decodeFromString(RealtimeMessageDto.serializer(), """{"type":"command","target":"k","command":{"type":"volume","volume":35}}""")
        assertEquals(35, volume.command!!.volume)
        assertNull(volume.by)
    }

    @Test fun sharesAndDisabled() {
        assertEquals("shares", ApiJson.decodeFromString(RealtimeMessageDto.serializer(), """{"type":"shares"}""").type)
        assertNull(ApiJson.decodeFromString(RealtimeMessageDto.serializer(), """{"type":"disabled"}""").command)
    }

    @Test fun channelAndCreatedLink() {
        val ch = ApiJson.decodeFromString(
            RealtimeChannelDto.serializer(),
            """{"hubUrl":"https://music.example.org/.well-known/mercure","topics":["https://music.example.org/users/3/player"],"token":"t","expiresAt":"2026-10-29T03:00:00Z","sharesActive":true}""",
        )
        assertTrue(ch.sharesActive)
        val created = ApiJson.decodeFromString(
            QueueShareCreatedDto.serializer(),
            """{"id":5,"label":"Invité","prefix":"ab12","rights":{"add":true,"edit":false,"control":true},"createdAt":"2026-10-29T01:00:00Z","expiresAt":"2026-10-29T07:00:00Z","lastUsedAt":null,"token":"secret","url":"https://music.example.org/telecommande#secret"}""",
        )
        assertTrue(created.rights.add)
        assertFalse(created.rights.edit)
        assertEquals("https://music.example.org/telecommande#secret", created.url)
    }
}
