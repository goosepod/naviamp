package app.naviamp.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    val scope = rememberCoroutineScope()
    val composition = remember { DesktopV2Composition.create(scope) }
    DisposableEffect(composition) {
        onDispose(composition::close)
    }
    Window(
        onCloseRequest = ::exitApplication,
        title = "Naviamp 2.0.0-alpha",
    ) {
        window.minimumSize = java.awt.Dimension(360, 640)
        DesktopNaviampCoreHost(composition.environment)
    }
}
