package app.naviamp.desktop

import app.naviamp.presentation.NaviampCoreExternalUriPort
import java.awt.Desktop
import java.net.URI

/** Opens external product links through the operating system's registered browser. */
class DesktopExternalUriPort(
    private val browse: (URI) -> Unit = ::browseDesktopUri,
) : NaviampCoreExternalUriPort {
    override fun open(uri: String) {
        browse(URI.create(uri))
    }
}

private fun browseDesktopUri(uri: URI) {
    check(Desktop.isDesktopSupported()) { "Desktop URI browsing is unavailable." }
    val desktop = Desktop.getDesktop()
    check(desktop.isSupported(Desktop.Action.BROWSE)) { "Desktop URI browsing is unavailable." }
    desktop.browse(uri)
}
