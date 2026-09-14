package app.naviamp.presentation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import app.naviamp.app.*
import kotlinx.coroutines.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NaviampCoreOwnershipTest {
    @Test fun removingBorrowedWindowKeepsConnectAlive() = runComposeUiTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val discovery = Discovery()
        val core = NaviampCore.create(scope, services(discovery))
        val visible = mutableStateOf(true)
        try {
            setContent { if (visible.value) NaviampCoreApp(core) }
            waitForIdle()
            runOnIdle { core.actions.shell.connectActions!!.onRefreshTargets() }
            val stops = discovery.stops
            runOnIdle { visible.value = false }
            waitForIdle()
            assertEquals(stops, discovery.stops)
            runOnIdle { visible.value = true }
            waitForIdle()
            assertEquals(stops, discovery.stops)
            core.close()
            assertTrue(discovery.stops > stops)
        } finally { core.close(); scope.cancel() }
    }

    @Test fun removingOwningCompositionClosesConnect() = runComposeUiTest {
        val discovery = Discovery()
        val services = services(discovery)
        val visible = mutableStateOf(true)
        var core: NaviampCore? = null
        setContent { if (visible.value) core = rememberNaviampCore(services) }
        waitForIdle()
        runOnIdle { core!!.actions.shell.connectActions!!.onRefreshTargets() }
        val stops = discovery.stops
        runOnIdle { visible.value = false }
        waitForIdle()
        assertTrue(discovery.stops > stops)
    }

    private class Discovery : NaviampConnectDiscoveryEffect {
        var stops = 0
        override fun start(listener: NaviampConnectDiscoveryListener) = NaviampConnectDiscoveryStartResult.Started
        override fun stop() { stops++ }
    }

    private fun services(discovery: Discovery) = fakeCoreServices().copy(connect = NaviampCoreConnectServices(
        deviceCapabilities = NaviampCoreBidirectionalConnectCapabilities,
        displayName = "Fixture", identity = FakeIdentity, identityVerifier = FakeIdentityVerifier,
        transport = UnusedTransportFactory,
        pake = UnusedPakeFactory, cipher = UnusedCipherFactory,
        trust = NaviampConnectTrustRepository(NaviampConnectTrustStorageEffect {}),
        discovery = discovery, newOpaqueId = { "fixture" }, newPairingCode = { "123456" }, nowEpochMillis = { 0L },
    ))
}
