package app.naviamp.domain.connect

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NaviampConnectPairingTest {
    @Test
    fun controllerRejectsAnIncompatibleTargetBeforeRequestingACode() {
        val controller = NaviampConnectControllerPairingController(
            localProtocolRange = NaviampConnectProtocolRange(1, 1),
        )
        controller.startDiscovery()
        controller.updateDiscoveredTarget(
            advertisement(protocolRange = NaviampConnectProtocolRange(2, 2)),
            nowEpochMillis = 1_000,
        )

        val state = controller.selectTarget("instance", nowEpochMillis = 1_000)

        assertEquals(
            NaviampConnectErrorCode.IncompatibleProtocol,
            assertIs<NaviampConnectControllerPairingState.Failed>(state).code,
        )
    }

    @Test
    fun controllerDoesNotRetainTheSubmittedShortCode() {
        val controller = NaviampConnectControllerPairingController()
        controller.startDiscovery()
        controller.updateDiscoveredTarget(advertisement(), nowEpochMillis = 1_000)
        controller.selectTarget("instance", nowEpochMillis = 1_000)
        var consumedCode: String? = null

        val state = controller.submitCode("493821") { code ->
            consumedCode = code
            true
        }

        assertEquals("493821", consumedCode)
        assertIs<NaviampConnectControllerPairingState.Handshaking>(state)
        assertTrue(state.toString().contains("493821").not())
    }

    @Test
    fun targetRequiresVisibleApprovalBeforeHandshake() {
        val target = NaviampConnectTargetPairingController()
        target.start(advertisement(), "pairing", "493821", nowEpochMillis = 1_000)

        val awaiting = target.requestApproval(controllerDevice(), "pairing", nowEpochMillis = 1_100)
        assertIs<NaviampConnectTargetPairingState.AwaitingApproval>(awaiting)

        val handshake = requireNotNull(target.approve(nowEpochMillis = 1_200))
        assertIs<NaviampConnectTargetPairingState.Handshaking>(handshake.state)
        assertEquals("493821", handshake.pairingCode.concatToString())
        assertEquals("", handshake.state.advertising.displayCode)
        handshake.pairingCode.fill('\u0000')

        val paired = target.complete(
            trust = NaviampConnectTrustRecord(
                trustedDeviceId = "trusted-controller",
                peerDevice = controllerDevice(),
                identityFingerprint = "controller-fingerprint",
                publicKeyBase64 = "controller-public-key",
                pairedAtEpochMillis = 1_300,
            ),
            nowEpochMillis = 1_300,
        )
        assertIs<NaviampConnectTargetPairingState.Paired>(paired)
    }

    @Test
    fun targetExpiresPairingAndRateLimitsRepeatedFailures() {
        val expiring = NaviampConnectTargetPairingController()
        expiring.start(advertisement(expiresAt = 2_000), "pairing", "493821", nowEpochMillis = 1_000)
        assertTrue(expiring.expireIfNeeded(nowEpochMillis = 2_000))
        assertEquals(
            NaviampConnectErrorCode.PairingExpired,
            assertIs<NaviampConnectTargetPairingState.Failed>(expiring.state).code,
        )

        val limited = NaviampConnectTargetPairingController(maximumAttempts = 2, rateLimitMillis = 10_000)
        limited.start(advertisement(), "pairing", "493821", nowEpochMillis = 1_000)
        limited.requestApproval(controllerDevice(), "wrong", nowEpochMillis = 1_100)
        val state = limited.requestApproval(controllerDevice(), "wrong", nowEpochMillis = 1_200)

        val failed = assertIs<NaviampConnectTargetPairingState.Failed>(state)
        assertEquals(NaviampConnectErrorCode.RateLimited, failed.code)
        assertEquals(11_200, failed.retryAtEpochMillis)
    }

    private fun advertisement(
        protocolRange: NaviampConnectProtocolRange = NaviampConnectProtocolRange(),
        expiresAt: Long = 60_000,
    ) = NaviampConnectAdvertisement(
        instanceId = "instance",
        displayName = "Living Room",
        protocolRange = protocolRange,
        capabilities = setOf(NaviampConnectCapability.TransportControls),
        port = 38_421,
        identityFingerprint = "target-fingerprint",
        expiresAtEpochMillis = expiresAt,
    )

    private fun controllerDevice() = NaviampConnectDevice(
        deviceId = "controller",
        displayName = "Phone",
        role = NaviampConnectDeviceRole.Controller,
    )
}
