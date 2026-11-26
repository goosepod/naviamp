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
            phoneActions.onConnectScreenVisibilityChanged(true)
            runCurrent()
            val target = phone.state.value.shell.connect.discoveredTargets.single { it.displayName == "mac" }
            phoneActions.onTargetSelected(target)
            phoneActions.onPairingCodeChanged(assertNotNull(mac.state.value.shell.connect.pairingCode))
            runCurrent()
            assertEquals("mac", phone.state.value.shell.connect.connectedTargetName)
            val sheet = assertNotNull(phone.state.value.shell.connect.postConnect)
            assertTrue(sheet.canSignIn)
            assertTrue(sheet.signInSelected, "A device without accounts preselects sign-in")
            assertEquals(0, destination.connects, "Nothing is sent before the user finishes the sheet")
            assertEquals(NaviampConnectStatusText.ConnectedToDevice, mac.state.value.shell.connect.statusMessage?.text)
            phoneActions.onPostConnectDone()
            runCurrent()
            assertTrue(phone.state.value.shell.connect.provisioningBusy)
            advanceTimeBy(16_000)
            runCurrent()
            assertEquals("mac", phone.state.value.shell.connect.connectedTargetName)
            advanceTimeBy(4_001)
            runCurrent()
            assertEquals("fixture", destination.currentSourceId())
            assertEquals(1, source.exports)
            assertEquals(1, destination.connects)
            assertEquals(1, network.advertisingStarts["mac"], "Fresh pairing must keep the advertised endpoint registered")
            assertNull(network.advertisingStops["mac"], "DNS teardown must not block the live session after Welcome")
            assertTrue(events.none { it.kind == NaviampCoreConnectTraceKind.HeartbeatOrWriteTimedOut })
            assertNull(phone.state.value.shell.connect.postConnect)
            assertNull(phone.state.value.shell.connect.connectedTargetName, "Done keeps playback on this device")
            assertEquals(1, phone.state.value.shell.connect.trustedDevices.size)
            phoneActions.onTrustedDeviceSelected(phone.state.value.shell.connect.trustedDevices.single())
            runCurrent()
            assertEquals("mac", phone.state.value.shell.connect.connectedTargetName)
            assertEquals(1, source.exports)
            assertNull(phone.state.value.shell.connect.postConnect, "A trusted resume shows no post-connect sheet")
            assertEquals(1, destination.connects)
        } finally { phone.close(); mac.close() }
    }

    private val realCrypto: (NaviampCoreConnectServices) -> NaviampCoreConnectServices = { it.copy(
        pake = BouncyCastleNaviampConnectPakeFactory,
        cipher = JvmNaviampConnectAuthenticatedCipherFactory,
    ) }

    private fun TestScope.enterCode(controller: NaviampCore, target: NaviampCore, targetName: String) {
        val actions = controller.actions.shell.connectActions!!
        actions.onConnectScreenVisibilityChanged(true)
        runCurrent()
        actions.onTargetSelected(controller.state.value.shell.connect.discoveredTargets.single { it.displayName == targetName })
        actions.onPairingCodeChanged(assertNotNull(target.state.value.shell.connect.pairingCode))
    }

    @Test
    fun signedInDeviceDoesNotPreselectSignIn() = runTest {
        val network = ConnectTestNetwork()
        val source = ConnectTestSource(configured = true)
        val destination = ConnectTestSource(configured = false)
        val phone = connectPeer("phone", "mac", network, source, preTrusted = false, configure = realCrypto)
        val secondPhone = connectPeer("phone-2", "mac", network, ConnectTestSource(configured = true),
            preTrusted = false, configure = realCrypto)
        val mac = connectPeer("mac", "phone", network, destination, preTrusted = false, configure = realCrypto)
        try {
            mac.actions.shell.connectActions!!.onStartPairingMode()
            runCurrent()
            enterCode(phone, mac, "mac")
            runCurrent()
            phone.actions.shell.connectActions!!.onPostConnectDone()
            runCurrent()
            assertEquals("fixture", destination.currentSourceId())
            mac.actions.shell.connectActions!!.onStartPairingMode()
            runCurrent()
            enterCode(secondPhone, mac, "mac")
            runCurrent()
            val sheet = assertNotNull(secondPhone.state.value.shell.connect.postConnect)
            assertTrue(sheet.deviceHasAccounts)
            assertFalse(sheet.signInSelected)
            secondPhone.actions.shell.connectActions!!.onPostConnectDone()
            runCurrent()
            assertEquals(1, destination.connects, "Done without sign-in sends nothing")
            assertNull(secondPhone.state.value.shell.connect.postConnect)
        } finally { phone.close(); secondPhone.close(); mac.close() }
    }

    @Test
    fun playOnDeviceSignsInAndKeepsTheRemoteOutput() = runTest {
        val network = ConnectTestNetwork()
        val source = ConnectTestSource(configured = true)
        val destination = ConnectTestSource(configured = false)
        val phone = connectPeer("phone", "mac", network, source, preTrusted = false, configure = realCrypto)
        val mac = connectPeer("mac", "phone", network, destination, preTrusted = false, configure = realCrypto)
        try {
            mac.actions.shell.connectActions!!.onStartPairingMode()
            runCurrent()
            enterCode(phone, mac, "mac")
            runCurrent()
            phone.actions.shell.connectActions!!.onPostConnectPlayOnDevice()
            runCurrent()
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals("fixture", destination.currentSourceId())
            assertEquals("mac", phone.state.value.shell.connect.connectedTargetName)
            assertNotEquals(app.naviamp.ui.NaviampConnectPlaybackDestinationUiStatus.Local,
                phone.state.value.shell.connect.playbackDestinationStatus)
            assertNull(phone.state.value.shell.connect.postConnect)
        } finally { phone.close(); mac.close() }
    }

    @Test
    fun secondPhoneConnectsToAnAlreadyTrustedTargetWithoutApproval() = runTest {
        val network = ConnectTestNetwork()
        val phoneA = connectPeer("phone-a", "mac", network, preTrusted = false, configure = realCrypto)
        val phoneB = connectPeer("phone-b", "mac", network, preTrusted = false, configure = realCrypto)
        val mac = connectPeer("mac", "phone-a", network, preTrusted = false, configure = realCrypto)
        try {
            mac.actions.shell.connectActions!!.onStartPairingMode()
            runCurrent()
            enterCode(phoneA, mac, "mac")
            runCurrent()
            assertEquals("mac", phoneA.state.value.shell.connect.connectedTargetName)
            mac.actions.shell.connectActions!!.onStartPairingMode()
            runCurrent()
            enterCode(phoneB, mac, "mac")
            runCurrent()
            assertEquals("mac", phoneB.state.value.shell.connect.connectedTargetName, "The displayed code is the consent")
            assertEquals(2, mac.state.value.shell.connect.trustedDevices.size)
        } finally { phoneA.close(); phoneB.close(); mac.close() }
    }

    @Test
    fun simultaneousCodeEntryConnectsExactlyOnePeer() = runTest {
        val network = ConnectTestNetwork()
        val phoneA = connectPeer("phone-a", "mac", network, preTrusted = false, configure = realCrypto)
        val phoneB = connectPeer("phone-b", "mac", network, preTrusted = false, configure = realCrypto)
        val mac = connectPeer("mac", "phone-a", network, preTrusted = false, configure = realCrypto)
        try {
            mac.actions.shell.connectActions!!.onStartPairingMode()
            runCurrent()
            val code = assertNotNull(mac.state.value.shell.connect.pairingCode)
            listOf(phoneA, phoneB).forEach { phone ->
                val actions = phone.actions.shell.connectActions!!
                actions.onConnectScreenVisibilityChanged(true)
                runCurrent()
                actions.onTargetSelected(phone.state.value.shell.connect.discoveredTargets.single { it.displayName == "mac" })
                actions.onPairingCodeChanged(code)
            }
            phoneA.actions.shell.connectActions!!.onSubmitPairingCode()
            phoneB.actions.shell.connectActions!!.onSubmitPairingCode()
            runCurrent()
            advanceTimeBy(60_000)
            runCurrent()
            val connected = listOf(phoneA, phoneB).count { it.state.value.shell.connect.connectedTargetName == "mac" }
            assertEquals(1, connected)
            assertEquals(1, mac.state.value.shell.connect.trustedDevices.size)
        } finally { phoneA.close(); phoneB.close(); mac.close() }
    }

    @Test
    fun reenteringACodeReplacesExistingTrust() = runTest {
        val network = ConnectTestNetwork()
        val phone = connectPeer("phone", "mac", network, preTrusted = false, configure = realCrypto)
        val mac = connectPeer("mac", "phone", network, preTrusted = false, configure = realCrypto)
        try {
            repeat(2) {
                mac.actions.shell.connectActions!!.onStartPairingMode()
                runCurrent()
                enterCode(phone, mac, "mac")
                runCurrent()
                assertEquals("mac", phone.state.value.shell.connect.connectedTargetName)
                phone.actions.shell.connectActions!!.onStopControlling()
                runCurrent()
            }
            assertEquals(1, phone.state.value.shell.connect.trustedDevices.size)
            assertEquals(1, mac.state.value.shell.connect.trustedDevices.size)
        } finally { phone.close(); mac.close() }
    }
}
