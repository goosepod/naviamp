package app.naviamp.android

import androidx.test.core.app.ActivityScenario
import app.naviamp.android.playback.AndroidBassAudioBackend
import app.naviamp.android.playback.AndroidBassJni
import app.naviamp.android.playback.AndroidBassPlaybackEngineRuntime
import app.naviamp.domain.playback.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.*

/** Real Android BASS/JNI end callbacks, including native registration cleanup on cancellation. */
class AndroidPreviewPlaybackInstrumentedTest {
    @Test fun previewPausesResumesFinishesAndReleasesCanceledNativeCallbacks() = runBlocking {
        ActivityScenario.launch(MainActivity::class.java).use {
            val binding = AndroidBassJni.load().getOrThrow()
            val engine = CoreBassPlaybackEngine(Result.success(AndroidBassAudioBackend(binding)), AndroidBassPlaybackEngineRuntime())
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            val state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
            val baseline = binding.endSyncRegistrationCount
            suspend fun play() = withContext(Dispatchers.Main) {
                engine.playPreview(scope, PlaybackRequest("memory://preview.wav", randomAccess = waveSource()), true, 0) { state.value = it }
            }
            suspend fun playing() {
                withTimeout(10_000) { state.first { it == PlaybackState.Playing || it is PlaybackState.Error } }
                assertEquals(PlaybackState.Playing, state.value)
            }
            try {
                repeat(3) {
                    play(); playing()
                    withContext(Dispatchers.Main) { engine.stopPreview() }
                    assertEquals(PlaybackState.Stopped, state.value)
                    assertEquals(baseline, binding.endSyncRegistrationCount, "Canceled previews must release native callbacks")
                }
                play(); playing()
                withContext(Dispatchers.Main) { engine.pausePreview() }
                assertEquals(PlaybackState.Paused, state.value)
                delay(200)
                withContext(Dispatchers.Main) { engine.resumePreview() }
                assertEquals(PlaybackState.Playing, state.value)
                withTimeout(10_000) { state.first { it == PlaybackState.Finished || it is PlaybackState.Error } }
                assertEquals(PlaybackState.Finished, state.value)
                assertEquals(baseline, binding.endSyncRegistrationCount, "Completed previews must release native callbacks")
            } finally { withContext(Dispatchers.Main) { engine.release() }; scope.cancel() }
        }
    }

    private fun waveSource(): PlaybackRandomAccessSource {
        val rate = 16_000
        val dataSize = rate * 2
        val bytes = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + dataSize); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1); putShort(1); putInt(rate); putInt(rate * 2)
            putShort(2); putShort(16); put("data".toByteArray()); putInt(dataSize)
            repeat(rate) { putShort((kotlin.math.sin(2 * Math.PI * 440 * it / rate) * 4000).toInt().toShort()) }
        }.array()
        return object : PlaybackRandomAccessSource {
            override val lengthBytes = bytes.size.toLong()
            override suspend fun read(offset: Long, count: Int) = bytes.copyOfRange(offset.toInt(), minOf(bytes.size, offset.toInt() + count))
        }
    }
}
