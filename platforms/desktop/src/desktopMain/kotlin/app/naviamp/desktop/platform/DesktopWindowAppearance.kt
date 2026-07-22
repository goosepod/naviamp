package app.naviamp.desktop.platform

import java.awt.Window
import javax.swing.SwingUtilities

/** Desktop OS appearance facts applied before AWT creates the application and per-window chrome. */
fun configureDesktopHostAppearance() {
    System.setProperty("compose.application.name", "Naviamp")
    System.setProperty("apple.awt.application.name", "Naviamp")
    System.setProperty("sun.awt.application.name", "Naviamp")
    if (isMacOs()) System.setProperty("apple.awt.application.appearance", "system")
}

fun configureDesktopWindowAppearance(window: Window, isDark: Boolean) {
    if (!isMacOs()) return
    runCatching {
        SwingUtilities.getRootPane(window)?.putClientProperty(
            "apple.awt.windowAppearance",
            if (isDark) "NSAppearanceNameDarkAqua" else "NSAppearanceNameAqua",
        )
    }
}

private fun isMacOs(): Boolean =
    System.getProperty("os.name").contains("Mac", ignoreCase = true)
