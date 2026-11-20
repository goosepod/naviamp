package app.naviamp.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class NaviampRemoteBackTest {
    @Test fun heldRemoteBackClosesOnlyDeepestPageAndReleaseDoesNotPopAgain() = runComposeUiTest {
        val dispatcher = NaviampSystemBackDispatcher()
        val nested = mutableStateOf(true)
        var outer = 0
        var inner = 0
        setContent {
            CompositionLocalProvider(LocalNaviampSystemBackDispatcher provides dispatcher) {
                NaviampSystemBackHandler(true) { outer++ }
                if (nested.value) NaviampSystemBackHandler(true) { inner++; nested.value = false }
                Box(Modifier.naviampRemoteBack(dispatcher)) {
                    Box(Modifier.testTag("focused").focusable())
                }
            }
        }
        onNodeWithTag("focused").performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput { keyDown(Key.Back) }
        waitForIdle()
        assertEquals(1, inner)
        onNodeWithTag("focused").performKeyInput { advanceEventTime(1_000); keyUp(Key.Back) }
        assertEquals(0, outer)
        onNodeWithTag("focused").performKeyInput { pressKey(Key.Back) }
        assertEquals(1, outer)
    }

    @Test fun focusedControlConsumesEscapeBeforeOuterPageAndRootWithoutHandlerRemainsUnhandled() = runComposeUiTest {
        val dispatcher = NaviampSystemBackDispatcher()
        val enabled = mutableStateOf(true)
        var outer = 0
        var child = 0
        setContent {
            CompositionLocalProvider(LocalNaviampSystemBackDispatcher provides dispatcher) {
                NaviampSystemBackHandler(enabled.value) { outer++ }
                Box(Modifier.naviampRemoteBack(dispatcher)) {
                    Box(Modifier.testTag("focused").onPreviewKeyEvent {
                        if (it.key == Key.Escape) { if (it.type == KeyEventType.KeyDown) child++; true } else false
                    }.focusable())
                }
            }
        }
        onNodeWithTag("focused").performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput { pressKey(Key.Escape) }
        assertEquals(1, child)
        assertEquals(0, outer)
        onNodeWithTag("focused").performKeyInput { pressKey(Key.Menu) }
        assertEquals(1, outer)
        runOnIdle { enabled.value = false }
        onNodeWithTag("focused").performKeyInput { pressKey(Key.Back) }
        assertEquals(1, outer)
    }
}
