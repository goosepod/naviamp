package app.naviamp.ui

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import java.awt.AWTEvent
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.EventQueue
import java.awt.Frame
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.AWTEventListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import javax.swing.JWindow
import org.jetbrains.skia.Image
import org.jetbrains.skia.RRect
import org.jetbrains.skiko.SkiaLayer
import org.jetbrains.skiko.SkikoRenderDelegate

/** Native window stacking keeps menus and dialogs above independently presented raster regions. */
fun configureNaviampDesktopRasterLayers() {
    if (System.getProperty("os.name").contains("Mac") && System.getProperty("compose.layers.type") == null) {
        System.setProperty("compose.layers.type", "WINDOW")
    }
}

/** AWT publishes visibility/owned-window facts; shared UI owns motion and fallback behavior. */
@Composable
fun NaviampDesktopRasterHost(window: Window, content: @Composable () -> Unit) {
    val presenter = remember(window) {
        val forceSkia = System.getenv("NAVIAMP_RASTER_FORCE_SKIA") == "true"
        val mac = System.getProperty("os.name").contains("Mac")
        if (!forceSkia && mac && System.getProperty("compose.layers.type") == "WINDOW" && NativeMetalVisualizerHost.libraryAvailable()) {
            MacRasterPresenter(window)
        } else if (!mac || forceSkia) DesktopSkiaRasterPresenter(window) else null
    }
    var visible by remember(window) { mutableStateOf(window.isShowing) }
    var overlay by remember(window) { mutableStateOf(false) }
    DisposableEffect(window) {
        fun update() {
            visible = window.isShowing && (window !is Frame || window.extendedState and Frame.ICONIFIED == 0)
            fun hasOverlay(owner: Window): Boolean = owner.ownedWindows.any {
                it.name != RasterOverlayWindowName && (it.isShowing || hasOverlay(it))
            }
            overlay = hasOverlay(window)
            (presenter as? DesktopSkiaRasterPresenter)?.reposition()
        }
        val listener = AWTEventListener { event ->
            val source = event.source as? Component
            if (source is Window || source == window) EventQueue.invokeLater { update() }
        }
        Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.WINDOW_EVENT_MASK or AWTEvent.COMPONENT_EVENT_MASK)
        update()
        onDispose { Toolkit.getDefaultToolkit().removeAWTEventListener(listener) }
    }
    NaviampRasterEnvironment(presenter, visible, overlay, content)
}

/** Windows and Linux isolate animation in small transparent Skia hardware surfaces. */
private class DesktopSkiaRasterPresenter(private val window: Window) : NaviampRasterPresenter {
    private val regions = mutableSetOf<DesktopSkiaRasterRegion>()
    override fun create(): NaviampRasterRegion = DesktopSkiaRasterRegion(window) { regions.remove(it) }.also(regions::add)
    fun reposition() = regions.forEach(DesktopSkiaRasterRegion::reposition)
}

private class DesktopSkiaRasterRegion(
    private val window: Window,
    private val removed: (DesktopSkiaRasterRegion) -> Unit,
) : NaviampRasterRegion {
    private data class Layer(val image: Image, val spec: NaviampRasterLayer)
    private data class Scene(
        val layers: List<Layer>, val originX: Float, val originY: Float, val viewportWidth: Float,
        val cornerRadius: Float, val startedAtNanos: Long,
    )

    @Volatile private var scene: Scene? = null
    private var clip = Rect.Zero
    private var scale = 1f
    private val surface = SkiaLayer().apply {
        transparency = true
        background = Color(0, 0, 0, 0)
        isFocusable = false
    }
    private val overlay = JWindow(window).apply {
        name = RasterOverlayWindowName
        background = Color(0, 0, 0, 0)
        focusableWindowState = false
        contentPane.layout = BorderLayout()
        contentPane.add(surface, BorderLayout.CENTER)
    }

    init {
        surface.renderDelegate = object : SkikoRenderDelegate {
            override fun onRender(canvas: org.jetbrains.skia.Canvas, width: Int, height: Int, nanoTime: Long) {
                canvas.clear(0x00000000)
                val current = scene ?: return
                val elapsed = (nanoTime - current.startedAtNanos) / 1_000_000L
                canvas.save()
                if (current.cornerRadius > 0f) {
                    canvas.clipRRect(RRect.makeXYWH(0f, 0f, width.toFloat(), height.toFloat(),
                        current.cornerRadius, current.cornerRadius), true)
                }
                var animateAgain = false
                current.layers.forEach { layer ->
                    val spec = layer.spec
                    val reveal = spec.revealMotion?.valueAt(elapsed) ?: spec.reveal
                    val edge = current.originX + current.viewportWidth * reveal
                    canvas.save()
                    canvas.clipRect(org.jetbrains.skia.Rect.makeLTRB(
                        if (spec.clipFromStart) edge else 0f, 0f,
                        if (spec.clipFromStart) width.toFloat() else edge, height.toFloat(),
                    ))
                    canvas.drawImage(layer.image,
                        current.originX + spec.origin.x + (spec.translation?.valueAt(elapsed) ?: 0f),
                        current.originY + spec.origin.y)
                    canvas.restore()
                    val motion = spec.translation ?: spec.revealMotion
                    animateAgain = animateAgain || (motion != null && (motion.repeat || elapsed < motion.durationMillis))
                }
                canvas.restore()
                if (animateAgain) surface.needRender()
            }
        }
        val forwarding = object : MouseAdapter() {
            override fun mousePressed(event: MouseEvent) = forward(event)
            override fun mouseReleased(event: MouseEvent) = forward(event)
            override fun mouseClicked(event: MouseEvent) = forward(event)
            override fun mouseMoved(event: MouseEvent) = forward(event)
            override fun mouseDragged(event: MouseEvent) = forward(event)
            override fun mouseWheelMoved(event: MouseWheelEvent) = forward(event)
        }
        listOf(surface, surface.canvas).forEach {
            it.addMouseListener(forwarding)
            it.addMouseMotionListener(forwarding)
            it.addMouseWheelListener(forwarding)
        }
    }

    override fun present(layers: List<NaviampRasterLayer>, bounds: Rect, clip: Rect, cornerRadius: Float): Boolean {
        val parent = findSkiaLayer(window) ?: return false
        if (!window.isShowing || bounds.isEmpty || clip.isEmpty) return false
        scale = parent.contentScale.coerceAtLeast(1f)
        this.clip = clip
        reposition()
        scene = Scene(
            layers.map { Layer(Image.makeFromBitmap(it.image.asSkiaBitmap()), it) },
            bounds.left - clip.left, bounds.top - clip.top, bounds.width, cornerRadius, System.nanoTime(),
        )
        overlay.isVisible = true
        surface.needRender()
        return true
    }

    override fun translationX(layerIndex: Int): Float? {
        val current = scene ?: return null
        val motion = current.layers.getOrNull(layerIndex)?.spec?.translation ?: return 0f
        return motion.valueAt((System.nanoTime() - current.startedAtNanos) / 1_000_000L)
    }

    override fun close() {
        scene = null
        overlay.dispose()
        surface.dispose()
        removed(this)
    }

    fun reposition() {
        if (!window.isShowing || clip.isEmpty) return
        val location = window.locationOnScreen
        overlay.setBounds(
            location.x + window.insets.left + (clip.left / scale).toInt(),
            location.y + window.insets.top + (clip.top / scale).toInt(),
            (clip.width / scale).toInt().coerceAtLeast(1),
            (clip.height / scale).toInt().coerceAtLeast(1),
        )
    }

    private fun forward(event: MouseEvent) {
        val target = findSkiaLayer(window) ?: return
        val screen = event.locationOnScreen
        val targetScreen = target.locationOnScreen
        val x = screen.x - targetScreen.x
        val y = screen.y - targetScreen.y
        val forwarded = if (event is MouseWheelEvent) MouseWheelEvent(target, event.id, event.`when`,
            event.modifiersEx, x, y, event.xOnScreen, event.yOnScreen, event.clickCount,
            event.isPopupTrigger, event.scrollType, event.scrollAmount, event.wheelRotation,
            event.preciseWheelRotation) else MouseEvent(target, event.id, event.`when`, event.modifiersEx,
            x, y, event.xOnScreen, event.yOnScreen, event.clickCount, event.isPopupTrigger, event.button)
        target.dispatchEvent(forwarded)
    }
}

private const val RasterOverlayWindowName = "naviamp-raster-overlay"

/** Only the JAWT/CALayer lifetime and image/JNI type conversions live here. */
private class MacRasterPresenter(private val window: Window) : NaviampRasterPresenter {
    override fun create(): NaviampRasterRegion = object : NaviampRasterRegion {
        var handle = 0L
        var cachedImages = emptyList<ImageBitmap>()
        var encoded = emptyArray<ByteArray>()

        override fun present(layers: List<NaviampRasterLayer>, bounds: Rect, clip: Rect, cornerRadius: Float): Boolean {
            val layer = findSkiaLayer(window) ?: return false
            return try {
                if (handle == 0L) handle = DesktopRasterNative.create(layer.canvas)
                if (handle == 0L) {
                    if (System.getenv("NAVIAMP_RASTER_DIAGNOSTICS") == "true") println("NaviampRaster native surface not attached")
                    return false
                }
                val images = layers.map { it.image }
                if (images != cachedImages) {
                    encoded = images.map { bitmap ->
                        Image.makeFromBitmap(bitmap.asSkiaBitmap()).use { image -> image.encodeToData()!!.use { it.bytes } }
                    }.toTypedArray()
                    cachedImages = images
                }
                val motions = layers.map { it.translation ?: it.revealMotion }
                DesktopRasterNative.present(handle,
                    doubleArrayOf(bounds.left.toDouble(), bounds.top.toDouble(), clip.left.toDouble(), clip.top.toDouble(),
                        clip.width.toDouble(), clip.height.toDouble(), cornerRadius.toDouble(), layer.contentScale.toDouble(),
                        bounds.width.toDouble(), bounds.height.toDouble()), encoded,
                    layers.map { doubleArrayOf(it.origin.x.toDouble(), it.origin.y.toDouble(), it.image.width.toDouble(),
                        it.image.height.toDouble(), it.reveal.toDouble(), if (it.clipFromStart) 1.0 else 0.0) }.toTypedArray(),
                    motions.map { motion -> motion?.values?.map(Float::toDouble)?.toDoubleArray() ?: doubleArrayOf() }.toTypedArray(),
                    motions.map { motion -> motion?.timesMillis?.map { it.toDouble() / motion.durationMillis }?.toDoubleArray() ?: doubleArrayOf() }.toTypedArray(),
                    motions.map { (it?.durationMillis ?: 1L) / 1000.0 }.toDoubleArray(),
                    layers.map { if (it.translation != null || (it.revealMotion == null && it.reveal == 1f && !it.clipFromStart)) 0 else 1 }.toIntArray(),
                    motions.map { it?.repeat ?: false }.toBooleanArray())
                true
            } catch (failure: UnsatisfiedLinkError) {
                if (System.getenv("NAVIAMP_RASTER_DIAGNOSTICS") == "true") println("NaviampRaster native linkage: ${failure.message}")
                close()
                false
            }
        }

        override fun translationX(layerIndex: Int): Float? =
            if (handle == 0L) null else DesktopRasterNative.translationX(handle, layerIndex).toFloat()

        override fun close() {
            if (handle != 0L) DesktopRasterNative.close(handle)
            handle = 0L
            cachedImages = emptyList()
            encoded = emptyArray()
        }
    }
}

private fun findSkiaLayer(component: Component): SkiaLayer? = when (component) {
    is SkiaLayer -> component
    is Container -> component.components.firstNotNullOfOrNull(::findSkiaLayer)
    else -> null
}

private object DesktopRasterNative {
    external fun create(component: Component): Long
    external fun present(handle: Long, region: DoubleArray, pngs: Array<ByteArray>, geometry: Array<DoubleArray>,
        values: Array<DoubleArray>, times: Array<DoubleArray>, durations: DoubleArray, kinds: IntArray, repeats: BooleanArray)
    external fun translationX(handle: Long, index: Int): Double
    external fun close(handle: Long)
}
