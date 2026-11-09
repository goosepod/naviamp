package app.naviamp.domain.playback

import app.naviamp.domain.bass.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class CoreBassOutputStartupRecoveryTest {
    @Test fun mainOutputRecoveryRetainsItsStreamAndPublishesOnlyTheRealClock() = runTest {
        val bass = StartupBackend()
        val engine = CoreBassPlaybackEngine(Result.success(bass), runtime(testScheduler))
        val progress = mutableListOf<PlaybackProgress>()
        engine.play(backgroundScope, PlaybackRequest("file://main"), {}, progress::add)
        runCurrent()
        advanceTimeBy(2_501); runCurrent()
        assertTrue(bass.reinitialized.isEmpty())
        assertEquals(0.0, progress.last().positionSeconds)
        advanceTimeBy(600); runCurrent()
        assertEquals(listOf(1), bass.reinitialized)
        assertEquals(1, bass.created)
        assertTrue(bass.freed.isEmpty())
        advanceTimeBy(1_000); runCurrent()
        assertEquals(0.25, progress.last().positionSeconds)
        advanceTimeBy(20_000); runCurrent()
        assertEquals(listOf(1), bass.reinitialized)
        engine.release()
    }

    @Test fun previewRecoveryPreservesThePausedMainStreamAndItsPosition() = runTest {
        val bass = StartupBackend(stallFrom = 2)
        val engine = CoreBassPlaybackEngine(Result.success(bass), runtime(testScheduler))
        engine.play(backgroundScope, PlaybackRequest("file://main"), {}, {})
        runCurrent()
        engine.pause()
        val pausedPosition = bass.positionSeconds(BassStreamHandle(1))
        engine.playPreview(backgroundScope, PlaybackRequest("https://sample"), true, 40, {})
        runCurrent()
        advanceTimeBy(3_100); runCurrent()
        assertEquals(listOf(2), bass.reinitialized)
        assertEquals(BassActiveState.Paused, bass.states[1])
        assertEquals(pausedPosition, bass.positionSeconds(BassStreamHandle(1)))
        assertTrue(bass.freed.isEmpty())
        engine.release()
    }

    @Test fun previewRecoveryFailureReleasesOnlyPreviewAndReportsTheFailure() = runTest {
        val bass = StartupBackend(stallFrom = 2, failRecovery = true)
        val engine = CoreBassPlaybackEngine(Result.success(bass), runtime(testScheduler))
        engine.play(backgroundScope, PlaybackRequest("file://main"), {}, {})
        runCurrent(); engine.pause()
        val states = mutableListOf<PlaybackState>()
        engine.playPreview(backgroundScope, PlaybackRequest("https://sample"), true, 40, states::add)
        runCurrent(); advanceTimeBy(3_100); runCurrent()
        assertIs<PlaybackState.Error>(states.last())
        assertEquals(listOf(2), bass.reinitialized)
        assertEquals(listOf(2), bass.freed)
        assertEquals(BassActiveState.Paused, bass.states[1])
        engine.release()
    }

    @Test fun canceledStartupCannotReinitializeAReplacementOrReleasedOutput() = runTest {
        val bass = StartupBackend()
        val engine = CoreBassPlaybackEngine(Result.success(bass), runtime(testScheduler))
        engine.play(backgroundScope, PlaybackRequest("file://main"), {}, {})
        runCurrent(); advanceTimeBy(1_100); engine.stop()
        advanceTimeBy(20_000); runCurrent()
        assertTrue(bass.reinitialized.isEmpty())
        engine.playPreview(backgroundScope, PlaybackRequest("https://sample"), true, 40, {})
        runCurrent(); engine.stopPreview()
        advanceTimeBy(20_000); runCurrent()
        assertTrue(bass.reinitialized.isEmpty())
        engine.release()
    }

    @Test fun stoppingDuringTheNativeSnapshotCannotReinitializeTheReleasedChannel() = runTest {
        val bass = StartupBackend()
        val engine = CoreBassPlaybackEngine(Result.success(bass), runtime(testScheduler))
        engine.play(backgroundScope, PlaybackRequest("file://main"), {}, {})
        runCurrent(); advanceTimeBy(2_501)
        bass.onSnapshot = { bass.onSnapshot = null; engine.stop() }
        advanceTimeBy(600); runCurrent()
        assertTrue(bass.reinitialized.isEmpty())
        assertEquals(listOf(1), bass.freed)
        engine.playPreview(backgroundScope, PlaybackRequest("https://sample"), true, 40, {})
        runCurrent(); advanceTimeBy(2_501)
        bass.onSnapshot = { bass.onSnapshot = null; engine.stopPreview() }
        advanceTimeBy(600); runCurrent()
        assertTrue(bass.reinitialized.isEmpty())
        assertEquals(listOf(1, 2), bass.freed)
        engine.release()
    }

    private fun runtime(clock: TestCoroutineScheduler) = object : BassPlaybackEngineRuntime {
        override val workContext = EmptyCoroutineContext
        override fun localFilePath(url: String) = url.removePrefix("file://")
        override fun nowEpochMillis() = clock.currentTime
        override fun <T> withPreparedPlaybackLock(block: () -> T) = block()
    }
}

private class StartupBackend(private val stallFrom: Int = 1, private val failRecovery: Boolean = false) : BassAudioBackend {
    var created = 0
    val states = mutableMapOf<Int, Int>()
    val reinitialized = mutableListOf<Int>()
    val freed = mutableListOf<Int>()
    var onSnapshot: (() -> Unit)? = null
    override fun init() = Result.success(Unit)
    override fun free() = Result.success(Unit)
    override fun setSampleRateConverterQuality(quality: Int) = Result.success(Unit)
    private fun create() = Result.success(BassStreamHandle(++created))
    override fun createFileStream(path: String) = create()
    override fun createUrlStream(url: String) = create()
    override fun createFileDecodeStream(path: String) = create()
    override fun createUrlDecodeStream(url: String) = create()
    override fun play(stream: BassStreamHandle): Result<Unit> { states[stream.value] = BassActiveState.Playing; return Result.success(Unit) }
    override fun pause(stream: BassStreamHandle): Result<Unit> { states[stream.value] = BassActiveState.Paused; return Result.success(Unit) }
    override fun stop(stream: BassStreamHandle): Result<Unit> { states[stream.value] = BassActiveState.Stopped; return Result.success(Unit) }
    override fun activeState(stream: BassStreamHandle) = states[stream.value]
    override fun positionSeconds(stream: BassStreamHandle) = if (stream.value >= 2 && stream.value !in reinitialized) 0.0 else 1.5
    override fun bufferedOutputSeconds(stream: BassStreamHandle): Double { onSnapshot?.invoke(); return 1.5 }
    override fun audiblePositionSeconds(playbackStream: BassStreamHandle, sourceStream: BassStreamHandle) =
        if (playbackStream.value < stallFrom) 1.0 else if (playbackStream.value in reinitialized && !failRecovery) 0.25 else 0.0
    override fun durationSeconds(stream: BassStreamHandle) = 12.0
    override fun setVolume(stream: BassStreamHandle, volume: Float) = Result.success(Unit)
    override fun setEndSync(stream: BassStreamHandle, callback: (BassStreamHandle) -> Unit) = Result.success(1)
    override fun freeStream(stream: BassStreamHandle): Result<Unit> { freed += stream.value; return Result.success(Unit) }
    override fun reinitializeOutput(stream: BassStreamHandle): Result<Unit> {
        reinitialized += stream.value
        return if (failRecovery) Result.failure(IllegalStateException("native init failure")) else Result.success(Unit)
    }
    override fun lengthBytes(stream: BassStreamHandle): Long? = null
    override fun readFloatData(stream: BassStreamHandle, buffer: FloatArray) = Result.success(0)
}
