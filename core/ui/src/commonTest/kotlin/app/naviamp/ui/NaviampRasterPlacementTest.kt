package app.naviamp.ui

import androidx.compose.ui.geometry.Offset
import kotlin.test.*

class NaviampRasterPlacementTest {
    @Test fun translationIncludesOriginAndPreservesSharedTiming() {
        val motion = requireNotNull(marqueeLayerMotion(80f))
        val placement = naviampRasterPlacement(Offset(12f, 3f), motion, 1f, null, false, 100f, 20f)
        assertEquals(3f, placement.y)
        assertEquals(motion.timesMillis, placement.x.motion!!.timesMillis)
        assertTrue(placement.x.motion.repeat)
        for (time in listOf(0L, 800L, 1200L, 5000L)) {
            assertEquals(12f + motion.valueAt(time), placement.x.motion.valueAt(time), .001f)
        }
    }

    @Test fun playedAndRemainingPixelsShareAnExactMovingEdge() {
        val motion = progressLayerMotion(.2f, 100.0)
        val played = naviampRasterPlacement(Offset.Zero, null, .2f, motion, false, 200f, 20f)
        val remaining = naviampRasterPlacement(Offset.Zero, null, .2f, motion, true, 200f, 20f)
        assertEquals(played.right, remaining.left)
        assertEquals(0f, played.left.value)
        assertEquals(200f, remaining.right.value)
        assertEquals(40f, played.right.value)
        assertEquals(60f, played.right.motion!!.valueAt(10000), .001f)
    }

    @Test fun layoutOnlySubmissionsKeepMotionClockWhileContentAndResizeRestartIt() {
        val clock = NaviampRasterSceneClock()
        val scene = listOf(marqueeLayerMotion(100f))
        assertTrue(clock.updateScene(scene, 100f, 20f, 10))
        assertFalse(clock.updateScene(scene.toList(), 100f, 20f, 900))
        assertEquals(10L, clock.startedMillis)
        assertTrue(clock.updateScene(scene, 120f, 20f, 1000))
        assertEquals(1000L, clock.startedMillis)
        assertTrue(clock.updateScene(listOf(null), 120f, 20f, 1100))
    }
}
