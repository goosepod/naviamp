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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.lang.management.ManagementFactory
import kotlinx.coroutines.delay

/** Synthetic real-window rendering probe. No provider, audio engine, or user data is loaded. */
fun main() {
    val integrated = System.getenv("NAVIAMP_PROBE_INTEGRATED") == "true"
    val verifyPixels = System.getenv("NAVIAMP_PROBE_VERIFY") == "true"
    if (integrated) configureNaviampDesktopRasterLayers()
    application {
    val compositor = remember { System.getenv("NAVIAMP_PROBE_COMPOSITOR") == "true" }
    val raster = remember { System.getenv("NAVIAMP_PROBE_RASTER") == "true" }
    val raw = remember { System.getenv("NAVIAMP_PROBE_RAW") == "true" }
    val isolated = remember { System.getenv("NAVIAMP_PROBE_ISOLATED") == "true" }
    var phase by remember { mutableStateOf("static") }
    var popupVisible by remember { mutableStateOf(false) }
    val rowCount = remember { System.getenv("NAVIAMP_PROBE_ROWS")?.toIntOrNull()?.coerceIn(0, 1000) ?: 150 }
    val cpu = remember { ManagementFactory.getOperatingSystemMXBean() as com.sun.management.OperatingSystemMXBean }
    Window(
        onCloseRequest = ::exitApplication,
        title = "Naviamp animation CPU probe",
        state = rememberWindowState(width = 1000.dp, height = 740.dp),
        alwaysOnTop = verifyPixels,
    ) {
        val marquee = phase == "marquee" || phase == "combined"
        val smooth = phase == "waveform" || phase == "combined"
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
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                WaveformScrubber(
                    amplitudes = List(512) { ((it * 17) % 101) / 100f },
                    value = 0.2f, enabled = true, smoothProgress = smooth,
                    durationSeconds = 300.0, colors = NaviampColors(),
                    onValueChange = {}, onValueChangeFinished = {},
                    modifier = Modifier.fillMaxWidth().height(32.dp),
                )
                Text(phase, color = Color.White)
            }
            }
            }
            Column(Modifier.weight(1f).padding(start = 24.dp).verticalScroll(rememberScrollState())) {
                repeat(rowCount) { index ->
                    Text("Library row $index — artist and album metadata", color = Color.White,
                        modifier = Modifier.fillMaxWidth().padding(12.dp).background(Color(0xff303039)))
                }
            }
        }
        }
        if (integrated) NaviampDesktopRasterHost(window, content) else content()
        if (popupVisible) androidx.compose.ui.window.Popup(alignment = androidx.compose.ui.Alignment.TopEnd) {
            Box(Modifier.size(80.dp, 30.dp).background(Color.Magenta))
        }
        LaunchedEffect(Unit) {
            try {
            delay(1_000)
            if (verifyPixels) captureProbe(window, "warmup")
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
            println("ANIMATION_PROBE integrated=$integrated compositor=$compositor raster=$raster raw=$raw isolated=$isolated rows=$rowCount layers=${counters.map { it.first }} phase,cpu_percent,frames")
            for (next in listOf("static", "marquee", "waveform", "combined")) {
                phase = next
                delay(5_000)
                val initialFrames = counters.map { it.second.get() }
                val positions = if (compositor) ProbeCompositor.positions(ProbeCompositor.handle).toList() else emptyList()
                val beforePixels = if (verifyPixels) captureProbe(window, "$next-before") else null
                val startCpu = cpu.processCpuTime
                val start = System.nanoTime()
                delay(10_000)
                val percent = (cpu.processCpuTime - startCpu).toDouble() / (System.nanoTime() - start) * 100.0
                println("ANIMATION_PROBE $next,$percent,${counters.mapIndexed { index, counter -> counter.second.get() - initialFrames[index] }}")
                if (verifyPixels) {
                    val afterCapture = captureProbe(window, "$next-after")
                    val beforeCapture = requireNotNull(beforePixels)
                    val afterPixels = afterCapture.pixels
                    val before = beforeCapture.pixels
                    var changed = 0
                    var textChanged = 0
                    var waveformChanged = 0
                    var textPixels = 0
                    for (y in 340 until minOf(580, before.height)) for (x in 24 until minOf(304, before.width)) {
                        if (beforeCapture.nearPointer(x, y) || afterCapture.nearPointer(x, y)) continue
                        val pixel = afterPixels.getRGB(x, y)
                        if (pixel != before.getRGB(x, y)) {
                            changed++
                            if (y < 450) textChanged++
                            if (y in 450..490) waveformChanged++
                        }
                        if ((pixel shr 16 and 255) > 200 && (pixel shr 8 and 255) > 200 && (pixel and 255) > 200) textPixels++
                    }
                    var siblingChanges = 0
                    for (y in 60 until minOf(690, before.height)) for (x in 350 until minOf(850, before.width)) {
                        if (beforeCapture.nearPointer(x, y) || afterCapture.nearPointer(x, y)) continue
                        if (afterPixels.getRGB(x, y) != before.getRGB(x, y)) siblingChanges++
                    }
                    println("ANIMATION_PIXELS $next changed=$changed text=$textChanged waveform=$waveformChanged visibleText=$textPixels sibling=$siblingChanges")
                    check(textPixels > 100) { "Cached text is blank" }
                    if (next == "marquee" || next == "combined") check(textChanged > 10) { "Text did not move" }
                    if (next == "waveform" || next == "combined") check(waveformChanged > 10) { "Waveform did not move" }
                    check(siblingChanges == 0) { "Unrelated content changed" }
                    check(counters.mapIndexed { index, counter -> counter.second.get() - initialFrames[index] }.all { it == 0L }) { "Static parent redrew" }
                }
                if (compositor) {
                    val after = ProbeCompositor.positions(ProbeCompositor.handle).toList()
                    println("COMPOSITOR_POSITIONS $next $positions -> $after")
                    check(after[0] > 0.0 && after[1] > 0.0) { "Native surface is empty" }
                    if (next == "marquee" || next == "combined") check((2..4).all { kotlin.math.abs(after[it] - positions[it]) > 1.0 }) { "Marquee did not move" }
                    if (next == "waveform" || next == "combined") check(after[5] > positions[5] + 1.0) { "Progress did not advance" }
                    check(counters.mapIndexed { index, counter -> counter.second.get() - initialFrames[index] }.all { it == 0L }) { "Static parent redrew" }
                }
            }
            if (verifyPixels) {
                // Exercise real owned-window notifications while the compositor timelines run.
                repeat(12) { index ->
                    popupVisible = index % 2 == 0
                    delay(80)
                    val pixels = captureProbe(window, "popup-$index").pixels
                    var visibleText = 0
                    for (y in 340 until minOf(450, pixels.height)) for (x in 24 until minOf(304, pixels.width)) {
                        val pixel = pixels.getRGB(x, y)
                        if ((pixel shr 16 and 255) > 200 && (pixel shr 8 and 255) > 200 && (pixel and 255) > 200) visibleText++
                    }
                    check(visibleText > 100) { "Text disappeared during popup transition $index" }
                }
                println("ANIMATION_POPUPS 12 visible-text samples passed")
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

private fun captureProbe(window: java.awt.Window, name: String): ProbeCapture {
    val origin = window.locationOnScreen
    val pointer = java.awt.MouseInfo.getPointerInfo().location.apply { translate(-origin.x, -origin.y) }
    val image = java.awt.Robot(window.graphicsConfiguration.device)
        .createScreenCapture(java.awt.Rectangle(window.locationOnScreen, window.size))
    val directory = java.io.File("build/animation-probe").apply { mkdirs() }
    javax.imageio.ImageIO.write(image, "png", java.io.File(directory, "$name.png"))
    check(image.getRGB(100, 100) and 0x00ffffff == 0x775533) { "Probe is obscured by another window" }
    return ProbeCapture(image, pointer)
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
