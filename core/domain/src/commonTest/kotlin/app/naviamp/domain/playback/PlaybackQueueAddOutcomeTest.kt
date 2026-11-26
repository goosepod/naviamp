package app.naviamp.domain.playback

import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.queue.PlaybackQueue
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackQueueAddOutcomeTest {
    private val manager = PlaybackQueueMutationManager()
    private val queue = PlaybackQueue(listOf(track("a")), 0)

    @Test
    fun addsReportWhereTheTracksWent() {
        assertEquals(PlaybackQueueAddOutcome.AddedToQueue, manager.appendTracks(queue, listOf(track("b"))).outcome)
        assertEquals(PlaybackQueueAddOutcome.AddedToPlayNext, manager.playNextTracks(queue, listOf(track("b"))).outcome)
        assertEquals(PlaybackQueueAddOutcome.AddedToPlayNext, manager.playNextTrack(queue, track("b")).outcome)
    }

    @Test
    fun deduplicatedAndEmptyAddsAreDistinguished() {
        assertEquals(PlaybackQueueAddOutcome.AlreadyQueued,
            manager.appendTracks(queue, listOf(track("a")), deduplicateExisting = true).outcome)
        assertEquals(PlaybackQueueAddOutcome.AlreadyQueued,
            manager.playNextTracks(queue, listOf(track("a")), deduplicateExisting = true).outcome)
        assertEquals(PlaybackQueueAddOutcome.NothingFound, manager.appendTracks(queue, emptyList()).outcome)
    }

    private fun track(id: String) = Track(TrackId(id), "Track $id", artistName = "Artist", albumTitle = null,
        durationSeconds = null, coverArtId = null, audioInfo = null, replayGain = null)
}
