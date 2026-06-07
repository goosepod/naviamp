package app.naviamp.domain.playback

import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackQueueManagerTest {
    @Test
    fun appendTracksStartsInactiveEmptyQueueAtFirstTrack() {
        val one = track("one")

        assertEquals(
            PlaybackQueueUpdate(
                queue = PlaybackQueue(tracks = listOf(one), currentIndex = 0),
                tracksChanged = true,
                status = "Added 1 track to queue.",
            ),
            PlaybackQueueManager().appendTracks(
                currentQueue = PlaybackQueue(),
                tracksToAdd = listOf(one),
            ),
        )
    }

    @Test
    fun appendTracksAppendsToExistingQueueAndKeepsCurrentIndex() {
        val one = track("one")
        val two = track("two")

        assertEquals(
            PlaybackQueueUpdate(
                queue = PlaybackQueue(tracks = listOf(one, two), currentIndex = 0),
                tracksChanged = true,
                status = "Added 1 track to queue.",
            ),
            PlaybackQueueManager().appendTracks(
                currentQueue = PlaybackQueue(tracks = listOf(one), currentIndex = 0),
                tracksToAdd = listOf(two),
            ),
        )
    }

    @Test
    fun appendTracksDeduplicatesExistingTracksWhenRequested() {
        val one = track("one")
        val two = track("two")

        assertEquals(
            PlaybackQueueUpdate(
                queue = PlaybackQueue(tracks = listOf(one, two), currentIndex = 0),
                tracksChanged = true,
                status = "Added 1 popular tracks to queue.",
            ),
            PlaybackQueueManager().appendTracks(
                currentQueue = PlaybackQueue(tracks = listOf(one), currentIndex = 0),
                tracksToAdd = listOf(one, two),
                label = "popular tracks",
                existingTracks = listOf(one),
                deduplicateExisting = true,
            ),
        )
    }

    @Test
    fun appendTracksReportsNoChangeForEmptyOrAlreadyQueuedTracks() {
        val one = track("one")
        val currentQueue = PlaybackQueue(tracks = listOf(one), currentIndex = 0)

        assertEquals(
            PlaybackQueueUpdate(
                queue = currentQueue,
                tracksChanged = false,
                status = "No tracks found.",
            ),
            PlaybackQueueManager().appendTracks(
                currentQueue = currentQueue,
                tracksToAdd = emptyList(),
            ),
        )
        assertEquals(
            PlaybackQueueUpdate(
                queue = currentQueue,
                tracksChanged = false,
                status = "Popular tracks are already in the queue.",
            ),
            PlaybackQueueManager().appendTracks(
                currentQueue = currentQueue,
                tracksToAdd = listOf(one),
                label = "popular tracks",
                existingTracks = listOf(one),
                deduplicateExisting = true,
            ),
        )
    }

    @Test
    fun cycleRepeatModeUsesSharedQueueOrder() {
        val manager = PlaybackQueueManager()

        assertEquals(RepeatMode.Queue, manager.cycleRepeatMode(RepeatMode.Off))
        assertEquals(RepeatMode.Track, manager.cycleRepeatMode(RepeatMode.Queue))
        assertEquals(RepeatMode.Off, manager.cycleRepeatMode(RepeatMode.Track))
        assertEquals(RepeatMode.Queue, nextRepeatMode(RepeatMode.Off))
    }

    @Test
    fun toggleUpcomingShuffleReturnsQueueAndSnapshotUpdates() {
        val one = track("one")
        val two = track("two")
        val three = track("three")
        val queue = PlaybackQueue(tracks = listOf(one, two, three), currentIndex = 0)
        val manager = PlaybackQueueManager()

        val shuffled = manager.toggleUpcomingShuffle(queue, shuffledSnapshot = null)

        assertEquals(true, shuffled.changed)
        assertEquals(listOf(two, three), shuffled.shuffledSnapshot)
        assertEquals(one, shuffled.queue.tracks.first())

        assertEquals(
            PlaybackShuffleUpdate(
                queue = queue,
                shuffledSnapshot = null,
                changed = true,
            ),
            manager.toggleUpcomingShuffle(shuffled.queue, shuffledSnapshot = shuffled.shuffledSnapshot),
        )
    }

    @Test
    fun toggleUpcomingShuffleReportsNoChangeWhenQueueCannotShuffle() {
        val one = track("one")
        val queue = PlaybackQueue(tracks = listOf(one), currentIndex = 0)

        assertEquals(
            PlaybackShuffleUpdate(
                queue = queue,
                shuffledSnapshot = null,
                changed = false,
            ),
            PlaybackQueueManager().toggleUpcomingShuffle(queue, shuffledSnapshot = null),
        )
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
