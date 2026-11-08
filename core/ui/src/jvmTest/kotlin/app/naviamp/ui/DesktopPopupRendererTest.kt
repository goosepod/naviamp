package app.naviamp.ui

import java.awt.EventQueue
import java.awt.GraphicsEnvironment
import javax.swing.JDialog
import javax.swing.JFrame
import org.junit.Assume.assumeFalse
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import org.jetbrains.skiko.GraphicsApi
import org.jetbrains.skiko.SkiaLayer

/** Exercises AWT ownership guards; portable menu and animation behavior is tested in Core. */
class DesktopPopupRendererTest {
    @Test fun futureLayersUseThePopupDefaultWithoutChangingTheExistingPrimaryLayer() = withWindows { parent, _ ->
        val previous = System.getProperty("skiko.renderApi")
        val primary = SkiaLayer()
        parent.contentPane.add(primary)
        var popup: SkiaLayer? = null
        try {
            val originalRenderer = primary.renderApi
            // Skiko's fast software redrawer is Windows-only; exercise its supported native
            // software implementation on the other window-server runners.
            val popupRenderer = if (System.getProperty("os.name").startsWith("Windows"))
                GraphicsApi.SOFTWARE_FAST else GraphicsApi.SOFTWARE_COMPAT
            configureNaviampDesktopPopupRenderer(parent, popupRenderer.name)
            popup = SkiaLayer()
            assertEquals(originalRenderer, primary.renderApi)
            assertEquals(popupRenderer, popup.renderApi)
        } finally {
            popup?.dispose()
            primary.dispose()
            if (previous == null) System.clearProperty("skiko.renderApi") else System.setProperty("skiko.renderApi", previous)
        }
    }

    @Test fun absentOrDefaultOverrideLeavesUnconfiguredWindowsAlone() = withWindows { parent, popup ->
        listOf(null, "", "  ", "DEFAULT", " default ").forEach { api ->
            configureNaviampDesktopPopupRenderer(parent, api)
            configureNaviampDesktopPopupRenderer(popup, api)
        }
    }

    @Test fun initializationCannotRunFromAnOwnedPopup() = withWindows { _, popup ->
        assertFailsWith<IllegalArgumentException> {
            configureNaviampDesktopPopupRenderer(popup, "OPENGL")
        }
    }

    @Test fun requestedOverrideFailsIfThePrimarySkikoSurfaceIsMissing() = withWindows { parent, _ ->
        assertFailsWith<IllegalStateException> {
            configureNaviampDesktopPopupRenderer(parent, "OPENGL")
        }
    }

    private fun withWindows(block: (JFrame, JDialog) -> Unit) {
        assumeFalse(GraphicsEnvironment.isHeadless())
        EventQueue.invokeAndWait {
            val parent = JFrame()
            val popup = JDialog(parent)
            try { block(parent, popup) } finally { popup.dispose(); parent.dispose() }
        }
    }
}
