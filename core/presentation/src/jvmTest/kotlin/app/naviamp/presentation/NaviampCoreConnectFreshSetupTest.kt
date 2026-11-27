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
    fun aUsedCodeIsReplacedSoTheStillOpenPanelNeverAdvertisesAStaleCode() = runTest {
        val network = ConnectTestNetwork()
        val phone = connectPeer("phone", "mac", network, ConnectTestSource(configured = true), preTrusted = false, configure = realCrypto)
        val secondPhone = connectPeer("phone-2", "mac", network, preTrusted = false, configure = realCrypto)
        val mac = connectPeer("mac", "phone", network, ConnectTestSource(configured = false), preTrusted = false, configure = realCrypto)
        try {
            mac.actions.shell.connectActions!!.onStartPairingMode()
            runCurrent()
            val firstCode = assertNotNull(mac.state.value.shell.connect.pairingCode)
            enterCode(phone, mac, "mac")
            runCurrent()
            assertEquals("mac", phone.state.value.shell.connect.connectedTargetName)
            val nextCode = assertNotNull(mac.state.value.shell.connect.pairingCode, "The open panel shows a fresh code")
            assertEquals(1, network.advertisingStarts["mac"], "Replacing the code must not re-register DNS-SD")
            phone.actions.shell.connectActions!!.onPostConnectSignInChanged(false)
            phone.actions.shell.connectActions!!.onPostConnectDone()
            runCurrent()
            enterCode(secondPhone, mac, "mac")
            runCurrent()
            assertEquals("mac", secondPhone.state.value.shell.connect.connectedTargetName,
                "The replacement code connects without reopening the panel (first=$firstCode next=$nextCode)")
        } finally { phone.close(); secondPhone.close(); mac.close() }
    }

    @Test
    fun doneAfterAFailedSignInClosesTheSheet() = runTest {
        val network = ConnectTestNetwork()
        val destination = ConnectTestSource(configured = false, failValidation = true)
        val phone = connectPeer("phone", "mac", network, ConnectTestSource(configured = true), preTrusted = false, configure = realCrypto)
        val mac = connectPeer("mac", "phone", network, destination, preTrusted = false, configure = realCrypto)
        try {
            mac.actions.shell.connectActions!!.onStartPairingMode()
            runCurrent()
            enterCode(phone, mac, "mac")
            runCurrent()
            phone.actions.shell.connectActions!!.onPostConnectDone()
            runCurrent()
            assertEquals(1, destination.connects)
            assertNotNull(phone.state.value.shell.connect.postConnect, "A failed sign-in keeps the sheet open")
            phone.actions.shell.connectActions!!.onPostConnectDone()
            runCurrent()
            assertNull(phone.state.value.shell.connect.postConnect, "Done closes the sheet instead of retrying")
            assertEquals(1, destination.connects)
        } finally { phone.close(); mac.close() }
    }

    @Test
    fun aConfiguredButDisconnectedTvIsNotAutoSetUpByOlderControllers() = runTest {
        val network = ConnectTestNetwork()
        val events = mutableListOf<NaviampCoreConnectTraceEvent>()
        val withTrace: (NaviampCoreConnectServices) -> NaviampCoreConnectServices = { realCrypto(it).copy(trace = events::add) }
        val phone = connectPeer("phone", "mac", network, ConnectTestSource(configured = true), preTrusted = false, configure = withTrace)
        val mac = connectPeer("mac", "phone", network, ConnectTestSource(configured = true, sourceId = "existing"),
            preTrusted = false, configure = realCrypto)
        try {
            mac.actions.shell.connectActions!!.onStartPairingMode()
            runCurrent()
            enterCode(phone, mac, "mac")
            runCurrent()
            assertEquals("mac", phone.state.value.shell.connect.connectedTargetName)
            assertTrue(events.none { it.kind == NaviampCoreConnectTraceKind.InitialSetupAuthorized },
                "Protocol-v1 controllers set up a device only when it has no accounts at all")
        } finally { phone.close(); mac.close() }
    }

    @Test
    fun dismissingTheSheetSendsNothingEvenWhenSignInIsTicked() = runTest {
        val network = ConnectTestNetwork()
        val destination = ConnectTestSource(configured = false)
        val phone = connectPeer("phone", "mac", network, ConnectTestSource(configured = true), preTrusted = false, configure = realCrypto)
        val mac = connectPeer("mac", "phone", network, destination, preTrusted = false, configure = realCrypto)
        try {
            mac.actions.shell.connectActions!!.onStartPairingMode()
            runCurrent()
            enterCode(phone, mac, "mac")
            runCurrent()
            assertTrue(assertNotNull(phone.state.value.shell.connect.postConnect).signInSelected)
            phone.actions.shell.connectActions!!.onPostConnectDismiss()
            runCurrent()
            assertNull(phone.state.value.shell.connect.postConnect)
            assertEquals(0, destination.connects, "Back, Escape or tapping outside must not send credentials")
        } finally { phone.close(); mac.close() }
    }

    @Test
    fun playOnPausesThisDeviceOnceTheTargetHasTheQueue() = runTest {
            val track = app.naviamp.domain.Track(id = app.naviamp.domain.TrackId("local"), title = "Local track",
                artistName = "Artist", albumTitle = "Album", durationSeconds = 180, coverArtId = null, audioInfo = null, replayGain = null)
        val network = ConnectTestNetwork()
        val effects = FakeCorePlaybackEffects()
        val phone = connectPeer("phone", "mac", network, ConnectTestSource(configured = true), preTrusted = false,
            configure = realCrypto, playbackEffects = effects, provider = FakeCoreMediaProvider())
        val mac = connectPeer("mac", "phone", network, ConnectTestSource(configured = true), preTrusted = false,
            configure = realCrypto, provider = FakeCoreMediaProvider(additionalLibraryTracks = listOf(track)))
        try {
            listOf(phone, mac).forEach(::connectToTheSameSource)
            mac.actions.shell.connectActions!!.onStartPairingMode()
            runCurrent()
            enterCode(phone, mac, "mac")
            runCurrent()
            phone.updateLivePlayback { it.copy(currentTrack = track, queue = app.naviamp.domain.queue.PlaybackQueue(listOf(track), 0),
                // Paused keeps the fake target from starting audio; the sender's pause call is what is checked.
                playbackState = app.naviamp.domain.playback.PlaybackState.Paused) }
            val pausesBefore = effects.pauseCount
            phone.actions.shell.connectActions!!.onPostConnectPlayOnDevice()
            runCurrent()
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(NaviampConnectStatusText.QueueSentToDevice, phone.state.value.shell.connect.statusMessage?.text)
            assertEquals(pausesBefore + 1, effects.pauseCount, "The sender stops its own audio after the handoff")
        } finally { phone.close(); mac.close() }
    }

    private fun connectToTheSameSource(core: NaviampCore) {
        core.stateStore.updateShell { shell ->
            shell.copy(connectionSettings = shell.connectionSettings.copy(connection = shell.connectionSettings.connection.copy(
                connected = true,
                savedConnections = listOf(app.naviamp.ui.NaviampSavedConnectionUi(
                    id = "fixture", displayName = "Fixture source", serverUrl = "https://fixture.example.invalid",
                    username = "fixture-user", current = true)),
            )))
        }
    }

    @Test
    fun aSecondCodeInTheStillOpenPanelGoesToTheNewDevice() = runTest {
        val network = ConnectTestNetwork()
        val phone = connectPeer("phone", "mac", network, ConnectTestSource(configured = true), preTrusted = false, configure = realCrypto)
        val mac = connectPeer("mac", "phone", network, ConnectTestSource(configured = true, sourceId = "existing"),
            preTrusted = false, configure = realCrypto)
        val den = connectPeer("den", "phone", network, ConnectTestSource(configured = true, sourceId = "existing"),
            preTrusted = false, configure = realCrypto)
        try {
            mac.actions.shell.connectActions!!.onStartPairingMode()
            runCurrent()
            enterCode(phone, mac, "mac")
            runCurrent()
            phone.actions.shell.connectActions!!.onPostConnectDone()
            runCurrent()
            mac.actions.shell.connectActions!!.onStopPairingMode()
            den.actions.shell.connectActions!!.onStartPairingMode()
            runCurrent()
            // The panel is still open: type the next device's code without reopening it.
            phone.actions.shell.connectActions!!.onPairingCodeChanged(assertNotNull(den.state.value.shell.connect.pairingCode))
            runCurrent()
            assertEquals("den", phone.state.value.shell.connect.connectedTargetName)
        } finally { phone.close(); mac.close(); den.close() }
    }

    @Test
    fun aFailedCodeDoesNotPinTheNextCodeToThatDevice() = runTest {
        val network = ConnectTestNetwork()
        val phone = connectPeer("phone", "mac", network, ConnectTestSource(configured = true), preTrusted = false, configure = realCrypto)
        val mac = connectPeer("mac", "phone", network, preTrusted = false, configure = realCrypto)
        val den = connectPeer("den", "phone", network, preTrusted = false, configure = realCrypto)
        try {
            val actions = phone.actions.shell.connectActions!!
            mac.actions.shell.connectActions!!.onStartPairingMode()
            actions.onConnectScreenVisibilityChanged(true)
            runCurrent()
            // Only one device shows a code, so it is chosen automatically; this code is wrong.
            actions.onPairingCodeChanged("000000")
            runCurrent()
            assertNull(phone.state.value.shell.connect.connectedTargetName)
            mac.actions.shell.connectActions!!.onStopPairingMode()
            den.actions.shell.connectActions!!.onStartPairingMode()
            runCurrent()
            assertEquals(listOf("den"), phone.state.value.shell.connect.discoveredTargets.map { it.displayName })
            actions.onPairingCodeChanged(assertNotNull(den.state.value.shell.connect.pairingCode))
            runCurrent()
            assertEquals("den", phone.state.value.shell.connect.connectedTargetName,
                "The next code goes to the device that now shows a code")
        } finally { phone.close(); mac.close(); den.close() }
    }

    @Test
    fun aTargetThatNeverSendsWelcomeReleasesTheConnection() = runTest {
        val network = ConnectTestNetwork()
        val phone = connectPeer("phone", "mac", network, preTrusted = false,
            configure = { realCrypto(it).copy(pairingHandshakeTimeoutMillis = 1_000L) })
        // The code handshake completes (the target saves its trust), then it stops responding before its Welcome.
        val mac = connectPeer("mac", "phone", network, preTrusted = false, configure = { realCrypto(it).copy(
            trust = app.naviamp.app.NaviampConnectTrustRepository(app.naviamp.app.NaviampConnectTrustStorageEffect {
                network.silenced += "mac"
            }),
        ) })
        try {
            mac.actions.shell.connectActions!!.onStartPairingMode()
            runCurrent()
            enterCode(phone, mac, "mac")
            runCurrent()
            advanceTimeBy(1_001L)
            runCurrent()
            val connect = phone.state.value.shell.connect
            assertFalse(connect.codeEntryBusy, "A missing Welcome must not block code entry")
            assertNull(connect.postConnect)
            assertNull(connect.connectedTargetName)
            assertEquals(NaviampConnectStatusText.ThePairedDeviceDidNotStartACompatibleControlSession, connect.statusMessage?.text)
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
        val codes = generateSequence(100_000) { it + 1 }.map(Int::toString).iterator()
        val mac = connectPeer("mac", "phone-a", network, preTrusted = false,
            configure = { realCrypto(it).copy(newPairingCode = codes::next) })
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
