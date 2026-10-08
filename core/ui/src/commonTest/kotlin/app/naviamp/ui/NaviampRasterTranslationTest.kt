package app.naviamp.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntRect
import kotlin.test.*

class NaviampRasterTranslationTest {
    @Test fun scrollingRetainsFractionalPositionAndFixedViewport() {
        val bounds = Rect(37f, 100f, 237f, 120f)
        val clip = Rect(47f, 102f, 227f, 118f)
        val a = assertNotNull(naviampRasterTranslation(bounds, clip, Offset(2f, 3f), -10.25f))
        val b = assertNotNull(naviampRasterTranslation(bounds, clip, Offset(2f, 3f), -10.75f))
        assertEquals(IntRect(10, 2, 190, 18), a.viewport)
        assertEquals(a.viewport, b.viewport)
        assertEquals(Offset(-8.25f, 3f), a.position)
        assertEquals(Offset(-8.75f, 3f), b.position)
    }

    @Test fun layoutMovesPreserveLocalGeometryAndClippedSurfacesDisappear() {
        val bounds = Rect(37f, 100f, 237f, 120f)
        val clip = Rect(47f, 102f, 227f, 118f)
        val origin = Offset(2f, 3f)
        val first = naviampRasterTranslation(bounds, clip, origin, 10f)
        assertEquals(first, naviampRasterTranslation(bounds.translate(20f, 30f), clip.translate(20f, 30f), origin, 10f))
        assertNull(naviampRasterTranslation(bounds, Rect(300f, 100f, 400f, 120f), origin, 10f))
        assertNotEquals(first, naviampRasterTranslation(Rect(37f, 100f, 137f, 120f), clip, origin, 10f))
    }
}
