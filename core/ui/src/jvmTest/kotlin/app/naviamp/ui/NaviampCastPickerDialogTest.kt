package app.naviamp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.*
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NaviampCastPickerDialogTest {
    @Test
    fun receiverAndLocalActionsAreAccessibleAndTheDialogFitsACompactWindow() = runDesktopComposeUiTest(520, 620) {
        var selected: String? = null
        var local = 0
        var dismissed = 0
        setContent {
            MaterialTheme {
                NaviampCastPickerDialog(NaviampCastPickerUi(visible = true,
                    targets = listOf(NaviampCastPickerTargetUi("onn", "Onn 4K Pro")), selectedTargetId = "onn"),
                    onSelect = { selected = it }, onLocal = { local++ }, onRetry = {}, onDismiss = { dismissed++ })
            }
        }
        onNodeWithText("Onn 4K Pro").assertIsDisplayed().performClick()
        assertEquals("onn", selected)
        onNodeWithText("Return to local playback").assertIsDisplayed().performClick()
        assertEquals(1, local)
        onNodeWithText("Close").assertIsDisplayed().performClick()
        assertEquals(1, dismissed)
        waitForIdle()
        val image = org.jetbrains.skia.Image.makeFromBitmap(onAllNodes(isRoot()).onLast().captureToImage().asSkiaBitmap())
        image.encodeToData()?.use { png ->
            File("build/cast-picker.png").also { it.parentFile.mkdirs() }.writeBytes(png.bytes)
        }
        image.close()
    }
}
