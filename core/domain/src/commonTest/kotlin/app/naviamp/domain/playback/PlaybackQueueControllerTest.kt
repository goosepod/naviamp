package app.naviamp.domain.playback

import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PlaybackQueueControllerTest {
    @Test
    fun startRestoreAndClearManageQueueAndSession() {
        val controller = PlaybackQueueController()
        val tracks = listOf(track("one"), track("two"))

        val started = assertNotNull(controller.start(tracks, index = 1))
        assertEquals(PlaybackQueue(tracks, currentIndex = 1), started.queue)
        assertEquals(1, started.sessionId)
        assertEquals(1, controller.playbackSessionId)

        assertEquals(true, controller.restore(tracks, index = 0))
        assertEquals(PlaybackQueue(tracks, currentIndex = 0), controller.queue)
        assertEquals(2, controller.playbackSessionId)

        controller.clear()
        assertEquals(PlaybackQueue(), controller.queue)
        assertEquals(3, controller.playbackSessionId)
    }

    @Test
    fun queueMutationsReturnChangedQueue() {
        val controller = PlaybackQueueController()
        val one = track("one")
        val two = track("two")
        val updatedOne = one.copy(title = "Updated")

        controller.start(listOf(one), index = 0)
        assertEquals(
            PlaybackQueue(listOf(one, two), currentIndex = 0),
            controller.appendTracks(listOf(two)),
        )
        assertEquals(
            PlaybackQueue(listOf(updatedOne, two), currentIndex = 0),
            controller.updateTrack(updatedOne),
        )
        assertNull(controller.updateTrack(track("missing")))
    }

    @Test
    fun replaceQueueCanSyncExternalPlatformStateWithoutStartingPlayback() {
        val queue = PlaybackQueue(listOf(track("one")), currentIndex = 0)
        val controller = PlaybackQueueController()

        controller.replaceQueue(queue)

        assertEquals(queue, controller.queue)
        assertEquals(0, controller.playbackSessionId)
    }

    @Test
    fun navigationSelectionsAdvanceSessionForUserNavigation() {
        val tracks = listOf(track("one"), track("two"))
        val controller = PlaybackQueueController()
        controller.start(tracks, index = 0)

        val next = assertNotNull(controller.next())
        assertEquals(2, next.sessionId)
        assertEquals(1, next.queue.currentIndex)

        val previous = assertNotNull(controller.previous())
        assertEquals(3, previous.sessionId)
        assertEquals(0, previous.queue.currentIndex)

        controller.setRepeatMode(RepeatMode.Queue)
        assertNull(controller.previous())
        assertEquals(3, controller.playbackSessionId)

        val wrappedPrevious = assertNotNull(controller.adjacent(offset = -1))
        assertEquals(4, wrappedPrevious.sessionId)
        assertEquals(1, wrappedPrevious.queue.currentIndex)
    }

    @Test
    fun finishedSelectionKeepsCurrentSession() {
        val tracks = listOf(track("one"), track("two"))
        val controller = PlaybackQueueController()
        controller.start(tracks, index = 0)

        val finished = assertNotNull(controller.finishedSelection())
        assertEquals(1, finished.sessionId)
        assertEquals(1, finished.queue.currentIndex)
        assertEquals(1, controller.playbackSessionId)
    }

    @Test
    fun preparedNextAndShuffleAreTrackedWithQueueChanges() {
        val tracks = listOf(track("one"), track("two"), track("three"))
        val controller = PlaybackQueueController()
        controller.start(tracks, index = 0)

        assertEquals(1, controller.nextGaplessQueueIndex())
        assertEquals(true, controller.shouldPrepareNext(1))
        controller.markPreparedNext(1)
        assertEquals(false, controller.shouldPrepareNext(1))

        val shuffled = assertNotNull(controller.toggleUpcomingShuffle(null))
        assertEquals(null, controller.preparedNextIndex)
        assertEquals(tracks.drop(1), shuffled.shuffledSnapshot)
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
