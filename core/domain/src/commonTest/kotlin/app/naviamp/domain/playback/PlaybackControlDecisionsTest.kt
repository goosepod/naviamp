package app.naviamp.domain.playback

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
}
