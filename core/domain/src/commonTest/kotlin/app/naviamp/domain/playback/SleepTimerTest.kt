package app.naviamp.domain.playback

import app.naviamp.domain.AlbumId
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
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
    fun trackEndTimerEndsAfterWhicheverTrackIsCurrent() {
        val timer = sleepTimerState(SleepTimerRequest.TrackEnd, 0L, snapshot(trackId = "a1"), "b1")
        val queue = queue("a1" to "a", "a2" to "a", "b1" to "b")

        assertTrue(sleepTimerEndsAfterCurrentTrack(timer, queue))
        assertTrue(sleepTimerEndsAfterCurrentTrack(timer, queue.copy(currentIndex = 1)))
        assertTrue(sleepTimerEndsAfterCurrentTrack(timer, queue, RepeatMode.Track))
    }

    @Test
    fun albumEndTimerEndsOnlyAfterTheLastQueuedTrackOfTheCurrentAlbum() {
        val timer = sleepTimerState(SleepTimerRequest.AlbumEnd, 0L, snapshot(trackId = "a1"), "b1")
        val queue = queue("a1" to "a", "a2" to "a", "b1" to "b", "single" to null)

        assertFalse(sleepTimerEndsAfterCurrentTrack(timer, queue))
        assertTrue(sleepTimerEndsAfterCurrentTrack(timer, queue.copy(currentIndex = 1)))
        assertTrue(sleepTimerEndsAfterCurrentTrack(timer, queue.copy(currentIndex = 2)))
        assertTrue(sleepTimerEndsAfterCurrentTrack(timer, queue.copy(currentIndex = 3)))
    }

    @Test
    fun queueEndTimerEndsAfterTheLastQueuedTrackEvenWhenTheQueueRepeats() {
        val timer = sleepTimerState(SleepTimerRequest.QueueEnd, 0L, snapshot(trackId = "a1"), "b1")
        val queue = queue("a1" to "a", "a2" to "a", "b1" to "b")

        assertFalse(sleepTimerEndsAfterCurrentTrack(timer, queue))
        assertFalse(sleepTimerEndsAfterCurrentTrack(timer, queue.copy(currentIndex = 1)))
        assertTrue(sleepTimerEndsAfterCurrentTrack(timer, queue.copy(currentIndex = 2)))
        assertTrue(sleepTimerEndsAfterCurrentTrack(timer, queue.copy(currentIndex = 2), RepeatMode.Queue))
    }

    @Test
    fun durationAndMissingTimersNeverHoldTheQueue() {
        val queue = queue("a1" to "a")
        assertFalse(sleepTimerEndsAfterCurrentTrack(null, queue))
        assertFalse(sleepTimerEndsAfterCurrentTrack(
            sleepTimerState(SleepTimerRequest.DurationMinutes(5), 0L, snapshot(trackId = "a1"), "a1"), queue))
        assertFalse(shouldExpireSleepTimer(
            sleepTimerState(SleepTimerRequest.TrackEnd, 0L, snapshot(trackId = "a1"), "a1"),
            0L,
            snapshot(trackId = "a1", position = 179.9, duration = 180.0).copy(playbackEnded = true),
        ))
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

    private fun queue(vararg tracks: Pair<String, String?>): PlaybackQueue =
        PlaybackQueue(
            tracks = tracks.map { (id, album) ->
                Track(
                    id = TrackId(id),
                    title = id,
                    artistName = "Artist",
                    albumId = album?.let(::AlbumId),
                    albumTitle = album,
                    durationSeconds = 180,
                    coverArtId = null,
                    audioInfo = null,
                    replayGain = null,
                )
            },
            currentIndex = 0,
        )

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
