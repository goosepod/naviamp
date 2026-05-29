package app.naviamp.domain.queue

import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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
    fun adjacentIndexMovesThroughQueue() {
        val queue = PlaybackQueue(
            tracks = listOf(track("1"), track("2"), track("3")),
            currentIndex = 1,
        )

        assertEquals(2, queue.nextIndex())
        assertEquals(0, queue.previousIndex())
    }

    @Test
    fun adjacentIndexCanWrapWithQueueRepeat() {
        val lastTrackQueue = PlaybackQueue(
            tracks = listOf(track("1"), track("2")),
            currentIndex = 1,
        )
        val firstTrackQueue = lastTrackQueue.copy(currentIndex = 0)

        assertEquals(null, lastTrackQueue.nextIndex(repeatMode = RepeatMode.Off))
        assertEquals(0, lastTrackQueue.nextIndex(repeatMode = RepeatMode.Queue))
        assertEquals(1, firstTrackQueue.previousIndex(repeatMode = RepeatMode.Queue))
        assertEquals(null, firstTrackQueue.previousIndex(repeatMode = RepeatMode.Queue, wrapQueue = false))
    }

    @Test
    fun adjacentIndexCanRepeatCurrentTrack() {
        val queue = PlaybackQueue(
            tracks = listOf(track("1"), track("2")),
            currentIndex = 0,
        )

        assertEquals(0, queue.nextIndex(repeatMode = RepeatMode.Track))
        assertEquals(1, queue.nextIndex(repeatMode = RepeatMode.Track, repeatTrack = false))
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

    @Test
    fun restoringUpcomingPreservesCurrentAndHistory() {
        val queue = PlaybackQueue(
            tracks = listOf(track("1"), track("2"), track("9"), track("8")),
            currentIndex = 1,
        ).restoreUpcoming(listOf(track("3"), track("4")))

        assertEquals(track("2"), queue.tracks[queue.currentIndex])
        assertEquals(listOf(track("1")), queue.backTo())
        assertEquals(listOf(track("3"), track("4")), queue.upNext())
    }

    @Test
    fun shuffleUpcomingReturnsOriginalUpcomingSnapshot() {
        val originalUpcoming = listOf(track("2"), track("3"), track("4"))
        val result = PlaybackQueue(
            tracks = listOf(track("1")) + originalUpcoming,
            currentIndex = 0,
        ).shuffleUpcoming()

        assertNotNull(result)
        val (queue, snapshot) = result
        assertEquals(originalUpcoming, snapshot)
        assertEquals(track("1"), queue.current)
        assertEquals(originalUpcoming.toSet(), queue.upNext().toSet())
    }

    @Test
    fun toggleUpcomingShuffleSwitchesBetweenShuffledAndRestoredQueue() {
        val originalUpcoming = listOf(track("2"), track("3"), track("4"))
        val queue = PlaybackQueue(
            tracks = listOf(track("1")) + originalUpcoming,
            currentIndex = 0,
        )

        val shuffled = assertNotNull(queue.toggleUpcomingShuffle(shuffledSnapshot = null))
        assertEquals(originalUpcoming, shuffled.shuffledSnapshot)
        assertEquals(originalUpcoming.toSet(), shuffled.queue.upNext().toSet())

        val restored = assertNotNull(shuffled.queue.toggleUpcomingShuffle(shuffled.shuffledSnapshot))
        assertEquals(null, restored.shuffledSnapshot)
        assertEquals(originalUpcoming, restored.queue.upNext())
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
