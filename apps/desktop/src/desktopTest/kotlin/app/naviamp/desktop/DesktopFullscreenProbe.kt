package app.naviamp.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import app.naviamp.ui.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

/** Opt-in visible native-window probe; validates the common controller against real decoration changes. */
fun main() = application {
    val startPlacement = if (System.getenv("NAVIAMP_PROBE_MAXIMIZED") == "true") WindowPlacement.Maximized else WindowPlacement.Floating
    val nativeState = rememberWindowState(placement = startPlacement, width = 1000.dp, height = 740.dp)
    val effect = remember { DesktopWindowEffect(nativeState) }
    val controller = remember { NaviampWindowController(effect, effect.snapshot()) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(Unit) { snapshotFlow(effect::snapshot).collect(controller::observe) }
    Window(onCloseRequest = ::exitApplication, title = "Naviamp fullscreen geometry probe", state = nativeState) {
        DisposableEffect(window) {
            val observer = effect.attachNativeFullscreenObserver(window, controller::observe)
            onDispose { observer.close() }
        }
        val shortcutPlatform = if (System.getProperty("os.name").startsWith("Mac"))
            app.naviamp.domain.settings.DesktopShortcutPlatform.MacOS
        else if (System.getProperty("os.name").startsWith("Windows"))
            app.naviamp.domain.settings.DesktopShortcutPlatform.Windows
        else app.naviamp.domain.settings.DesktopShortcutPlatform.Linux
        NaviampWindowEnvironment(controller, shortcutPlatform) {
            Column(Modifier.fillMaxSize().background(Color.Black).onGloballyPositioned { rootSize = it.size }) {
                Text("Native fullscreen layout fixture", color = Color.White)
                Spacer(Modifier.weight(1f))
                SharedBottomNavigationBar(NaviampColors(), SharedRoute.Home, onRouteSelected = {})
                Box(Modifier.fillMaxWidth().height(8.dp).background(Color.White))
            }
        }
        LaunchedEffect(window) {
            try {
                delay(1_000)
                val initial = effect.snapshot()
                val baselineBounds = window.bounds
                repeat(3) { cycle ->
                    check(controller.toggle())
                    withTimeout(10_000) { while (window.placement != WindowPlacement.Fullscreen) delay(50) }
                    delay(1_000)
                    check(controller.state.placement == NaviampWindowPlacement.Fullscreen)
                    checkLayout(window, rootSize, "fullscreen-$cycle")
                    nativeState.isMinimized = true
                    withTimeout(10_000) { while (!window.isMinimized) delay(50) }
                    delay(1_000)
                    check(controller.state.placement == NaviampWindowPlacement.Fullscreen)
                    nativeState.isMinimized = false
                    window.toFront()
                    withTimeout(10_000) {
                        while (window.isMinimized || window.x < -30_000 || window.width < 500) {
                            println("FULLSCREEN_RESTORE_PENDING native=${window.isMinimized} state=${nativeState.isMinimized} bounds=${window.bounds}")
                            delay(250)
                        }
                    }
                    delay(1_000)
                    check(window.placement == WindowPlacement.Fullscreen)
                    checkLayout(window, rootSize, "fullscreen-unminimized-$cycle")
                    check(controller.toggle())
                    withTimeout(10_000) { while (window.placement != startPlacement) delay(50) }
                    delay(1_000)
                    check(controller.state.placement == initial.placement)
                    checkLayout(window, rootSize, "restored-$cycle")
                    check(window.bounds == baselineBounds) { "Window geometry drifted: ${window.bounds} != $baselineBounds" }
                    check(effect.snapshot() == initial) { "Shared restoration snapshot drifted" }
                }
                println("FULLSCREEN_GEOMETRY_PROBE passed")
                exitApplication()
            } catch (failure: Throwable) {
                failure.printStackTrace()
                kotlin.system.exitProcess(1)
            }
        }
    }
}

private fun checkLayout(window: androidx.compose.ui.awt.ComposeWindow, root: IntSize, phase: String) {
    val insets = window.insets
    val clientWidth = window.width - insets.left - insets.right
    val clientHeight = window.height - insets.top - insets.bottom
    val scale = window.graphicsConfiguration.defaultTransform
    println("FULLSCREEN_GEOMETRY $phase window=${window.bounds} insets=$insets client=$clientWidth,$clientHeight compose=$root scale=$scale")
    check(root.width == (clientWidth * scale.scaleX).toInt() && root.height == (clientHeight * scale.scaleY).toInt()) {
        "Compose content bounds do not fit the decorated native window"
    }
    val capture = java.awt.Robot(window.graphicsConfiguration.device).createScreenCapture(java.awt.Rectangle(window.locationOnScreen, window.size))
    val pixel = capture.getRGB(window.width / 2, window.height - insets.bottom - 4) and 0xffffff
    check((pixel shr 16 and 255) > 240 && (pixel and 255) > 240 && (pixel shr 8 and 255) > 240) { "Bottom controls are clipped: footer marker=$pixel" }
}
