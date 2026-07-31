package app.naviamp.app

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NaviampConnectivityMonitorTest {
    @Test
    fun preservesOnlineFirstBehaviorWhenAHostCannotInspectTheNetwork() {
        val unknown = naviampConnectivityMonitor(networkAvailable = { error("unavailable") })
        val explicitlyOffline = naviampConnectivityMonitor(networkAvailable = { false })

        assertTrue(unknown.currentSnapshot().available)
        assertFalse(explicitlyOffline.currentSnapshot().available)
    }

    @Test
    fun supportsAFailClosedHostWithoutDuplicatingFailurePolicy() {
        val monitor = naviampConnectivityMonitor(
            networkAvailable = { error("unavailable") },
            availableWhenUnknown = false,
        )

        assertFalse(monitor.currentSnapshot().available)
    }
}
