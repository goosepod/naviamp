package app.naviamp.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.test.Test

/** Android's screenshot boundary records the actual TV dialog, including native window bounds. */
@OptIn(ExperimentalTestApi::class)
class NaviampAccountSwitcherTvCaptureTest {
    @Test
    fun captureTheRemoteChooser() = runComposeUiTest {
        setContent {
            NaviampAccountSwitcher(
                NaviampConnectionSettingsUi(
                    connection = NaviampShellConnectionUi(connected = true, savedConnections = listOf(
                        NaviampSavedConnectionUi("alice", "Family music", "https://music.example", "Alice", current = true),
                        NaviampSavedConnectionUi("bob", "Family music", "https://music.example", "Bob"),
                    )),
                    accountSwitcher = NaviampAccountSwitcherUi(visible = true),
                ),
                NaviampConnectionSettingsActions({}, {}, {}, {}, {}, {}, {}, {}),
                NaviampColors.Dark,
            )
        }
        onNodeWithTag(NaviampAccountChooserTag).assertIsDisplayed()
        onNodeWithTag(NaviampAccountAddTag).assertIsDisplayed()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?.let(::File) ?: instrumentation.targetContext.cacheDir
        output.mkdirs()
        File(output, "account-chooser-tv.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
