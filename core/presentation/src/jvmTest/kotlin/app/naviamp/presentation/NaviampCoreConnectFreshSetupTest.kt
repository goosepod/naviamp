package app.naviamp.presentation

import app.naviamp.app.BouncyCastleNaviampConnectPakeFactory
import app.naviamp.app.JvmNaviampConnectAuthenticatedCipherFactory
import app.naviamp.ui.NaviampConnectStatusText
import kotlinx.coroutines.test.*
import kotlin.test.*

/** Real JVM PAKE/cipher adapters over the shared peer fixture; no physical device or provider network. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NaviampCoreConnectFreshSetupTest {
    @Test
    fun freshCodeSetupSurvivesSlowValidationAndTrustedReconnectDoesNotReplayCredentials() = runTest {
        val network = ConnectTestNetwork()
        val events = mutableListOf<NaviampCoreConnectTraceEvent>()
        val source = ConnectTestSource(configured = true)
        val destination = ConnectTestSource(configured = false, validationDelayMillis = 20_000)
        val configure: (NaviampCoreConnectServices) -> NaviampCoreConnectServices = { it.copy(
            pake = BouncyCastleNaviampConnectPakeFactory,
            cipher = JvmNaviampConnectAuthenticatedCipherFactory,
            trace = events::add,
        ) }
        val phone = connectPeer("phone", "mac", network, source, preTrusted = false, configure = configure)
        val mac = connectPeer("mac", "phone", network, destination, preTrusted = false, configure = configure)
        try {
            val phoneActions = phone.actions.shell.connectActions!!
            mac.actions.shell.connectActions!!.onStartPairingMode()
            phoneActions.onRefreshTargets()
            runCurrent()
            val target = phone.state.value.shell.connect.discoveredTargets.single { it.displayName == "mac" }
            phoneActions.onTargetSelected(target)
            phoneActions.onPairingCodeChanged(assertNotNull(mac.state.value.shell.connect.pairingCode))
            phoneActions.onSubmitPairingCode()
            runCurrent()
            assertEquals("mac", phone.state.value.shell.connect.connectedTargetName)
            assertTrue(phone.state.value.shell.connect.provisioningBusy)
            assertNull(mac.state.value.shell.connect.pendingProvisioningConnectionName, "Code consent allows initial setup without another approval")
            advanceTimeBy(16_000)
            runCurrent()
            assertEquals("mac", phone.state.value.shell.connect.connectedTargetName)
            advanceTimeBy(4_001)
            runCurrent()
            assertEquals(NaviampConnectStatusText.TvSetupCompletedSecurely, phone.state.value.shell.connect.statusMessage?.text)
            assertEquals("fixture", destination.currentSourceId())
            assertEquals(1, source.exports)
            assertEquals(1, destination.connects)
            assertEquals(1, network.advertisingStarts["mac"], "Fresh pairing must keep the advertised endpoint registered")
            assertNull(network.advertisingStops["mac"], "DNS teardown must not block the live session after Welcome")
            assertTrue(events.any { it.kind == NaviampCoreConnectTraceKind.InitialSetupAuthorized })
            assertTrue(events.none { it.kind == NaviampCoreConnectTraceKind.HeartbeatOrWriteTimedOut })
            phoneActions.onStopControlling()
            runCurrent()
            phoneActions.onTrustedDeviceSelected(phone.state.value.shell.connect.trustedDevices.single())
            runCurrent()
            assertEquals("mac", phone.state.value.shell.connect.connectedTargetName)
            assertEquals(1, source.exports)
            assertEquals(1, destination.connects)
        } finally { phone.close(); mac.close() }
    }
}
