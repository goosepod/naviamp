package app.naviamp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class NaviampTelevisionArtworkContrastTest {
    @Test fun textAndPlayedWaveformMeetContrastAcrossSrgbArtworkPixels() {
        val colors = NaviampTelevisionColors
        var minimumText = Double.POSITIVE_INFINITY
        var minimumWaveform = Double.POSITIVE_INFINITY
        // Includes opaque white: the worst-case background under this fixed dark reading surface.
        for (r in 0..16) for (g in 0..16) for (b in 0..16) {
            val artwork = Color(r / 16f, g / 16f, b / 16f)
            val protectedArtwork = Color.Black.copy(alpha = 0.38f).compositeOver(artwork)
            val surface = televisionReadingSurfaceColor(colors).compositeOver(protectedArtwork)
            for (foreground in listOf(colors.primaryText, colors.secondaryText, colors.mutedText)) {
                minimumText = min(minimumText, contrast(foreground.compositeOver(surface), surface))
            }
            for (accent in listOf(Color.Black, Color.White, Color.Red, Color.Blue, Color.Green, Color.Yellow, artwork)) {
                val played = waveformPlayedColor(televisionWaveformColors(colors, accent), enabled = false).compositeOver(surface)
                minimumWaveform = min(minimumWaveform, contrast(played, surface))
            }
        }
        assertTrue(minimumText >= 4.5, "Text contrast $minimumText")
        assertTrue(minimumWaveform >= 3.0, "Played waveform contrast $minimumWaveform")
        val output = File("build/reports/television-artwork-contrast/ratios.txt")
        output.parentFile.mkdirs()
        output.writeText("4913 sRGB background samples; minimum text=$minimumText; minimum played waveform=$minimumWaveform\n")
    }

    @Test fun brightWhite720p() = capture("white", 1280, 720, 1f)
    @Test fun brightWhite4k() = capture("white", 3840, 2160, 3f)
    @Test fun saturatedYellow720p() = capture("yellow", 1280, 720, 1f)
    @Test fun saturatedYellow4k() = capture("yellow", 3840, 2160, 3f)
    @Test fun highContrastArtwork720p() = capture("checker", 1280, 720, 1f)
    @Test fun highContrastArtwork4k() = capture("checker", 3840, 2160, 3f)
    @Test fun multicolorArtwork720p() = capture("gradient", 1280, 720, 1f)
    @Test fun multicolorArtwork4k() = capture("gradient", 3840, 2160, 3f)

    private fun capture(pattern: String, width: Int, height: Int, density: Float) = runDesktopComposeUiTest(width, height) {
        mainClock.autoAdvance = false
        setContent { CompositionLocalProvider(LocalDensity provides Density(density)) {
            Box(Modifier.fillMaxSize()) {
                Canvas(Modifier.fillMaxSize()) {
                    when (pattern) {
                        "white" -> drawRect(Color.White)
                        "yellow" -> drawRect(Color.Yellow)
                        "checker" -> {
                            drawRect(Color.Black)
                            for (x in 0..15) for (y in 0..8) if ((x + y) % 2 == 0) drawRect(Color.White,
                                topLeft = Offset(x * size.width / 16, y * size.height / 9), size = Size(size.width / 16, size.height / 9))
                        }
                        else -> drawRect(Brush.linearGradient(listOf(Color.Magenta, Color.Yellow, Color.Cyan, Color.Blue)))
                    }
                }
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.38f)))
                TelevisionReadingSurface(NaviampTelevisionColors)
                TelevisionNowPlaying(NowPlayingUi(id = pattern, title = "Artwork contrast and readable controls", subtitle = "Artist — long metadata remains readable",
                    stateLabel = "Playing", isPlaying = true, canPlayPause = true, canRepeat = true, repeatMode = NaviampRepeatMode.Queue,
                    lyricsAvailable = true, lyricsVisible = true, positionSeconds = 73.0, durationSeconds = 180.0,
                    lyricsLines = listOf(NaviampLyricLineUi(0, "Previous lyric remains readable"), NaviampLyricLineUi(70000, "Active lyric stands out"),
                        NaviampLyricLineUi(90000, "Upcoming lyric remains readable")),
                    waveform = app.naviamp.domain.waveform.AudioWaveform(List(320) { (it % 17 + 1) / 18f })),
                    null, NaviampTelevisionColors, playerColors = NaviampPlayerColors.fromSingleColor(Color.Black, NaviampTelevisionColors),
                    actions = NaviampNowPlayingActions({}, {}, {}, {}, {}, {}, {}), onClose = {}, onOpenSettings = {})
            }
        } }
        mainClock.advanceTimeBy(700)
        onNodeWithText("Active lyric stands out").assertIsDisplayed().assertIsSelected()
        onNodeWithContentDescription("Pause").assertIsFocused()
        onNodeWithContentDescription("Repeat all").assertIsDisplayed()
        val pixels = onRoot().captureToImage().toPixelMap()
        // Check a rendered background pixel, not just the mathematical policy.
        val surface = pixels[2, 2]
        assertTrue(contrast(NaviampTelevisionColors.mutedText, surface) >= 4.5)
        val snapshot = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until height) for (x in 0 until width) snapshot.setRGB(x, y, pixels[x, y].toArgb())
        val output = File("build/reports/television-artwork-contrast/$pattern-${width}x$height.png")
        output.parentFile.mkdirs()
        ImageIO.write(snapshot, "png", output)
    }
    private fun contrast(a: Color, b: Color): Double =
        (max(a.luminance(), b.luminance()) + 0.05) / (min(a.luminance(), b.luminance()) + 0.05)
}
