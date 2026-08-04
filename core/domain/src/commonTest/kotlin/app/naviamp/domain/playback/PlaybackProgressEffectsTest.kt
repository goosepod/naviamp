package app.naviamp.domain.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackProgressEffectsTest {
    @Test
    fun appliesSharedProgressStateEffects() {
        val calls = mutableListOf<String>()
        val progress = PlaybackProgress(positionSeconds = 12.0, durationSeconds = 90.0)

        val result = applyPlaybackProgressEffects(
            plan = PlaybackProgressUpdatePlan(
                progress = progress,
                clearPendingSeek = true,
                clearPendingRestoreStart = true,
                shouldSavePlaybackPosition = true,
            ),
            applier = PlaybackProgressEffectApplier(
                clearPendingSeek = { calls += "seek" },
                clearPendingRestoreStart = { calls += "restore" },
                savePlaybackPosition = { calls += "save:$it" },
                reportPlaybackProgress = { calls += "report:$it" },
                updateProgress = { calls += "update:$it" },
            ),
        )

        assertEquals(progress, result.progress)
        assertEquals(listOf("seek", "restore", "save:$progress", "report:$progress", "update:$progress"), calls)
    }

    @Test
    fun preservesHostControlledUiThrottling() {
        var updated = false
        val progress = PlaybackProgress(positionSeconds = 4.0, durationSeconds = null)

        val result = applyPlaybackProgressEffects(
            plan = PlaybackProgressUpdatePlan(progress = progress),
            updateProgress = false,
            applier = PlaybackProgressEffectApplier(updateProgress = { updated = true }),
        )

        assertEquals(progress, result.progress)
        assertFalse(updated)
    }

    @Test
    fun resetClearsPendingStateWithoutReportingProgress() {
        var cleared = false
        var reset = false
        var reported = false

        val result = applyPlaybackProgressEffects(
            plan = PlaybackProgressUpdatePlan(resetToUnknown = true, clearPendingSeek = true),
            applier = PlaybackProgressEffectApplier(
                clearPendingSeek = { cleared = true },
                resetProgress = { reset = true },
                reportPlaybackProgress = { reported = true },
            ),
        )

        assertTrue(result.resetToUnknown)
        assertTrue(cleared)
        assertTrue(reset)
        assertFalse(reported)
    }
}
