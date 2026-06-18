package app.naviamp.desktop

import app.naviamp.ui.NaviampAboutUi
import java.util.Properties

internal fun loadDesktopAboutUi(): NaviampAboutUi {
    val properties = Properties()
    Thread.currentThread().contextClassLoader
        .getResourceAsStream("naviamp-build.properties")
        ?.use(properties::load)

    return NaviampAboutUi(
        version = properties.getProperty("version")?.takeIf { it.isNotBlank() } ?: "Unknown",
        buildNumber = properties.getProperty("buildNumber")?.takeIf { it.isNotBlank() } ?: "Unknown",
    )
}
