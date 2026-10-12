package app.naviamp.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntRect
import kotlin.math.ceil
import kotlin.math.floor

/** A compositor that clips to whole pixels need not re-submit unchanged pixel geometry. */
internal data class NaviampRasterPixelCrop(val source: IntRect, val destination: IntRect)

internal fun naviampRasterPixelCrop(
    image: Rect,
    bounds: Rect,
    clip: Rect,
    reveal: Float?,
    clipFromStart: Boolean,
): NaviampRasterPixelCrop? {
    var left = maxOf(clip.left, image.left)
    var right = minOf(clip.right, image.right)
    val top = maxOf(clip.top, image.top)
    val bottom = minOf(clip.bottom, image.bottom)
    if (reveal != null) {
        val edge = bounds.left + bounds.width * reveal
        if (clipFromStart) left = maxOf(left, edge) else right = minOf(right, edge)
    }
    if (right <= left || bottom <= top) return null
    return NaviampRasterPixelCrop(
        source = IntRect(floor(left - image.left).toInt(), floor(top - image.top).toInt(),
            ceil(right - image.left).toInt(), ceil(bottom - image.top).toInt()),
        destination = IntRect(floor(left - bounds.left).toInt(), floor(top - bounds.top).toInt(),
            ceil(right - bounds.left).toInt(), ceil(bottom - bounds.top).toInt()),
    )
}

/** Buffer/layer replacement forces a commit; unchanged geometry and hidden content do not. */
internal class NaviampRasterPixelCropState {
    private var initialized = false
    private var previous: NaviampRasterPixelCrop? = null
    fun update(next: NaviampRasterPixelCrop?, force: Boolean = false): Boolean {
        if (initialized && previous == next && !force) return false
        initialized = true
        previous = next
        return true
    }
}
