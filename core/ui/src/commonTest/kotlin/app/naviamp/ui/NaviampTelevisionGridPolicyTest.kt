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
        assertEquals(5, televisionGridColumnCount(888.dp))
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
}
