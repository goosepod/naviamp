package app.naviamp.app

import app.naviamp.domain.connect.NaviampConnectDevice
import app.naviamp.domain.connect.NaviampConnectDeviceCapability
import app.naviamp.domain.connect.NaviampConnectDeviceRole
import app.naviamp.domain.connect.NaviampConnectTrustRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NaviampConnectPlaybackDestinationControllerTest {
    @Test
    fun selectingRemoteOutputArmsItWithoutRequiringAConnection() {
        val controller = NaviampConnectPlaybackDestinationController()

        controller.select(targetTrust())

        assertEquals(
            NaviampConnectPlaybackDestination.Remote(
                NaviampConnectSelectedPlaybackDevice("trusted-speakers", "speakers", "Office Speakers"),
                NaviampConnectRemoteOutputStatus.Armed,
            ),
            controller.state.value,
        )
        assertFalse(controller.isConnectedRemote())
        assertFalse(controller.hasRemotePlaybackAuthority())
    }

    @Test
    fun playbackAuthorityActivatesOnlyForTheCurrentConnectedSelection() {
        val controller = NaviampConnectPlaybackDestinationController()
        controller.select(targetTrust())

        assertFalse(controller.activatePlaybackAuthority("trusted-speakers"))
        assertTrue(controller.connected(targetTrust().peerDevice))
        assertFalse(controller.activatePlaybackAuthority("some-other-target"))
        assertTrue(controller.activatePlaybackAuthority("trusted-speakers"))
        assertTrue(controller.hasRemotePlaybackAuthority())

        controller.select(targetTrust("trusted-two", "two", "Two"))
        assertFalse(controller.hasRemotePlaybackAuthority())
    }

    @Test
    fun transientLossPreservesSelectionForReconnect() {
        val controller = NaviampConnectPlaybackDestinationController()
        controller.select(targetTrust())
        assertTrue(controller.connecting("trusted-speakers"))
        assertTrue(controller.connected(targetTrust().peerDevice))

        assertTrue(controller.reconnecting())
        assertEquals(
            NaviampConnectRemoteOutputStatus.Reconnecting,
            (controller.state.value as NaviampConnectPlaybackDestination.Remote).status,
        )
        assertEquals("trusted-speakers", controller.selectedTrustedDeviceId())
    }

    @Test
    fun staleSessionCallbacksCannotChangeANewSelection() {
        val controller = NaviampConnectPlaybackDestinationController()
        controller.select(targetTrust("trusted-one", "one", "One"))
        controller.select(targetTrust("trusted-two", "two", "Two"))

        assertFalse(controller.connecting("trusted-one"))
        assertFalse(controller.connected(targetTrust("trusted-one", "one", "One").peerDevice))
        assertEquals("trusted-two", controller.selectedTrustedDeviceId())
    }

    @Test
    fun explicitLocalSelectionDetachesWithoutForgettingTrust() {
        val trust = targetTrust()
        val controller = NaviampConnectPlaybackDestinationController()
        controller.select(trust)

        controller.selectLocal()

        assertEquals(NaviampConnectPlaybackDestination.Local, controller.state.value)
        assertEquals("trusted-speakers", trust.trustedDeviceId)
    }

    @Test
    fun controllerOnlyDeviceCannotBeSelectedAsPlaybackOutput() {
        val trust = NaviampConnectTrustRecord(
            trustedDeviceId = "trusted-controller",
            peerDevice = NaviampConnectDevice(
                deviceId = "controller",
                displayName = "Controller",
                role = NaviampConnectDeviceRole.Controller,
                deviceCapabilities = setOf(NaviampConnectDeviceCapability.ControlPlayback),
            ),
            identityFingerprint = "fingerprint-controller",
            publicKeyBase64 = "key-controller",
            pairedAtEpochMillis = 1L,
        )

        assertFailsWith<IllegalArgumentException> {
            NaviampConnectPlaybackDestinationController().select(trust)
        }
    }

    private fun targetTrust(
        trustedDeviceId: String = "trusted-speakers",
        deviceId: String = "speakers",
        displayName: String = "Office Speakers",
    ) = NaviampConnectTrustRecord(
        trustedDeviceId = trustedDeviceId,
        peerDevice = NaviampConnectDevice(
            deviceId = deviceId,
            displayName = displayName,
            role = NaviampConnectDeviceRole.Target,
            deviceCapabilities = setOf(NaviampConnectDeviceCapability.PlaybackTarget),
        ),
        identityFingerprint = "fingerprint-$deviceId",
        publicKeyBase64 = "key-$deviceId",
        pairedAtEpochMillis = 1L,
    )
}
