package app.naviamp.app

import app.naviamp.domain.connect.NaviampConnectAdvertisement
import app.naviamp.domain.connect.NaviampConnectCapability
import app.naviamp.domain.connect.NaviampConnectDiscoveryMetadata
import app.naviamp.domain.connect.NaviampConnectProtocolRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NaviampConnectAdvertisingControllerTest {
    @Test
    fun advertisesOnlyMinimalSharedMetadataAndTracksNativeName() {
        val effect = FakeAdvertisingEffect()
        val controller = NaviampConnectAdvertisingController(effect, nowEpochMillis = { 1_000 })
        val advertisement = advertisement()

        controller.start(advertisement)

        assertEquals("Naviamp Living Room", effect.service.serviceName)
        assertEquals(NaviampConnectDiscoveryMetadata.encode(advertisement), effect.service.textAttributes)
        assertIs<NaviampConnectAdvertisingStatus.Starting>(controller.state.value)

        effect.listener.onServiceRegistered("Naviamp Living Room (2)")

        val advertising = assertIs<NaviampConnectAdvertisingStatus.Advertising>(controller.state.value)
        assertEquals("Naviamp Living Room (2)", advertising.registeredServiceName)
    }

    @Test
    fun expiredAdvertisementNeverTouchesNativeRegistration() {
        val effect = FakeAdvertisingEffect()
        val controller = NaviampConnectAdvertisingController(effect, nowEpochMillis = { 5_000 })

        assertFailsWith<IllegalArgumentException> { controller.start(advertisement(expiresAt = 5_000)) }

        assertTrue(!effect.started)
    }

    @Test
    fun expiryStopsAnActiveAdvertisement() {
        var now = 1_000L
        val effect = FakeAdvertisingEffect()
        val controller = NaviampConnectAdvertisingController(effect, nowEpochMillis = { now })
        controller.start(advertisement(expiresAt = 2_000))
        effect.listener.onServiceRegistered("Naviamp Living Room")

        now = 2_000

        assertTrue(controller.refreshExpiry())
        assertEquals(1, effect.stopCount)
        assertEquals(NaviampConnectAdvertisingStatus.Idle, controller.state.value)
    }

    @Test
    fun permissionDenialBecomesSharedFailureState() {
        val effect = FakeAdvertisingEffect(NaviampConnectAdvertisingStartResult.PermissionDenied)
        val controller = NaviampConnectAdvertisingController(effect, nowEpochMillis = { 1_000 })

        controller.start(advertisement())

        val failed = assertIs<NaviampConnectAdvertisingStatus.Failed>(controller.state.value)
        assertTrue(failed.message.contains("permission"))
    }

    private fun advertisement(expiresAt: Long = 5_000) = NaviampConnectAdvertisement(
        instanceId = "target-instance",
        displayName = "Living Room",
        protocolRange = NaviampConnectProtocolRange(),
        capabilities = setOf(NaviampConnectCapability.TransportControls),
        port = 42_424,
        identityFingerprint = "target-fingerprint",
        expiresAtEpochMillis = expiresAt,
    )
}

private class FakeAdvertisingEffect(
    private val startResult: NaviampConnectAdvertisingStartResult = NaviampConnectAdvertisingStartResult.Started,
) : NaviampConnectAdvertisingEffect {
    lateinit var service: NaviampConnectRegistrationService
    lateinit var listener: NaviampConnectAdvertisingListener
    var started = false
    var stopCount = 0

    override fun start(
        service: NaviampConnectRegistrationService,
        listener: NaviampConnectAdvertisingListener,
    ): NaviampConnectAdvertisingStartResult {
        this.service = service
        this.listener = listener
        started = true
        return startResult
    }

    override fun stop() {
        stopCount += 1
    }
}
