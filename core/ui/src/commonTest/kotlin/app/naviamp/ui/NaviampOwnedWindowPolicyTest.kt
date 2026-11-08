package app.naviamp.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NaviampOwnedWindowPolicyTest {
    @Test fun menusIgnoreInitialInactiveStateAndStayOpenForOwnedFocusTransfers() {
        val activation = NaviampMenuActivation()
        assertFalse(activation.shouldDismiss(false))
        assertFalse(activation.shouldDismiss(true))
        assertFalse(activation.shouldDismiss(true))
        assertTrue(activation.shouldDismiss(false))
    }
    @Test fun ownedOverlaysNeverRequestGlobalAlwaysOnTop() {
        assertFalse(NaviampOwnedWindowPolicy.alwaysOnTop)
        assertTrue(NaviampOwnedWindowPolicy.followsOwnerStack)
    }
}
