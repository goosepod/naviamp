package app.naviamp.domain.playback

import app.naviamp.domain.bass.BassActiveState
import app.naviamp.domain.bass.BassPlaybackSnapshot
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BassOutputStartupRecoveryTest {
    @Test fun fullyBufferedOutputWithNoAudibleAdvanceIsReinitializedOnlyOnce() {
        val first = planBassOutputStartupRecovery(BassOutputStartupRecoveryState(), snapshot(), 100)
        assertFalse(first.reinitializeOutput)
        assertFalse(planBassOutputStartupRecovery(first.state, snapshot(), 2_599).reinitializeOutput)
        val recovery = planBassOutputStartupRecovery(first.state, snapshot(), 2_600)
        assertTrue(recovery.reinitializeOutput)
        assertFalse(planBassOutputStartupRecovery(recovery.state, snapshot(), 20_000).reinitializeOutput)
    }

    @Test fun actualClockAdvanceDisarmsStartupRecoveryEvenIfPlaybackLaterStopsAdvancing() {
        val first = planBassOutputStartupRecovery(BassOutputStartupRecoveryState(), snapshot(), 0)
        val moving = planBassOutputStartupRecovery(first.state, snapshot(audible = 0.03), 100)
        assertTrue(moving.state.complete)
        assertFalse(planBassOutputStartupRecovery(moving.state, snapshot(audible = 0.03), 10_000).reinitializeOutput)
    }

    @Test fun pausedAndInputStarvedTimeDoesNotCountTowardOutputRecovery() {
        val first = planBassOutputStartupRecovery(BassOutputStartupRecoveryState(), snapshot(), 0)
        val paused = planBassOutputStartupRecovery(first.state, snapshot(active = BassActiveState.Paused), 3_000)
        assertFalse(paused.reinitializeOutput)
        val resumed = planBassOutputStartupRecovery(paused.state, snapshot(), 10_000)
        assertFalse(resumed.reinitializeOutput)
        val starved = planBassOutputStartupRecovery(resumed.state, snapshot(active = BassActiveState.Stalled), 20_000)
        assertFalse(starved.reinitializeOutput)
        val loading = planBassOutputStartupRecovery(starved.state, snapshot(decoded = 0.1), 30_000)
        assertFalse(loading.reinitializeOutput)
        assertFalse(planBassOutputStartupRecovery(loading.state, snapshot(), 40_000).reinitializeOutput)
    }

    @Test fun missingInvalidOrInsufficientDecodedClocksCannotTriggerRecovery() {
        for (progress in listOf(
            PlaybackProgress.Unknown,
            PlaybackProgress(0.0, 12.0, null),
            PlaybackProgress(Double.NaN, 12.0, 1.5),
            PlaybackProgress(0.0, 12.0, Double.POSITIVE_INFINITY),
            PlaybackProgress(0.0, 12.0, 0.1),
        )) {
            val native = snapshot().copy(progress = progress)
            val first = planBassOutputStartupRecovery(BassOutputStartupRecoveryState(), native, 0)
            assertFalse(planBassOutputStartupRecovery(first.state, native, 20_000).reinitializeOutput)
        }
    }

    @Test fun queuedAudioAlsoProvesStarvationForADirectPreviewWithoutADecodeClock() {
        val direct = snapshot(decoded = 0.0).copy(bufferedOutputSeconds = 1.5)
        val first = planBassOutputStartupRecovery(BassOutputStartupRecoveryState(), direct, 0)
        assertTrue(planBassOutputStartupRecovery(first.state, direct, 2_500).reinitializeOutput)
        for (buffered in listOf(null, Double.NaN, Double.POSITIVE_INFINITY, -1.0, 0.1)) {
            val empty = direct.copy(bufferedOutputSeconds = buffered)
            val initial = planBassOutputStartupRecovery(BassOutputStartupRecoveryState(), empty, 0)
            assertFalse(planBassOutputStartupRecovery(initial.state, empty, 20_000).reinitializeOutput)
        }
    }

    @Test fun pausedDecodePositionIsNotEvidenceThatTheOutputClockAdvanced() {
        val first = planBassOutputStartupRecovery(BassOutputStartupRecoveryState(), snapshot(), 0)
        val paused = planBassOutputStartupRecovery(first.state, snapshot(audible = 1.5, active = BassActiveState.Paused), 1_000)
        assertFalse(paused.state.complete)
        val resumed = planBassOutputStartupRecovery(paused.state, snapshot(), 2_000)
        assertTrue(planBassOutputStartupRecovery(resumed.state, snapshot(), 4_500).reinitializeOutput)
    }

    private fun snapshot(audible: Double = 0.0, decoded: Double = 1.5, active: Int = BassActiveState.Playing) =
        BassPlaybackSnapshot(active, BassActiveState.Playing,
            PlaybackProgress(audible, 12.0, decoded), PlaybackStreamMetadata())
}
