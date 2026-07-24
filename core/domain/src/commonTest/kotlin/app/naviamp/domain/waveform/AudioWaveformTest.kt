package app.naviamp.domain.waveform

import app.naviamp.domain.AudioCodec
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.bass.BassAudioBackend
import app.naviamp.domain.bass.BassStreamHandle
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
    fun normalizesChunkedFloatPcmSamples() = runTest {
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
    fun rejectsIncompleteFloatPcmWaveform() = runTest {
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
    fun analyzesFloatPcmThroughBassBackendPort() = runTest {
        val waveform = analyzeBassFloatPcmWaveform(
            bass = FakeBassAudioBackend(listOf(floatArrayOf(0.5f), floatArrayOf(1.0f))),
            stream = BassStreamHandle(7),
            bucketCount = 2,
        )

        assertNotNull(waveform)
        assertEquals(listOf(0.5f, 1.0f), waveform.amplitudes)
    }

    @Test
    fun usesSequentialPcmInsteadOfSlowLevelWindows() = runTest {
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
    fun analyzesPcmWhenBassWaveformLevelsWouldBeTruncated() = runTest {
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
    fun analyzesPcmWhenBassWaveformLevelsWouldBeSparse() = runTest {
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
    fun cancellableBassAnalysisStopsReadingAfterItsJobIsCancelled() = runTest {
        lateinit var analysisJob: Job
        val backend = FakeBassAudioBackend(
            chunks = List(4) { FloatArray(16_384) { 0.5f } },
            onRead = { readCount ->
                if (readCount == 1) analysisJob.cancel()
            },
        )
        analysisJob = launch(start = CoroutineStart.LAZY) {
            analyzeBassFloatPcmWaveform(
                bass = backend,
                stream = BassStreamHandle(7),
                bucketCount = 100,
            )
        }

        analysisJob.start()
        analysisJob.join()

        assertTrue(analysisJob.isCancelled)
        assertEquals(1, backend.readCount)
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
    private val onRead: (Int) -> Unit = {},
) : BassAudioBackend {
    private var chunkIndex = 0
    var readCount: Int = 0
        private set

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
        readCount += 1
        onRead(readCount)
        chunk.copyInto(buffer)
        return Result.success(chunk.size)
    }

    override fun freeStream(stream: BassStreamHandle): Result<Unit> =
        Result.success(Unit)
}
