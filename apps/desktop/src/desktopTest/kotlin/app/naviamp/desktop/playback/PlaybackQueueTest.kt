package app.naviamp.desktop.playback

import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackQueueTest {
    @Test
    fun backToListsRecentTracksFirst() {
        val queue = PlaybackQueue(
            tracks = listOf(track("1"), track("2"), track("3")),
            currentIndex = 2,
        )

        assertEquals(listOf(track("2"), track("1")), queue.backTo())
    }

    @Test
    fun invalidCurrentIndexHasNoHistoryOrUpcomingTracks() {
        val queue = PlaybackQueue(
            tracks = listOf(track("1"), track("2")),
            currentIndex = -1,
        )

        assertEquals(emptyList(), queue.backTo())
        assertEquals(emptyList(), queue.upNext())
        assertEquals(false, queue.hasPrevious())
        assertEquals(false, queue.hasNext())
    }

    @Test
    fun jumpingToUpcomingKeepsSkippedTracksUpcoming() {
        val queue = PlaybackQueue(
            tracks = listOf(track("1"), track("2"), track("3"), track("4")),
            currentIndex = 0,
        ).jumpTo(2)

        assertEquals(track("3"), queue.tracks[queue.currentIndex])
        assertEquals(listOf(track("1")), queue.backTo())
        assertEquals(listOf(track("2"), track("4")), queue.upNext())
    }

    @Test
    fun jumpingToUpcomingCanSkipThroughQueue() {
        val queue = PlaybackQueue(
            tracks = listOf(track("1"), track("2"), track("3"), track("4")),
            currentIndex = 0,
        ).jumpTo(2, moveSelectedToCurrent = false)

        assertEquals(track("3"), queue.tracks[queue.currentIndex])
        assertEquals(listOf(track("2"), track("1")), queue.backTo())
        assertEquals(listOf(track("4")), queue.upNext())
    }

    @Test
    fun jumpingToHistoryMakesLaterHistoryUpcoming() {
        val queue = PlaybackQueue(
            tracks = listOf(track("1"), track("2"), track("3"), track("4")),
            currentIndex = 3,
        ).jumpTo(1)

        assertEquals(track("2"), queue.tracks[queue.currentIndex])
        assertEquals(listOf(track("1")), queue.backTo())
        assertEquals(listOf(track("3"), track("4")), queue.upNext())
    }

    private fun track(id: String): Track =
        Track(
            id = TrackId(id),
            title = "Track $id",
            artistName = "Artist",
            albumTitle = "Album",
            durationSeconds = 180,
            coverArtId = null,
            audioInfo = null,
            replayGain = null,
        )
}
