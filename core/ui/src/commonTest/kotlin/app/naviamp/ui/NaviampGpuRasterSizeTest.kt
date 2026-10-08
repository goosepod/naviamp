package app.naviamp.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlin.test.*

class NaviampGpuRasterSizeTest {
    @Test fun smallSurfacesStayExactAndLargeWaterFieldsRespectThePixelBudget() {
        assertEquals(IntSize(358, 358), naviampGpuRasterSize(NaviampVisualizer.OceanOfInk, 358, 358))
        for ((width, height) in listOf(1006 to 814, 1920 to 1080, 3840 to 2160, 814 to 1006)) {
            val size = naviampGpuRasterSize(NaviampVisualizer.OceanOfInk, width, height)
            assertTrue(size.width.toLong() * size.height <= 512 * 512)
            assertTrue(kotlin.math.abs(size.width.toFloat() / size.height - width.toFloat() / height) < .01f)
            assertTrue(size.width <= width && size.height <= height)
        }
        for (effect in listOf(NaviampVisualizer.AnalogSignalFailure, NaviampVisualizer.AudioSphere)) {
            assertEquals(IntSize(1006, 814), naviampGpuRasterSize(effect, 1006, 814))
        }
        assertFailsWith<IllegalArgumentException> { naviampGpuRasterSize(NaviampVisualizer.OceanOfInk, 0, 10) }
        assertFailsWith<IllegalArgumentException> { naviampGpuRasterSize(NaviampVisualizer.OceanOfInk, 10, 10, 0) }
        val narrow = naviampGpuRasterSize(NaviampVisualizer.OceanOfInk, 1, Int.MAX_VALUE)
        assertTrue(narrow.width.toLong() * narrow.height <= 512 * 512)
    }

    @Test fun rasterClippingPreservesNonzeroOriginsPartialClipsAndEmptySurfaces() {
        val bounds = Rect(37f, 100f, 1037f, 900f)
        val size = IntSize(500, 400)
        assertEquals(IntRect(0, 0, 500, 400), naviampGpuRasterClip(bounds, bounds, size))
        assertEquals(IntRect(50, 100, 450, 350), naviampGpuRasterClip(bounds, Rect(137f, 200f, 937f, 700f), size))
        assertEquals(IntRect.Zero, naviampGpuRasterClip(bounds, Rect(1100f, 100f, 1200f, 900f), size))
        assertEquals(IntRect.Zero, naviampGpuRasterClip(Rect.Zero, bounds, size))
    }
}
