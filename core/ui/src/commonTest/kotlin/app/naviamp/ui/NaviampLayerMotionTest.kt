package app.naviamp.ui

import kotlin.test.*

class NaviampLayerMotionTest {
    @Test fun marqueePausesAtBothEndsAndRepeatsWithoutJumping() {
        val motion = requireNotNull(marqueeLayerMotion(100f))
        assertEquals(0f, motion.valueAt(400))
        assertEquals(-50f, motion.valueAt(2000))
        assertEquals(-100f, motion.valueAt(3600))
        assertEquals(-50f, motion.valueAt(5200))
        assertEquals(0f, motion.valueAt(6400))
        assertEquals(0f, motion.valueAt(6800))
    }

    @Test fun rightToLeftReversesDirectionAndShortOverflowKeepsMinimumTravelTime() {
        val motion = requireNotNull(marqueeLayerMotion(10f, rightToLeft = true))
        assertEquals(5f, motion.valueAt(1700))
        assertEquals(5200L, motion.durationMillis)
    }

    @Test fun noMotionForFittingTextOrInvalidInputs() {
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY).forEach { assertNull(marqueeLayerMotion(it)) }
        listOf(null, 0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { assertNull(progressLayerMotion(.2f, it)) }
        assertNull(progressLayerMotion(Float.NaN, 300.0))
        assertNull(progressLayerMotion(1f, 300.0))
    }

    @Test fun progressAdvancesAtTrackSpeedAndStopsAtEnd() {
        val motion = requireNotNull(progressLayerMotion(.2f, 300.0))
        assertEquals(.2f, motion.valueAt(-10))
        assertEquals(.3f, motion.valueAt(30_000), .00001f)
        assertEquals(1f, motion.valueAt(400_000))
        assertFalse(motion.repeat)
    }

    @Test fun rejectsMalformedNativeAnimationData() {
        assertFailsWith<IllegalArgumentException> { NaviampLayerMotion(listOf(0f), listOf(0L)) }
        assertFailsWith<IllegalArgumentException> { NaviampLayerMotion(listOf(0f, 1f), listOf(0L, 0L)) }
        assertFailsWith<IllegalArgumentException> { NaviampLayerMotion(listOf(0f, Float.NaN), listOf(0L, 1L)) }
    }

    @Test fun playbackPredictionPreservesContinuityButHonorsSeekAndPause() {
        val prediction = NaviampProgressPrediction()
        assertEquals(.2f, prediction.update(.2f, true, 300.0, 0))
        assertEquals(.2033333f, prediction.update(.203f, true, 300.0, 1000), .00001f)
        assertEquals(.7f, prediction.update(.7f, true, 300.0, 2000))
        assertEquals(.701f, prediction.update(.701f, false, 300.0, 2500))
        assertEquals(.701f, prediction.update(.701f, true, 300.0, 9000))
    }
}
