package app.naviamp.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NaviampTelevisionQueuePolicyTest {
    @Test
    fun reorderTracksTheSpecificQueueOccurrenceAndAbsoluteDestination() {
        val items = listOf(
            queueItem(index = 4, title = "Duplicate"),
            queueItem(index = 5, title = "Middle"),
            queueItem(index = 6, title = "Duplicate"),
        )

        val started = requireNotNull(televisionQueueBeginReorder(items, itemIndex = 2))
        val moved = televisionQueueMoveReorder(televisionQueueMoveReorder(started, -1), -1)

        assertEquals(6, moved.sourceQueueIndex)
        assertEquals(4, moved.destinationQueueIndex)
        assertEquals(listOf("queue:6", "queue:4", "queue:5"), moved.items.map { it.id })
    }

    @Test
    fun reorderStopsAtUpcomingListEdgesAndRejectsUnresolvedRows() {
        val items = listOf(queueItem(2, "Two"), queueItem(3, "Three"))
        val first = requireNotNull(televisionQueueBeginReorder(items, 0))
        val last = requireNotNull(televisionQueueBeginReorder(items, 1))

        assertEquals(first, televisionQueueMoveReorder(first, -1))
        assertEquals(last, televisionQueueMoveReorder(last, 1))
        assertNull(televisionQueueBeginReorder(listOf(NaviampNowPlayingItemUi("track", "Track", "Artist")), 0))
    }

    @Test
    fun repeatModeLabelsMatchTheSharedThreeStateCycle() {
        assertEquals("Repeat off", televisionRepeatModeDescription(NaviampRepeatMode.Off))
        assertEquals("Repeat all", televisionRepeatModeDescription(NaviampRepeatMode.Queue))
        assertEquals("Repeat one", televisionRepeatModeDescription(NaviampRepeatMode.Track))
    }

    private fun queueItem(index: Int, title: String): NaviampNowPlayingItemUi =
        NaviampNowPlayingItemUi(id = nowPlayingQueueItemId(index), title = title, subtitle = "Artist")
}
