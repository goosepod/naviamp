package app.naviamp.domain.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QueueBrowsePolicyTest {
    @Test
    fun pagesQueueFromCurrentTrackAndWraps() {
        val page = queueBrowsePage(queueSize = 7, currentIndex = 5, requestedPage = 0, pageSize = 4)
        assertEquals(listOf(5, 6, 0, 1), page.indexedItems)
        assertNull(page.previousPage)
        assertEquals(1, page.nextPage)
        assertEquals(1, page.firstOrdinal)
        assertEquals(4, page.lastOrdinal)
    }

    @Test
    fun clampsPastLastPage() {
        val page = queueBrowsePage(queueSize = 5, currentIndex = -1, requestedPage = 99, pageSize = 3)
        assertEquals(listOf(3, 4), page.indexedItems)
        assertEquals(0, page.previousPage)
        assertNull(page.nextPage)
        assertEquals(4, page.firstOrdinal)
        assertEquals(5, page.lastOrdinal)
    }
}
