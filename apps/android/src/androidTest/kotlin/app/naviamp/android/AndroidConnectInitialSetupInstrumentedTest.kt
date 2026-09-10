package app.naviamp.android

import androidx.test.platform.app.InstrumentationRegistry
import app.naviamp.app.*
import app.naviamp.domain.connect.*
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.presentation.*
import app.naviamp.ui.*
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.test.*

/** Opt-in: actual TV Core, NSD, TCP, PAKE, Keystore and provider storage on a disposable emulator. */
class AndroidConnectInitialSetupInstrumentedTest {
    @Test fun codeEntryFinishesSetupAndReconnectDoesNotReplayIt(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("tvLocalFixture") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val target = withContext(Dispatchers.Main) { AndroidNaviampApplicationRuntime.get(context).core }
        check(target.state.value.shell.connectionSettings.connection.savedConnections.isEmpty()) {
            "Requires an empty disposable TV installation"
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val store = NaviampCoreStateStore()
        val source = SetupFixtureSource()
        var storedTrust: String? = null
        val trust = NaviampConnectTrustRepository(object : NaviampConnectTrustStorageEffect {
            override fun read() = storedTrust
            override fun write(value: String) { storedTrust = value }
        })
        val secrets = mutableMapOf<String, ByteArray>()
        val credentials = NaviampConnectSessionCredentialRepository(object : NaviampConnectSessionCredentialStorageEffect {
            override fun read(peerDeviceId: String) = secrets[peerDeviceId]?.copyOf()
            override fun write(peerDeviceId: String, value: ByteArray) { secrets[peerDeviceId] = value.copyOf() }
            override fun remove(peerDeviceId: String) { secrets.remove(peerDeviceId)?.fill(0) }
        })
        val controller = withContext(Dispatchers.Main) {
            store.updateShell { shell -> shell.copy(general = shell.general.copy(
                interfaceSettings = shell.general.interfaceSettings.copy(startPlayingOnLaunch = true, groupAlbumsByReleaseType = false))) }
            NaviampCoreConnectController(scope, store, NaviampCoreConnectServices(
                deviceCapabilities = setOf(NaviampConnectDeviceCapability.ControlPlayback),
                displayName = "Setup fixture controller", identity = SetupFixtureIdentity(),
                identityVerifier = JvmNaviampConnectIdentityVerifier,
                transport = JvmNaviampConnectTcpTransportFactory(),
                pake = BouncyCastleNaviampConnectPakeFactory,
                cipher = JvmNaviampConnectAuthenticatedCipherFactory,
                trust = trust,
                credentials = credentials, discovery = SetupFixtureDiscovery(context),
                newOpaqueId = { UUID.randomUUID().toString() }, newPairingCode = { "123456" },
                nowEpochMillis = System::currentTimeMillis,
            ), providerSessions = source, localConnection = NaviampCoreConnectionController(
                NaviampConnectionController(), store, source, source.initialInventory(),
            ))
        }
        val targetActions = assertNotNull(target.actions.shell.connectActions)
        suspend fun waitFor(description: String, predicate: () -> Boolean) {
            try { withTimeout(60_000) { while (!predicate()) delay(100) } }
            catch (failure: TimeoutCancellationException) {
                error("$description: controller=${store.state.value.shell.connect}; target=${target.state.value.shell.connect}")
            }
        }
        try {
            withContext(Dispatchers.Main) {
                targetActions.onStartPairingMode()
                controller.actions.onRefreshTargets()
            }
            waitFor("discover target") { store.state.value.shell.connect.discoveredTargets.isNotEmpty() }
            val code = assertNotNull(target.state.value.shell.connect.pairingCode)
            withContext(Dispatchers.Main) {
                controller.actions.onTargetSelected(store.state.value.shell.connect.discoveredTargets.first())
                controller.actions.onPairingCodeChanged(code)
                controller.actions.onSubmitPairingCode()
            }
            waitFor("automatic pairing and missing-password prompt") { store.state.value.shell.connect.needsProvisioningCredential }
            assertNull(target.state.value.shell.connect.pendingProvisioningConnectionName)
            assertNull(target.state.value.shell.connectionSettings.currentSourceId)
            withContext(Dispatchers.Main) { controller.actions.onSubmitProvisioningCredential("invalid") }
            waitFor("failed password validation") { !store.state.value.shell.connect.provisioningBusy }
            assertTrue(store.state.value.shell.connect.needsProvisioningCredential)
            assertNull(source.password)
            assertNull(target.state.value.shell.connectionSettings.currentSourceId)
            withContext(Dispatchers.Main) { controller.actions.onCancelProvisioningCredential() }
            assertFalse(store.state.value.shell.connect.needsProvisioningCredential)
            withContext(Dispatchers.Main) { controller.actions.onProvisionTarget() }
            waitFor("retry password prompt") { store.state.value.shell.connect.needsProvisioningCredential }
            source.serverUrl = "http://127.0.0.1:1"
            withContext(Dispatchers.Main) { controller.actions.onSubmitProvisioningCredential("fixture") }
            waitFor("target rejects an unreachable source") { !store.state.value.shell.connect.provisioningBusy }
            assertTrue(store.state.value.shell.connect.needsProvisioningCredential)
            assertNull(target.state.value.shell.connectionSettings.currentSourceId)
            assertFalse(target.state.value.shell.general.interfaceSettings.startPlayingOnLaunch)
            source.serverUrl = "http://127.0.0.1:18080"
            withContext(Dispatchers.Main) { controller.actions.onProvisionTarget() }
            waitFor("automatic source and settings provisioning") {
                store.state.value.shell.connect.statusMessage?.text == NaviampConnectStatusText.TvSetupCompletedSecurely
            }
            assertTrue(target.state.value.shell.connectionSettings.connection.connected)
            assertFalse(target.state.value.shell.general.interfaceSettings.startPlayingOnLaunch,
                "Device-specific startup behavior must stay local")
            assertFalse(target.state.value.shell.general.interfaceSettings.groupAlbumsByReleaseType)
            assertEquals("fixture", source.password)
            assertNull(target.state.value.shell.connect.pendingProvisioningConnectionName)
            val sourceId = assertNotNull(target.state.value.shell.connectionSettings.currentSourceId)
            val exportCount = source.exports
            withContext(Dispatchers.Main) { controller.actions.onStopControlling() }
            withContext(Dispatchers.Main) {
                controller.actions.onTrustedDeviceSelected(store.state.value.shell.connect.trustedDevices.single())
            }
            waitFor("trusted reconnect") { store.state.value.shell.connect.connectedTargetName != null }
            delay(1_000)
            assertEquals(sourceId, target.state.value.shell.connectionSettings.currentSourceId)
            assertEquals(exportCount, source.exports, "Trusted reconnect must not export credentials or replay setup")
            assertFalse(store.state.value.shell.connect.needsProvisioningCredential)
            assertFalse(store.state.value.shell.connect.provisioningBusy)
            withContext(Dispatchers.Main) {
                store.updateShell { shell -> shell.copy(general = shell.general.copy(
                    interfaceSettings = shell.general.interfaceSettings.copy(groupAlbumsByReleaseType = true))) }
                controller.actions.onProvisionTarget()
            }
            waitFor("later setup requires target approval") { target.state.value.shell.connect.pendingProvisioningConnectionName != null }
            assertFalse(target.state.value.shell.general.interfaceSettings.groupAlbumsByReleaseType)
            withContext(Dispatchers.Main) { targetActions.onRejectProvisioning() }
            waitFor("rejected later setup finishes") { !store.state.value.shell.connect.provisioningBusy }
            assertFalse(target.state.value.shell.general.interfaceSettings.groupAlbumsByReleaseType)
            assertEquals(sourceId, target.state.value.shell.connectionSettings.currentSourceId)
        } finally {
            withContext(Dispatchers.Main) { controller.close(); targetActions.onStopPairingMode() }
            scope.cancel()
            secrets.values.forEach { it.fill(0) }
        }
    }
}

private class SetupFixtureSource : NaviampCoreProviderSessionPort {
    var password: String? = null
    var serverUrl = "http://127.0.0.1:18080"
    var exports = 0
    private val inventory = NaviampCoreConnectionInventory(listOf(NaviampCoreSavedConnectionRecord(
        "fixture", "Setup fixture", "http://127.0.0.1:18080", "fixture")), "fixture")
    override fun initialInventory() = inventory
    override suspend fun editableConnection(id: String) = NaviampCoreEditableConnection(ConnectionFormState(
        displayName = "Setup fixture", serverUrl = serverUrl, username = "fixture",
        password = password.orEmpty(),
    ))
    override suspend fun currentProvisioningConnection(): NaviampCoreEditableConnection {
        exports++
        return editableConnection("fixture")
    }
    override suspend fun connect(request: NaviampCoreConnectionRequest, plan: NaviampConnectionAttemptPlan): NaviampCoreConnectedSession {
        val form = (request as NaviampCoreConnectionRequest.Form).form
        check(form.password == "fixture") { "Synthetic validation failure" }
        check(!plan.clearExistingPlayback && !plan.clearProviderData)
        password = form.password
        return NaviampCoreConnectedSession("fixture", "Setup fixture", "test", inventory)
    }
    override suspend fun deleteConnection(id: String) = inventory
    override suspend fun smartPlaylistProvider(password: String?) = null
    override suspend fun refreshActiveSession() = true
    override suspend fun persistActiveSession() = Unit
    override suspend fun clearActiveSession() { password = null }
}

private class SetupFixtureIdentity : NaviampConnectDeviceIdentityEffect {
    private val key = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()
    override fun loadOrCreate(): NaviampConnectDeviceIdentity {
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(key.public.encoded)
            .joinToString("") { "%02x".format(it) }
        return NaviampConnectDeviceIdentity(fingerprint.take(32), fingerprint,
            Base64.getEncoder().encodeToString(key.public.encoded))
    }
    override fun sign(payload: ByteArray): ByteArray = Signature.getInstance("SHA256withECDSA").run {
        initSign(key.private); update(payload); sign()
    }
}

/** Restrict the synthetic controller to this emulator's identity, even on a LAN with real TVs. */
private class SetupFixtureDiscovery(context: android.content.Context) : NaviampConnectDiscoveryEffect {
    private val native = AndroidNaviampConnectDiscoveryEffect(context)
    private val fingerprint = AndroidNaviampConnectDeviceIdentityEffect().loadOrCreate().identityFingerprint
    override fun start(listener: NaviampConnectDiscoveryListener): NaviampConnectDiscoveryStartResult =
        native.start(object : NaviampConnectDiscoveryListener {
            override fun onServiceResolved(service: NaviampConnectResolvedService) {
                if (service.textAttributes["fp"] == fingerprint) {
                    listener.onServiceResolved(service.copy(addresses = listOf("127.0.0.1")))
                }
            }
            override fun onServiceLost(serviceName: String) = listener.onServiceLost(serviceName)
            override fun onDiscoveryFailed(message: String) = listener.onDiscoveryFailed(message)
            override fun onPermissionDenied() = listener.onPermissionDenied()
        })
    override fun stop() = native.stop()
}
