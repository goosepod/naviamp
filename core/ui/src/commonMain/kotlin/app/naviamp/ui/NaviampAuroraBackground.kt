package app.naviamp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

internal fun NaviampPlayerColors.auroraColors(steps: Int): List<Color> = when (steps.coerceIn(2, 5)) {
    2 -> listOf(backgroundStart, backgroundEnd)
    3 -> gradientColors
    4 -> listOf(backgroundStart, backgroundMid, additionalBackgroundColors.getOrElse(0) { backgroundMid }, backgroundEnd)
    else -> listOf(backgroundStart, backgroundMid,
        additionalBackgroundColors.getOrElse(0) { backgroundMid },
        additionalBackgroundColors.getOrElse(1) { backgroundEnd }, backgroundEnd)
}

/** A centered gradient whose endpoints span the rectangle at any selected angle. */
internal fun auroraGradientEndpoints(size: Size, angleDegrees: Int): Pair<Offset, Offset> {
    val radians = angleDegrees.coerceIn(0, 180) * kotlin.math.PI / 180.0
    val direction = Offset(cos(radians).toFloat(), sin(radians).toFloat())
    val halfSpan = (abs(direction.x) * size.width + abs(direction.y) * size.height) / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    return center - direction * halfSpan to center + direction * halfSpan
}

@Composable
internal fun NaviampAuroraBackground(colors: NaviampPlayerColors, steps: Int, angleDegrees: Int) {
    Box(Modifier.fillMaxSize().drawWithCache {
        val (start, end) = auroraGradientEndpoints(size, angleDegrees)
        val brush = Brush.linearGradient(colors.auroraColors(steps), start = start, end = end)
        onDrawBehind { drawRect(brush) }
    })
}
