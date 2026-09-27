package app.naviamp.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VisualizerCycleTest {
    @Test
    fun movesInBothDirectionsAndWrapsInDisplayedOrder() {
        val first = orderedNaviampVisualizers.first()
        val last = orderedNaviampVisualizers.last()
        assertEquals(orderedNaviampVisualizers[1], cycleNaviampVisualizer(first, VisualizerCycleDirection.Next))
        assertEquals(last, cycleNaviampVisualizer(first, VisualizerCycleDirection.Previous))
        assertEquals(first, cycleNaviampVisualizer(last, VisualizerCycleDirection.Next))
    }

    @Test
    fun unavailableAndSingleChoiceDoNotCycle() {
        val current = NaviampVisualizer.AudioSphere
        assertNull(cycleNaviampVisualizer(current, VisualizerCycleDirection.Next, emptyList()))
        assertNull(cycleNaviampVisualizer(current, VisualizerCycleDirection.Next, listOf(current)))
    }

    @Test
    fun removedSelectionRecoversToTheEndInRequestedDirection() {
        val choices = listOf(NaviampVisualizer.AudioSphere, NaviampVisualizer.ReactiveBars)
        assertEquals(choices.first(), cycleNaviampVisualizer(NaviampVisualizer.VinylGroove, VisualizerCycleDirection.Next, choices))
        assertEquals(choices.last(), cycleNaviampVisualizer(NaviampVisualizer.VinylGroove, VisualizerCycleDirection.Previous, choices))
    }
}
