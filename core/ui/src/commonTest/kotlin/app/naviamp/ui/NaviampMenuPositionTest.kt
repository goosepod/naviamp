package app.naviamp.ui

import androidx.compose.ui.unit.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NaviampMenuPositionTest {
    @Test fun menuUsesAnchorThenFlipsWhenItWouldLeaveTheViewport() {
        val window = IntSize(1000, 740)
        val content = IntSize(220, 200)
        assertEquals(IntOffset(100, 140), naviampMenuPosition(IntRect(100, 100, 140, 140),
            window, content, LayoutDirection.Ltr, IntOffset.Zero, 48))
        assertEquals(IntOffset(740, 400), naviampMenuPosition(IntRect(920, 600, 960, 640),
            window, content, LayoutDirection.Ltr, IntOffset.Zero, 48))
    }

    @Test fun rtlOffsetsFollowTheStartEdge() {
        assertEquals(IntOffset(212, 147), naviampMenuPosition(IntRect(400, 100, 440, 140),
            IntSize(1000, 740), IntSize(220, 200), LayoutDirection.Rtl, IntOffset(8, 7), 48))
    }

    @Test fun smallOrResizedWindowsNeverProduceNegativePlacement() {
        for (direction in LayoutDirection.entries) {
            val position = naviampMenuPosition(IntRect(920, 600, 960, 640), IntSize(160, 100),
                IntSize(220, 200), direction, IntOffset.Zero, 48)
            assertTrue(position.x >= 0 && position.y >= 0)
        }
    }
}
