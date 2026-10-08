package app.naviamp.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntRect
import kotlin.test.*

class NaviampRasterPixelCropTest {
    @Test fun subpixelProgressCommitsExactlyWhenThePresentedPixelsChange() {
        val bounds = Rect(37f, 1200f, 1037f, 1210f)
        val state = NaviampRasterPixelCropState()
        var commits = 0
        // A 10 minute track advances 1000 pixels. Native pixel geometry at every 60 Hz
        // tick is unchanged by suppressing redundant commits between pixel boundaries.
        var lastCommitted: NaviampRasterPixelCrop? = null
        repeat(600) { frame ->
            val progress = .25f + frame / 60f / 600f
            val expected = naviampRasterPixelCrop(bounds, bounds, bounds, progress, false)
            if (state.update(expected)) { commits++; lastCommitted = expected }
            assertEquals(expected, lastCommitted, "visible frame $frame must be identical")
        }
        assertTrue(commits in 16..19, "expected about 17 pixel changes, got $commits")
    }

    @Test fun clippingAndOppositeRevealKeepNonzeroWindowCoordinates() {
        val bounds = Rect(37f, 100f, 237f, 120f)
        val clip = Rect(47f, 102f, 227f, 118f)
        val image = Rect(32f, 100f, 252f, 120f)
        val played = assertNotNull(naviampRasterPixelCrop(image, bounds, clip, .25f, false))
        assertEquals(IntRect(15, 2, 55, 18), played.source)
        assertEquals(IntRect(10, 2, 50, 18), played.destination)
        val remaining = assertNotNull(naviampRasterPixelCrop(image, bounds, clip, .25f, true))
        assertEquals(IntRect(55, 2, 195, 18), remaining.source)
        assertEquals(IntRect(50, 2, 190, 18), remaining.destination)
        assertNull(naviampRasterPixelCrop(image, bounds, clip, 0f, false))
    }

    @Test fun seeksResizesBuffersAndRestorationCommitImmediately() {
        val state = NaviampRasterPixelCropState()
        val bounds = Rect(0f, 0f, 200f, 20f)
        val first = naviampRasterPixelCrop(bounds, bounds, bounds, .2f, false)
        val seek = naviampRasterPixelCrop(bounds, bounds, bounds, .7f, false)
        assertTrue(state.update(first))
        assertFalse(state.update(first))
        assertTrue(state.update(first, force = true))
        assertTrue(state.update(seek))
        assertTrue(state.update(null))
        assertFalse(state.update(null))
        assertTrue(state.update(seek))
        val resized = Rect(0f, 0f, 100f, 20f)
        assertTrue(state.update(naviampRasterPixelCrop(resized, resized, resized, .7f, false)))
    }
}
