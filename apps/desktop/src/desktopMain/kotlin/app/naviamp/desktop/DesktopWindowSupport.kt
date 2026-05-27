package app.naviamp.desktop

import androidx.compose.ui.window.WindowState
import app.naviamp.desktop.settings.WindowSettings
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import java.awt.Taskbar
import java.awt.Window
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
        val hwnd = Native.getComponentPointer(window)
        val value = IntByReference(if (isDark) 1 else 0)
        WindowsDwmApi.instance.DwmSetWindowAttribute(
            hwnd,
            DwmWindowAttributeUseImmersiveDarkMode,
            value.pointer,
            Int.SIZE_BYTES,
        )
        WindowsDwmApi.instance.DwmSetWindowAttribute(
            hwnd,
            DwmWindowAttributeUseImmersiveDarkModeBefore20H1,
            value.pointer,
            Int.SIZE_BYTES,
        )
    }
}

private interface WindowsDwmApi : Library {
    fun DwmSetWindowAttribute(
        hwnd: Pointer,
        dwAttribute: Int,
        pvAttribute: Pointer,
        cbAttribute: Int,
    ): Int

    companion object {
        val instance: WindowsDwmApi by lazy {
            Native.load("dwmapi", WindowsDwmApi::class.java)
        }
    }
}

private const val DwmWindowAttributeUseImmersiveDarkModeBefore20H1 = 19
private const val DwmWindowAttributeUseImmersiveDarkMode = 20
