package app.naviamp.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class NaviampResponsiveActionRowTest {
    @Test
    fun narrowRowsReserveOneSlotForOverflow() {
        assertEquals(3, responsiveVisibleActionCount(176f, actionCount = 7))
    }

    @Test
    fun wideRowsShowEveryActionWithoutOverflow() {
        assertEquals(7, responsiveVisibleActionCount(400f, actionCount = 7))
    }

    @Test
    fun extremelyNarrowRowsStillReserveOverflow() {
        assertEquals(0, responsiveVisibleActionCount(30f, actionCount = 3))
    }
}
