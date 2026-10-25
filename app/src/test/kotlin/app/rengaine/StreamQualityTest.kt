package app.rengaine

import app.rengaine.data.remote.dto.AudioFormatDto
import app.rengaine.playback.StreamQuality
import app.rengaine.playback.StreamQuality.HIGH
import app.rengaine.playback.StreamQuality.LOW
import app.rengaine.playback.StreamQuality.MEDIUM
import app.rengaine.playback.StreamQuality.ORIGINAL
import org.junit.Assert.assertEquals
import org.junit.Test

/** Qualité du flux (F7) : ne demander une conversion que si le serveur la fera (`Transcoder::plan`). */
class StreamQualityTest {
    private val flac = AudioFormatDto(format = "flac", lossless = true, bitrateKbps = 900)
    private val mp3_320 = AudioFormatDto(format = "mp3", bitrateKbps = 320)
    private val mp3_128 = AudioFormatDto(format = "mp3", bitrateKbps = 128)
    private val opus_160 = AudioFormatDto(format = "opus", bitrateKbps = 160)

    @Test fun `original reste original`() = assertEquals(ORIGINAL, StreamQuality.plan(ORIGINAL, flac))
    @Test fun `sans perte - toujours converti`() = assertEquals(HIGH, StreamQuality.plan(HIGH, flac))
    @Test fun `avec perte vers un débit plus bas - converti`() = assertEquals(MEDIUM, StreamQuality.plan(MEDIUM, mp3_320))
    @Test fun `avec perte vers un débit plus haut - original`() = assertEquals(ORIGINAL, StreamQuality.plan(HIGH, mp3_128))
    @Test fun `débit égal - original`() = assertEquals(ORIGINAL, StreamQuality.plan(MEDIUM, AudioFormatDto(format = "aac", bitrateKbps = 160)))
    @Test fun `même format - original`() = assertEquals(ORIGINAL, StreamQuality.plan(LOW, opus_160))
    @Test fun `source inconnue - on demande`() = assertEquals(LOW, StreamQuality.plan(LOW, null))
    @Test fun `réglage inconnu - original`() = assertEquals(ORIGINAL, StreamQuality.of("ultra"))
}
