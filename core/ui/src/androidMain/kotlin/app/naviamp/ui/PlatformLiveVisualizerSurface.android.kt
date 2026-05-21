package app.naviamp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap

@Composable
internal actual fun PlatformLiveVisualizerSurface(
    bandsProvider: () -> List<Float>,
    active: Boolean,
    colors: NaviampColors,
    modifier: Modifier,
) {
    Canvas(modifier = modifier) {
        val bands = bandsProvider()
        val visibleBands = minOf(bands.size, (size.width / 6f).toInt().coerceAtLeast(16))
        if (!active || visibleBands <= 0) {
            drawLine(
                color = colors.primaryText.copy(alpha = 0.16f),
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 1.4f,
                cap = StrokeCap.Round,
            )
            return@Canvas
        }

        val step = size.width / visibleBands.toFloat()
        val strokeWidth = (step * 0.48f).coerceIn(1.2f, 3.2f)
        val centerY = size.height / 2f
        repeat(visibleBands) { index ->
            val sourceIndex = if (visibleBands == 1) {
                0
            } else {
                ((index / (visibleBands - 1f)) * (bands.size - 1)).toInt()
            }
            val amplitude = bands[sourceIndex].coerceIn(0f, 1f)
            val barHeight = (2f + amplitude * (size.height - 2f)).coerceAtMost(size.height)
            val x = index * step + step / 2f
            drawLine(
                color = colors.accent.copy(alpha = 0.30f + amplitude * 0.55f),
                start = Offset(x, centerY - barHeight / 2f),
                end = Offset(x, centerY + barHeight / 2f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}
