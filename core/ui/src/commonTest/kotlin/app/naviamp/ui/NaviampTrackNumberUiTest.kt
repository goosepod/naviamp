package app.naviamp.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class NaviampTrackNumberUiTest {
    @Test
    fun formatsTrackNumbersWithTrailingPeriod() {
        assertEquals("1.", trackNumberLabel(1))
        assertEquals("10.", trackNumberLabel(10))
        assertEquals("1000.", trackNumberLabel(1000))
    }

    @Test
    fun columnWidthIsSharedByEveryRowAndGrowsForLongerLists() {
        assertEquals(18.dp, trackNumberColumnWidth(1))
        assertEquals(18.dp, trackNumberColumnWidth(99))
        assertEquals(24.dp, trackNumberColumnWidth(100))
        assertEquals(30.dp, trackNumberColumnWidth(1000))
    }

    @Test
    fun navibeatMixIconsUseMostOfTheArtworkTile() {
        assertEquals(54f, navibeatMixIconSize(100.dp).value, absoluteTolerance = 0.001f)
    }

    @Test
    fun carouselForwardArrowAlignsTheItemPartiallyVisibleAtTheRightEdge() {
        assertEquals(
            276,
            homeCarouselScrollTarget(
                current = 0,
                viewport = 306,
                itemStride = 138,
                maximum = 690,
                forward = true,
            ),
        )
    }

    @Test
    fun carouselBackArrowFirstAlignsAPartialLeftItemThenMovesAViewport() {
        assertEquals(
            276,
            homeCarouselScrollTarget(310, 306, 138, 690, forward = false),
        )
        assertEquals(
            0,
            homeCarouselScrollTarget(276, 306, 138, 690, forward = false),
        )
    }

    @Test
    fun televisionCarouselFocusSnapsPartialRowsAndKeepsTheFocusedCardVisible() {
        assertEquals(
            138,
            homeCarouselFocusedItemScrollTarget(
                current = 310,
                viewport = 306,
                itemIndex = 3,
                itemWidth = 128,
                itemStride = 138,
                maximum = 690,
            ),
        )
        assertEquals(0, homeCarouselFocusedItemScrollTarget(0, 306, 0, 128, 138, 690))
        assertEquals(0, homeCarouselFocusedItemScrollTarget(0, 306, 1, 128, 138, 690))
        assertEquals(
            276,
            homeCarouselFocusedItemScrollTarget(
                current = 276,
                viewport = 306,
                itemIndex = 4,
                itemWidth = 128,
                itemStride = 138,
                maximum = 690,
            ),
        )
    }
}
