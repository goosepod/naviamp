package app.naviamp.domain.playback

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackReportDecisionsTest {
    @Test
    fun playReportThresholdUsesHalfDurationCappedAtMaximum() {
        assertEquals(50.0, playReportThresholdSeconds(100.0))
        assertEquals(240.0, playReportThresholdSeconds(1_000.0))
        assertEquals(240.0, playReportThresholdSeconds(null))
        assertEquals(240.0, playReportThresholdSeconds(0.0))
    }

    @Test
    fun playbackTrackReportingRequiresProviderSupportAndNonRadioTrack() {
        assertEquals(
            false,
            canReportPlaybackTrack(
                supportsPlayReporting = false,
                isInternetRadioTrack = false,
            ),
        )
        assertEquals(
            false,
            canReportPlaybackTrack(
                supportsPlayReporting = true,
                isInternetRadioTrack = true,
            ),
        )
        assertEquals(
            true,
            canReportPlaybackTrack(
                supportsPlayReporting = true,
                isInternetRadioTrack = false,
            ),
        )
    }

    @Test
    fun nowPlayingReportRequiresReportablePlayingTrack() {
        assertEquals(
            false,
            shouldReportNowPlaying(
                supportsPlayReporting = true,
                isInternetRadioTrack = false,
                playbackState = PlaybackState.Paused,
            ),
        )
        assertEquals(
            false,
            shouldReportNowPlaying(
                supportsPlayReporting = true,
                isInternetRadioTrack = true,
                playbackState = PlaybackState.Playing,
            ),
        )
        assertEquals(
            true,
            shouldReportNowPlaying(
                supportsPlayReporting = true,
                isInternetRadioTrack = false,
                playbackState = PlaybackState.Playing,
            ),
        )
    }

    @Test
    fun playReportSubmissionRequiresSupportNewSessionPlayableTrackAndThresholdProgress() {
        assertEquals(
            false,
            shouldSubmitPlayReport(
                supportsPlayReporting = false,
                isInternetRadioTrack = false,
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
                isInternetRadioTrack = true,
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
                isInternetRadioTrack = false,
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
                isInternetRadioTrack = false,
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
                isInternetRadioTrack = false,
                activeSessionId = 2,
                submittedSessionId = null,
                positionSeconds = 50.0,
                durationSeconds = 100.0,
            ),
        )
    }

    @Test
    fun playReportSubmissionWorksWithLongSessionIds() {
        assertEquals(
            true,
            shouldSubmitPlayReport(
                supportsPlayReporting = true,
                isInternetRadioTrack = false,
                activeSessionId = 2L,
                submittedSessionId = null,
                positionSeconds = 50.0,
                durationSeconds = 100.0,
            ),
        )
    }
}
