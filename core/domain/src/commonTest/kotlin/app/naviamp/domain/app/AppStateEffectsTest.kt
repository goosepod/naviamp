package app.naviamp.domain.app

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppStateEffectsTest {
    @Test
    fun storageStatsRefreshOnDiagnosticsSettingsAndDownloads() {
        assertTrue(shouldRefreshStorageStats(NaviampRoute.Home, diagnosticsVisible = true))
        assertTrue(shouldRefreshStorageStats(NaviampRoute.Settings))
        assertTrue(shouldRefreshStorageStats(NaviampRoute.Downloads))
        assertFalse(shouldRefreshStorageStats(NaviampRoute.Home))
    }
}
