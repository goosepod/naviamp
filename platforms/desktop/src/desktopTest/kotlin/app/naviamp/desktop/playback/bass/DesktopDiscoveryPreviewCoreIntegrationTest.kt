package app.naviamp.desktop.playback.bass

import app.naviamp.domain.playback.CoreBassPlaybackEngine
import app.naviamp.domain.bass.BassAudioBackend
import app.naviamp.domain.bass.BassActiveState
import app.naviamp.domain.bass.BassStreamHandle
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals

/** A separate class gives the native Core contract its own JVM and process-global BASS state. */
class DesktopDiscoveryPreviewCoreIntegrationTest {
    @Test fun releasePreviewCommandsPreserveTheRealCoreQueueAndSession() {
        assumeTrue("Real output is required for channel-clock acceptance; no-sound CI is not this check",
            System.getProperty("naviamp.bass.test.outputDevice") != "0")
        val main = Files.createTempFile("naviamp-preview-core-", ".wav")
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val executor = Executors.newCachedThreadPool { work -> Thread(work, "preview-core-fixture").apply { isDaemon = true } }
        val binding = DesktopBassJniBinding.load().getOrThrow()
        val native = DesktopBassAudioBackend(binding)
        val mainHandles = MutableStateFlow<Pair<BassStreamHandle, BassStreamHandle>?>(null)
        val observed = object : BassAudioBackend by native {
            override fun audiblePositionSeconds(playbackStream: BassStreamHandle, sourceStream: BassStreamHandle): Double? =
                native.audiblePositionSeconds(playbackStream, sourceStream).also {
                    mainHandles.value = playbackStream to sourceStream
                }
        }
        val engine = CoreBassPlaybackEngine(Result.success(observed), DesktopBassPlaybackEngineRuntime())
        try {
            Files.write(main, app.naviamp.testkit.naviampDiscoveryPreviewFixtureWave(40))
            val short = app.naviamp.testkit.naviampDiscoveryPreviewFixtureWave(1)
            val long = app.naviamp.testkit.naviampDiscoveryPreviewFixtureWave(8)
            server.executor = executor
            server.createContext("/") { exchange ->
                val bytes = if (exchange.requestURI.path == "/short.wav") short else long
                exchange.responseHeaders.add("Content-Type", "audio/wav")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
                exchange.close()
            }
            server.start()
            runBlocking {
                app.naviamp.testkit.verifyNaviampDiscoveryPreviewPlayback(engine, main.toUri().toString(),
                    "http://127.0.0.1:${server.address.port}/short.wav", "http://127.0.0.1:${server.address.port}/long.wav",
                    nativeMainPosition = { mainHandles.value?.let { (playback, source) ->
                        if (native.activeState(playback) == BassActiveState.Playing)
                            native.audiblePositionSeconds(playback, source)
                        else native.positionSeconds(source)
                    } })
            }
            assertEquals(0, binding.endSyncRegistrationCount, "Main and preview native callbacks must be released")
        } finally {
            engine.release()
            server.stop(0)
            executor.shutdownNow()
            Files.deleteIfExists(main)
        }
    }

}
