package app.naviamp.ui

import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NaviampCoverArtDecodingTest {
    @Test
    fun thumbnailsPreserveAspectRatioWithoutUpscaling() {
        assertEquals(IntSize(20, 10), naviampCoverArtSize(80, 40, 20))
        assertEquals(IntSize(10, 20), naviampCoverArtSize(40, 80, 20))
        assertEquals(IntSize(80, 40), naviampCoverArtSize(80, 40, 160))
        assertEquals(IntSize(1, 1), naviampCoverArtSize(80, 40, 0))
    }

    @Test
    fun paletteIgnoresTransparentPixelsAndPreservesOpaqueColors() {
        val pixels = intArrayOf(0xffff0000.toInt(), 0, 0xc800ff00.toInt(), 0xc90000ff.toInt())
        assertEquals(
            listOf(NaviampRgbSample(255, 0, 0), NaviampRgbSample(0, 0, 255)),
            naviampCoverArtSamples(pixels, 2, 2),
        )
        assertTrue(naviampCoverArtSamples(IntArray(4), 2, 2).isEmpty())
    }
}
