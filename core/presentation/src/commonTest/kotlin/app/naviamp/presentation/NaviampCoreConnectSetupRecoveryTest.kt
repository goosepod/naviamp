package app.naviamp.presentation

import app.naviamp.app.NaviampConnectionAttemptPlan
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.ui.NaviampConnectStatusText
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NaviampCoreConnectSetupRecoveryTest {
    @Test
    fun trustedDeviceSetupAppliesWithoutApproval() = runTest {
        val network = ConnectTestNetwork()
        val events = mutableListOf<NaviampCoreConnectTraceEvent>()
        val source = ConnectTestSource(configured = true)
        val destination = ConnectTestSource(configured = false, validationDelayMillis = 20_000)
        val phone = connectPeer("phone", "mac", network, source) { it.copy(trace = events::add) }
        val mac = connectPeer("mac", "phone", network, destination) { it.copy(trace = events::add) }
        try {
            runCurrent()
            val actions = phone.actions.shell.connectActions!!
            actions.onTrustedDeviceSelected(phone.state.value.shell.connect.trustedDevices.single())
            runCurrent()
            actions.onProvisionTarget()
            runCurrent()
            advanceTimeBy(16_000)
            runCurrent()
            assertEquals("mac", phone.state.value.shell.connect.connectedTargetName, "Validation must not block heartbeat replies")
            assertTrue(phone.state.value.shell.connect.provisioningBusy)
            advanceTimeBy(4_001)
            runCurrent()
            assertEquals(NaviampConnectStatusText.TvSetupCompletedSecurely, phone.state.value.shell.connect.statusMessage?.text)
            assertFalse(phone.state.value.shell.connect.needsProvisioningCredential)
            assertEquals(1, destination.connects, "A trusted device setup applies without approval on the target")
            assertEquals("fixture", destination.currentSourceId())
            assertEquals(1, source.exports)
            assertTrue(events.none { it.kind == NaviampCoreConnectTraceKind.SetupRejected })
            assertTrue(events.none { it.kind == NaviampCoreConnectTraceKind.HeartbeatOrWriteTimedOut })
            assertTrue(events.none { it.logLine().contains("fixture-password") || it.logLine().contains("example.invalid") })
        } finally { phone.close(); mac.close() }
    }

    @Test
    fun validationFailureStillOffersCredentialRecoveryAndPreservesTheTargetSource() = runTest {
        val network = ConnectTestNetwork()
        val source = ConnectTestSource(configured = true)
        val destination = ConnectTestSource(configured = true, sourceId = "existing", failValidation = true)
        val phone = connectPeer("phone", "mac", network, source)
        val mac = connectPeer("mac", "phone", network, destination)
        try {
            runCurrent()
            val actions = phone.actions.shell.connectActions!!
            actions.onTrustedDeviceSelected(phone.state.value.shell.connect.trustedDevices.single())
            runCurrent()
            actions.onProvisionTarget()
            runCurrent()
            runCurrent()
            assertEquals(NaviampConnectStatusText.CouldNotValidateThatConnectionCheckTheServerAndCredentialThenRetry,
                phone.state.value.shell.connect.statusMessage?.text)
            assertTrue(phone.state.value.shell.connect.needsProvisioningCredential)
            assertFalse(phone.state.value.shell.connect.provisioningBusy)
            assertEquals("existing", destination.currentSourceId())
            assertEquals("existing", mac.state.value.shell.connectionSettings.currentSourceId)
            assertEquals("mac", phone.state.value.shell.connect.connectedTargetName)
        } finally { phone.close(); mac.close() }
    }
}

internal class ConnectTestSource(
    configured: Boolean,
    private val sourceId: String = "fixture",
    private val validationDelayMillis: Long = 0,
    private val failValidation: Boolean = false,
) : NaviampCoreProviderSessionPort {
    private var inventory = if (configured) configuredInventory() else NaviampCoreConnectionInventory()
    var exports = 0
        private set
    var connects = 0
        private set
    private fun configuredInventory() = NaviampCoreConnectionInventory(listOf(NaviampCoreSavedConnectionRecord(
        sourceId, "Fixture source", "https://$sourceId.example.invalid", "fixture-user")), sourceId)
    override fun initialInventory() = inventory
    override suspend fun editableConnection(id: String) = NaviampCoreEditableConnection(ConnectionFormState(
        displayName = "Fixture source", serverUrl = "https://$sourceId.example.invalid", username = "fixture-user",
        password = "fixture-password",
    ))
    override suspend fun currentProvisioningConnection(): NaviampCoreEditableConnection {
        exports++
        return editableConnection(sourceId)
    }
    override suspend fun connect(request: NaviampCoreConnectionRequest, plan: NaviampConnectionAttemptPlan): NaviampCoreConnectedSession {
        connects++
        delay(validationDelayMillis)
        check(!failValidation) { "Synthetic validation failure" }
        inventory = configuredInventory()
        return NaviampCoreConnectedSession(sourceId, "Fixture source", "test", inventory)
    }
    override suspend fun deleteConnection(id: String) = inventory
    override suspend fun smartPlaylistProvider(password: String?) = null
    override suspend fun refreshActiveSession() = true
    override suspend fun persistActiveSession() = Unit
    override suspend fun clearActiveSession() = Unit
}
