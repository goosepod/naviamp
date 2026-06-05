package app.naviamp.desktop

import androidx.compose.ui.window.WindowState
import app.naviamp.desktop.playback.bass.BassPlatform
import app.naviamp.desktop.playback.bass.DesktopBassLibraryResolver
import app.naviamp.desktop.settings.WindowSettings
import java.awt.Taskbar
import java.awt.Window
import java.io.File
import javax.imageio.ImageIO

fun configureDesktopApplicationName() {
    System.setProperty("compose.application.name", "Naviamp")
    System.setProperty("apple.awt.application.name", "Naviamp")
    System.setProperty("sun.awt.application.name", "Naviamp")
}

fun configureDesktopIcon() {
    runCatching {
        if (!Taskbar.isTaskbarSupported()) return
        val taskbar = Taskbar.getTaskbar()
        if (!taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) return
        val iconUrl = Thread.currentThread().contextClassLoader.getResource("icons/naviamp.png") ?: return
        taskbar.iconImage = ImageIO.read(iconUrl)
    }
}

fun configureDesktopAppearance() {
    if (System.getProperty("os.name").contains("Mac", ignoreCase = true)) {
        System.setProperty("apple.awt.application.appearance", "system")
    }
}

fun configureNativeTitleBar(window: Window, isDark: Boolean) {
    configureMacTitleBar(window, isDark)
    configureWindowsTitleBar(window, isDark)
}

fun WindowState.toWindowSettings(): WindowSettings =
    WindowSettings(
        widthDp = size.width.value.coerceAtLeast(320f),
        heightDp = size.height.value.coerceAtLeast(420f),
    )

private fun configureMacTitleBar(window: Window, isDark: Boolean) {
    if (!System.getProperty("os.name").contains("Mac", ignoreCase = true)) return
    runCatching {
        val appearance = if (isDark) "NSAppearanceNameDarkAqua" else "NSAppearanceNameAqua"
        javax.swing.SwingUtilities.getRootPane(window)
            ?.putClientProperty("apple.awt.windowAppearance", appearance)
    }
}

private fun configureWindowsTitleBar(window: Window, isDark: Boolean) {
    if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) return
    runCatching {
        WindowsTitleBarJni.configure(window, isDark)
    }
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
            val directory = DesktopBassLibraryResolver(platform = platform).resolve() ?: return false
            System.load(File(directory, platform.libraryName("bass")).absolutePath)
            System.load(File(directory, platform.libraryName("bassmix")).absolutePath)
            System.load(File(directory, platform.libraryName("naviamp_bass")).absolutePath)
            true
        }.getOrDefault(false)
}

private external fun nativeConfigureWindowsTitleBar(window: Window, isDark: Boolean): Boolean
