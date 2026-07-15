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
    fun rejectsIncompleteFloatPcmWaveform() {
        var supplied = false

        val waveform = normalizeFloatPcmWaveform(
            totalSamples = 100,
            bucketCount = 10,
        ) { buffer ->
            if (supplied) return@normalizeFloatPcmWaveform 0
            supplied = true
            floatArrayOf(0.5f, 1.0f).copyInto(buffer)
            2
        }

        assertEquals(null, waveform)
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
    fun usesSequentialPcmInsteadOfSlowLevelWindows() {
        val waveform = analyzeBassFloatPcmWaveform(
            bass = FakeBassAudioBackend(
                chunks = listOf(floatArrayOf(0.25f), floatArrayOf(1.0f)),
                waveformLevels = floatArrayOf(0.25f, 1.0f),
            ),
            stream = BassStreamHandle(7),
            bucketCount = 2,
        )

        assertNotNull(waveform)
        assertEquals(listOf(0.25f, 1.0f), waveform.amplitudes)
    }

    @Test
    fun analyzesPcmWhenBassWaveformLevelsWouldBeTruncated() {
        val waveform = analyzeBassFloatPcmWaveform(
            bass = FakeBassAudioBackend(
                chunks = listOf(FloatArray(20) { 0.5f }),
                waveformLevels = floatArrayOf(0.8f, 1.0f) + FloatArray(18),
            ),
            stream = BassStreamHandle(7),
            bucketCount = 20,
        )

        assertNotNull(waveform)
        assertEquals(20, waveform.amplitudes.size)
        assertEquals(List(20) { 1.0f }, waveform.amplitudes)
    }

    @Test
    fun analyzesPcmWhenBassWaveformLevelsWouldBeSparse() {
        val sparseLevels = FloatArray(100).also { levels ->
            levels[0] = 0.8f
            levels[25] = 1.0f
            levels[50] = 0.7f
            levels[75] = 0.9f
            levels[99] = 0.6f
        }
        val waveform = analyzeBassFloatPcmWaveform(
            bass = FakeBassAudioBackend(
                chunks = listOf(FloatArray(100) { 0.5f }),
                waveformLevels = sparseLevels,
            ),
            stream = BassStreamHandle(7),
            bucketCount = 100,
        )

        assertNotNull(waveform)
        assertEquals(100, waveform.amplitudes.size)
        assertEquals(List(100) { 1.0f }, waveform.amplitudes)
    }

    @Test
    fun suppressesIsolatedLeadingWaveformSpikeAndRestoresDynamicRange() {
        val waveform = cleanWaveformAmplitudes(
            listOf(1.0f, 0.08f, 0.12f) + List(29) { 0.10f },
        )

        assertEquals(2f / 3f, waveform.first())
        assertEquals(2f / 3f, waveform[1])
        assertEquals(1.0f, waveform[2])
    }

    @Test
    fun computesSeekSecondsFromFraction() {
        assertEquals(90.0, seekSecondsForFraction(0.5f, 180.0))
        assertEquals(180.0, seekSecondsForFraction(2f, 180.0))
        assertEquals(null, seekSecondsForFraction(0.5f, null))
    }

    @Test
    fun buildsStableWaveformQualityKeys() {
        assertEquals("original:waveform-v10", StreamQuality.Original.waveformCacheKey())
        assertEquals(
            "transcoded:opus:128:waveform-v10",
            StreamQuality.Transcoded(AudioCodec.Opus, 128).waveformCacheKey(),
        )
    }
}

private class FakeBassAudioBackend(
    private val chunks: List<FloatArray>,
    private val waveformLevels: FloatArray? = null,
) : BassAudioBackend {
    private var chunkIndex = 0

    override fun createFileDecodeStream(path: String): Result<BassStreamHandle> =
        Result.success(BassStreamHandle(1))

    override fun createUrlDecodeStream(url: String): Result<BassStreamHandle> =
        Result.success(BassStreamHandle(1))

    override fun lengthBytes(stream: BassStreamHandle): Long =
        chunks.sumOf { it.size }.toLong() * Float.SIZE_BYTES

    override fun waveformLevels(stream: BassStreamHandle, bucketCount: Int): Result<FloatArray> =
        waveformLevels
            ?.let { Result.success(it) }
            ?: super.waveformLevels(stream, bucketCount)

    override fun readFloatData(stream: BassStreamHandle, buffer: FloatArray): Result<Int> {
        val chunk = chunks.getOrNull(chunkIndex++) ?: return Result.success(0)
        chunk.copyInto(buffer)
        return Result.success(chunk.size)
    }

    override fun freeStream(stream: BassStreamHandle): Result<Unit> =
        Result.success(Unit)
}
