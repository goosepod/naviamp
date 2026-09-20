package app.naviamp.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import kotlin.test.*

class NaviampRasterPlacementTest {
    @Test fun translationIncludesOriginAndPreservesSharedTiming() {
        val motion = requireNotNull(marqueeLayerMotion(80f))
        val placement = NaviampRasterLayer(ImageBitmap(200, 20), Offset(12f, 3f), translation = motion).placement(100f, 20f)
        assertEquals(3f, placement.y)
        assertEquals(motion.timesMillis, placement.x.motion!!.timesMillis)
        assertTrue(placement.x.motion.repeat)
        for (time in listOf(0L, 800L, 1200L, 5000L)) {
            assertEquals(12f + motion.valueAt(time), placement.x.motion.valueAt(time), .001f)
        }
    }

    @Test fun playedAndRemainingPixelsShareAnExactMovingEdge() {
        val motion = progressLayerMotion(.2f, 100.0)
        val image = ImageBitmap(200, 20)
        val played = NaviampRasterLayer(image, reveal = .2f, revealMotion = motion).placement(200f, 20f)
        val remaining = NaviampRasterLayer(image, reveal = .2f, revealMotion = motion, clipFromStart = true).placement(200f, 20f)
        assertEquals(played.right, remaining.left)
        assertEquals(0f, played.left.value)
        assertEquals(200f, remaining.right.value)
        assertEquals(40f, played.right.value)
        assertEquals(60f, played.right.motion!!.valueAt(10000), .001f)
    }

    @Test fun layoutOnlySubmissionsKeepMotionClockWhileContentAndResizeRestartIt() {
        val clock = NaviampRasterSceneClock()
        val layers = listOf(NaviampRasterLayer(ImageBitmap(200, 20), translation = marqueeLayerMotion(100f)))
        assertTrue(clock.update(layers, 100f, 20f, 10))
        assertFalse(clock.update(layers.toList(), 100f, 20f, 900))
        assertEquals(10L, clock.startedMillis)
        assertTrue(clock.update(layers, 120f, 20f, 1000))
        assertEquals(1000L, clock.startedMillis)
        assertTrue(clock.update(layers.map { it.copy(translation = null) }, 120f, 20f, 1100))
    }
}
