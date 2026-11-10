package app.naviamp.ui

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals

class NaviampTooltipPositionTest {
    @Test fun measuredPopupIsCenteredAboveTheAnchor() {
        assertEquals(IntOffset(170, 156), position(IntRect(200, 200, 220, 220)))
    }
    @Test fun topEdgeFlipsBelowAndSideEdgesClamp() {
        assertEquals(IntOffset(4, 26), position(IntRect(0, 2, 20, 22)))
        assertEquals(IntOffset(416, 156), position(IntRect(480, 200, 500, 220)))
    }
    @Test fun movingAndResizingUseCurrentGeometry() {
        val anchor = IntRect(300, 200, 320, 220)
        assertEquals(IntOffset(270, 156), position(anchor))
        assertEquals(IntOffset(266, 156), position(anchor, IntSize(350, 300)))
    }
    @Test fun tinyViewportNeverProducesANegativeOrigin() {
        assertEquals(IntOffset.Zero, position(IntRect(0, 0, 20, 20), IntSize(10, 10)))
    }
    private fun position(anchor: IntRect, viewport: IntSize = IntSize(500, 300)) =
        naviampTooltipPosition(anchor, viewport, IntSize(80, 40), 4)
}
