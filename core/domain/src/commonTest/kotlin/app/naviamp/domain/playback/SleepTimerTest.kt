package app.naviamp.domain.playback

import kotlin.math.pow
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

    @Test
    fun fadeFollowsPlayingTimeAndEndsInSilenceAtTheDeadline() {
        val timer = sleepTimerState(
            request = SleepTimerRequest.DurationMinutes(20),
            nowEpochMillis = 0L,
            snapshot = snapshot(trackId = "one"),
            queueLastTrackId = "one",
            fadeMinutes = 3,
            playing = true,
        )

        assertEquals(1f, sleepTimerFadeVolumeFactor(timer, 17 * 60_000L))
        val quarter = sleepTimerFadeVolumeFactor(timer, 17 * 60_000L + 45_000L)
        val half = sleepTimerFadeVolumeFactor(timer, 18 * 60_000L + 30_000L)
        assertTrue(quarter in half..1f && half > 0f)
        assertFalse(shouldExpireSleepTimer(timer, 20 * 60_000L - 1L, snapshot(trackId = "one")))
        assertEquals(0f, sleepTimerFadeVolumeFactor(timer, 20 * 60_000L))
        assertTrue(shouldExpireSleepTimer(timer, 20 * 60_000L, snapshot(trackId = "one")))

        val paused = timer.withPlaybackActive(active = false, nowEpochMillis = 18 * 60_000L + 30_000L)
        val frozen = sleepTimerFadeVolumeFactor(paused, 30 * 60_000L)
        assertEquals(half, frozen)
        assertEquals(90_000L, sleepTimerRemainingMillis(paused, 30 * 60_000L))
        assertFalse(shouldExpireSleepTimer(paused, 30 * 60_000L, snapshot(trackId = "one")))
        val resumed = paused.withPlaybackActive(active = true, nowEpochMillis = 30 * 60_000L)
        assertEquals(half, sleepTimerFadeVolumeFactor(resumed, 30 * 60_000L))
        assertTrue(shouldExpireSleepTimer(resumed, 31 * 60_000L + 30_000L, snapshot(trackId = "one")))
    }

    @Test
    fun fadeOptionsNormalizeAndNeverExceedTheTimer() {
        assertEquals(listOf(0, 0, 1, 1, 3, 5, 5, 10, 10), listOf(-4, 0, 1, 2, 3, 5, 9, 10, 60).map(::normalizedSleepTimerFadeMinutes))
        val short = sleepTimerState(
            request = SleepTimerRequest.DurationMinutes(2),
            nowEpochMillis = 0L,
            snapshot = snapshot(trackId = "one"),
            queueLastTrackId = "one",
            fadeMinutes = 10,
            playing = true,
        )
        assertEquals(120_000L, short.fadeMillis)
        assertTrue(sleepTimerFadeVolumeFactor(short, 1L) < 1f)
        assertEquals(0L, sleepTimerState(SleepTimerRequest.TrackEnd, 0L, snapshot(trackId = "one"), "one", fadeMinutes = 3).fadeMillis)
    }

    @Test
    fun droppingTheFadeKeepsTheRemainingTimeAtFullVolume() {
        val timer = sleepTimerState(
            request = SleepTimerRequest.DurationMinutes(10),
            nowEpochMillis = 0L,
            snapshot = snapshot(trackId = "one"),
            queueLastTrackId = "one",
            fadeMinutes = 5,
            playing = true,
        ).withPlaybackActive(active = false, nowEpochMillis = 6 * 60_000L)

        val plain = timer.withoutFade(nowEpochMillis = 8 * 60_000L)

        assertEquals(0L, plain.fadeMillis)
        assertTrue(plain.pauseAtEnd)
        assertEquals(1f, sleepTimerFadeVolumeFactor(plain, 8 * 60_000L))
        assertEquals(4 * 60_000L, sleepTimerRemainingMillis(plain, 8 * 60_000L))
    }

    @Test
    fun fadeCurveFallsSteadilyInPerceivedLoudness() {
        assertEquals(1f, sleepTimerFadeVolumeFactor(0f))
        assertEquals(0f, sleepTimerFadeVolumeFactor(1f))
        val samples = (0..10).map { sleepTimerFadeVolumeFactor(it / 10f) }
        assertEquals(samples.sortedDescending(), samples)
        // Loudness follows amplitude^0.6, so it should be about half way at the midpoint.
        val loudness = sleepTimerFadeVolumeFactor(0.5f).toDouble().pow(0.6)
        assertTrue(loudness in 0.45..0.55, "loudness $loudness")
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
