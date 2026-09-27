package app.naviamp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.center
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.right
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class VisualizerSwipeUiTest {
    @Test
    fun deliberateHorizontalTouchCyclesExactlyOncePerGesture() = runComposeUiTest {
        val directions = mutableListOf<VisualizerCycleDirection>()
        setContent { Box(Modifier.size(300.dp, 200.dp).visualizerSwipe(directions::add).testTag("visualizer")) }
        val surface = onNodeWithTag("visualizer")

        surface.performTouchInput {
            down(center)
            moveTo(Offset(1f, center.y), delayMillis = 200)
            up()
        }
        surface.performTouchInput {
            down(center)
            moveTo(Offset(right - 1f, center.y), delayMillis = 200)
            up()
        }
        runOnIdle {
            assertEquals(listOf(VisualizerCycleDirection.Next, VisualizerCycleDirection.Previous), directions)
        }
    }

    @Test
    fun shortAndVerticalTouchesDoNotCycle() = runComposeUiTest {
        val directions = mutableListOf<VisualizerCycleDirection>()
        setContent { Box(Modifier.size(300.dp, 200.dp).visualizerSwipe(directions::add).testTag("visualizer")) }
        val surface = onNodeWithTag("visualizer")

        surface.performTouchInput {
            down(center)
            moveTo(Offset(center.x + 20f, center.y), delayMillis = 200)
            up()
        }
        surface.performTouchInput {
            down(center)
            moveTo(Offset(center.x + 20f, 1f), delayMillis = 200)
            up()
        }
        surface.performTouchInput {
            down(Offset(1f, center.y))
            moveTo(Offset(right - 1f, center.y), delayMillis = 200)
            up()
        }
        runOnIdle { assertEquals(emptyList(), directions) }
    }

    @Test
    fun mouseDragAndTrackpadStyleScrollDoNotCycle() = runComposeUiTest {
        val directions = mutableListOf<VisualizerCycleDirection>()
        setContent { Box(Modifier.size(300.dp, 200.dp).visualizerSwipe(directions::add).testTag("visualizer")) }
        onNodeWithTag("visualizer").performMouseInput {
            moveTo(center)
            press()
            moveTo(Offset(1f, center.y))
            release()
            scroll(Offset(180f, 0f))
        }
        runOnIdle { assertEquals(emptyList(), directions) }
    }
}
