package app.naviamp.app

import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.queue.PlaybackQueue
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
    fun shuffleAndRestoreUseOneSharedSnapshot() {
        val first = track("first")
        val upcoming = listOf(track("second"), track("third"), track("fourth"))
        val initialQueue = PlaybackQueue(listOf(first) + upcoming, currentIndex = 0)
        val playback = NaviampLivePlaybackController(
            NaviampLivePlaybackState(queue = initialQueue),
        )
        val coordinator = NaviampPlaybackQueueCoordinator(playback)

        val shuffled = coordinator.toggleUpcomingShuffle(null)
        assertTrue(shuffled.changed)
        assertEquals(upcoming.toSet(), playback.state.value.queue.upNext().toSet())

        val restored = coordinator.toggleUpcomingShuffle(shuffled.shuffledSnapshot)
        assertTrue(restored.changed)
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
