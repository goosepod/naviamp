package app.naviamp.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NaviampNowPlayingSheetPolicyTest {
    private val fling = 1000f

    private fun settlesOpen(startOpen: Boolean, closedFraction: Float, velocity: Float) =
        naviampNowPlayingSheetSettlesOpen(startOpen, closedFraction, velocity, fling)

    @Test
    fun dismissNeedsDistanceOrFling() {
        assertTrue(settlesOpen(startOpen = true, closedFraction = 0.2f, velocity = 200f))
        assertFalse(settlesOpen(startOpen = true, closedFraction = NaviampNowPlayingSheetDistanceFraction, velocity = 0f))
        assertFalse(settlesOpen(startOpen = true, closedFraction = 0.1f, velocity = fling))
    }

    @Test
    fun upwardFlingCancelsALongDismissDrag() {
        assertTrue(settlesOpen(startOpen = true, closedFraction = 0.6f, velocity = -fling))
    }

    @Test
    fun openingFromTheMiniPlayerUsesTheSameRules() {
        assertFalse(settlesOpen(startOpen = false, closedFraction = 0.8f, velocity = -200f))
        assertTrue(settlesOpen(startOpen = false, closedFraction = 1f - NaviampNowPlayingSheetDistanceFraction, velocity = 0f))
        assertTrue(settlesOpen(startOpen = false, closedFraction = 0.95f, velocity = -fling))
        assertFalse(settlesOpen(startOpen = false, closedFraction = 0.3f, velocity = fling))
    }

    @Test
    fun flingThresholdScalesWithDensity() {
        assertEquals(2 * naviampNowPlayingSheetFlingThresholdPx(density = 1f), naviampNowPlayingSheetFlingThresholdPx(density = 2f))
    }
}
