package app.naviamp.ui

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NaviampPlayerColorsTest {
    @Test
    fun preservesDistinctSecondaryHue() {
        val palette = naviampAlbumPalette(
            List(60) { NaviampRgbSample(205, 28, 45) } +
                List(40) { NaviampRgbSample(18, 135, 92) },
        )

        assertNotNull(palette)
        val selected = listOf(palette.primary, palette.secondary)
        assertTrue(selected.any { it.red > it.green })
        assertTrue(selected.any { it.green > it.red })

        val gradient = NaviampPlayerColors.from(
            NaviampAlbumPalette(
                primary = Color(0xFFCD1C2D),
                secondary = Color(0xFF12875C),
                accent = Color(0xFFB7192B),
            ),
            NaviampColors.Dark,
        )
        assertTrue(gradient.backgroundMid.green > gradient.backgroundStart.green)
        assertTrue(gradient.backgroundEnd.green > gradient.backgroundStart.green)
        assertTrue(gradient.backgroundEnd.green > gradient.backgroundEnd.red)
        assertTrue(gradient.backgroundEnd.green >= 0.30f)
    }

    @Test
    fun lightArtworkLiftsPlayerGradient() {
        val brownSamples = List(20) { NaviampRgbSample(105, 66, 70) }
        val darkPalette = naviampAlbumPalette(brownSamples)
        val lightPalette = naviampAlbumPalette(
            brownSamples + List(80) { NaviampRgbSample(244, 242, 237) },
        )

        assertNotNull(darkPalette)
        assertNotNull(lightPalette)
        assertTrue(lightPalette.lightSampleRatio > 0.7f)

        val darkGradient = NaviampPlayerColors.from(darkPalette, NaviampColors.Dark)
        val lightGradient = NaviampPlayerColors.from(lightPalette, NaviampColors.Dark)
        assertTrue(lightGradient.backgroundMid.channelAverage() > darkGradient.backgroundMid.channelAverage())
        assertTrue(lightGradient.backgroundEnd.channelAverage() > darkGradient.backgroundEnd.channelAverage())
    }
}

private fun Color.channelAverage(): Float = (red + green + blue) / 3f
