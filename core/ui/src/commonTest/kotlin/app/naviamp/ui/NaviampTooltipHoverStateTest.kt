package app.naviamp.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull

class NaviampTooltipHoverStateTest {
    @Test fun hoverWaitsAndLeavingBeforeDelayCancels() {
        val pending = NaviampTooltipHoverState().hover(true, 0)
        assertFalse(pending.advance(449).visible)
        assertTrue(pending.advance(450).visible)
        assertFalse(pending.hover(false, 300).advance(1000).visible)
    }

    @Test fun popupPointerTransferDoesNotRecreateVisibleContent() {
        var state = NaviampTooltipHoverState().hover(true, 0).advance(450)
        repeat(20) { index ->
            val now = 500L + index * 500L
            state = state.hover(false, now).advance(now + 16)
            assertTrue(state.visible)
            state = state.hover(true, now + 32).advance(now + 200)
            assertTrue(state.visible)
            assertNull(state.deadlineMillis)
        }
    }

    @Test fun genuineExitHidesPromptlyAndNextHoverWaitsAgain() {
        val leaving = NaviampTooltipHoverState().hover(true, 0).advance(450).hover(false, 500)
        assertTrue(leaving.advance(599).visible)
        val hidden = leaving.advance(600)
        assertFalse(hidden.visible)
        assertFalse(hidden.hover(true, 700).advance(1149).visible)
        assertTrue(hidden.hover(true, 700).advance(1150).visible)
    }
}
