package app.naviamp.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import app.naviamp.desktop.platform.configureDesktopHostAppearance
import app.naviamp.desktop.platform.configureDesktopApplicationIcon
import app.naviamp.desktop.platform.configureDesktopWindowAppearance

fun main() {
    configureDesktopHostAppearance()
    configureDesktopApplicationIcon()
    application {
        val scope = rememberCoroutineScope()
        val composition = remember { DesktopV2Composition.create(scope) }
        DisposableEffect(composition) {
            onDispose(composition::close)
        }
        Window(
            onCloseRequest = ::exitApplication,
            title = "Naviamp 2.0.0-alpha",
        ) {
            val darkTitleBar = isSystemInDarkTheme()
            LaunchedEffect(window, darkTitleBar) {
                configureDesktopWindowAppearance(window, darkTitleBar)
            }
            window.minimumSize = java.awt.Dimension(360, 640)
            DesktopNaviampCoreHost(composition.environment)
        }
    }
}
