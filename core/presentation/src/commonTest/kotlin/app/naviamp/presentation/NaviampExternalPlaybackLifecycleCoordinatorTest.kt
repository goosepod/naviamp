package app.naviamp.presentation

import kotlin.test.Test
import kotlin.test.assertEquals

class NaviampExternalPlaybackLifecycleCoordinatorTest {
    @Test
    fun interruptionPausesPlayingTrackAndResumesWhenSystemPermits() {
        var state = NaviampExternalPlaybackState.Playing
        val commands = mutableListOf<String>()
        val coordinator = coordinator({ state }, commands)

        coordinator.interruptionBegan()
        state = NaviampExternalPlaybackState.Paused
        coordinator.interruptionEnded(shouldResume = true)

        assertEquals(listOf("pause", "play"), commands)
    }

    @Test
    fun interruptionDoesNotResumeWhenSystemRefusesOrPlaybackWasAlreadyPaused() {
        var state = NaviampExternalPlaybackState.Playing
        val commands = mutableListOf<String>()
        val coordinator = coordinator({ state }, commands)

        coordinator.interruptionBegan()
        state = NaviampExternalPlaybackState.Paused
        coordinator.interruptionEnded(shouldResume = false)
        coordinator.interruptionBegan()
        coordinator.interruptionEnded(shouldResume = true)

        assertEquals(listOf("pause"), commands)
    }

    @Test
    fun outputDisconnectPausesPlayingTrackAndCancelsPendingResume() {
        var state = NaviampExternalPlaybackState.Playing
        val commands = mutableListOf<String>()
        val coordinator = coordinator({ state }, commands)

        coordinator.interruptionBegan()
        state = NaviampExternalPlaybackState.Playing
        coordinator.outputDisconnected()
        state = NaviampExternalPlaybackState.Paused
        coordinator.interruptionEnded(shouldResume = true)

        assertEquals(listOf("pause", "pause"), commands)
    }

    private fun coordinator(
        state: () -> NaviampExternalPlaybackState,
        commands: MutableList<String>,
    ) = NaviampExternalPlaybackLifecycleCoordinator(
        snapshot = { NaviampExternalPlaybackSnapshot(state = state()) },
        play = { commands += "play" },
        pause = { commands += "pause" },
    )
}
