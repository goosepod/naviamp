package app.naviamp.domain.library

import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryPagingTest {
    @Test
    fun nextLibraryLimitOnlyGrowsWhenVisibleRowsReachCurrentLimit() {
        assertEquals(
            50,
            nextLibraryLimit(
                visibleCount = 49,
                currentLimit = 50,
                pageSize = 50,
            ),
        )
        assertEquals(
            100,
            nextLibraryLimit(
                visibleCount = 50,
                currentLimit = 50,
                pageSize = 50,
            ),
        )
    }

    @Test
    fun libraryLimitForOffsetRoundsUpToContainingPage() {
        assertEquals(50, libraryLimitForOffset(offset = 0, pageSize = 50))
        assertEquals(50, libraryLimitForOffset(offset = 49, pageSize = 50))
        assertEquals(100, libraryLimitForOffset(offset = 50, pageSize = 50))
    }
}
