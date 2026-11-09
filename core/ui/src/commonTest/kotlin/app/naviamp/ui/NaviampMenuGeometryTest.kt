package app.naviamp.ui

import androidx.compose.ui.unit.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.math.abs

class NaviampMenuGeometryTest {
    private val window = IntSize(1000, 740)
    private val padded = IntSize(284, 264)

    @Test fun belowAnchorAttachesTheSurfaceRatherThanItsShadow() {
        val frame = naviampMenuGeometry(IntRect(100, 100, 140, 140), window, padded,
            LayoutDirection.Ltr, IntOffset.Zero, 48, 32)
        assertEquals(IntRect(100, 140, 320, 340), frame.surfaceBounds)
        assertEquals(IntOffset(68, 108), frame.paddedPosition)
    }

    @Test fun flippedMenuEndsAtTheAnchor() {
        val frame = naviampMenuGeometry(IntRect(920, 600, 960, 640), window, padded,
            LayoutDirection.Ltr, IntOffset.Zero, 48, 32)
        assertEquals(IntRect(740, 400, 960, 600), frame.surfaceBounds)
        assertEquals(IntOffset(708, 368), frame.paddedPosition)
    }

    @Test fun rtlAndExplicitOffsetsApplyToTheVisibleSurface() {
        val frame = naviampMenuGeometry(IntRect(400, 100, 440, 140), window, padded,
            LayoutDirection.Rtl, IntOffset(8, 7), 48, 32)
        assertEquals(IntRect(212, 147, 432, 347), frame.surfaceBounds)
        assertEquals(IntOffset(180, 115), frame.paddedPosition)
    }

    @Test fun tallMenuRetainsEqualSpaceForItsShadows() {
        val frame = naviampMenuGeometry(IntRect(100, 600, 140, 640), window, IntSize(284, 740),
            LayoutDirection.Ltr, IntOffset.Zero, 48, 32)
        assertEquals(IntRect(100, 32, 320, 708), frame.surfaceBounds)
        assertEquals(IntOffset(68, 0), frame.paddedPosition)
    }

    @Test fun shadowMayClipAtTheWindowEdgeWithoutDisplacingItsSurface() {
        val frame = naviampMenuGeometry(IntRect(0, 100, 40, 140), window, padded,
            LayoutDirection.Ltr, IntOffset.Zero, 48, 32)
        assertEquals(IntRect(0, 140, 220, 340), frame.surfaceBounds)
        assertEquals(IntOffset(-32, 108), frame.paddedPosition)
    }

    @Test fun belowAndAbovePivotsStayOnTheAnchorEdge() {
        for ((anchor, point) in listOf(
            IntRect(100, 100, 140, 140) to IntOffset(120, 140),
            IntRect(920, 600, 960, 640) to IntOffset(940, 600),
        )) {
            val frame = naviampMenuGeometry(anchor, window, padded,
                LayoutDirection.Ltr, IntOffset.Zero, 48, 32)
            val x = frame.paddedPosition.x + frame.transformOrigin.pivotFractionX * padded.width
            val y = frame.paddedPosition.y + frame.transformOrigin.pivotFractionY * padded.height
            assertTrue(abs(x - point.x) < .001f && abs(y - point.y) < .001f,
                "The scale pivot must stay on the attachment point: expected=$point actual=($x,$y)")
        }
    }

    @Test fun widerAnchorUsesTheCenterOfItsIntersectionWithTheMenu() {
        val frame = naviampMenuGeometry(IntRect(0, 100, 1000, 140), window, padded,
            LayoutDirection.Ltr, IntOffset.Zero, 48, 32)
        assertEquals(.5f, frame.transformOrigin.pivotFractionX)
    }

    @Test fun emptyViewportCannotCreateAnInvalidPivot() {
        val frame = naviampMenuGeometry(IntRect.Zero, IntSize.Zero, IntSize.Zero,
            LayoutDirection.Ltr, IntOffset.Zero, 48, 32)
        assertEquals(.5f, frame.transformOrigin.pivotFractionX)
        assertEquals(.5f, frame.transformOrigin.pivotFractionY)
    }
}
