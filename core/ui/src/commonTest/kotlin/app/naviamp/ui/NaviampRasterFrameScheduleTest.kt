package app.naviamp.ui

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.test.*

class NaviampRasterFrameScheduleTest {
    @Test fun regionSchedulingKeepsTranslationCadenceAndStopsStaticOrFinishedContent() {
        val progress = progressLayerMotion(.25f, 600.0)
        val translation = NaviampLayerMotion(listOf(0f, -100f), listOf(0L, 2400L))
        assertNull(naviampRasterMotionDelay(null, null, 1000f, 50L))
        val progressDelay = assertNotNull(naviampRasterMotionDelay(null, progress, 1000f, 50L))
        assertTrue(progressDelay > 16L)
        val translationDelay = assertNotNull(naviampRasterMotionDelay(translation, null, 1000f, 50L))
        assertEquals(16L, minOf(progressDelay, translationDelay))
        assertNull(naviampRasterMotionDelay(translation, null, 1000f, 2400L))
        assertEquals(16L, naviampRasterMotionDelay(translation.copy(repeat = true), null, 1000f, 2400L))
    }

    @Test fun pixelDeadlinesPreserveEveryDisplayedProgressFrameWithoutContinuousWakeups() {
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
            assertTrue(callbacks < 60, "only about 17 pixel boundaries in 10 seconds, got $callbacks")
        }
    }

    @Test fun pausesEndpointsAndRepeatingKeyframesDoNotSpinOrFreeze() {
        val motion = NaviampLayerMotion(listOf(0f, 0f, 1f, 1f), listOf(0L, 800L, 1800L, 2600L))
        assertEquals(800L, motion.nextPixelChangeDelay(100f, 0L))
        assertEquals(800L, motion.nextPixelChangeDelay(100f, 1800L))
        assertNull(motion.nextPixelChangeDelay(100f, 2600L))
        assertNull(motion.nextPixelChangeDelay(0f, 0L))
        assertEquals(800L, motion.copy(repeat = true).nextPixelChangeDelay(100f, 2600L))
    }

    @Test fun inputPreemptsLongWaitsAndHidingCancelsTheCallback() {
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
