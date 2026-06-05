package app.naviamp.domain.waveform

import app.naviamp.domain.AudioCodec
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.bass.BassAudioBackend
import app.naviamp.domain.bass.BassStreamHandle
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
    fun analyzesFloatPcmThroughBassBackendPort() {
        val waveform = analyzeBassFloatPcmWaveform(
            bass = FakeBassAudioBackend(listOf(floatArrayOf(0.5f), floatArrayOf(1.0f))),
            stream = BassStreamHandle(7),
            bucketCount = 2,
        )

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

private class FakeBassAudioBackend(
    private val chunks: List<FloatArray>,
) : BassAudioBackend {
    private var chunkIndex = 0

    override fun createFileDecodeStream(path: String): Result<BassStreamHandle> =
        Result.success(BassStreamHandle(1))

    override fun createUrlDecodeStream(url: String): Result<BassStreamHandle> =
        Result.success(BassStreamHandle(1))

    override fun lengthBytes(stream: BassStreamHandle): Long =
        chunks.sumOf { it.size }.toLong() * Float.SIZE_BYTES

    override fun readFloatData(stream: BassStreamHandle, buffer: FloatArray): Result<Int> {
        val chunk = chunks.getOrNull(chunkIndex++) ?: return Result.success(0)
        chunk.copyInto(buffer)
        return Result.success(chunk.size)
    }

    override fun freeStream(stream: BassStreamHandle): Result<Unit> =
        Result.success(Unit)
}
