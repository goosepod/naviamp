package app.naviamp.desktop.platform

import app.naviamp.desktop.playback.bass.BassPlatform
import app.naviamp.desktop.playback.bass.DesktopBassLibraryResolver
import java.awt.Window
import java.io.File
import javax.swing.SwingUtilities

/** Desktop OS appearance facts applied before AWT creates the application and per-window chrome. */
fun configureDesktopHostAppearance() {
    System.setProperty("compose.application.name", "Naviamp")
    System.setProperty("apple.awt.application.name", "Naviamp")
    System.setProperty("sun.awt.application.name", "Naviamp")
    if (isMacOs()) System.setProperty("apple.awt.application.appearance", "system")
}

fun configureDesktopWindowAppearance(window: Window, isDark: Boolean) {
    configureMacOsWindowAppearance(window, isDark)
    configureWindowsWindowAppearance(window, isDark)
}

private fun configureMacOsWindowAppearance(window: Window, isDark: Boolean) {
    if (!isMacOs()) return
    runCatching {
        SwingUtilities.getRootPane(window)?.putClientProperty(
            "apple.awt.windowAppearance",
            if (isDark) "NSAppearanceNameDarkAqua" else "NSAppearanceNameAqua",
        )
    }
}

private fun configureWindowsWindowAppearance(window: Window, isDark: Boolean) {
    if (!isWindows()) return
    runCatching { WindowsTitleBarJni.configure(window, isDark) }
}

private object WindowsTitleBarJni {
    private val platform = BassPlatform.current()
    private val nativeLibraryLoaded: Boolean by lazy(::loadNativeLibrary)

    fun configure(window: Window, isDark: Boolean): Boolean {
        if (!nativeLibraryLoaded) return false
        return nativeConfigureWindowsTitleBar(window, isDark)
    }

    private fun loadNativeLibrary(): Boolean =
        runCatching {
            val directory = DesktopBassLibraryResolver(platform = platform)
                .resolveWithLibraries("bass", "bassmix", "naviamp_bass")
                ?: return false
            System.load(File(directory, platform.libraryName("bass")).absolutePath)
            System.load(File(directory, platform.libraryName("bassmix")).absolutePath)
            System.load(File(directory, platform.libraryName("naviamp_bass")).absolutePath)
            true
        }.getOrDefault(false)
}

private fun isMacOs(): Boolean =
    System.getProperty("os.name").contains("Mac", ignoreCase = true)

private fun isWindows(): Boolean =
    System.getProperty("os.name").contains("Windows", ignoreCase = true)

private external fun nativeConfigureWindowsTitleBar(window: Window, isDark: Boolean): Boolean
