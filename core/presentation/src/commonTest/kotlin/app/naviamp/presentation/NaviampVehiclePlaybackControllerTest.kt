package app.naviamp.presentation

import app.naviamp.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class NaviampVehiclePlaybackControllerTest {
    private val labels = NaviampVehiclePlaybackLabels("Radio", "Favorite", "Unfavorite", "Shuffle", "Off", "All", "One", "Failed", "Cancel")
    private fun product() = MutableStateFlow(NaviampCoreState(shell = NaviampAppShellUiState(
        connectionSettings = NaviampConnectionSettingsUi(currentSourceId = "source"),
        nowPlaying = NowPlayingUi(id = "song", title = "Song", subtitle = "Artist", stateLabel = "Paused",
            canStartRadio = true, canFavorite = true, shuffleEnabled = true, canRepeat = true),
    )))
    private fun TestScope.controller(product: MutableStateFlow<NaviampCoreState>,
        execute: suspend (NaviampCoreCommand) -> NaviampCoreCommandResult = { NaviampCoreCommandResult.Completed }) =
        NaviampVehiclePlaybackController(backgroundScope, product, NaviampCoreExternalPlaybackBridge(product, {}), execute, labels, 100)
    private fun MutableStateFlow<NaviampCoreState>.playing(change: (NowPlayingUi) -> NowPlayingUi) {
        value = value.copy(shell = value.shell.copy(nowPlaying = change(value.shell.nowPlaying!!)))
    }

    @Test fun controlsFollowSharedOrderingCapabilitiesAndActiveState() = runTest {
        val product = product()
        val controller = controller(product)
        assertEquals(listOf("StartRadio", "Favorite", "ShuffleOff", "RepeatOff"), controller.state.value.buttons.map { it.action.name })
        product.playing { it.copy(favoriteActive = true, shuffleActive = true, repeatMode = NaviampRepeatMode.Track) }
        runCurrent()
        assertEquals(listOf(false, true, true, true), controller.state.value.buttons.map { it.selected })
        assertEquals(listOf("Radio", "Unfavorite", "Shuffle", "One"), controller.state.value.buttons.map { it.title })
        product.playing { it.copy(canFavorite = false, canStartRadio = false, shuffleEnabled = false, canRepeat = false) }
        runCurrent()
        assertTrue(controller.state.value.buttons.isEmpty())
        controller.close()
    }

    @Test fun liveIdleAndRemoteAuthorityDoNotExposeUnsupportedControls() = runTest {
        val product = product()
        val controller = controller(product)
        product.value = product.value.copy(shell = product.value.shell.copy(connect = product.value.shell.connect.copy(remotePlaybackAuthorityActive = true)))
        runCurrent()
        assertFalse(controller.state.value.buttons.any { it.action == NaviampExternalPlaybackAction.StartRadio })
        product.playing { it.copy(isLive = true) }
        runCurrent()
        assertTrue(controller.state.value.buttons.isEmpty())
        product.playing { it.copy(id = "", isLive = false) }
        runCurrent()
        assertTrue(controller.state.value.buttons.isEmpty())
        controller.close()
    }

    @Test fun progressDoesNotInvalidateControlsButSourceAndOccurrenceDo() = runTest {
        val product = product()
        val commands = mutableListOf<NaviampCoreCommand>()
        val controller = controller(product) { commands += it; NaviampCoreCommandResult.PlaybackActionApplied(true) }
        runCurrent()
        val original = controller.state.value
        product.playing { it.copy(positionSeconds = 20.0, volumePercent = 42) }
        runCurrent()
        assertEquals(original, controller.state.value)
        for (change in listOf<(NaviampCoreState) -> NaviampCoreState>(
            { it.copy(sourceGeneration = it.sourceGeneration + 1) },
            { it.copy(playbackSelectionGeneration = it.playbackSelectionGeneration + 1) },
        )) {
            val old = controller.state.value
            product.value = change(product.value)
            controller.select(NaviampExternalPlaybackAction.StartRadio, old.revision)
        }
        assertTrue(commands.isEmpty())
        controller.close()
    }

    @Test fun eachControlDelegatesAndWaitsForAcknowledgedStateIncludingRemoteDelay() = runTest {
        for (action in listOf(NaviampExternalPlaybackAction.Favorite, NaviampExternalPlaybackAction.ShuffleOff,
            NaviampExternalPlaybackAction.RepeatOff, NaviampExternalPlaybackAction.StartRadio)) {
            val product = product()
            val commands = mutableListOf<NaviampCoreCommand>()
            val controller = controller(product) {
                commands += it
                if (action == NaviampExternalPlaybackAction.StartRadio) NaviampCoreCommandResult.PlaybackActionApplied(true)
                else NaviampCoreCommandResult.Completed
            }
            val old = controller.state.value
            controller.select(action, old.revision)
            controller.select(action, old.revision)
            runCurrent()
            assertEquals(1, commands.size, action.name)
            if (action != NaviampExternalPlaybackAction.StartRadio) {
                assertTrue(controller.state.value.buttons.none { it.enabled })
                product.playing { it.copy(favoriteActive = true, shuffleActive = true, repeatMode = NaviampRepeatMode.Queue) }
                runCurrent()
            }
            assertTrue(controller.state.value.buttons.all { it.enabled }, action.name)
            assertNull(controller.state.value.alert)
            if (action == NaviampExternalPlaybackAction.StartRadio) assertEquals("song", assertIs<NaviampCoreCommand.NowPlaying.CurrentTrack>(commands.single()).request.target?.trackId)
            controller.close()
        }
    }

    @Test fun repeatCyclesThroughAllThreeModes() = runTest {
        val product = product()
        val controller = controller(product) {
            product.playing { it.copy(repeatMode = when (it.repeatMode) {
                NaviampRepeatMode.Off -> NaviampRepeatMode.Queue
                NaviampRepeatMode.Queue -> NaviampRepeatMode.Track
                NaviampRepeatMode.Track -> NaviampRepeatMode.Off
            }) }
            NaviampCoreCommandResult.Completed
        }
        for ((action, next) in listOf(NaviampExternalPlaybackAction.RepeatOff to "RepeatQueue",
            NaviampExternalPlaybackAction.RepeatQueue to "RepeatTrack", NaviampExternalPlaybackAction.RepeatTrack to "RepeatOff")) {
            controller.select(action, controller.state.value.revision)
            runCurrent()
            assertEquals(next, controller.state.value.buttons.last().action.name)
            assertNull(controller.state.value.alert)
        }
        controller.close()
    }

    @Test fun rejectionExceptionAndTimeoutProduceDismissibleTranslatedFailureAndPermitRetry() = runTest {
        for (mode in listOf("rejected", "exception", "timeout")) {
            val product = product()
            var fail = true
            val controller = controller(product) {
                if (!fail) NaviampCoreCommandResult.PlaybackActionApplied(true)
                else when (mode) {
                    "rejected" -> NaviampCoreCommandResult.PlaybackActionApplied(false)
                    "exception" -> error("secret provider diagnostic")
                    else -> { awaitCancellation() }
                }
            }
            controller.select(NaviampExternalPlaybackAction.StartRadio, controller.state.value.revision)
            advanceTimeBy(101); runCurrent()
            val alert = assertNotNull(controller.state.value.alert, mode)
            assertEquals("Failed", alert.title)
            assertEquals("Cancel", alert.dismiss)
            controller.dismissAlert(alert.id + 1)
            assertNotNull(controller.state.value.alert)
            controller.dismissAlert(alert.id)
            assertNull(controller.state.value.alert)
            fail = false
            controller.select(NaviampExternalPlaybackAction.StartRadio, controller.state.value.revision)
            runCurrent()
            assertNull(controller.state.value.alert)
            controller.close()
        }
    }

    @Test fun sourceTrackOccurrenceAndDetachCancelOnlySceneRequestWithoutStaleFailure() = runTest {
        for (mode in listOf("source", "track", "occurrence", "detach")) {
            val product = product()
            var cancelled = false
            val controller = controller(product) {
                try { awaitCancellation() } finally { cancelled = true }
            }
            controller.select(NaviampExternalPlaybackAction.StartRadio, controller.state.value.revision)
            runCurrent()
            when (mode) {
                "source" -> product.value = product.value.copy(sourceGeneration = 1)
                "track" -> product.playing { it.copy(id = "next") }
                "occurrence" -> product.value = product.value.copy(playbackSelectionGeneration = 1)
                else -> controller.close()
            }
            runCurrent()
            assertTrue(cancelled, mode)
            assertNull(controller.state.value.alert, mode)
            assertTrue(controller.state.value.buttons.all { it.enabled })
            controller.close()
        }
    }
}
