package app.naviamp.desktop.playback.bass

import app.naviamp.domain.playback.CoreBassPlaybackEngine
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackRequest
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.bass.BassAudioBackend
import app.naviamp.domain.bass.BassActiveState
import app.naviamp.domain.bass.BassStreamHandle
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Real BASS/JNI channel lifetime and pause-position acceptance, with muted output. */
class DesktopPreviewPlaybackIntegrationTest {
    @Test fun previewFinishesWhileMainRetainsItsPausedNativeStreamAndPosition() {
        assumeTrue("Real output is required for channel-clock acceptance; no-sound CI is not this check",
            System.getProperty("naviamp.bass.test.outputDevice") != "0")
        val file = Files.createTempFile("naviamp-preview-main-", ".wav")
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val executor = Executors.newCachedThreadPool { work -> Thread(work, "preview-fixture").apply { isDaemon = true } }
        val binding = DesktopBassJniBinding.load().getOrThrow()
        val nativeBackend = DesktopBassAudioBackend(binding)
        val mainSource = MutableStateFlow<BassStreamHandle?>(null)
        val mainPlayback = MutableStateFlow<BassStreamHandle?>(null)
        val backend = object : BassAudioBackend by nativeBackend {
            override fun positionSeconds(stream: BassStreamHandle): Double? =
                nativeBackend.positionSeconds(stream).also { mainSource.compareAndSet(null, stream) }

            override fun play(stream: BassStreamHandle): Result<Unit> =
                nativeBackend.play(stream).onSuccess { mainPlayback.compareAndSet(null, stream) }
        }
        val engine = CoreBassPlaybackEngine(Result.success(backend), DesktopBassPlaybackEngineRuntime())
        try {
            Files.write(file, silentWave(12))
            val preview = silentWave(1)
            server.executor = executor
            server.createContext("/preview.wav") { exchange ->
                exchange.responseHeaders.add("Content-Type", "audio/wav")
                exchange.sendResponseHeaders(200, preview.size.toLong())
                exchange.responseBody.use { it.write(preview) }
                exchange.close()
            }
            server.start()
            runBlocking {
                val main = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
                val sample = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
                val progress = MutableStateFlow(PlaybackProgress.Unknown)
                engine.setVolume(0)
                engine.play(this, PlaybackRequest(file.toUri().toString()), { main.value = it }, { progress.value = it })
                withTimeout(10_000) { progress.first { (it.positionSeconds ?: 0.0) >= 0.2 } }
                engine.pause()
                val sourceHandle = checkNotNull(mainSource.value)
                val playbackHandle = checkNotNull(mainPlayback.value)
                assertEquals(BassActiveState.Paused, nativeBackend.activeState(playbackHandle))
                val nativePausedPosition = checkNotNull(nativeBackend.positionSeconds(sourceHandle))
                // A fixed sleep cannot establish that the shared polling callback has run.
                // Compare only after its paused snapshot agrees with the actual native clock.
                withTimeout(10_000) {
                    progress.first { abs((it.positionSeconds ?: Double.NEGATIVE_INFINITY) - nativePausedPosition) < 0.15 }
                }
                val pausedPosition = checkNotNull(progress.value.positionSeconds)
                assertEquals(PlaybackState.Paused, main.value)
                engine.playPreview(this, PlaybackRequest("http://127.0.0.1:${server.address.port}/preview.wav"), true, 0) { sample.value = it }
                withTimeout(10_000) { sample.first { it == PlaybackState.Finished || it is PlaybackState.Error } }
                assertEquals(PlaybackState.Finished, sample.value)
                assertEquals(PlaybackState.Paused, main.value)
                assertEquals(BassActiveState.Paused, nativeBackend.activeState(playbackHandle))
                val nativeAfterPreview = checkNotNull(nativeBackend.positionSeconds(sourceHandle))
                assertTrue(abs(nativeAfterPreview - nativePausedPosition) < 0.15,
                    "Native main position changed during preview: $nativePausedPosition -> $nativeAfterPreview")
                val afterPreview = checkNotNull(progress.value.positionSeconds)
                assertTrue(abs(afterPreview - pausedPosition) < 0.15,
                    "Main position changed during preview: $pausedPosition -> $afterPreview")
                engine.resume()
                withTimeout(5_000) { progress.first { (it.positionSeconds ?: 0.0) > pausedPosition + 0.2 } }
                println("Independent preview finished; native main $nativePausedPosition -> $nativeAfterPreview; sampled main $pausedPosition -> $afterPreview, resumed to ${progress.value.positionSeconds}")
                engine.stop()
                assertEquals(0, binding.endSyncRegistrationCount, "Main and preview native callbacks must be released")
            }
        } finally {
            engine.release()
            server.stop(0)
            executor.shutdownNow()
            Files.deleteIfExists(file)
        }
    }

    private fun silentWave(seconds: Int): ByteArray {
        val rate = 16_000
        val size = rate * 2 * seconds
        return ByteBuffer.allocate(44 + size).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray()).putInt(36 + size).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2)
            .putShort(2).putShort(16).put("data".toByteArray()).putInt(size).array()
    }
}
