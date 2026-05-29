package app.naviamp.domain.playback

import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.settings.PreviousButtonBehavior
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackControlDecisionsTest {
    @Test
    fun restartInsteadOfPreviousRequiresRestartBehaviorAndThreshold() {
        assertEquals(
            true,
            shouldRestartInsteadOfPrevious(
                previousButtonBehavior = PreviousButtonBehavior.RestartThenPrevious,
                positionSeconds = 12.0,
                restartThresholdSeconds = 10.0,
            ),
        )
        assertEquals(
            false,
            shouldRestartInsteadOfPrevious(
                previousButtonBehavior = PreviousButtonBehavior.RestartThenPrevious,
                positionSeconds = 10.0,
                restartThresholdSeconds = 10.0,
            ),
        )
        assertEquals(
            false,
            shouldRestartInsteadOfPrevious(
                previousButtonBehavior = PreviousButtonBehavior.AlwaysPrevious,
                positionSeconds = 12.0,
                restartThresholdSeconds = 10.0,
            ),
        )
    }

    @Test
    fun repeatModeCyclesThroughQueueTrackAndOff() {
        assertEquals(RepeatMode.Queue, nextRepeatMode(RepeatMode.Off))
        assertEquals(RepeatMode.Track, nextRepeatMode(RepeatMode.Queue))
        assertEquals(RepeatMode.Off, nextRepeatMode(RepeatMode.Track))
    }

    @Test
    fun previousButtonCanUseQueueHistoryOrRestartCurrentTrackWhenConfigured() {
        val queue = PlaybackQueue(tracks = listOf(track("one")), currentIndex = 0)

        assertEquals(
            true,
            canUsePreviousButton(
                queue = queue,
                previousButtonBehavior = PreviousButtonBehavior.RestartThenPrevious,
                positionSeconds = 12.0,
                restartThresholdSeconds = 10.0,
            ),
        )
        assertEquals(
            false,
            canUsePreviousButton(
                queue = queue,
                previousButtonBehavior = PreviousButtonBehavior.AlwaysPrevious,
                positionSeconds = 12.0,
                restartThresholdSeconds = 10.0,
            ),
        )
    }

    @Test
    fun nextButtonCanWrapWhenQueueRepeatIsActive() {
        val queue = PlaybackQueue(tracks = listOf(track("one")), currentIndex = 0)

        assertEquals(false, canUseNextButton(queue, RepeatMode.Off))
        assertEquals(true, canUseNextButton(queue, RepeatMode.Queue))
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
