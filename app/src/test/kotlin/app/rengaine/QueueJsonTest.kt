package app.rengaine

import app.rengaine.data.remote.ApiJson
import app.rengaine.data.remote.dto.PlayQueueDto
import app.rengaine.data.remote.dto.PlayQueueInputDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** File d'attente (F6) : le corps de `PUT /me/queue` respecte le contrat. */
class QueueJsonTest {

    @Test
    fun `currentIndex nul est envoye explicitement`() {
        val json = PlayQueueInputDto(emptyList(), null, 0, "normal").toJson().toString()
        assertEquals("""{"trackIds":[],"currentIndex":null,"positionMs":0,"mode":"normal"}""", json)
    }

    @Test
    fun `file complete`() {
        val json = PlayQueueInputDto(listOf(3, 1, 2), 1, 61_500, "repeat_all").toJson().toString()
        assertEquals("""{"trackIds":[3,1,2],"currentIndex":1,"positionMs":61500,"mode":"repeat_all"}""", json)
    }

    @Test
    fun `mode inconnu garde tel quel`() {
        val queue = ApiJson.decodeFromString(
            PlayQueueDto.serializer(),
            """{"trackIds":[5],"currentIndex":null,"positionMs":0,"mode":"futur","version":4,"updatedAt":"2026-10-24T10:00:00Z","updatedBy":null,"tracks":[]}""",
        )
        assertEquals("futur", queue.mode)
        assertNull(queue.currentIndex)
        assertEquals(4L, queue.version)
    }
}
