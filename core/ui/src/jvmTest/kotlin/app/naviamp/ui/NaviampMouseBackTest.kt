package app.naviamp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class NaviampMouseBackTest {
    @Test fun backPressFiresOnceAcrossRecompositionAndIgnoresOtherMouseButtons() = runComposeUiTest {
        val calls = mutableStateOf(0)
        setContent { Box(Modifier.size(220.dp).testTag("surface").naviampMouseBack { calls.value++ }) { Text(calls.value.toString()) } }
        onNodeWithTag("surface").performMouseInput { moveTo(center); press(MouseButton(3)) }
        runOnIdle { assertEquals(1, calls.value) }
        onNodeWithTag("surface").performMouseInput { moveTo(center.copy(x = center.x + 8)); release(MouseButton(3)) }
        runOnIdle { assertEquals(1, calls.value) }
        onNodeWithTag("surface").performMouseInput { click(button = MouseButton.Primary); click(button = MouseButton.Secondary); click(button = MouseButton(4)) }
        runOnIdle { assertEquals(1, calls.value) }
        onNodeWithTag("surface").performMouseInput { click(button = MouseButton(3)) }
        runOnIdle { assertEquals(2, calls.value) }
    }
    @Test fun modalBackHandlerClosesTheDialogBeforeTheUnderlyingPage() = runComposeUiTest {
        val dispatcher = NaviampSystemBackDispatcher(); val open = mutableStateOf(true)
        var pageBack = 0
        setContent { CompositionLocalProvider(LocalNaviampSystemBackDispatcher provides dispatcher) {
            NaviampSystemBackHandler(true) { pageBack++ }
            Text("Page")
            if (open.value) NaviampDialog({ open.value = false }) { Text("Dialog") }
        } }
        runOnIdle { assertNotNull(dispatcher.currentHandler).invoke() }
        onNodeWithText("Dialog").assertDoesNotExist()
        runOnIdle { assertEquals(0, pageBack); assertNotNull(dispatcher.currentHandler).invoke(); assertEquals(1, pageBack) }
    }
}
