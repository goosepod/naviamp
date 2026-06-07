package app.naviamp.domain.playback

import app.naviamp.domain.ReplayGain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PreparedBassPlaybackPlannerTest {
    @Test
    fun reusesMatchingPreparedPlayback() {
        val request = request("one")

        val plan = planPreparedBassPlayback(
            playbackHandle = 1,
            currentSourceHandle = 2,
            preparedRequest = request,
            preparedHandle = 3,
            supportsMixer = true,
            request = request,
            allowDirectFallback = false,
        )

        assertEquals(PreparedBassPlaybackPlan.ReusePrepared, plan)
    }

    @Test
    fun preparesMixerWhenActiveMixerPlaybackIsAvailable() {
        val plan = planPreparedBassPlayback(
            playbackHandle = 1,
            currentSourceHandle = 2,
            preparedRequest = null,
            preparedHandle = 0,
            supportsMixer = true,
            request = request("two"),
            allowDirectFallback = false,
        )

        val mixer = assertIs<PreparedBassPlaybackPlan.PrepareMixer>(plan)
        assertEquals(1f, mixer.replayGainFactor)
    }

    @Test
    fun preparesDirectFallbackOnlyWhenAllowed() {
        val request = request("two")

        assertIs<PreparedBassPlaybackPlan.PrepareDirect>(
            planPreparedBassPlayback(
                playbackHandle = 0,
                currentSourceHandle = 0,
                preparedRequest = null,
                preparedHandle = 0,
                supportsMixer = false,
                request = request,
                allowDirectFallback = true,
            ),
        )
        assertEquals(
            PreparedBassPlaybackPlan.NotSupported,
            planPreparedBassPlayback(
                playbackHandle = 0,
                currentSourceHandle = 0,
                preparedRequest = null,
                preparedHandle = 0,
                supportsMixer = false,
                request = request,
                allowDirectFallback = false,
            ),
        )
    }

    @Test
    fun appliesReplayGainFactorToPreparePlans() {
        val plan = assertIs<PreparedBassPlaybackPlan.PrepareMixer>(
            planPreparedBassPlayback(
                playbackHandle = 1,
                currentSourceHandle = 2,
                preparedRequest = null,
                preparedHandle = 0,
                supportsMixer = true,
                request = request(
                    mediaId = "gain",
                    replayGain = PlaybackReplayGain(
                        replayGain = ReplayGain(
                            trackGainDb = -6.0,
                            albumGainDb = null,
                            trackPeak = null,
                            albumPeak = null,
                        ),
                        source = ReplayGainSource.Provider,
                    ),
                ).copy(replayGainMode = ReplayGainMode.Track),
                allowDirectFallback = false,
            ),
        )

        assertEquals(0.5011872f, plan.replayGainFactor, absoluteTolerance = 0.000001f)
    }

    @Test
    fun adoptsOnlyMatchingPreparedMixerPlayback() {
        val request = request("one")

        val adopt = planPreparedBassPlaybackAdoption(
            playbackHandle = 1,
            preparedRequest = request,
            preparedHandle = 3,
            supportsMixer = true,
            request = request,
        )
        val skip = planPreparedBassPlaybackAdoption(
            playbackHandle = 1,
            preparedRequest = request.copy(mediaId = "other"),
            preparedHandle = 3,
            supportsMixer = true,
            request = request,
        )

        assertTrue(adopt.shouldAdopt)
        assertEquals(3, adopt.preparedHandle)
        assertFalse(skip.shouldAdopt)
    }

    private fun request(
        mediaId: String,
        replayGain: PlaybackReplayGain? = null,
    ): PlaybackRequest =
        PlaybackRequest(
            url = "file:///$mediaId.flac",
            mediaId = mediaId,
            replayGain = replayGain,
        )
}
