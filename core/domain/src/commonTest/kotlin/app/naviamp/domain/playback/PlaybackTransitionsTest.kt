package app.naviamp.domain.playback

import app.naviamp.domain.ReplayGain
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.bass.BassActiveState
import app.naviamp.domain.bass.BassStreamInfo
import app.naviamp.domain.queue.PlaybackQueue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
    fun mapsBassMixerCapabilityToPlaybackFeatureSupport() {
        assertEquals(
            BassPlaybackFeatureSupport(
                supportsGapless = true,
                supportsCrossfade = true,
            ),
            bassPlaybackFeatureSupport(supportsMixer = true),
        )
        assertEquals(
            BassPlaybackFeatureSupport(
                supportsGapless = false,
                supportsCrossfade = false,
            ),
            bassPlaybackFeatureSupport(supportsMixer = false),
        )
    }

    @Test
    fun plansBassMixerCreationFromSourceInfoWithFallbacks() {
        val sourcePlan = planBassMixerCreation(
            sourceInfo = BassStreamInfo(frequency = 48_000, channels = 6),
            crossfadeDurationSeconds = 0,
        )
        val fallbackPlan = planBassMixerCreation(
            sourceInfo = BassStreamInfo(frequency = 0, channels = -1),
            crossfadeDurationSeconds = 4,
        )

        assertEquals(48_000, sourcePlan.frequency)
        assertEquals(6, sourcePlan.channels)
        assertTrue(sourcePlan.queueSources)
        assertEquals(DefaultBassMixerFrequency, fallbackPlan.frequency)
        assertEquals(DefaultBassMixerChannels, fallbackPlan.channels)
        assertFalse(fallbackPlan.queueSources)
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
    fun skipsCrossfadePrepareNextWhenTrackIsShorterThanFadeWindow() {
        val plan = planPrepareNextPlayback(
            progress = PlaybackProgress(positionSeconds = 1.0, durationSeconds = 5.0),
            nextQueueIndex = 1,
            alreadyPreparedNext = false,
            gaplessEnabled = false,
            supportsGapless = true,
            crossfadeDurationSeconds = 8,
            supportsCrossfade = true,
            gaplessPrepareWindowSeconds = 2.0,
        )

        assertFalse(plan.shouldPrepare)
        assertEquals(PrepareNextPlaybackReason.NotNeeded, plan.reason)
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
    fun plansPrepareNextQueuePlaybackWithSelectedTrack() {
        val one = track("one")
        val two = track("two")
        val plan = planPrepareNextQueuePlayback(
            queue = PlaybackQueue(listOf(one, two), currentIndex = 0),
            progress = PlaybackProgress(positionSeconds = 96.0, durationSeconds = 100.0),
            nextQueueIndex = 1,
            alreadyPreparedNext = false,
            gaplessEnabled = true,
            supportsGapless = true,
            crossfadeDurationSeconds = 5,
            supportsCrossfade = true,
            gaplessPrepareWindowSeconds = 2.0,
        )

        assertEquals(1, plan?.nextQueueIndex)
        assertEquals(two, plan?.track)
        assertEquals(5.0, plan?.prepareWindowSeconds)
        assertEquals(PrepareNextPlaybackReason.Crossfade, plan?.reason)
    }

    @Test
    fun skipsPrepareNextQueuePlaybackWhenGateOrTrackSelectionFails() {
        val queue = PlaybackQueue(listOf(track("one"), track("two")), currentIndex = 0)

        assertNull(
            planPrepareNextQueuePlayback(
                queue = queue,
                progress = PlaybackProgress(positionSeconds = 90.0, durationSeconds = 100.0),
                nextQueueIndex = 1,
                alreadyPreparedNext = false,
                gaplessEnabled = true,
                supportsGapless = true,
                crossfadeDurationSeconds = 5,
                supportsCrossfade = true,
                gaplessPrepareWindowSeconds = 2.0,
            ),
        )
        assertNull(
            planPrepareNextQueuePlayback(
                queue = queue,
                progress = PlaybackProgress(positionSeconds = 99.0, durationSeconds = 100.0),
                nextQueueIndex = 9,
                alreadyPreparedNext = false,
                gaplessEnabled = true,
                supportsGapless = true,
                crossfadeDurationSeconds = 5,
                supportsCrossfade = true,
                gaplessPrepareWindowSeconds = 2.0,
            ),
        )
    }

    @Test
    fun computesReplayGainAdjustmentForTrackMode() {
        val request = PlaybackRequest(
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
        )
        val adjustment = playbackReplayGainAdjustment(request)

        assertEquals(ReplayGainMode.Track, adjustment.mode)
        assertEquals(ReplayGainSource.Provider, adjustment.source)
        assertEquals(-6.0, adjustment.gainDb)
        assertEquals(0.5011872f, adjustment.volumeFactor, absoluteTolerance = 0.000001f)
        assertEquals(0.5011872f, playbackReplayGainFactor(request), absoluteTolerance = 0.000001f)
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
    fun selectsSourceHandleWhenPlaybackUsesSeparateMixerOutput() {
        assertEquals(8, playbackSourceHandle(playbackHandle = 7, sourceHandle = 8))
        assertEquals(7, playbackSourceHandle(playbackHandle = 7, sourceHandle = 0))
    }

    @Test
    fun computesUserVolumeFactorWithOptionalDucking() {
        assertEquals(0.8f, playbackUserVolumeFactor(80))
        assertEquals(0.2f, playbackUserVolumeFactor(80, transientDuckFactor = 0.25f))
        assertEquals(1f, playbackUserVolumeFactor(120))
        assertEquals(0f, playbackUserVolumeFactor(-10))
    }

    @Test
    fun keepsOnlyPositivePlaybackStartSeekPositions() {
        assertEquals(null, playbackStartSeekPosition(null))
        assertEquals(null, playbackStartSeekPosition(0.0))
        assertEquals(null, playbackStartSeekPosition(-1.0))
        assertEquals(12.5, playbackStartSeekPosition(12.5))
    }

    @Test
    fun plansBassMixerPlaybackUseFromBackendAndMediaRequirements() {
        val request = PlaybackRequest(url = "file:///track.flac", mediaId = "track-1")
        val streamOnlyRequest = request.copy(mediaId = null)

        assertTrue(
            shouldUseBassMixerPlayback(
                request = request,
                supportsMixer = true,
                requireMediaId = false,
            ),
        )
        assertFalse(
            shouldUseBassMixerPlayback(
                request = request,
                supportsMixer = false,
                requireMediaId = false,
            ),
        )
        assertTrue(
            shouldUseBassMixerPlayback(
                request = request,
                supportsMixer = true,
                requireMediaId = true,
            ),
        )
        assertFalse(
            shouldUseBassMixerPlayback(
                request = streamOnlyRequest,
                supportsMixer = true,
                requireMediaId = true,
            ),
        )
    }

    @Test
    fun preparesBassMixerSourceOnlyWhenPlaybackSourceAndMixerAreAvailable() {
        assertTrue(
            canPrepareBassMixerSource(
                playbackHandle = 7,
                currentSourceHandle = 8,
                supportsMixer = true,
            ),
        )
        assertFalse(
            canPrepareBassMixerSource(
                playbackHandle = 0,
                currentSourceHandle = 8,
                supportsMixer = true,
            ),
        )
        assertFalse(
            canPrepareBassMixerSource(
                playbackHandle = 7,
                currentSourceHandle = 0,
                supportsMixer = true,
            ),
        )
        assertFalse(
            canPrepareBassMixerSource(
                playbackHandle = 7,
                currentSourceHandle = 8,
                supportsMixer = false,
            ),
        )
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
    fun detectsPlaybackProgressAtEndWithTolerance() {
        assertTrue(
            isPlaybackProgressAtEnd(
                PlaybackProgress(positionSeconds = 99.5, durationSeconds = 100.0),
            ),
        )
        assertFalse(
            isPlaybackProgressAtEnd(
                PlaybackProgress(positionSeconds = 98.0, durationSeconds = 100.0),
            ),
        )
        assertFalse(isPlaybackProgressAtEnd(PlaybackProgress.Unknown))
    }

    @Test
    fun detectsPlaybackProgressOverrunAfterKnownDuration() {
        assertTrue(
            hasPlaybackProgressOverrunDuration(
                PlaybackProgress(positionSeconds = 100.6, durationSeconds = 100.0),
            ),
        )
        assertFalse(
            hasPlaybackProgressOverrunDuration(
                PlaybackProgress(positionSeconds = 100.2, durationSeconds = 100.0),
            ),
        )
        assertFalse(hasPlaybackProgressOverrunDuration(PlaybackProgress.Unknown))
    }

    @Test
    fun buildsVisualizerBandsFromFft() {
        val bands = visualizerBandsFromFft(
            fft = floatArrayOf(0f, 0.02f, 0.04f, 0.10f, 0.20f),
            bandCount = 2,
            gain = 10f,
        )

        assertEquals(2, bands.size)
        assertEquals(0.4f, bands[0], absoluteTolerance = 0.000001f)
        assertEquals(1f, bands[1], absoluteTolerance = 0.000001f)
        assertEquals(emptyList(), visualizerBandsFromFft(floatArrayOf(), bandCount = 2))
    }

    @Test
    fun buildsVisualizerFrameFromFft() {
        val frame = playbackVisualizerFrameFromFft(
            fft = floatArrayOf(0f, 0.1f, 0.2f),
            timestampMillis = 123L,
        )

        assertEquals(VisualizerBandCount, frame?.bands?.size)
        assertEquals(123L, frame?.timestampMillis)
        assertEquals(null, playbackVisualizerFrameFromFft(floatArrayOf()))
    }

    @Test
    fun mapsBassActiveStatesToPlaybackStates() {
        assertEquals(PlaybackState.Playing, playbackStateForBassActiveState(BassActiveState.Playing))
        assertEquals(PlaybackState.Loading, playbackStateForBassActiveState(BassActiveState.Stalled))
        assertEquals(PlaybackState.Paused, playbackStateForBassActiveState(BassActiveState.Paused))
        assertEquals(null, playbackStateForBassActiveState(BassActiveState.Stopped))
        assertEquals(null, playbackStateForBassActiveState(99))
    }

    @Test
    fun detectsFinishedPlaybackFromBassStatesAndProgress() {
        val atEnd = PlaybackProgress(positionSeconds = 99.5, durationSeconds = 100.0)
        val notAtEnd = PlaybackProgress(positionSeconds = 90.0, durationSeconds = 100.0)

        assertTrue(
            shouldFinishPlaybackForBassState(
                activeState = BassActiveState.Stopped,
                progress = atEnd,
            ),
        )
        assertTrue(
            shouldFinishPlaybackForBassState(
                activeState = BassActiveState.Playing,
                currentSourceActiveState = BassActiveState.Stopped,
                progress = atEnd,
            ),
        )
        assertFalse(
            shouldFinishPlaybackForBassState(
                activeState = BassActiveState.Stopped,
                progress = notAtEnd,
            ),
        )
        assertFalse(
            shouldFinishPlaybackForBassState(
                activeState = BassActiveState.Playing,
                currentSourceActiveState = BassActiveState.Playing,
                progress = atEnd,
            ),
        )
        assertTrue(
            shouldFinishPlaybackForBassState(
                activeState = BassActiveState.Playing,
                currentSourceActiveState = BassActiveState.Playing,
                progress = PlaybackProgress(positionSeconds = 100.6, durationSeconds = 100.0),
            ),
        )
    }

    @Test
    fun continuesBassPlaybackPollingUntilOutputStops() {
        assertFalse(shouldContinueBassPlaybackPolling(BassActiveState.Stopped))
        assertTrue(shouldContinueBassPlaybackPolling(BassActiveState.Playing))
        assertTrue(shouldContinueBassPlaybackPolling(BassActiveState.Stalled))
        assertTrue(shouldContinueBassPlaybackPolling(BassActiveState.Paused))
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
