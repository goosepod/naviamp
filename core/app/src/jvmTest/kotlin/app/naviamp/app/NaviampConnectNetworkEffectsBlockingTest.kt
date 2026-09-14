package app.naviamp.app

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NaviampConnectNetworkEffectsBlockingTest {
    @Test
    fun blockedNativeStopLeavesOwnerResponsiveAndCloseDrainsAfterRelease() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val stopping = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val cleaned = CompletableDeferred<Unit>()
        var stops = 0
        val native = object : NaviampConnectAdvertisingEffect {
            override fun start(service: NaviampConnectRegistrationService, listener: NaviampConnectAdvertisingListener): NaviampConnectAdvertisingStartResult {
                started.complete(Unit)
                return NaviampConnectAdvertisingStartResult.Started
            }
            override fun stop() {
                if (++stops == 1) {
                    stopping.complete(Unit)
                    check(release.await(10, TimeUnit.SECONDS))
                } else cleaned.complete(Unit)
            }
        }
        val effects = NaviampConnectNetworkEffects(this, null, native)
        try {
            effects.advertising!!.start(NaviampConnectRegistrationService("target", 42, emptyMap()),
                object : NaviampConnectAdvertisingListener {
                    override fun onServiceRegistered(registeredServiceName: String) = Unit
                    override fun onRegistrationFailed(message: String) = Unit
                })
            withTimeout(5_000) { started.await() }
            effects.advertising!!.stop()
            withTimeout(5_000) { stopping.await() }
            // Owner work and close must finish while the native DNS-SD call remains blocked.
            withTimeout(1_000) { withContext(coroutineContext) { effects.close() } }
            assertFalse(cleaned.isCompleted)
            release.countDown()
            withTimeout(5_000) { effects.awaitClosed() }
            assertTrue(cleaned.isCompleted)
        } finally {
            release.countDown()
            effects.close()
            withContext(Dispatchers.IO) { effects.awaitClosed() }
        }
    }
}
