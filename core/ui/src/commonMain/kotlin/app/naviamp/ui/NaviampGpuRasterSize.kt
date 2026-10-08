package app.naviamp.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

/** Expensive continuous fields have a bounded fragment budget independent of display density. */
internal fun naviampGpuRasterSize(
    visualizer: NaviampVisualizer, width: Int, height: Int, pixelBudget: Int = 512 * 512,
): IntSize {
    require(width > 0 && height > 0 && pixelBudget > 0)
    if (visualizer != NaviampVisualizer.OceanOfInk || width.toLong() * height <= pixelBudget) return IntSize(width, height)
    val scale = sqrt(pixelBudget.toDouble() / (width.toDouble() * height))
    val rasterWidth = floor(width * scale).toInt().coerceIn(1, minOf(width, pixelBudget))
    val rasterHeight = floor(height * scale).toInt().coerceIn(1, minOf(height, pixelBudget / rasterWidth))
    return IntSize(rasterWidth, rasterHeight)
}

/** Bottom-origin raster clipping; display geometry and shader raster dimensions are independent. */
internal fun naviampGpuRasterClip(bounds: Rect, clip: Rect, raster: IntSize): IntRect {
    if (bounds.isEmpty || raster.width <= 0 || raster.height <= 0) return IntRect.Zero
    val visible = bounds.intersect(clip)
    if (visible.isEmpty) return IntRect.Zero
    val sx = raster.width / bounds.width
    val sy = raster.height / bounds.height
    return IntRect(
        floor((visible.left - bounds.left) * sx).toInt().coerceIn(0, raster.width),
        floor((bounds.bottom - visible.bottom) * sy).toInt().coerceIn(0, raster.height),
        ceil((visible.right - bounds.left) * sx).toInt().coerceIn(0, raster.width),
        ceil((bounds.bottom - visible.top) * sy).toInt().coerceIn(0, raster.height),
    )
}
