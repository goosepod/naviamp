package app.naviamp.domain.bass

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
}

private class RecordingBassAudioBackend(
    private val removeSucceeds: Boolean = true,
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
}
