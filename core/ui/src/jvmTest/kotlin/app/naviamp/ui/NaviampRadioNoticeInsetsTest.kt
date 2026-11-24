package app.naviamp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.naviamp.domain.radio.RadioBuildDiagnostics
import app.naviamp.domain.radio.RadioBuildOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NaviampRadioNoticeInsetsTest {
    @Test fun radioNoticeKeepsItsCloseButtonAboveTheSystemNavigationBar() =
        runDesktopComposeUiTest(width = 412, height = 800) {
            var dismissed = false
            setContent {
                Box(Modifier.fillMaxSize().testTag("window")) {
                    NaviampRadioNotice(
                        NaviampColors.Dark,
                        RadioBuildDiagnostics(RadioBuildOutcome.Failed, autoplay = true),
                        onDismiss = { dismissed = true },
                        modifier = Modifier.align(Alignment.BottomCenter).testTag("notice"),
                        systemInsets = WindowInsets(left = 16, top = 120, right = 24, bottom = 48),
                    )
                }
            }
            val window = onNodeWithTag("window").fetchSemanticsNode().boundsInRoot
            val notice = onNodeWithTag("notice").fetchSemanticsNode().boundsInRoot
            val close = onNodeWithText("Close").fetchSemanticsNode().boundsInRoot
            assertEquals(window.bottom, notice.bottom, 0.5f)
            assertTrue(close.bottom <= window.bottom - 48f, "close button $close overlaps the navigation bar")
            assertTrue(close.right <= window.right - 24f, "close button $close overlaps the side inset")
            assertTrue(close.top - notice.top < 120f, "notice $notice reserves the top status bar inset above its content")
            onNodeWithText("Close").performClick()
            runOnIdle { assertTrue(dismissed) }
        }
}
