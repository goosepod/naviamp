package app.naviamp.app

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NaviampCastSessionControllerTest {
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
        var starts = 0
        var stops = 0
        var disconnects = 0
        var loadResult = true
        val commands = mutableListOf<NaviampCastReceiverCommand>()
        override fun start(listener: NaviampCastSessionListener) { starts++ }
        override fun stop() { stops++ }
        override fun disconnect() { disconnects++ }
        override suspend fun load(selectionId: Long, media: NaviampCastReceiverMedia): Boolean = loadResult
        override suspend fun command(selectionId: Long, command: NaviampCastReceiverCommand): Boolean =
            commands.add(command)
    }
}
