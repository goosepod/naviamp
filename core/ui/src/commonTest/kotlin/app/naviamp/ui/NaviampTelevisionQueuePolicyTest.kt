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
        assertNull(naviampRepeatIconCenterText(NaviampRepeatMode.Off))
        assertEquals("A", naviampRepeatIconCenterText(NaviampRepeatMode.Queue))
        assertEquals("1", naviampRepeatIconCenterText(NaviampRepeatMode.Track))
    }

    @Test
    fun liveQueueShowsTheOtherSavedInternetRadioStations() {
        val stations = listOf(
            NaviampNowPlayingItemUi("one", "One", "Internet radio"),
            NaviampNowPlayingItemUi("two", "Two", "Internet radio"),
            NaviampNowPlayingItemUi("three", "Three", "Internet radio"),
        )
        val nowPlaying = NowPlayingUi(
            id = "two",
            title = "Current stream title",
            subtitle = "Two",
            stateLabel = "Playing",
            isLive = true,
            radioStations = stations,
            upNext = listOf(queueItem(4, "Stale music queue item")),
        )

        assertEquals(
            listOf("one", "three"),
            televisionNowPlayingQueueItems(nowPlaying).map { it.id },
        )
        assertEquals(true, televisionNowPlayingQueueAvailable(nowPlaying))
    }

    @Test
    fun trackQueueContinuesToShowUpcomingTracks() {
        val upcoming = listOf(queueItem(2, "Two"), queueItem(3, "Three"))
        val nowPlaying = NowPlayingUi(
            id = "track",
            title = "Track",
            subtitle = "Artist",
            stateLabel = "Playing",
            radioStations = listOf(NaviampNowPlayingItemUi("radio", "Radio", "Internet radio")),
            upNext = upcoming,
        )

        assertEquals(upcoming, televisionNowPlayingQueueItems(nowPlaying))
        assertEquals(false, televisionNowPlayingQueueAvailable(nowPlaying))
    }

    private fun queueItem(index: Int, title: String): NaviampNowPlayingItemUi =
        NaviampNowPlayingItemUi(id = nowPlayingQueueItemId(index), title = title, subtitle = "Artist")
}
