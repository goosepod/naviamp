package app.naviamp.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Independent edges remain drawable when the viewport clips part of a section's contour. */
internal fun Modifier.smartPlaylistSectionOutline(color: Color, radius: Dp): Modifier = drawWithCache {
    val width = 1.dp.toPx().coerceAtMost(minOf(size.width, size.height))
    val r = radius.toPx().coerceIn(width / 2f, minOf(size.width, size.height) / 2f)
    val half = width / 2f
    val arcSize = Size((r * 2f - width).coerceAtLeast(0f), (r * 2f - width).coerceAtLeast(0f))
    val stroke = Stroke(width)
    onDrawBehind {
        drawRect(color, Offset(r, 0f), Size((size.width - 2f * r).coerceAtLeast(0f), width))
        drawRect(color, Offset(r, size.height - width), Size((size.width - 2f * r).coerceAtLeast(0f), width))
        drawRect(color, Offset(0f, r), Size(width, (size.height - 2f * r).coerceAtLeast(0f)))
        drawRect(color, Offset(size.width - width, r), Size(width, (size.height - 2f * r).coerceAtLeast(0f)))
        drawArc(color, 180f, 90f, false, Offset(half, half), arcSize, style = stroke)
        drawArc(color, 270f, 90f, false, Offset(size.width - 2f * r + half, half), arcSize, style = stroke)
        drawArc(color, 0f, 90f, false, Offset(size.width - 2f * r + half, size.height - 2f * r + half), arcSize, style = stroke)
        drawArc(color, 90f, 90f, false, Offset(half, size.height - 2f * r + half), arcSize, style = stroke)
    }
}
