package app.naviamp.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NaviampCastSessionControllerTest {
    @Test
    fun discoveryIsOrderedAndIgnoredAfterStop() {
        val effect = FakeCastEffect()
        val controller = NaviampCastSessionController(effect, NaviampPlaybackOutputSelectionController())
        controller.start()
        controller.start()
        assertEquals(1, effect.starts)

        controller.onTargetsChanged(listOf(
            NaviampCastTarget("b", "Kitchen"),
            NaviampCastTarget("a", "Bedroom"),
            NaviampCastTarget("b", "Kitchen duplicate"),
        ))
        assertEquals(listOf("a", "b"), controller.state.value.targets.map(NaviampCastTarget::id))

        controller.stop()
        controller.onTargetsChanged(listOf(NaviampCastTarget("c", "New")))
        assertTrue(controller.state.value.targets.isEmpty())
        assertEquals(1, effect.stops)
    }

    @Test
    fun displacedSessionCannotTakeAuthorityOrClearConnect() {
        val effect = FakeCastEffect()
        val outputs = NaviampPlaybackOutputSelectionController()
        val controller = NaviampCastSessionController(effect, outputs)
        controller.select(NaviampCastTarget("tv", "TV"))
        val castId = effect.connectedIds.single()
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
        controller.select(NaviampCastTarget("tv", "TV"))
        val castId = effect.connectedIds.single()
        controller.onConnected(castId, "TV")
        assertTrue(outputs.activatePlaybackAuthority(castId))

        controller.onDisconnected(castId)

        assertFalse(outputs.hasRemotePlaybackAuthority())
        assertEquals(NaviampRemoteOutputPhase.Unavailable,
            (outputs.state.value as NaviampPlaybackOutputSelection.Remote).phase)
        assertEquals(castId,
            (outputs.state.value as NaviampPlaybackOutputSelection.Remote).selectionId)
    }

    private class FakeCastEffect : NaviampCastSessionEffect {
        var starts = 0
        var stops = 0
        var disconnects = 0
        val connectedIds = mutableListOf<Long>()

        override fun start(listener: NaviampCastSessionListener) { starts++ }
        override fun stop() { stops++ }
        override fun connect(targetId: String, selectionId: Long) { connectedIds += selectionId }
        override fun disconnect() { disconnects++ }
    }
}
