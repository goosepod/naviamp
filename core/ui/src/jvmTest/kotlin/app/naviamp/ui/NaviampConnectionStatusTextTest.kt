package app.naviamp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class NaviampConnectionStatusTextTest {
    @Test fun statusKeysOutsideTheEditorAreShownAsLocalizedText() = runComposeUiTest {
        setContent {
            Column {
                NaviampConnectionStatusText("connection_aurral_companion_invalid", NaviampColors.Dark)
                NaviampConnectionStatusText("Connected to https://music.example.", NaviampColors.Dark)
            }
        }
        onNodeWithText("connection_aurral_companion_invalid").assertDoesNotExist()
        onNodeWithText("Enter an Aurral HTTP or HTTPS server URL and username.").assertExists()
        onNodeWithText("Connected to https://music.example.").assertExists()
    }
}
