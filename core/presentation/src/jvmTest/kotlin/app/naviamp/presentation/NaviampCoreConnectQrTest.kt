package app.naviamp.presentation

import app.naviamp.app.BouncyCastleNaviampConnectPakeFactory
import app.naviamp.app.JvmNaviampConnectAuthenticatedCipherFactory
import app.naviamp.domain.connect.naviampConnectLinkSecret
import app.naviamp.ui.NaviampConnectStatusText
import kotlinx.coroutines.test.*
import kotlin.test.*

/** Scanning a QR code between two shared Core peers over the real PAKE and cipher (#715). */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NaviampCoreConnectQrTest {
    private var nextSecret = 0
    private val realCrypto: (NaviampCoreConnectServices) -> NaviampCoreConnectServices = { it.copy(
        pake = BouncyCastleNaviampConnectPakeFactory,
        cipher = JvmNaviampConnectAuthenticatedCipherFactory,
        newLinkSecret = { naviampConnectLinkSecret(ByteArray(16) { index -> (index + ++nextSecret).toByte() }) },
    ) }

    @Test
    fun scanningTheQrConnectsWithNoTypingAndOpensTheSheet() = runTest {
        val network = ConnectTestNetwork()
        val phone = connectPeer("phone", Tv, network, ConnectTestSource(configured = true), preTrusted = false, configure = realCrypto)
        val tv = connectPeer(Tv, "phone", network, preTrusted = false, configure = realCrypto)
        try {
            tv.actions.shell.connectActions!!.onStartPairingMode()
            runCurrent()
            val connect = tv.state.value.shell.connect
            val link = assertNotNull(connect.pairingLink, "The QR is shown together with the code")
            assertNotNull(connect.pairingCode)
            assertFalse(connect.pairingCode!! in link.substringAfter("&s="), "The QR carries its own secret, not the code")
            phone.actions.shell.connectActions!!.onConnectLinkScanned(link)
            runCurrent()
            assertEquals(Tv, phone.state.value.shell.connect.connectedTargetName)
            assertNotNull(phone.state.value.shell.connect.postConnect, "A scan continues into the post-connect sheet")
            assertNotEquals(link, tv.state.value.shell.connect.pairingLink, "A used QR is replaced at once")
        } finally { phone.close(); tv.close() }
    }

    @Test
    fun aUsedQrIsRefusedAsReplacedWhileTheCurrentCodeStillWorks() = runTest {
        val network = ConnectTestNetwork()
        val phoneA = connectPeer("phone-a", Tv, network, preTrusted = false, configure = realCrypto)
        val phoneB = connectPeer("phone-b", Tv, network, preTrusted = false, configure = realCrypto)
        val tv = connectPeer(Tv, "phone-a", network, preTrusted = false, configure = realCrypto)
        try {
            tv.actions.shell.connectActions!!.onStartPairingMode()
            runCurrent()
            val used = assertNotNull(tv.state.value.shell.connect.pairingLink)
            phoneA.actions.shell.connectActions!!.onConnectLinkScanned(used)
            runCurrent()
            assertEquals(Tv, phoneA.state.value.shell.connect.connectedTargetName)
            val phoneBActions = phoneB.actions.shell.connectActions!!
            phoneBActions.onConnectScreenVisibilityChanged(true)
            runCurrent()
            phoneBActions.onConnectLinkScanned(used)
            runCurrent()
            assertNull(phoneB.state.value.shell.connect.connectedTargetName)
            assertEquals(NaviampConnectStatusText.QrNoLongerShown, phoneB.state.value.shell.connect.statusMessage?.text)
            phoneBActions.onPairingCodeChanged(assertNotNull(tv.state.value.shell.connect.pairingCode))
            runCurrent()
            assertEquals(Tv, phoneB.state.value.shell.connect.connectedTargetName, "The code on screen still works")
        } finally { phoneA.close(); phoneB.close(); tv.close() }
    }

    @Test
    fun anAlteredQrIsNotVerifiedAndTheCodeStillWorks() = runTest {
        val network = ConnectTestNetwork()
        val phone = connectPeer("phone", Tv, network, preTrusted = false, configure = realCrypto)
        val tv = connectPeer(Tv, "phone", network, preTrusted = false, configure = realCrypto)
        try {
            val actions = phone.actions.shell.connectActions!!
            tv.actions.shell.connectActions!!.onStartPairingMode()
            actions.onConnectScreenVisibilityChanged(true)
            runCurrent()
            val link = assertNotNull(tv.state.value.shell.connect.pairingLink)
            val altered = link.substringBefore("&s=") + "&s=" + naviampConnectLinkSecret(ByteArray(16) { 42 })
            actions.onConnectLinkScanned(altered)
            runCurrent()
            assertNull(phone.state.value.shell.connect.connectedTargetName)
            assertEquals(NaviampConnectStatusText.QrNotVerified, phone.state.value.shell.connect.statusMessage?.text)
            actions.onPairingCodeChanged(assertNotNull(tv.state.value.shell.connect.pairingCode))
            runCurrent()
            assertEquals(Tv, phone.state.value.shell.connect.connectedTargetName)
        } finally { phone.close(); tv.close() }
    }

    @Test
    fun textThatIsNotAConnectLinkIsRefusedClearly() = runTest {
        val network = ConnectTestNetwork()
        val phone = connectPeer("phone", Tv, network, preTrusted = false, configure = realCrypto)
        try {
            phone.actions.shell.connectActions!!.onConnectLinkScanned("https://example.com/menu")
            runCurrent()
            assertEquals(NaviampConnectStatusText.QrNotRecognized, phone.state.value.shell.connect.statusMessage?.text)
            phone.actions.shell.connectActions!!.onConnectLinkScanned("naviamp://connect?v=9#n=TV")
            runCurrent()
            assertEquals(NaviampConnectStatusText.QrNeedsNewerVersion, phone.state.value.shell.connect.statusMessage?.text)
        } finally { phone.close() }
    }

    @Test
    fun aLinkFromOutsideTheAppReportsItsOutcomeAppWide() = runTest {
        val network = ConnectTestNetwork()
        val phone = connectPeer("phone", Tv, network, preTrusted = false, configure = realCrypto)
        val tv = connectPeer(Tv, "phone", network, preTrusted = false, configure = realCrypto)
        try {
            // An in-app scan reports on the Connect screen only.
            phone.actions.shell.connectActions!!.onConnectLinkScanned("not a link")
            runCurrent()
            assertNull(phone.state.value.shell.connect.linkStatus)
            // The system camera opens the app wherever it is, so the result must be visible there.
            phone.openConnectLink("not a link")
            runCurrent()
            assertEquals(NaviampConnectStatusText.QrNotRecognized, phone.state.value.shell.connect.linkStatus?.text)
            phone.actions.shell.connectActions!!.onDismissLinkStatus()
            runCurrent()
            assertNull(phone.state.value.shell.connect.linkStatus)
            tv.actions.shell.connectActions!!.onStartPairingMode()
            runCurrent()
            phone.openConnectLink(assertNotNull(tv.state.value.shell.connect.pairingLink))
            runCurrent()
            assertEquals(Tv, phone.state.value.shell.connect.connectedTargetName)
            assertNull(phone.state.value.shell.connect.linkStatus, "Success continues in the post-connect sheet")
            assertNotNull(phone.state.value.shell.connect.postConnect)
        } finally { phone.close(); tv.close() }
    }

    @Test
    fun aDeviceWithoutALinkSecretShowsOnlyTheCode() = runTest {
        val network = ConnectTestNetwork()
        val tv = connectPeer(Tv, "phone", network, preTrusted = false,
            configure = { realCrypto(it).copy(newLinkSecret = null) })
        try {
            tv.actions.shell.connectActions!!.onStartPairingMode()
            runCurrent()
            assertNotNull(tv.state.value.shell.connect.pairingCode)
            assertNull(tv.state.value.shell.connect.pairingLink)
        } finally { tv.close() }
    }

    private companion object {
        /** The fixture's identity fingerprint is the device ID, so the TV's ID is a 64-hex fingerprint. */
        val Tv = "a1".repeat(32)
    }
}
