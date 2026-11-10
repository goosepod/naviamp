package app.naviamp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.TestTimeSource
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalTestApi::class)
class NaviampTooltipPlacementTest {
    @Test fun firstVisibleSurfaceAlreadyHasMeasuredBoundsAndDoesNotCoverItsAnchor() = runComposeUiTest {
        mainClock.autoAdvance = false
        val positions = mutableListOf<Pair<DpSize, DpOffset>>()
        val anchorX = mutableStateOf(300)
        var clicks = 0
        val timeSource = TestTimeSource()
        val surface = object : NaviampOwnedDialogSurface {
            @Composable override fun Render(size: DpSize, properties: PopupProperties,
                onCloseRequest: () -> Unit, content: @Composable () -> Unit) = error("Not a viewport dialog")
            @Composable override fun RenderPositioned(size: DpSize, offset: DpOffset, properties: PopupProperties,
                onCloseRequest: () -> Unit, content: @Composable () -> Unit) {
                assertTrue(!properties.focusable)
                positions += size to offset
                NaviampPositionedTooltipPopup(size, offset, properties, onCloseRequest, content)
            }
        }
        setContent {
            val native = LocalWindowInfo.current
            val focused = remember(native) { object : WindowInfo by native {
                override val isWindowFocused = true
                override val containerSize = IntSize(500, 320)
            } }
            CompositionLocalProvider(LocalDensity provides Density(1f), LocalWindowInfo provides focused,
                LocalNaviampTooltipTimeSource provides timeSource,
                LocalNaviampOwnedDialogSurface provides surface) {
                Box(Modifier.size(500.dp, 320.dp)) {
                    Box(Modifier.offset(anchorX.value.dp, 200.dp)) {
                        NaviampTooltip("Measured tooltip", NaviampColors.Dark) {
                            Button(onClick = { clicks++ }) { Text("Anchor") }
                        }
                    }
                }
            }
        }
        onNodeWithText("Anchor").performMouseInput { moveTo(center) }
        waitForIdle()
        // Commit the hover interaction and its delay before advancing the injected clock.
        mainClock.advanceTimeBy(48)
        timeSource += 400.milliseconds
        mainClock.advanceTimeBy(400)
        assertTrue(positions.isEmpty())
        timeSource += 100.milliseconds
        mainClock.advanceTimeBy(100)
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        assertTrue(positions.isNotEmpty(), "Hover must request a measured tooltip surface")
        onNodeWithText("Measured tooltip").assertIsDisplayed()
        val (size, offset) = positions.first()
        assertTrue(size.width.value > 30 && size.height.value > 10)
        assertTrue(offset.x.value > 200, "First surface must not start at the window origin")
        assertTrue(offset.y.value + size.height.value < 200, "Tooltip must be above, not over, its control")
        onNodeWithText("Anchor").performMouseInput { click() }
        runOnIdle { assertEquals(1, clicks); anchorX.value = 350 }
        mainClock.advanceTimeBy(48)
        assertTrue(positions.last().second.x > offset.x)
    }
}
