package app.naviamp.ui

import androidx.compose.ui.geometry.Rect
import java.awt.Component
import java.awt.Window

/** JAWT/CAMetalLayer adapter. Shared Core supplies every frame and presentation decision. */
internal class DesktopGpuVisualizerPresenter(private val window: Window) : NaviampGpuVisualizerPresenter {
    override fun create(shader: NaviampGpuVisualizerShader): NaviampGpuVisualizerRegion =
        DesktopGpuVisualizerRegion(window, shader)
}

private class DesktopGpuVisualizerRegion(
    private val window: Window, private val shader: NaviampGpuVisualizerShader,
) : NaviampGpuVisualizerRegion {
    private var handle = 0L
    private var geometry = DoubleArray(12)
    private var visible = false
    private fun attach(): Boolean {
        if (handle != 0L) return true
        val parent = findSkiaLayer(window) ?: return false
        if (!parent.canvas.isDisplayable) return false
        handle = DesktopGpuVisualizerNative.create(parent.canvas, shader.metal)
        if (handle == 0L) return false
        DesktopGpuVisualizerNative.place(handle, geometry)
        DesktopGpuVisualizerNative.setVisible(handle, visible)
        return true
    }
    override fun place(bounds: Rect, clip: Rect, cornerRadius: Float) {
        val scale = findSkiaLayer(window)?.contentScale ?: 1f
        geometry = doubleArrayOf(bounds.left.toDouble(), bounds.top.toDouble(), bounds.width.toDouble(),
            bounds.height.toDouble(), clip.left.toDouble(), clip.top.toDouble(), clip.width.toDouble(),
            clip.height.toDouble(), scale.toDouble(), cornerRadius.toDouble(), geometry[10], geometry[11])
        if (handle != 0L) DesktopGpuVisualizerNative.place(handle, geometry)
    }
    override fun setVisible(visible: Boolean) {
        if (this.visible == visible) return
        this.visible = visible
        if (handle != 0L) DesktopGpuVisualizerNative.setVisible(handle, visible)
    }
    override fun submit(frame: NaviampGpuVisualizerFrame): NaviampGpuSubmission = try {
        val raster = frame.rasterSize
        val resized = geometry[10] != raster.width.toDouble() || geometry[11] != raster.height.toDouble()
        geometry[10] = raster.width.toDouble(); geometry[11] = raster.height.toDouble()
        if (!attach()) NaviampGpuSubmission.NotReady else {
            if (resized) DesktopGpuVisualizerNative.place(handle, geometry)
            NaviampGpuSubmission.entries.getOrElse(DesktopGpuVisualizerNative.submit(handle, frame.bands, frame.uniforms)) {
                NaviampGpuSubmission.Failed
            }
        }
    } catch (_: Throwable) { NaviampGpuSubmission.Failed }
    override fun close() {
        if (handle != 0L) DesktopGpuVisualizerNative.close(handle)
        handle = 0L
    }
}

internal object DesktopGpuVisualizerNative {
    external fun create(component: Component, shader: String): Long
    external fun place(handle: Long, geometry: DoubleArray)
    external fun setVisible(handle: Long, visible: Boolean)
    external fun submit(handle: Long, bands: FloatArray, uniforms: IntArray): Int
    external fun close(handle: Long)
}
