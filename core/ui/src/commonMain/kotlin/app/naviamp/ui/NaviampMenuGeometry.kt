package app.naviamp.ui

import androidx.compose.ui.unit.*
import androidx.compose.ui.graphics.TransformOrigin

/** The visible surface attaches to its control; shadow space is only a drawing outset. */
internal data class NaviampMenuGeometry(val surfaceBounds: IntRect, val paddedPosition: IntOffset,
    val transformOrigin: TransformOrigin)

internal fun naviampMenuGeometry(anchor: IntRect, window: IntSize, paddedSize: IntSize,
    direction: LayoutDirection, offset: IntOffset, margin: Int, padding: Int): NaviampMenuGeometry {
    val surfaceSize = IntSize((paddedSize.width - padding * 2).coerceAtLeast(0),
        (paddedSize.height - padding * 2).coerceAtLeast(0))
    val position = naviampMenuPosition(anchor, window, surfaceSize, direction, offset, margin)
    val surface = IntRect(position, surfaceSize)
    return NaviampMenuGeometry(surface, position - IntOffset(padding, padding), TransformOrigin(
        menuPivot(surface.left, surface.right, anchor.left, anchor.right, paddedSize.width, padding),
        menuPivot(surface.top, surface.bottom, anchor.top, anchor.bottom, paddedSize.height, padding),
    ))
}

private fun menuPivot(start: Int, end: Int, anchorStart: Int, anchorEnd: Int,
    paddedLength: Int, padding: Int): Float {
    if (paddedLength == 0 || end <= start) return .5f
    val attachment = when {
        end <= anchorStart -> (end - start).toFloat()
        start >= anchorEnd -> 0f
        else -> (maxOf(start, anchorStart) + minOf(end, anchorEnd)) * .5f - start
    }
    return ((padding + attachment) / paddedLength).coerceIn(0f, 1f)
}

internal fun naviampMenuPosition(anchor: IntRect, window: IntSize, content: IntSize,
    direction: LayoutDirection, offset: IntOffset, margin: Int): IntOffset {
    val horizontal = if (direction == LayoutDirection.Ltr)
        listOf(anchor.left + offset.x, anchor.right - content.width + offset.x)
    else listOf(anchor.right - content.width - offset.x, anchor.left - offset.x)
    val x = horizontal.firstOrNull { it >= 0 && it + content.width <= window.width }
        ?: horizontal.first().coerceIn(0, (window.width - content.width).coerceAtLeast(0))
    // A tall menu shares the remaining space instead of losing its lower shadow at the edge.
    val inset = margin.coerceAtMost(((window.height - content.height) / 2).coerceAtLeast(0))
    val bottom = (window.height - inset - content.height).coerceAtLeast(inset)
    val y = listOf(anchor.bottom + offset.y, anchor.top - content.height + offset.y)
        .firstOrNull { it in inset..bottom }
        ?: (anchor.top - content.height / 2 + offset.y).coerceIn(inset, bottom)
    return IntOffset(x, y)
}
