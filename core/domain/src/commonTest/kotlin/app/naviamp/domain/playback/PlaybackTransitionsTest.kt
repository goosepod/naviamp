package app.naviamp.domain.playback

import app.naviamp.domain.ReplayGain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackTransitionsTest {
    @Test
    fun normalizesCrossfadeDurations() {
        assertEquals(0, normalizedCrossfadeDurationSeconds(-1))
        assertEquals(5, normalizedCrossfadeDurationSeconds(5))
        assertEquals(MaxCrossfadeDurationSeconds, normalizedCrossfadeDurationSeconds(99))
        assertEquals(5_000, crossfadeDurationMillis(5))
    }

    @Test
    fun queuesMixerSourcesOnlyWhenCrossfadeIsOff() {
        assertTrue(shouldQueueMixerSources(0))
        assertEquals(false, shouldQueueMixerSources(3))
    }

    @Test
    fun plansCrossfadePrepareNextWhenInsideCrossfadeWindow() {
        val plan = planPrepareNextPlayback(
            progress = PlaybackProgress(positionSeconds = 96.0, durationSeconds = 100.0),
            nextQueueIndex = 1,
            alreadyPreparedNext = false,
            gaplessEnabled = true,
            supportsGapless = true,
            crossfadeDurationSeconds = 5,
            supportsCrossfade = true,
            gaplessPrepareWindowSeconds = 2.0,
        )

        assertTrue(plan.shouldPrepare)
        assertEquals(PrepareNextPlaybackReason.Crossfade, plan.reason)
        assertEquals(5.0, plan.prepareWindowSeconds)
    }

    @Test
    fun plansGaplessPrepareNextWhenCrossfadeIsUnavailable() {
        val plan = planPrepareNextPlayback(
            progress = PlaybackProgress(positionSeconds = 99.0, durationSeconds = 100.0),
            nextQueueIndex = 1,
            alreadyPreparedNext = false,
            gaplessEnabled = true,
            supportsGapless = true,
            crossfadeDurationSeconds = 5,
            supportsCrossfade = false,
            gaplessPrepareWindowSeconds = 2.0,
        )

        assertTrue(plan.shouldPrepare)
        assertEquals(PrepareNextPlaybackReason.Gapless, plan.reason)
        assertEquals(2.0, plan.prepareWindowSeconds)
    }

    @Test
    fun skipsPrepareNextWhenAlreadyPreparedOrProgressIsUnknown() {
        assertEquals(
            false,
            planPrepareNextPlayback(
                progress = PlaybackProgress(positionSeconds = 99.0, durationSeconds = 100.0),
                nextQueueIndex = 1,
                alreadyPreparedNext = true,
                gaplessEnabled = true,
                supportsGapless = true,
                crossfadeDurationSeconds = 0,
                supportsCrossfade = true,
                gaplessPrepareWindowSeconds = 2.0,
            ).shouldPrepare,
        )
        assertEquals(
            false,
            planPrepareNextPlayback(
                progress = PlaybackProgress.Unknown,
                nextQueueIndex = 1,
                alreadyPreparedNext = false,
                gaplessEnabled = true,
                supportsGapless = true,
                crossfadeDurationSeconds = 0,
                supportsCrossfade = true,
                gaplessPrepareWindowSeconds = 2.0,
            ).shouldPrepare,
        )
    }

    @Test
    fun computesReplayGainAdjustmentForTrackMode() {
        val adjustment = playbackReplayGainAdjustment(
            PlaybackRequest(
                url = "file:///track.flac",
                replayGainMode = ReplayGainMode.Track,
                replayGain = PlaybackReplayGain(
                    replayGain = ReplayGain(
                        trackGainDb = -6.0,
                        albumGainDb = -3.0,
                        trackPeak = null,
                        albumPeak = null,
                    ),
                    source = ReplayGainSource.Provider,
                ),
            ),
        )

        assertEquals(ReplayGainMode.Track, adjustment.mode)
        assertEquals(ReplayGainSource.Provider, adjustment.source)
        assertEquals(-6.0, adjustment.gainDb)
        assertEquals(0.5011872f, adjustment.volumeFactor, absoluteTolerance = 0.000001f)
    }

    @Test
    fun limitsReplayGainBoostThatWouldClipPeak() {
        val adjustment = playbackReplayGainAdjustment(
            PlaybackRequest(
                url = "file:///track.flac",
                replayGainMode = ReplayGainMode.Album,
                replayGain = PlaybackReplayGain(
                    replayGain = ReplayGain(
                        trackGainDb = null,
                        albumGainDb = 6.0,
                        trackPeak = null,
                        albumPeak = 0.8,
                    ),
                    source = ReplayGainSource.LocalTags,
                ),
            ),
        )

        assertEquals(1.25f, adjustment.volumeFactor)
        assertTrue(adjustment.clippingPrevented)
    }

    @Test
    fun reusesPreparedPlaybackOnlyWhenRequestAndStreamMatch() {
        val request = PlaybackRequest(url = "file:///track.flac", mediaId = "track-1")

        assertTrue(
            shouldReusePreparedPlayback(
                preparedRequest = request,
                hasPreparedStream = true,
                request = request,
            ),
        )
        assertFalse(
            shouldReusePreparedPlayback(
                preparedRequest = request.copy(mediaId = "track-2"),
                hasPreparedStream = true,
                request = request,
            ),
        )
        assertFalse(
            shouldReusePreparedPlayback(
                preparedRequest = request,
                hasPreparedStream = false,
                request = request,
            ),
        )
    }

    @Test
    fun clearsPreparedPlaybackMetadataToPlatformDefaults() {
        val reset = clearPreparedPlaybackMetadata()

        assertEquals(null, reset.request)
        assertEquals(null, reset.replayGainAdjustment)
        assertEquals(1f, reset.replayGainFactor)
        assertEquals(null, reset.error)
    }

    @Test
    fun clearsPlaybackStreamStateToPlatformDefaults() {
        val reset = clearPlaybackStreamState()

        assertEquals(0, reset.stream)
        assertEquals(0, reset.currentSourceStream)
        assertFalse(reset.crossfadeActive)
        assertEquals(ReplayGainMode.Off, reset.replayGainAdjustment.mode)
        assertEquals(1f, reset.replayGainFactor)
    }

    @Test
    fun plansDirectPlaybackVolumeWithReplayGainOnOutput() {
        val plan = playbackVolumeApplicationPlan(
            userVolumeFactor = 0.5f,
            replayGainFactor = 0.75f,
            hasSeparateSourceStream = false,
        )

        assertEquals(0.5f, plan.outputVolumeFactor)
        assertEquals(null, plan.sourceReplayGainFactor)
        assertEquals(0.375f, plan.directVolumeFactor)
    }

    @Test
    fun plansMixerPlaybackVolumeWithReplayGainOnSource() {
        val plan = playbackVolumeApplicationPlan(
            userVolumeFactor = 0.5f,
            replayGainFactor = 0.75f,
            hasSeparateSourceStream = true,
        )

        assertEquals(0.5f, plan.outputVolumeFactor)
        assertEquals(0.75f, plan.sourceReplayGainFactor)
        assertEquals(0.375f, plan.directVolumeFactor)
    }

    @Test
    fun plansQueuedPreparedMixerTransitionWhenCrossfadeIsOff() {
        val plan = planPreparedMixerTransition(
            crossfadeDurationSeconds = 0,
            replayGainFactor = 0.75f,
        )

        assertFalse(plan.shouldCrossfade)
        assertEquals(0, plan.durationMillis)
        assertEquals(0.75f, plan.initialNextSourceVolume)
        assertEquals(0.75f, plan.finalNextSourceVolume)
        assertFalse(plan.shouldFadeCurrentSource)
    }

    @Test
    fun plansCrossfadePreparedMixerTransition() {
        val plan = planPreparedMixerTransition(
            crossfadeDurationSeconds = 5,
            replayGainFactor = 0.75f,
        )

        assertTrue(plan.shouldCrossfade)
        assertEquals(5_000, plan.durationMillis)
        assertEquals(0f, plan.initialNextSourceVolume)
        assertEquals(0.75f, plan.finalNextSourceVolume)
        assertTrue(plan.shouldFadeCurrentSource)
    }

    @Test
    fun adoptsPreparedPlaybackOnlyWhenActivePreparedMatchingAndMixerCapable() {
        val request = PlaybackRequest(url = "file:///track.flac", mediaId = "track-1")

        assertTrue(
            planPreparedPlaybackAdoption(
                hasActiveStream = true,
                preparedRequest = request,
                hasPreparedStream = true,
                supportsMixer = true,
                request = request,
            ).shouldAdopt,
        )
        assertFalse(
            planPreparedPlaybackAdoption(
                hasActiveStream = false,
                preparedRequest = request,
                hasPreparedStream = true,
                supportsMixer = true,
                request = request,
            ).shouldAdopt,
        )
        assertFalse(
            planPreparedPlaybackAdoption(
                hasActiveStream = true,
                preparedRequest = request,
                hasPreparedStream = true,
                supportsMixer = false,
                request = request,
            ).shouldAdopt,
        )
        assertFalse(
            planPreparedPlaybackAdoption(
                hasActiveStream = true,
                preparedRequest = request.copy(mediaId = "track-2"),
                hasPreparedStream = true,
                supportsMixer = true,
                request = request,
            ).shouldAdopt,
        )
    }

    @Test
    fun buildsEqualPowerFadeInEnvelope() {
        val envelope = equalPowerFadeEnvelope(
            startBytes = 10,
            durationBytes = 80,
            fadeIn = true,
            scale = 0.5f,
        )

        assertEquals(EqualPowerEnvelopeSteps + 1, envelope.size)
        assertEquals(10, envelope.first().positionBytes)
        assertEquals(90, envelope.last().positionBytes)
        assertEquals(0f, envelope.first().volume)
        assertEquals(0.5f, envelope.last().volume)
        assertTrue(envelope.zipWithNext().all { (left, right) -> left.volume <= right.volume })
    }

    @Test
    fun buildsEqualPowerFadeOutEnvelope() {
        val envelope = equalPowerFadeEnvelope(
            startBytes = 0,
            durationBytes = 80,
            fadeIn = false,
            scale = 0.75f,
        )

        assertEquals(0.75f, envelope.first().volume)
        assertEquals(0f, envelope.last().volume, absoluteTolerance = 0.000001f)
        assertTrue(envelope.zipWithNext().all { (left, right) -> left.volume >= right.volume })
    }

    @Test
    fun buildsCrossfadeEnvelopePairs() {
        val fadeIn = crossfadeFadeInEnvelopePoints(
            durationBytes = 80,
            volumeFactor = 0.5f,
        )
        val fadeOut = crossfadeFadeOutEnvelopePoints(
            startBytes = 20,
            durationBytes = 80,
            volumeFactor = 0.75f,
        )

        assertEquals(0L, fadeIn.first().first)
        assertEquals(0f, fadeIn.first().second)
        assertEquals(80L, fadeIn.last().first)
        assertEquals(0.5f, fadeIn.last().second)
        assertEquals(20L, fadeOut.first().first)
        assertEquals(0.75f, fadeOut.first().second)
        assertEquals(100L, fadeOut.last().first)
        assertEquals(0f, fadeOut.last().second, absoluteTolerance = 0.000001f)
    }
}
