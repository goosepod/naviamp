package app.naviamp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal data class LyricMirrorTunnelLine(
    val text: String,
    val progressToNext: Float,
)

@Composable
internal fun LyricMirrorTunnelVisualizerSurface(
    bandsProvider: () -> List<Float>,
    visualizerColors: NaviampPlayerColors,
    active: Boolean,
    colors: NaviampColors,
    lyricLine: LyricMirrorTunnelLine?,
    renderPolicy: VisualizerRenderPolicy,
    modifier: Modifier,
) {
    var frameMillis by remember { mutableLongStateOf(0L) }
    LaunchedEffect(active, renderPolicy.targetFrameIntervalMillis) {
        if (!active) {
            frameMillis = 0L
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

    val bands = bandsProvider()
    val sampledBands = remember(bands, renderPolicy.maxCanvasSamples) {
        lyricTunnelSampleBands(bands, minOf(renderPolicy.maxCanvasSamples, 64))
    }
    val bass = sampledBands.take(10).averageOrZero().coerceIn(0f, 1f)
    val energy = sampledBands.averageOrZero().coerceIn(0f, 1f)
    val pixelPop = lyricLine?.progressToNext
        ?.let { progress -> ((progress - 0.62f) / 0.36f).coerceIn(0f, 1f) }
        ?: 0f
    val beatIntensity = if (active && bass > 0.34f) ((bass - 0.34f) / 0.48f).coerceIn(0f, 1f) else 0f
    val lyricText = lyricLine?.text?.takeIf { it.isNotBlank() } ?: "..."
    val tunnelPush = remember { FloatArray(LyricTunnelRingCount) }
    val previousFrameMillis = remember { mutableLongStateOf(0L) }
    val previousMillis = previousFrameMillis.longValue.takeIf { it > 0L } ?: frameMillis
    val deltaSeconds = ((frameMillis - previousMillis).coerceAtLeast(0L) / 1000f).coerceIn(0f, 0.12f)
    previousFrameMillis.longValue = frameMillis
    updateLyricTunnelPush(tunnelPush, beatIntensity, deltaSeconds)

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawLyricTunnelScene(
                samples = sampledBands,
                playerColors = visualizerColors,
                colors = colors,
                active = active,
                timeSeconds = frameMillis / 1000f,
                energy = energy,
                ringPush = tunnelPush,
            )
        }
        LyricTunnelText(
            text = lyricText,
            colors = colors,
            playerColors = visualizerColors,
            pixelPop = pixelPop,
            beatIntensity = beatIntensity,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        )
    }
}

@Composable
private fun LyricTunnelText(
    text: String,
    colors: NaviampColors,
    playerColors: NaviampPlayerColors,
    pixelPop: Float,
    beatIntensity: Float,
    modifier: Modifier,
) {
    val outlineOffset = (1.2f + beatIntensity * 4.6f + pixelPop * 1.4f).dp
    val outlineAlpha = (0.86f + beatIntensity * 0.12f - pixelPop * 0.30f).coerceIn(0f, 0.96f)
    val fillAlpha = (1f - pixelPop * 0.62f).coerceIn(0f, 1f)
    val style = TextStyle(
        fontSize = 25.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.ExtraBold,
        textAlign = TextAlign.Center,
    )

    Box(modifier = modifier) {
        val outlineColor = lerp(Color.White, playerColors.accent, 0.22f).copy(alpha = outlineAlpha)
        val offsets = listOf(
            Modifier.align(Alignment.Center).padding(start = outlineOffset),
            Modifier.align(Alignment.Center).padding(end = outlineOffset),
            Modifier.align(Alignment.Center).padding(top = outlineOffset),
            Modifier.align(Alignment.Center).padding(bottom = outlineOffset),
            Modifier.align(Alignment.Center).padding(start = outlineOffset * 0.74f, top = outlineOffset * 0.74f),
            Modifier.align(Alignment.Center).padding(end = outlineOffset * 0.74f, top = outlineOffset * 0.74f),
            Modifier.align(Alignment.Center).padding(start = outlineOffset * 0.74f, bottom = outlineOffset * 0.74f),
            Modifier.align(Alignment.Center).padding(end = outlineOffset * 0.74f, bottom = outlineOffset * 0.74f),
        )
        offsets.forEach { outlineModifier ->
            Text(
                text = text,
                color = outlineColor,
                style = style,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = outlineModifier,
            )
        }
        if (pixelPop > 0f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawLyricOutlineParticles(
                    pixelPop = pixelPop,
                    color = outlineColor,
                    playerColors = playerColors,
                )
            }
        }
        Text(
            text = text,
            color = Color.Black.copy(alpha = fillAlpha),
            style = style,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.Center),
        )
        Text(
            text = text,
            color = colors.primaryText.copy(alpha = 0.10f * fillAlpha),
            style = style,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

internal fun DrawScope.drawLyricTunnelScene(
    samples: List<Float>,
    playerColors: NaviampPlayerColors,
    colors: NaviampColors,
    active: Boolean,
    timeSeconds: Float,
    energy: Float,
    ringPush: FloatArray,
) {
    clipRect {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    lerp(playerColors.backgroundMid, Color.Black, 0.42f).copy(alpha = 0.96f),
                    lerp(playerColors.backgroundStart, Color.Black, 0.58f).copy(alpha = 0.98f),
                    Color.Black.copy(alpha = 0.94f),
                ),
                center = Offset(size.width / 2f, size.height / 2f),
                radius = max(size.width, size.height) * (0.64f + energy * 0.08f),
            ),
        )

        val edgeInset = max(8f, min(size.width, size.height) * 0.035f)
        val center = Offset(size.width / 2f, size.height / 2f)
        val minSide = min(size.width, size.height)
        val outerStroke = 8.4f + ringPush.maxOrZero() * 2.2f
        val outerFrameInset = edgeInset + outerStroke / 2f
        val outerWidth = (size.width - outerFrameInset * 2f).coerceAtLeast(1f)
        val outerHeight = (size.height - outerFrameInset * 2f).coerceAtLeast(1f)

        drawRoundRect(
            color = playerColors.accent.copy(alpha = 0.78f + energy * 0.16f),
            topLeft = Offset(outerFrameInset, outerFrameInset),
            size = Size(outerWidth, outerHeight),
            cornerRadius = CornerRadius(3f, 3f),
            style = Stroke(width = outerStroke),
        )
        val innerStroke = 4.2f + ringPush.maxOrZero() * 1.4f
        val innerInset = outerFrameInset + outerStroke / 2f + innerStroke / 2f + 4f
        val innerWidth = (size.width - innerInset * 2f).coerceAtLeast(1f)
        val innerHeight = (size.height - innerInset * 2f).coerceAtLeast(1f)
        drawRoundRect(
            color = lerp(playerColors.accent, colors.primaryText, 0.24f)
                .copy(alpha = 0.52f + ringPush.maxOrZero() * 0.22f),
            topLeft = Offset(innerInset, innerInset),
            size = Size(innerWidth, innerHeight),
            cornerRadius = CornerRadius(2f, 2f),
            style = Stroke(width = innerStroke),
        )

        repeat(LyricTunnelRingCount) { index ->
            val push = ringPush.getOrNull(index)?.coerceIn(0f, 1f) ?: 0f
            if (push <= 0.035f) return@repeat
            val fraction = index / (LyricTunnelRingCount - 1f)
            val amplitude = samples.getOrNull((fraction * (samples.lastIndex).coerceAtLeast(0)).toInt()) ?: 0f
            val drift = sin(timeSeconds * 1.25f + index * 0.72f) * minSide * 0.003f
            val basePerspective = 0.26f + fraction * 0.63f
            val perspective = (basePerspective + push * (0.10f + fraction * 0.07f)).coerceIn(0.18f, 0.96f)
            val strokeWidth = (1.4f + fraction * 3.8f + push * 2.6f).coerceIn(1.2f, 7.4f)
            val safeWidth = (innerWidth - strokeWidth * 2f - 5f).coerceAtLeast(1f)
            val safeHeight = (innerHeight - strokeWidth * 2f - 5f).coerceAtLeast(1f)
            val width = (safeWidth * perspective + amplitude * 7f + drift).coerceIn(1f, safeWidth)
            val height = (safeHeight * perspective + amplitude * 7f - drift).coerceIn(1f, safeHeight)
            val topLeft = Offset(center.x - width / 2f, center.y - height / 2f)
            val lineColor = lerp(playerColors.backgroundEnd, playerColors.accent, fraction * 0.72f)
            drawRoundRect(
                color = lineColor.copy(alpha = (0.08f + push * 0.48f - (1f - fraction) * 0.05f).coerceIn(0.04f, 0.76f)),
                topLeft = topLeft,
                size = Size(width, height),
                cornerRadius = CornerRadius(3f + fraction * 7f, 3f + fraction * 7f),
                style = Stroke(width = strokeWidth),
            )
        }
    }
}

private fun updateLyricTunnelPush(
    ringPush: FloatArray,
    beatIntensity: Float,
    deltaSeconds: Float,
) {
    val falloff = 0.24f * deltaSeconds
    for (index in ringPush.indices) {
        val waveDelay = index * 0.055f
        val target = (beatIntensity - waveDelay).coerceIn(0f, 1f)
        ringPush[index] = max(ringPush[index] - falloff, target)
    }
}

private fun DrawScope.drawLyricOutlineParticles(
    pixelPop: Float,
    color: Color,
    playerColors: NaviampPlayerColors,
) {
    val eased = pixelPop * pixelPop * (3f - 2f * pixelPop)
    val alpha = (0.52f * (1f - pixelPop * 0.72f)).coerceIn(0f, 0.52f)
    if (alpha <= 0.01f) return

    val center = Offset(size.width / 2f, size.height / 2f)
    val textWidth = size.width * 0.66f
    val textHeight = size.height * 0.58f
    repeat(ParticleCount) { index ->
        val side = index % 4
        val lane = index / 4
        val laneFraction = ((lane % 8) + 0.5f) / 8f
        val jitter = ((index * 37) % 11 - 5) * 0.8f
        val base = when (side) {
            0 -> Offset(center.x - textWidth / 2f + textWidth * laneFraction, center.y - textHeight / 2f)
            1 -> Offset(center.x + textWidth / 2f, center.y - textHeight / 2f + textHeight * laneFraction)
            2 -> Offset(center.x + textWidth / 2f - textWidth * laneFraction, center.y + textHeight / 2f)
            else -> Offset(center.x - textWidth / 2f, center.y + textHeight / 2f - textHeight * laneFraction)
        }
        val direction = Offset(
            x = (base.x - center.x).let { if (it == 0f) jitter else it },
            y = (base.y - center.y).let { if (it == 0f) -jitter else it },
        ).normalizedOrZero()
        val travel = 5f + eased * (18f + (index % 5) * 4f)
        val particleSize = 1.4f + (index % 3) * 0.8f + pixelPop * 1.2f
        val particleCenter = base + direction * travel + Offset(jitter, -jitter * 0.35f) * eased
        drawRect(
            color = lerp(color, playerColors.accent, (index % 7) / 10f).copy(alpha = alpha),
            topLeft = Offset(particleCenter.x - particleSize / 2f, particleCenter.y - particleSize / 2f),
            size = Size(particleSize, particleSize),
        )
    }
}

private fun Offset.normalizedOrZero(): Offset {
    val length = kotlin.math.sqrt(x * x + y * y)
    return if (length <= 0.001f) Offset.Zero else Offset(x / length, y / length)
}

private fun lyricTunnelSampleBands(bands: List<Float>, visibleBands: Int): List<Float> {
    if (visibleBands <= 0) return emptyList()
    return List(visibleBands) { index ->
        val sourceIndex = if (visibleBands == 1 || bands.size <= 1) {
            0
        } else {
            ((index / (visibleBands - 1f)) * (bands.size - 1)).toInt()
        }
        bands.getOrNull(sourceIndex)?.coerceIn(0f, 1f) ?: 0f
    }
}

private fun List<Float>.averageOrZero(): Float =
    if (isEmpty()) 0f else average().toFloat()

private fun FloatArray.maxOrZero(): Float =
    if (isEmpty()) 0f else maxOrNull() ?: 0f

private const val LyricTunnelRingCount = 7
private const val ParticleCount = 32
