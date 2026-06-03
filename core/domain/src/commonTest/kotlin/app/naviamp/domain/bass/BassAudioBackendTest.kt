package app.naviamp.domain.bass

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BassAudioBackendTest {
    @Test
    fun unsupportedPlaybackOperationsReturnFailures() {
        val backend = MinimalBassAudioBackend()

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
}

private class MinimalBassAudioBackend : BassAudioBackend {
    override fun createFileDecodeStream(path: String): Result<BassStreamHandle> =
        Result.success(BassStreamHandle(1))

    override fun createUrlDecodeStream(url: String): Result<BassStreamHandle> =
        Result.success(BassStreamHandle(1))

    override fun lengthBytes(stream: BassStreamHandle): Long? = null

    override fun readFloatData(stream: BassStreamHandle, buffer: FloatArray): Result<Int> =
        Result.success(0)

    override fun freeStream(stream: BassStreamHandle): Result<Unit> =
        Result.success(Unit)
}
