package app.naviamp.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

@Composable
internal actual fun PlatformLiveVisualizerSurface(
    coverArtUrl: String?,
    bandsProvider: () -> List<Float>,
    visualizer: NaviampVisualizer,
    visualizerColors: NaviampPlayerColors,
    active: Boolean,
    colors: NaviampColors,
    modifier: Modifier,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        AndroidShaderVisualizerSurface(
            coverArtUrl = coverArtUrl,
            bandsProvider = bandsProvider,
            visualizer = visualizer,
            visualizerColors = visualizerColors,
            active = active,
            colors = colors,
            modifier = modifier,
        )
        return
    }
    AndroidCanvasVisualizerSurface(
        bandsProvider = bandsProvider,
        visualizer = visualizer,
        visualizerColors = visualizerColors,
        active = active,
        colors = colors,
        modifier = modifier,
    )
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun AndroidShaderVisualizerSurface(
    coverArtUrl: String?,
    bandsProvider: () -> List<Float>,
    visualizer: NaviampVisualizer,
    visualizerColors: NaviampPlayerColors,
    active: Boolean,
    colors: NaviampColors,
    modifier: Modifier,
) {
    var frameMillis by remember { mutableLongStateOf(0L) }
    LaunchedEffect(active, visualizer) {
        if (!active) {
            frameMillis = 0L
            return@LaunchedEffect
        }
        while (true) {
            withFrameMillis { frameMillis = it }
        }
    }
    var albumArtBitmap by remember(coverArtUrl) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(coverArtUrl, visualizer) {
        albumArtBitmap = if (coverArtUrl != null && visualizer == NaviampVisualizer.AlbumArtReactive) {
            withContext(Dispatchers.IO) {
                runCatching { URL(coverArtUrl).openStream().use(BitmapFactory::decodeStream) }.getOrNull()
            }
        } else {
            null
        }
    }
    val renderer = remember(visualizer) { AndroidShaderVisualizerRenderer(visualizer) }
    DisposableEffect(renderer) {
        onDispose { renderer.close() }
    }

    Canvas(modifier = modifier) {
        val bands = bandsProvider()
        val visibleBands = minOf(bands.size, (size.width / 6f).toInt().coerceAtLeast(16))
        if (visibleBands <= 0 || size.width <= 0f || size.height <= 0f) {
            return@Canvas
        }
        drawIntoCanvas { canvas ->
            renderer.draw(
                canvas = canvas.nativeCanvas,
                width = size.width,
                height = size.height,
                bands = bands,
                visibleBands = visibleBands,
                active = active,
                colors = colors,
                visualizerColors = visualizerColors,
                albumArtBitmap = albumArtBitmap,
                timeSeconds = frameMillis / 1000f,
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class AndroidShaderVisualizerRenderer(
    visualizer: NaviampVisualizer,
) : AutoCloseable {
    private val runtimeShader = RuntimeShader(visualizer.shaderSource)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val uniformBands = FloatArray(AndroidVisualizerShaderBandCount)
    private val smoothBands = FloatArray(AndroidVisualizerShaderBandCount)
    private val historyPixels = IntArray(AndroidVisualizerHistoryColumns * AndroidVisualizerHistoryRows)
    private val previousHistoryBands = FloatArray(AndroidVisualizerShaderBandCount)
    private val historyBitmap = Bitmap.createBitmap(AndroidVisualizerHistoryColumns, AndroidVisualizerHistoryRows, Bitmap.Config.ARGB_8888)
    private val historyShader = BitmapShader(historyBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    private val fallbackAlbumArtBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
        eraseColor(android.graphics.Color.WHITE)
    }
    private val fallbackAlbumArtShader = BitmapShader(fallbackAlbumArtBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    private var albumArtBitmap: Bitmap? = null
    private var albumArtShader: BitmapShader? = null
    private var lastHistoryPushSeconds = -1f

    fun draw(
        canvas: AndroidCanvas,
        width: Float,
        height: Float,
        bands: List<Float>,
        visibleBands: Int,
        active: Boolean,
        colors: NaviampColors,
        visualizerColors: NaviampPlayerColors,
        albumArtBitmap: Bitmap?,
        timeSeconds: Float,
    ) {
        repeat(AndroidVisualizerShaderBandCount) { index ->
            val sourceIndex = if (AndroidVisualizerShaderBandCount == 1 || bands.isEmpty()) {
                0
            } else {
                ((index / (AndroidVisualizerShaderBandCount - 1f)) * (bands.size - 1)).toInt()
            }
            val target = bands.getOrNull(sourceIndex)?.coerceIn(0f, 1f) ?: 0f
            val rise = if (target > smoothBands[index]) 0.42f else 0.18f
            smoothBands[index] += (target - smoothBands[index]) * rise
            uniformBands[index] = smoothBands[index].coerceIn(0f, 1f)
        }
        updateHistoryTexture(timeSeconds, active)
        val bass = uniformBands.take(8).average().toFloat().coerceIn(0f, 1f)
        val mids = uniformBands.drop(8).take(12).average().toFloat().coerceIn(0f, 1f)
        val highs = uniformBands.drop(20).average().toFloat().coerceIn(0f, 1f)

        runtimeShader.setFloatUniform("iResolution", width, height)
        runtimeShader.setFloatUniform("iTime", timeSeconds)
        runtimeShader.setFloatUniform("iAccent", visualizerColors.accent.red, visualizerColors.accent.green, visualizerColors.accent.blue, visualizerColors.accent.alpha)
        runtimeShader.setFloatUniform("iColorA", visualizerColors.backgroundStart.red, visualizerColors.backgroundStart.green, visualizerColors.backgroundStart.blue, 1f)
        runtimeShader.setFloatUniform("iColorB", visualizerColors.backgroundMid.red, visualizerColors.backgroundMid.green, visualizerColors.backgroundMid.blue, 1f)
        runtimeShader.setFloatUniform("iColorC", visualizerColors.backgroundEnd.red, visualizerColors.backgroundEnd.green, visualizerColors.backgroundEnd.blue, 1f)
        runtimeShader.setFloatUniform("iReadable", colors.primaryText.red, colors.primaryText.green, colors.primaryText.blue, colors.primaryText.alpha)
        runtimeShader.setFloatUniform(
            "iIdle",
            colors.primaryText.red,
            colors.primaryText.green,
            colors.primaryText.blue,
            colors.primaryText.alpha * 0.16f,
        )
        runtimeShader.setFloatUniform("iActive", if (active) 1f else 0f)
        runtimeShader.setFloatUniform("iVisibleBands", visibleBands.toFloat())
        runtimeShader.setFloatUniform("iSourceBands", bands.size.toFloat().coerceAtLeast(1f))
        runtimeShader.setFloatUniform("iEnergy", bass, mids, highs, uniformBands.average().toFloat().coerceIn(0f, 1f))
        runtimeShader.setFloatUniform("iBands", uniformBands)
        val albumShader = albumShaderFor(albumArtBitmap)
        val albumBitmap = albumArtBitmap ?: fallbackAlbumArtBitmap
        runtimeShader.setFloatUniform("iAlbumArtSize", albumBitmap.width.toFloat(), albumBitmap.height.toFloat())
        runtimeShader.setInputShader("iHistory", historyShader)
        runtimeShader.setInputShader("iAlbumArt", albumShader)
        paint.shader = runtimeShader
        canvas.drawRect(0f, 0f, width, height, paint)
    }

    override fun close() {
        historyBitmap.recycle()
        fallbackAlbumArtBitmap.recycle()
    }

    private fun albumShaderFor(bitmap: Bitmap?): Shader {
        if (bitmap == null) return fallbackAlbumArtShader
        if (albumArtBitmap !== bitmap) {
            albumArtBitmap = bitmap
            albumArtShader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        return albumArtShader ?: fallbackAlbumArtShader
    }

    private fun updateHistoryTexture(timeSeconds: Float, active: Boolean) {
        if (!active) return
        if (lastHistoryPushSeconds >= 0f && timeSeconds - lastHistoryPushSeconds < AndroidVisualizerHistoryIntervalSeconds) return
        val changed = uniformBands.indices.any { index ->
            abs(uniformBands[index] - previousHistoryBands[index]) > 0.006f
        }
        if (!changed && lastHistoryPushSeconds >= 0f) return
        lastHistoryPushSeconds = timeSeconds
        repeat(AndroidVisualizerShaderBandCount) { index ->
            previousHistoryBands[index] = uniformBands[index]
        }

        val rowWidth = AndroidVisualizerHistoryColumns
        System.arraycopy(
            historyPixels,
            0,
            historyPixels,
            rowWidth,
            rowWidth * (AndroidVisualizerHistoryRows - 1),
        )
        repeat(AndroidVisualizerHistoryColumns) { column ->
            val sourceIndex = ((column / (AndroidVisualizerHistoryColumns - 1f)) * (AndroidVisualizerShaderBandCount - 1)).toInt()
            val value = (uniformBands[sourceIndex].coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
            historyPixels[column] = android.graphics.Color.argb(255, value, value, value)
        }
        historyBitmap.setPixels(historyPixels, 0, rowWidth, 0, 0, AndroidVisualizerHistoryColumns, AndroidVisualizerHistoryRows)
    }
}

@Composable
private fun AndroidCanvasVisualizerSurface(
    bandsProvider: () -> List<Float>,
    visualizer: NaviampVisualizer,
    visualizerColors: NaviampPlayerColors,
    active: Boolean,
    colors: NaviampColors,
    modifier: Modifier,
) {
    var frameMillis by remember { mutableLongStateOf(0L) }
    LaunchedEffect(active, visualizer) {
        if (!active) {
            frameMillis = 0L
            return@LaunchedEffect
        }
        while (true) {
            withFrameMillis { frameMillis = it }
        }
    }
    Canvas(modifier = modifier) {
        val bands = bandsProvider()
        val visibleBands = minOf(bands.size, (size.width / 6f).toInt().coerceAtLeast(16))
        if (!active || visibleBands <= 0) {
            drawIdleLine(colors)
            return@Canvas
        }

        val samples = sampleBands(bands, visibleBands)
        val energy = samples.average().toFloat().coerceIn(0f, 1f)
        val timeSeconds = frameMillis / 1000f
        when (visualizer) {
            NaviampVisualizer.ReactiveBars -> drawReactiveBars(samples, colors)
            NaviampVisualizer.FluidGradient -> drawFluidGradient(samples, visualizerColors, colors, timeSeconds)
            NaviampVisualizer.AudioSphere -> drawAudioSphere(samples, visualizerColors, colors, timeSeconds)
            NaviampVisualizer.AudioTunnel -> drawAudioTunnel(samples, visualizerColors, colors, timeSeconds)
            NaviampVisualizer.RibbonTrail -> drawRibbonTrail(samples, visualizerColors, colors, timeSeconds)
            NaviampVisualizer.SpectralRidge,
            NaviampVisualizer.FftMountain,
            NaviampVisualizer.FrequencyTerrain -> drawFrequencyTerrain(samples, visualizerColors, colors)
            NaviampVisualizer.ParticleField,
            NaviampVisualizer.ParticleGalaxy -> drawParticleField(samples, visualizerColors, colors, timeSeconds, galaxy = visualizer == NaviampVisualizer.ParticleGalaxy)
            NaviampVisualizer.AlbumArtReactive -> drawAlbumArtReactive(samples, visualizerColors, colors, energy)
            NaviampVisualizer.WaveInterference -> drawWaveInterference(samples, visualizerColors, colors, timeSeconds)
            NaviampVisualizer.VinylGroove -> drawVinylGroove(samples, visualizerColors, colors, timeSeconds)
        }
    }
}

private const val AndroidVisualizerShaderBandCount = 32
private const val AndroidVisualizerHistoryColumns = 32
private const val AndroidVisualizerHistoryRows = 32
private const val AndroidVisualizerHistoryIntervalSeconds = 0.075f

private fun DrawScope.drawIdleLine(colors: NaviampColors) {
    drawLine(
        color = colors.primaryText.copy(alpha = 0.16f),
        start = Offset(0f, size.height / 2f),
        end = Offset(size.width, size.height / 2f),
        strokeWidth = 1.4f,
        cap = StrokeCap.Round,
    )
}

private fun sampleBands(bands: List<Float>, visibleBands: Int): List<Float> =
    List(visibleBands) { index ->
        val sourceIndex = if (visibleBands == 1 || bands.size == 1) {
            0
        } else {
            ((index / (visibleBands - 1f)) * (bands.size - 1)).toInt()
        }
        bands.getOrNull(sourceIndex)?.coerceIn(0f, 1f) ?: 0f
    }

private fun DrawScope.drawReactiveBars(samples: List<Float>, colors: NaviampColors) {
    val step = size.width / samples.size.toFloat()
    val strokeWidth = (step * 0.48f).coerceIn(1.2f, 3.2f)
    val centerY = size.height / 2f
    samples.forEachIndexed { index, amplitude ->
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

private fun DrawScope.drawFluidGradient(
    samples: List<Float>,
    playerColors: NaviampPlayerColors,
    colors: NaviampColors,
    timeSeconds: Float,
) {
    val energy = samples.average().toFloat().coerceIn(0f, 1f)
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(
                playerColors.backgroundStart.copy(alpha = 0.75f),
                playerColors.backgroundMid.copy(alpha = 0.86f),
                playerColors.backgroundEnd.copy(alpha = 0.95f),
            ),
            start = Offset(size.width * (0.2f + 0.1f * sin(timeSeconds)), 0f),
            end = Offset(size.width, size.height),
        ),
    )
    drawWaveInterference(samples, playerColors, colors, timeSeconds + energy)
}

private fun DrawScope.drawAudioSphere(
    samples: List<Float>,
    playerColors: NaviampPlayerColors,
    colors: NaviampColors,
    timeSeconds: Float,
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = minOf(size.width, size.height) * 0.18f
    val energy = samples.average().toFloat().coerceIn(0f, 1f)
    drawCircle(playerColors.accent.copy(alpha = 0.10f + energy * 0.18f), radius * (1.8f + energy), center)
    samples.forEachIndexed { index, amplitude ->
        val angle = (index / samples.size.toFloat()) * (PI.toFloat() * 2f) + timeSeconds * 0.35f
        val inner = radius * (0.78f + amplitude * 0.20f)
        val outer = radius * (1.12f + amplitude * 1.35f)
        drawLine(
            color = colors.primaryText.copy(alpha = 0.18f + amplitude * 0.55f),
            start = center + Offset(cos(angle) * inner, sin(angle) * inner),
            end = center + Offset(cos(angle) * outer, sin(angle) * outer),
            strokeWidth = 2.2f,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawAudioTunnel(
    samples: List<Float>,
    playerColors: NaviampPlayerColors,
    colors: NaviampColors,
    timeSeconds: Float,
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val energy = samples.average().toFloat().coerceIn(0f, 1f)
    repeat(9) { ring ->
        val fraction = (ring + 1) / 9f
        val wobble = samples[(ring * samples.size / 9).coerceIn(samples.indices)]
        val radius = minOf(size.width, size.height) * fraction * (0.08f + energy * 0.015f) + ring * 9f
        drawCircle(
            color = if (ring % 2 == 0) playerColors.accent.copy(alpha = 0.16f + wobble * 0.28f) else colors.primaryText.copy(alpha = 0.08f + wobble * 0.18f),
            radius = radius + sin(timeSeconds * 2f + ring) * 5f,
            center = center,
            style = Stroke(width = 1.4f + wobble * 3f),
        )
    }
}

private fun DrawScope.drawRibbonTrail(
    samples: List<Float>,
    playerColors: NaviampPlayerColors,
    colors: NaviampColors,
    timeSeconds: Float,
) {
    repeat(3) { lane ->
        val path = Path()
        samples.forEachIndexed { index, amplitude ->
            val x = index / (samples.lastIndex).coerceAtLeast(1).toFloat() * size.width
            val baseline = size.height * (0.30f + lane * 0.20f)
            val y = baseline + sin(index * 0.35f + timeSeconds * (1.2f + lane)) * 20f - amplitude * size.height * 0.24f
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = (if (lane == 1) playerColors.accent else colors.primaryText).copy(alpha = 0.22f + lane * 0.10f),
            style = Stroke(width = 2.2f + lane, cap = StrokeCap.Round),
        )
    }
}

private fun DrawScope.drawFrequencyTerrain(
    samples: List<Float>,
    playerColors: NaviampPlayerColors,
    colors: NaviampColors,
) {
    val baseY = size.height * 0.76f
    val path = Path().apply {
        moveTo(0f, size.height)
        samples.forEachIndexed { index, amplitude ->
            val x = index / (samples.lastIndex).coerceAtLeast(1).toFloat() * size.width
            val y = baseY - amplitude * size.height * 0.62f
            lineTo(x, y)
        }
        lineTo(size.width, size.height)
        close()
    }
    drawPath(path, playerColors.accent.copy(alpha = 0.30f))
    drawPath(path, colors.primaryText.copy(alpha = 0.38f), style = Stroke(width = 1.5f))
}

private fun DrawScope.drawParticleField(
    samples: List<Float>,
    playerColors: NaviampPlayerColors,
    colors: NaviampColors,
    timeSeconds: Float,
    galaxy: Boolean,
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    samples.forEachIndexed { index, amplitude ->
        val seed = index * 12.9898f
        val angle = seed + timeSeconds * if (galaxy) 0.18f else 0.05f
        val distance = if (galaxy) {
            minOf(size.width, size.height) * (0.08f + index / samples.size.toFloat() * 0.44f)
        } else {
            minOf(size.width, size.height) * (0.16f + ((seed * 0.37f) % 1f) * 0.42f)
        }
        val x = if (galaxy) center.x + cos(angle) * distance else (index / samples.size.toFloat()) * size.width
        val y = if (galaxy) center.y + sin(angle) * distance * 0.62f else size.height * (0.25f + ((seed * 0.23f) % 1f) * 0.55f)
        drawCircle(
            color = (if (index % 3 == 0) playerColors.accent else colors.primaryText).copy(alpha = 0.20f + amplitude * 0.55f),
            radius = 1.5f + amplitude * 5f,
            center = Offset(x, y),
        )
    }
}

private fun DrawScope.drawAlbumArtReactive(
    samples: List<Float>,
    playerColors: NaviampPlayerColors,
    colors: NaviampColors,
    energy: Float,
) {
    drawRect(playerColors.backgroundMid.copy(alpha = 0.45f + energy * 0.25f))
    val columns = 6
    val rows = 4
    repeat(columns) { column ->
        repeat(rows) { row ->
            val index = (column + row * columns) % samples.size
            val amplitude = samples[index]
            drawRect(
                color = if ((column + row) % 2 == 0) playerColors.accent.copy(alpha = 0.10f + amplitude * 0.25f) else colors.primaryText.copy(alpha = 0.06f + amplitude * 0.14f),
                topLeft = Offset(column * size.width / columns, row * size.height / rows),
                size = androidx.compose.ui.geometry.Size(size.width / columns + 1f, size.height / rows + 1f),
            )
        }
    }
}

private fun DrawScope.drawWaveInterference(
    samples: List<Float>,
    playerColors: NaviampPlayerColors,
    colors: NaviampColors,
    timeSeconds: Float,
) {
    repeat(5) { wave ->
        val path = Path()
        samples.forEachIndexed { index, amplitude ->
            val x = index / (samples.lastIndex).coerceAtLeast(1).toFloat() * size.width
            val phase = timeSeconds * (0.8f + wave * 0.12f) + wave * 1.7f
            val y = size.height / 2f +
                sin(index * 0.23f + phase) * size.height * (0.08f + wave * 0.018f) -
                amplitude * size.height * 0.18f
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path,
            color = (if (wave % 2 == 0) playerColors.accent else colors.primaryText).copy(alpha = 0.12f + wave * 0.035f),
            style = Stroke(width = 1.5f + wave * 0.35f, cap = StrokeCap.Round),
        )
    }
}

private fun DrawScope.drawVinylGroove(
    samples: List<Float>,
    playerColors: NaviampPlayerColors,
    colors: NaviampColors,
    timeSeconds: Float,
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val energy = samples.average().toFloat().coerceIn(0f, 1f)
    val maxRadius = minOf(size.width, size.height) * 0.44f
    repeat(12) { ring ->
        val amplitude = samples[(ring * samples.size / 12).coerceIn(samples.indices)]
        drawArc(
            color = (if (ring % 3 == 0) playerColors.accent else colors.primaryText).copy(alpha = 0.10f + amplitude * 0.22f),
            startAngle = timeSeconds * 25f + ring * 18f,
            sweepAngle = 240f + energy * 90f,
            useCenter = false,
            topLeft = Offset(center.x - maxRadius * (ring + 1) / 12f, center.y - maxRadius * (ring + 1) / 12f),
            size = androidx.compose.ui.geometry.Size(maxRadius * 2f * (ring + 1) / 12f, maxRadius * 2f * (ring + 1) / 12f),
            style = Stroke(width = 1.2f + amplitude * 2.4f, cap = StrokeCap.Round),
        )
    }
    drawCircle(playerColors.accent.copy(alpha = 0.20f + energy * 0.28f), radius = maxRadius * 0.16f, center = center)
}
