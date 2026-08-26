package app.naviamp.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NaviampTelevisionGridPolicyTest {
    @Test
    fun rightAdvancesInRowMajorOrder() {
        assertEquals(1, televisionGridRightTarget(currentIndex = 0, itemCount = 12))
        assertEquals(5, televisionGridRightTarget(currentIndex = 4, itemCount = 12))
        assertEquals(10, televisionGridRightTarget(currentIndex = 9, itemCount = 12))
    }

    @Test
    fun rightStopsAtTheFinalItemOrInvalidInput() {
        assertNull(televisionGridRightTarget(currentIndex = 11, itemCount = 12))
        assertNull(televisionGridRightTarget(currentIndex = -1, itemCount = 12))
        assertNull(televisionGridRightTarget(currentIndex = 0, itemCount = 0))
    }

    @Test
    fun columnCountMatchesTheCardsThatFitInTheTelevisionViewport() {
        assertEquals(6, televisionGridColumnCount(888.dp))
        assertEquals(1, televisionGridColumnCount(100.dp))
    }

    @Test
    fun horizontalItemsShareOneVerticalScrollAnchor() {
        assertEquals(0, televisionGridRowStart(itemIndex = 0, columnCount = 5))
        assertEquals(0, televisionGridRowStart(itemIndex = 4, columnCount = 5))
        assertEquals(5, televisionGridRowStart(itemIndex = 5, columnCount = 5))
        assertEquals(5, televisionGridRowStart(itemIndex = 9, columnCount = 5))
        assertNull(televisionGridRowStart(itemIndex = -1, columnCount = 5))
        assertNull(televisionGridRowStart(itemIndex = 0, columnCount = 0))
    }

    @Test
    fun homeRightEdgeAdvancesToTheNextRailAndStopsAtTheLastRail() {
        assertEquals(1, televisionHomeRightEdgeTarget(sectionIndex = 0, sectionCount = 3))
        assertEquals(2, televisionHomeRightEdgeTarget(sectionIndex = 1, sectionCount = 3))
        assertNull(televisionHomeRightEdgeTarget(sectionIndex = 2, sectionCount = 3))
        assertNull(televisionHomeRightEdgeTarget(sectionIndex = -1, sectionCount = 3))
    }

    @Test
    fun focusedHomeRailsKeepVerticalContextExceptAtTheTop() {
        assertEquals(0, televisionHomeSectionScrollOffset(sectionIndex = 0, contextInsetPx = 72))
        assertEquals(-72, televisionHomeSectionScrollOffset(sectionIndex = 1, contextInsetPx = 72))
        assertEquals(-72, televisionHomeSectionScrollOffset(sectionIndex = 4, contextInsetPx = 72))
        assertEquals(0, televisionHomeSectionScrollOffset(sectionIndex = 1, contextInsetPx = -10))
    }
}
