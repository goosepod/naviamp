package app.naviamp.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NaviampSmartPlaylistDialogInsetsTest {
    @Test
    fun editorStaysBelowTheStatusBarAndAboveTheKeyboard() = runDesktopComposeUiTest(width = 400, height = 800) {
        setContent {
            SmartPlaylistBuilderDialog(
                NaviampColors(),
                onDismissRequest = {},
                onSave = {},
                systemInsets = WindowInsets(top = 48.dp),
                keyboardInsets = WindowInsets(bottom = 320.dp),
            )
        }
        val statusBarBottom = with(density) { 48.dp.toPx() }
        val keyboardTop = with(density) { (800 - 320).dp.toPx() }

        val dialog = onNodeWithTag(SmartPlaylistEditorDialogTestTag).fetchSemanticsNode().boundsInWindow
        val title = onNodeWithText("Smart playlist").fetchSemanticsNode().boundsInWindow
        val save = onNodeWithTag(SmartPlaylistSaveTestTag).fetchSemanticsNode().boundsInWindow

        assertTrue(title.top >= statusBarBottom, "title ${title.top} must clear the status bar $statusBarBottom")
        assertTrue(dialog.top >= 0f, "dialog must start inside the window")
        assertTrue(save.bottom <= keyboardTop, "actions ${save.bottom} must stay above the keyboard $keyboardTop")
        onNodeWithTag("smart-playlist-editor-scroll").assert(hasScrollAction())
    }
}
