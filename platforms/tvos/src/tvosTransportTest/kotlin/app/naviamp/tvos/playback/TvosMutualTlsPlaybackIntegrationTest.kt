@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package app.naviamp.tvos.playback

import app.naviamp.testkit.NaviampEventLoopTestRun
import app.naviamp.testkit.NaviampMobileMutualTlsHost
import app.naviamp.testkit.verifyNaviampMobileMutualTls
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.*
import kotlin.test.Test

/** Only Foundation callback delivery/private test-file IO and the real tvOS BASS factory live here. */
class TvosMutualTlsPlaybackIntegrationTest {
    @Test fun realMutualTlsAuthenticatesAndSeeksThroughNormalPlayback() {
        check(NSThread.isMainThread)
        val run = NaviampEventLoopTestRun {
            val directory = "${NSTemporaryDirectory().trimEnd('/')}/naviamp-tvos-mtls-${NSUUID.UUID().UUIDString}"
            check(NSFileManager.defaultManager.createDirectoryAtPath(directory, true, null, null))
            try {
                verifyNaviampMobileMutualTls(object : NaviampMobileMutualTlsHost {
                    override suspend fun fixtureBootstrap(): String? =
                        (NSProcessInfo.processInfo.environment["NAVIAMP_MTLS_FIXTURE_BOOTSTRAP"] as? String)?.also {
                            println("mTLS runner bootstrap supplied")
                        }

                    override suspend fun verifyNativeCallbackDelivery() = withContext(Dispatchers.Main) {
                        check(NSThread.isMainThread)
                    }

                    override fun transportContext(): String = "mainThread=${NSThread.isMainThread}; " +
                        "operationQueuePresent=${NSOperationQueue.currentQueue() != null}"

                    override suspend fun writePrivateFile(name: String, bytes: ByteArray): String {
                        val path = "$directory/$name"
                        bytes.usePinned {
                            check(NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()).writeToFile(path, true))
                        }
                        return path
                    }

                    override fun createEngine() = createTvosBassPlaybackEngine()
                })
            } finally { check(NSFileManager.defaultManager.removeItemAtPath(directory, null)) }
        }
        try {
            while (!run.isCompleted) NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.01))
            run.finish()
        } finally { run.close() }
    }
}
