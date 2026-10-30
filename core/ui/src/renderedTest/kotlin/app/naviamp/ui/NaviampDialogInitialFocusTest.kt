package app.naviamp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NaviampDialogInitialFocusTest {
    private val nativeSurface = object : NaviampOwnedDialogSurface {
        @Composable override fun Render(size: androidx.compose.ui.unit.DpSize,
            properties: androidx.compose.ui.window.PopupProperties, onCloseRequest: () -> Unit,
            content: @Composable () -> Unit) = content()
    }

    @Test fun escapeDismissesWithoutFirstClickingOrTabbingIntoTheDialog() = runComposeUiTest {
        var open by mutableStateOf(true)
        setContent {
            CompositionLocalProvider(LocalNaviampOwnedPopupWindows provides true,
                LocalNaviampOwnedDialogSurface provides nativeSurface,
                LocalNaviampWindowGroupActive provides true) {
                Box(Modifier.size(700.dp, 500.dp)) {
                    if (open) NaviampAlertDialog(onDismissRequest = { open = false },
                        title = { Text("Details") },
                        confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } })
                }
            }
        }
        onAllNodes(isRoot()).onLast().performKeyInput { pressKey(Key.Escape) }
        runOnIdle { assertFalse(open) }
    }

    @Test fun initialFocusAllowsTabActivationButRespectsEscapeDisabled() = runComposeUiTest {
        var confirmed = false
        var dismissed = false
        lateinit var inputMode: InputModeManager
        setContent {
            inputMode = LocalInputModeManager.current
            CompositionLocalProvider(LocalNaviampOwnedPopupWindows provides true,
                LocalNaviampOwnedDialogSurface provides nativeSurface,
                LocalNaviampWindowGroupActive provides true) {
                Box(Modifier.size(700.dp, 500.dp)) {
                    NaviampAlertDialog(onDismissRequest = { dismissed = true },
                        properties = DialogProperties(dismissOnBackPress = false),
                        confirmButton = {
                            Row {
                                TextButton(onClick = { confirmed = true }) { Text("Confirm") }
                                TextButton(onClick = {}) { Text("Other action") }
                            }
                        })
                }
            }
        }
        onAllNodes(isRoot()).onLast().performKeyInput { pressKey(Key.Escape) }
        runOnIdle {
            assertFalse(dismissed)
            // Compose key injection bypasses Android's native transition out of touch mode.
            // Buttons follow system focusability, so model a connected keyboard explicitly.
            assertTrue(inputMode.requestInputMode(InputMode.Keyboard))
        }
        onAllNodes(isRoot()).onLast().performKeyInput { pressKey(Key.Tab) }
        onNodeWithText("Confirm").assertIsFocused().performKeyInput { pressKey(Key.Tab) }
        onNodeWithText("Other action").assertIsFocused().performKeyInput {
            withKeyDown(Key.ShiftLeft) { pressKey(Key.Tab) }
        }
        onNodeWithText("Confirm").assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        runOnIdle { assertFalse(dismissed); assertTrue(confirmed) }
    }
}
