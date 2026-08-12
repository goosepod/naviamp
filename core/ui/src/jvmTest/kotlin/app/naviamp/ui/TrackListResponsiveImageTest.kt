package app.naviamp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TrackListResponsiveImageTest {
    @Test
    fun trackTitlesStayAlignedAtPhoneAndDesktopWidths() {
        listOf(320, 900).forEach(::assertTrackListImageAtWidth)
    }
}

@OptIn(ExperimentalTestApi::class)
private fun assertTrackListImageAtWidth(widthDp: Int) = runComposeUiTest {
    setContent {
        Box(Modifier.width(widthDp.dp).height(120.dp)) {
            Column {
                testTrackRow(number = 9, title = "Single digit track")
                testTrackRow(number = 10, title = "Double digit track")
            }
        }
    }
    waitForIdle()

    val firstTitle = onNodeWithText("Single digit track").fetchSemanticsNode().boundsInRoot
    val secondTitle = onNodeWithText("Double digit track").fetchSemanticsNode().boundsInRoot
    val firstNumber = onNodeWithText("9.").fetchSemanticsNode().boundsInRoot
    val secondNumber = onNodeWithText("10.").fetchSemanticsNode().boundsInRoot

    assertTrue(abs(firstTitle.left - secondTitle.left) < 0.5f, "Titles drifted at ${widthDp}dp")
    assertTrue(abs(firstNumber.right - secondNumber.right) < 0.5f, "Track numbers are not right aligned at ${widthDp}dp")

    val pixels = onRoot().captureToImage().toPixelMap()
    assertTrue(pixels.width > 0 && pixels.height > 0)
    val sampled = buildSet {
        val xStep = (pixels.width / 24).coerceAtLeast(1)
        val yStep = (pixels.height / 12).coerceAtLeast(1)
        for (x in 0 until pixels.width step xStep) {
            for (y in 0 until pixels.height step yStep) add(pixels[x, y])
        }
    }
    assertTrue(sampled.size > 2, "Responsive snapshot rendered blank at ${widthDp}dp")
    assertEquals(widthDp, pixels.width, "Snapshot must honor the requested 1x test density")
}

@androidx.compose.runtime.Composable
private fun testTrackRow(number: Int, title: String) {
    TrackRow(
        track = SharedTrackRowUi(id = number.toString(), title = title, subtitle = "Artist — Album"),
        colors = NaviampColors(),
        onTrackAction = null,
        canSelect = false,
        canStartRadio = false,
        canAddToQueue = false,
        canDownload = false,
        canAddToPlaylist = false,
        showCoverArt = false,
        trackNumber = number,
        trackNumberWidth = trackNumberColumnWidth(10),
    )
}
