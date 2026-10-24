package app.naviamp.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntRect
import kotlin.math.ceil
import kotlin.math.floor

/** A fixed viewport clips a cached image; scrolling changes only its fractional translation. */
internal data class NaviampRasterTranslation(val viewport: IntRect, val position: Offset)

internal fun naviampRasterTranslation(bounds: Rect, clip: Rect, origin: Offset, x: Float): NaviampRasterTranslation? {
    val visible = bounds.intersect(clip)
    if (visible.isEmpty) return null
    return NaviampRasterTranslation(
        IntRect(floor(visible.left - bounds.left).toInt(), floor(visible.top - bounds.top).toInt(),
            ceil(visible.right - bounds.left).toInt(), ceil(visible.bottom - bounds.top).toInt()),
        Offset(origin.x + x, origin.y),
    )
}
