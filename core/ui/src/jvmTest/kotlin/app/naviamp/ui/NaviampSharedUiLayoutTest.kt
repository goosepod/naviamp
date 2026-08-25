package app.naviamp.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NaviampSharedUiLayoutTest {
    @Test
    fun connectionEditorKeepsSettingsOwnedScrollOnlyOnSettingsRoute() {
        assertTrue(
            sharedRouteCanUseOwnScroll(
                editingConnection = true,
                selectedRoute = SharedRoute.Settings,
            ),
        )
        assertFalse(
            sharedRouteCanUseOwnScroll(
                editingConnection = true,
                selectedRoute = SharedRoute.Home,
            ),
        )
        assertTrue(
            sharedRouteCanUseOwnScroll(
                editingConnection = false,
                selectedRoute = SharedRoute.Home,
            ),
        )
    }
}
