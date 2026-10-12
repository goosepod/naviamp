package app.naviamp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class NaviampSmartPlaylistDateUiTest {
    @Test fun importedDateIsSelectedAndConfirmationPreservesItsIsoValue() = runComposeUiTest {
        var selected = "2024-02-29"
        var dismissed = false
        setContent {
            CompositionLocalProvider(LocalNaviampOwnedPopupWindows provides true,
                LocalNaviampWindowGroupActive provides true) {
                Box(Modifier.size(700.dp, 900.dp)) {
                    NaviampAlertDialog(onDismissRequest = { dismissed = true }, confirmButton = {}, text = {
                        SmartPlaylistOperandField(true, selected, { selected = it }, "Date", NaviampColors())
                    })
                }
            }
        }
        onNodeWithText("Date format: YYYY-MM-DD (year-month-day).").assertExists()
        onNodeWithTag("smart-playlist-choose-date").performClick()
        onNodeWithTag("smart-playlist-use-date").assertIsEnabled().performClick()
        runOnIdle { assertEquals("2024-02-29", selected); assertFalse(dismissed) }
        onNodeWithTag("smart-playlist-use-date").assertDoesNotExist()
        onNodeWithTag("smart-playlist-choose-date").performClick()
        onNodeWithTag("smart-playlist-use-date").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
        onNodeWithTag("smart-playlist-use-date").performKeyInput { pressKey(androidx.compose.ui.input.key.Key.Escape) }
        onNodeWithTag("smart-playlist-use-date").assertDoesNotExist()
        runOnIdle { assertEquals("2024-02-29", selected); assertFalse(dismissed) }
    }
    @Test fun selectingANewDayRequiresConfirmationAndCancelPreservesTheOperand() = runComposeUiTest {
        var selected = "2024-02-29"
        setContent { SmartPlaylistOperandField(true, selected, { selected = it }, "Date", NaviampColors()) }
        onNodeWithTag("smart-playlist-choose-date").performClick()
        onNodeWithText("February 15", substring = true).performClick()
        runOnIdle { assertEquals("2024-02-29", selected) }
        onNodeWithText("Cancel", substring = false).performClick()
        runOnIdle { assertEquals("2024-02-29", selected) }
        onNodeWithTag("smart-playlist-choose-date").performClick()
        onNodeWithText("February 15", substring = true).performClick()
        onNodeWithTag("smart-playlist-use-date").performClick()
        runOnIdle { assertEquals("2024-02-15", selected) }
    }
    @Test fun invalidTypedDateRemainsUntouchedUntilAValidDayIsChosen() = runComposeUiTest {
        var selected = "2025-02-29"
        setContent { SmartPlaylistOperandField(true, selected, { selected = it }, "Date", NaviampColors()) }
        onNodeWithTag("smart-playlist-choose-date").performClick()
        onNodeWithTag("smart-playlist-use-date").assertIsNotEnabled()
        onNodeWithText("Cancel", substring = false).performClick()
        runOnIdle { assertEquals("2025-02-29", selected) }
    }
    @Test fun relativeDaysHaveGuidanceAndNoCalendar() = runComposeUiTest {
        var selected = "30"
        setContent { SmartPlaylistOperandField(false, selected, { selected = it }, "Days", NaviampColors(), relativeDays = true) }
        onNodeWithTag("smart-playlist-choose-date").assertDoesNotExist()
        onNodeWithText("Enter a positive number of days, for example 30.").assertExists()
        onNodeWithText("30").performTextReplacement("60")
        runOnIdle { assertEquals("60", selected) }
    }
}
