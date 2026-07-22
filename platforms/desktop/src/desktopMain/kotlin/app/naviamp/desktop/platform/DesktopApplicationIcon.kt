package app.naviamp.desktop.platform

import java.awt.Taskbar
import javax.imageio.ImageIO

/** Applies Naviamp's packaged icon through the JVM Desktop/Dock API. */
fun configureDesktopApplicationIcon() {
    runCatching {
        if (!Taskbar.isTaskbarSupported()) return
        val taskbar = Taskbar.getTaskbar()
        if (!taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) return
        val iconUrl = Thread.currentThread().contextClassLoader.getResource("icons/naviamp.png") ?: return
        taskbar.iconImage = ImageIO.read(iconUrl)
    }
}
