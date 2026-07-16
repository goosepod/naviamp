package app.naviamp.ui

import androidx.compose.ui.graphics.Color
import app.naviamp.domain.settings.AuroraTone
import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test
    fun singleColorPaletteKeepsSelectedHueButCreatesReadableEnergy() {
        val selected = naviampColorFromHex("#24364A")
        assertNotNull(selected)

        val palette = NaviampPlayerColors.fromSingleColor(selected, NaviampColors.Dark)

        assertTrue(palette.accent.channelAverage() > selected.channelAverage())
        assertTrue(palette.gradientColors.distinct().size > 1)
        assertEquals(null, naviampColorFromHex("#xyzxyz"))
    }

    @Test
    fun lightAuroraLiftsGradientAndColorPickerRoundTrips() {
        val dark = NaviampPlayerColors.fromSingleColor(Color(0xFF24364A), NaviampColors.Dark)
        val light = dark.withAuroraTone(AuroraTone.Light)

        assertTrue(light.backgroundStart.channelAverage() > dark.backgroundStart.channelAverage())
        assertTrue(light.backgroundMid.channelAverage() > dark.backgroundMid.channelAverage())
        val picked = naviampColorFromHsv(0.58f, 0.72f, 0.46f)
        assertEquals(naviampColorToHex(picked), naviampColorToHex(naviampColorFromHex(naviampColorToHex(picked))!!))
    }
}

private fun Color.channelAverage(): Float = (red + green + blue) / 3f
