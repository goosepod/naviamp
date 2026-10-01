package app.naviamp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import app.naviamp.domain.settings.DesktopShortcutPlatform
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class NaviampWindowUiTest {
    @Test fun buttonAndShortcutsUseTheSameWindowState() = runComposeUiTest {
        val requests = mutableListOf<NaviampWindowSnapshot>()
        val initial = NaviampWindowSnapshot(width = 1100f, height = 700f, x = 40f, y = 60f)
        val controller = NaviampWindowController({ requests.add(it); true }, initial)
        setContent {
            NaviampWindowEnvironment(controller, DesktopShortcutPlatform.MacOS) {
                NaviampWindowFullscreenButton(NaviampColors())
            }
        }
        onNodeWithContentDescription("Enter fullscreen").performClick()
        onNodeWithContentDescription("Exit fullscreen").assertIsDisplayed()
        onNodeWithTag("application-window").performKeyInput { pressKey(Key.Escape) }
        onNodeWithContentDescription("Enter fullscreen").assertIsDisplayed()
        onNodeWithTag("application-window").performKeyInput { pressKey(Key.F11) }
        onNodeWithContentDescription("Exit fullscreen").assertIsDisplayed()
        onNodeWithTag("application-window").performKeyInput {
            keyDown(Key.CtrlLeft); keyDown(Key.MetaLeft); pressKey(Key.F); keyUp(Key.MetaLeft); keyUp(Key.CtrlLeft)
        }
        runOnIdle { assertEquals(initial, requests.last()) }
    }

    @Test fun escapeDismissesMenuBeforeExitingFullscreen() = runComposeUiTest {
        val controller = NaviampWindowController({ true }, NaviampWindowSnapshot())
        controller.toggle()
        setContent {
            NaviampRasterEnvironment(null, true, false) {
                NaviampWindowEnvironment(controller, DesktopShortcutPlatform.Linux) {
                    Column {
                        NaviampWindowFullscreenButton(NaviampColors())
                        NaviampRowOverflowMenu(NaviampColors(), listOf(
                            NaviampRowMenuItem("Test action", NaviampTransportIcons.MoreVertical, {}),
                        ))
                    }
                }
            }
        }
        onNodeWithContentDescription("More actions").performClick()
        onNodeWithText("Test action").assertIsDisplayed()
        onNodeWithText("Test action").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
        onNodeWithText("Test action").performKeyInput { pressKey(Key.Escape) }
        onNodeWithText("Test action").assertDoesNotExist()
        onNodeWithContentDescription("Exit fullscreen").assertIsDisplayed()
        onNodeWithTag("application-window").performKeyInput { pressKey(Key.Escape) }
        onNodeWithContentDescription("Enter fullscreen").assertIsDisplayed()
    }

    @Test fun heldShortcutDoesNotRepeatedlyToggleTheWindow() = runComposeUiTest {
        var requests = 0
        val controller = NaviampWindowController({ requests++; true }, NaviampWindowSnapshot())
        setContent { NaviampWindowEnvironment(controller, DesktopShortcutPlatform.Windows) {
            NaviampWindowFullscreenButton(NaviampColors())
        } }
        onNodeWithTag("application-window").performKeyInput {
            keyDown(Key.F11)
            advanceEventTime(600)
            keyUp(Key.F11)
        }
        runOnIdle { assertEquals(1, requests) }
        onNodeWithTag("application-window").performKeyInput { pressKey(Key.F11) }
        runOnIdle { assertEquals(2, requests) }
    }

    @Test fun hostsWithoutAWindowEffectDoNotShowFullscreenControl() = runComposeUiTest {
        setContent { NaviampWindowEnvironment(null, null) { NaviampWindowFullscreenButton(NaviampColors()) } }
        onNodeWithTag("window-fullscreen").assertDoesNotExist()
    }
}
