package app.naviamp.desktop

import java.awt.EventQueue
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.Window
import java.awt.event.WindowEvent
import javax.swing.JDialog
import kotlin.test.Test
import kotlin.test.BeforeTest
import org.junit.Assume.assumeFalse
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopOwnedWindowAdapterTest {
    @BeforeTest fun requireNativeWindowServer() {
        assumeFalse("AWT ownership tests require a native window server", GraphicsEnvironment.isHeadless())
    }

    @Test fun toolkitObservationConfiguresARealNewOwnedWindow() {
        lateinit var owner: Frame
        lateinit var child: JDialog
        lateinit var observation: AutoCloseable
        EventQueue.invokeAndWait {
            owner = Frame()
            owner.setSize(120, 100)
            owner.isVisible = true
            observation = DesktopOwnedWindowAdapter(owner) {}.attach()
            child = JDialog(owner)
            child.setSize(80, 60)
            child.isAlwaysOnTop = true
            child.isVisible = true
        }
        try {
            // WINDOW_OPENED is posted by AWT; inspect after the EDT has delivered it.
            EventQueue.invokeAndWait { assertFalse(child.isAlwaysOnTop) }
        } finally {
            EventQueue.invokeAndWait { observation.close(); child.dispose(); owner.dispose() }
        }
    }

    @Test fun ownedNativeWindowsFollowOwnerWhileUnrelatedWindowsAreUntouched() = EventQueue.invokeAndWait {
        val owner = Frame()
        val child = JDialog(owner)
        val nested = JDialog(child)
        val unrelated = JDialog()
        try {
            child.isAlwaysOnTop = true
            nested.isAlwaysOnTop = true
            unrelated.isAlwaysOnTop = true
            val adapter = DesktopOwnedWindowAdapter(owner) {}
            adapter.eventDispatched(WindowEvent(child, WindowEvent.WINDOW_OPENED))
            adapter.eventDispatched(WindowEvent(nested, WindowEvent.WINDOW_OPENED))
            adapter.eventDispatched(WindowEvent(unrelated, WindowEvent.WINDOW_OPENED))
            assertFalse(child.isAlwaysOnTop)
            assertFalse(nested.isAlwaysOnTop)
            assertTrue(unrelated.isAlwaysOnTop)
        } finally {
            nested.dispose(); child.dispose(); unrelated.dispose(); owner.dispose()
        }
    }

    @Test fun nativeActivationTransfersStayActiveWithinTheOwnerGroup() = EventQueue.invokeAndWait {
        val owner = Frame()
        val child = JDialog(owner)
        val nested = JDialog(child)
        val unrelated = Frame()
        val observed = mutableListOf<Boolean>()
        try {
            val adapter = DesktopOwnedWindowAdapter(owner, observed::add)
            adapter.eventDispatched(WindowEvent(owner, WindowEvent.WINDOW_ACTIVATED))
            adapter.eventDispatched(WindowEvent(owner, WindowEvent.WINDOW_DEACTIVATED, child))
            adapter.eventDispatched(WindowEvent(child, WindowEvent.WINDOW_DEACTIVATED, nested))
            adapter.eventDispatched(WindowEvent(nested, WindowEvent.WINDOW_DEACTIVATED, unrelated))
            adapter.eventDispatched(WindowEvent(child, WindowEvent.WINDOW_ACTIVATED))
            adapter.eventDispatched(WindowEvent(child, WindowEvent.WINDOW_DEACTIVATED, null as Window?))
            assertEquals(listOf(true, true, true, false, true, false), observed)
        } finally {
            nested.dispose(); child.dispose(); unrelated.dispose(); owner.dispose()
        }
    }
}
