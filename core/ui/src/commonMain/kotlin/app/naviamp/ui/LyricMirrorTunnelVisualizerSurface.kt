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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal data class LyricMirrorTunnelLine(
    val key: String,
    val text: String,
    val progressToNext: Float,
    val enterProgress: Float = 1f,
    val exitProgress: Float = 0f,
    val verticalSlot: Float = 0f,
    val role: LyricMirrorTunnelLineRole = LyricMirrorTunnelLineRole.Active,
)

internal enum class LyricMirrorTunnelLineRole {
    Previous,
    Active,
    Upcoming,
}

internal data class LyricMirrorTunnelStage(
    val lines: List<LyricMirrorTunnelLine> = emptyList(),
) {
    val primaryLine: LyricMirrorTunnelLine?
        get() = lines.firstOrNull { it.role == LyricMirrorTunnelLineRole.Active }
            ?: lines.firstOrNull()
}

internal val EmptyLyricMirrorTunnelStage = LyricMirrorTunnelStage()

internal fun LyricMirrorTunnelLine?.asLyricMirrorTunnelStage(): LyricMirrorTunnelStage =
    this?.let { LyricMirrorTunnelStage(listOf(it)) } ?: EmptyLyricMirrorTunnelStage

internal fun LyricMirrorTunnelStage.primaryText(): String =
    primaryLine?.text.orEmpty()

internal fun LyricMirrorTunnelStage.primaryProgressToNext(): Float =
    primaryLine?.progressToNext ?: 0f

internal fun LyricMirrorTunnelStage.primaryLineForNativeMask(): LyricMirrorTunnelLine? =
    primaryLine

internal fun LyricMirrorTunnelLine(
    text: String,
    progressToNext: Float,
): LyricMirrorTunnelLine = LyricMirrorTunnelLine(
    key = text,
    text = text,
    progressToNext = progressToNext,
)

@Composable
internal fun LyricMirrorTunnelVisualizerSurface(
    bandsProvider: () -> List<Float>,
    visualizerColors: NaviampPlayerColors,
    active: Boolean,
    colors: NaviampColors,
    lyricStage: LyricMirrorTunnelStage,
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
    val beatIntensity = if (active && bass > 0.34f) ((bass - 0.34f) / 0.48f).coerceIn(0f, 1f) else 0f
    val stageLines = lyricStage.lines
    val primaryStageLine = lyricStage.primaryLine
    var activeLine by remember { mutableStateOf<LyricMirrorTunnelLine?>(null) }
    var activeLineStartedAtMillis by remember { mutableLongStateOf(0L) }
    var outgoingLine by remember { mutableStateOf<LyricMirrorTunnelLine?>(null) }
    var outgoingLineStartedAtMillis by remember { mutableLongStateOf(0L) }
    val previousFrameMillis = remember { mutableLongStateOf(0L) }
    LaunchedEffect(primaryStageLine?.key) {
        val nextActiveLine = primaryStageLine
        if (nextActiveLine == null) {
            activeLine?.let { previous ->
                outgoingLine = previous.copy(
                    role = LyricMirrorTunnelLineRole.Previous,
                    enterProgress = 1f,
                    exitProgress = 0f,
                )
                outgoingLineStartedAtMillis = frameMillis.takeIf { it > 0L } ?: previousFrameMillis.longValue
                activeLine = null
                activeLineStartedAtMillis = 0L
            }
        } else if (nextActiveLine.key != activeLine?.key) {
            activeLine?.let { previous ->
                outgoingLine = previous.copy(
                    role = LyricMirrorTunnelLineRole.Previous,
                    enterProgress = 1f,
                    exitProgress = 0f,
                )
                outgoingLineStartedAtMillis = frameMillis.takeIf { it > 0L } ?: previousFrameMillis.longValue
            }
            activeLine = nextActiveLine.copy(
                role = LyricMirrorTunnelLineRole.Active,
                enterProgress = nextActiveLine.enterProgress,
                exitProgress = 0f,
            )
            val startFrameMillis = frameMillis.takeIf { it > 0L } ?: previousFrameMillis.longValue
            activeLineStartedAtMillis = startFrameMillis -
                (nextActiveLine.enterProgress.coerceIn(0f, 1f) * LyricTunnelEnterMillis).toLong()
        }
    }
    val frameClockMillis = frameMillis.takeIf { it > 0L } ?: previousFrameMillis.longValue
    val activeDisplayLine = activeLine
        ?.copy(
            enterProgress = if (activeLineStartedAtMillis > 0L) {
                ((frameClockMillis - activeLineStartedAtMillis).toFloat() / LyricTunnelEnterMillis).coerceIn(0f, 1f)
            } else {
                1f
            },
            exitProgress = 0f,
        )
    val outgoingDisplayLine = outgoingLine
        ?.let { line ->
            val exitProgress = if (outgoingLineStartedAtMillis > 0L) {
                ((frameClockMillis - outgoingLineStartedAtMillis).toFloat() / LyricTunnelFallMillis).coerceIn(0f, 1f)
            } else {
                1f
            }
            if (exitProgress < 1f) line.copy(exitProgress = exitProgress) else null
        }
    if (outgoingDisplayLine == null && outgoingLine != null) {
        outgoingLine = null
    }
    val displayLines = if (activeDisplayLine == null) {
        buildList {
            outgoingDisplayLine?.let(::add)
            addAll(stageLines)
        }
    } else {
        buildList {
            outgoingDisplayLine?.let(::add)
            addAll(lyricStage.lines.filter { it.role == LyricMirrorTunnelLineRole.Upcoming })
            add(activeDisplayLine)
        }
    }
    val tunnelPush = remember { FloatArray(LyricTunnelRingCount) }
    val previousMillis = previousFrameMillis.longValue.takeIf { it > 0L } ?: frameMillis
    val deltaSeconds = ((frameMillis - previousMillis).coerceAtLeast(0L) / 1000f).coerceIn(0f, 0.12f)
    previousFrameMillis.longValue = frameMillis
    updateLyricTunnelPush(tunnelPush, beatIntensity, deltaSeconds)

    Box(modifier = modifier.clipToBounds()) {
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
        displayLines.forEach { line ->
            key(line.key) {
                LyricTunnelStageLine(
                    line = line,
                    colors = colors,
                    playerColors = visualizerColors,
                    beatIntensity = beatIntensity,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxSize()
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                )
            }
        }
    }
}

@Composable
private fun LyricTunnelStageLine(
    line: LyricMirrorTunnelLine,
    colors: NaviampColors,
    playerColors: NaviampPlayerColors,
    beatIntensity: Float,
    modifier: Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val enter = lyricTunnelEaseInOut(line.enterProgress.coerceIn(0f, 1f))
    val exit = line.exitProgress.coerceIn(0f, 1f)
    val active = line.role == LyricMirrorTunnelLineRole.Active
    val foreground = line.role != LyricMirrorTunnelLineRole.Previous
    val lineLength = line.text.length.coerceAtLeast(1)
    val baseFontSize = if (foreground) 30f else 22f
    val fontSize = (baseFontSize - (lineLength - 18).coerceAtLeast(0) * 0.28f).coerceIn(if (foreground) 22f else 17f, baseFontSize)
    val emphasis = if (foreground) 1f else 0.72f
    val alpha = (enter * (1f - exit * 0.34f) * emphasis).coerceIn(0f, 1f)
    val slotOffsetPx = line.verticalSlot * 74f
    val enterLiftPx = (1f - enter) * -28f
    val fallPx = exit * exit * 58f
    val driftPx = sin((line.key.hashCode() and 0xff) * 0.17f) * exit * 3f
    val beatScale = if (active && enter > 0.98f) beatIntensity * 0.018f else 0f
    val scale = (0.92f + enter * 0.08f + beatScale - exit * 0.02f).coerceIn(0.82f, 1.06f)
    val particleRelease = exit
    val shadowOffset = (1.4f + beatIntensity * 1.8f).dp
    val shadowAlpha = (0.48f * (1f - exit * 3.6f)).coerceIn(0f, 0.48f) * alpha
    val textAlpha = (alpha * (1f - exit * 3.8f)).coerceIn(0f, 1f)
    val particleOpacity = if (foreground) {
        (0.46f + exit * 0.54f).coerceIn(0f, 1f)
    } else {
        1f
    }
    val style = TextStyle(
        fontSize = fontSize.sp,
        lineHeight = (fontSize * 1.08f).sp,
        fontWeight = FontWeight.ExtraBold,
        textAlign = TextAlign.Center,
    )

    Box(
        modifier = modifier.graphicsLayer {
            translationX = driftPx
            translationY = slotOffsetPx + enterLiftPx + fallPx
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        },
    ) {
        Text(
            text = line.text,
            color = Color.Black.copy(alpha = shadowAlpha),
            style = style,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.Center).padding(start = shadowOffset, top = shadowOffset),
        )
        if (foreground || particleRelease > 0f) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    },
            ) {
                drawLyricGlyphParticles(
                    textMeasurer = textMeasurer,
                    text = line.text,
                    style = style,
                    release = particleRelease,
                    opacity = particleOpacity,
                    color = Color.White,
                    playerColors = playerColors,
                )
            }
        }
        Text(
            text = line.text,
            color = Color.White.copy(alpha = textAlpha * 0.44f),
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

private fun DrawScope.drawLyricGlyphParticles(
    textMeasurer: TextMeasurer,
    text: String,
    style: TextStyle,
    release: Float,
    opacity: Float,
    color: Color,
    playerColors: NaviampPlayerColors,
) {
    val eased = release * release * (3f - 2f * release)
    val alpha = (0.98f * opacity * (1f - release * 0.34f)).coerceIn(0f, 0.98f)
    if (alpha <= 0.01f) return

    val center = Offset(size.width / 2f, size.height / 2f)
    val measured = textMeasurer.measure(
        text = text,
        style = style.copy(color = Color.White),
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        constraints = androidx.compose.ui.unit.Constraints(
            maxWidth = size.width.toInt().coerceAtLeast(1),
            maxHeight = size.height.toInt().coerceAtLeast(1),
        ),
    )
    val layoutSize = Size(measured.size.width.toFloat(), measured.size.height.toFloat())
    if (layoutSize.width <= 0f || layoutSize.height <= 0f) return

    val textTopLeft = Offset(
        x = center.x - layoutSize.width / 2f,
        y = center.y - layoutSize.height / 2f,
    )
    val glyphBoxes = text.indices
        .filterNot { text[it].isWhitespace() }
        .map { measured.getBoundingBox(it) }
        .filter { it.width > 0.5f && it.height > 0.5f }

    if (release <= 0.001f) {
        drawText(
            textLayoutResult = measured,
            color = Color.White,
            topLeft = textTopLeft,
        )
    }

    repeat(ParticleCount) { index ->
        val seedA = lyricParticleHash(index, 13)
        val seedB = lyricParticleHash(index, 47)
        val seedC = lyricParticleHash(index, 91)
        val glyphBox = glyphBoxes.getOrNull((index * 37 + (seedA * glyphBoxes.size.coerceAtLeast(1)).toInt()).floorMod(glyphBoxes.size.coerceAtLeast(1)))
        val sourceX = if (glyphBox != null) {
            textTopLeft.x + glyphBox.left + seedA * glyphBox.width
        } else {
            textTopLeft.x + seedA * layoutSize.width
        }
        val sourceY = if (glyphBox != null) {
            textTopLeft.y + glyphBox.top + seedB * glyphBox.height
        } else {
            textTopLeft.y + seedB * layoutSize.height
        }
        val sourceDepth = ((sourceY - textTopLeft.y) / layoutSize.height.coerceAtLeast(1f)).coerceIn(0f, 1f)
        val verticalWeight = (0.62f + sourceDepth * 0.72f + seedB * 0.24f).coerceIn(0.62f, 1.58f)
        val sideways = (seedC - 0.5f) * eased * (1.2f + release * 5.4f)
        val gravity = eased * eased * (40f + 360f * verticalWeight)
        val particleSize = (1.0f + seedC * 1.7f + release * 0.8f).coerceIn(1f, 3.5f)
        val particleCenter = Offset(
            x = sourceX + sideways,
            y = sourceY + gravity,
        )
        val trailStart = Offset(
            x = sourceX + sideways * 0.28f,
            y = sourceY + gravity * 0.58f,
        )
        val sparkleColor = lerp(color, playerColors.accent, seedC * 0.12f).copy(alpha = alpha)
        drawLine(
            color = sparkleColor.copy(alpha = alpha * 0.44f),
            start = trailStart,
            end = particleCenter,
            strokeWidth = (particleSize * 0.48f).coerceAtLeast(0.8f),
            blendMode = if (release <= 0.001f) BlendMode.SrcIn else BlendMode.SrcOver,
        )
        drawRect(
            color = sparkleColor,
            topLeft = Offset(particleCenter.x - particleSize / 2f, particleCenter.y - particleSize / 2f),
            size = Size(particleSize, particleSize),
            blendMode = if (release <= 0.001f) BlendMode.SrcIn else BlendMode.SrcOver,
        )
    }
}

private fun lyricTunnelEaseInOut(value: Float): Float {
    val clamped = value.coerceIn(0f, 1f)
    return clamped * clamped * clamped * (clamped * (clamped * 6f - 15f) + 10f)
}

private fun Int.floorMod(divisor: Int): Int =
    ((this % divisor) + divisor) % divisor

private fun lyricParticleHash(index: Int, salt: Int): Float {
    val value = kotlin.math.sin((index * 12.9898f + salt * 78.233f).toDouble()) * 43758.5453
    return (value - kotlin.math.floor(value)).toFloat()
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
private const val ParticleCount = 1040
private const val LyricTunnelEnterMillis = 820f
private const val LyricTunnelFallMillis = 3800f
