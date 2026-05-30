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

    @Test
    fun adjacentActionRestartsPreviousWhenConfiguredPastThreshold() {
        val action = planPlaybackAdjacentAction(
            currentTrack = track("two"),
            activeQueue = listOf(track("one"), track("two")),
            offset = -1,
            repeatMode = RepeatMode.Off,
            previousButtonBehavior = PreviousButtonBehavior.RestartThenPrevious,
            positionSeconds = 12.0,
            restartThresholdSeconds = 10.0,
        )

        assertEquals(PlaybackAdjacentAction.RestartCurrent, action)
    }

    @Test
    fun adjacentActionSelectsTrackFromActiveQueue() {
        val one = track("one")
        val two = track("two")
        val three = track("three")
        val action = planPlaybackAdjacentAction(
            currentTrack = two,
            activeQueue = listOf(one, two, three),
            offset = 1,
            repeatMode = RepeatMode.Off,
            previousButtonBehavior = PreviousButtonBehavior.RestartThenPrevious,
            positionSeconds = 12.0,
            restartThresholdSeconds = 10.0,
        )

        assertEquals(PlaybackAdjacentAction.PlayTrack(three, listOf(one, two, three)), action)
    }

    @Test
    fun adjacentActionWrapsWithQueueRepeat() {
        val one = track("one")
        val two = track("two")
        val action = planPlaybackAdjacentAction(
            currentTrack = two,
            activeQueue = listOf(one, two),
            offset = 1,
            repeatMode = RepeatMode.Queue,
            previousButtonBehavior = PreviousButtonBehavior.AlwaysPrevious,
            positionSeconds = 12.0,
            restartThresholdSeconds = 10.0,
        )

        assertEquals(PlaybackAdjacentAction.PlayTrack(one, listOf(one, two)), action)
    }

    @Test
    fun adjacentActionDoesNothingWhenCurrentTrackIsMissingFromQueue() {
        val action = planPlaybackAdjacentAction(
            currentTrack = track("two"),
            activeQueue = listOf(track("one")),
            offset = 1,
            repeatMode = RepeatMode.Off,
            previousButtonBehavior = PreviousButtonBehavior.AlwaysPrevious,
            positionSeconds = 12.0,
            restartThresholdSeconds = 10.0,
        )

        assertEquals(PlaybackAdjacentAction.None, action)
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
