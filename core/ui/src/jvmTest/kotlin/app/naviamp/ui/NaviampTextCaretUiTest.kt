package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class NaviampTextCaretUiTest {
    @Test fun darkFieldsShowTheCaretAndMoveItWithTheInsertionPoint() = checkCaret(NaviampColors.Dark)
    @Test fun lightFieldsShowTheCaretAndMoveItWithTheInsertionPoint() = checkCaret(NaviampColors.Light)
    @Test fun errorFieldsKeepAReadableCaret() = checkCaret(NaviampColors.Dark, error = true)

    @Test fun ownedLayerShowsTheCaretWhenTheParentRetainsNativeFocus() =
        checkCaret(NaviampColors.Dark, ownedFocus = true)

    private fun checkCaret(colors: NaviampColors, error: Boolean = false, ownedFocus: Boolean = false) = runComposeUiTest {
        mainClock.autoAdvance = false
        var value by mutableStateOf("    ")
        setContent {
            val input: @Composable () -> Unit = {
            MaterialTheme(colorScheme = darkColorScheme(primary = colors.accent, onSurface = colors.primaryText)) {
                Box(Modifier.width(400.dp).background(colors.controlSurface)) {
                    if (error) {
                        OutlinedTextField(value, { value = it }, singleLine = true, isError = true,
                            colors = naviampOutlinedTextFieldColors(colors), modifier = Modifier.testTag("input"))
                    } else {
                        NaviampTextField(value, { value = it }, "Field", colors, modifier = Modifier.testTag("input"))
                    }
                }
            }
            }
            if (ownedFocus) {
                val original = LocalWindowInfo.current
                val inactiveLayer = remember(original) {
                    object : WindowInfo by original { override val isWindowFocused: Boolean = false }
                }
                CompositionLocalProvider(LocalWindowInfo provides inactiveLayer, LocalNaviampWindowGroupActive provides true) {
                    NaviampOwnedWindowFocusScope(input)
                }
            } else input()
        }
        val field = onNodeWithTag("input")
        field.performClick()
        mainClock.advanceTimeBy(250)
        field.performTextInputSelection(TextRange(1))
        mainClock.advanceTimeBy(100)
        val first = caretColumns(field, colors.primaryText)
        assertTrue(first.isNotEmpty(), "The focused caret must be visibly painted inside the field")
        field.performTextInputSelection(TextRange(3))
        mainClock.advanceTimeBy(100)
        val moved = caretColumns(field, colors.primaryText)
        assertTrue(moved.isNotEmpty(), "The moved caret must still be visible")
        assertTrue(moved.min() > first.max(), "The rendered caret must follow the insertion point")
        field.performTextInputSelection(TextRange(1))
        mainClock.advanceTimeByFrame()
        field.performTextInput("x")
        mainClock.advanceTimeByFrame()
        runOnIdle { assertEquals(" x   ", value) }
        field.assertTextContains(" x   ", substring = false)
    }

    // Spaces isolate the caret from glyphs. Ignore the border and floating label when counting
    // foreground-colored vertical strokes in the actual rendered input.
    private fun caretColumns(field: SemanticsNodeInteraction, foreground: Color): List<Int> {
        val pixels = field.captureToImage().toPixelMap()
        return (4 until pixels.width - 4).filter { x ->
            (20 until pixels.height - 10).count { y ->
                val pixel = pixels[x, y]
                abs(pixel.red - foreground.red) < .01f && abs(pixel.green - foreground.green) < .01f &&
                    abs(pixel.blue - foreground.blue) < .01f
            } >= 8
        }
    }
}
