package app.naviamp.desktop.playback.bass

import app.naviamp.domain.playback.CoreBassPlaybackEngine
import app.naviamp.presentation.NaviampCoreSettingsValueStore
import app.naviamp.testkit.naviampDiscoveryPreviewFixtureWave
import app.naviamp.testkit.verifyNaviampRecentRadioPlayback
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import kotlin.test.Test

/** Real desktop BASS/JNI and filesystem boundaries for the shared Radio replay contract. */
class DesktopRecentRadioPlaybackTest {
    @Test fun replaysKeepOnePersistedStationAndRealPlaybackAcrossCoreRestart() {
        assumeTrue("A real output device is required for the moving native-clock probe",
            System.getProperty("naviamp.bass.test.outputDevice") != "0")
        val directory = Files.createTempDirectory("naviamp-recent-radio-")
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val engine = CoreBassPlaybackEngine(
            Result.success(DesktopBassAudioBackend(DesktopBassJniBinding.load().getOrThrow())),
            DesktopBassPlaybackEngineRuntime(),
        )
        try {
            val bytes = naviampDiscoveryPreviewFixtureWave(30)
            server.createContext("/radio.wav") { exchange ->
                exchange.responseHeaders.add("Content-Type", "audio/wav")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
                exchange.close()
            }
            server.start()
            val values = object : NaviampCoreSettingsValueStore {
                override fun read(key: String): String? = directory.resolve(key).let { if (Files.exists(it)) Files.readString(it) else null }
                override fun write(key: String, value: String) { Files.writeString(directory.resolve(key), value) }
            }
            runBlocking { verifyNaviampRecentRadioPlayback(engine,
                "http://127.0.0.1:${server.address.port}/radio.wav", values) }
        } finally {
            engine.release()
            server.stop(0)
            Files.list(directory).use { paths -> paths.forEach { Files.deleteIfExists(it) } }
            Files.deleteIfExists(directory)
        }
    }
}
