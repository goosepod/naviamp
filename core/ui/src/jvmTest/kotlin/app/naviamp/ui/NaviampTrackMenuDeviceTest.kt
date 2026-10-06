package app.naviamp.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import app.naviamp.domain.settings.DesktopShortcutPlatform
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NaviampTrackMenuDeviceTest {
    @Test fun deviceChoicesStayOutOfTrackMenuWhileActiveRemoteControlCanBeStopped() = runDesktopComposeUiTest(1000, 740) {
        val remote = mutableStateOf<String?>(null)
        var pickerRequests = 0
        var stopRequests = 0
        setContent {
            NaviampWindowEnvironment(NaviampWindowController({ true }, NaviampWindowSnapshot()), DesktopShortcutPlatform.MacOS) {
                NaviampNowPlayingPanel(
                    nowPlaying = NowPlayingUi(
                        id = "song", title = "Song", subtitle = "Artist", stateLabel = "Paused",
                        castAvailable = true,
                        remoteOutputDeviceName = remote.value,
                        playbackOutputs = listOf(
                            NaviampPlaybackOutputUi(null, "Naviamp Desktop", selected = remote.value == null),
                            NaviampPlaybackOutputUi("pixel", "Pixel 10a", selected = remote.value != null),
                        ),
                    ),
                    colors = NaviampColors(),
                    actions = NaviampNowPlayingActions({}, {}, {}, {}, {}, {}, {},
                        onCastPicker = { pickerRequests++ },
                        onRemoteOutputAction = { stopRequests++ },
                    ),
                )
            }
        }
        onNodeWithContentDescription("Track actions").performClick()
        onNodeWithText("Playback device: Naviamp Desktop").assertDoesNotExist()
        onNodeWithText("Pixel 10a").assertDoesNotExist()
        onNodeWithText("Stop controlling Pixel 10a").assertDoesNotExist()
        onNodeWithText("Cast to a device").performClick()
        runOnIdle { assertEquals(1, pickerRequests); remote.value = "Pixel 10a" }
        onNodeWithContentDescription("Track actions").performClick()
        // Active remote context remains in the player badge, outside the track menu.
        onNodeWithText("Playback device: Pixel 10a").assertIsDisplayed()
        onNodeWithText("Pixel 10a").assertDoesNotExist()
        onNodeWithText("Stop controlling Pixel 10a").assertIsDisplayed().performClick()
        runOnIdle { assertEquals(1, stopRequests) }
    }
}
