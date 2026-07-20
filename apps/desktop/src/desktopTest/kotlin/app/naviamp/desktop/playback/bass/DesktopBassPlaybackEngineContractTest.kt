package app.naviamp.desktop.playback.bass

import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackRequest
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.bass.BassAudioBackend
import app.naviamp.domain.bass.BassStreamHandle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopBassPlaybackEngineContractTest {
    @Test
    fun stopPublishesStoppedStateAndUnknownProgress() = runTest {
        val engine = DesktopBassPlaybackEngine(
            backendResult = Result.failure(IllegalStateException("BASS unavailable")),
        )
        val states = mutableListOf<PlaybackState>()
        val progress = mutableListOf<PlaybackProgress>()
        engine.play(
            scope = this,
            request = PlaybackRequest(url = "file:///track.flac", mediaId = "track-1"),
            onStateChanged = states::add,
            onProgressChanged = progress::add,
        )
        states.clear()
        progress.clear()

        engine.stop()

        assertEquals(listOf<PlaybackState>(PlaybackState.Stopped), states)
        assertEquals(listOf(PlaybackProgress.Unknown), progress)
    }

    @Test
    fun releaseFreesTheNativeBackendOnlyOnce() {
        val backend = RecordingReleaseBackend()
        val engine = DesktopBassPlaybackEngine(Result.success(backend))

        engine.release()
        engine.release()

        assertEquals(1, backend.freeCalls)
    }
}

private class RecordingReleaseBackend : BassAudioBackend {
    var freeCalls = 0

    override fun free(): Result<Unit> {
        freeCalls += 1
        return Result.success(Unit)
    }

    override fun createFileDecodeStream(path: String): Result<BassStreamHandle> = error("Not used")
    override fun createUrlDecodeStream(url: String): Result<BassStreamHandle> = error("Not used")
    override fun lengthBytes(stream: BassStreamHandle): Long? = error("Not used")
    override fun readFloatData(stream: BassStreamHandle, buffer: FloatArray): Result<Int> = error("Not used")
    override fun freeStream(stream: BassStreamHandle): Result<Unit> = error("Not used")
}
