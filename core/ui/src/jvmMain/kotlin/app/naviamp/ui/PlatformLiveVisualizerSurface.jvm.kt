package app.naviamp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Shader

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
    var frameMillis by remember { mutableLongStateOf(0L) }
    var albumArtImage by remember(coverArtUrl) { androidx.compose.runtime.mutableStateOf<Image?>(null) }
    LaunchedEffect(active, visualizer) {
        if (!active) {
            frameMillis = 0L
            return@LaunchedEffect
        }
        // Drive shader animation at the display frame clock. FFT sampling is intentionally
        // throttled upstream so visual smoothness does not require 60 BASS reads per second.
        while (true) {
            withFrameMillis { frameMillis = it }
        }
    }
    LaunchedEffect(coverArtUrl, visualizer) {
        albumArtImage = if (coverArtUrl != null && visualizer == NaviampVisualizer.AlbumArtReactive) {
            runCatching { jvmPlatformCoverArtShaderImage(coverArtUrl) }.getOrNull()
        } else {
            null
        }
    }

    val effect = remember(visualizer) {
        runCatching { RuntimeEffect.makeForShader(visualizer.shaderSource) }.getOrNull()
    }
    if (effect == null) {
        CanvasVisualizerSurface(bandsProvider, active, colors, modifier)
        return
    }
    val renderer = remember(effect, visualizer) { ShaderVisualizerRenderer(effect, visualizer) }
    DisposableEffect(renderer) {
        onDispose {
            renderer.close()
            effect.close()
        }
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
                albumArtImage = albumArtImage,
                timeSeconds = frameMillis / 1000f,
            )
        }
    }
}

private class ShaderVisualizerRenderer(
    effect: RuntimeEffect,
    private val visualizer: NaviampVisualizer,
) : AutoCloseable {
    private val builder = RuntimeShaderBuilder(effect)
    private val paint = Paint()
    private val uniformBands = FloatArray(VisualizerShaderBandCount)
    private val smoothBands = FloatArray(VisualizerShaderBandCount)
    private val historyPixels = ByteArray(VisualizerHistoryColumns * VisualizerHistoryRows * 4)
    private val previousHistoryBands = FloatArray(VisualizerShaderBandCount)
    private var shader: Shader? = null
    private var historyShader: Shader? = null
    private var historyImage: Image? = null
    private var lastHistoryPushSeconds = -1f

    fun draw(
        canvas: org.jetbrains.skia.Canvas,
        width: Float,
        height: Float,
        bands: List<Float>,
        visibleBands: Int,
        active: Boolean,
        colors: NaviampColors,
        visualizerColors: NaviampPlayerColors,
        albumArtImage: Image?,
        timeSeconds: Float,
    ) {
        repeat(VisualizerShaderBandCount) { index ->
            val sourceIndex = if (VisualizerShaderBandCount == 1 || bands.isEmpty()) {
                0
            } else {
                ((index / (VisualizerShaderBandCount - 1f)) * (bands.size - 1)).toInt()
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

        builder.uniform("iResolution", width, height)
        builder.uniform("iTime", timeSeconds)
        builder.uniform("iAccent", visualizerColors.accent.red, visualizerColors.accent.green, visualizerColors.accent.blue, visualizerColors.accent.alpha)
        builder.uniform("iColorA", visualizerColors.backgroundStart.red, visualizerColors.backgroundStart.green, visualizerColors.backgroundStart.blue, 1f)
        builder.uniform("iColorB", visualizerColors.backgroundMid.red, visualizerColors.backgroundMid.green, visualizerColors.backgroundMid.blue, 1f)
        builder.uniform("iColorC", visualizerColors.backgroundEnd.red, visualizerColors.backgroundEnd.green, visualizerColors.backgroundEnd.blue, 1f)
        builder.uniform("iReadable", colors.primaryText.red, colors.primaryText.green, colors.primaryText.blue, colors.primaryText.alpha)
        builder.uniform(
            "iIdle",
            colors.primaryText.red,
            colors.primaryText.green,
            colors.primaryText.blue,
            colors.primaryText.alpha * 0.16f,
        )
        builder.uniform("iActive", if (active) 1f else 0f)
        builder.uniform("iVisibleBands", visibleBands.toFloat())
        builder.uniform("iSourceBands", bands.size.toFloat().coerceAtLeast(1f))
        builder.uniform("iEnergy", bass, mids, highs, uniformBands.average().toFloat().coerceIn(0f, 1f))
        builder.uniform("iBands", uniformBands)
        builder.uniform(
            "iAlbumArtSize",
            (albumArtImage?.width ?: 1).toFloat(),
            (albumArtImage?.height ?: 1).toFloat(),
        )
        if (visualizer == NaviampVisualizer.SpectralRidge || visualizer == NaviampVisualizer.FftMountain) {
            historyShader?.let { builder.child("iHistory", it) }
        }
        var temporaryAlbumShader: Shader? = null
        if (visualizer == NaviampVisualizer.AlbumArtReactive) {
            val albumShader = (albumArtImage ?: fallbackAlbumArtImage).makeShader(
                FilterTileMode.CLAMP,
                FilterTileMode.CLAMP,
                SamplingMode.LINEAR,
                null,
            )
            temporaryAlbumShader = albumShader
            builder.child("iAlbumArt", albumShader)
        }

        shader?.close()
        shader = builder.makeShader()
        temporaryAlbumShader?.close()
        paint.shader = shader
        canvas.drawRect(Rect.makeWH(width, height), paint)
    }

    override fun close() {
        shader?.close()
        shader = null
        historyShader?.close()
        historyShader = null
        historyImage?.close()
        historyImage = null
        paint.close()
        builder.close()
    }

    private fun updateHistoryTexture(timeSeconds: Float, active: Boolean) {
        if (!active || (visualizer != NaviampVisualizer.SpectralRidge && visualizer != NaviampVisualizer.FftMountain)) return
        if (lastHistoryPushSeconds >= 0f && timeSeconds - lastHistoryPushSeconds < VisualizerHistoryIntervalSeconds) return
        val changed = uniformBands.indices.any { index ->
            kotlin.math.abs(uniformBands[index] - previousHistoryBands[index]) > 0.006f
        }
        if (!changed && lastHistoryPushSeconds >= 0f) return
        lastHistoryPushSeconds = timeSeconds
        repeat(VisualizerShaderBandCount) { index ->
            previousHistoryBands[index] = uniformBands[index]
        }

        val rowBytes = VisualizerHistoryColumns * 4
        historyPixels.copyInto(
            destination = historyPixels,
            destinationOffset = rowBytes,
            startIndex = 0,
            endIndex = rowBytes * (VisualizerHistoryRows - 1),
        )
        repeat(VisualizerHistoryColumns) { column ->
            val sourceIndex = ((column / (VisualizerHistoryColumns - 1f)) * (VisualizerShaderBandCount - 1)).toInt()
            val amplitude = uniformBands[sourceIndex].coerceIn(0f, 1f)
            val value = (amplitude * 255f).toInt().coerceIn(0, 255).toByte()
            val pixel = column * 4
            historyPixels[pixel] = value
            historyPixels[pixel + 1] = value
            historyPixels[pixel + 2] = value
            historyPixels[pixel + 3] = 255.toByte()
        }

        val nextImage = Image.makeRaster(
            ImageInfo(VisualizerHistoryColumns, VisualizerHistoryRows, ColorType.RGBA_8888, ColorAlphaType.OPAQUE),
            historyPixels,
            rowBytes,
        )
        val nextShader = nextImage.makeShader(
            FilterTileMode.CLAMP,
            FilterTileMode.CLAMP,
            SamplingMode.LINEAR,
            null,
        )
        historyShader?.close()
        historyImage?.close()
        historyImage = nextImage
        historyShader = nextShader
    }

    private companion object {
        private val fallbackAlbumArtImage: Image by lazy {
            Image.makeRaster(
                ImageInfo(1, 1, ColorType.RGBA_8888, ColorAlphaType.OPAQUE),
                byteArrayOf(255.toByte(), 255.toByte(), 255.toByte(), 255.toByte()),
                4,
            )
        }
    }
}

@Composable
private fun CanvasVisualizerSurface(
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

private const val VisualizerShaderBandCount = 32
private const val VisualizerHistoryColumns = 32
private const val VisualizerHistoryRows = 32
private const val VisualizerHistoryIntervalSeconds = 0.075f

