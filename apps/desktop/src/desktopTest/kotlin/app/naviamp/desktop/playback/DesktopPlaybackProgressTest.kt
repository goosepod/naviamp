package app.naviamp.desktop

import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.desktop.playback.PlaybackSource
import app.naviamp.domain.AudioCodec
import app.naviamp.domain.StreamQuality
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
    fun playbackPositionSaveRequiresCurrentTrackAndThresholdMovement() {
        val queue = PlaybackQueue(tracks = listOf(track("one")), currentIndex = 0)

        assertEquals(false, shouldSavePlaybackPosition(queue, positionSeconds = null, lastSavedPositionSeconds = null))
        assertEquals(false, shouldSavePlaybackPosition(PlaybackQueue(), positionSeconds = 10.0, lastSavedPositionSeconds = null))
        assertEquals(true, shouldSavePlaybackPosition(queue, positionSeconds = 10.0, lastSavedPositionSeconds = null))
        assertEquals(
            false,
            shouldSavePlaybackPosition(
                queue = queue,
                positionSeconds = 12.0,
                lastSavedPositionSeconds = 10.0,
                saveThresholdSeconds = 5.0,
            ),
        )
        assertEquals(
            true,
            shouldSavePlaybackPosition(
                queue = queue,
                positionSeconds = 15.0,
                lastSavedPositionSeconds = 10.0,
                saveThresholdSeconds = 5.0,
            ),
        )
    }

    @Test
    fun playReportThresholdUsesHalfDurationCappedAtMaximum() {
        assertEquals(50.0, playReportThresholdSeconds(100.0))
        assertEquals(240.0, playReportThresholdSeconds(1_000.0))
        assertEquals(240.0, playReportThresholdSeconds(null))
        assertEquals(240.0, playReportThresholdSeconds(0.0))
    }

    @Test
    fun playReportSubmissionRequiresSupportNewSessionAndThresholdProgress() {
        assertEquals(
            false,
            shouldSubmitPlayReport(
                supportsPlayReporting = false,
                activeSessionId = 2,
                submittedSessionId = null,
                positionSeconds = 60.0,
                durationSeconds = 100.0,
            ),
        )
        assertEquals(
            false,
            shouldSubmitPlayReport(
                supportsPlayReporting = true,
                activeSessionId = 2,
                submittedSessionId = 2,
                positionSeconds = 60.0,
                durationSeconds = 100.0,
            ),
        )
        assertEquals(
            false,
            shouldSubmitPlayReport(
                supportsPlayReporting = true,
                activeSessionId = 2,
                submittedSessionId = null,
                positionSeconds = 49.0,
                durationSeconds = 100.0,
            ),
        )
        assertEquals(
            true,
            shouldSubmitPlayReport(
                supportsPlayReporting = true,
                activeSessionId = 2,
                submittedSessionId = null,
                positionSeconds = 50.0,
                durationSeconds = 100.0,
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

    @Test
    fun repeatModeCyclesThroughQueueTrackAndOff() {
        assertEquals(RepeatMode.Queue, nextRepeatMode(RepeatMode.Off))
        assertEquals(RepeatMode.Track, nextRepeatMode(RepeatMode.Queue))
        assertEquals(RepeatMode.Off, nextRepeatMode(RepeatMode.Track))
    }

    @Test
    fun transcodedProviderStreamSeekReplaysCurrentTrack() {
        val transcoded = StreamQuality.Transcoded(AudioCodec.Opus, bitrateKbps = 192)

        assertEquals(true, shouldReplayCurrentForSeek(transcoded, PlaybackSource.ProviderStream))
        assertEquals(true, shouldReplayCurrentForSeek(transcoded, PlaybackSource.ProviderStreamCacheDisabled))
        assertEquals(false, shouldReplayCurrentForSeek(transcoded, PlaybackSource.CachedFile))
        assertEquals(false, shouldReplayCurrentForSeek(StreamQuality.Original, PlaybackSource.ProviderStream))
    }

    @Test
    fun desktopSeekPlanSkipsInternetRadioAndPreservesDuration() {
        assertEquals(
            null,
            planDesktopSeek(
                isInternetRadioTrack = true,
                positionSeconds = 30.0,
                currentProgress = PlaybackProgress.Unknown,
                trackDurationSeconds = 180,
                streamQuality = StreamQuality.Original,
                playbackSource = PlaybackSource.CachedFile,
            ),
        )

        val plan = planDesktopSeek(
            isInternetRadioTrack = false,
            positionSeconds = 30.0,
            currentProgress = PlaybackProgress(positionSeconds = 10.0, durationSeconds = null),
            trackDurationSeconds = 180,
            streamQuality = StreamQuality.Original,
            playbackSource = PlaybackSource.CachedFile,
        )

        assertEquals(PlaybackProgress(positionSeconds = 30.0, durationSeconds = 180.0), plan?.progress)
        assertEquals(false, plan?.shouldReplayCurrent)
    }

    @Test
    fun desktopSeekPlanReplaysTranscodedProviderStream() {
        val plan = planDesktopSeek(
            isInternetRadioTrack = false,
            positionSeconds = 30.0,
            currentProgress = PlaybackProgress(positionSeconds = 10.0, durationSeconds = 200.0),
            trackDurationSeconds = 180,
            streamQuality = StreamQuality.Transcoded(AudioCodec.Opus, bitrateKbps = 192),
            playbackSource = PlaybackSource.ProviderStream,
        )

        assertEquals(PlaybackProgress(positionSeconds = 30.0, durationSeconds = 200.0), plan?.progress)
        assertEquals(true, plan?.shouldReplayCurrent)
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
