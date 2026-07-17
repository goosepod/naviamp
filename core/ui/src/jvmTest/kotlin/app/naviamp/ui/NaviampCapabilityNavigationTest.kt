package app.naviamp.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NaviampCapabilityNavigationTest {
    @Test
    fun downloadsRouteFollowsSharedCapabilityDecision() {
        assertTrue(SharedRoute.Downloads in sharedBottomNavigationRoutes(supportsDownloads = true))
        assertFalse(SharedRoute.Downloads in sharedBottomNavigationRoutes(supportsDownloads = false))
    }
}
