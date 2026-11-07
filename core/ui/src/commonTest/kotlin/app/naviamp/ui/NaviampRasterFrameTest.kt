package app.naviamp.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.test.*

class NaviampRasterFrameTest {
    private val bounds = Rect(100f, 200f, 660f, 240f)
    private fun NaviampRasterFrame.sample(
        time: Long = 0, motion: NaviampLayerMotion? = null, clip: Rect = bounds,
        reveal: Float = 1f, revealMotion: NaviampLayerMotion? = null, remaining: Boolean = false,
        origin: Offset = Offset.Zero,
    ) = update(800, 40, origin, motion, reveal, revealMotion, remaining, bounds, clip, time)

    @Test fun initialFrameAndNewNativeLifetimeAlwaysSubmit() {
        val frame = NaviampRasterFrame()
        assertTrue(frame.sample())
        assertTrue(frame.visible)
        assertFalse(frame.sample())
        assertTrue(NaviampRasterFrame().sample())
    }

    @Test fun translationPreservesPixelRoundingAndMovingClip() {
        val frame = NaviampRasterFrame()
        val motion = NaviampLayerMotion(listOf(0f, -100f), listOf(0, 1000))
        assertTrue(frame.sample(11, motion, Rect(110f, 205f, 650f, 235f)))
        assertEquals(listOf(11, 5, 552, 35), listOf(frame.sourceLeft, frame.sourceTop, frame.sourceRight, frame.sourceBottom))
        assertEquals(listOf(10, 5, 550, 35), listOf(frame.destinationLeft, frame.destinationTop, frame.destinationRight, frame.destinationBottom))
        assertFalse(frame.sample(12, motion, Rect(110f, 205f, 650f, 235f)))
        assertTrue(frame.sample(21, motion, Rect(110f, 205f, 650f, 235f)))
        assertEquals(12, frame.sourceLeft)
    }

    @Test fun unchangedHoldDoesNotSubmitButMotionResumesOnOriginalTimeline() {
        val frame = NaviampRasterFrame()
        val motion = requireNotNull(marqueeLayerMotion(100f))
        assertTrue(frame.sample(0, motion))
        for (time in 16L..800L step 16) assertFalse(frame.sample(time, motion))
        assertTrue(frame.sample(816, motion))
        assertTrue(frame.sample(motion.durationMillis, motion))
        assertEquals(0, frame.sourceLeft)
    }

    @Test fun hiddenRestoreAndChangedClipStillSubmit() {
        val frame = NaviampRasterFrame()
        assertTrue(frame.sample())
        assertTrue(frame.sample(clip = Rect.Zero))
        assertFalse(frame.visible)
        assertFalse(frame.sample(clip = Rect.Zero))
        assertTrue(frame.sample())
        assertTrue(frame.sample(clip = Rect(110f, 200f, 660f, 240f)))
        assertEquals(10, frame.sourceLeft)
        assertTrue(frame.sample(origin = Offset(0f, 2f)))
        assertEquals(2, frame.destinationTop)
    }

    @Test fun complementaryProgressPreservesEveryExistingPixelEdgeAndEndpoint() {
        val played = NaviampRasterFrame()
        val remaining = NaviampRasterFrame()
        val motion = requireNotNull(progressLayerMotion(.2f, 300.0))
        var submissions = 0
        var ticks = 0
        for (time in 0L..motion.durationMillis step 16) {
            if (played.sample(time, revealMotion = motion)) submissions++
            remaining.sample(time, revealMotion = motion, remaining = true)
            val edge = bounds.width * motion.valueAt(time)
            assertEquals(kotlin.math.ceil(edge).toInt(), played.destinationRight)
            if (remaining.visible) assertEquals(kotlin.math.floor(edge).toInt(), remaining.destinationLeft)
            ticks++
        }
        assertTrue(submissions < ticks / 10, "$submissions changed frames from $ticks sampled ticks")
        played.sample(motion.durationMillis, revealMotion = motion)
        remaining.sample(motion.durationMillis, revealMotion = motion, remaining = true)
        assertEquals(560, played.destinationRight)
        assertFalse(remaining.visible)
    }
}
