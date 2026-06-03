package app.naviamp.domain.playback

import kotlin.test.Test
import kotlin.test.assertEquals
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
}
