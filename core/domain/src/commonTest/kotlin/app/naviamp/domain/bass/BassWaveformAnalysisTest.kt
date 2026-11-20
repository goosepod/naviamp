package app.naviamp.domain.bass

import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BassWaveformAnalysisTest {
    @Test
    fun backendDefaultUsesTheSameStreamForDurationRewindAndRmsReads() {
        val calls = mutableListOf<String>()
        val backend = object : BassAudioBackend {
            override fun durationSeconds(stream: BassStreamHandle): Double {
                calls += "duration:${stream.value}"
                return 2.0
            }
            override fun seek(stream: BassStreamHandle, seconds: Double): Result<Unit> {
                calls += "seek:${stream.value}:$seconds"
                return Result.success(Unit)
            }
            override fun readMonoRmsLevel(stream: BassStreamHandle, seconds: Float): Result<Float> {
                calls += "read:${stream.value}:$seconds"
                return Result.success(0.5f)
            }
            override fun createFileDecodeStream(path: String): Result<BassStreamHandle> = error("Unexpected create")
            override fun createUrlDecodeStream(url: String): Result<BassStreamHandle> = error("Unexpected create")
            override fun lengthBytes(stream: BassStreamHandle): Long? = error("Unexpected length")
            override fun readFloatData(stream: BassStreamHandle, buffer: FloatArray): Result<Int> = error("Unexpected PCM")
            override fun freeStream(stream: BassStreamHandle): Result<Unit> = error("Caller owns stream lifetime")
        }
        assertContentEquals(floatArrayOf(0.5f, 0.5f), backend.waveformLevels(BassStreamHandle(7), 2).getOrThrow())
        assertEquals(listOf("duration:7", "seek:7:0.0", "read:7:1.0", "read:7:1.0"), calls)
    }

    @Test
    fun combinesRmsEnergyWithWindowDurationRatherThanAveragingAmplitudes() {
        val windows = mutableListOf<Float>()
        val samples = listOf(1f, 0f, 0.5f, 1f).iterator()
        var rewound = false
        val result = analyzeBassWaveformLevels(3.0, 2, {
            rewound = true
            Result.success(Unit)
        }) { seconds ->
            assertTrue(rewound)
            windows += seconds
            Result.success(samples.next())
        }.getOrThrow()

        assertEquals(listOf(1f, 0.5f, 1f, 0.5f), windows)
        assertEquals(sqrt(1.0 / 1.5).toFloat(), result[0], 0.00001f)
        assertEquals(sqrt(0.75 / 1.5).toFloat(), result[1], 0.00001f)
    }

    @Test
    fun clampsNativeLevelsToTheDisplayRange() {
        val samples = listOf(-2f, 3f).iterator()
        assertContentEquals(floatArrayOf(0f, 1f), analyzeBassWaveformLevels(
            2.0, 2, { Result.success(Unit) }, { Result.success(samples.next()) },
        ).getOrThrow())
    }

    @Test
    fun normalizesBucketCountAndHandlesEmptyAudioWithoutNativeEffects() {
        for ((requested, expected) in listOf(-1 to 1, 0 to 1, 2 to 2, Int.MAX_VALUE to 4_096)) {
            val result = analyzeBassWaveformLevels(0.0, requested,
                { error("No decoder rewind needed") }, { error("No samples needed") }).getOrThrow()
            assertEquals(expected, result.size)
            assertTrue(result.all { it == 0f })
        }
    }

    @Test
    fun rejectsInvalidDurationsBeforeInvokingNativeEffects() {
        for (duration in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> {
                analyzeBassWaveformLevels(duration, 1,
                    { error("Unexpected rewind") }, { error("Unexpected read") }).getOrThrow()
            }
        }
    }

    @Test
    fun stopsIfTheNativeRewindFails() {
        val failure = IllegalStateException("native seek failed")
        assertSame(failure, analyzeBassWaveformLevels(1.0, 1,
            { Result.failure(failure) }, { error("Must not consume after failed seek") }).exceptionOrNull())
    }

    @Test
    fun stopsOnTheFirstNativeReadFailureWithoutReturningPartialBuckets() {
        val failure = IllegalStateException("native decoder failed")
        var reads = 0
        val result = analyzeBassWaveformLevels(3.0, 3, { Result.success(Unit) }) {
            reads++
            if (reads == 2) Result.failure(failure) else Result.success(0.5f)
        }
        assertSame(failure, result.exceptionOrNull())
        assertEquals(2, reads)
    }

    @Test
    fun rejectsNonFiniteNativeLevels() {
        for (level in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertFailsWith<IllegalStateException> {
                analyzeBassWaveformLevels(1.0, 1,
                    { Result.success(Unit) }, { Result.success(level) }).getOrThrow()
            }
        }
    }

    @Test
    fun boundsNativeReadWindowsForLongBuckets() {
        val windows = mutableListOf<Float>()
        val result = analyzeBassWaveformLevels(5.25, 1, { Result.success(Unit) }) {
            windows += it
            Result.success(0.5f)
        }.getOrThrow()
        assertEquals(listOf(1f, 1f, 1f, 1f, 1f, 0.25f), windows)
        assertContentEquals(floatArrayOf(0.5f), result)
    }

    @Test
    fun ignoresSubResolutionTailsWithoutAnUnboundedNativeRead() {
        var reads = 0
        val result = analyzeBassWaveformLevels(0.00001, 1, { Result.success(Unit) }) {
            reads++
            Result.success(1f)
        }.getOrThrow()
        assertContentEquals(floatArrayOf(0f), result)
        assertEquals(0, reads)
    }

    @Test
    fun rejectsDurationThatCannotAdvanceInsteadOfLoopingForever() {
        assertFailsWith<IllegalArgumentException> {
            analyzeBassWaveformLevels(Double.MAX_VALUE, 1,
                { Result.success(Unit) }, { error("Cannot advance the window") }).getOrThrow()
        }
    }
}
