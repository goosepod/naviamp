package app.naviamp.domain.playback

import app.naviamp.domain.AudioCodec
import app.naviamp.domain.StreamQuality
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackSeekDecisionsTest {
    @Test
    fun seekPlanSkipsInternetRadioAndPreservesDuration() {
        assertEquals(
            null,
            planPlaybackSeek(
                isInternetRadioTrack = true,
                positionSeconds = 30.0,
                currentProgress = PlaybackProgress.Unknown,
                trackDurationSeconds = 180,
                streamQuality = StreamQuality.Original,
                shouldReplayTranscodedStream = false,
            ),
        )

        val plan = planPlaybackSeek(
            isInternetRadioTrack = false,
            positionSeconds = 30.0,
            currentProgress = PlaybackProgress(positionSeconds = 10.0, durationSeconds = null),
            trackDurationSeconds = 180,
            streamQuality = StreamQuality.Original,
            shouldReplayTranscodedStream = false,
        )

        assertEquals(PlaybackProgress(positionSeconds = 30.0, durationSeconds = 180.0), plan?.progress)
        assertEquals(false, plan?.shouldReplayCurrent)
        assertEquals(30.0, plan?.pendingSeekPositionSeconds)
        assertEquals(true, plan?.shouldClearRestoredStartPosition)
    }

    @Test
    fun seekPlanReplaysTranscodedStreamWhenPlatformRequiresReplay() {
        val plan = planPlaybackSeek(
            isInternetRadioTrack = false,
            positionSeconds = 30.0,
            currentProgress = PlaybackProgress(positionSeconds = 10.0, durationSeconds = 200.0),
            trackDurationSeconds = 180,
            streamQuality = StreamQuality.Transcoded(AudioCodec.Opus, bitrateKbps = 192),
            shouldReplayTranscodedStream = true,
        )

        assertEquals(PlaybackProgress(positionSeconds = 30.0, durationSeconds = 200.0), plan?.progress)
        assertEquals(true, plan?.shouldReplayCurrent)
    }

    @Test
    fun seekPlanClampsNegativePositionsForUiProgress() {
        val plan = planPlaybackSeek(
            isInternetRadioTrack = false,
            positionSeconds = -5.0,
            currentProgress = PlaybackProgress(positionSeconds = 10.0, durationSeconds = 200.0),
            trackDurationSeconds = 180,
            streamQuality = StreamQuality.Original,
            shouldReplayTranscodedStream = false,
        )

        assertEquals(PlaybackProgress(positionSeconds = 0.0, durationSeconds = 200.0), plan?.progress)
        assertEquals(-5.0, plan?.pendingSeekPositionSeconds)
    }

    @Test
    fun transcodedSeekReplayRequiresTranscodedQualityAndPlatformReplayNeed() {
        val transcoded = StreamQuality.Transcoded(AudioCodec.Opus, bitrateKbps = 192)

        assertEquals(true, shouldReplayTranscodedSeek(transcoded, shouldReplayTranscodedStream = true))
        assertEquals(false, shouldReplayTranscodedSeek(transcoded, shouldReplayTranscodedStream = false))
        assertEquals(false, shouldReplayTranscodedSeek(StreamQuality.Original, shouldReplayTranscodedStream = true))
    }

    @Test
    fun seekReplaySourceDecisionOnlyReplaysProviderStreams() {
        assertEquals(false, shouldReplayCurrentForSeek(PlaybackSource.Unknown))
        assertEquals(false, shouldReplayCurrentForSeek(PlaybackSource.DownloadedFile))
        assertEquals(false, shouldReplayCurrentForSeek(PlaybackSource.CachedFile))
        assertEquals(true, shouldReplayCurrentForSeek(PlaybackSource.ProviderStream))
        assertEquals(true, shouldReplayCurrentForSeek(PlaybackSource.ProviderStreamCacheDisabled))
    }
}
