package app.naviamp.domain.playback

import app.naviamp.domain.ReplayGain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
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
        assertEquals(ReplayGainMode.Off, mixer.replayGainAdjustment.mode)
        assertTrue(mixer.isLocalFileUrl)
    }

    @Test
    fun preparesDirectFallbackOnlyWhenAllowed() {
        val request = request("two")

        val direct = assertIs<PreparedBassPlaybackPlan.PrepareDirect>(
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
        assertTrue(direct.isLocalFileUrl)
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
        assertEquals(0.5011872f, plan.replayGainAdjustment.volumeFactor, absoluteTolerance = 0.000001f)
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

    @Test
    fun buildsPreparedSuccessAndFailureStateUpdates() {
        val request = request("one")
        val adjustment = PlaybackReplayGainAdjustment.off().copy(volumeFactor = 0.5f)

        val success = preparedBassPlaybackSucceeded(
            preparedHandle = 8,
            request = request,
            replayGainAdjustment = adjustment,
        )
        val failure = preparedBassPlaybackFailed(IllegalStateException("No stream"))

        assertEquals(8, success.preparedHandle)
        assertEquals(request, success.preparedRequest)
        assertEquals(adjustment, success.replayGainAdjustment)
        assertEquals(0.5f, success.replayGainFactor)
        assertNull(success.error)
        assertEquals(0, failure.preparedHandle)
        assertNull(failure.preparedRequest)
        assertNull(failure.replayGainAdjustment)
        assertEquals(1f, failure.replayGainFactor)
        assertEquals("No stream", failure.error)
    }

    @Test
    fun buildsPreparedAdoptionUpdateAndClearsPreparedState() {
        val update = preparedBassPlaybackAdopted(
            adoption = PreparedBassPlaybackAdoption(
                shouldAdopt = true,
                preparedHandle = 7,
            ),
            replayGainFactor = 0.75f,
        )

        requireNotNull(update)
        assertEquals(7, update.currentSourceHandle)
        assertEquals(0.75f, update.replayGainFactor)
        assertEquals(0.75f, update.replayGainAdjustment.volumeFactor)
        assertNull(update.preparedReset.request)
        assertEquals(1f, update.preparedReset.replayGainFactor)
        assertNull(
            preparedBassPlaybackAdopted(
                adoption = PreparedBassPlaybackAdoption(
                    shouldAdopt = false,
                    preparedHandle = 7,
                ),
                replayGainFactor = 0.75f,
            ),
        )
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
