package app.naviamp.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NaviampCastDiscoveryControllerTest {
    @Test
    fun preservesTheDiscoveryInterfaceForEachReceiverEndpoint() {
        val fixture = Fixture()
        fixture.controller.start()
        fixture.effect.listener.onServiceResolved(service("wifi", "tv", "TV").copy(localAddress = "192.0.2.10"))
        fixture.effect.listener.onServiceResolved(service("other", "tv", "TV").copy(localAddress = "198.51.100.10"))
        assertEquals(setOf("192.0.2.10", "198.51.100.10"),
            fixture.controller.target("tv")!!.endpoints.map { it.localAddress }.toSet())
        fixture.effect.listener.onServiceLost("other")
        assertEquals("192.0.2.10", fixture.controller.target("tv")!!.endpoints.single().localAddress)
    }

    @Test
    fun mergesInterfacesByReceiverIdAndKeepsOtherInterfaceOnLoss() {
        val fixture = Fixture()
        fixture.controller.start()
        fixture.effect.listener.onServiceResolved(service("wifi", "tv", "Living Room"))
        fixture.effect.listener.onServiceResolved(service("ethernet", "tv", "Living Room TV", "192.0.2.2"))
        fixture.effect.listener.onServiceResolved(service("speaker", "speaker", "Bedroom"))
        assertEquals(listOf("Bedroom", "Living Room TV"), fixture.names())
        assertEquals(listOf("192.0.2.2", "192.0.2.1"), fixture.controller.target("tv")!!.endpoints.map { it.host })

        fixture.effect.listener.onServiceLost("ethernet")
        assertEquals(listOf("Bedroom", "Living Room"), fixture.names())
        assertEquals(listOf(NaviampCastEndpoint("192.0.2.1", 8009)), fixture.controller.target("tv")!!.endpoints)
        fixture.effect.listener.onServiceLost("wifi")
        assertNull(fixture.controller.target("tv"))
    }

    @Test
    fun validatesMetadataAndUpdatesAServiceThatChangesIdentity() {
        val fixture = Fixture()
        fixture.controller.start()
        val valid = service("wifi", "tv", "TV")
        listOf(
            valid.copy(serviceKey = " "), valid.copy(port = 0), valid.copy(port = 65_536),
            valid.copy(addresses = listOf(" ")), valid.copy(textAttributes = mapOf("id" to "tv")),
            valid.copy(textAttributes = mapOf("id" to " ", "fn" to "TV")),
        ).forEach(fixture.effect.listener::onServiceResolved)
        assertTrue(fixture.controller.state.value.targets.isEmpty())
        fixture.effect.listener.onServiceResolved(valid.copy(addresses = listOf("192.0.2.1", "192.0.2.1")))
        assertEquals(1, fixture.controller.target("tv")!!.endpoints.size)
        fixture.effect.listener.onServiceResolved(service("wifi", "new-tv", "New TV"))
        assertNull(fixture.controller.target("tv"))
        assertEquals("New TV", fixture.controller.target("new-tv")!!.target.displayName)
    }

    @Test
    fun refreshExtendsLifetimeAndSelectionRechecksExpiry() {
        val fixture = Fixture()
        fixture.controller.start()
        fixture.effect.listener.onServiceResolved(service("wifi", "tv", "TV"))
        fixture.now = 1_900
        fixture.effect.listener.onServiceResolved(service("wifi", "tv", "TV"))
        fixture.now = 2_000
        fixture.controller.refreshExpiry()
        assertEquals(listOf("TV"), fixture.names())
        fixture.now = 2_900
        assertNull(fixture.controller.target("tv"))
        assertTrue(fixture.controller.state.value.targets.isEmpty())
    }

    @Test
    fun ignoresAllCallbacksFromStoppedOrPreviousDiscovery() {
        val fixture = Fixture()
        fixture.controller.start()
        val old = fixture.effect.listener
        fixture.controller.stop()
        old.onServiceResolved(service("wifi", "tv", "Old TV"))
        assertEquals(NaviampCastDiscoveryState(), fixture.controller.state.value)
        fixture.controller.start()
        fixture.effect.listener.onServiceResolved(service("wifi", "tv", "New TV"))
        old.onServiceResolved(service("wifi", "tv", "Old TV"))
        old.onServiceLost("wifi")
        old.onDiscoveryFailed(NaviampCastDiscoveryProblem.PermissionDenied)
        assertEquals(listOf("New TV"), fixture.names())
        assertTrue(fixture.controller.state.value.discovering)
    }

    @Test
    fun failureCleansUpAndCanBeRetriedIncludingSynchronousFailure() {
        val fixture = Fixture()
        fixture.effect.failOnStart = true
        fixture.controller.start()
        assertEquals(NaviampCastDiscoveryProblem.Failed, fixture.controller.state.value.problem)
        assertEquals(1, fixture.effect.stops)
        fixture.effect.failOnStart = false
        fixture.controller.start()
        fixture.effect.listener.onServiceResolved(service("wifi", "tv", "TV"))
        fixture.effect.listener.onDiscoveryFailed(NaviampCastDiscoveryProblem.Unavailable)
        assertEquals(NaviampCastDiscoveryState(problem = NaviampCastDiscoveryProblem.Unavailable),
            fixture.controller.state.value)
        assertEquals(2, fixture.effect.stops)
        fixture.controller.start()
        assertEquals(NaviampCastDiscoveryState(discovering = true), fixture.controller.state.value)
    }

    @Test
    fun startAndStopAreIdempotentAndCleanupCannotRepublishResults() {
        val fixture = Fixture()
        fixture.controller.start()
        fixture.controller.start()
        assertEquals(1, fixture.effect.starts)
        assertEquals(NaviampCastDiscoveryController.ServiceType, fixture.effect.serviceType)
        fixture.effect.onStop = {
            fixture.effect.listener.onServiceResolved(service("wifi", "tv", "TV"))
        }
        fixture.controller.stop()
        fixture.controller.stop()
        assertEquals(1, fixture.effect.stops)
        assertEquals(NaviampCastDiscoveryState(), fixture.controller.state.value)
    }

    private class Fixture {
        var now = 1_000L
        val effect = DiscoveryEffect()
        val controller = NaviampCastDiscoveryController(effect, { now }, resultLifetimeMillis = 1_000)
        fun names() = controller.state.value.targets.map { it.target.displayName }
    }

    private class DiscoveryEffect : NaviampCastDiscoveryEffect {
        lateinit var listener: NaviampCastDiscoveryListener
        var serviceType: String? = null
        var starts = 0
        var stops = 0
        var failOnStart = false
        var onStop: () -> Unit = {}
        override fun start(serviceType: String, listener: NaviampCastDiscoveryListener) {
            starts++
            this.serviceType = serviceType
            this.listener = listener
            if (failOnStart) error("Native discovery could not start")
        }
        override fun stop() { stops++; onStop() }
    }

    private fun service(key: String, id: String, name: String, host: String = "192.0.2.1") =
        NaviampCastResolvedService(key, listOf(host), 8009, mapOf("id" to id, "fn" to name))
}
