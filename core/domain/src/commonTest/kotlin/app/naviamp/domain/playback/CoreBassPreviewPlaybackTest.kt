package app.naviamp.domain.playback

import app.naviamp.domain.bass.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CoreBassPreviewPlaybackTest {
    @Test fun independentPreviewNeverFreesOrReplacesThePausedMainChannel() = runTest {
        val bass = PreviewBackend()
        val engine = CoreBassPlaybackEngine(Result.success(bass), PreviewRuntime)
        val main = mutableListOf<PlaybackState>()
        val preview = mutableListOf<PlaybackState>()
        engine.play(backgroundScope, PlaybackRequest("file://owned"), main::add, {})
        runCurrent()
        engine.pause()
        val original = main.toList()
        engine.playPreview(backgroundScope, PlaybackRequest("https://sample.test/42.mp3"), true, 40, preview::add)
        runCurrent()
        assertEquals(PlaybackState.Playing, preview.last())
        assertEquals(BassActiveState.Paused, bass.states[1])
        assertEquals(BassActiveState.Playing, bass.states[2])
        assertEquals(0.4f, bass.volumes[2])
        engine.pausePreview()
        assertEquals(PlaybackState.Paused, preview.last())
        engine.resumePreview()
        bass.ends.getValue(2)(BassStreamHandle(2)); runCurrent()
        assertEquals(PlaybackState.Finished, preview.last())
        assertEquals(original, main)
        assertEquals(listOf(2), bass.freed)
        assertEquals(0, bass.deviceFrees)
        engine.playPreview(backgroundScope, PlaybackRequest("https://sample.test/43.mp3"), true, 40, preview::add)
        runCurrent()
        engine.resume()
        assertEquals(BassActiveState.Playing, bass.states[1])
        assertTrue(3 in bass.freed)
        engine.release()
        assertEquals(1, bass.deviceFrees)
    }

    @Test fun replacingStoppingAndReleasingPreviewsIgnoreOldEndCallbacks() = runTest {
        val bass = PreviewBackend()
        val engine = CoreBassPlaybackEngine(Result.success(bass), PreviewRuntime)
        val first = mutableListOf<PlaybackState>()
        val second = mutableListOf<PlaybackState>()
        engine.playPreview(backgroundScope, PlaybackRequest("https://sample.test/1.mp3"), true, 50, first::add)
        runCurrent()
        val old = bass.ends.getValue(1)
        engine.playPreview(backgroundScope, PlaybackRequest("https://sample.test/2.mp3"), true, 50, second::add)
        runCurrent()
        old(BassStreamHandle(1)); runCurrent()
        assertEquals(PlaybackState.Playing, second.last())
        engine.release()
        assertEquals(PlaybackState.Stopped, second.last())
        bass.ends.getValue(2)(BassStreamHandle(2)); runCurrent()
        assertEquals(PlaybackState.Stopped, second.last())
        assertEquals(listOf(1, 2), bass.freed)
        assertEquals(1, bass.deviceFrees)
    }

    @Test fun focusInterruptionAndDuckingApplyToThePreviewInsteadOfMainAudio() = runTest {
        val bass = PreviewBackend()
        val focus = PreviewFocus()
        val wake = PreviewWakeLock()
        val engine = FocusedBassPlaybackEngine(bass, PreviewRuntime, focus, wake)
        val states = mutableListOf<PlaybackState>()
        engine.play(backgroundScope, PlaybackRequest("file://owned"), {}, {})
        runCurrent()
        engine.pause()
        engine.playPreview(backgroundScope, PlaybackRequest("https://sample.test/1.mp3"), true, 80, states::add)
        runCurrent()
        testScheduler.advanceTimeBy(1_100)
        runCurrent()
        assertTrue(wake.isHeld)
        assertEquals(BassActiveState.Paused, bass.states[1])
        focus.callback!!(PlaybackFocusChange.TransientLoss)
        assertEquals(PlaybackState.Paused, states.last())
        focus.callback!!(PlaybackFocusChange.Gain)
        assertEquals(PlaybackState.Playing, states.last())
        focus.callback!!(PlaybackFocusChange.Duck)
        assertEquals(0.2f, bass.volumes[2])
        engine.stopPreview()
        assertFalse(wake.isHeld)
        assertEquals(0, bass.deviceFrees)
        engine.release()
    }
}

private object PreviewRuntime : BassPlaybackEngineRuntime {
    override val workContext = EmptyCoroutineContext
    override fun localFilePath(url: String) = url.removePrefix("file://").takeIf { url.startsWith("file://") }
    override fun nowEpochMillis() = 1_000L
    override fun <T> withPreparedPlaybackLock(block: () -> T) = block()
}

private class PreviewBackend : BassAudioBackend {
    private var next = 0
    val states = mutableMapOf<Int, Int>()
    val ends = mutableMapOf<Int, (BassStreamHandle) -> Unit>()
    val volumes = mutableMapOf<Int, Float>()
    val freed = mutableListOf<Int>()
    var deviceFrees = 0
    override fun init() = Result.success(Unit)
    override fun free(): Result<Unit> { deviceFrees++; return Result.success(Unit) }
    override fun setSampleRateConverterQuality(quality: Int) = Result.success(Unit)
    private fun create() = Result.success(BassStreamHandle(++next))
    override fun createFileStream(path: String) = create()
    override fun createUrlStream(url: String) = create()
    override fun createFileDecodeStream(path: String) = create()
    override fun createUrlDecodeStream(url: String) = create()
    override fun lengthBytes(stream: BassStreamHandle): Long? = null
    override fun readFloatData(stream: BassStreamHandle, buffer: FloatArray) = Result.success(0)
    override fun activeState(stream: BassStreamHandle) = states[stream.value]
    override fun play(stream: BassStreamHandle): Result<Unit> { states[stream.value] = BassActiveState.Playing; return Result.success(Unit) }
    override fun pause(stream: BassStreamHandle): Result<Unit> { states[stream.value] = BassActiveState.Paused; return Result.success(Unit) }
    override fun stop(stream: BassStreamHandle): Result<Unit> { states[stream.value] = BassActiveState.Stopped; return Result.success(Unit) }
    override fun setEndSync(stream: BassStreamHandle, callback: (BassStreamHandle) -> Unit): Result<Int> {
        ends[stream.value] = callback; return Result.success(1)
    }
    override fun setVolume(stream: BassStreamHandle, volume: Float): Result<Unit> { volumes[stream.value] = volume; return Result.success(Unit) }
    override fun freeStream(stream: BassStreamHandle): Result<Unit> { freed += stream.value; return Result.success(Unit) }
}

private class PreviewFocus : PlaybackFocusEffect {
    var callback: ((PlaybackFocusChange) -> Unit)? = null
    override fun request(onChange: (PlaybackFocusChange) -> Unit): Boolean { callback = onChange; return true }
    override fun abandon() {}
}
private class PreviewWakeLock : PlaybackWakeLockEffect {
    override var isHeld = false
    override fun nowMillis() = 0L
    override fun acquire(timeoutMillis: Long) { isHeld = true }
    override fun release() { isHeld = false }
}
