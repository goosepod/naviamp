package app.naviamp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

@OptIn(ExperimentalTestApi::class)
class NaviampMenuAttachmentTest {
    @Test fun visibleMenuStartsAtTheAnchorBelow() = verifyAttachment(false, LayoutDirection.Ltr)
    @Test fun visibleMenuEndsAtTheAnchorAbove() = verifyAttachment(true, LayoutDirection.Ltr)
    @Test fun rtlVisibleMenuStartsAtTheAnchorBelow() = verifyAttachment(false, LayoutDirection.Rtl)
    @Test fun rtlVisibleMenuEndsAtTheAnchorAbove() = verifyAttachment(true, LayoutDirection.Rtl)
    @Test fun openingMenuKeepsItsBottomAnchorStationary() = verifyAttachment(false, LayoutDirection.Ltr, true)
    @Test fun openingMenuKeepsItsTopAnchorStationary() = verifyAttachment(true, LayoutDirection.Ltr, true)
    @Test fun lowerPaddingRemainsInsideTheMenuAfterEntrance() = verifyAttachment(false, LayoutDirection.Ltr, inspectPadding = true)
    @Test fun upperPaddingRemainsInsideTheMenuAfterEntrance() = verifyAttachment(true, LayoutDirection.Ltr, inspectPadding = true)

    private fun verifyAttachment(above: Boolean, direction: LayoutDirection,
        inspectOpening: Boolean = false, inspectPadding: Boolean = false) = runComposeUiTest {
        mainClock.autoAdvance = !inspectOpening
        var open by mutableStateOf(false)
        var anchorCoordinates: LayoutCoordinates? = null
        var menuCoordinates: LayoutCoordinates? = null
        setContent {
            CompositionLocalProvider(LocalNaviampOwnedPopupWindows provides true,
                LocalLayoutDirection provides direction) {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.align(if (above) Alignment.BottomStart else Alignment.TopStart)
                        .offset(100.dp, if (above) (-80).dp else 100.dp).size(40.dp)
                        .testTag("anchor").onGloballyPositioned { anchorCoordinates = it }.clickable { open = true }) {
                        NaviampDropdownMenu(open, { open = false }, Modifier.testTag("menu")
                            .onGloballyPositioned { menuCoordinates = it }) {
                            repeat(3) { NaviampDropdownMenuItem("Action $it", onClick = {}) }
                        }
                    }
                }
            }
        }
        onNodeWithTag("anchor").performClick()
        waitForIdle()
        // Parent and popup are independent roots on Android; compare their window coordinates.
        val anchor = runOnIdle { checkNotNull(anchorCoordinates).boundsInWindow() }
        if (inspectOpening) {
            val expected = if (above) anchor.top else anchor.bottom
            val edges = buildList {
                repeat(14) { frame ->
                    mainClock.advanceTimeByFrame()
                    waitForIdle()
                    // The first tick starts the animation at zero opacity; observe every later tick.
                    if (frame > 0) {
                        val bounds = runOnIdle { checkNotNull(menuCoordinates).boundsInWindow() }
                        add(if (above) bounds.bottom else bounds.top)
                    }
                }
            }
            assertTrue(edges.all { abs(it - expected) <= 1f },
                "Visible attachment must stay at $expected through entrance (above=$above): $edges")
        }
        val menu = runOnIdle { checkNotNull(menuCoordinates).boundsInWindow() }
        val expectedY = if (above) anchor.top else anchor.bottom
        val actualY = if (above) menu.bottom else menu.top
        assertTrue(abs(actualY - expectedY) <= 1f,
            "Visible menu must touch its anchor (above=$above, $direction): anchor=$anchor menu=$menu")
        val expectedX = if (direction == LayoutDirection.Ltr) anchor.left else anchor.right
        val actualX = if (direction == LayoutDirection.Ltr) menu.left else menu.right
        assertTrue(abs(actualX - expectedX) <= 1f,
            "Shadow padding must not move the visible start edge: anchor=$anchor menu=$menu")
        val pixels = onNodeWithTag("menu").captureToImage().toPixelMap()
        var labelPixels = 0
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            val pixel = pixels[x, y]
            if (pixel.alpha > .5f && pixel.red > .7f && pixel.green > .7f && pixel.blue > .7f) labelPixels++
        }
        assertTrue(labelPixels > 50, "Correct semantics must also paint readable menu labels")
        if (inspectPadding) {
            val rootBounds = onNodeWithTag("menu").fetchSemanticsNode().boundsInRoot
            val inside = Offset(rootBounds.center.x, if (above) rootBounds.top + 2f else rootBounds.bottom - 2f)
            onAllNodes(isRoot()).onLast().performTouchInput { click(inside) }
            runOnIdle { assertTrue(open, "An inside-padding click must not dismiss the menu") }
            onAllNodes(isRoot()).onLast().performTouchInput { click(Offset(8f, 8f)) }
            runOnIdle { assertFalse(open, "An outside click must still dismiss the menu") }
        }
    }
}
