package app.naviamp.desktop

import androidx.compose.ui.awt.ComposeWindow
import java.awt.EventQueue
import java.awt.Window
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicBoolean
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import app.naviamp.desktop.platform.availableDesktopScreenBounds
import app.naviamp.ui.NaviampWindowEffect
import app.naviamp.ui.NaviampWindowPlacement
import app.naviamp.ui.NaviampWindowScreen
import app.naviamp.ui.NaviampWindowSnapshot
import app.naviamp.ui.onAvailableScreen

/** Native boundary: Compose Desktop WindowState and AWT monitor coordinates cannot run in common Kotlin. */
internal class DesktopWindowEffect(private val window: WindowState) : NaviampWindowEffect {
    private var nativeWindow: ComposeWindow? = null
    private var pendingNativeRestore: NaviampWindowSnapshot? = null

    fun snapshot(): NaviampWindowSnapshot {
        val position = window.position as? WindowPosition.Absolute
        return NaviampWindowSnapshot(
            placement = when (window.placement) {
                WindowPlacement.Floating -> NaviampWindowPlacement.Floating
                WindowPlacement.Maximized -> NaviampWindowPlacement.Maximized
                WindowPlacement.Fullscreen -> NaviampWindowPlacement.Fullscreen
            },
            width = window.size.width.value,
            height = window.size.height.value,
            x = position?.x?.value,
            y = position?.y?.value,
        )
    }

    /** AppKit fullscreen completion is asynchronous and need not emit a later AWT resize event. */
    fun attachNativeFullscreenObserver(nativeWindow: ComposeWindow, publish: (NaviampWindowSnapshot) -> Unit): AutoCloseable {
        this.nativeWindow = nativeWindow
        if (!System.getProperty("os.name").contains("Mac", ignoreCase = true)) {
            return AutoCloseable { this.nativeWindow = null }
        }
        val utilities = Class.forName("com.apple.eawt.FullScreenUtilities")
        val listenerType = Class.forName("com.apple.eawt.FullScreenListener")
        val active = AtomicBoolean(true)
        val listener = Proxy.newProxyInstance(listenerType.classLoader, arrayOf(listenerType)) { proxy, method, args ->
            when (method.name) {
                "windowEnteredFullScreen", "windowExitedFullScreen" -> {
                    EventQueue.invokeLater {
                        if (active.get()) {
                            // AppKit restores decoration after the last AWT reshape. Refresh the
                            // macOS peer's native insets without changing the window's geometry.
                            refreshMacOsWindowInsets(nativeWindow)
                            nativeWindow.invalidate()
                            nativeWindow.validate()
                            val restore = pendingNativeRestore
                            if (method.name == "windowExitedFullScreen" && restore != null) {
                                pendingNativeRestore = null
                                setWindowState(restore)
                            } else {
                                window.placement = nativeWindow.placement
                            }
                            publish(snapshot())
                        }
                    }
                    null
                }
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                "toString" -> "Naviamp AppKit fullscreen listener"
                else -> null
            }
        }
        utilities.getMethod("addFullScreenListenerTo", Window::class.java, listenerType).invoke(null, nativeWindow, listener)
        return AutoCloseable {
            active.set(false)
            this.nativeWindow = null
            pendingNativeRestore = null
            utilities.getMethod("removeFullScreenListenerFrom", Window::class.java, listenerType).invoke(null, nativeWindow, listener)
        }
    }

    override fun apply(snapshot: NaviampWindowSnapshot): Boolean {
        val restored = snapshot.onAvailableScreen(availableDesktopScreenBounds().map {
            NaviampWindowScreen(it.x.toFloat(), it.y.toFloat(), it.width.toFloat(), it.height.toFloat())
        })
        val native = nativeWindow
        if (restored.placement == NaviampWindowPlacement.Maximized && native?.placement == WindowPlacement.Fullscreen) {
            // Compose's Maximized setter does not clear native fullscreen. AppKit must
            // finish exiting its Space before the requested maximization can execute.
            if (System.getProperty("os.name").contains("Mac", ignoreCase = true)) {
                pendingNativeRestore = restored
                native.placement = WindowPlacement.Floating
                return true
            }
            native.placement = WindowPlacement.Floating
        }
        setWindowState(restored)
        return true
    }

    private fun setWindowState(restored: NaviampWindowSnapshot) {
        window.placement = when (restored.placement) {
            NaviampWindowPlacement.Floating -> WindowPlacement.Floating
            NaviampWindowPlacement.Maximized -> WindowPlacement.Maximized
            NaviampWindowPlacement.Fullscreen -> WindowPlacement.Fullscreen
        }
        if (restored.placement != NaviampWindowPlacement.Fullscreen) {
            window.size = DpSize(restored.width.dp, restored.height.dp)
            window.position = restored.x?.let { x -> restored.y?.let { y -> WindowPosition.Absolute(x.dp, y.dp) } }
                ?: WindowPosition.PlatformDefault
        }
    }
}

/** Replays the native reshape boundary so AWT rereads NSWindow's completed decoration insets. */
private fun refreshMacOsWindowInsets(window: Window) {
    val accessor = Class.forName("sun.awt.AWTAccessor").getMethod("getComponentAccessor").invoke(null)
    val peer = Class.forName("sun.awt.AWTAccessor\$ComponentAccessor")
        .getMethod("getPeer", java.awt.Component::class.java).invoke(accessor, window) ?: return
    val bounds = window.bounds
    Class.forName("sun.lwawt.LWWindowPeer").getMethod(
        "notifyReshape", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
        Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
    ).invoke(peer, bounds.x, bounds.y, bounds.width, bounds.height)
}
