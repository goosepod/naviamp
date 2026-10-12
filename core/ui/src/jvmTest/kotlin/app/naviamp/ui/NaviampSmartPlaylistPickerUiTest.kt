package app.naviamp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalTestApi::class)
class NaviampSmartPlaylistPickerUiTest {
    @Test
    fun openingClickDoesNotMountThePopupUntilMouseRelease() = runComposeUiTest {
        var dismissed = false
        setContent {
            CompositionLocalProvider(LocalNaviampOwnedPopupWindows provides true,
                LocalNaviampWindowGroupActive provides true) {
                Box(Modifier.size(700.dp, 500.dp)) {
                    NaviampAlertDialog(onDismissRequest = { dismissed = true }, confirmButton = {}, text = {
                        SmartPlaylistDropdown("Field", "Artist", NaviampColors(),
                            listOf("Artist" to "artist", "Rating" to "rating"), {})
                    })
                }
            }
        }
        onNodeWithText("Artist").performMouseInput { moveTo(center); press() }
        mainClock.advanceTimeBy(250)
        onNodeWithText("Rating").assertDoesNotExist()
        onNodeWithText("Artist").performMouseInput { release() }
        mainClock.advanceTimeBy(500)
        runOnIdle { assertFalse(dismissed, "An editable anchor click must not dismiss its dialog") }
        onNodeWithText("Rating").assertExists().performMouseInput { click() }
        onNodeWithText("Rating").assertDoesNotExist()
    }

    @Test
    fun focusedPickerReopensAfterSelectionAndEscapeWithoutLosingTheValue() = runComposeUiTest {
        var selected = "Artist"
        setContent {
            var value by remember { mutableStateOf("Artist") }
            NaviampAlertDialog(onDismissRequest = {}, confirmButton = {}, text = {
                SmartPlaylistDropdown("Field", value, NaviampColors(),
                    listOf("Artist" to "Artist", "Rating" to "Rating"), { value = it; selected = it })
            })
        }
        onNodeWithText("Artist").performMouseInput { click() }
        onNodeWithText("Rating").performMouseInput { click() }
        runOnIdle { assertEquals("Rating", selected) }
        onNodeWithText("Rating").performMouseInput { click() }
        onNodeWithText("Artist").assertExists()
        onNode(hasText("Rating") and hasSetTextAction()).performKeyInput { pressKey(androidx.compose.ui.input.key.Key.Escape) }
        onNodeWithText("Artist").assertDoesNotExist()
        onNodeWithText("Rating").performMouseInput { click() }
        onNodeWithText("Artist").performMouseInput { click() }
        runOnIdle { assertEquals("Artist", selected) }
    }

    @Test
    fun keyboardActivationOpensThePickerAndEscapeKeepsTheSelectedValue() = runComposeUiTest {
        setContent {
            NaviampAlertDialog(onDismissRequest = {}, confirmButton = {}, text = {
                SmartPlaylistDropdown("Field", "Artist", NaviampColors(),
                    listOf("Artist" to "Artist", "Rating" to "Rating"), {})
            })
        }
        onNodeWithText("Artist").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
        onNodeWithText("Rating").assertDoesNotExist()
        onNodeWithText("Artist").performKeyInput { pressKey(androidx.compose.ui.input.key.Key.Enter) }
        onNodeWithText("Rating").assertExists()
        onNode(hasText("Artist") and hasSetTextAction()).performKeyInput { pressKey(androidx.compose.ui.input.key.Key.Escape) }
        onNodeWithText("Rating").assertDoesNotExist()
        onNodeWithText("Artist").assertExists()
    }

    @Test
    fun editablePickerCanFilterOptionsWithoutChangingTheSelectedRule() = runComposeUiTest {
        var selected = "is"
        setContent {
            NaviampAlertDialog(onDismissRequest = {}, confirmButton = {}, text = {
                SmartPlaylistDropdown("Operator", "is", NaviampColors(),
                    listOf("is" to "is", "contains" to "contains", "begins with" to "begins with"), { selected = it })
            })
        }
        onNodeWithText("is").performMouseInput { click() }
        onNode(hasText("is", substring = false) and hasSetTextAction()).performTextReplacement("cont")
        onNodeWithText("contains").assertExists()
        onNodeWithText("begins with").assertDoesNotExist()
        runOnIdle { assertEquals("is", selected) }
        onNodeWithText("contains").performMouseInput { click() }
        runOnIdle { assertEquals("contains", selected) }
    }
}
