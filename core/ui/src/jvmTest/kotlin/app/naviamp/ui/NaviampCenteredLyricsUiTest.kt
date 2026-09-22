package app.naviamp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NaviampCenteredLyricsUiTest {
    @Test
    fun wrappedAndEdgeLyricsCenterUsingTheirRenderedBounds() = runComposeUiTest {
        var activeLine by mutableIntStateOf(2)
        val lines = listOf(
            NaviampLyricLineUi(0, text = "First lyric"),
            NaviampLyricLineUi(1_000, text = "A short lyric before the active line"),
            NaviampLyricLineUi(
                2_000,
                text = "This active lyric wraps across several rendered rows so its complete block must be centered",
            ),
            NaviampLyricLineUi(3_000, text = "A short lyric after the active line"),
            NaviampLyricLineUi(4_000, text = "Last lyric"),
        )

        setContent {
            Box(Modifier.size(width = 180.dp, height = 320.dp).testTag("lyrics-viewport")) {
                CenteredLyricsList(
                    lines = lines,
                    activeLineIndex = activeLine,
                    positionMillis = null,
                    offsetMillis = 0,
                    colors = NaviampColors.Dark,
                    listState = rememberLazyListState(),
                    onSeek = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        waitForIdle()
        assertLineCentered(2)
        val wrappedHeight = onNodeWithTag("lyrics-line-2").fetchSemanticsNode().boundsInRoot.height
        val shortHeight = onNodeWithTag("lyrics-line-1").fetchSemanticsNode().boundsInRoot.height
        assertTrue(
            wrappedHeight >= shortHeight * 2f - 0.5f,
            "expected a multi-row active lyric: wrapped=$wrappedHeight, short=$shortHeight",
        )

        runOnUiThread { activeLine = 0 }
        waitForIdle()
        assertLineCentered(0)

        runOnUiThread { activeLine = lines.lastIndex }
        waitForIdle()
        assertLineCentered(lines.lastIndex)

        mainClock.autoAdvance = false
        runOnUiThread { activeLine = 1 }
        mainClock.advanceTimeBy(600L)
        waitForIdle()
        val afterScroll = onNodeWithTag("lyrics-line-1").fetchSemanticsNode().boundsInRoot
        mainClock.advanceTimeBy(600L)
        waitForIdle()
        val afterEmphasis = onNodeWithTag("lyrics-line-1").fetchSemanticsNode().boundsInRoot
        assertTrue(
            abs(afterEmphasis.center.y - afterScroll.center.y) <= 0.5f,
            "active line moved after scrolling completed: ${afterScroll.center.y} to ${afterEmphasis.center.y}",
        )
        assertTrue(
            abs(afterEmphasis.height - afterScroll.height) <= 0.5f,
            "active line height changed after scrolling completed: ${afterScroll.height} to ${afterEmphasis.height}",
        )
    }

    private fun androidx.compose.ui.test.ComposeUiTest.assertLineCentered(index: Int) {
        val viewport = onNodeWithTag("lyrics-viewport").fetchSemanticsNode().boundsInRoot
        val line = onNodeWithTag("lyrics-line-$index").fetchSemanticsNode().boundsInRoot
        assertTrue(
            abs(line.center.y - viewport.center.y) <= 1.5f,
            "line $index center=${line.center.y}, viewport center=${viewport.center.y}",
        )
    }
}
