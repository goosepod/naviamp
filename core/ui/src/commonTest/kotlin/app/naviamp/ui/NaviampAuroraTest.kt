package app.naviamp.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlin.test.*

class NaviampAuroraTest {
    @Test fun balancedPreservesColorsAndOtherTonesAdjustAllFiveStops() {
        val colors = NaviampPlayerColors(Color.Gray, Color.Gray, Color.Gray, Color.Gray,
            listOf(Color.Gray, Color.Gray))
        assertEquals(colors, colors.withAuroraTone(app.naviamp.domain.settings.AuroraTone.Dark))
        val light = colors.withAuroraTone(app.naviamp.domain.settings.AuroraTone.Light).auroraColors(5)
        val dark = colors.withAuroraTone(app.naviamp.domain.settings.AuroraTone.DeepDark).auroraColors(5)
        assertTrue(light.all { it.red > Color.Gray.red })
        assertTrue(dark.all { it.red < Color.Gray.red })
    }

    @Test fun angleSpansLandscapeAndPortraitWithoutMovingTheCenter() {
        for (size in listOf(Size(1200f, 600f), Size(300f, 800f))) {
            for (angle in listOf(0, 45, 90, 135, 180)) {
                val (start, end) = auroraGradientEndpoints(size, angle)
                assertEquals(size.width, start.x + end.x, 0.001f)
                assertEquals(size.height, start.y + end.y, 0.001f)
            }
            val (left, right) = auroraGradientEndpoints(size, 0)
            assertEquals(0f, left.x, 0.001f)
            assertEquals(size.width, right.x, 0.001f)
            val (top, bottom) = auroraGradientEndpoints(size, 90)
            assertEquals(0f, top.y, 0.001f)
            assertEquals(size.height, bottom.y, 0.001f)
            val (reverseStart, reverseEnd) = auroraGradientEndpoints(size, 180)
            assertEquals(right.x, reverseStart.x, 0.001f)
            assertEquals(left.x, reverseEnd.x, 0.001f)
        }
    }

    @Test fun extractsFiveCoverColorsAndUsesSelectedStopCount() {
        val samples = listOf(NaviampRgbSample(210, 20, 40), NaviampRgbSample(20, 170, 40),
            NaviampRgbSample(30, 40, 210), NaviampRgbSample(200, 160, 20), NaviampRgbSample(170, 25, 180))
        val palette = assertNotNull(naviampAlbumPalette(samples.flatMap { sample -> List(40) { sample } }))
        val extracted = listOf(palette.primary, palette.accent, palette.secondary) + palette.additionalColors
        assertEquals(5, extracted.distinct().size)
        assertTrue(extracted.all { color -> samples.any { Color(it.red, it.green, it.blue) == color } })
        val colors = NaviampPlayerColors.from(palette, NaviampColors.Dark)
        for (count in 2..5) assertEquals(count, colors.auroraColors(count).size)
        assertEquals(5, colors.auroraColors(5).distinct().size)
    }
}
