package app.naviamp.domain.playback

import app.naviamp.domain.bass.BassAudioBackend
import app.naviamp.domain.bass.BassActiveState
import app.naviamp.domain.bass.BassPlaybackBufferPolicy
import app.naviamp.domain.bass.BassStreamHandle
import app.naviamp.domain.settings.SampleRateConverter
import app.naviamp.domain.settings.SampleRateMatching
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        assertEquals(BassPlaybackBufferPolicy(), backend.bufferPolicy)
        assertEquals(1, backend.playCalls)
        assertTrue(backend.releasedHandles.contains(BassStreamHandle(41)))
    }

    @Test
    fun reportsWhenProviderPlaybackSwitchesToDownloadedFallback() = runTest {
        val backend = RecordingPlaybackBackend()
        val engine = CoreBassPlaybackEngine(
            backendResult = Result.success(backend),
            runtime = FakeBassPlaybackEngineRuntime,
        )
        var fallbackNotifications = 0
        engine.setDownloadFallbackListener { fallbackNotifications += 1 }

        engine.play(
            scope = this,
            request = PlaybackRequest(
                url = "https://offline.example/track.flac",
                fallbackUrl = "file:///downloads/track.opus",
                mediaId = "track-1",
            ),
            onStateChanged = {},
            onProgressChanged = {},
        )
        advanceUntilIdle()

        assertEquals(1, fallbackNotifications)
        assertEquals("/downloads/track.opus", backend.openedPath)
    }

    @Test
    fun diagnosticsReportSampleRatePolicyAndActiveOutput() = runTest {
        val backend = RecordingPlaybackBackend()
        val engine = CoreBassPlaybackEngine(
            backendResult = Result.success(backend),
            runtime = FakeBassPlaybackEngineRuntime,
        )
        engine.setSampleRateConverter(SampleRateConverter.Sinc64)
        engine.setSampleRateMatching(SampleRateMatching.Strict)

        engine.play(
            scope = this,
            request = PlaybackRequest(
                url = "file:///music/hi-res.flac",
                mediaId = "track-hi-res",
                samplingRateHz = 96_000,
            ),
            onStateChanged = {},
            onProgressChanged = {},
        )
        advanceUntilIdle()

        val diagnostics = engine.statsRows().toMap()
        assertEquals("Strict", diagnostics["Sample-rate matching"])
        assertEquals("64 point sinc (BASS quality 4)", diagnostics["Sample-rate converter"])
        assertEquals("96 kHz (96000 Hz)", diagnostics["Track source sample rate"])
        assertEquals("96 kHz (96000 Hz)", diagnostics["Requested output sample rate"])
        assertEquals("96 kHz (96000 Hz)", diagnostics["Active output sample rate"])
        assertEquals(96_000, backend.initializedSampleRateHz)
        assertEquals(4, backend.sampleRateConverterQuality)
    }

    @Test
    fun diagnosticsNeverRetainPlaybackRequestValues() = runTest {
        val engine = CoreBassPlaybackEngine(
            backendResult = Result.failure(IllegalStateException("BASS unavailable")),
            runtime = FakeBassPlaybackEngineRuntime,
        )
        val syntheticUsername = "diagnostic-user"
        val syntheticToken = "synthetic-token-value"
        val syntheticSalt = "synthetic-salt-value"

        engine.play(
            scope = this,
            request = PlaybackRequest(
                url = "https://embedded-user:embedded-password@example.test/rest/stream.view" +
                    "?u=$syntheticUsername&t=$syntheticToken&s=$syntheticSalt&id=track-123" +
                    "#synthetic-fragment-secret",
                mediaId = "track-123",
            ),
            onStateChanged = {},
            onProgressChanged = {},
        )

        val lastRequest = engine.statsRows().toMap().getValue("Last request")
        assertEquals(
            "https://<redacted>@example.test/rest/stream.view" +
                "?u=<redacted>&t=<redacted>&s=<redacted>&id=<redacted>#<redacted>",
            lastRequest,
        )
        listOf(
            syntheticUsername,
            syntheticToken,
            syntheticSalt,
            "embedded-user",
            "embedded-password",
            "track-123",
            "synthetic-fragment-secret",
        ).forEach { secret ->
            assertFalse(secret in lastRequest)
        }
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
    var bufferPolicy: BassPlaybackBufferPolicy? = null
    var playCalls: Int = 0
    var initializedSampleRateHz: Int? = null
    var sampleRateConverterQuality: Int? = null
    val releasedHandles = mutableListOf<BassStreamHandle>()
    private var activeStateCalls: Int = 0

    override fun init(): Result<Unit> = Result.success(Unit)

    override fun init(deviceId: String?): Result<Unit> = Result.success(Unit)

    override fun init(deviceId: String?, sampleRateHz: Int): Result<Unit> {
        initializedSampleRateHz = sampleRateHz
        return Result.success(Unit)
    }

    override fun configurePlaybackBuffers(policy: BassPlaybackBufferPolicy): Result<Unit> {
        bufferPolicy = policy
        return Result.success(Unit)
    }

    override fun configureInternetStreams(): Result<Unit> = Result.success(Unit)

    override fun setSampleRateConverterQuality(quality: Int): Result<Unit> {
        sampleRateConverterQuality = quality
        return Result.success(Unit)
    }

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
