package app.naviamp.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NaviampPopupRegistryTest {
    @Test fun nestedPopupKeepsNativeSurfacesBehindContentUntilLastDismissal() {
        val registry = NaviampPopupRegistry()
        assertFalse(registry.visible)
        val dismissMenu = registry.register()
        val dismissDialog = registry.register()
        dismissMenu()
        assertTrue(registry.visible)
        dismissMenu() // Repeated cleanup must not dismiss the remaining dialog.
        assertTrue(registry.visible)
        dismissDialog()
        assertFalse(registry.visible)
        val dismissReopenedMenu = registry.register()
        assertTrue(registry.visible)
        dismissReopenedMenu()
        assertFalse(registry.visible)
    }
}
