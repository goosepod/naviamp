package app.naviamp.domain.playback

import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.settings.PreviousButtonBehavior
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

    @Test
    fun previousCommandRestartsBeforeSelectingPreviousWhenConfigured() {
        val one = track("one")
        val two = track("two")
        val queue = PlaybackQueue(tracks = listOf(one, two), currentIndex = 1)

        assertEquals(
            PlaybackQueueNavigationCommand.RestartCurrent,
            PlaybackQueueManager().previousCommand(
                queue = queue,
                previousButtonBehavior = PreviousButtonBehavior.RestartThenPrevious,
                positionSeconds = 4.0,
                restartThresholdSeconds = 3.0,
            ),
        )
    }

    @Test
    fun previousCommandSelectsPreviousOnlyWhenAvailable() {
        val one = track("one")
        val two = track("two")
        val manager = PlaybackQueueManager()

        assertEquals(
            PlaybackQueueNavigationCommand.Previous,
            manager.previousCommand(
                queue = PlaybackQueue(tracks = listOf(one, two), currentIndex = 1),
                previousButtonBehavior = PreviousButtonBehavior.AlwaysPrevious,
                positionSeconds = 10.0,
                restartThresholdSeconds = 3.0,
            ),
        )
        assertEquals(
            PlaybackQueueNavigationCommand.None,
            manager.previousCommand(
                queue = PlaybackQueue(tracks = listOf(one, two), currentIndex = 0),
                previousButtonBehavior = PreviousButtonBehavior.AlwaysPrevious,
                positionSeconds = 10.0,
                restartThresholdSeconds = 3.0,
            ),
        )
    }

    @Test
    fun nextCommandAllowsQueueRepeatWrap() {
        val one = track("one")
        val two = track("two")
        val queue = PlaybackQueue(tracks = listOf(one, two), currentIndex = 1)
        val manager = PlaybackQueueManager()

        assertEquals(PlaybackQueueNavigationCommand.None, manager.nextCommand(queue, RepeatMode.Off))
        assertEquals(PlaybackQueueNavigationCommand.Next, manager.nextCommand(queue, RepeatMode.Queue))
    }

    @Test
    fun jumpCommandValidatesQueueIndex() {
        val one = track("one")
        val two = track("two")
        val queue = PlaybackQueue(tracks = listOf(one, two), currentIndex = 0)

        assertEquals(
            PlaybackQueueNavigationCommand.JumpTo(index = 1, moveSelectedToCurrent = false),
            PlaybackQueueManager().jumpCommand(
                queue = queue,
                index = 1,
                moveSelectedToCurrent = false,
            ),
        )
        assertEquals(
            PlaybackQueueNavigationCommand.None,
            PlaybackQueueManager().jumpCommand(queue, index = 0),
        )
        assertEquals(
            PlaybackQueueNavigationCommand.None,
            PlaybackQueueManager().jumpCommand(queue, index = 2),
        )
    }

    @Test
    fun planAdjacentActionUsesCurrentQueueAndRestartPolicy() {
        val one = track("one")
        val two = track("two")
        val queue = listOf(one, two)
        val manager = PlaybackQueueManager()

        assertEquals(
            PlaybackAdjacentAction.PlayTrack(track = two, queue = queue),
            manager.planAdjacentAction(
                currentTrack = one,
                activeQueue = queue,
                offset = 1,
                repeatMode = RepeatMode.Off,
                previousButtonBehavior = PreviousButtonBehavior.RestartThenPrevious,
                positionSeconds = 0.0,
                restartThresholdSeconds = 3.0,
            ),
        )
        assertEquals(
            PlaybackAdjacentAction.RestartCurrent,
            manager.planAdjacentAction(
                currentTrack = two,
                activeQueue = queue,
                offset = -1,
                repeatMode = RepeatMode.Off,
                previousButtonBehavior = PreviousButtonBehavior.RestartThenPrevious,
                positionSeconds = 4.0,
                restartThresholdSeconds = 3.0,
            ),
        )
    }

    @Test
    fun finishCurrentTrackAdvancesOrStopsFromRepeatMode() {
        val one = track("one")
        val two = track("two")
        val queue = PlaybackQueue(tracks = listOf(one, two), currentIndex = 0)
        val manager = PlaybackQueueManager()

        assertEquals(
            PlaybackQueueFinishedUpdate(
                queue = PlaybackQueue(tracks = listOf(one, two), currentIndex = 1),
                command = PlaybackQueueFinishedCommand.PlayNext,
            ),
            manager.finishCurrentTrack(queue, RepeatMode.Off),
        )
        assertEquals(
            PlaybackQueueFinishedUpdate(
                queue = PlaybackQueue(tracks = listOf(one, two), currentIndex = 0),
                command = PlaybackQueueFinishedCommand.ReplayCurrent,
            ),
            manager.finishCurrentTrack(queue, RepeatMode.Track),
        )
        assertEquals(
            PlaybackQueueFinishedUpdate(
                queue = PlaybackQueue(tracks = listOf(one, two), currentIndex = 1),
                command = PlaybackQueueFinishedCommand.None,
            ),
            manager.finishCurrentTrack(
                queue = PlaybackQueue(tracks = listOf(one, two), currentIndex = 1),
                repeatMode = RepeatMode.Off,
            ),
        )
    }

    @Test
    fun finishCurrentTrackWrapsQueueRepeat() {
        val one = track("one")
        val two = track("two")
        val queue = PlaybackQueue(tracks = listOf(one, two), currentIndex = 1)

        assertEquals(
            PlaybackQueueFinishedUpdate(
                queue = PlaybackQueue(tracks = listOf(one, two), currentIndex = 0),
                command = PlaybackQueueFinishedCommand.PlayNext,
            ),
            PlaybackQueueManager().finishCurrentTrack(queue, RepeatMode.Queue),
        )
    }

    @Test
    fun preparedNextIndexUsesSharedRepeatModePolicy() {
        val one = track("one")
        val two = track("two")
        val queue = PlaybackQueue(tracks = listOf(one, two), currentIndex = 1)
        val manager = PlaybackQueueManager()

        assertEquals(null, manager.nextPreparedQueueIndex(queue, RepeatMode.Off))
        assertEquals(0, manager.nextPreparedQueueIndex(queue, RepeatMode.Queue))
        assertEquals(1, manager.nextPreparedQueueIndex(queue, RepeatMode.Track))
        assertEquals(true, manager.shouldPrepareNextQueueIndex(preparedNextIndex = null, nextQueueIndex = 1))
        assertEquals(false, manager.shouldPrepareNextQueueIndex(preparedNextIndex = 1, nextQueueIndex = 1))
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
