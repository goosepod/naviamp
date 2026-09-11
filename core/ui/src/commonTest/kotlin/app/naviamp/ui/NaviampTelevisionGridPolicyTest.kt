package app.naviamp.ui

import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NaviampTelevisionGridPolicyTest {
    @Test
    fun activeLyricsKeepTwoLinesOfContextAboveThem() {
        assertEquals(0, lyricsActiveLineScrollTarget(-1))
        assertEquals(0, lyricsActiveLineScrollTarget(0))
        assertEquals(0, lyricsActiveLineScrollTarget(2))
        assertEquals(1, lyricsActiveLineScrollTarget(3))
        assertEquals(7, lyricsActiveLineScrollTarget(9))
    }

    @Test
    fun waveformProgressUsesAContinuousClippingBoundary() {
        assertEquals(0f, waveformPlayedClipWidth(width = 800f, value = -1f))
        assertEquals(200f, waveformPlayedClipWidth(width = 800f, value = 0.25f))
        assertEquals(800f, waveformPlayedClipWidth(width = 800f, value = 2f))
    }

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
    fun televisionLibraryGroupsNumbersUnderOneShortcut() {
        val shortcuts = televisionLibraryShortcuts()

        assertEquals('#', shortcuts.first())
        assertEquals(('A'..'Z').toList(), shortcuts.drop(1))
    }

    @Test
    fun libraryShortcutScrollsOnlyWhenTheTargetIsNotVisible() {
        assertFalse(televisionLibraryShortcutRequiresScroll(targetIndex = 4, visibleItemIndices = 2..6))
        assertFalse(televisionLibraryShortcutRequiresScroll(targetIndex = 2, visibleItemIndices = 2..6))
        assertTrue(televisionLibraryShortcutRequiresScroll(targetIndex = 7, visibleItemIndices = 2..6))
        assertTrue(televisionLibraryShortcutRequiresScroll(targetIndex = 0, visibleItemIndices = emptyList()))
    }

    @Test
    fun libraryShortcutTargetsTheRequestedOrNextAvailableSection() {
        val titles = listOf("2Pac", "Air", "Can", "Massive Attack")

        assertEquals(0, televisionLibraryShortcutTarget(titles, '#'))
        assertEquals(1, televisionLibraryShortcutTarget(titles, 'A'))
        assertEquals(2, televisionLibraryShortcutTarget(titles, 'B'))
        assertNull(televisionLibraryShortcutTarget(titles, 'Z'))
        assertEquals('M', televisionLibrarySection("  massive attack"))
        assertEquals('#', televisionLibrarySection("2Pac"))
        assertEquals('#', televisionLibrarySection("!!!"))
    }

    @Test
    fun focusedHomeRailsKeepVerticalContextExceptAtTheTop() {
        assertEquals(0, televisionHomeSectionScrollOffset(sectionIndex = 0, contextInsetPx = 72))
        assertEquals(-72, televisionHomeSectionScrollOffset(sectionIndex = 1, contextInsetPx = 72))
        assertEquals(-72, televisionHomeSectionScrollOffset(sectionIndex = 4, contextInsetPx = 72))
        assertEquals(0, televisionHomeSectionScrollOffset(sectionIndex = 1, contextInsetPx = -10))
    }

    @Test
    fun homeConsumesOnlyRightAtTheEndOfARail() {
        assertTrue(televisionHomeConsumesEndOfRailKey(Key.DirectionRight))
        assertFalse(televisionHomeConsumesEndOfRailKey(Key.DirectionLeft))
        assertFalse(televisionHomeConsumesEndOfRailKey(Key.DirectionDown))
    }

    @Test
    fun verticalHomeNavigationTargetsTheAdjacentRail() {
        assertEquals(1, televisionHomeVerticalSectionTarget(0, 3, Key.DirectionDown))
        assertEquals(2, televisionHomeVerticalSectionTarget(1, 3, Key.DirectionDown))
        assertNull(televisionHomeVerticalSectionTarget(2, 3, Key.DirectionDown))
        assertEquals(1, televisionHomeVerticalSectionTarget(2, 3, Key.DirectionUp))
        assertEquals(0, televisionHomeVerticalSectionTarget(1, 3, Key.DirectionUp))
        assertNull(televisionHomeVerticalSectionTarget(0, 3, Key.DirectionUp))
        assertNull(televisionHomeVerticalSectionTarget(0, 3, Key.DirectionRight))
        assertNull(televisionHomeVerticalSectionTarget(-1, 3, Key.DirectionDown))
    }

    @Test
    fun homeRailsDefaultToFirstAndRememberTheirOwnValidItem() {
        assertEquals(0, televisionHomeRememberedItemIndex(null, itemCount = 6))
        assertEquals(2, televisionHomeRememberedItemIndex(2, itemCount = 6))
        assertEquals(5, televisionHomeRememberedItemIndex(8, itemCount = 6))
        assertEquals(0, televisionHomeRememberedItemIndex(2, itemCount = 0))
    }
}
