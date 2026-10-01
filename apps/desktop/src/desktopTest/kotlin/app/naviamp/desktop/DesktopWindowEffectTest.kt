package app.naviamp.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import app.naviamp.ui.NaviampWindowController
import app.naviamp.ui.NaviampWindowPlacement
import kotlin.test.*

class DesktopWindowEffectTest {
    @Test fun nativeFullscreenListenerCanBeRegisteredAndReleased() {
        if (!System.getProperty("os.name").startsWith("Mac")) return
        java.awt.EventQueue.invokeAndWait {
            val window = androidx.compose.ui.awt.ComposeWindow()
            try {
                val effect = DesktopWindowEffect(WindowState())
                val observer = effect.attachNativeFullscreenObserver(window) {}
                observer.close()
            } finally {
                window.dispose()
            }
        }
    }

    @Test fun translatesNativePlacementAndAbsoluteGeometry() {
        for ((native, shared) in listOf(
            WindowPlacement.Floating to NaviampWindowPlacement.Floating,
            WindowPlacement.Maximized to NaviampWindowPlacement.Maximized,
            WindowPlacement.Fullscreen to NaviampWindowPlacement.Fullscreen,
        )) {
            val window = WindowState(placement = native, size = DpSize(1100.dp, 720.dp),
                position = WindowPosition.Absolute((-900).dp, 50.dp))
            val snapshot = DesktopWindowEffect(window).snapshot()
            assertEquals(shared, snapshot.placement)
            assertEquals(1100f, snapshot.width)
            assertEquals(720f, snapshot.height)
            assertEquals(-900f, snapshot.x)
            assertEquals(50f, snapshot.y)
        }
    }

    @Test fun executesSharedRestorationIncludingMaximizedPlacement() {
        for (placement in listOf(WindowPlacement.Floating, WindowPlacement.Maximized)) {
            val window = WindowState(placement = placement, size = DpSize(1100.dp, 720.dp),
                position = WindowPosition.PlatformDefault)
            val effect = DesktopWindowEffect(window)
            val initial = effect.snapshot()
            val controller = NaviampWindowController(effect, initial)
            assertTrue(controller.toggle())
            assertEquals(WindowPlacement.Fullscreen, window.placement)
            controller.observe(effect.snapshot())
            assertTrue(controller.toggle())
            assertEquals(placement, window.placement)
            assertEquals(DpSize(1100.dp, 720.dp), window.size)
            assertEquals(WindowPosition.PlatformDefault, window.position)
        }
    }
}
