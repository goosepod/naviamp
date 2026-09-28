package app.naviamp.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NaviampPlaybackOutputSelectionControllerTest {
    @Test
    fun selectingCastDisplacesConnectAndInvalidatesItsSessionCallbacks() {
        val controller = NaviampPlaybackOutputSelectionController()
        val connectSelection = controller.select(target(NaviampRemoteOutputKind.Connect, "tv"))
        assertTrue(controller.connected(connectSelection))
        assertTrue(controller.activatePlaybackAuthority(connectSelection))

        val castSelection = controller.select(target(NaviampRemoteOutputKind.Cast, "speaker"))

        assertFalse(controller.hasRemotePlaybackAuthority())
        assertFalse(controller.reconnecting(connectSelection))
        assertFalse(controller.activatePlaybackAuthority(connectSelection))
        assertEquals(
            NaviampPlaybackOutputSelection.Remote(
                target(NaviampRemoteOutputKind.Cast, "speaker"), castSelection,
                NaviampRemoteOutputPhase.Armed,
            ),
            controller.state.value,
        )
    }

    @Test
    fun staleCallbackFromSameReceiverCannotReclaimOutputAfterReselection() {
        val controller = NaviampPlaybackOutputSelectionController()
        val oldSelection = controller.select(target(NaviampRemoteOutputKind.Cast, "speaker"))
        val newSelection = controller.select(target(NaviampRemoteOutputKind.Cast, "speaker"))

        assertFalse(controller.connected(oldSelection))
        assertFalse(controller.hasRemotePlaybackAuthority())
        assertTrue(controller.connected(newSelection, "Living Room"))
        assertTrue(controller.activatePlaybackAuthority(newSelection))
        assertTrue(controller.hasRemotePlaybackAuthority())
        assertEquals("Living Room", (controller.state.value as NaviampPlaybackOutputSelection.Remote).target.displayName)
    }

    @Test
    fun receiverLossPreservesIntentButRevokesActivePlaybackAuthorityUntilReconnected() {
        val controller = NaviampPlaybackOutputSelectionController()
        val selection = controller.select(target(NaviampRemoteOutputKind.Cast, "speaker"))
        controller.connected(selection)
        controller.activatePlaybackAuthority(selection)

        assertTrue(controller.reconnecting(selection))
        assertFalse(controller.hasRemotePlaybackAuthority())
        assertEquals(NaviampRemoteOutputPhase.Reconnecting,
            (controller.state.value as NaviampPlaybackOutputSelection.Remote).phase)
        assertFalse(controller.activatePlaybackAuthority(selection))
        assertTrue(controller.connected(selection))
        assertFalse(controller.hasRemotePlaybackAuthority())
        assertTrue(controller.activatePlaybackAuthority(selection))
        assertTrue(controller.hasRemotePlaybackAuthority())
    }

    @Test
    fun selectingLocalRejectsLateRemoteEvents() {
        val controller = NaviampPlaybackOutputSelectionController()
        val selection = controller.select(target(NaviampRemoteOutputKind.Cast, "speaker"))

        controller.selectLocal()

        assertFalse(controller.connected(selection))
        assertFalse(controller.activatePlaybackAuthority(selection))
        assertFalse(controller.hasRemotePlaybackAuthority())
        assertEquals(NaviampPlaybackOutputSelection.Local, controller.state.value)
    }

    private fun target(kind: NaviampRemoteOutputKind, id: String) =
        NaviampRemoteOutputTarget(kind, id, id)
}
