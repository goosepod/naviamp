package app.naviamp.app

import app.naviamp.domain.connect.NaviampConnectAdvertisement
import app.naviamp.domain.connect.NaviampConnectCapability
import app.naviamp.domain.connect.NaviampConnectProtocolRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NaviampConnectDiscoveryControllerTest {
    @Test
    fun discoversCompatibleTargetsInStableDisplayOrderAndRefreshesThem() {
        val effect = FakeDiscoveryEffect()
        val controller = NaviampConnectDiscoveryController(effect, nowEpochMillis = { 1_000 })

        controller.start()
        effect.listener.onServiceResolved(service("second", "Bedroom"))
        effect.listener.onServiceResolved(service("first", "Den"))
        effect.listener.onServiceResolved(service("second", "Bedroom TV"))

        assertEquals(
            listOf("Bedroom TV", "Den"),
            controller.state.value.targets.map { it.advertisement.displayName },
        )
        assertEquals(NaviampConnectDiscoveryStatus.Discovering, controller.state.value.status)
    }

    @Test
    fun ignoresIncompatibleTargets() {
        val effect = FakeDiscoveryEffect()
        val controller = NaviampConnectDiscoveryController(effect, nowEpochMillis = { 1_000 })
        controller.start()

        effect.listener.onServiceResolved(
            service("future", "Future", protocolRange = NaviampConnectProtocolRange(2, 3)),
        )

        assertTrue(controller.state.value.targets.isEmpty())
    }

    @Test
    fun removesTargetsWhenTheirLifetimeExpires() {
        var now = 1_000L
        val effect = FakeDiscoveryEffect()
        val controller = NaviampConnectDiscoveryController(
            effect,
            nowEpochMillis = { now },
            resultLifetimeMillis = 1_000,
        )
        controller.start()
        effect.listener.onServiceResolved(service("den", "Den"))

        now = 2_000
        controller.refreshExpiry()

        assertTrue(controller.state.value.targets.isEmpty())
    }

    @Test
    fun nativeLossRemovesTheMatchingService() {
        val effect = FakeDiscoveryEffect()
        val controller = NaviampConnectDiscoveryController(effect, nowEpochMillis = { 1_000 })
        controller.start()
        effect.listener.onServiceResolved(service("den", "Den"))

        effect.listener.onServiceLost("Naviamp-den")

        assertTrue(controller.state.value.targets.isEmpty())
    }

    @Test
    fun rejectsAnIdentityChangeForTheSameDiscoveryInstance() {
        val effect = FakeDiscoveryEffect()
        val controller = NaviampConnectDiscoveryController(effect, nowEpochMillis = { 1_000 })
        controller.start()
        effect.listener.onServiceResolved(service("den", "Den", fingerprint = "key-a"))
        effect.listener.onServiceResolved(service("den", "Den", fingerprint = "key-b"))

        assertTrue(controller.state.value.targets.isEmpty())
        assertEquals(
            NaviampConnectDiscoveryProblem.TargetIdentityChanged("den"),
            controller.state.value.problem,
        )
    }

    @Test
    fun exposesPermissionDenialWithoutStartingDiscovery() {
        val effect = FakeDiscoveryEffect(NaviampConnectDiscoveryStartResult.PermissionDenied)
        val controller = NaviampConnectDiscoveryController(effect, nowEpochMillis = { 1_000 })

        controller.start()

        assertEquals(NaviampConnectDiscoveryStatus.Idle, controller.state.value.status)
        assertIs<NaviampConnectDiscoveryProblem.PermissionDenied>(controller.state.value.problem)
    }

    @Test
    fun stopClearsResultsAndStopsTheNativeEffect() {
        val effect = FakeDiscoveryEffect()
        val controller = NaviampConnectDiscoveryController(effect, nowEpochMillis = { 1_000 })
        controller.start()
        effect.listener.onServiceResolved(service("den", "Den"))

        controller.stop()

        assertEquals(1, effect.stopCount)
        assertEquals(NaviampConnectDiscoveryState(), controller.state.value)
    }

    private fun service(
        id: String,
        name: String,
        fingerprint: String = "target-key",
        protocolRange: NaviampConnectProtocolRange = NaviampConnectProtocolRange(),
    ): NaviampConnectResolvedService {
        val advertisement = NaviampConnectAdvertisement(
            instanceId = id,
            displayName = name,
            protocolRange = protocolRange,
            capabilities = setOf(NaviampConnectCapability.TransportControls),
            port = 42_424,
            identityFingerprint = fingerprint,
            expiresAtEpochMillis = Long.MAX_VALUE,
        )
        return NaviampConnectResolvedService(
            serviceName = "Naviamp-$id",
            addresses = listOf("192.0.2.1"),
            port = 42_424,
            textAttributes = app.naviamp.domain.connect.NaviampConnectDiscoveryMetadata.encode(advertisement),
        )
    }
}

private class FakeDiscoveryEffect(
    private val startResult: NaviampConnectDiscoveryStartResult = NaviampConnectDiscoveryStartResult.Started,
) : NaviampConnectDiscoveryEffect {
    lateinit var listener: NaviampConnectDiscoveryListener
    var stopCount = 0

    override fun start(listener: NaviampConnectDiscoveryListener): NaviampConnectDiscoveryStartResult {
        this.listener = listener
        return startResult
    }

    override fun stop() {
        stopCount += 1
    }
}
