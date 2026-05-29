package app.naviamp.domain.settings

import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.queue.PlaybackQueue
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackSessionMappingTest {
    @Test
    fun restoredTrackSessionBuildsQueueAndProgressFromSavedSession() {
        val session = PlaybackSessionSettings.fromTracks(
            tracks = listOf(track("one"), track("two", durationSeconds = 240)),
            currentIndex = 1,
            positionSeconds = 62.0,
        )

        val restored = session?.restoredTrackSession()

        assertEquals(1, restored?.currentIndex)
        assertEquals(TrackId("two"), restored?.currentTrack?.id)
        assertEquals(
            PlaybackQueue(tracks = session?.toTracks().orEmpty(), currentIndex = 1),
            restored?.playbackQueue,
        )
        assertEquals(
            PlaybackProgress(positionSeconds = 62.0, durationSeconds = 240.0),
            restored?.playbackProgress,
        )
    }

    @Test
    fun restoredTrackSessionRejectsInvalidCurrentIndex() {
        val session = PlaybackSessionSettings(
            tracks = listOf(SavedTrack.fromTrack(track("one"))),
            currentIndex = 4,
            positionSeconds = 12.0,
        )

        assertEquals(null, session.restoredTrackSession())
        assertEquals(PlaybackQueue(), session.restoredPlaybackQueue())
    }

    @Test
    fun playbackSessionFromQueueRejectsInvalidQueueAndDropsNonPositivePosition() {
        assertEquals(null, playbackSessionFromQueue(PlaybackQueue()))

        val session = playbackSessionFromQueue(
            queue = PlaybackQueue(tracks = listOf(track("one")), currentIndex = 0),
            positionSeconds = 0.0,
        )

        assertEquals(null, session?.positionSeconds)
    }

    @Test
    fun playbackSessionFromCurrentTrackFallsBackToSingleTrackQueue() {
        val currentTrack = track("current")
        val session = playbackSessionFromCurrentTrack(
            currentTrack = currentTrack,
            queue = PlaybackQueue(tracks = listOf(track("other")), currentIndex = 0),
            positionSeconds = 12.0,
        )

        assertEquals(listOf(currentTrack), session?.toTracks())
        assertEquals(0, session?.currentIndex)
        assertEquals(12.0, session?.positionSeconds)
    }

    @Test
    fun adjacentTrackSessionMovesWithinBoundsAndClearsPosition() {
        val session = PlaybackSessionSettings.fromTracks(
            tracks = listOf(track("one"), track("two"), track("three")),
            currentIndex = 1,
            positionSeconds = 62.0,
        )

        val next = session?.adjacentTrackSession(1)
        val previous = session?.adjacentTrackSession(-1)
        val clamped = session?.adjacentTrackSession(99)

        assertEquals(2, next?.currentIndex)
        assertEquals(null, next?.positionSeconds)
        assertEquals(0, previous?.currentIndex)
        assertEquals(2, clamped?.currentIndex)
    }

    private fun track(
        id: String,
        durationSeconds: Int = 180,
    ): Track =
        Track(
            id = TrackId(id),
            title = "Track $id",
            artistName = "Artist",
            albumTitle = "Album",
            durationSeconds = durationSeconds,
            coverArtId = null,
            audioInfo = null,
            replayGain = null,
        )
}
