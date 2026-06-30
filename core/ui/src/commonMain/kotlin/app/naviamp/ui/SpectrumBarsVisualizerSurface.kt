package app.naviamp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.max

@Composable
internal fun SpectrumBarsVisualizerSurface(
    bandsProvider: () -> List<Float>,
    visualizerColors: NaviampPlayerColors,
    active: Boolean,
    colors: NaviampColors,
    renderPolicy: VisualizerRenderPolicy,
    modifier: Modifier,
) {
    var frameMillis by remember { mutableLongStateOf(0L) }
    val smoothedBands = remember(renderPolicy.maxCanvasSamples) { FloatArray(renderPolicy.maxCanvasSamples) }
    val peakBands = remember(renderPolicy.maxCanvasSamples) { FloatArray(renderPolicy.maxCanvasSamples) }
    val previousFrameMillis = remember { mutableLongStateOf(0L) }

    LaunchedEffect(active, renderPolicy.targetFrameIntervalMillis) {
        if (!active) {
            frameMillis = 0L
            previousFrameMillis.longValue = 0L
            smoothedBands.fill(0f)
            peakBands.fill(0f)
            return@LaunchedEffect
        }
        var lastRenderedFrameMillis = 0L
        while (true) {
            withFrameMillis { nextFrameMillis ->
                if (lastRenderedFrameMillis == 0L ||
                    nextFrameMillis - lastRenderedFrameMillis >= renderPolicy.targetFrameIntervalMillis
                ) {
                    lastRenderedFrameMillis = nextFrameMillis
                    frameMillis = nextFrameMillis
                }
            }
        }
    }

    Canvas(modifier = modifier) {
        val bands = bandsProvider()
        val visibleBands = minOf(
            bands.size,
            renderPolicy.maxCanvasSamples,
            (size.width / 7f).toInt().coerceAtLeast(18),
        )
        if (!active || visibleBands <= 0) {
            drawSpectrumIdleLine(colors)
            return@Canvas
        }

        val previousMillis = previousFrameMillis.longValue.takeIf { it > 0L } ?: frameMillis
        val deltaSeconds = ((frameMillis - previousMillis).coerceAtLeast(0L) / 1000f).coerceIn(0f, 0.08f)
        previousFrameMillis.longValue = frameMillis

        val samples = spectrumSampleBands(bands, visibleBands)
        drawSpectrumBars(
            samples = samples,
            smoothedBands = smoothedBands,
            peakBands = peakBands,
            deltaSeconds = deltaSeconds,
            playerColors = visualizerColors,
            colors = colors,
        )
    }
}

private fun DrawScope.drawSpectrumIdleLine(colors: NaviampColors) {
    drawLine(
        color = colors.primaryText.copy(alpha = 0.16f),
        start = Offset(0f, size.height / 2f),
        end = Offset(size.width, size.height / 2f),
        strokeWidth = 1.4f,
    )
}

private fun spectrumSampleBands(bands: List<Float>, visibleBands: Int): List<Float> =
    List(visibleBands) { index ->
        val sourceIndex = if (visibleBands == 1 || bands.size == 1) {
            0
        } else {
            ((index / (visibleBands - 1f)) * (bands.size - 1)).toInt()
        }
        bands.getOrNull(sourceIndex)?.coerceIn(0f, 1f) ?: 0f
    }

private fun DrawScope.drawSpectrumBars(
    samples: List<Float>,
    smoothedBands: FloatArray,
    peakBands: FloatArray,
    deltaSeconds: Float,
    playerColors: NaviampPlayerColors,
    colors: NaviampColors,
) {
    val energy = samples.average().toFloat().coerceIn(0f, 1f)
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                playerColors.backgroundStart.copy(alpha = 0.32f + energy * 0.12f),
                playerColors.backgroundMid.copy(alpha = 0.36f + energy * 0.16f),
                playerColors.backgroundEnd.copy(alpha = 0.42f + energy * 0.18f),
            ),
        ),
    )

    val horizontalPadding = max(10f, size.width * 0.035f)
    val topPadding = max(12f, size.height * 0.08f)
    val bottomPadding = max(14f, size.height * 0.11f)
    val usableWidth = (size.width - horizontalPadding * 2f).coerceAtLeast(1f)
    val usableHeight = (size.height - topPadding - bottomPadding).coerceAtLeast(1f)
    val step = usableWidth / samples.size.toFloat()
    val barWidth = (step * 0.62f).coerceIn(2.4f, 12f)
    val cornerRadius = CornerRadius(barWidth * 0.38f, barWidth * 0.38f)
    val peakHeight = (barWidth * 0.34f).coerceIn(1.6f, 4f)
    val peakFallPerSecond = 0.34f

    samples.forEachIndexed { index, rawAmplitude ->
        val emphasized = (rawAmplitude * 1.16f).coerceIn(0f, 1f)
        val current = smoothedBands.getOrElse(index) { 0f }
        val rise = 0.52f
        val fall = 0.14f
        val smoothed = current + (emphasized - current) * if (emphasized > current) rise else fall
        smoothedBands[index] = smoothed.coerceIn(0f, 1f)

        val previousPeak = peakBands.getOrElse(index) { 0f }
        val nextPeak = if (smoothed >= previousPeak) {
            smoothed
        } else {
            max(smoothed, previousPeak - peakFallPerSecond * deltaSeconds)
        }
        peakBands[index] = nextPeak.coerceIn(0f, 1f)

        val x = horizontalPadding + index * step + (step - barWidth) / 2f
        val barHeight = (3f + smoothedBands[index] * usableHeight).coerceAtMost(usableHeight)
        val barTop = size.height - bottomPadding - barHeight
        val peakY = size.height - bottomPadding - peakBands[index] * usableHeight
        val gradientPosition = index / (samples.lastIndex).coerceAtLeast(1).toFloat()
        val barColor = spectrumBarColor(playerColors, colors, gradientPosition, smoothedBands[index])

        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    lerp(barColor, colors.primaryText, 0.28f).copy(alpha = 0.84f),
                    barColor.copy(alpha = 0.58f + smoothedBands[index] * 0.34f),
                    playerColors.backgroundEnd.copy(alpha = 0.40f + smoothedBands[index] * 0.20f),
                ),
                startY = barTop,
                endY = size.height - bottomPadding,
            ),
            topLeft = Offset(x, barTop),
            size = Size(barWidth, barHeight),
            cornerRadius = cornerRadius,
        )
        drawRoundRect(
            color = colors.primaryText.copy(alpha = 0.52f + peakBands[index] * 0.28f),
            topLeft = Offset(x, (peakY - peakHeight).coerceAtLeast(topPadding)),
            size = Size(barWidth, peakHeight),
            cornerRadius = CornerRadius(peakHeight, peakHeight),
        )
    }

    drawLine(
        color = colors.primaryText.copy(alpha = 0.12f),
        start = Offset(horizontalPadding, size.height - bottomPadding + 1f),
        end = Offset(size.width - horizontalPadding, size.height - bottomPadding + 1f),
        strokeWidth = 1f,
    )
}

private fun spectrumBarColor(
    playerColors: NaviampPlayerColors,
    colors: NaviampColors,
    position: Float,
    amplitude: Float,
): Color {
    val gradientColor = if (position < 0.5f) {
        lerp(playerColors.backgroundStart, playerColors.backgroundMid, position * 2f)
    } else {
        lerp(playerColors.backgroundMid, playerColors.backgroundEnd, (position - 0.5f) * 2f)
    }
    return lerp(gradientColor, playerColors.accent, 0.34f + amplitude * 0.24f)
        .let { lerp(it, colors.primaryText, 0.18f) }
}
