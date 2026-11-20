package app.naviamp.ui

import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class VisualizerCompositionClockTest {
    @Test
    fun movingClockIsReadableByDrawCallbacksWithoutRecomposingTheParent() = runTest {
        assertVisualizerClockDrawIsolation()
    }
}
