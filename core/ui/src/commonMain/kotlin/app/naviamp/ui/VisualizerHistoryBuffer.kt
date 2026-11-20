package app.naviamp.ui

import kotlin.math.abs

/** Owns history eligibility, cadence, change threshold, row aging and portable pixel encoding. */
internal class VisualizerHistoryBuffer(
    private val visualizer: NaviampVisualizer,
    private val renderPolicy: VisualizerRenderPolicy,
) {
    private val previousBands = FloatArray(VisualizerFrameBandCount)
    private val pixels = IntArray(VisualizerHistoryColumns * VisualizerHistoryRows)
    private var cachedRgba: ByteArray? = null
    private var lastPushSeconds = -1f

    fun update(timeSeconds: Float, active: Boolean, bands: FloatArray): Boolean {
        if (!active || !visualizer.usesHistoryShader) return false
        if (lastPushSeconds >= 0f && timeSeconds - lastPushSeconds < renderPolicy.historyIntervalSeconds) return false
        val changed = bands.indices.any { index -> abs(bands[index] - previousBands[index]) > 0.006f }
        if (!changed && lastPushSeconds >= 0f) return false
        lastPushSeconds = timeSeconds
        bands.copyInto(previousBands)
        pixels.copyInto(pixels, VisualizerHistoryColumns, 0, VisualizerHistoryColumns * (VisualizerHistoryRows - 1))
        repeat(VisualizerHistoryColumns) { column ->
            val sourceIndex = ((column / (VisualizerHistoryColumns - 1f)) * (VisualizerFrameBandCount - 1)).toInt()
            val value = (bands[sourceIndex].coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
            pixels[column] = (255 shl 24) or (value shl 16) or (value shl 8) or value
        }
        cachedRgba?.let { rgba ->
            val rowBytes = VisualizerHistoryColumns * 4
            rgba.copyInto(rgba, rowBytes, 0, rowBytes * (VisualizerHistoryRows - 1))
            repeat(VisualizerHistoryColumns) { column -> encodeRgbaPixel(rgba, column) }
        }
        return true
    }

    fun copyArgbTo(destination: IntArray) {
        require(destination.size == pixels.size)
        pixels.copyInto(destination)
    }

    fun copyRgbaTo(destination: ByteArray) {
        require(destination.size == pixels.size * 4)
        val rgba = cachedRgba ?: ByteArray(destination.size).also { buffer ->
            pixels.indices.forEach { index -> encodeRgbaPixel(buffer, index) }
            cachedRgba = buffer
        }
        rgba.copyInto(destination)
    }

    private fun encodeRgbaPixel(destination: ByteArray, index: Int) {
        val pixel = pixels[index]
        val offset = index * 4
        destination[offset] = (pixel ushr 16).toByte()
        destination[offset + 1] = (pixel ushr 8).toByte()
        destination[offset + 2] = pixel.toByte()
        destination[offset + 3] = (pixel ushr 24).toByte()
    }
}

internal val NaviampVisualizer.usesHistoryShader: Boolean
    get() = this == NaviampVisualizer.SpectralRidge || this == NaviampVisualizer.FftMountain ||
        this == NaviampVisualizer.PixelRidge || this == NaviampVisualizer.PixelMountain

internal val NaviampVisualizer.historyUsesNearestSampling: Boolean
    get() = this == NaviampVisualizer.PixelRidge || this == NaviampVisualizer.PixelMountain
