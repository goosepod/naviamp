package app.naviamp.desktop

import app.naviamp.desktop.playback.bass.DesktopBassAudioBackend
import app.naviamp.desktop.playback.bass.DesktopBassJniBinding
import app.naviamp.desktop.playback.bass.DesktopBassPlaybackEngineRuntime
import app.naviamp.domain.playback.CoreBassPlaybackEngine
import app.naviamp.testkit.NaviampMobileMutualTlsHost
import app.naviamp.testkit.verifyNaviampMobileMutualTls
import app.naviamp.testkit.withNaviampMobileMutualTlsFixture
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class DesktopSharedMutualTlsContractTest {
    @Test fun realTlsContractAuthenticatesAndSeeksThroughTheSharedPlaybackPath() {
        val directory = Files.createTempDirectory("naviamp-mobile-contract-")
        try {
            withNaviampMobileMutualTlsFixture { _ -> runBlocking {
                verifyNaviampMobileMutualTls(object : NaviampMobileMutualTlsHost {
                    override suspend fun writePrivateFile(name: String, bytes: ByteArray): String {
                        val source = directory.resolve(name).also { Files.write(it, bytes) }
                        val port = app.naviamp.desktop.settings.DesktopCoreCertificateFilePort(directory.resolve("imported")) { source.toFile() }
                        val kind = if (name.endsWith(".p12")) app.naviamp.presentation.NaviampConnectionCertificateKind.ClientIdentity else app.naviamp.presentation.NaviampConnectionCertificateKind.ServerAuthority
                        val imported = checkNotNull(port.chooseAndCopy(kind))
                        Files.delete(source)
                        return imported
                    }
                    override fun createEngine(): CoreBassPlaybackEngine {
                        val backend = DesktopBassAudioBackend(DesktopBassJniBinding.load().getOrThrow())
                        val output = System.getProperty("naviamp.bass.test.outputDevice")
                        val engine = CoreBassPlaybackEngine(Result.success(backend), DesktopBassPlaybackEngineRuntime())
                        if (output != null) {
                            backend.init(output).getOrThrow()
                            engine.setAudioOutputDevice(output).getOrThrow()
                        }
                        return engine
                    }
                })
            } }
        } finally { directory.toFile().deleteRecursively() }
    }
}
