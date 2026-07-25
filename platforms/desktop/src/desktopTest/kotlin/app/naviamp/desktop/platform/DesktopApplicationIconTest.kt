package app.naviamp.desktop.platform

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopApplicationIconTest {
    @Test
    fun loadsPackagedWindowIcon() {
        val icon = assertNotNull(desktopApplicationIcon())

        assertTrue(icon.getWidth(null) > 0)
        assertTrue(icon.getHeight(null) > 0)
    }
}
