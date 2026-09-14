package app.naviamp.app

import app.naviamp.domain.connect.NaviampConnectAdvertisement
import app.naviamp.domain.connect.NaviampConnectProtocolRange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NaviampConnectNetworkEffectsTest {
    @Test
    fun startStopAndRestartAreOrderedAndOldCallbacksCannotChangeCurrentState() = runTest {
        val calls = mutableListOf<String>()
        val native = Advertising(calls)
        val workerDispatcher = StandardTestDispatcher(testScheduler)
        val effects = NaviampConnectNetworkEffects(this, null, native, workerDispatcher)
        val controller = NaviampConnectAdvertisingController(effects.advertising!!, { currentTime })
        controller.start(advertisement("first"))
        assertTrue(calls.isEmpty(), "Starting registration must not invoke native work on the caller")
        runCurrent()
        val old = native.listeners.single()
        controller.stop()
        controller.start(advertisement("second"))
        old.onServiceRegistered("obsolete")
        old.onPermissionDenied()
        old.onRegistrationFailed("obsolete failure")
        runCurrent()
        assertEquals(listOf("start:first", "stop", "start:second"), calls)
        assertIs<NaviampConnectAdvertisingStatus.Starting>(controller.state.value)
        native.listeners.last().onServiceRegistered("current")
        runCurrent()
        assertEquals("second", assertIs<NaviampConnectAdvertisingStatus.Advertising>(controller.state.value).advertisement.instanceId)
        effects.close(); runCurrent(); effects.awaitClosed()
    }

    @Test
    fun obsoleteQueuedStartsAreSkippedAndExpiryInvalidatesLateRegistration() = runTest {
        val calls = mutableListOf<String>()
        val native = Advertising(calls)
        val effects = NaviampConnectNetworkEffects(this, null, native, StandardTestDispatcher(testScheduler))
        val controller = NaviampConnectAdvertisingController(effects.advertising!!, { currentTime })
        controller.start(advertisement("obsolete"))
        controller.stop()
        controller.start(advertisement("current"))
        runCurrent()
        assertEquals(listOf("stop", "start:current"), calls)
        advanceTimeBy(1_001)
        assertTrue(controller.refreshExpiry())
        native.listeners.single().onServiceRegistered("expired")
        runCurrent()
        assertEquals(NaviampConnectAdvertisingStatus.Idle, controller.state.value)
        effects.close(); runCurrent(); effects.awaitClosed()
    }

    @Test
    fun discoveryCallbacksStayOnTheirRequestAndBothKindsShareOrdering() = runTest {
        val calls = mutableListOf<String>()
        val listeners = mutableListOf<NaviampConnectDiscoveryListener>()
        val native = object : NaviampConnectDiscoveryEffect {
            override fun start(listener: NaviampConnectDiscoveryListener): NaviampConnectDiscoveryStartResult {
                calls += "browse"; listeners += listener
                return NaviampConnectDiscoveryStartResult.Started
            }
            override fun stop() { calls += "stop-browse" }
        }
        val effects = NaviampConnectNetworkEffects(this, native, Advertising(calls), StandardTestDispatcher(testScheduler))
        val discovery = NaviampConnectDiscoveryController(effects.discovery!!, nowEpochMillis = { currentTime })
        discovery.start(); runCurrent()
        val old = listeners.single()
        discovery.stop()
        effects.advertising!!.start(NaviampConnectRegistrationService("target", 42, emptyMap()), IgnoredAdvertisingListener)
        discovery.start(); runCurrent()
        old.onDiscoveryFailed("old failure"); old.onPermissionDenied(); old.onServiceLost("target")
        runCurrent()
        assertNull(discovery.state.value.problem)
        assertEquals(NaviampConnectDiscoveryStatus.Discovering, discovery.state.value.status)
        listeners.last().onPermissionDenied(); runCurrent()
        assertEquals(NaviampConnectDiscoveryProblem.PermissionDenied, discovery.state.value.problem)
        assertEquals(listOf("browse", "stop-browse", "start:target", "browse", "stop-browse"), calls)
        effects.close(); runCurrent(); effects.awaitClosed()
    }

    @Test
    fun ownerCancellationStillDrainsCleanupAndOneStopFailureDoesNotSkipTheOther() = runTest {
        val ownerJob = Job()
        val owner = CoroutineScope(ownerJob + StandardTestDispatcher(testScheduler))
        val calls = mutableListOf<String>()
        val discovery = object : NaviampConnectDiscoveryEffect {
            override fun start(listener: NaviampConnectDiscoveryListener) = NaviampConnectDiscoveryStartResult.Started
            override fun stop() { calls += "stop-browse"; error("native failure") }
        }
        val effects = NaviampConnectNetworkEffects(owner, discovery, Advertising(calls), StandardTestDispatcher(testScheduler))
        effects.advertising!!.start(NaviampConnectRegistrationService("obsolete", 42, emptyMap()), IgnoredAdvertisingListener)
        owner.cancel()
        runCurrent(); effects.awaitClosed()
        assertEquals(listOf("stop-browse", "stop"), calls)
        effects.close(); runCurrent()
        assertEquals(2, calls.size, "Close must be idempotent")
    }

    @Test
    fun asynchronousDiscoveryStartPreservesUnavailableAndPermissionResults() = runTest {
        for (result in listOf(NaviampConnectDiscoveryStartResult.Unavailable("unavailable"), NaviampConnectDiscoveryStartResult.PermissionDenied)) {
            val native = object : NaviampConnectDiscoveryEffect {
                override fun start(listener: NaviampConnectDiscoveryListener) = result
                override fun stop() = Unit
            }
            val effects = NaviampConnectNetworkEffects(this, native, null, StandardTestDispatcher(testScheduler))
            val discovery = NaviampConnectDiscoveryController(effects.discovery!!, nowEpochMillis = { currentTime })
            discovery.start()
            runCurrent()
            val expected = when (result) {
                NaviampConnectDiscoveryStartResult.PermissionDenied -> NaviampConnectDiscoveryProblem.PermissionDenied
                is NaviampConnectDiscoveryStartResult.Unavailable -> NaviampConnectDiscoveryProblem.Unavailable(result.message)
                else -> error("Unexpected test result")
            }
            assertEquals(expected, discovery.state.value.problem)
            effects.close(); runCurrent(); effects.awaitClosed()
        }
    }

    @Test
    fun nativeStartExceptionsBecomeCurrentFailureAndCleanupRemainsAvailable() = runTest {
        val calls = mutableListOf<String>()
        val native = object : NaviampConnectAdvertisingEffect {
            override fun start(service: NaviampConnectRegistrationService, listener: NaviampConnectAdvertisingListener): NaviampConnectAdvertisingStartResult = error("native failure")
            override fun stop() { calls += "stop" }
        }
        val effects = NaviampConnectNetworkEffects(this, null, native, StandardTestDispatcher(testScheduler))
        val advertising = NaviampConnectAdvertisingController(effects.advertising!!, { currentTime })
        advertising.start(advertisement("target")); runCurrent()
        assertEquals("native failure", assertIs<NaviampConnectAdvertisingStatus.Failed>(advertising.state.value).message)
        assertEquals(listOf("stop"), calls)
        effects.close(); runCurrent(); effects.awaitClosed()
    }

    private class Advertising(private val calls: MutableList<String>) : NaviampConnectAdvertisingEffect {
        val listeners = mutableListOf<NaviampConnectAdvertisingListener>()
        override fun start(service: NaviampConnectRegistrationService, listener: NaviampConnectAdvertisingListener): NaviampConnectAdvertisingStartResult {
            calls += "start:${service.textAttributes["id"] ?: service.serviceName.removePrefix("Naviamp ")}"
            listeners += listener
            return NaviampConnectAdvertisingStartResult.Started
        }
        override fun stop() { calls += "stop" }
    }

    private object IgnoredAdvertisingListener : NaviampConnectAdvertisingListener {
        override fun onServiceRegistered(registeredServiceName: String) = Unit
        override fun onRegistrationFailed(message: String) = Unit
    }
    private fun advertisement(id: String) = NaviampConnectAdvertisement(
        instanceId = id, displayName = id, protocolRange = NaviampConnectProtocolRange(),
        capabilities = emptySet(), port = 42, identityFingerprint = "fingerprint", expiresAtEpochMillis = 1_000)
}
