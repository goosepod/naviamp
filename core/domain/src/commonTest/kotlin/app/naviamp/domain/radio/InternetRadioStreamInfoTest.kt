package app.naviamp.domain.radio

import app.naviamp.domain.playback.PlaybackStreamMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InternetRadioStreamInfoTest {
    @Test fun readsActiveIcyBitrateAndMimeTypeCaseInsensitively() {
        val info = PlaybackStreamMetadata(properties = mapOf("Content-Type" to "audio/aac; charset=UTF-8", "ICY-BR" to "320"))
            .internetRadioAudioInfo()
        assertEquals("AAC", info.codec)
        assertEquals(320, info.bitrateKbps)
    }
    @Test fun icecastPropertiesSupplyFallbackBitrateAndSampleRate() {
        val info = PlaybackStreamMetadata(properties = mapOf("content-type" to "audio/mpeg",
            "ice-audio-info" to "ice-samplerate=44100;bitrate=128;samplerate=48000;channels=2"))
            .internetRadioAudioInfo()
        assertEquals("MP3", info.codec)
        assertEquals(128, info.bitrateKbps)
        assertEquals(48000, info.samplingRateHz)
    }
    @Test fun unknownAndMalformedPropertiesDoNotInventStreamQuality() {
        listOf(emptyMap(), mapOf("content-type" to "text/html", "icy-br" to "unknown"),
            mapOf("icy-br" to "-128", "ice-audio-info" to "bitrate=0;samplerate=oops")).forEach {
            val info = PlaybackStreamMetadata(properties = it).internetRadioAudioInfo()
            assertNull(info.codec)
            assertNull(info.bitrateKbps)
            assertNull(info.samplingRateHz)
        }
    }
}
