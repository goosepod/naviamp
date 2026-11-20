package app.naviamp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class NaviampTelevisionNavigationInputTest {
    @Test fun downEntersContentFromNativeTextOrPointerMode() = verifyEntry(click = false)
    @Test fun selectEntersContentFromNativeTextOrPointerMode() = verifyEntry(click = true)

    private fun verifyEntry(click: Boolean) = runDesktopComposeUiTest(1000, 900) {
        val mode = object : InputModeManager {
            override var inputMode by mutableStateOf(InputMode.Touch)
            override fun requestInputMode(inputMode: InputMode): Boolean { this.inputMode = inputMode; return true }
        }
        val contentFocus = FocusRequester()
        var activations = 0
        setContent {
            CompositionLocalProvider(LocalInputModeManager provides mode) {
                Column {
                    TelevisionNavigationButton(NaviampTelevisionDestination.Home, NaviampColors.Dark,
                        Modifier.testTag("navigation").focusProperties { canFocus = true },
                        onClick = { activations++; contentFocus.requestFocus() }, onFocused = {},
                        onDown = { activations++; contentFocus.requestFocus() })
                    DiscoveryNavigationCard(NaviampColors.Dark, {}, Modifier.testTag("content")
                        .focusRequester(contentFocus)) { androidx.compose.material3.Text("Aurral") }
                }
            }
        }
        onNodeWithTag("navigation").performSemanticsAction(SemanticsActions.RequestFocus)
        assertEquals(InputMode.Touch, mode.inputMode)
        if (click) onNodeWithTag("navigation").performKeyInput { pressKey(Key.DirectionCenter) }
        else onNodeWithTag("navigation").performKeyInput { pressKey(Key.DirectionDown) }
        assertEquals(InputMode.Keyboard, mode.inputMode)
        assertEquals(1, activations)
        onNodeWithTag("content").assertIsFocused()
    }
}
