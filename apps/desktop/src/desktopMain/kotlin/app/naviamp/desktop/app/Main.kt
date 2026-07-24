package app.naviamp.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.naviamp.desktop.platform.DesktopWindowGeometry
import app.naviamp.desktop.platform.DesktopWindowGeometryStore
import app.naviamp.desktop.platform.MinDesktopWindowHeightDp
import app.naviamp.desktop.platform.MinDesktopWindowWidthDp
import app.naviamp.desktop.platform.availableDesktopScreenBounds
import app.naviamp.desktop.platform.configureDesktopHostAppearance
import app.naviamp.desktop.platform.configureDesktopApplicationIcon
import app.naviamp.desktop.platform.configureDesktopWindowAppearance
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

fun main() {
    configureDesktopHostAppearance()
    configureDesktopApplicationIcon()
    application {
        val scope = rememberCoroutineScope()
        val composition = remember { DesktopComposition.create(scope) }
        val windowGeometryStore = remember { DesktopWindowGeometryStore() }
        val initialWindowGeometry = remember {
            windowGeometryStore.load().normalized(availableDesktopScreenBounds())
        }
        val windowState = rememberWindowState(
            size = DpSize(initialWindowGeometry.widthDp.dp, initialWindowGeometry.heightDp.dp),
            position = initialWindowGeometry.windowPosition(),
        )
        DisposableEffect(composition) {
            onDispose {
                windowGeometryStore.save(windowState.geometry())
                composition.close()
            }
        }
        LaunchedEffect(windowState) {
            snapshotFlow(windowState::geometry)
                .distinctUntilChanged()
                .collectLatest { geometry ->
                    delay(250L)
                    windowGeometryStore.save(geometry)
                }
        }
        Window(
            state = windowState,
            onCloseRequest = ::exitApplication,
            title = "Naviamp 2.0.0-alpha",
        ) {
            val darkTitleBar = isSystemInDarkTheme()
            LaunchedEffect(window, darkTitleBar) {
                configureDesktopWindowAppearance(window, darkTitleBar)
            }
            window.minimumSize = java.awt.Dimension(
                MinDesktopWindowWidthDp.toInt(),
                MinDesktopWindowHeightDp.toInt(),
            )
            DesktopNaviampCoreHost(composition.environment)
        }
    }
}

private fun DesktopWindowGeometry.windowPosition(): WindowPosition =
    xDp?.let { x -> yDp?.let { y -> WindowPosition.Absolute(x.dp, y.dp) } } ?: WindowPosition.PlatformDefault

private fun WindowState.geometry(): DesktopWindowGeometry {
    val absolutePosition = position as? WindowPosition.Absolute
    return DesktopWindowGeometry(
        widthDp = size.width.value.coerceAtLeast(MinDesktopWindowWidthDp),
        heightDp = size.height.value.coerceAtLeast(MinDesktopWindowHeightDp),
        xDp = absolutePosition?.x?.value,
        yDp = absolutePosition?.y?.value,
    )
}
