package app.naviamp.ui

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.test.*

class NaviampRasterFrameScheduleTest {
    @Test fun translationKeepsItsCadenceWhileStaticAndFinishedContentStop() {
        val progress = progressLayerMotion(.25f, 600.0)
        val translation = NaviampLayerMotion(listOf(0f, -100f), listOf(0L, 2400L))
        assertNull(naviampRasterMotionDelay(null, null, 1000f, 50L))
        assertTrue(assertNotNull(naviampRasterMotionDelay(null, progress, 1000f, 50L)) > 16L)
        assertEquals(16L, naviampRasterMotionDelay(translation, progress, 1000f, 50L))
        assertNull(naviampRasterMotionDelay(translation, null, 1000f, 2400L))
        assertEquals(16L, naviampRasterMotionDelay(translation.copy(repeat = true), null, 1000f, 2400L))
    }

    @Test fun deadlinesPresentEveryReferencePixelOnTheSameVsyncWithoutContinuousWakeups() {
        for (motion in listOf(
            NaviampLayerMotion(listOf(.25f, 1f), listOf(0L, 450_000L)),
            NaviampLayerMotion(listOf(.75f, 0f), listOf(0L, 450_000L)),
            NaviampLayerMotion(listOf(.2f, .2f, .8f), listOf(0L, 800L, 360_800L)),
        )) {
            var due = 0L
            var presentedFloor = 0f
            var presentedCeil = 0f
            var callbacks = 0
            for (now in 0L..10_000L step 16L) {
                val edge = motion.valueAt(now) * 1000f
                if (now >= due) {
                    callbacks++
                    presentedFloor = floor(edge)
                    presentedCeil = ceil(edge)
                    due = now + (motion.nextPixelChangeDelay(1000f, now) ?: Long.MAX_VALUE / 2)
                }
                assertEquals(floor(edge), presentedFloor, "floor at $now")
                assertEquals(ceil(edge), presentedCeil, "ceil at $now")
            }
            assertTrue(callbacks < 60, "$callbacks callbacks for approximately 17 changing pixel edges")
        }
    }

    @Test fun holdsRepeatBoundariesAndInvalidSurfacesNeitherSpinNorFreeze() {
        val motion = NaviampLayerMotion(listOf(0f, 0f, 1f, 1f), listOf(0L, 800L, 1800L, 2600L))
        assertEquals(800L, motion.nextPixelChangeDelay(100f, 0L))
        assertEquals(800L, motion.nextPixelChangeDelay(100f, 1800L))
        assertNull(motion.nextPixelChangeDelay(100f, 2600L))
        for (width in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertNull(motion.nextPixelChangeDelay(width, 0L))
        }
        assertEquals(800L, motion.copy(repeat = true).nextPixelChangeDelay(100f, 2600L))
    }

    @Test fun inputPreemptsADeferredFrameAndHidingCancelsIt() {
        val schedule = NaviampRasterFrameSchedule()
        assertEquals(NaviampRasterFrameRequest.Schedule(600), schedule.request(listOf(600), 0))
        assertEquals(NaviampRasterFrameRequest.Unchanged, schedule.request(listOf(600), 10))
        assertEquals(NaviampRasterFrameRequest.Schedule(0), schedule.request(listOf(0, 16), 20))
        schedule.delivered()
        assertEquals(NaviampRasterFrameRequest.Schedule(16), schedule.request(listOf(600, 16), 20))
        assertEquals(NaviampRasterFrameRequest.Cancel, schedule.request(emptyList(), 21))
        assertEquals(NaviampRasterFrameRequest.Unchanged, schedule.request(emptyList(), 22))
    }
}
