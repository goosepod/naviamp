package app.naviamp.desktop.platform

import java.awt.Taskbar
import java.awt.Window
import javax.imageio.ImageIO

/** Applies Naviamp's packaged icon through the JVM Desktop/Dock API. */
fun configureDesktopApplicationIcon() {
    runCatching {
        if (!Taskbar.isTaskbarSupported()) return
        val taskbar = Taskbar.getTaskbar()
        if (!taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) return
        taskbar.iconImage = desktopApplicationIcon() ?: return
    }
}

/** Applies the packaged image to an individual AWT window, including Windows title-bar chrome. */
fun configureDesktopWindowIcon(window: Window) {
    desktopApplicationIcon()?.let(window::setIconImage)
}

internal fun desktopApplicationIcon() = runCatching {
    val iconUrl = DesktopApplicationIconResource::class.java.classLoader
        .getResource("icons/naviamp.png")
        ?: return@runCatching null
    ImageIO.read(iconUrl)
}.getOrNull()

private object DesktopApplicationIconResource
