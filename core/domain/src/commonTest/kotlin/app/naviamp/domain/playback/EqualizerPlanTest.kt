package app.naviamp.domain.playback

import kotlin.test.*

class EqualizerPlanTest {
    @Test fun preservesAllTenBandsAtCdSampleRate() {
        val plan = planEqualizer(List(10) { 6f }, 44_100)
        assertEquals(EqualizerBandFrequencies.map { it.toFloat() }, plan.map { it.frequencyHz })
        assertTrue(plan.all { it.gainDb == 6f && it.bandwidthOctaves == 1.5f })
        assertContentEquals(floatArrayOf(31f, 1.5f, 6f), plan.take(1).toNativeEqualizerParameters())
    }

    @Test fun omitsInaudibleBandsWithoutRetuningThem() {
        assertEquals(listOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f),
            planEqualizer(List(10) { -6f }, 8000).map { it.frequencyHz })
        assertFailsWith<IllegalArgumentException> { planEqualizer(listOf(1f), 0) }
    }

    @Test fun boundsGainsAndIgnoresMissingFlatAndNonfiniteBands() {
        val plan = planEqualizer(listOf(99f, -99f, Float.NaN, Float.POSITIVE_INFINITY, 0.01f), 48_000)
        assertEquals(listOf(12f, -12f), plan.map { it.gainDb })
        assertTrue(planEqualizer(emptyList(), 48_000).isEmpty())
        assertEquals(10, planEqualizer(List(15) { 1f }, 48_000).size)
    }
}
