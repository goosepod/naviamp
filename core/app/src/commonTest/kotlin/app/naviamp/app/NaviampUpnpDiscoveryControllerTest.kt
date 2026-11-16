package app.naviamp.app

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class NaviampUpnpDiscoveryControllerTest {
    @Test fun nativeOperationsUseInjectedContextAndRestartWaitsForCleanup() = runTest {
        val calls = mutableListOf<String>()
        val callbacks = mutableListOf<(NaviampUpnpDiscoveryPacket) -> Unit>()
        val effect = object : NaviampUpnpDiscoveryEffect {
            override fun start(search: String, receive: (NaviampUpnpDiscoveryPacket) -> Unit) { calls += "start"; callbacks += receive }
            override fun refresh(search: String) { calls += "refresh" }
            override fun stop() { calls += "stop" }
        }
        val controller = NaviampUpnpDiscoveryController(backgroundScope, effect,
            NaviampUpnpHttpTransport { NaviampUpnpHttpResponse(200, emptyMap(), upnpFixtureDescription()) }, { 0L },
            StandardTestDispatcher(testScheduler))
        controller.start()
        assertTrue(calls.isEmpty(), "Blocking native startup must not run inline on the UI context")
        controller.stop(); controller.start(); runCurrent()
        assertEquals(listOf("start", "stop", "start"), calls)
        callbacks.first()(packet("192.0.2.10")); runCurrent()
        assertTrue(controller.state.value.targets.isEmpty())
        callbacks.last()(packet("192.0.2.10")); runCurrent()
        assertEquals(1, controller.state.value.targets.size)
        controller.refresh()
        assertEquals("start", calls.last(), "Refresh also uses the injected context")
        runCurrent(); assertEquals("refresh", calls.last())
        controller.stop(); runCurrent(); assertEquals("stop", calls.last())
    }

    @Test fun failedOldStartupCannotPoisonRestartAndCancelledUiStillCleansUp() = runTest {
        var starts = 0; var stops = 0
        val effect = object : NaviampUpnpDiscoveryEffect {
            override fun start(search: String, receive: (NaviampUpnpDiscoveryPacket) -> Unit) { if (++starts == 1) error("old native startup failed") }
            override fun refresh(search: String) = Unit
            override fun stop() { stops++ }
        }
        val owner = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val controller = NaviampUpnpDiscoveryController(owner, effect,
            NaviampUpnpHttpTransport { error("No description requested") }, { 0L }, StandardTestDispatcher(testScheduler))
        controller.start(); controller.stop(); controller.start(); runCurrent()
        assertEquals(2, starts)
        assertFalse(controller.state.value.failed)
        val before = stops
        owner.cancel(); controller.stop(); runCurrent()
        assertEquals(before + 1, stops)
    }

    @Test fun failedDescriptionCanBeRetriedByALaterAnnouncement() = runTest {
        val effect = Effect(); var calls = 0
        val controller = NaviampUpnpDiscoveryController(backgroundScope, effect, NaviampUpnpHttpTransport {
            NaviampUpnpHttpResponse(if (++calls == 1) 503 else 200, emptyMap(), upnpFixtureDescription())
        }, { 0L })
        controller.start(); effect.receive(packet("192.0.2.10")); runCurrent()
        assertTrue(controller.state.value.targets.isEmpty())
        effect.receive(packet("192.0.2.10")); runCurrent()
        assertEquals(1, controller.state.value.targets.size)
        controller.stop()
    }

    @Test fun interfacesExpireIndependentlyAndOldScanCallbacksCannotReturnTargets() = runTest {
        var now = 0L
        val effect = Effect()
        val controller = NaviampUpnpDiscoveryController(backgroundScope, effect,
            NaviampUpnpHttpTransport { NaviampUpnpHttpResponse(200, emptyMap(), upnpFixtureDescription()) }, { now })
        controller.start()
        val old = effect.receive
        effect.receive(packet("192.0.2.10")); effect.receive(packet("192.0.2.11")); runCurrent()
        assertEquals(2, controller.state.value.targets.size)
        effect.receive(packet("192.0.2.10", departed = true)); runCurrent()
        assertEquals("192.0.2.11", controller.state.value.targets.single().localAddress)
        now = 2_000; controller.refreshExpiry()
        assertTrue(controller.state.value.targets.isEmpty())
        controller.stop(); controller.start()
        old(packet("192.0.2.10")); runCurrent()
        assertTrue(controller.state.value.targets.isEmpty())
        controller.stop()
    }

    @Test fun delayedDescriptionsFromAStoppedScanAreDiscardedAndDuplicatesDoNotRefetch() = runTest {
        val effect = Effect(); val gate = CompletableDeferred<Unit>(); var calls = 0
        val controller = NaviampUpnpDiscoveryController(backgroundScope, effect, NaviampUpnpHttpTransport {
            calls++; withContext(NonCancellable) { gate.await() }
            NaviampUpnpHttpResponse(200, emptyMap(), upnpFixtureDescription())
        }, { 0L })
        controller.start(); effect.receive(packet("192.0.2.10")); runCurrent()
        effect.receive(packet("192.0.2.10")); runCurrent()
        assertEquals(1, calls)
        controller.stop(); gate.complete(Unit); runCurrent()
        assertTrue(controller.state.value.targets.isEmpty())
    }

    private fun packet(local: String, departed: Boolean = false) = NaviampUpnpDiscoveryPacket("192.0.2.2", local,
        if (departed) "NOTIFY * HTTP/1.1\r\nNT: $NAVIAMP_UPNP_RENDERER_TYPE\r\nNTS: ssdp:byebye\r\nUSN: uuid:fixture::$NAVIAMP_UPNP_RENDERER_TYPE\r\n\r\n"
        else "HTTP/1.1 200 OK\r\nST: $NAVIAMP_UPNP_RENDERER_TYPE\r\nUSN: uuid:fixture::$NAVIAMP_UPNP_RENDERER_TYPE\r\nLOCATION: http://192.0.2.2/device.xml\r\nCACHE-CONTROL: max-age=2\r\n\r\n")
    private class Effect : NaviampUpnpDiscoveryEffect {
        lateinit var receive: (NaviampUpnpDiscoveryPacket) -> Unit
        override fun start(search: String, receive: (NaviampUpnpDiscoveryPacket) -> Unit) { this.receive = receive }
        override fun refresh(search: String) = Unit
        override fun stop() = Unit
    }
}
