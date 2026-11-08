package app.naviamp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NaviampSmartPlaylistFieldLabelsTest {
    @Test fun labelsStayAboveOutlinesWhenEmptyFilledAndFocused() = checkLabels(1f)
    @Test fun labelsStayAboveOutlinesAtLargeText() = checkLabels(1.5f)

    private fun checkLabels(fontScale: Float) = runDesktopComposeUiTest(600, 780) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    val colors = NaviampColors()
                    Surface(color = colors.controlSurface) {
                        Column(Modifier.width(500.dp).padding(16.dp).testTag("label-fixture"),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            var value by remember { mutableStateOf("") }
                            SmartPlaylistOutlinedTextField(value, { value = it }, colors,
                                Modifier.fillMaxWidth().testTag("editable"), label = "Name", singleLine = true)
                            SmartPlaylistDropdown("Match", "Any rule", colors, listOf("Any rule" to true), {})
                            SmartPlaylistOperandField(true, "2026-10-19", {}, "Value", colors)
                        }
                    }
                }
            }
        }
        fun check(label: String) {
            val caption = onNodeWithText(label).fetchSemanticsNode().boundsInRoot
            val input = onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot
            assertTrue(caption.bottom + 3f <= input.top, "$label must be above its outline")
            assertEquals(input.left, caption.left, "$label must align with its field")
            onNodeWithContentDescription(label).assert(hasSetTextAction())
        }
        listOf("Name", "Match", "Value").forEach(::check)
        val initial = onNodeWithText("Name").fetchSemanticsNode().boundsInRoot
        onNodeWithTag("editable").performClick().performTextInput("Favorites")
        waitForIdle()
        check("Name")
        assertEquals(initial, onNodeWithText("Name").fetchSemanticsNode().boundsInRoot)
        onNodeWithTag("editable").performTextClearance()
        check("Name")
        assertEquals(initial, onNodeWithText("Name").fetchSemanticsNode().boundsInRoot)
        val output = File("build/reports/smart-playlist-field-labels-$fontScale.png")
        output.parentFile.mkdirs()
        org.jetbrains.skia.Image.makeFromBitmap(onNodeWithTag("label-fixture").captureToImage().asSkiaBitmap()).use {
            output.writeBytes(requireNotNull(it.encodeToData()).bytes)
        }
    }
}
