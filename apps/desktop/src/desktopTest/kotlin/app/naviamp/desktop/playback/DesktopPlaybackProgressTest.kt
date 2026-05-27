package app.naviamp.desktop

import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.settings.PreviousButtonBehavior
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopPlaybackProgressTest {
    @Test
    fun ignoresStaleEngineProgressSoonAfterSeek() {
        assertEquals(
            true,
            shouldIgnoreProgressForPendingSeek(
                pendingSeekPositionSeconds = 60.0,
                pendingSeekIssuedAtMillis = 1_000,
                incomingPositionSeconds = 12.0,
                nowMillis = 1_500,
                toleranceSeconds = 1.0,
                staleWindowMillis = 2_000,
            ),
        )
    }

    @Test
    fun acceptsEngineProgressAfterSeekWindowExpires() {
        assertEquals(
            false,
            shouldIgnoreProgressForPendingSeek(
                pendingSeekPositionSeconds = 60.0,
                pendingSeekIssuedAtMillis = 1_000,
                incomingPositionSeconds = 12.0,
                nowMillis = 3_500,
                toleranceSeconds = 1.0,
                staleWindowMillis = 2_000,
            ),
        )
    }

    @Test
    fun clearsPendingSeekWhenIncomingProgressReachesTarget() {
        assertEquals(
            true,
            shouldClearPendingSeek(
                pendingSeekPositionSeconds = 60.0,
                pendingSeekIssuedAtMillis = 1_000,
                incomingPositionSeconds = 60.5,
                nowMillis = 1_500,
                toleranceSeconds = 1.0,
                staleWindowMillis = 2_000,
            ),
        )
    }

    @Test
    fun updatesUiForLargeProgressMovementOrDurationChange() {
        assertEquals(
            false,
            shouldUpdatePlaybackProgressUi(
                pendingSeekPositionSeconds = null,
                currentProgress = PlaybackProgress(positionSeconds = 10.0, durationSeconds = 200.0),
                mergedProgress = PlaybackProgress(positionSeconds = 10.2, durationSeconds = 200.0),
                nowMillis = 1_200,
                lastUiUpdateMillis = 1_000,
                positionThresholdSeconds = 1.0,
                updateIntervalMillis = 1_000,
            ),
        )
        assertEquals(
            true,
            shouldUpdatePlaybackProgressUi(
                pendingSeekPositionSeconds = null,
                currentProgress = PlaybackProgress(positionSeconds = 10.0, durationSeconds = 200.0),
                mergedProgress = PlaybackProgress(positionSeconds = 12.0, durationSeconds = 200.0),
                nowMillis = 1_200,
                lastUiUpdateMillis = 1_000,
                positionThresholdSeconds = 1.0,
                updateIntervalMillis = 1_000,
            ),
        )
        assertEquals(
            true,
            shouldUpdatePlaybackProgressUi(
                pendingSeekPositionSeconds = null,
                currentProgress = PlaybackProgress(positionSeconds = 10.0, durationSeconds = 200.0),
                mergedProgress = PlaybackProgress(positionSeconds = 10.2, durationSeconds = 201.0),
                nowMillis = 1_200,
                lastUiUpdateMillis = 1_000,
                positionThresholdSeconds = 1.0,
                updateIntervalMillis = 1_000,
            ),
        )
    }

    @Test
    fun previousButtonCanRestartCurrentTrackWhenConfigured() {
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
