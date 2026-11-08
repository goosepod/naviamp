package app.naviamp.ui

import java.awt.AWTEvent
import java.awt.Component
import java.awt.Container
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.AWTEventListener
import java.awt.event.WindowEvent
import org.jetbrains.skiko.GraphicsApi
import org.jetbrains.skiko.SkiaLayer

/** Diagnostic only: change a popup's renderer, never the parent or a global renderer default. */
internal fun probePopupRenderer(value: String?): GraphicsApi? = when (value) {
    null -> null
    "OPENGL" -> GraphicsApi.OPENGL
    "SOFTWARE_FAST" -> GraphicsApi.SOFTWARE_FAST
    else -> error("NAVIAMP_PROBE_POPUP_RENDER_API must be OPENGL or SOFTWARE_FAST")
}

internal fun Window.isProbePopupOf(owner: Window): Boolean =
    this !== owner && name != RasterOverlayWindowName &&
        generateSequence(this.owner) { it.owner }.any { it === owner }

/** Native AWT ownership/Skiko instrumentation; all menu, dialog and motion behavior stays shared. */
internal fun observeProbePopupWindows(owner: Window, renderer: GraphicsApi?): AutoCloseable {
    require(renderer == null || System.getProperty("os.name").startsWith("Windows")) {
        "The popup renderer comparison is a Windows-only diagnostic"
    }
    fun layers(component: Component): List<SkiaLayer> = buildList {
        if (component is SkiaLayer) add(component)
        if (component is Container) component.components.forEach { addAll(layers(it)) }
    }
    fun observe(window: Window) {
        if (!window.isProbePopupOf(owner)) return
        layers(window).forEach { layer ->
            val original = layer.renderApi
            if (renderer != null) layer.renderApi = renderer
            println("ANIMATION_POPUP_RENDERER window=${window.name} original=$original " +
                "actual=${layer.renderApi} transparent=${layer.transparency} bounds=${window.bounds} " +
                "info=${layer.renderInfo}")
        }
    }
    val listener = AWTEventListener { event ->
        if (event is WindowEvent && event.id == WindowEvent.WINDOW_OPENED) observe(event.window)
    }
    val toolkit = Toolkit.getDefaultToolkit()
    toolkit.addAWTEventListener(listener, AWTEvent.WINDOW_EVENT_MASK)
    fun existing(window: Window) {
        window.ownedWindows.forEach { child -> observe(child); existing(child) }
    }
    existing(owner)
    return AutoCloseable { toolkit.removeAWTEventListener(listener) }
}

/** Compare the transparent shadow padding with the same physical pixels before opening. */
internal fun verifyProbeTooltipTransparency(
    before: java.awt.image.BufferedImage,
    after: java.awt.image.BufferedImage,
    parentOrigin: java.awt.Point,
    popupBounds: java.awt.Rectangle,
) {
    require(before.width == after.width && before.height == after.height)
    val left = popupBounds.x - parentOrigin.x + 1
    val top = popupBounds.y - parentOrigin.y + 1
    val right = left + popupBounds.width - 3
    val bottom = top + popupBounds.height - 3
    val corners = listOf(left to top, right to top, left to bottom, right to bottom)
        .filter { (x, y) -> x in 0 until before.width && y in 0 until before.height }
    check(corners.size >= 2) { "Tooltip padding is outside the captured parent; no transparency result" }
    corners.forEach { (x, y) ->
        val expected = before.getRGB(x, y)
        val actual = after.getRGB(x, y)
        check(listOf(0, 8, 16).all { shift ->
            kotlin.math.abs((expected shr shift and 255) - (actual shr shift and 255)) <= 8
        }) { "Tooltip padding obscured its parent at $x,$y: expected=%06x actual=%06x"
            .format(expected and 0xffffff, actual and 0xffffff) }
    }
    println("ANIMATION_TOOLTIP transparent_corners=${corners.size}")
}
