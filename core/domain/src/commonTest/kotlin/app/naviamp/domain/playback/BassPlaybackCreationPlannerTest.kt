package app.naviamp.domain.playback

import app.naviamp.domain.ReplayGain
import app.naviamp.domain.bass.BassCreatedPlayback
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BassPlaybackCreationPlannerTest {
    @Test
    fun plansMixerPlaybackAndReplayGainForMediaBackedRequests() {
        val plan = planBassPlaybackCreation(
            request = request(
                mediaId = "track",
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
            supportsMixer = true,
            requireMediaId = true,
        )

        assertTrue(plan.useMixer)
        assertTrue(plan.isLocalFileUrl)
        assertEquals(0.5011872f, plan.replayGainFactor, absoluteTolerance = 0.000001f)
        assertEquals(ReplayGainMode.Track, plan.replayGainAdjustment.mode)
    }

    @Test
    fun skipsMixerWhenMixerIsNotRequired() {
        val plan = planBassPlaybackCreation(
            request = request(mediaId = "track"),
            supportsMixer = true,
            requireMediaId = true,
            requiresMixer = false,
        )

        assertFalse(plan.useMixer)
        assertTrue(plan.isLocalFileUrl)
        assertEquals(1f, plan.replayGainFactor)
    }

    @Test
    fun skipsMixerWhenMediaIdIsRequiredButMissing() {
        val plan = planBassPlaybackCreation(
            request = request(mediaId = null),
            supportsMixer = true,
            requireMediaId = true,
        )

        assertFalse(plan.useMixer)
        assertTrue(plan.isLocalFileUrl)
        assertEquals(1f, plan.replayGainFactor)
    }

    @Test
    fun marksRemoteUrlsAsNotLocalFiles() {
        val plan = planBassPlaybackCreation(
            request = PlaybackRequest(
                url = "https://example.test/track.flac",
                mediaId = "track",
            ),
            supportsMixer = false,
            requireMediaId = false,
        )

        assertFalse(plan.isLocalFileUrl)
    }

    @Test
    fun mapsCreatedPlaybackToActivationUpdate() {
        val adjustment = PlaybackReplayGainAdjustment.off().copy(volumeFactor = 0.8f)
        val update = bassPlaybackActivated(
            playback = BassCreatedPlayback(
                playbackHandle = 10,
                sourceHandle = 11,
                replayGainFactor = 0.8f,
            ),
            replayGainAdjustment = adjustment,
        )

        assertEquals(10, update.playbackHandle)
        assertEquals(11, update.sourceHandle)
        assertEquals(adjustment, update.replayGainAdjustment)
        assertEquals(0.8f, update.replayGainFactor)
    }

    private fun request(
        mediaId: String?,
        replayGain: PlaybackReplayGain? = null,
    ): PlaybackRequest =
        PlaybackRequest(
            url = "file:///track.flac",
            mediaId = mediaId,
            replayGain = replayGain,
        )
}
