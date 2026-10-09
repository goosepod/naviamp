package app.naviamp.desktop

import app.naviamp.ui.NaviampOwnedWindowPolicy
import app.naviamp.ui.attachNaviampNativeOwnedWindow
import java.awt.AWTEvent
import java.awt.KeyboardFocusManager
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.AWTEventListener
import java.awt.event.WindowEvent

/** AWT ownership and window ordering cannot be implemented in common Kotlin. */
internal class DesktopOwnedWindowAdapter(
    private val owner: Window,
    private val publishActive: (Boolean) -> Unit,
) : AWTEventListener {
    override fun eventDispatched(event: AWTEvent) {
        val windowEvent = event as? WindowEvent ?: return
        if (!windowEvent.window.belongsTo(owner)) return
        when (windowEvent.id) {
            WindowEvent.WINDOW_OPENED -> if (windowEvent.window !== owner) apply(windowEvent.window)
            WindowEvent.WINDOW_ACTIVATED -> publishActive(true)
            WindowEvent.WINDOW_DEACTIVATED -> publishActive(windowEvent.oppositeWindow.belongsTo(owner))
        }
    }

    private fun apply(window: Window) {
        // Compose scene-layer JDialogs request global always-on-top. Execute the shared policy.
        window.isAlwaysOnTop = NaviampOwnedWindowPolicy.alwaysOnTop
        window.owner?.let { attachNaviampNativeOwnedWindow(window, it) }
    }

    fun attach(): AutoCloseable {
        val toolkit = Toolkit.getDefaultToolkit()
        toolkit.addAWTEventListener(this, AWTEvent.WINDOW_EVENT_MASK)
        fun configureChildren(window: Window) {
            window.ownedWindows.forEach { child -> apply(child); configureChildren(child) }
        }
        configureChildren(owner)
        publishActive(KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow.belongsTo(owner))
        return AutoCloseable { toolkit.removeAWTEventListener(this) }
    }
}

/** Ownership is an AWT resource relationship, including nested dialogs and nonfocusable tooltips. */
internal fun Window?.belongsTo(owner: Window): Boolean =
    generateSequence(this) { window -> window.owner }.any { window -> window == owner }
