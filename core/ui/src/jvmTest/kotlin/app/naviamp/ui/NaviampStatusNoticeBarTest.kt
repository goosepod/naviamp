package app.naviamp.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NaviampStatusNoticeBarTest {
    @Test
    fun aNoticeThatReplacesAFocusedOneKeepsTheFocusPause() = runComposeUiTest {
        val notice = mutableStateOf(NaviampStatusNotice(1, NaviampStatusMessage.LibraryRefreshed))
        val lifetime = mutableListOf<Pair<Long, NaviampTimedNoticeAction>>()
        setContent {
            val current = notice.value
            NaviampStatusNoticeBar(NaviampColors.Dark, current, onDismiss = {},
                onLifetime = { action, _ -> lifetime += current.id to action })
        }
        onNodeWithTag("status-notice-dismiss").performSemanticsAction(SemanticsActions.RequestFocus)
        waitForIdle()
        onNodeWithTag("status-notice-dismiss").assertIsFocused()

        lifetime.clear()
        notice.value = NaviampStatusNotice(2, NaviampStatusMessage.TrackUnavailable)
        waitForIdle()

        onNodeWithText("That track is no longer available.").assertExists()
        // Focus never left Close, so the new notice must be paused rather than timed out under the user.
        onNodeWithTag("status-notice-dismiss").assertIsFocused()
        assertEquals(listOf(2L to NaviampTimedNoticeAction.Presented, 2L to NaviampTimedNoticeAction.FocusGained), lifetime)
    }
}
