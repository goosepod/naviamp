package app.naviamp.presentation

import app.naviamp.app.*
import kotlin.test.*

class NaviampCoreRemoteOutputUiTest {
    @Test fun castAndUpnpShowTheirSelectedDeviceAuthorityAndUnavailableState() {
        for (kind in listOf(NaviampRemoteOutputKind.Cast, NaviampRemoteOutputKind.Upnp)) {
            val selected = NaviampPlaybackOutputSelection.Remote(NaviampRemoteOutputTarget(kind, "receiver", "Living room"),
                1, NaviampRemoteOutputPhase.Connected, playbackAuthorityActive = true)
            val ui = naviampCoreRemoteOutputUi(true, selected)
            assertEquals("Living room", ui.selectedTargetName)
            assertTrue(ui.selected); assertTrue(ui.playbackActive); assertFalse(ui.unavailable)
            val failed = naviampCoreRemoteOutputUi(true, selected.copy(phase = NaviampRemoteOutputPhase.Unavailable, playbackAuthorityActive = false))
            assertTrue(failed.selected); assertTrue(failed.unavailable); assertFalse(failed.playbackActive)
        }
    }

    @Test fun localAndConnectDoNotAcquireACastOrUpnpIndicator() {
        val connect = NaviampPlaybackOutputSelection.Remote(NaviampRemoteOutputTarget(NaviampRemoteOutputKind.Connect, "speaker", "Connect speaker"),
            1, NaviampRemoteOutputPhase.Connected, playbackAuthorityActive = true)
        for (selection in listOf(NaviampPlaybackOutputSelection.Local, connect)) {
            val ui = naviampCoreRemoteOutputUi(false, selection)
            assertFalse(ui.available); assertFalse(ui.selected); assertFalse(ui.playbackActive); assertNull(ui.selectedTargetName)
        }
    }
}
