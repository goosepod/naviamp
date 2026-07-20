package app.naviamp.app

import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.playback.PlaybackQueueFinishedCommand
import app.naviamp.domain.playback.PlaybackQueueNavigationCommand
import app.naviamp.domain.playback.PlaybackQueueMutationUpdate
import app.naviamp.domain.settings.PreviousButtonBehavior
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NaviampPlaybackQueueCoordinatorTest {
    @Test
    fun lifecycleDecisionsUpdateTheSharedQueueSnapshot() {
        val first = track("first")
        val second = track("second")
        val playback = NaviampLivePlaybackController()
        val coordinator = NaviampPlaybackQueueCoordinator(playback)

        val started = coordinator.startQueue(listOf(first, second), index = 1)
        assertTrue(started.changed)
        assertEquals(second, playback.state.value.queue.current)

        val replacement = PlaybackQueue(listOf(second, first), currentIndex = 0, playNextCount = 8)
        assertTrue(coordinator.replaceQueue(replacement).changed)
        assertEquals(replacement, playback.state.value.queue)

        val restored = coordinator.restoreQueue(replacement)
        assertTrue(restored.changed)
        assertEquals(1, restored.queue.playNextCount)
        assertEquals(restored.queue, playback.state.value.queue)

        assertTrue(coordinator.clearQueue().changed)
        assertEquals(PlaybackQueue(), playback.state.value.queue)
    }

    @Test
    fun invalidLifecycleRequestsDoNotReplaceTheVisibleQueue() {
        val first = track("first")
        val initialQueue = PlaybackQueue(listOf(first), currentIndex = 0)
        val playback = NaviampLivePlaybackController(
            NaviampLivePlaybackState(queue = initialQueue),
        )
        val coordinator = NaviampPlaybackQueueCoordinator(playback)

        assertFalse(coordinator.startQueue(emptyList(), index = 0).changed)
        assertFalse(coordinator.restoreQueue(PlaybackQueue()).changed)
        assertEquals(initialQueue, playback.state.value.queue)
    }

    @Test
    fun boundedMutationsUpdateTheSharedQueueSnapshot() {
        val first = track("first")
        val second = track("second")
        val next = track("next")
        val appended = track("appended")
        val playback = NaviampLivePlaybackController(
            NaviampLivePlaybackState(queue = PlaybackQueue(listOf(first, second), currentIndex = 0)),
        )
        val coordinator = NaviampPlaybackQueueCoordinator(playback)

        assertTrue(coordinator.playNextTracks(listOf(next)).tracksChanged)
        assertEquals(listOf(first, next, second), playback.state.value.queue.tracks)
        assertEquals(1, playback.state.value.queue.playNextCount)

        assertTrue(coordinator.appendTracks(listOf(appended)).tracksChanged)
        assertEquals(listOf(first, next, second, appended), playback.state.value.queue.tracks)

        assertTrue(coordinator.removeAt(2).changed)
        assertEquals(listOf(first, next, appended), playback.state.value.queue.tracks)

        assertTrue(coordinator.selectIndex(2).changed)
        assertEquals(appended, playback.state.value.queue.current)
    }

    @Test
    fun remainingVisibleMutationsUpdateTheSharedQueueSnapshot() {
        val first = track("first")
        val second = track("second")
        val third = track("third")
        val replacement = track("replacement")
        val playback = NaviampLivePlaybackController(
            NaviampLivePlaybackState(
                queue = PlaybackQueue(listOf(first, second, third), currentIndex = 0),
            ),
        )
        val coordinator = NaviampPlaybackQueueCoordinator(playback)

        assertTrue(coordinator.moveToNext(2).changed)
        assertEquals(listOf(first, third, second), playback.state.value.queue.tracks)
        assertEquals(1, playback.state.value.queue.playNextCount)

        val updatedThird = third.copy(title = "Updated third")
        assertTrue(coordinator.updateTrack(updatedThird).changed)
        assertEquals(updatedThird, playback.state.value.queue.tracks[1])

        assertTrue(coordinator.replaceUpcomingTracks(first, listOf(replacement)).changed)
        assertEquals(listOf(first, updatedThird, replacement), playback.state.value.queue.tracks)

        assertTrue(coordinator.clearUpcoming().changed)
        assertEquals(listOf(first), playback.state.value.queue.tracks)
    }

    @Test
    fun commandControllerMirrorsOnlyChangedUserMutationsToThePlatform() {
        val first = track("first")
        val second = track("second")
        val third = track("third")
        val playback = NaviampLivePlaybackController(
            NaviampLivePlaybackState(
                queue = PlaybackQueue(listOf(first, second, third), currentIndex = 0),
            ),
        )
        val applied = mutableListOf<PlaybackQueueMutationUpdate>()
        val commands = NaviampPlaybackQueueCommandController(
            queue = NaviampPlaybackQueueCoordinator(playback),
            execution = NaviampPlaybackQueueMutationExecution(applied::add),
        )

        assertFalse(commands.removeAt(99).changed)
        assertTrue(applied.isEmpty())

        assertTrue(commands.moveToNext(2).changed)
        assertEquals(listOf(first, third, second), applied.single().queue.tracks)
        assertTrue(applied.single().clearPreparedNext)

        assertTrue(commands.removeAt(1).changed)
        assertEquals(listOf(first, second), applied.last().queue.tracks)

        assertTrue(commands.clearUpcoming().changed)
        assertEquals(listOf(first), applied.last().queue.tracks)
        assertEquals(playback.state.value.queue, applied.last().queue)
    }

    @Test
    fun repeatCommandCyclesSharedStateBeforeMirroringToPlatform() {
        val playback = NaviampLivePlaybackController()
        val applied = mutableListOf<RepeatMode>()
        val commands = NaviampPlaybackRepeatCommandController(
            queue = NaviampPlaybackQueueCoordinator(playback),
            execution = NaviampPlaybackRepeatModeExecution(applied::add),
        )

        assertEquals(RepeatMode.Queue, commands.cycle())
        assertEquals(RepeatMode.Queue, playback.state.value.repeatMode)
        assertEquals(listOf(RepeatMode.Queue), applied)

        assertEquals(RepeatMode.Track, commands.cycle())
        assertEquals(RepeatMode.Track, playback.state.value.repeatMode)
        assertEquals(listOf(RepeatMode.Queue, RepeatMode.Track), applied)
    }

    @Test
    fun shuffleAndRestoreUseOneSharedSnapshot() {
        val first = track("first")
        val upcoming = listOf(track("second"), track("third"), track("fourth"))
        val initialQueue = PlaybackQueue(listOf(first) + upcoming, currentIndex = 0)
        val playback = NaviampLivePlaybackController(
            NaviampLivePlaybackState(queue = initialQueue),
        )
        val coordinator = NaviampPlaybackQueueCoordinator(playback)

        val shuffled = coordinator.toggleUpcomingShuffle()
        assertTrue(shuffled.changed)
        assertEquals(upcoming.toSet(), playback.state.value.queue.upNext().toSet())
        assertEquals(shuffled.shuffledSnapshot, playback.state.value.shuffledUpNextSnapshot)

        val restored = coordinator.toggleUpcomingShuffle()
        assertTrue(restored.changed)
        assertEquals(initialQueue, playback.state.value.queue)
        assertEquals(null, playback.state.value.shuffledUpNextSnapshot)
    }

    @Test
    fun adjacentAndFinishedTransitionsUseSharedRepeatState() {
        val first = track("first")
        val second = track("second")
        val playback = NaviampLivePlaybackController(
            NaviampLivePlaybackState(
                queue = PlaybackQueue(listOf(first, second), currentIndex = 0),
                progress = app.naviamp.domain.playback.PlaybackProgress(
                    positionSeconds = 8.0,
                    durationSeconds = 180.0,
                ),
            ),
        )
        val coordinator = NaviampPlaybackQueueCoordinator(playback)

        assertEquals(PlaybackQueueNavigationCommand.Next, coordinator.nextCommand())
        assertTrue(coordinator.selectNext().changed)
        assertEquals(second, playback.state.value.queue.current)

        assertEquals(RepeatMode.Queue, coordinator.cycleRepeatMode())
        assertTrue(coordinator.selectNext().changed)
        assertEquals(first, playback.state.value.queue.current)

        assertEquals(
            PlaybackQueueNavigationCommand.RestartCurrent,
            coordinator.previousCommand(
                previousButtonBehavior = PreviousButtonBehavior.RestartThenPrevious,
            ),
        )

        val finished = coordinator.finishCurrentTrack(removePlayedTracksFromQueue = true)
        assertEquals(PlaybackQueueFinishedCommand.PlayNext, finished.command)
        assertEquals(second, playback.state.value.queue.current)
    }

    @Test
    fun repeatTrackFinishedTransitionKeepsTheCurrentQueue() {
        val first = track("first")
        val initialQueue = PlaybackQueue(listOf(first), currentIndex = 0)
        val playback = NaviampLivePlaybackController(
            NaviampLivePlaybackState(queue = initialQueue, repeatMode = RepeatMode.Track),
        )
        val coordinator = NaviampPlaybackQueueCoordinator(playback)

        val finished = coordinator.finishCurrentTrack()

        assertEquals(PlaybackQueueFinishedCommand.ReplayCurrent, finished.command)
        assertEquals(initialQueue, playback.state.value.queue)
    }

    @Test
    fun rejectedMutationsLeaveTheSharedSnapshotUntouched() {
        val first = track("first")
        val initialQueue = PlaybackQueue(listOf(first), currentIndex = 0)
        val playback = NaviampLivePlaybackController(
            NaviampLivePlaybackState(queue = initialQueue),
        )
        val coordinator = NaviampPlaybackQueueCoordinator(playback)

        assertFalse(coordinator.appendTracks(emptyList()).tracksChanged)
        assertFalse(coordinator.removeAt(10).changed)
        assertFalse(coordinator.selectIndex(0).changed)
        assertEquals(initialQueue, playback.state.value.queue)
    }

    @Test
    fun appendCanDeduplicateAgainstTheVisibleQueue() {
        val first = track("first")
        val playback = NaviampLivePlaybackController(
            NaviampLivePlaybackState(queue = PlaybackQueue(listOf(first), currentIndex = 0)),
        )
        val coordinator = NaviampPlaybackQueueCoordinator(playback)

        val update = coordinator.appendTracks(
            tracksToAdd = listOf(first),
            existingTracks = playback.state.value.queue.tracks,
            deduplicateExisting = true,
        )

        assertFalse(update.tracksChanged)
        assertEquals(listOf(first), playback.state.value.queue.tracks)
    }

    @Test
    fun generatedRadioBatchesApplyOnlyForTheCurrentRequest() {
        val seed = track("seed")
        val existing = track("existing")
        val fetched = track("fetched")
        val playback = NaviampLivePlaybackController(
            NaviampLivePlaybackState(queue = PlaybackQueue(listOf(seed, existing), currentIndex = 0)),
        )
        val coordinator = NaviampPlaybackQueueCoordinator(playback)

        val stale = coordinator.appendGeneratedRadioTracks(
            seedTrack = seed,
            fetchedTracks = listOf(existing, fetched),
            requestIsCurrent = false,
        )
        assertFalse(stale.tracksChanged)
        assertEquals(listOf(seed, existing), playback.state.value.queue.tracks)

        val applied = coordinator.appendGeneratedRadioTracks(
            seedTrack = seed,
            fetchedTracks = listOf(existing, fetched),
            requestIsCurrent = true,
        )
        assertTrue(applied.tracksChanged)
        assertEquals(listOf(seed, existing, fetched), playback.state.value.queue.tracks)
    }

    @Test
    fun generatedRadioUpcomingAndSonicContinuationUseTheSharedQueueSnapshot() {
        val history = track("history")
        val current = track("current")
        val oldUpcoming = track("old-upcoming")
        val radioTrack = track("radio")
        val sonicTrack = track("sonic")
        val playback = NaviampLivePlaybackController(
            NaviampLivePlaybackState(
                queue = PlaybackQueue(listOf(history, current, oldUpcoming), currentIndex = 1),
            ),
        )
        val coordinator = NaviampPlaybackQueueCoordinator(playback)

        assertTrue(
            coordinator.replaceGeneratedRadioUpcomingTracks(
                currentTrack = current,
                fetchedTracks = listOf(radioTrack),
                requestIsCurrent = true,
            ).changed,
        )
        assertEquals(listOf(history, current, radioTrack), playback.state.value.queue.tracks)

        val sonic = coordinator.appendSonicContinuationTracks(listOf(radioTrack, sonicTrack))
        assertTrue(sonic.tracksChanged)
        assertEquals(listOf(history, current, radioTrack, sonicTrack), playback.state.value.queue.tracks)
    }

    private fun track(id: String) = Track(
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
