package app.naviamp.presentation

import kotlin.test.Test
import kotlin.test.assertEquals

class NaviampExternalPlaybackLifecycleCoordinatorTest {
    @Test fun anotherBeginBeforeAcknowledgmentRevokesTheEarlierEndUntilTheNewInterruptionEnds() {
        var state = playable()
        val calls = mutableListOf<String>()
        val coordinator = NaviampExternalPlaybackLifecycleCoordinator({ state }, { calls += "play" }, { calls += "pause" })
        coordinator.interruptionBegan()
        coordinator.interruptionEnded(true)
        coordinator.interruptionBegan()
        state = state.copy(state = NaviampExternalPlaybackState.Paused)
        coordinator.onSnapshot(state)
        assertEquals(listOf("pause"), calls)
        coordinator.interruptionEnded(true)
        assertEquals(listOf("pause", "play"), calls)
    }

    @Test fun repeatedBeginBeforePauseAcknowledgmentRequestsOnlyOnePauseAndDefersResume() {
        var state = playable()
        val calls = mutableListOf<String>()
        val coordinator = NaviampExternalPlaybackLifecycleCoordinator({ state }, { calls += "play" }, { calls += "pause" })
        coordinator.interruptionBegan()
        coordinator.interruptionBegan()
        coordinator.interruptionEnded(true)
        coordinator.interruptionEnded(true)
        assertEquals(listOf("pause"), calls)
        state = state.copy(state = NaviampExternalPlaybackState.Paused)
        coordinator.onSnapshot(state)
        coordinator.onSnapshot(state)
        assertEquals(listOf("pause", "play"), calls)
    }

    @Test fun sourceReplacementCapabilityLossAndTerminalStatesCannotInheritAnInterruptionResume() {
        for (replacement in listOf(
            playable().copy(current = NaviampExternalMediaItem("replacement", "Replacement", "")),
            playable().copy(currentQueueIndex = 1),
            playable().copy(canPlayPause = false),
            playable().copy(state = NaviampExternalPlaybackState.Idle),
            playable().copy(state = NaviampExternalPlaybackState.Loading),
            playable().copy(current = null),
        )) {
            var state = playable()
            val calls = mutableListOf<String>()
            val coordinator = NaviampExternalPlaybackLifecycleCoordinator({ state }, { calls += "play" }, { calls += "pause" })
            coordinator.interruptionBegan()
            state = replacement
            coordinator.onSnapshot(state)
            state = playable().copy(state = NaviampExternalPlaybackState.Paused)
            coordinator.onSnapshot(state)
            coordinator.interruptionEnded(true)
            assertEquals(listOf("pause"), calls, "replacement=$replacement")
        }
    }

    @Test fun disconnectOrExplicitIntentBeforePauseAcknowledgmentCancelsDeferredResume() {
        for (disconnect in listOf(true, false)) {
            var state = playable()
            val calls = mutableListOf<String>()
            val coordinator = NaviampExternalPlaybackLifecycleCoordinator({ state }, { calls += "play" }, { calls += "pause" })
            coordinator.interruptionBegan()
            coordinator.interruptionEnded(true)
            if (disconnect) coordinator.outputDisconnected() else coordinator.cancelPendingResume()
            state = state.copy(state = NaviampExternalPlaybackState.Paused)
            coordinator.onSnapshot(state)
            coordinator.interruptionEnded(true)
            assertEquals(if (disconnect) listOf("pause", "pause") else listOf("pause"), calls)
        }
    }

    @Test
    fun repeatedInterruptionPausesPlayingTrackAndResumesOnceWhenSystemPermits() {
        var state = NaviampExternalPlaybackState.Playing
        val commands = mutableListOf<String>()
        val coordinator = coordinator({ state }, commands)

        coordinator.interruptionBegan()
        state = NaviampExternalPlaybackState.Paused
        coordinator.interruptionBegan()
        coordinator.interruptionEnded(shouldResume = true)
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

    private fun identity() = NaviampExternalPlaybackResumeIdentity("source", 1, 1, 1, 1, "track", 0, false)
    private fun playing() = NaviampExternalPlaybackSnapshot(state = NaviampExternalPlaybackState.Playing,
        current = NaviampExternalMediaItem("track", "Track", ""), canPlayPause = true, resumeIdentity = identity())

    @Test fun sourceSelectionIntentOccurrenceAndSleepTimerChangesPreventResume() {
        val original = identity()
        for (replacement in listOf(original.copy(sourceId = "other"), original.copy(sourceGeneration = 2),
            original.copy(accountGeneration = 2), original.copy(selectionGeneration = 2),
            original.copy(intentGeneration = 2), original.copy(mediaId = "other"), original.copy(queueIndex = 1),
            original.copy(sleepTimerActive = true))) {
            var value = playing(); val actions = mutableListOf<String>()
            val owner = NaviampExternalPlaybackLifecycleCoordinator({ value }, { actions += "play" }, { actions += "pause" })
            owner.interruptionBegan()
            value = value.copy(state = NaviampExternalPlaybackState.Paused, resumeIdentity = replacement)
            owner.interruptionEnded(true)
            assertEquals(listOf("pause"), actions, replacement.toString())
        }
    }
    @Test fun duplicateBeganCannotRearmAChangedTrack() {
        var value = playing(); val actions = mutableListOf<String>()
        val owner = NaviampExternalPlaybackLifecycleCoordinator({ value }, { actions += "play" }, { actions += "pause" })
        owner.interruptionBegan()
        value = value.copy(resumeIdentity = identity().copy(mediaId = "other"))
        owner.interruptionBegan(); value = value.copy(state = NaviampExternalPlaybackState.Paused)
        owner.interruptionEnded(true); assertEquals(listOf("pause"), actions)
    }
    @Test fun remoteOutputsAndPendingConnectionsNeverResumeFromLocalAudioEvents() {
        for (mode in listOf("remote", "connecting", "uncontrollable", "already-playing", "idle")) {
            var value = playing(); val actions = mutableListOf<String>()
            val owner = NaviampExternalPlaybackLifecycleCoordinator({ value }, { actions += "play" }, { actions += "pause" })
            owner.interruptionBegan()
            value = value.copy(state = NaviampExternalPlaybackState.Paused)
            value = when (mode) {
                "remote" -> value.copy(localPlayback = false)
                "connecting" -> value.copy(connectionPending = true)
                "uncontrollable" -> value.copy(canPlayPause = false)
                "already-playing" -> value.copy(state = NaviampExternalPlaybackState.Playing)
                else -> value.copy(state = NaviampExternalPlaybackState.Idle)
            }
            owner.interruptionEnded(true); assertEquals(listOf("pause"), actions, mode)
        }
        val actions = mutableListOf<String>()
        val owner = NaviampExternalPlaybackLifecycleCoordinator({ playing().copy(localPlayback = false) }, { actions += "play" }, { actions += "pause" })
        owner.interruptionBegan(); owner.outputDisconnected(); owner.interruptionEnded(true)
        assertEquals(emptyList(), actions)
    }
    @Test fun loadingIsStoppedAndNeverAutomaticallyStartedAfterInterruptionOrOutputLoss() {
        for (outputLoss in listOf(false, true)) {
            val actions = mutableListOf<String>()
            val owner = NaviampExternalPlaybackLifecycleCoordinator({ playing().copy(state = NaviampExternalPlaybackState.Loading, canPlayPause = false) },
                { actions += "play" }, { actions += "pause" }, { actions += "stop" })
            if (outputLoss) owner.outputDisconnected() else owner.interruptionBegan()
            owner.interruptionEnded(true); assertEquals(listOf("stop"), actions)
        }
    }
    @Test fun disconnectWhilePausedCancelsEngineResumeAndCloseRejectsQueuedNotifications() {
        var value = playing(); val actions = mutableListOf<String>()
        val owner = NaviampExternalPlaybackLifecycleCoordinator({ value }, { actions += "play" }, { actions += "pause" })
        owner.interruptionBegan(); value = value.copy(state = NaviampExternalPlaybackState.Paused)
        owner.outputDisconnected(); owner.interruptionEnded(true); owner.close()
        value = playing(); owner.interruptionBegan(); owner.interruptionEnded(true); owner.outputDisconnected()
        assertEquals(listOf("pause", "pause"), actions)
    }

    @Test
    fun latePauseAcknowledgmentCannotResumeAnIdenticalItemFromAnOldSource() {
        var state = playing()
        val calls = mutableListOf<String>()
        val coordinator = NaviampExternalPlaybackLifecycleCoordinator({ state }, { calls += "play" }, { calls += "pause" })
        coordinator.interruptionBegan()
        coordinator.interruptionEnded(shouldResume = true)

        state = state.copy(
            state = NaviampExternalPlaybackState.Paused,
            resumeIdentity = identity().copy(sourceGeneration = 2),
        )
        coordinator.onSnapshot(state)
        coordinator.interruptionEnded(shouldResume = true)

        assertEquals(listOf("pause"), calls)
    }

    @Test
    fun aDenialRevokesAnEarlyResumeRecommendationBeforePauseAcknowledgment() {
        var state = playing()
        val calls = mutableListOf<String>()
        val coordinator = NaviampExternalPlaybackLifecycleCoordinator({ state }, { calls += "play" }, { calls += "pause" })
        coordinator.interruptionBegan()
        coordinator.interruptionEnded(shouldResume = true)
        coordinator.interruptionEnded(shouldResume = false)

        state = state.copy(state = NaviampExternalPlaybackState.Paused)
        coordinator.onSnapshot(state)

        assertEquals(listOf("pause"), calls)
    }

    private fun coordinator(
        state: () -> NaviampExternalPlaybackState,
        commands: MutableList<String>,
    ) = NaviampExternalPlaybackLifecycleCoordinator(
        snapshot = { playable().copy(state = state()) },
        play = { commands += "play" },
        pause = { commands += "pause" },
    )

    private fun playable() = NaviampExternalPlaybackSnapshot(state = NaviampExternalPlaybackState.Playing,
        current = NaviampExternalMediaItem("track", "Track", ""), canPlayPause = true, currentQueueIndex = 0)
}
