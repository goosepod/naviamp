package app.naviamp.domain.queue

import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PlaybackQueueTest {
    @Test
    fun repeatedPlayNextTracksPreserveInsertionOrder() {
        val queue = PlaybackQueue(
            tracks = listOf(track("current"), track("context-1"), track("context-2")),
            currentIndex = 0,
        )
            .playNextTracks(listOf(track("priority-1")))
            .playNextTracks(listOf(track("priority-2")))

        assertEquals(
            listOf(track("current"), track("priority-1"), track("priority-2"), track("context-1"), track("context-2")),
            queue.tracks,
        )
        assertEquals(listOf(track("priority-1"), track("priority-2")), queue.playNext())
        assertEquals(listOf(track("context-1"), track("context-2")), queue.contextUpNext())
    }

    @Test
    fun advancingConsumesOnlyTheStartedPriorityTrack() {
        val queue = PlaybackQueue(
            tracks = listOf(track("current"), track("priority-1"), track("priority-2"), track("context")),
            currentIndex = 0,
            playNextCount = 2,
        ).next()

        assertEquals(track("priority-1"), queue.current)
        assertEquals(listOf(track("priority-2")), queue.playNext())
        assertEquals(listOf(track("context")), queue.contextUpNext())
    }

    @Test
    fun shuffleLeavesPriorityTracksInOrder() {
        val priority = listOf(track("priority-1"), track("priority-2"))
        val context = listOf(track("context-1"), track("context-2"), track("context-3"))
        val result = PlaybackQueue(
            tracks = listOf(track("current")) + priority + context,
            currentIndex = 0,
            playNextCount = priority.size,
        ).shuffleUpcoming()

        assertNotNull(result)
        assertEquals(priority, result.first.playNext())
        assertEquals(context.toSet(), result.first.contextUpNext().toSet())
        assertEquals(context, result.second)
    }

    @Test
    fun selectingAContextTrackMovesRemainingPriorityTracksAfterIt() {
        val queue = PlaybackQueue(
            tracks = listOf(track("current"), track("priority-1"), track("priority-2"), track("context-1"), track("context-2")),
            currentIndex = 0,
            playNextCount = 2,
        ).jumpTo(3)

        assertEquals(track("context-1"), queue.current)
        assertEquals(listOf(track("priority-1"), track("priority-2")), queue.playNext())
        assertEquals(listOf(track("context-2")), queue.contextUpNext())
    }

    @Test
    fun replacingUpcomingTracksKeepsTheCurrentDuplicateOccurrence() {
        val duplicate = track("duplicate")
        val queue = PlaybackQueue(
            tracks = listOf(track("before"), duplicate, track("middle"), duplicate, track("old-upcoming")),
            currentIndex = 3,
        ).replaceUpcomingTracks(
            currentTrack = duplicate,
            upcomingTracks = listOf(track("replacement")),
        )

        assertEquals(
            listOf(track("before"), duplicate, track("middle"), duplicate, track("replacement")),
            queue.tracks,
        )
        assertEquals(3, queue.currentIndex)
        assertEquals(track("replacement"), queue.upNext().single())
    }

    @Test
    fun resolvingTrackOccurrencePrefersTheValidatedQueueIndex() {
        val duplicate = track("duplicate")
        val tracks = listOf(track("before"), duplicate, track("middle"), duplicate)

        assertEquals(3, resolveTrackOccurrenceIndex(tracks, duplicate, preferredIndex = 3))
        assertEquals(1, resolveTrackOccurrenceIndex(tracks, duplicate, preferredIndex = 2))
    }

    @Test
    fun removingPriorityTrackShrinksPriorityBlock() {
        val queue = PlaybackQueue(
            tracks = listOf(track("current"), track("priority-1"), track("priority-2"), track("context")),
            currentIndex = 0,
            playNextCount = 2,
        ).removeAt(1)

        assertEquals(listOf(track("priority-2")), queue.playNext())
        assertEquals(listOf(track("context")), queue.contextUpNext())
    }

    @Test
    fun movingExistingPriorityTrackNextPromotesItToTheFront() {
        val queue = PlaybackQueue(
            tracks = listOf(track("current"), track("priority-1"), track("priority-2"), track("priority-3"), track("context")),
            currentIndex = 0,
            playNextCount = 3,
        ).moveToNext(2)

        assertEquals(
            listOf(track("priority-2"), track("priority-1"), track("priority-3")),
            queue.playNext(),
        )
        assertEquals(listOf(track("context")), queue.contextUpNext())
    }

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
    fun removeAtRemovesUpcomingTrackWithoutChangingCurrent() {
        val queue = PlaybackQueue(
            tracks = listOf(track("1"), track("2"), track("3")),
            currentIndex = 0,
        ).removeAt(1)

        assertEquals(track("1"), queue.current)
        assertEquals(listOf(track("3")), queue.upNext())
    }

    @Test
    fun removeAtAdjustsCurrentIndexWhenRemovingHistory() {
        val queue = PlaybackQueue(
            tracks = listOf(track("1"), track("2"), track("3")),
            currentIndex = 2,
        ).removeAt(0)

        assertEquals(1, queue.currentIndex)
        assertEquals(track("3"), queue.current)
        assertEquals(listOf(track("2")), queue.backTo())
    }

    @Test
    fun clearUpcomingKeepsCurrentAndHistory() {
        val queue = PlaybackQueue(
            tracks = listOf(track("1"), track("2"), track("3"), track("4")),
            currentIndex = 2,
        ).clearUpcoming()

        assertEquals(track("3"), queue.current)
        assertEquals(listOf(track("2"), track("1")), queue.backTo())
        assertEquals(emptyList(), queue.upNext())
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

    @Test
    fun moveUpcomingTrackToNextReordersWithoutDuplicating() {
        val queue = PlaybackQueue(
            tracks = listOf(track("1"), track("2"), track("3"), track("4")),
            currentIndex = 0,
        ).moveToNext(3)

        assertEquals(listOf(track("1"), track("4"), track("2"), track("3")), queue.tracks)
        assertEquals(0, queue.currentIndex)
        assertEquals(1, queue.playNextCount)
    }

    @Test
    fun moveHistoryTrackToNextPreservesCurrentTrack() {
        val queue = PlaybackQueue(
            tracks = listOf(track("1"), track("2"), track("3"), track("4")),
            currentIndex = 2,
        ).moveToNext(0)

        assertEquals(listOf(track("2"), track("3"), track("1"), track("4")), queue.tracks)
        assertEquals(track("3"), queue.current)
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
