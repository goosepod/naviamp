package app.naviamp.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NaviampCastSessionControllerTest {
    @Test
    fun stoppedNativePickerCannotSelectAfterRestart() {
        val effect = FakeCastEffect()
        val outputs = NaviampPlaybackOutputSelectionController()
        val controller = NaviampCastSessionController(effect, outputs)
        controller.start()
        val old = effect.listener
        controller.stop()
        controller.start()
        assertEquals(-1L, old.onTargetSelected(NaviampCastTarget("old", "Old TV")))
        assertEquals(NaviampPlaybackOutputSelection.Local, outputs.state.value)
        assertTrue(effect.listener.onTargetSelected(NaviampCastTarget("new", "New TV")) > 0)
    }

    @Test
    fun explicitSelectionWaitsForConnectionCallbackAndDoesNotClaimPlayback() = runTest {
        val effect = FakeCastEffect()
        val outputs = NaviampPlaybackOutputSelectionController()
        val controller = NaviampCastSessionController(effect, outputs)
        assertFalse(controller.selectTarget(discoveredTarget()))
        controller.start()
        assertFalse(controller.selectTarget(discoveredTarget().copy(endpoints = emptyList())))
        assertTrue(controller.selectTarget(discoveredTarget()))
        val selected = outputs.state.value as NaviampPlaybackOutputSelection.Remote
        assertEquals(NaviampRemoteOutputPhase.Connecting, selected.phase)
        assertFalse(outputs.hasRemotePlaybackAuthority())
        assertEquals(listOf(selected.selectionId to discoveredTarget()), effect.connections)
        controller.onConnected(selected.selectionId, "TV")
        assertEquals(NaviampRemoteOutputPhase.Connected,
            (outputs.state.value as NaviampPlaybackOutputSelection.Remote).phase)
        assertFalse(outputs.hasRemotePlaybackAuthority())
    }

    @Test
    fun explicitSelectionRejectsFailureAndLateCompletionAfterReturningLocal() = runTest {
        val effect = FakeCastEffect()
        val outputs = NaviampPlaybackOutputSelectionController()
        val controller = NaviampCastSessionController(effect, outputs)
        controller.start()
        effect.connectAction = { false }
        assertFalse(controller.selectTarget(discoveredTarget()))
        assertEquals(NaviampRemoteOutputPhase.Unavailable,
            (outputs.state.value as NaviampPlaybackOutputSelection.Remote).phase)
        effect.connectAction = { error("TLS connection failed") }
        assertFalse(controller.selectTarget(discoveredTarget()))
        val completion = CompletableDeferred<Boolean>()
        effect.connectAction = { completion.await() }
        var accepted: Boolean? = null
        val selection = launch { accepted = controller.selectTarget(discoveredTarget()) }
        runCurrent()
        val oldId = (outputs.state.value as NaviampPlaybackOutputSelection.Remote).selectionId
        controller.selectLocal()
        completion.complete(true)
        selection.join()
        controller.onConnected(oldId, "TV")
        assertEquals(false, accepted)
        assertEquals(NaviampPlaybackOutputSelection.Local, outputs.state.value)
    }

    @Test
    fun cancelledSelectionPropagatesCancellationAndRevokesConnection() = runTest {
        val effect = FakeCastEffect()
        val outputs = NaviampPlaybackOutputSelectionController()
        val controller = NaviampCastSessionController(effect, outputs)
        controller.start()
        effect.connectAction = { throw CancellationException("Selection cancelled") }
        try {
            controller.selectTarget(discoveredTarget())
            kotlin.test.fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            val selectedId = (outputs.state.value as NaviampPlaybackOutputSelection.Remote).selectionId
            controller.onConnected(selectedId, "Late TV")
            assertEquals(NaviampRemoteOutputPhase.Unavailable,
                (outputs.state.value as NaviampPlaybackOutputSelection.Remote).phase)
            assertEquals(1, effect.disconnects)
        }
    }

    @Test
    fun explicitSelectionCannotCompleteAcrossStopAndRestart() = runTest {
        val effect = FakeCastEffect()
        val outputs = NaviampPlaybackOutputSelectionController()
        val controller = NaviampCastSessionController(effect, outputs)
        controller.start()
        val completion = CompletableDeferred<Boolean>()
        effect.connectAction = { completion.await() }
        var accepted: Boolean? = null
        val selection = launch { accepted = controller.selectTarget(discoveredTarget()) }
        runCurrent()
        val oldId = (outputs.state.value as NaviampPlaybackOutputSelection.Remote).selectionId
        controller.stop()
        controller.start()
        completion.complete(true)
        selection.join()
        controller.onConnected(oldId, "Late TV")
        assertEquals(false, accepted)
        assertEquals(NaviampRemoteOutputPhase.Unavailable,
            (outputs.state.value as NaviampPlaybackOutputSelection.Remote).phase)
        assertFalse(outputs.hasRemotePlaybackAuthority())
    }

    private fun discoveredTarget() = NaviampCastDiscoveredTarget(
        NaviampCastTarget("tv", "TV"), listOf(NaviampCastEndpoint("192.0.2.1", 8009)),
    )

    @Test
    fun effectLifecycleIsIdempotent() {
        val effect = FakeCastEffect()
        val controller = NaviampCastSessionController(effect, NaviampPlaybackOutputSelectionController())
        controller.start()
        controller.start()
        assertEquals(1, effect.starts)
        controller.stop()
        controller.stop()
        assertEquals(1, effect.stops)
    }

    @Test
    fun displacedSessionCannotTakeAuthorityOrClearConnect() {
        val effect = FakeCastEffect()
        val outputs = NaviampPlaybackOutputSelectionController()
        val controller = NaviampCastSessionController(effect, outputs)
        val castId = controller.onTargetSelected(NaviampCastTarget("tv", "TV"))
        controller.onConnecting(castId)
        controller.onConnected(castId, "Living Room TV")
        assertEquals(NaviampRemoteOutputPhase.Connected,
            (outputs.state.value as NaviampPlaybackOutputSelection.Remote).phase)
        assertFalse(outputs.hasRemotePlaybackAuthority())

        val connectId = outputs.select(NaviampRemoteOutputTarget(
            NaviampRemoteOutputKind.Connect, "speaker", "Speaker",
        ))
        controller.onConnected(castId, "Stale TV")
        controller.onDisconnected(castId)
        controller.selectLocal()

        assertEquals(connectId,
            (outputs.state.value as NaviampPlaybackOutputSelection.Remote).selectionId)
        assertEquals(1, effect.disconnects)
    }

    @Test
    fun receiverLossRevokesAuthorityAndSelectionRemainsForRecovery() {
        val effect = FakeCastEffect()
        val outputs = NaviampPlaybackOutputSelectionController()
        val controller = NaviampCastSessionController(effect, outputs)
        val castId = controller.onTargetSelected(NaviampCastTarget("tv", "TV"))
        controller.onConnected(castId, "TV")
        assertTrue(outputs.activatePlaybackAuthority(castId))

        controller.onDisconnected(castId)

        assertFalse(outputs.hasRemotePlaybackAuthority())
        assertEquals(NaviampRemoteOutputPhase.Unavailable,
            (outputs.state.value as NaviampPlaybackOutputSelection.Remote).phase)
        assertEquals(castId,
            (outputs.state.value as NaviampPlaybackOutputSelection.Remote).selectionId)
    }

    @Test
    fun normalRouteStopReturnsToLocalWithoutDisconnectingAgain() {
        val effect = FakeCastEffect()
        val outputs = NaviampPlaybackOutputSelectionController()
        val controller = NaviampCastSessionController(effect, outputs)
        val castId = controller.onTargetSelected(NaviampCastTarget("tv", "TV"))
        controller.onConnected(castId, "TV")

        controller.onStopped(castId)

        assertEquals(NaviampPlaybackOutputSelection.Local, outputs.state.value)
        assertEquals(0, effect.disconnects)
    }

    @Test
    fun loadClaimsAuthorityOnlyAfterReceiverAcceptsAndIgnoresStaleStatus() = runTest {
        val effect = FakeCastEffect()
        val outputs = NaviampPlaybackOutputSelectionController()
        val controller = NaviampCastSessionController(effect, outputs)
        val first = controller.onTargetSelected(NaviampCastTarget("tv", "TV"))
        controller.onConnected(first, "TV")
        val media = NaviampCastReceiverMedia(
            "http://192.0.2.1/media", "audio/mpeg", "Song", "Artist", null, null,
            60_000, 8_000, true,
        )
        effect.loadResult = false
        assertFalse(controller.load(first, media))
        assertFalse(outputs.hasRemotePlaybackAuthority())
        effect.loadResult = true
        assertTrue(controller.load(first, media))
        assertFalse(outputs.hasRemotePlaybackAuthority())
        assertTrue(controller.activatePlaybackAuthority(first))
        assertTrue(outputs.hasRemotePlaybackAuthority())
        assertTrue(controller.command(first, NaviampCastReceiverCommand.Pause))

        val second = controller.onTargetSelected(NaviampCastTarget("tv", "TV"))
        controller.onConnected(second, "TV")
        controller.onMediaStatus(first, NaviampCastReceiverStatus(NaviampCastReceiverPlayerState.Playing, 9_000, null, null))
        assertTrue(controller.mediaStatus.value == null)
        assertFalse(controller.command(first, NaviampCastReceiverCommand.Play))
        assertEquals(listOf<NaviampCastReceiverCommand>(NaviampCastReceiverCommand.Pause), effect.commands)
    }

    private class FakeCastEffect : NaviampCastSessionEffect {
        lateinit var listener: NaviampCastSessionListener
        var starts = 0
        var stops = 0
        var disconnects = 0
        var loadResult = true
        var connectAction: suspend () -> Boolean = { true }
        val connections = mutableListOf<Pair<Long, NaviampCastDiscoveredTarget>>()
        val commands = mutableListOf<NaviampCastReceiverCommand>()
        override fun start(listener: NaviampCastSessionListener) { starts++; this.listener = listener }
        override fun stop() { stops++ }
        override fun disconnect() { disconnects++ }
        override suspend fun connect(selectionId: Long, target: NaviampCastDiscoveredTarget): Boolean {
            connections += selectionId to target
            return connectAction()
        }
        override suspend fun load(selectionId: Long, media: NaviampCastReceiverMedia): Boolean = loadResult
        override suspend fun command(selectionId: Long, command: NaviampCastReceiverCommand): Boolean =
            commands.add(command)
    }
}
