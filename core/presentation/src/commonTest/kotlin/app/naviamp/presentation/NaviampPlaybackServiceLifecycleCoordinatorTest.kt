package app.naviamp.presentation

import kotlin.test.Test
import kotlin.test.assertEquals

class NaviampPlaybackServiceLifecycleCoordinatorTest {
    @Test
    fun rejectedBackgroundStartRetriesOnceOnVisibilityWithoutPolling() {
        val events = mutableListOf<String>()
        var allowed = false
        val coordinator = NaviampPlaybackServiceLifecycleCoordinator(
            start = { events.add("start"); allowed }, stop = { events.add("stop") },
        )
        coordinator.retentionChanged(true)
        repeat(10) {
            coordinator.retentionChanged(true)
            coordinator.visibilityChanged(false)
        }
        assertEquals(listOf("start"), events)
        allowed = true
        coordinator.visibilityChanged(true)
        coordinator.visibilityChanged(true)
        coordinator.retentionChanged(true)
        assertEquals(listOf("start", "start"), events)
        coordinator.visibilityChanged(false)
        coordinator.visibilityChanged(true)
        coordinator.retentionChanged(false)
        assertEquals(listOf("start", "start", "stop"), events)
    }

    @Test
    fun stoppedPlaybackCancelsDeferredStart() {
        var starts = 0
        var stops = 0
        val coordinator = NaviampPlaybackServiceLifecycleCoordinator(
            start = { starts++; false }, stop = { stops++ },
        )
        coordinator.retentionChanged(true)
        coordinator.retentionChanged(false)
        coordinator.visibilityChanged(true)
        assertEquals(1, starts)
        assertEquals(0, stops)
    }

    @Test
    fun repeatedRejectedVisibilityRetriesWaitForAnotherVisibilityTransition() {
        var starts = 0
        val coordinator = NaviampPlaybackServiceLifecycleCoordinator(start = { starts++; false }, stop = {})
        coordinator.retentionChanged(true)
        repeat(10) { coordinator.visibilityChanged(true) }
        assertEquals(2, starts)
        coordinator.visibilityChanged(false)
        coordinator.visibilityChanged(true)
        assertEquals(3, starts)
    }

    @Test
    fun pauseAndTrackTransitionsThatRetainTheSurfaceDoNotRestartIt() {
        val events = mutableListOf<String>()
        val coordinator = NaviampPlaybackServiceLifecycleCoordinator(
            start = { events.add("start"); true }, stop = { events.add("stop") },
        )
        coordinator.visibilityChanged(true)
        coordinator.retentionChanged(true)
        repeat(10) { coordinator.retentionChanged(true) }
        coordinator.retentionChanged(false)
        coordinator.retentionChanged(false)
        coordinator.retentionChanged(true)
        assertEquals(listOf("start", "stop", "start"), events)
    }
}
