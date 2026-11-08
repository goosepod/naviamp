package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.naviamp.domain.smartplaylist.*
import app.naviamp.domain.settings.ConnectionFormMusicFolder
import kotlinx.coroutines.delay
import java.awt.Window as AwtWindow
import java.io.File
import org.jetbrains.skia.Image

/** Visible native scene-layer probe. Uses synthetic data and programmatic shared scroll state. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun main() {
    configureNaviampDesktopRasterLayers()
    val verifyRenderTarget = System.getenv("NAVIAMP_SMART_PROBE_RENDER_TARGET") == "true"
    val verifyPixels = System.getenv("NAVIAMP_SMART_PROBE_VERIFY_PIXELS") == "true"
    application {
        val nativeState = rememberWindowState(width = 1184.dp, height = 794.dp)
        val scroll = rememberScrollState()
        var editorOpen by remember { mutableStateOf(false) }
        val output = File(System.getenv("NAVIAMP_SMART_PROBE_OUTPUT") ?: "build/reports/smart-playlist-native")
        Window(onCloseRequest = ::exitApplication, title = "Naviamp smart-playlist native probe", state = nativeState,
            alwaysOnTop = verifyPixels) {
            NaviampDesktopRasterHost(window, nativeState) {
                val colors = NaviampColors()
                androidx.compose.material3.MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme(
                    background = colors.background, surface = colors.controlSurface, primary = colors.accent,
                    onPrimary = colors.onAccent, onBackground = colors.primaryText, onSurface = colors.primaryText),
                    typography = rememberNaviampTypography()) {
                Box(Modifier.fillMaxSize().background(Color(0xff225533))) {
                    Text("Stable owner surface", color = Color.White)
                    if (editorOpen) SmartPlaylistBuilderDialog(NaviampColors(), SmartPlaylistDraft(name = "Native window mix",
                        conditions = listOf(SmartPlaylistConditionDraft(value = "Artist")), selectedLibraryIds = listOf("1")),
                        availableLibraries = listOf(ConnectionFormMusicFolder("1", "Music Library")),
                        scrollState = scroll,
                        onPreview = { SmartPlaylistPreview(indexedTrackCount = 100, matchingTrackCount = 100, resultTrackCount = 100) },
                        onDismissRequest = {}, onSave = {})
                }
                }
            }
            LaunchedEffect(window) {
                try {
                    output.mkdirs()
                    delay(500)
                    window.renderImmediately()
                    editorOpen = true
                    delay(800)
                    val initial = window.bounds
                    var initialPixels: java.awt.image.BufferedImage? = null
                    val nativeBounds = window.ownedWindows.filter { it.isShowing }.associateWith { it.bounds }
                    val nativeEvents = mutableListOf<String>()
                    val listener = java.awt.event.AWTEventListener { event ->
                        val native = event.source as? AwtWindow
                        if (native != null && (native === window || native in nativeBounds) &&
                            (event.id == java.awt.event.ComponentEvent.COMPONENT_MOVED ||
                                event.id == java.awt.event.ComponentEvent.COMPONENT_RESIZED)) {
                            nativeEvents += "${native.javaClass.simpleName}:${native.bounds}"
                        }
                    }
                    java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(listener, java.awt.AWTEvent.COMPONENT_EVENT_MASK)
                    val offsets = listOf(0, 150, 600, 850, Int.MAX_VALUE, 0)
                    for ((index, offset) in offsets.withIndex()) {
                        scroll.scrollTo(offset.coerceAtMost(scroll.maxValue))
                        delay(400)
                        window.ownedWindows.filter { it.isShowing }.forEachIndexed { layerIndex, native ->
                            if (verifyRenderTarget) findSkiaLayer(native)?.let { layer ->
                                val bytes = readNativeRenderTarget(layer)
                                File(output, "$index-$layerIndex-render-target.png").writeBytes(bytes)
                                val pixels = javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(bytes))
                                val nested = index == 3
                                if (index != 4) {
                                    val ys = if (nested) 150..210 else 390..450
                                    val edges = if (nested) listOf(-256f, -246f, 245f, 255f) else listOf(-256f, 255f)
                                    for (edge in edges) {
                                        val x = ((layer.width / 2f + edge) * layer.contentScale).toInt()
                                        val visible = ys.count { y ->
                                            val pixelY = (y * layer.contentScale).toInt()
                                            (x..x + 1).any { sample -> (pixels.getRGB(sample, pixelY) shr 16 and 255) > 45 }
                                        }
                                        check(visible >= 55) { "Clipped outline lost side $edge: $visible/61 pixels at scroll ${scroll.value}" }
                                        println("SMART_NATIVE GL outline=$visible/61 phase=$index edge=$edge")
                                    }
                                    if (index == 0) println("SMART_NATIVE GL renderer=${layer.renderInfo}")
                                }
                            }
                        }
                        check(window.bounds == initial) { "Owner moved: ${window.bounds} != $initial" }
                        nativeBounds.forEach { (native, bounds) -> check(native.bounds == bounds) {
                            "Popup moved during scroll: ${native.bounds} != $bounds"
                        } }
                        val windows = AwtWindow.getWindows().filter { it.isShowing && (it === window || it.owner === window) }
                        windows.forEachIndexed { layerIndex, native ->
                            val layer = findSkiaLayer(native)
                            println("SMART_NATIVE phase=$index scroll=${scroll.value} layer=$layerIndex bounds=${native.bounds} scale=${layer?.contentScale} background-alpha=${native.background.alpha}")
                            layer?.screenshot()?.use { bitmap ->
                                Image.makeFromBitmap(bitmap).use { image ->
                                    image.encodeToData()?.use { File(output, "$index-$layerIndex.png").writeBytes(it.bytes) }
                                }
                            }
                        }
                        if (verifyPixels) {
                        // Focus alone does not prove visibility on Windows. Pin only this synthetic
                        // fixture for capture, verify its colored marker, then restore native flags.
                        // Pin the owner only: toggling an owned child's topmost flag also changes
                        // its owner's native stacking order on Windows.
                        window.isAlwaysOnTop = false
                        window.isAlwaysOnTop = true
                        window.toFront()
                        windows.filter { it !== window }.forEach { it.toFront() }
                        delay(250)
                        val pixels = java.awt.Robot(window.graphicsConfiguration.device).createScreenCapture(
                            java.awt.Rectangle(window.locationOnScreen, window.size))
                        val marker = pixels.getRGB(window.insets.left + 40, window.insets.top + 100)
                        check(kotlin.math.abs((marker shr 16 and 255) - 14) <= 8 &&
                            kotlin.math.abs((marker shr 8 and 255) - 34) <= 8 &&
                            kotlin.math.abs((marker and 255) - 20) <= 8) {
                            "Synthetic fixture is occluded; visible pixels cannot be accepted: ${marker.toUInt().toString(16)}"
                        }
                        javax.imageio.ImageIO.write(pixels, "png", File(output, "$index-visible.png"))
                        val center = pixels.getSubimage(pixels.width / 2 - 250, 100, 500, 580)
                        var readable = 0
                        for (y in 0 until center.height) for (x in 0 until center.width) {
                            val pixel = center.getRGB(x, y)
                            if ((pixel shr 16 and 255) > 180 && (pixel shr 8 and 255) > 180 && (pixel and 255) > 180) readable++
                        }
                        check(readable > 500) { "Dialog text disappeared at phase $index: $readable bright pixels" }
                        if (index == 0) initialPixels = pixels
                        if (index == 2 || index == offsets.lastIndex) {
                            val first = checkNotNull(initialPixels)
                            var changed = 0
                            for (y in 100 until 680) for (x in pixels.width / 2 - 250 until pixels.width / 2 + 250) {
                                val before = first.getRGB(x, y)
                                val after = pixels.getRGB(x, y)
                                if (listOf(0, 8, 16).any { shift -> kotlin.math.abs((before shr shift and 255) - (after shr shift and 255)) > 10 }) changed++
                            }
                            if (index == 2) {
                                check(changed > 1_000) { "Scrolling did not change visible content: $changed pixels" }
                                println("SMART_NATIVE scrolled-pixel-difference=$changed")
                            } else {
                                check(changed < 3_000) { "Returning to the top changed field drawing: $changed pixels" }
                                println("SMART_NATIVE restored-pixel-difference=$changed")
                            }
                        }
                        }
                    }
                    java.awt.Toolkit.getDefaultToolkit().removeAWTEventListener(listener)
                    check(nativeEvents.isEmpty()) { "Transient native window movement: $nativeEvents" }
                    fun checkOwnerClient(phase: String) {
                        check(window.isShowing && window.graphicsConfiguration.bounds.intersects(window.bounds)) {
                            "Owner is not visibly restored after $phase"
                        }
                        val insets = window.insets
                        val expected = java.awt.Rectangle(window.x + insets.left, window.y + insets.top,
                            window.width - insets.left - insets.right, window.height - insets.top - insets.bottom)
                        val dialogs = window.ownedWindows.filter { it.isShowing }
                        check(dialogs.isNotEmpty()) { "Owned dialog disappeared after $phase" }
                        dialogs.forEach { check(it.bounds == expected) { "Wrong viewport after $phase: ${it.bounds} != $expected" } }
                        println("SMART_NATIVE $phase owner-client=$expected")
                    }
                    nativeState.size = androidx.compose.ui.unit.DpSize(1000.dp, 740.dp)
                    delay(500)
                    checkOwnerClient("resize")
                    nativeState.position = androidx.compose.ui.window.WindowPosition.Absolute(120.dp, 80.dp)
                    delay(350)
                    checkOwnerClient("move")
                    nativeState.isMinimized = true
                    delay(350)
                    nativeState.isMinimized = false
                    delay(700)
                    checkOwnerClient("restore")
                    println("SMART_NATIVE geometry passed")
                    println("SMART_NATIVE visible-pixel-verification=" + if (verifyPixels) "passed" else "not requested; requires an unobscured interactive desktop")
                    exitApplication()
                } catch (failure: Throwable) {
                    failure.printStackTrace()
                    kotlin.system.exitProcess(1)
                }
            }
        }
    }
}

/** Test-only readback of this synthetic window's GL target, after rendering and before buffer swap. */
private fun readNativeRenderTarget(layer: org.jetbrains.skiko.SkiaLayer): ByteArray {
    val redrawer = layer.javaClass.getMethod("getRedrawer\$skiko").invoke(layer)
    check(redrawer.javaClass.simpleName == "WindowsOpenGLRedrawer") { "Expected Windows OpenGL renderer" }
    for (name in listOf("makeCurrent", "draw")) {
        redrawer.javaClass.getDeclaredMethod(name).apply { isAccessible = true }.invoke(redrawer)
    }
    val handler = redrawer.javaClass.getDeclaredField("contextHandler").apply { isAccessible = true }.get(redrawer)
    val surface = Class.forName("org.jetbrains.skiko.context.ContextHandler").getDeclaredField("surface")
        .apply { isAccessible = true }.get(handler) as org.jetbrains.skia.Surface
    return org.jetbrains.skia.Bitmap().use { bitmap ->
        check(bitmap.allocPixels(org.jetbrains.skia.ImageInfo.makeN32Premul(surface.width, surface.height)))
        check(surface.canvas.readPixels(bitmap, 0, 0)) { "GL pixel readback failed" }
        Image.makeFromBitmap(bitmap).use { checkNotNull(it.encodeToData()).use { data -> data.bytes } }
    }
}
