package app.naviamp.ui

import java.awt.EventQueue
import javax.swing.JDialog
import javax.swing.JFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.jetbrains.skiko.GraphicsApi

class NaviampPopupRendererProbeTest {
    @Test fun rendererComparisonExcludesRejectedDirect3dAndGlobalDefaults() {
        assertNull(probePopupRenderer(null))
        assertEquals(GraphicsApi.OPENGL, probePopupRenderer("OPENGL"))
        assertEquals(GraphicsApi.SOFTWARE_FAST, probePopupRenderer("SOFTWARE_FAST"))
        listOf("DIRECT3D", "DEFAULT", "SOFTWARE_COMPAT", "").forEach { value ->
            assertFailsWith<IllegalStateException> { probePopupRenderer(value) }
        }
    }

    @Test fun tooltipPaddingRejectsBlackRectanglesAndAcceptsTransparentCorners() {
        val before = java.awt.image.BufferedImage(80, 60, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val after = java.awt.image.BufferedImage(80, 60, java.awt.image.BufferedImage.TYPE_INT_RGB)
        for (y in 0 until 60) for (x in 0 until 80) {
            val color = if ((x + y) % 2 == 0) 0x775533 else 0x923456
            before.setRGB(x, y, color)
            after.setRGB(x, y, color)
        }
        val origin = java.awt.Point(100, 200)
        val popup = java.awt.Rectangle(110, 210, 40, 30)
        // Its opaque body is allowed; only shadow-padding corners should reveal the parent.
        for (y in 18 until 32) for (x in 18 until 42) after.setRGB(x, y, 0x24242b)
        verifyProbeTooltipTransparency(before, after, origin, popup)
        for (y in 10 until 40) for (x in 10 until 50) after.setRGB(x, y, 0)
        assertFailsWith<IllegalStateException> {
            verifyProbeTooltipTransparency(before, after, origin, popup)
        }
    }

    @Test fun tooltipOutsideCaptureCannotBeCountedAsPassing() {
        val image = java.awt.image.BufferedImage(80, 60, java.awt.image.BufferedImage.TYPE_INT_RGB)
        assertFailsWith<IllegalStateException> {
            verifyProbeTooltipTransparency(image, image, java.awt.Point(0, 0), java.awt.Rectangle(90, 70, 40, 30))
        }
    }

    @Test fun rendererComparisonOnlyTargetsOwnedPopupsIncludingNestedOnes() {
        EventQueue.invokeAndWait {
            val owner = JFrame()
            val other = JFrame()
            try {
                val popup = JDialog(owner)
                val nested = JDialog(popup)
                val unrelated = JDialog(other)
                val animationSurface = JDialog(owner).apply { name = RasterOverlayWindowName }
                assertFalse(owner.isProbePopupOf(owner))
                assertTrue(popup.isProbePopupOf(owner))
                assertTrue(nested.isProbePopupOf(owner))
                assertFalse(other.isProbePopupOf(owner))
                assertFalse(unrelated.isProbePopupOf(owner))
                assertFalse(animationSurface.isProbePopupOf(owner))
            } finally {
                owner.dispose()
                other.dispose()
            }
        }
    }
}
