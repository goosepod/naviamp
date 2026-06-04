package app.naviamp.domain.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackPrefetchTest {
    @Test
    fun initialStatsClampConfiguredDepth() {
        assertEquals(
            MaxAudioPrefetchDepth,
            initialAudioPrefetchStats(enabled = true, configuredDepth = 50).configuredDepth,
        )
    }

    @Test
    fun startedResetsRunCounters() {
        val stats = AudioPrefetchStats(
            completed = 2,
            failed = 1,
            sidecarFailed = 1,
            lastError = "old",
            lastSidecarError = "sidecar",
        ).started(3)

        assertTrue(stats.running)
        assertEquals(3, stats.queued)
        assertEquals(0, stats.completed)
        assertEquals(0, stats.failed)
        assertEquals(null, stats.lastError)
        assertEquals(null, stats.lastSidecarError)
    }

    @Test
    fun audioSuccessCountsSidecarResult() {
        val stats = AudioPrefetchStats()
            .audioSuccess(PlaybackSidecarPrepResult())
            .audioSuccess(PlaybackSidecarPrepResult(failed = 1, lastError = "lyrics failed"))

        assertEquals(2, stats.completed)
        assertEquals(1, stats.sidecarCompleted)
        assertEquals(1, stats.sidecarFailed)
        assertEquals("lyrics failed", stats.lastSidecarError)
    }

    @Test
    fun finishedClearsRunningFlag() {
        assertFalse(AudioPrefetchStats(running = true).finished().running)
    }
}
