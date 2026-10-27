package app.naviamp.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NaviampSmartPlaylistSectionOutlineTest {
    @Test fun outerAndNestedOutlinesStayVisibleWhenClippedByScrolling() = checkOutline(1f)
    @Test fun outlinesStayVisibleAtFractionalDisplayScale() = checkOutline(1.5f)

    private fun checkOutline(scale: Float) = runDesktopComposeUiTest(600, 800) {
        lateinit var scroll: ScrollState
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(scale, 1f)) {
                scroll = rememberScrollState()
                val colors = NaviampColors(border = Color.White)
                Box(Modifier.size(320.dp, 240.dp).background(Color.Black).testTag("outline-viewport")) {
                    Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                        SmartPlaylistSection("Sort and Limit", colors) {
                            SmartPlaylistNestedSection("Sort 1", colors) {
                                Spacer(Modifier.height(450.dp))
                            }
                        }
                    }
                }
            }
        }
        for ((phase, offset) in listOf(0, 64, 180, 0).withIndex()) {
            runOnIdle { scroll.dispatchRawDelta(offset * scale - scroll.value) }
            waitForIdle()
            val image = onNodeWithTag("outline-viewport").captureToImage()
            val pixels = image.toPixelMap()
            // Sample both sides well away from text/corners, including when the section top is clipped.
            for (edge in listOf(0, 10, 309, 319)) {
                val x = (edge * scale).toInt()
                val visible = ((80 * scale).toInt() until (110 * scale).toInt()).count { y ->
                    (x - 1..x + 2).any { sample ->
                        val color = pixels[sample.coerceIn(0, pixels.width - 1), y]
                        color.red > 0.15f && color.green > 0.15f && color.blue > 0.15f
                    }
                }
                assertTrue(visible >= 25 * scale, "Missing section edge $edge at scroll $offset, scale $scale")
            }
            val output = File("build/reports/smart-playlist-outlines-$scale-$phase.png")
            output.parentFile.mkdirs()
            org.jetbrains.skia.Image.makeFromBitmap(image.asSkiaBitmap()).use {
                output.writeBytes(requireNotNull(it.encodeToData()).bytes)
            }
        }
    }
}
