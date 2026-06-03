package app.naviamp.domain.bass

import app.naviamp.domain.playback.planPreparedMixerTransition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BassAudioBackendTest {
    @Test
    fun unsupportedPlaybackOperationsReturnFailures() {
        val backend = RecordingBassAudioBackend()

        val result = backend.play(BassStreamHandle(1))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("BASS play"))
    }

    @Test
    fun exposesStableBassActiveStateValues() {
        assertEquals(0, BassActiveState.Stopped)
        assertEquals(1, BassActiveState.Playing)
        assertEquals(2, BassActiveState.Stalled)
        assertEquals(3, BassActiveState.Paused)
    }

    @Test
    fun formatsBassVersionIntegers() {
        assertEquals("2.4.17.0", bassVersionLabel(0x02041100))
    }

    @Test
    fun formatsBassErrorCodes() {
        assertEquals("no error", bassErrorMessage(0))
        assertEquals("connection timed out", bassErrorMessage(40))
        assertEquals("unknown BASS error", bassErrorMessage(-1))
        assertEquals("BASS error 999", bassErrorMessage(999))
    }

    @Test
    fun formatsBackendFailureMessages() {
        assertEquals(
            "BASS play failed: unknown BASS error",
            RecordingBassAudioBackend(lastErrorCode = null).bassFailureMessage("BASS play failed"),
        )
        assertEquals(
            "BASS play failed: invalid handle",
            RecordingBassAudioBackend(lastErrorCode = 5).bassFailureMessage("BASS play failed"),
        )
    }

    @Test
    fun plansDistinctNonZeroHandlesForRelease() {
        assertEquals(
            listOf(BassStreamHandle(10), BassStreamHandle(20)),
            bassStreamHandlesForRelease(0, 10, 20, 10, 0),
        )
    }

    @Test
    fun releaseRemovesMixerChannelBeforeFreeingStream() {
        val backend = RecordingBassAudioBackend()

        val result = backend.releaseBassStream(BassStreamHandle(42))

        assertTrue(result.isSuccess)
        assertEquals(listOf("remove:42", "free:42"), backend.calls)
    }

    @Test
    fun releaseStillFreesWhenMixerRemovalFails() {
        val backend = RecordingBassAudioBackend(removeSucceeds = false)

        val result = backend.releaseBassStream(BassStreamHandle(42))

        assertTrue(result.isSuccess)
        assertEquals(listOf("remove:42", "free:42"), backend.calls)
    }

    @Test
    fun intReleaseHelperDelegatesToStreamHandleRelease() {
        val backend = RecordingBassAudioBackend()

        val result = backend.releaseBassStream(42)

        assertTrue(result.isSuccess)
        assertEquals(listOf("remove:42", "free:42"), backend.calls)
    }

    @Test
    fun intHandleHelpersDelegateToStreamHandleOperations() {
        val backend = RecordingBassAudioBackend()

        val volumeResult = backend.setVolume(7, 0.5f)
        val activeState = backend.activeState(7)

        assertTrue(volumeResult.isSuccess)
        assertEquals(BassActiveState.Stopped, activeState)
        assertEquals(listOf("volume:7:0.5"), backend.calls)
    }

    @Test
    fun appliesDirectBassPlaybackVolume() {
        val backend = RecordingBassAudioBackend()

        val results = backend.applyBassPlaybackVolume(
            outputStream = 7,
            sourceStream = 7,
            userVolumeFactor = 0.5f,
            replayGainFactor = 0.8f,
        )

        assertTrue(results.all { it.isSuccess })
        assertEquals(listOf("volume:7:0.4"), backend.calls)
    }

    @Test
    fun appliesSeparateOutputAndSourceBassPlaybackVolume() {
        val backend = RecordingBassAudioBackend()

        val results = backend.applyBassPlaybackVolume(
            outputStream = 7,
            sourceStream = 8,
            userVolumeFactor = 0.5f,
            replayGainFactor = 0.8f,
        )

        assertTrue(results.all { it.isSuccess })
        assertEquals(listOf("volume:7:0.5", "volume:8:0.8"), backend.calls)
    }

    @Test
    fun createsBassPlaybackVisualizerFrameFromFft() {
        val backend = RecordingBassAudioBackend()

        val frame = requireNotNull(backend.bassPlaybackVisualizerFrame(
            stream = 7,
            bins = 4,
            timestampMillis = 123L,
        ).getOrThrow())

        assertEquals(123L, frame.timestampMillis)
        assertTrue(frame.bands.isNotEmpty())
        assertEquals(listOf("fft:7:4"), backend.calls)
    }

    @Test
    fun preparedMixerTransitionAppliesEnvelopesWhenBytePositionsAreAvailable() {
        val backend = RecordingBassAudioBackend()

        val result = backend.applyPreparedBassMixerTransition(
            mixer = BassStreamHandle(1),
            nextSource = BassStreamHandle(2),
            currentSource = BassStreamHandle(3),
            currentSourceVolumeFactor = 0.8f,
            transition = planPreparedMixerTransition(crossfadeDurationSeconds = 5, replayGainFactor = 0.7f),
        )

        assertTrue(result.isSuccess)
        assertEquals(emptyList(), result.getOrThrow().fallbackErrors)
        assertEquals(
            listOf(
                "volume:2:0.0",
                "add:1:2",
                "secondsToBytes:2:5.0",
                "envelope:2",
                "positionBytes:3",
                "secondsToBytes:3:5.0",
                "envelope:3",
            ),
            backend.calls,
        )
    }

    @Test
    fun preparedMixerTransitionIntOverloadTreatsZeroCurrentSourceAsAbsent() {
        val backend = RecordingBassAudioBackend()

        val result = backend.applyPreparedBassMixerTransition(
            mixer = 1,
            nextSource = 2,
            currentSource = 0,
            currentSourceVolumeFactor = 0.8f,
            transition = planPreparedMixerTransition(crossfadeDurationSeconds = 5, replayGainFactor = 0.7f),
        )

        assertTrue(result.isSuccess)
        assertEquals(
            listOf(
                "volume:2:0.0",
                "add:1:2",
                "secondsToBytes:2:5.0",
                "envelope:2",
            ),
            backend.calls,
        )
    }

    @Test
    fun preparedMixerTransitionFallsBackToSlidesWhenEnvelopesFail() {
        val backend = RecordingBassAudioBackend(envelopeSucceeds = false)

        val result = backend.applyPreparedBassMixerTransition(
            mixer = BassStreamHandle(1),
            nextSource = BassStreamHandle(2),
            currentSource = BassStreamHandle(3),
            currentSourceVolumeFactor = 0.8f,
            transition = planPreparedMixerTransition(crossfadeDurationSeconds = 5, replayGainFactor = 0.7f),
        )

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().fallbackErrors.size)
        assertEquals(
            listOf(
                "volume:2:0.0",
                "add:1:2",
                "secondsToBytes:2:5.0",
                "envelope:2",
                "volume:2:0.0",
                "slide:2:0.7:5000",
                "positionBytes:3",
                "secondsToBytes:3:5.0",
                "envelope:3",
                "slide:3:0.0:5000",
            ),
            backend.calls,
        )
    }
}

private class RecordingBassAudioBackend(
    private val removeSucceeds: Boolean = true,
    private val envelopeSucceeds: Boolean = true,
    override val lastErrorCode: Int? = null,
) : BassAudioBackend {
    val calls = mutableListOf<String>()

    override fun createFileDecodeStream(path: String): Result<BassStreamHandle> =
        error("Not used")

    override fun createUrlDecodeStream(url: String): Result<BassStreamHandle> =
        error("Not used")

    override fun lengthBytes(stream: BassStreamHandle): Long? =
        error("Not used")

    override fun readFloatData(stream: BassStreamHandle, buffer: FloatArray): Result<Int> =
        error("Not used")

    override fun removeMixerChannel(stream: BassStreamHandle): Result<Unit> {
        calls += "remove:${stream.value}"
        return if (removeSucceeds) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("not attached"))
        }
    }

    override fun freeStream(stream: BassStreamHandle): Result<Unit> {
        calls += "free:${stream.value}"
        return Result.success(Unit)
    }

    override fun addMixerChannel(
        mixer: BassStreamHandle,
        stream: BassStreamHandle,
    ): Result<Unit> {
        calls += "add:${mixer.value}:${stream.value}"
        return Result.success(Unit)
    }

    override fun setVolume(stream: BassStreamHandle, volume: Float): Result<Unit> {
        calls += "volume:${stream.value}:$volume"
        return Result.success(Unit)
    }

    override fun setMixerVolumeEnvelope(
        stream: BassStreamHandle,
        points: List<Pair<Long, Float>>,
    ): Result<Unit> {
        calls += "envelope:${stream.value}"
        return if (envelopeSucceeds) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("envelope failed"))
        }
    }

    override fun slideVolume(
        stream: BassStreamHandle,
        volume: Float,
        durationMillis: Int,
    ): Result<Unit> {
        calls += "slide:${stream.value}:$volume:$durationMillis"
        return Result.success(Unit)
    }

    override fun fft(stream: BassStreamHandle, bins: Int): Result<FloatArray> {
        calls += "fft:${stream.value}:$bins"
        return Result.success(FloatArray(bins) { index -> index / bins.toFloat() })
    }

    override fun positionBytes(stream: BassStreamHandle): Long? {
        calls += "positionBytes:${stream.value}"
        return 200L
    }

    override fun secondsToBytes(stream: BassStreamHandle, seconds: Double): Long? {
        calls += "secondsToBytes:${stream.value}:$seconds"
        return (seconds * 100).toLong()
    }
}
