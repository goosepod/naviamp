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
import app.naviamp.desktop.platform.configureDesktopWindowIcon
import app.naviamp.ui.NaviampWindowController
import app.naviamp.ui.NaviampWindowSnapshot
import app.naviamp.ui.naviampAppIconPainter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

fun main() {
    app.naviamp.ui.configureNaviampDesktopRasterLayers()
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
        val windowEffect = remember(windowState) { DesktopWindowEffect(windowState) }
        val windowController = remember(windowEffect) { NaviampWindowController(windowEffect, windowEffect.snapshot()) }
        LaunchedEffect(windowEffect, windowController) {
            snapshotFlow(windowEffect::snapshot).collect(windowController::observe)
        }
        DisposableEffect(composition) {
            onDispose {
                windowGeometryStore.save(windowController.windowedSnapshot.geometry())
                composition.close()
            }
        }
        LaunchedEffect(windowController) {
            snapshotFlow { windowController.windowedSnapshot.geometry() }
                .distinctUntilChanged()
                .collectLatest { geometry ->
                    delay(250L)
                    windowGeometryStore.save(geometry)
                }
        }
        Window(
            state = windowState,
            onCloseRequest = ::exitApplication,
            title = "Naviamp",
            icon = naviampAppIconPainter(),
        ) {
            DisposableEffect(window, windowEffect, windowController) {
                val observer = windowEffect.attachNativeFullscreenObserver(window, windowController::observe)
                onDispose { observer.close() }
            }
            val darkTitleBar = isSystemInDarkTheme()
            LaunchedEffect(window, darkTitleBar) {
                configureDesktopWindowIcon(window)
                configureDesktopWindowAppearance(window, darkTitleBar)
            }
            window.minimumSize = java.awt.Dimension(
                MinDesktopWindowWidthDp.toInt(),
                MinDesktopWindowHeightDp.toInt(),
            )
            app.naviamp.ui.NaviampDesktopRasterHost(window, windowState) {
                DesktopNaviampCoreHost(composition.environment, window, windowController = windowController)
            }
        }
    }
}

private fun DesktopWindowGeometry.windowPosition(): WindowPosition =
    xDp?.let { x -> yDp?.let { y -> WindowPosition.Absolute(x.dp, y.dp) } } ?: WindowPosition.PlatformDefault

private fun NaviampWindowSnapshot.geometry(): DesktopWindowGeometry = DesktopWindowGeometry(
    widthDp = width,
    heightDp = height,
    xDp = x,
    yDp = y,
)
