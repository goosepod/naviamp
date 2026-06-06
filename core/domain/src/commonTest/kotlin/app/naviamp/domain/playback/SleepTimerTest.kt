package app.naviamp.domain.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SleepTimerTest {
    @Test
    fun durationTimerExpiresWhenRemainingTimeReachesZero() {
        val timer = sleepTimerState(
            request = SleepTimerRequest.DurationMinutes(15),
            nowEpochMillis = 1_000L,
            snapshot = snapshot(trackId = "one"),
            queueLastTrackId = "one",
        )

        assertEquals("Sleep in 15m 00s", sleepTimerDisplayLabel(timer, 1_000L))
        assertFalse(shouldExpireSleepTimer(timer, 1_000L + 14 * 60_000L, snapshot(trackId = "one")))
        assertTrue(shouldExpireSleepTimer(timer, 1_000L + 15 * 60_000L, snapshot(trackId = "one")))
    }

    @Test
    fun trackEndTimerExpiresWhenTheCurrentTrackIsEnding() {
        val timer = sleepTimerState(
            request = SleepTimerRequest.TrackEnd,
            nowEpochMillis = 1_000L,
            snapshot = snapshot(trackId = "one"),
            queueLastTrackId = "two",
        )

        assertFalse(shouldExpireSleepTimer(timer, 2_000L, snapshot(trackId = "one", position = 20.0, duration = 180.0)))
        assertTrue(shouldExpireSleepTimer(timer, 2_000L, snapshot(trackId = "one", position = 179.6, duration = 180.0)))
        assertTrue(shouldExpireSleepTimer(timer, 2_000L, snapshot(trackId = "two", position = 0.0, duration = 180.0)))
    }

    @Test
    fun albumEndTimerWaitsForTheLastTrackFromThatAlbum() {
        val timer = sleepTimerState(
            request = SleepTimerRequest.AlbumEnd,
            nowEpochMillis = 1_000L,
            snapshot = snapshot(trackId = "one", albumId = "album"),
            queueLastTrackId = "three",
        )

        assertFalse(
            shouldExpireSleepTimer(
                timer,
                2_000L,
                snapshot(trackId = "one", albumId = "album", nextAlbumId = "album", position = 179.6, duration = 180.0),
            ),
        )
        assertTrue(
            shouldExpireSleepTimer(
                timer,
                2_000L,
                snapshot(trackId = "two", albumId = "album", nextAlbumId = "other", position = 179.6, duration = 180.0),
            ),
        )
    }

    @Test
    fun queueEndTimerTargetsTheLastQueuedTrack() {
        val timer = sleepTimerState(
            request = SleepTimerRequest.QueueEnd,
            nowEpochMillis = 1_000L,
            snapshot = snapshot(trackId = "one"),
            queueLastTrackId = "three",
        )

        assertFalse(shouldExpireSleepTimer(timer, 2_000L, snapshot(trackId = "one", position = 179.6, duration = 180.0)))
        assertFalse(shouldExpireSleepTimer(timer, 2_000L, snapshot(trackId = "two", position = 179.6, duration = 180.0)))
        assertTrue(shouldExpireSleepTimer(timer, 2_000L, snapshot(trackId = "three", position = 179.6, duration = 180.0)))
    }

    private fun snapshot(
        trackId: String?,
        albumId: String? = "album",
        nextAlbumId: String? = null,
        position: Double? = null,
        duration: Double? = null,
    ): SleepTimerPlaybackSnapshot =
        SleepTimerPlaybackSnapshot(
            currentTrackId = trackId,
            currentAlbumId = albumId,
            nextTrackAlbumId = nextAlbumId,
            positionSeconds = position,
            durationSeconds = duration,
        )
}
