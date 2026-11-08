package app.naviamp.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Keep bitmap-backed layer aggregation covered on real JVM and Android graphics runtimes. */
class NaviampRasterLayerScheduleTest {
    @Test fun sharedLayerSamplingOwnsPlacementRevealAndReadiness() {
        val bounds = Rect(37f, 100f, 237f, 120f)
        val layer = NaviampRasterLayer(ImageBitmap(220, 20), origin = Offset(-5f, 0f),
            revealMotion = NaviampLayerMotion(listOf(0f, 1f), listOf(0L, 1000L)))
        val crop = assertNotNull(naviampRasterPixelCrop(layer, bounds, bounds, 250L))
        assertEquals(IntRect(5, 0, 55, 20), crop.source)
        assertEquals(IntRect(0, 0, 50, 20), crop.destination)
        assertEquals(0L, naviampRasterFrameDelay(listOf(layer), 200f, 250L, needsUpdate = true))
        assertNull(naviampRasterFrameDelay(listOf(layer), 200f, 250L, available = false, needsUpdate = true))
        val moving = layer.copy(translation = NaviampLayerMotion(listOf(0f, -100f), listOf(0L, 1000L)))
        assertEquals(Offset(-30f, 0f), assertNotNull(naviampRasterTranslation(moving, bounds, bounds, 250L)).position)
        // A scrolling bitmap uses its fixed viewport, rather than the progress reveal.
        assertEquals(IntRect(30, 0, 220, 20), assertNotNull(naviampRasterPixelCrop(moving, bounds, bounds, 250L)).source)
    }

    @Test fun layerSchedulingKeepsTranslationCadenceAndStopsStaticOrFinishedContent() {
        val image = ImageBitmap(1, 1)
        val progress = NaviampRasterLayer(image, revealMotion = progressLayerMotion(.25f, 600.0))
        val static = NaviampRasterLayer(image, reveal = .25f)
        val translation = NaviampRasterLayer(image,
            translation = NaviampLayerMotion(listOf(0f, -100f), listOf(0L, 2400L)))
        assertNull(naviampRasterFrameDelay(listOf(static), 1000f, 50L))
        assertTrue(assertNotNull(naviampRasterFrameDelay(listOf(progress), 1000f, 50L)) > 16L)
        assertEquals(16L, naviampRasterFrameDelay(listOf(progress, translation), 1000f, 50L))
        assertNull(naviampRasterFrameDelay(listOf(translation), 1000f, 2400L))
        assertEquals(16L, naviampRasterFrameDelay(listOf(translation.copy(
            translation = translation.translation!!.copy(repeat = true))), 1000f, 2400L))
    }
}
