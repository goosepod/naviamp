package app.naviamp.domain.queue

import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayQueueTest {
    @Test
    fun emptyQueueHasNoCurrentTrack() {
        assertNull(PlayQueue().current)
    }

    @Test
    fun nextMovesToFollowingTrack() {
        val first = track("1", "First")
        val second = track("2", "Second")

        val queue = PlayQueue(listOf(first, second)).next()

        assertEquals(second, queue.current)
    }

    private fun track(id: String, title: String): Track =
        Track(
            id = TrackId(id),
            title = title,
            artistName = "Test Artist",
            albumTitle = "Test Album",
            durationSeconds = 180,
            coverArtId = null,
            replayGain = null,
        )
}

