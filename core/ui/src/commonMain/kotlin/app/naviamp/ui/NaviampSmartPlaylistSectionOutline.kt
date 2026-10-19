package app.naviamp.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Keep clipped section outlines on the ordinary path renderer, including their visible sides. */
internal fun Modifier.smartPlaylistSectionOutline(color: Color, radius: Dp): Modifier = drawWithCache {
    val width = 1.dp.toPx()
    val inset = width / 2f
    val right = size.width - inset
    val bottom = size.height - inset
    val r = (radius.toPx() - inset).coerceIn(0f, (minOf(size.width, size.height) / 2f - inset).coerceAtLeast(0f))
    val c = r * 0.55228475f
    // Build the contour explicitly instead of using the GPU's stroked rounded-rectangle primitive.
    // Its geometry stays unchanged as the scroll viewport clips off the top and bottom edges.
    val outline = Path().apply {
        moveTo(inset + r, inset)
        lineTo(right - r, inset)
        cubicTo(right - r + c, inset, right, inset + r - c, right, inset + r)
        lineTo(right, bottom - r)
        cubicTo(right, bottom - r + c, right - r + c, bottom, right - r, bottom)
        lineTo(inset + r, bottom)
        cubicTo(inset + r - c, bottom, inset, bottom - r + c, inset, bottom - r)
        lineTo(inset, inset + r)
        cubicTo(inset, inset + r - c, inset + r - c, inset, inset + r, inset)
        close()
    }
    val stroke = Stroke(width)
    onDrawBehind { drawPath(outline, color, style = stroke) }
}
