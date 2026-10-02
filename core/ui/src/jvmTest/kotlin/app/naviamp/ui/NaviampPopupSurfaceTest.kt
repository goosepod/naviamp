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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalTestApi::class)
class NaviampPopupSurfaceTest {
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
