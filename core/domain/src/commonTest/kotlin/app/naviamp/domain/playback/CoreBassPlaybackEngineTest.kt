package app.naviamp.domain.playback

import app.naviamp.domain.bass.BassAudioBackend
import app.naviamp.domain.bass.BassActiveState
import app.naviamp.domain.bass.BassStreamHandle
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CoreBassPlaybackEngineTest {
    @Test
    fun stopPublishesStoppedStateAndUnknownProgress() = runTest {
        val engine = CoreBassPlaybackEngine(
            backendResult = Result.failure(IllegalStateException("BASS unavailable")),
            runtime = FakeBassPlaybackEngineRuntime,
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
        val engine = CoreBassPlaybackEngine(
            backendResult = Result.success(backend),
            runtime = FakeBassPlaybackEngineRuntime,
        )

        engine.release()
        engine.release()

        assertEquals(1, backend.freeCalls)
    }

    @Test
    fun runsPlaybackLifecycleAgainstANativeFreeBackend() = runTest {
        val backend = RecordingPlaybackBackend()
        val engine = CoreBassPlaybackEngine(
            backendResult = Result.success(backend),
            runtime = FakeBassPlaybackEngineRuntime,
        )
        val states = mutableListOf<PlaybackState>()

        engine.play(
            scope = this,
            request = PlaybackRequest(url = "file:///music/track.flac", mediaId = "track-1"),
            onStateChanged = states::add,
            onProgressChanged = {},
        )
        advanceUntilIdle()

        assertTrue(PlaybackState.Loading in states)
        assertTrue(PlaybackState.Playing in states)
        assertTrue(PlaybackState.Finished in states)
        assertEquals("/music/track.flac", backend.openedPath)
        assertEquals(1, backend.playCalls)
        assertTrue(backend.releasedHandles.contains(BassStreamHandle(41)))
    }
}

private object FakeBassPlaybackEngineRuntime : BassPlaybackEngineRuntime {
    override val workContext: CoroutineContext = EmptyCoroutineContext

    override fun localFilePath(url: String): String? =
        url.removePrefix("file://").takeIf { url.startsWith("file://") }

    override fun nowEpochMillis(): Long = 1_000L

    override fun <T> withPreparedPlaybackLock(block: () -> T): T = block()
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

private class RecordingPlaybackBackend : BassAudioBackend {
    var openedPath: String? = null
    var playCalls: Int = 0
    val releasedHandles = mutableListOf<BassStreamHandle>()
    private var activeStateCalls: Int = 0

    override fun init(): Result<Unit> = Result.success(Unit)

    override fun init(deviceId: String?): Result<Unit> = Result.success(Unit)

    override fun configureInternetStreams(): Result<Unit> = Result.success(Unit)

    override fun setSampleRateConverterQuality(quality: Int): Result<Unit> = Result.success(Unit)

    override fun createFileStream(path: String): Result<BassStreamHandle> {
        openedPath = path
        return Result.success(BassStreamHandle(41))
    }

    override fun createFileDecodeStream(path: String): Result<BassStreamHandle> =
        Result.success(BassStreamHandle(42))

    override fun createUrlDecodeStream(url: String): Result<BassStreamHandle> =
        Result.success(BassStreamHandle(42))

    override fun play(stream: BassStreamHandle): Result<Unit> {
        playCalls += 1
        return Result.success(Unit)
    }

    override fun stop(stream: BassStreamHandle): Result<Unit> = Result.success(Unit)

    override fun activeState(stream: BassStreamHandle): Int =
        if (activeStateCalls++ == 0) BassActiveState.Playing else BassActiveState.Stopped

    override fun setVolume(stream: BassStreamHandle, volume: Float): Result<Unit> = Result.success(Unit)

    override fun positionSeconds(stream: BassStreamHandle): Double = activeStateCalls.toDouble()

    override fun audiblePositionSeconds(
        playbackStream: BassStreamHandle,
        sourceStream: BassStreamHandle,
    ): Double = positionSeconds(sourceStream)

    override fun durationSeconds(stream: BassStreamHandle): Double = 2.0

    override fun lengthBytes(stream: BassStreamHandle): Long = 0L

    override fun readFloatData(stream: BassStreamHandle, buffer: FloatArray): Result<Int> =
        Result.success(0)

    override fun freeStream(stream: BassStreamHandle): Result<Unit> {
        releasedHandles += stream
        return Result.success(Unit)
    }
}
