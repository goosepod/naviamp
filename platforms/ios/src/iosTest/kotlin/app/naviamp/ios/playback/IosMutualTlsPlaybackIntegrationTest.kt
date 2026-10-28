@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package app.naviamp.ios.playback

import app.naviamp.domain.bass.BassAudioBackend
import app.naviamp.domain.playback.CoreBassPlaybackEngine
import app.naviamp.ios.bass.native.BASS_Init
import app.naviamp.ios.bass.native.BASS_ErrorGetCode
import app.naviamp.testkit.NaviampMobileMutualTlsHost
import app.naviamp.testkit.verifyNaviampMobileMutualTls
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.runBlocking
import platform.Foundation.*
import kotlin.test.Test

class IosMutualTlsPlaybackIntegrationTest {
    @Test fun realMutualTlsAuthenticatesAndSeeksThroughNormalPlayback() = runBlocking {
        val directory = "${NSTemporaryDirectory().trimEnd('/')}/naviamp-mtls-${NSUUID.UUID().UUIDString}"
        check(NSFileManager.defaultManager.createDirectoryAtPath(directory, true, null, null))
        try {
            verifyNaviampMobileMutualTls(object : NaviampMobileMutualTlsHost {
                override suspend fun fixtureBootstrap(): String? {
                    val text = NSProcessInfo.processInfo.environment["NAVIAMP_MTLS_FIXTURE_BOOTSTRAP"] as? String
                    if (text != null) println("mTLS runner bootstrap supplied")
                    return text
                }

                override suspend fun writePrivateFile(name: String, bytes: ByteArray): String {
                    val path = "$directory/$name"
                    bytes.usePinned {
                        check(NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()).writeToFile(path, true))
                    }
                    val port = app.naviamp.ios.settings.IosCoreCertificateFilePort("$directory/imported") { NSURL.fileURLWithPath(path) }
                    val kind = if (name.endsWith(".p12")) app.naviamp.presentation.NaviampConnectionCertificateKind.ClientIdentity else app.naviamp.presentation.NaviampConnectionCertificateKind.ServerAuthority
                    val imported = checkNotNull(port.chooseAndCopy(kind))
                    check(NSFileManager.defaultManager.removeItemAtPath(path, null))
                    return imported
                }
                override fun createEngine(): CoreBassPlaybackEngine {
                    val native = IosBassAudioBackend()
                    // Exercise the real native decoder and callbacks without an audible output device.
                    val backend = object : BassAudioBackend by native {
                        override fun init(): Result<Unit> = runCatching {
                            check(BASS_Init(0, 48_000u, 0u, null, null) != 0 || BASS_ErrorGetCode() == 14)
                        }
                        override fun init(deviceId: String?): Result<Unit> = init()
                        override fun init(deviceId: String?, sampleRateHz: Int): Result<Unit> = init()
                    }
                    return CoreBassPlaybackEngine(Result.success(backend), IosBassPlaybackEngineRuntime())
                }
            })
        } finally { NSFileManager.defaultManager.removeItemAtPath(directory, null) }
    }
}
