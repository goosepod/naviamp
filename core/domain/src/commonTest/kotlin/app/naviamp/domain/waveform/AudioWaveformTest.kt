package app.naviamp.domain.waveform

import app.naviamp.domain.AudioCodec
import app.naviamp.domain.StreamQuality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AudioWaveformTest {
    @Test
    fun normalizesPeakBuckets() {
        val waveform = normalizeWaveformPeaks(
            peaks = listOf(0.1f, 0.5f, 1.0f, 0.25f),
            bucketCount = 2,
        )

        assertNotNull(waveform)
        assertEquals(listOf(1.0f, 0.25f), waveform.amplitudes)
    }

    @Test
    fun normalizesChunkedFloatPcmSamples() {
        val chunks = listOf(floatArrayOf(0.5f), floatArrayOf(1.0f))
        var chunkIndex = 0

        val waveform = normalizeFloatPcmWaveform(
            totalSamples = 2,
            bucketCount = 2,
        ) { buffer ->
            val chunk = chunks.getOrNull(chunkIndex++) ?: return@normalizeFloatPcmWaveform 0
            chunk.copyInto(buffer)
            chunk.size
        }

        assertNotNull(waveform)
        assertEquals(listOf(0.5f, 1.0f), waveform.amplitudes)
    }

    @Test
    fun computesSeekSecondsFromFraction() {
        assertEquals(90.0, seekSecondsForFraction(0.5f, 180.0))
        assertEquals(180.0, seekSecondsForFraction(2f, 180.0))
        assertEquals(null, seekSecondsForFraction(0.5f, null))
    }

    @Test
    fun buildsStableWaveformQualityKeys() {
        assertEquals("original:waveform-v2", StreamQuality.Original.waveformCacheKey())
        assertEquals(
            "transcoded:opus:128:waveform-v2",
            StreamQuality.Transcoded(AudioCodec.Opus, 128).waveformCacheKey(),
        )
    }
}
