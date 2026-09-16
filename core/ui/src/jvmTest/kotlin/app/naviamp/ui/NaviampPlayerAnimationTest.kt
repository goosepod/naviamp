package app.naviamp.ui

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NaviampPlayerAnimationTest {
    @Test
    fun marqueeMovesAndStopsWhenDisabled() = assertMarqueeMovesAndStops(LayoutDirection.Ltr)

    @Test
    fun rightToLeftMarqueeMovesAndStopsWhenDisabled() = assertMarqueeMovesAndStops(LayoutDirection.Rtl)

    private fun assertMarqueeMovesAndStops(direction: LayoutDirection) = runComposeUiTest {
        mainClock.autoAdvance = false
        val enabled = mutableStateOf(true)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                BouncingTitleText(
                    text = "A very long title with distinct words that scroll across the player",
                    color = Color.White,
                    fontSize = 14,
                    marqueeEnabled = enabled.value,
                    modifier = Modifier.width(120.dp).testTag("marquee"),
                )
            }
        }
        mainClock.advanceTimeBy(100)
        fun pixels(): IntArray {
            val image = onNodeWithTag("marquee").captureToImage()
            return IntArray(image.width * image.height).also { image.readPixels(it) }
        }
        val start = pixels()
        mainClock.advanceTimeBy(2200)
        assertFalse(start.contentEquals(pixels()))
        runOnUiThread { enabled.value = false }
        mainClock.advanceTimeBy(100)
        val stopped = pixels()
        mainClock.advanceTimeBy(2200)
        assertTrue(stopped.contentEquals(pixels()))
    }

}
