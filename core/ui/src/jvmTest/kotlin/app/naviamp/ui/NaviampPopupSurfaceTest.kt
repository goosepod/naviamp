package app.naviamp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NaviampPopupSurfaceTest {
    @Test fun ownedMenuSupportsArrowFocusAndEscape() = runComposeUiTest {
        var open by mutableStateOf(true)
        setContent {
            CompositionLocalProvider(LocalNaviampOwnedPopupWindows provides true) {
                Box(Modifier.size(700.dp, 500.dp)) {
                    NaviampDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        repeat(3) { NaviampDropdownMenuItem("Action $it", onClick = {}) }
                    }
                }
            }
        }
        onNodeWithText("Action 0").performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput { pressKey(Key.DirectionDown) }
        onNodeWithText("Action 1").assertIsFocused().performKeyInput { pressKey(Key.Escape) }
        onNodeWithText("Action 1").assertDoesNotExist()
        assertFalse(open)
    }

    @Test fun ownedMenuPaintsItsLabelsAfterEntrance() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalNaviampOwnedPopupWindows provides true) {
                Box(Modifier.size(700.dp, 500.dp)) {
                    NaviampDropdownMenu(expanded = true, onDismissRequest = {}) {
                        NaviampDropdownMenuItem("Visible action", onClick = {})
                    }
                }
            }
        }
        waitForIdle()
        val pixels = onNodeWithText("Visible action").captureToImage().toPixelMap()
        var visibleText = 0
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            val pixel = pixels[x, y]
            if (pixel.alpha > .5f && pixel.red > .7f && pixel.green > .7f && pixel.blue > .7f) visibleText++
        }
        assertTrue(visibleText > 50, "The menu must paint readable labels, not only expose semantics")
    }

    @Test fun ownedMenuKeepsBoundsAcrossHoverAndClosesOnRepeatedAnchorClick() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalNaviampOwnedPopupWindows provides true) {
                NaviampRowOverflowMenu(NaviampColors(), List(6) {
                    NaviampRowMenuItem("Action $it", NaviampTransportIcons.MoreVertical, {})
                })
            }
        }
        onNodeWithContentDescription("More actions").performMouseInput { click() }
        waitForIdle()
        val initial = onNodeWithText("Action 0").fetchSemanticsNode().boundsInRoot
        repeat(6) { index ->
            onNodeWithText("Action $index").performMouseInput { moveTo(center) }
            waitForIdle()
            assertEquals(initial, onNodeWithText("Action 0").fetchSemanticsNode().boundsInRoot)
        }
        onNodeWithContentDescription("More actions").performMouseInput { click() }
        onNodeWithText("Action 0").assertDoesNotExist()
    }

    @Test fun menuDismissesOnExternalActivationButDialogDraftSurvives() = runComposeUiTest {
        val active = mutableStateOf(true)
        var menuOpen by mutableStateOf(true)
        var dialogOpen by mutableStateOf(false)
        var draft by mutableStateOf("draft")
        setContent {
            CompositionLocalProvider(LocalNaviampOwnedPopupWindows provides true,
                LocalNaviampWindowGroupActive provides active.value) {
                Box(Modifier.size(700.dp, 500.dp)) {
                    NaviampDropdownMenu(menuOpen, { menuOpen = false }) {
                        NaviampDropdownMenuItem("Menu action", onClick = {})
                    }
                    if (dialogOpen) NaviampAlertDialog(
                        onDismissRequest = { dialogOpen = false },
                        confirmButton = { Text("Confirm") },
                        text = { androidx.compose.material3.OutlinedTextField(draft, { draft = it }) },
                    )
                }
            }
        }
        onNodeWithText("Menu action").assertExists()
        runOnIdle { active.value = false }
        onNodeWithText("Menu action").assertDoesNotExist()
        runOnIdle { active.value = true; dialogOpen = true }
        onNode(hasSetTextAction()).performTextReplacement("unsaved input")
        runOnIdle { active.value = false }
        assertTrue(dialogOpen)
        runOnIdle { active.value = true }
        onNodeWithText("unsaved input").assertExists()
    }

    @Test fun dialogContentDoesNotTriggerOutsideDismissal() = runComposeUiTest {
        var dismissed = false
        setContent {
            CompositionLocalProvider(LocalNaviampOwnedPopupWindows provides true) {
                Box(Modifier.size(700.dp, 500.dp)) {
                    NaviampAlertDialog(onDismissRequest = { dismissed = true },
                        confirmButton = { Text("Confirm") }, title = { Text("Settings") },
                        text = {
                            var checked by remember { mutableStateOf(false) }
                            Checkbox(checked, { checked = it }, Modifier.testTag("checkbox"))
                        })
                }
            }
        }
        onNodeWithTag("checkbox").performMouseInput { click() }
        onNodeWithTag("checkbox").assertIsOn()
        onAllNodes(isDialog()).assertCountEquals(1)
        runOnIdle { assertFalse(dismissed) }
    }
}
