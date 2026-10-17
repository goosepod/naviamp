package app.naviamp.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.net.Uri
import app.naviamp.android.playback.AndroidBassAudioBackend
import app.naviamp.android.playback.AndroidBassJni
import app.naviamp.android.playback.AndroidBassPlaybackEngineRuntime
import app.naviamp.domain.playback.CoreBassPlaybackEngine
import app.naviamp.testkit.NaviampMobileMutualTlsHost
import app.naviamp.testkit.verifyNaviampMobileMutualTls
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidMutualTlsPlaybackInstrumentedTest {
    @Test fun realMutualTlsAuthenticatesAndSeeksThroughNormalPlayback() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "mtls-${UUID.randomUUID()}")
        check(directory.mkdirs())
        try {
            verifyNaviampMobileMutualTls(object : NaviampMobileMutualTlsHost {
                override suspend fun writePrivateFile(name: String, bytes: ByteArray): String {
                    val source = File(directory, name).also { it.writeBytes(bytes) }
                    val port = AndroidCoreCertificateFilePort(context,
                        AndroidCoreUriPicker { _, _ -> Uri.fromFile(source).toString() }, File(directory, "imported"))
                    val kind = if (name.endsWith(".p12")) app.naviamp.presentation.NaviampConnectionCertificateKind.ClientIdentity else app.naviamp.presentation.NaviampConnectionCertificateKind.ServerAuthority
                    val imported = checkNotNull(port.chooseAndCopy(kind))
                    check(source.delete())
                    return imported
                }
                override fun createEngine(): CoreBassPlaybackEngine = CoreBassPlaybackEngine(
                    Result.success(AndroidBassAudioBackend(AndroidBassJni.load().getOrThrow())),
                    AndroidBassPlaybackEngineRuntime(),
                ).also { it.setVolume(0) }
            })
        } finally { directory.deleteRecursively() }
    }
}
