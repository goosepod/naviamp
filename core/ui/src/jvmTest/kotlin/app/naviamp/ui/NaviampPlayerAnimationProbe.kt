package app.naviamp.ui

import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.awt.SwingPanel
import org.jetbrains.skiko.SkiaLayer
import org.jetbrains.skiko.SkikoRenderDelegate
import java.awt.Component
import java.awt.Container
import java.util.concurrent.atomic.AtomicLong
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.lang.management.ManagementFactory
import java.awt.event.InputEvent
import kotlinx.coroutines.delay

/** Synthetic real-window rendering probe. No provider, audio engine, or user data is loaded. */
fun main() {
    val integrated = System.getenv("NAVIAMP_PROBE_INTEGRATED") == "true"
    val expectFallback = System.getenv("NAVIAMP_PROBE_EXPECT_FALLBACK") == "true"
    val verifyPixels = System.getenv("NAVIAMP_PROBE_VERIFY") == "true"
    val fullscreenProbe = System.getenv("NAVIAMP_PROBE_FULLSCREEN") == "true"
    val lifecycleProbe = System.getenv("NAVIAMP_PROBE_LIFECYCLE") == "true"
    val popupProbe = System.getenv("NAVIAMP_PROBE_POPUPS") == "true"
    val menuProbe = System.getenv("NAVIAMP_PROBE_MENUS") == "true"
    val dialogProbe = System.getenv("NAVIAMP_PROBE_DIALOGS") == "true"
    val tooltipProbe = System.getenv("NAVIAMP_PROBE_TOOLTIPS") == "true"
    val hoverProbe = System.getenv("NAVIAMP_PROBE_HOVER") == "true"
    if (integrated) configureNaviampDesktopRasterLayers()
    application {
    val compositor = remember { System.getenv("NAVIAMP_PROBE_COMPOSITOR") == "true" }
    val raster = remember { System.getenv("NAVIAMP_PROBE_RASTER") == "true" }
    val raw = remember { System.getenv("NAVIAMP_PROBE_RAW") == "true" }
    val isolated = remember { System.getenv("NAVIAMP_PROBE_ISOLATED") == "true" }
    var phase by remember { mutableStateOf("static") }
    var waveformInputEvents by remember { mutableIntStateOf(0) }
    var waveformCenter by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val transparentBounds = remember { mutableStateListOf(Rect.Zero, Rect.Zero, Rect.Zero, Rect.Zero) }
    var alphaBounds by remember { mutableStateOf(Rect.Zero) }
    var popupVisible by remember { mutableStateOf(false) }
    val rowCount = remember { System.getenv("NAVIAMP_PROBE_ROWS")?.toIntOrNull()?.coerceIn(0, 1000) ?: 150 }
    val cpu = remember { ManagementFactory.getOperatingSystemMXBean() as com.sun.management.OperatingSystemMXBean }
    val threadCpu = remember { ManagementFactory.getThreadMXBean() }
    val compilation = remember { ManagementFactory.getCompilationMXBean() }
    val compositors = remember {
        ProcessHandle.allProcesses().use { processes -> processes.filter {
            java.io.File(it.info().command().orElse("")).name in setOf("xfwm4", "xcompmgr", "Xorg", "Xvfb")
        }.toList() }
    }
    val probeWindowState = rememberWindowState(width = 1000.dp, height = 740.dp)
    Window(
        onCloseRequest = ::exitApplication,
        title = "Naviamp animation CPU probe",
        state = probeWindowState,
        alwaysOnTop = verifyPixels,
    ) {
        val marquee = phase == "marquee" || phase.endsWith("combined")
        val smooth = phase == "waveform" || phase.endsWith("combined")
        val playbackValue = remember { mutableFloatStateOf(.2f) }
        LaunchedEffect(smooth) {
            if (smooth) while (true) {
                delay(1_000)
                playbackValue.floatValue += 1f / 300f
            }
        }
        val content: @Composable () -> Unit = {
        Row(Modifier.fillMaxSize().background(Color(0xff24242b)).padding(24.dp)) {
            if (compositor) ProbeCompositorSurface(marquee, smooth, Modifier.width(280.dp).fillMaxHeight())
            else if (raster) ProbeRasterAnimationSurface(marquee, smooth, Modifier.width(280.dp).fillMaxHeight())
            else if (raw) ProbeRawAnimationSurface(marquee, smooth, Modifier.width(280.dp).fillMaxHeight())
            else CompositionLocalProvider(LocalNaviampAnimationSurface provides if (isolated) ProbeNativeAnimationSurface else NaviampInlineAnimationSurface) {
            NaviampAnimationRegion(Modifier.width(280.dp).fillMaxHeight()) {
            Column(Modifier.fillMaxSize().background(Color(0xff24242b)), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(280.dp).background(Color(0xff775533)))
                repeat(3) { index ->
                    BouncingTitleText(
                        "Long scrolling player metadata $index with enough text to overflow its viewport",
                        color = Color.White, fontSize = 16, marqueeEnabled = marquee,
                        modifier = Modifier.fillMaxWidth().then(if (verifyPixels) probePattern() else Modifier)
                            .onGloballyPositioned { transparentBounds[index] = Rect(it.positionInWindow(), Size(it.size.width.toFloat(), it.size.height.toFloat())) },
                    )
                }
                WaveformScrubber(
                    amplitudes = List(512) { ((it * 17) % 101) / 100f },
                    value = 0.2f, drawValue = { playbackValue.floatValue }, enabled = true, smoothProgress = smooth,
                    durationSeconds = 300.0, colors = NaviampColors(),
                    onValueChange = { waveformInputEvents++ }, onValueChangeFinished = {},
                    modifier = Modifier.fillMaxWidth().height(32.dp).then(if (verifyPixels) probePattern() else Modifier).onGloballyPositioned { coordinates ->
                        transparentBounds[3] = Rect(coordinates.positionInWindow(), Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()))
                        val position = coordinates.positionInWindow()
                        waveformCenter = position + androidx.compose.ui.geometry.Offset(
                            coordinates.size.width / 2f, coordinates.size.height / 2f,
                        )
                    },
                )
                Text(phase, color = Color.White)
                if (verifyPixels) ProbeAlphaFixture(Modifier.fillMaxWidth().height(32.dp)
                    .onGloballyPositioned {
                        alphaBounds = Rect(it.positionInWindow(), Size(it.size.width.toFloat(), it.size.height.toFloat()))
                    })
            }
            }
            }
            Column(Modifier.weight(1f).padding(start = 24.dp).verticalScroll(rememberScrollState())) {
                if (hoverProbe) NaviampTooltip("Tooltip probe", NaviampColors()) {
                    Text("Hover here for tooltip measurement", color = Color.White,
                        modifier = Modifier.fillMaxWidth().padding(12.dp))
                }
                repeat(rowCount) { index ->
                    Text("Library row $index — artist and album metadata", color = Color.White,
                        modifier = Modifier.fillMaxWidth().padding(12.dp).background(Color(0xff303039)))
                }
            }
        }
        if (popupVisible && dialogProbe) {
            NaviampPopupPresence()
            NaviampAlertDialog(onDismissRequest = { popupVisible = false },
                title = { Text("Probe dialog") }, text = { Text("Steady modal rendering") },
                confirmButton = { Text("Confirm") })
        } else if (popupVisible && menuProbe) Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.TopEnd) {
            Box(Modifier.size(48.dp)) {
                NaviampDropdownMenu(expanded = true, onDismissRequest = { popupVisible = false }) {
                    repeat(6) { index -> NaviampDropdownMenuItem("Probe action $index", onClick = {}) }
                }
            }
        } else if (popupVisible) androidx.compose.ui.window.Popup(alignment = androidx.compose.ui.Alignment.TopEnd) {
            NaviampPopupPresence()
            androidx.compose.material3.Surface(
                color = Color(0xff24242b).copy(alpha = 0.98f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(5.dp),
                shadowElevation = 5.dp,
            ) { Text("Tooltip probe", color = Color.White, modifier = Modifier.padding(8.dp, 5.dp)) }
        }
        }
        if (integrated) NaviampDesktopRasterHost(window, probeWindowState, content) else content()
        LaunchedEffect(Unit) {
            try {
            // Allow the native test window to be raised onto the measured desktop space.
            delay(if (verifyPixels) 15_000 else 1_000)
            if (verifyPixels) captureProbe(window, "warmup")
            val baselineSize = probeWindowState.size
            val baselinePosition = probeWindowState.position
            val counters = probeLayers(window).map { layer ->
                val count = AtomicLong()
                val delegate = requireNotNull(layer.renderDelegate)
                layer.renderDelegate = object : SkikoRenderDelegate {
                    override fun onRender(canvas: org.jetbrains.skia.Canvas, width: Int, height: Int, nanoTime: Long) {
                        count.incrementAndGet()
                        delegate.onRender(canvas, width, height, nanoTime)
                    }
                }
                "${layer.width}x${layer.height}:${layer.renderApi}" to count
            }
            println("ANIMATION_PROBE integrated=$integrated compositor=$compositor raster=$raster raw=$raw isolated=$isolated menu=$menuProbe dialog=$dialogProbe rows=$rowCount layers=${counters.map { it.first }} phase,cpu_percent,frames")
            val opened = AtomicLong()
            val closed = AtomicLong()
            val lifecycle = java.awt.event.AWTEventListener { event ->
                if (event.id == java.awt.event.WindowEvent.WINDOW_OPENED) opened.incrementAndGet()
                if (event.id == java.awt.event.WindowEvent.WINDOW_CLOSED) closed.incrementAndGet()
            }
            java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(lifecycle, java.awt.AWTEvent.WINDOW_EVENT_MASK)
            val defaultPhases = if (popupProbe && System.getenv("NAVIAMP_PROBE_POPUP_ONLY") == "true")
                listOf("popup-combined", "restored-combined")
            else if (popupProbe) listOf("static", "marquee", "waveform", "combined", "popup-combined", "restored-combined")
            else if (hoverProbe) listOf("hover")
            else if (tooltipProbe) listOf("static", "tooltip", "dismissed")
            else listOf("static", "marquee", "waveform", "combined")
            val phases = System.getenv("NAVIAMP_PROBE_PHASES")?.split(',') ?: (defaultPhases + if (lifecycleProbe) listOf("paused", "hidden-combined", "restored-combined", "resized-combined") else emptyList())
            val cycles = System.getenv("NAVIAMP_PROBE_CYCLES")?.toIntOrNull()?.coerceIn(1, 5) ?: 1
            for (mode in if (fullscreenProbe) listOf("windowed", "fullscreen", "restored") else listOf("windowed")) {
            probeWindowState.placement = if (mode == "fullscreen") androidx.compose.ui.window.WindowPlacement.Fullscreen
                else androidx.compose.ui.window.WindowPlacement.Floating
            delay(3_000)
            println("ANIMATION_WINDOW mode=$mode placement=${probeWindowState.placement} size=${window.size} scale=${window.graphicsConfiguration.defaultTransform} refresh=${window.graphicsConfiguration.device.displayMode.refreshRate}")
            for (cycle in 1..cycles) for (next in phases) {
                println("ANIMATION_CYCLE $cycle mode=$mode phase=$next")
                phase = next
                if (lifecycleProbe) {
                    if (next == phases.first()) {
                        probeWindowState.size = baselineSize
                        probeWindowState.position = baselinePosition
                    }
                    probeWindowState.isMinimized = next == "hidden-combined"
                    if (next == "resized-combined") {
                        probeWindowState.position = androidx.compose.ui.window.WindowPosition(60.dp, 40.dp)
                        probeWindowState.size = androidx.compose.ui.unit.DpSize(1040.dp, 760.dp)
                    }
                }
                val capturePixels = verifyPixels && next != "hidden-combined"
                if (popupProbe) popupVisible = next == "popup-combined"
                if (tooltipProbe) popupVisible = next == "tooltip"
                if (hoverProbe) kotlinx.coroutines.withTimeout(90_000) {
                    while (window.ownedWindows.none { it.isShowing }) delay(100)
                }
                if (capturePixels) {
                    val origin = window.locationOnScreen
                    java.awt.Robot(window.graphicsConfiguration.device).mouseMove(
                        origin.x + window.width - 10, origin.y + window.height - 10,
                    )
                }
                delay(5_000)
                println("ANIMATION_GEOMETRY cycle=$cycle phase=$next bounds=${window.bounds} scale=${window.graphicsConfiguration.defaultTransform} refresh=${window.graphicsConfiguration.device.displayMode.refreshRate}")
                if (lifecycleProbe) println("ANIMATION_LIFECYCLE $next state=${window.extendedState} minimized=${probeWindowState.isMinimized} owned=${window.ownedWindows.map { it.name to it.isShowing }}")
                val openedBefore = opened.get()
                val closedBefore = closed.get()
                val initialFrames = counters.map { it.second.get() }
                val positions = if (compositor) ProbeCompositor.positions(ProbeCompositor.handle).toList() else emptyList()
                val dimmed = dialogProbe && popupVisible
                val popupBounds = window.ownedWindows.filter { it.isShowing }.associateWith { it.bounds }
                val beforePixels = if (capturePixels) captureProbe(window, "$mode-cycle$cycle-$next-before", dimmed) else null
                if (menuProbe && popupVisible && beforePixels != null) verifyMenuPaint(beforePixels.pixels)
                val compositorCpu = compositors.associateWith { it.info().totalCpuDuration().orElse(null)?.toNanos() }
                val threadIds = threadCpu.allThreadIds
                val threadBefore = threadIds.associateWith { threadCpu.getThreadCpuTime(it) }
                val compilationBefore = compilation.totalCompilationTime
                val startCpu = cpu.processCpuTime
                val start = System.nanoTime()
                delay(if (hoverProbe) 30_000 else 10_000)
                val percent = (cpu.processCpuTime - startCpu).toDouble() / (System.nanoTime() - start) * 100.0
                println("ANIMATION_PROBE $mode/$next,$percent,${counters.mapIndexed { index, counter -> counter.second.get() - initialFrames[index] }}")
                val elapsedNanos = System.nanoTime() - start
                val busyThreads = threadIds.toList().mapNotNull { id ->
                    val before = threadBefore.getValue(id)
                    val after = threadCpu.getThreadCpuTime(id)
                    val name = threadCpu.getThreadInfo(id)?.threadName
                    if (before < 0 || after < 0 || name == null) null
                    else name to (after - before) * 100.0 / elapsedNanos
                }.sortedByDescending { it.second }.take(5)
                println("ANIMATION_CPU_DETAIL $next compilation_millis=${compilation.totalCompilationTime - compilationBefore} threads=$busyThreads")
                compositors.forEach { process ->
                    val before = compositorCpu[process]
                    val after = process.info().totalCpuDuration().orElse(null)?.toNanos()
                    if (before != null && after != null) println("ANIMATION_COMPOSITOR $next pid=${process.pid()} command=${process.info().command().orElse("")} cpu_percent=${(after - before).toDouble() / elapsedNanos * 100}")
                }
                if (next == "hidden-combined") {
                    check(window.extendedState and java.awt.Frame.ICONIFIED != 0) { "Probe did not minimize" }
                    check(window.ownedWindows.none { it.isShowing }) { "Raster windows remained visible while minimized" }
                    check(counters.mapIndexed { index, counter -> counter.second.get() - initialFrames[index] }.all { it == 0L }) { "Hidden parent redrew" }
                }
                println("ANIMATION_WINDOWS $next opened=${opened.get() - openedBefore} closed=${closed.get() - closedBefore}")
                if (hoverProbe) {
                    check(window.ownedWindows.any { it.isShowing }) { "Hover tooltip is not visible" }
                    check(opened.get() == openedBefore && closed.get() == closedBefore) { "Hover recreated native popup windows" }
                }
                if (capturePixels) {
                    check(popupBounds.all { (popup, bounds) -> popup.bounds == bounds }) { "Native popup geometry changed" }
                    val afterCapture = captureProbe(window, "$mode-cycle$cycle-$next-after", dimmed)
                    if (menuProbe && popupVisible) verifyMenuPaint(afterCapture.pixels)
                    val beforeCapture = requireNotNull(beforePixels)
                    verifyProbeAlpha(afterCapture.pixels, window, alphaBounds, dimmed)
                    transparentBounds.forEachIndexed { index, rect -> verifyProbeBackground(afterCapture.pixels, window, rect, index, dimmed) }
                    val afterPixels = afterCapture.pixels
                    val before = beforeCapture.pixels
                    var changed = 0
                    var textChanged = 0
                    var waveformChanged = 0
                    var textPixels = 0
                    for (y in 340 until minOf(580, before.height)) for (x in 24 until minOf(304, before.width)) {
                        if (beforeCapture.nearPointer(x, y) || afterCapture.nearPointer(x, y)) continue
                        val pixel = afterPixels.getRGB(x, y)
                        if (probePixelsDiffer(pixel, before.getRGB(x, y))) {
                            changed++
                            // Window decorations differ between real window managers and Xvfb.
                            // Keep a small overlap so both coordinate layouts cover the third label
                            // and waveform without making either motion assertion depend on insets.
                            if (y < 445) textChanged++
                            if (y in 415..500) waveformChanged++
                        }
                        val threshold = if (dimmed) 80 else 200
                        if ((pixel shr 16 and 255) > threshold && (pixel shr 8 and 255) > threshold && (pixel and 255) > threshold) textPixels++
                    }
                    var siblingChanges = 0
                    for (y in 60 until minOf(690, before.height)) for (x in 350 until minOf(850, before.width)) {
                        if (beforeCapture.nearPointer(x, y) || afterCapture.nearPointer(x, y)) continue
                        if (probePixelsDiffer(afterPixels.getRGB(x, y), before.getRGB(x, y))) siblingChanges++
                    }
                    println("ANIMATION_PIXELS $next changed=$changed text=$textChanged waveform=$waveformChanged visibleText=$textPixels sibling=$siblingChanges")
                    check(textPixels > 100) { "Cached text is blank" }
                    if (next == "marquee" || next.endsWith("combined")) check(textChanged > 10) { "Text did not move" }
                    if (next == "waveform" || next.endsWith("combined")) check(waveformChanged > 10) { "Waveform did not move" }
                    check(siblingChanges == 0) { "Unrelated content changed" }
                    if (expectFallback && (marquee || smooth)) check(counters.indices.any { counters[it].second.get() > initialFrames[it] }) { "Expected the shared fallback to animate" }
                    if (!expectFallback && (next != "popup-combined" || System.getProperty("compose.layers.type") == "WINDOW"))
                        check(counters.mapIndexed { index, counter -> counter.second.get() - initialFrames[index] }.all { it == 0L }) { "Static parent redrew" }
                }
                if (compositor) {
                    val after = ProbeCompositor.positions(ProbeCompositor.handle).toList()
                    println("COMPOSITOR_POSITIONS $next $positions -> $after")
                    check(after[0] > 0.0 && after[1] > 0.0) { "Native surface is empty" }
                    if (next == "marquee" || next.endsWith("combined")) check((2..4).all { kotlin.math.abs(after[it] - positions[it]) > 1.0 }) { "Marquee did not move" }
                    if (next == "waveform" || next.endsWith("combined")) check(after[5] > positions[5] + 1.0) { "Progress did not advance" }
                    if (next != "popup-combined") check(counters.mapIndexed { index, counter -> counter.second.get() - initialFrames[index] }.all { it == 0L }) { "Static parent redrew" }
                }
            }
            }
            java.awt.Toolkit.getDefaultToolkit().removeAWTEventListener(lifecycle)
            if (verifyPixels) {
                // Exercise real owned-window notifications while the compositor timelines run.
                repeat(12) { index ->
                    popupVisible = index % 2 == 0
                    delay(if (dialogProbe || menuProbe) 300 else 80)
                    val dimmed = dialogProbe && popupVisible
                    val pixels = captureProbe(window, "popup-$index", dimmed, transitioning = dialogProbe).pixels
                    var visibleText = 0
                    for (y in 340 until minOf(450, pixels.height)) for (x in 24 until minOf(304, pixels.width)) {
                        val pixel = pixels.getRGB(x, y)
                        val threshold = if (dimmed) 30 else 200
                        if ((pixel shr 16 and 255) > threshold && (pixel shr 8 and 255) > threshold && (pixel and 255) > threshold) visibleText++
                    }
                    check(visibleText > 100) { "Text disappeared during popup transition $index" }
                }
                println("ANIMATION_POPUPS 12 visible-text samples passed")
                val origin = window.locationOnScreen
                val robot = java.awt.Robot(window.graphicsConfiguration.device)
                val inputX = origin.x + window.insets.left + waveformCenter.x.toInt()
                val inputY = origin.y + window.insets.top + waveformCenter.y.toInt()
                robot.mouseMove(inputX, inputY)
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
                delay(300)
                check(waveformInputEvents > 0) { "Native raster surface intercepted shared waveform input" }
                println("ANIMATION_INPUT waveform events=$waveformInputEvents point=$inputX,$inputY insets=${window.insets}")
                if (System.getenv("NAVIAMP_PROBE_STACKING") == "true") verifyProbeStacking(window, alphaBounds, transparentBounds.first())
            }
            exitApplication()
            } catch (failure: Throwable) {
                failure.printStackTrace()
                kotlin.system.exitProcess(1)
            }
        }
    }
}

}

private data class ProbeCapture(val pixels: java.awt.image.BufferedImage, val pointer: java.awt.Point) {
    // Exclude the OS/automation pointer halo, which is composed outside our window surface.
    fun nearPointer(x: Int, y: Int) = kotlin.math.abs(pointer.x - x) < 100 && kotlin.math.abs(pointer.y - y) < 100
}

private fun captureProbe(window: java.awt.Window, name: String, dimmed: Boolean = false,
    transitioning: Boolean = false): ProbeCapture {
    val origin = window.locationOnScreen
    val pointer = java.awt.MouseInfo.getPointerInfo().location.apply { translate(-origin.x, -origin.y) }
    val image = java.awt.Robot(window.graphicsConfiguration.device)
        .createScreenCapture(java.awt.Rectangle(window.locationOnScreen, window.size))
    val directory = java.io.File("build/animation-probe").apply { mkdirs() }
    val marker = if (dimmed) 0x302214 else 0x775533
    // Native translucent-window color conversion shifts this dark marker by several channel
    // values on macOS. This tolerance applies only to the visibility marker, never motion checks.
    val pixel = image.getRGB(100, 100)
    val red = pixel shr 16 and 255
    val green = pixel shr 8 and 255
    val blue = pixel and 255
    val intensity = red / 119.0
    // Rapid modal reopen samples may legitimately catch the entrance's partially dimmed scrim.
    // Require the known brown tint and a nonblank intensity throughout that transition.
    val markerVisible = if (transitioning && dimmed) {
        intensity in .35..1.04 && kotlin.math.abs(green - 85 * intensity) <= 6 &&
            kotlin.math.abs(blue - 51 * intensity) <= 6
    } else !probePixelsDiffer(pixel, marker, if (dimmed) 6 else 2)
    check(markerVisible) {
        "Probe is obscured by another window"
    }
    javax.imageio.ImageIO.write(image, "png", java.io.File(directory, "$name.png"))
    return ProbeCapture(image, pointer)
}

// Display color conversion can dither an otherwise unchanged capture by one channel value.
// Ignore that noise while still detecting actual content movement or sibling repaint artifacts.
private fun probePixelsDiffer(first: Int, second: Int, tolerance: Int = 2): Boolean =
    listOf(0, 8, 16).any { shift -> kotlin.math.abs((first shr shift and 255) - (second shr shift and 255)) > tolerance }

private fun verifyMenuPaint(image: java.awt.image.BufferedImage) {
    var menuPixels = 0
    var labelPixels = 0
    for (y in 0 until image.height) for (x in 2 until image.width - 2) {
        if (!probePixelsDiffer(image.getRGB(x, y), 0x292c36, 3)) menuPixels++
        val pixel = image.getRGB(x, y)
        if ((pixel and 255) > 180 && (pixel shr 8 and 255) > 180 && (pixel shr 16 and 255) > 180 &&
            listOf(-2, 2).any { !probePixelsDiffer(image.getRGB(x + it, y), 0x292c36, 3) }) labelPixels++
    }
    check(menuPixels > 5_000 && labelPixels > 50) {
        "Owned menu is blank or not visible on the physical display ($menuPixels surface, $labelPixels label pixels)"
    }
    println("ANIMATION_MENU surface=$menuPixels labels=$labelPixels")
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
private object ProbeNativeAnimationSurface : NaviampAnimationSurface {
    @Composable
    override fun Content(modifier: Modifier, content: @Composable () -> Unit) {
        val current = rememberUpdatedState(content)
        val panel = remember { ComposePanel().apply { setContent { current.value() } } }
        SwingPanel(factory = { panel }, background = Color(0xff24242b), modifier = modifier)
    }
}

private fun probeLayers(component: Component): List<SkiaLayer> =
    buildList {
        if (component is SkiaLayer) add(component)
        if (component is Container) addAll(component.components.flatMap(::probeLayers))
    }


@Composable
private fun ProbeRawAnimationSurface(marquee: Boolean, smooth: Boolean, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val labels = remember(measurer) {
        List(3) { index -> measurer.measure(
            "Long scrolling player metadata $index with enough text to overflow its viewport",
            style = TextStyle(color = Color.White, fontSize = 16.sp), softWrap = false,
        ) }
    }
    val drawing = NaviampAnimationDrawing { scope, time ->
        with(scope) {
            drawRect(Color(0xff24242b))
            drawRect(Color(0xff775533), size = Size(size.width, 280.dp.toPx()))
            val offset = if (marquee) ((time / 1_000_000L) % 8000) / 24f else 0f
            labels.forEachIndexed { index, label ->
                drawText(label, topLeft = Offset(-offset, (296 + 34 * index).dp.toPx()))
            }
            val progress = if (smooth) 0.2f + ((time / 1_000_000L) % 10000) / 300_000f else 0.2f
            drawLine(Color.Gray, Offset(0f, 410.dp.toPx()), Offset(size.width, 410.dp.toPx()), 6f)
            drawLine(Color.White, Offset(0f, 410.dp.toPx()), Offset(size.width * progress, 410.dp.toPx()), 6f)
        }
        marquee || smooth
    }
    val current = rememberUpdatedState(drawing)
    val layer = remember {
        SkiaLayer().apply {
            val scope = CanvasDrawScope()
            renderDelegate = object : SkikoRenderDelegate {
                override fun onRender(canvas: org.jetbrains.skia.Canvas, width: Int, height: Int, nanoTime: Long) {
                    var again = false
                    scope.draw(Density(contentScale), LayoutDirection.Ltr, canvas.asComposeCanvas(), Size(width.toFloat(), height.toFloat())) {
                        again = current.value.drawFrame(this, nanoTime)
                    }
                    if (again) needRedraw()
                }
            }
        }
    }
    SideEffect { layer.needRedraw() }
    SwingPanel(factory = { layer }, background = Color(0xff24242b), modifier = modifier, update = { it.needRedraw() })
}

/** Diagnostic: cache Compose-shaped text once, then ask AWT to blit only the moving region. */
@Composable
private fun ProbeRasterAnimationSurface(marquee: Boolean, smooth: Boolean, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val labels = remember(measurer, density) {
        List(3) { index ->
            val text = measurer.measure("Long scrolling player metadata $index with enough text to overflow its viewport",
                style = TextStyle(color = Color.White, fontSize = 16.sp), softWrap = false)
            org.jetbrains.skia.Surface.makeRasterN32Premul(text.size.width, text.size.height).use { surface ->
                CanvasDrawScope().draw(density, LayoutDirection.Ltr, surface.canvas.asComposeCanvas(),
                    Size(text.size.width.toFloat(), text.size.height.toFloat())) { drawText(text) }
                surface.makeImageSnapshot().use { image ->
                    image.encodeToData()!!.use { data -> javax.imageio.ImageIO.read(data.bytes.inputStream()) }
                }
            }
        }
    }
    val active = rememberUpdatedState(marquee to smooth)
    val panel = remember(labels) {
        object : javax.swing.JPanel() {
            val timer = javax.swing.Timer(16) { if (active.value.first || active.value.second) repaint(0, 290, width, 140) }
            override fun addNotify() { super.addNotify(); timer.start() }
            override fun removeNotify() { timer.stop(); super.removeNotify() }
            override fun paintComponent(graphics: java.awt.Graphics) {
                val g = graphics.create() as java.awt.Graphics2D
                try {
                    g.color = java.awt.Color(0x24242b); g.fillRect(0, 0, width, height)
                    g.color = java.awt.Color(0x775533); g.fillRect(0, 0, width, 280)
                    g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                    val time = System.nanoTime() / 1_000_000L
                    val offset = if (active.value.first) (time % 8000) / 24.0 else 0.0
                    labels.forEachIndexed { index, image ->
                        val transform = java.awt.geom.AffineTransform.getTranslateInstance(-offset, (296 + 34 * index).toDouble())
                        transform.scale(1.0 / density.density, 1.0 / density.density)
                        g.drawImage(image, transform, null)
                    }
                    val progress = if (active.value.second) 0.2 + (time % 10000) / 300_000.0 else 0.2
                    g.color = java.awt.Color.GRAY; g.fillRect(0, 410, width, 3)
                    g.color = java.awt.Color.WHITE; g.fill(java.awt.geom.Rectangle2D.Double(0.0, 410.0, width * progress, 3.0))
                } finally { g.dispose() }
            }
        }
    }
    SwingPanel(factory = { panel }, background = Color(0xff24242b), modifier = modifier, update = { it.repaint() })
}

private object ProbeCompositor {
    var handle = 0L
    external fun positions(handle: Long): DoubleArray
    init { System.load(requireNotNull(System.getProperty("naviamp.probe.compositor.library"))) }
    external fun create(component: java.awt.Component): Long
    external fun layer(handle: Long, png: ByteArray?, x: Double, y: Double, width: Double, height: Double,
        property: String, values: DoubleArray, times: DoubleArray, duration: Double, repeat: Boolean)
    external fun clear(handle: Long)
    external fun dispose(handle: Long)
}

@Composable
private fun ProbeCompositorSurface(marquee: Boolean, smooth: Boolean, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val labels = remember(measurer, density) {
        List(3) { index ->
            val text = measurer.measure("Long scrolling player metadata $index with enough text to overflow its viewport",
                style = TextStyle(color = Color.White, fontSize = 16.sp), softWrap = false)
            val png = org.jetbrains.skia.Surface.makeRasterN32Premul(text.size.width, text.size.height).use { surface ->
                CanvasDrawScope().draw(density, LayoutDirection.Ltr, surface.canvas.asComposeCanvas(),
                    Size(text.size.width.toFloat(), text.size.height.toFloat())) { drawText(text) }
                surface.makeImageSnapshot().use { image -> image.encodeToData()!!.use { it.bytes } }
            }
            Triple(png, text.size.width / density.density.toDouble(), text.size.height / density.density.toDouble())
        }
    }
    val waveforms = remember(density) {
        listOf(0f, 1f).map { progress ->
            val width = (280 * density.density).toInt()
            val height = (32 * density.density).toInt()
            org.jetbrains.skia.Surface.makeRasterN32Premul(width, height).use { surface ->
                CanvasDrawScope().draw(density, LayoutDirection.Ltr, surface.canvas.asComposeCanvas(), Size(width.toFloat(), height.toFloat())) {
                    drawWaveformScrubberContent(List(512) { ((it * 17) % 101) / 100f }, progress,
                        true, NaviampColors(), true, Color.White)
                }
                surface.makeImageSnapshot().use { image -> image.encodeToData()!!.use { it.bytes } }
            }
        }
    }
    val latest = rememberUpdatedState(marquee to smooth)
    val canvas = remember(labels, waveforms) {
        object : java.awt.Canvas() {
            var handle = 0L
            fun updateScene() {
                if (handle == 0L) return
                ProbeCompositor.clear(handle)
                labels.forEachIndexed { index, (png, w, h) ->
                    val motion = if (latest.value.first) marqueeLayerMotion(((w - 280.0) * density.density).toFloat()) else null
                    ProbeCompositor.layer(handle, png, 0.0, (296 + index * 34).toDouble(), w, h, "transform.translation.x",
                        motion?.values?.map { it / density.density.toDouble() }?.toDoubleArray() ?: doubleArrayOf(),
                        motion?.timesMillis?.map { it.toDouble() / motion.durationMillis }?.toDoubleArray() ?: doubleArrayOf(),
                        (motion?.durationMillis ?: 1L) / 1000.0, motion?.repeat ?: false)
                }
                val progress = if (latest.value.second) progressLayerMotion(.2f, 300.0) else null
                ProbeCompositor.layer(handle, waveforms[0], 0.0, 394.0, 280.0, 32.0, "transform.translation.x",
                    doubleArrayOf(), doubleArrayOf(), 1.0, false)
                ProbeCompositor.layer(handle, waveforms[1], 0.0, 394.0, 280.0, 32.0, "bounds.size.width",
                    progress?.values?.map { it * 280.0 }?.toDoubleArray() ?: doubleArrayOf(56.0),
                    progress?.timesMillis?.map { it.toDouble() / progress.durationMillis }?.toDoubleArray() ?: doubleArrayOf(0.0),
                    (progress?.durationMillis ?: 1L) / 1000.0, progress?.repeat ?: false)
            }
            override fun addNotify() { super.addNotify(); handle = ProbeCompositor.create(this); check(handle != 0L); ProbeCompositor.handle = handle; updateScene() }
            override fun removeNotify() { ProbeCompositor.dispose(handle); handle = 0L; super.removeNotify() }
        }
    }
    SwingPanel(factory = { javax.swing.JPanel(java.awt.BorderLayout()).apply { add(canvas, java.awt.BorderLayout.CENTER) } },
        background = Color(0xff24242b), modifier = modifier, update = { canvas.updateScene() })
}

/** Known premultiplied pixels over a nonblack two-color parent; opaque black must fail. */
@Composable
private fun ProbeAlphaFixture(modifier: Modifier) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val image = remember(density) {
        ImageBitmap(with(density) { 280.dp.toPx().toInt() }, with(density) { 32.dp.toPx().toInt() }).also { bitmap ->
            CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bitmap), Size(bitmap.width.toFloat(), bitmap.height.toFloat())) {
                drawRect(Color.White, size = Size(size.width / 4, size.height))
                drawRect(Color.Red.copy(alpha = .5f), topLeft = Offset(size.width / 4, 0f), size = Size(size.width / 4, size.height))
            }
        }
    }
    val layers = remember(image) { listOf(NaviampRasterLayer(image)) }
    NaviampAnimatedRaster(layers, modifier.then(probePattern()))
}

private fun verifyProbeAlpha(image: java.awt.image.BufferedImage, window: java.awt.Window, bounds: Rect, dimmed: Boolean = false) {
    check(!bounds.isEmpty) { "Alpha fixture was not laid out" }
    val left = window.insets.left + bounds.left.toInt()
    val top = window.insets.top + bounds.top.toInt()
    fun sample(x: Int) = image.getRGB(left + (bounds.width * x / 280).toInt(), top + (bounds.height / 2).toInt()) and 0xffffff
    fun matches(actual: Int, expected: Int) = listOf(0, 8, 16).all {
        kotlin.math.abs((actual shr it and 255) - (expected shr it and 255)) <= 2
    }
    val opaque = sample(35)
    val blended = sample(105)
    val transparent = sample(210)
    println("ANIMATION_ALPHA opaque=%06x blended=%06x transparent=%06x".format(opaque, blended, transparent))
    check(matches(opaque, probeScrimColor(0xffffff, dimmed))) { "Opaque raster pixels missing" }
    check(matches(blended, probeScrimColor(0x923456, dimmed))) { "Premultiplied alpha did not blend over the parent: %06x".format(blended) }
    check(matches(transparent, probeScrimColor(0xac6824, dimmed))) { "Transparent raster pixels obscured the patterned parent: %06x".format(transparent) }
}

/** The shared modal's settled black scrim has 60% opacity; retain the same pixel tolerance. */
private fun probeScrimColor(color: Int, dimmed: Boolean): Int = if (!dimmed) color else
    listOf(0, 8, 16).fold(0) { result, shift -> result or (((color shr shift and 255) * .4).toInt() shl shift) }

private fun probePattern() = Modifier.drawBehind {
    drawRect(Color(0xff2468ac))
    drawRect(Color(0xffac6824), topLeft = Offset(size.width / 2, 0f), size = Size(size.width / 2, size.height))
}

private fun verifyProbeBackground(image: java.awt.image.BufferedImage, window: java.awt.Window, bounds: Rect, index: Int, dimmed: Boolean = false) {
    check(!bounds.isEmpty) { "Raster region $index was not laid out" }
    val left = window.insets.left + bounds.left.toInt()
    val top = window.insets.top + bounds.top.toInt()
    val width = bounds.width.toInt()
    val height = bounds.height.toInt()
    val samples = intArrayOf(0, 0)
    for (y in 2 until height - 2) for (x in 2 until width - 2) {
        val side = if (x < width / 2) 0 else 1
        val expected = probeScrimColor(if (side == 0) 0x2468ac else 0xac6824, dimmed)
        if (!probePixelsDiffer(image.getRGB(left + x, top + y), expected)) samples[side]++
    }
    println("ANIMATION_BACKGROUND region=$index samples=${samples.toList()}")
    check(samples.all { it > width * height / 20 }) { "Raster region $index obscured the patterned parent" }
}

/** Use a real independent top-level window; native raster pixels must follow their owner's stack. */
private suspend fun verifyProbeStacking(window: java.awt.Window, alphaBounds: Rect, textBounds: Rect) {
    val onTop = window.isAlwaysOnTop
    val cover = javax.swing.JFrame("Raster stacking probe").apply {
        isUndecorated = true
        contentPane.background = java.awt.Color(0x10aa30)
        setBounds(window.x, window.y, window.width, window.height)
    }
    try {
        window.isAlwaysOnTop = false
        cover.isVisible = true
        cover.toFront()
        cover.requestFocus()
        delay(1_000)
        val point = window.locationOnScreen
        val capture = java.awt.Robot(window.graphicsConfiguration.device)
            .createScreenCapture(java.awt.Rectangle(point, window.size))
        val x = window.insets.left + textBounds.left.toInt() + 10
        val y = window.insets.top + textBounds.top.toInt() + textBounds.height.toInt() / 2
        check(capture.getRGB(x, y) and 0xffffff == 0x10aa30) { "Raster pixels stayed above another window" }
        window.toFront()
        window.requestFocus()
        delay(1_000)
        val restored = captureProbe(window, "stack-restored").pixels
        verifyProbeAlpha(restored, window, alphaBounds)
        var text = 0
        for (row in 0 until textBounds.height.toInt()) for (column in 0 until textBounds.width.toInt()) {
            val pixel = restored.getRGB(window.insets.left + textBounds.left.toInt() + column,
                window.insets.top + textBounds.top.toInt() + row)
            if ((pixel shr 16 and 255) > 200 && (pixel shr 8 and 255) > 200 && (pixel and 255) > 200) text++
        }
        check(text > 100) { "Raster text did not return above its owner" }
        println("ANIMATION_STACKING covered pixels hidden; raised owner restored alpha and text")
    } finally {
        cover.dispose()
        window.isAlwaysOnTop = onTop
    }
}
