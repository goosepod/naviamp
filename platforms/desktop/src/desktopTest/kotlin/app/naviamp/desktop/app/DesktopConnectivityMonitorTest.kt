package app.naviamp.desktop

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopConnectivityMonitorTest {
    @Test
    fun mapsTheJvmProbeToTheSharedSnapshot() {
        assertTrue(DesktopConnectivityMonitor { true }.currentSnapshot().available)
        assertFalse(DesktopConnectivityMonitor { false }.currentSnapshot().available)
    }

    @Test
    fun preservesOnlineFirstBehaviorWhenTheProbeFails() {
        val snapshot = DesktopConnectivityMonitor { error("Interface inspection denied") }
            .currentSnapshot()

        assertTrue(snapshot.available)
        assertFalse(snapshot.mobileData)
    }
}
