package app.naviamp.presentation

import app.naviamp.app.BouncyCastleNaviampConnectPakeFactory
import app.naviamp.app.JvmNaviampConnectAuthenticatedCipherFactory
import app.naviamp.ui.NaviampConnectStatusText
import app.naviamp.ui.NaviampConnectTransferAccountStatus
import app.naviamp.ui.NaviampConnectTransferOutcome
import kotlinx.coroutines.test.*
import kotlin.test.*

/** Full sign-in (#714) between two real Core instances over the authenticated JVM channel. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NaviampCoreConnectAccountTransferPeerTest {
    private val events = mutableListOf<NaviampCoreConnectTraceEvent>()
    private val realCrypto: (NaviampCoreConnectServices) -> NaviampCoreConnectServices = { it.copy(
        pake = BouncyCastleNaviampConnectPakeFactory,
        cipher = JvmNaviampConnectAuthenticatedCipherFactory,
        trace = events::add,
    ) }

    private fun TestScope.connectWithCode(phone: NaviampCore, tv: NaviampCore) {
        tv.actions.shell.connectActions!!.onStartPairingMode()
        runCurrent()
        val actions = phone.actions.shell.connectActions!!
        actions.onConnectScreenVisibilityChanged(true)
        runCurrent()
        actions.onTargetSelected(phone.state.value.shell.connect.discoveredTargets.single { it.displayName == "tv" })
        actions.onPairingCodeChanged(assertNotNull(tv.state.value.shell.connect.pairingCode))
        runCurrent()
    }

    @Test
    fun aPhoneWithThreeAccountsSignsInAFreshTvWithAllOfThemAndItsSettings() = runTest {
        val network = ConnectTestNetwork()
        val phoneAccounts = ConnectTestAccounts(listOf(
            testAccount("home", "https://home.example"),
            testAccount("keyed", "https://keyed.example", password = null, apiKey = "api-key"),
            testAccount("jelly", "https://jelly.example", providerId = "jellyfin"),
        ), active = "keyed")
        val tvAccounts = ConnectTestAccounts()
        val phone = connectPeer("phone", "tv", network, preTrusted = false, accounts = phoneAccounts, configure = realCrypto)
        val tv = connectPeer("tv", "phone", network, preTrusted = false, accounts = tvAccounts, configure = realCrypto)
        try {
            connectWithCode(phone, tv)
            val sheet = assertNotNull(phone.state.value.shell.connect.postConnect)
            assertTrue(sheet.signInSelected, "An empty device is signed in by default")
            assertFalse(sheet.pull)
            assertTrue(sheet.includeSettings, "Settings follow the accounts onto an empty device")
            phone.actions.shell.connectActions!!.onPostConnectDone()
            runCurrent()
            assertEquals(setOf("https://home.example", "https://keyed.example", "https://jelly.example"),
                tvAccounts.sources.map { it.baseUrl }.toSet())
            assertEquals("api-key", tvAccounts.source("https://keyed.example")?.token)
            assertEquals(tvAccounts.source("https://keyed.example")?.id, tvAccounts.activeId,
                "The sender's active account is active on the receiver")
            assertEquals(1, tvAccounts.appliedSettings.size)
            assertNull(phone.state.value.shell.connect.postConnect, "Everything arrived, so Done finishes")
            assertEquals(NaviampConnectStatusText.DeviceIsSignedIn, phone.state.value.shell.connect.statusMessage?.text)
            assertEquals(NaviampConnectStatusText.SignedInFromDevice, tv.state.value.shell.connect.statusMessage?.text)
            assertTrue(events.none { event -> listOf("pw-home", "api-key", "pw-jelly").any { it in event.logLine() } })
        } finally { phone.close(); tv.close() }
    }

    @Test
    fun aFreshPhonePullsTheAccountsOfAConfiguredTv() = runTest {
        val network = ConnectTestNetwork()
        val phoneAccounts = ConnectTestAccounts()
        val tvAccounts = ConnectTestAccounts(listOf(
            testAccount("living", "https://living.example"),
            testAccount("den", "https://den.example"),
        ), active = "den")
        val phone = connectPeer("phone", "tv", network, preTrusted = false, accounts = phoneAccounts, configure = realCrypto)
        val tv = connectPeer("tv", "phone", network, preTrusted = false, accounts = tvAccounts, configure = realCrypto)
        try {
            connectWithCode(phone, tv)
            val sheet = assertNotNull(phone.state.value.shell.connect.postConnect)
            assertTrue(sheet.pull, "A device without accounts signs itself in from one that has them")
            assertTrue(sheet.signInSelected)
            phone.actions.shell.connectActions!!.onPostConnectDone()
            runCurrent()
            assertEquals(setOf("https://living.example", "https://den.example"), phoneAccounts.sources.map { it.baseUrl }.toSet())
            assertEquals(phoneAccounts.source("https://den.example")?.id, phoneAccounts.activeId)
            assertEquals(listOf("pw-living", "pw-den").toSet(), phoneAccounts.validatedSecrets.toSet())
            assertEquals(2, tvAccounts.sources.size, "Serving accounts never changes the serving device")
            assertEquals(NaviampConnectStatusText.SignedInFromDevice, phone.state.value.shell.connect.statusMessage?.text)
            assertEquals(NaviampConnectStatusText.SentAccountsToDevice, tv.state.value.shell.connect.statusMessage?.text)
        } finally { phone.close(); tv.close() }
    }

    @Test
    fun aConfiguredReceiverKeepsItsAccountsAndOnlyTheMatchingCredentialChanges() = runTest {
        val network = ConnectTestNetwork()
        val phoneAccounts = ConnectTestAccounts(listOf(
            testAccount("home", "https://home.example", password = "new-home"),
            testAccount("work", "https://work.example"),
        ))
        val tvAccounts = ConnectTestAccounts(listOf(
            testAccount("tv-home", "https://home.example", name = "Living room server", password = "old-home",
                folders = listOf("jazz")),
            testAccount("tv-other", "https://other.example"),
        ), active = "tv-other")
        val phone = connectPeer("phone", "tv", network, preTrusted = false, accounts = phoneAccounts, configure = realCrypto)
        val tv = connectPeer("tv", "phone", network, preTrusted = false, accounts = tvAccounts, configure = realCrypto)
        try {
            connectWithCode(phone, tv)
            val actions = phone.actions.shell.connectActions!!
            val sheet = assertNotNull(phone.state.value.shell.connect.postConnect)
            assertFalse(sheet.signInSelected, "A configured device is not signed in by default")
            assertFalse(sheet.includeSettings)
            actions.onPostConnectSignInChanged(true)
            actions.onPostConnectDone()
            runCurrent()
            val home = assertNotNull(tvAccounts.source("https://home.example"))
            assertEquals("tv-home", home.id)
            assertEquals("new-home", home.password)
            assertEquals(listOf("jazz"), home.selectedMusicFolderIds, "The receiver keeps its library choice")
            assertEquals("Living room server", home.displayName)
            assertNotNull(tvAccounts.source("https://other.example"), "Existing accounts are never removed")
            assertNotNull(tvAccounts.source("https://work.example"))
            assertTrue(tvAccounts.appliedSettings.isEmpty(), "Settings stay unless chosen")
        } finally { phone.close(); tv.close() }
    }

    @Test
    fun anAccountTheReceiverRejectsKeepsTheSheetOpenWithEachOutcome() = runTest {
        val network = ConnectTestNetwork()
        val phoneAccounts = ConnectTestAccounts(listOf(
            testAccount("home", "https://home.example"),
            testAccount("stale", "https://stale.example", password = "stale-pw"),
        ))
        val tvAccounts = ConnectTestAccounts(rejectedSecrets = setOf("stale-pw"))
        val phone = connectPeer("phone", "tv", network, preTrusted = false, accounts = phoneAccounts, configure = realCrypto)
        val tv = connectPeer("tv", "phone", network, preTrusted = false, accounts = tvAccounts, configure = realCrypto)
        try {
            connectWithCode(phone, tv)
            val actions = phone.actions.shell.connectActions!!
            actions.onPostConnectDone()
            runCurrent()
            val sheet = assertNotNull(phone.state.value.shell.connect.postConnect)
            assertEquals(
                mapOf("home" to NaviampConnectTransferOutcome.Added, "stale" to NaviampConnectTransferOutcome.Failed),
                sheet.results.associate { it.displayName to it.outcome },
            )
            assertEquals(NaviampConnectStatusText.SomeAccountsNeedAttention, phone.state.value.shell.connect.statusMessage?.text)
            assertEquals(1, tvAccounts.sources.size)
            actions.onPostConnectDone()
            runCurrent()
            assertNull(phone.state.value.shell.connect.postConnect, "The next tap finishes without sending again")
            assertEquals(1, tvAccounts.sources.size)
        } finally { phone.close(); tv.close() }
    }

    @Test
    fun anAccountWithoutAStoredPasswordIsAskedForOnTheSendingDeviceOnly() = runTest {
        val network = ConnectTestNetwork()
        val phoneAccounts = ConnectTestAccounts(listOf(
            testAccount("home", "https://home.example"),
            testAccount("legacy", "https://legacy.example", password = null, token = "md5-session"),
            testAccount("cert", "https://cert.example").copy(tlsSettings = app.naviamp.domain.source.ConnectionTlsSettings(
                clientCertificateKeyStorePath = "/data/client.p12")),
        ))
        val tvAccounts = ConnectTestAccounts()
        val phone = connectPeer("phone", "tv", network, preTrusted = false, accounts = phoneAccounts, configure = realCrypto)
        val tv = connectPeer("tv", "phone", network, preTrusted = false, accounts = tvAccounts, configure = realCrypto)
        try {
            connectWithCode(phone, tv)
            val actions = phone.actions.shell.connectActions!!
            actions.onPostConnectChoose()
            runCurrent()
            val accounts = assertNotNull(phone.state.value.shell.connect.postConnect).accounts.associateBy { it.id }
            assertEquals(NaviampConnectTransferAccountStatus.NeedsPassword, accounts.getValue("legacy").status)
            assertEquals(NaviampConnectTransferAccountStatus.SetUpOnDevice, accounts.getValue("cert").status)
            assertEquals(NaviampConnectTransferAccountStatus.Ready, accounts.getValue("home").status)
            actions.onPostConnectAccountPassword("legacy", "typed-pw")
            actions.onPostConnectDone()
            runCurrent()
            assertEquals("typed-pw", tvAccounts.source("https://legacy.example")?.password)
            assertFalse("md5-session" in tvAccounts.validatedSecrets, "Session tokens never travel")
            val results = assertNotNull(phone.state.value.shell.connect.postConnect).results.associate { it.displayName to it.outcome }
            assertEquals(NaviampConnectTransferOutcome.SetUpOnDevice, results["cert"], "A certificate account is reported, not dropped")
            assertNull(tvAccounts.source("https://cert.example"))
        } finally { phone.close(); tv.close() }
    }

    @Test
    fun yourDevicesSendsTheAccountsAgainToATrustedDevice() = runTest {
        val network = ConnectTestNetwork()
        val phoneAccounts = ConnectTestAccounts(listOf(testAccount("home", "https://home.example")))
        val tvAccounts = ConnectTestAccounts()
        val phone = connectPeer("phone", "tv", network, preTrusted = false, accounts = phoneAccounts, configure = realCrypto)
        val tv = connectPeer("tv", "phone", network, preTrusted = false, accounts = tvAccounts, configure = realCrypto)
        try {
            connectWithCode(phone, tv)
            val actions = phone.actions.shell.connectActions!!
            actions.onPostConnectSignInChanged(false)
            actions.onPostConnectDone()
            runCurrent()
            assertTrue(tvAccounts.sources.isEmpty())
            phoneAccounts.sources += testAccount("work", "https://work.example")
            actions.onSendAccountsToDevice(phone.state.value.shell.connect.trustedDevices.single())
            runCurrent()
            val sheet = assertNotNull(phone.state.value.shell.connect.postConnect, "Send my accounts opens the sheet")
            assertTrue(sheet.signInSelected)
            actions.onPostConnectDone()
            runCurrent()
            assertEquals(setOf("https://home.example", "https://work.example"), tvAccounts.sources.map { it.baseUrl }.toSet())
        } finally { phone.close(); tv.close() }
    }
}
