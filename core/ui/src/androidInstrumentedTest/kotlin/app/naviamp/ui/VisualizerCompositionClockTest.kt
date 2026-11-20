package app.naviamp.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VisualizerCompositionClockTest {
    @Test
    fun movingClockIsReadableByDrawCallbacksWithoutRecomposingTheParent() = runTest {
        assertVisualizerClockDrawIsolation()
    }
}
