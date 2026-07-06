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
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.awt.Color as AwtColor
import java.util.Locale
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
    tempoBpm: Int?,
    colors: NaviampColors,
    lyricStage: LyricMirrorTunnelStage,
    modifier: Modifier,
) {
    var frameMillis by remember { mutableLongStateOf(0L) }
    var albumArtImage by remember(coverArtUrl) { androidx.compose.runtime.mutableStateOf<Image?>(null) }
    val renderPolicy = remember(visualizer) {
        visualizerRenderPolicy(visualizer, jvmVisualizerRenderTier())
    }
    val rendererMode = remember(visualizer) {
        selectedVisualizerRendererMode(
            visualizer = visualizer,
            nativeRendererAvailable = jvmNativeVisualizerAvailable(visualizer),
        )
    }
    LaunchedEffect(active, visualizer, renderPolicy.targetFrameIntervalMillis) {
        if (!active) {
            frameMillis = 0L
            return@LaunchedEffect
        }
        // Drive shader animation at the display frame clock. FFT sampling is intentionally
        // throttled upstream so visual smoothness does not require 60 BASS reads per second.
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
    val primaryLyricLine = lyricStage.primaryLineForNativeMask()
    LaunchedEffect(coverArtUrl, visualizer, primaryLyricLine?.text) {
        albumArtImage = when {
            visualizer == NaviampVisualizer.LyricMirrorTunnel ->
                jvmLyricMaskShaderImage(primaryLyricLine?.text.orEmpty())
            coverArtUrl != null && (visualizer.usesAlbumArtShader || visualizer.nativeShaderDefinition != null) ->
                runCatching { jvmPlatformCoverArtShaderImage(coverArtUrl) }.getOrNull()
            else -> null
        }
    }

    if (rendererMode == VisualizerRendererMode.NativeGpu) {
        NativeDesktopVisualizerSurface(
            bandsProvider = bandsProvider,
            visualizer = visualizer,
            renderPolicy = renderPolicy,
            active = active,
            visualizerColors = visualizerColors,
            colors = colors,
            albumArtImage = albumArtImage,
            frameMillis = frameMillis,
            lyricLine = primaryLyricLine,
            tempoBpm = tempoBpm,
            modifier = modifier,
        )
        return
    }
    if (rendererMode == VisualizerRendererMode.Canvas) {
        if (visualizer == NaviampVisualizer.LyricMirrorTunnel) {
            LyricMirrorTunnelVisualizerSurface(
                bandsProvider = bandsProvider,
                visualizerColors = visualizerColors,
                active = active,
                colors = colors,
                lyricStage = lyricStage,
                renderPolicy = renderPolicy,
                modifier = modifier,
            )
        } else {
            SpectrumBarsVisualizerSurface(
                bandsProvider = bandsProvider,
                visualizerColors = visualizerColors,
                active = active,
                colors = colors,
                renderPolicy = renderPolicy,
                modifier = modifier,
            )
        }
        return
    }

    val effect = remember(visualizer) {
        runCatching { RuntimeEffect.makeForShader(visualizer.shaderSource) }.getOrNull()
    }
    if (effect == null) {
        CanvasVisualizerSurface(bandsProvider, active, colors, renderPolicy, modifier)
        return
    }
    val renderer = remember(effect, visualizer, renderPolicy) {
        ShaderVisualizerRenderer(effect, visualizer, renderPolicy)
    }
    DisposableEffect(renderer) {
        onDispose {
            renderer.close()
            effect.close()
        }
    }

    Canvas(modifier = modifier) {
        val drawStartedNanos = System.nanoTime()
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
                tempoBpm = tempoBpm,
            )
        }
        renderer.recordDrawNanos(System.nanoTime() - drawStartedNanos, active)
    }
}

@Composable
private fun NativeDesktopVisualizerSurface(
    bandsProvider: () -> List<Float>,
    visualizer: NaviampVisualizer,
    renderPolicy: VisualizerRenderPolicy,
    active: Boolean,
    visualizerColors: NaviampPlayerColors,
    colors: NaviampColors,
    albumArtImage: Image?,
    frameMillis: Long,
    lyricLine: LyricMirrorTunnelLine?,
    tempoBpm: Int?,
    modifier: Modifier,
) {
    val host = remember(visualizer, renderPolicy) { createNativeDesktopVisualizerHost(visualizer, renderPolicy) }
    val fallbackEffect = remember(visualizer) {
        runCatching { RuntimeEffect.makeForShader(visualizer.shaderSource) }.getOrNull()
    }
    val fallbackRenderer = remember(fallbackEffect, visualizer, renderPolicy) {
        fallbackEffect?.let { ShaderVisualizerRenderer(it, visualizer, renderPolicy) }
    }
    DisposableEffect(host) {
        onDispose { host.close() }
    }
    DisposableEffect(fallbackRenderer, fallbackEffect) {
        onDispose {
            fallbackRenderer?.close()
            fallbackEffect?.close()
        }
    }
    LaunchedEffect(host, albumArtImage) {
        host.updateAlbumArt(albumArtImage)
    }

    Canvas(modifier = modifier) {
        val bands = bandsProvider()
        val visibleBands = minOf(bands.size, (size.width / 6f).toInt().coerceAtLeast(16))
        val nativePixelScale = nativeDesktopVisualizerPixelScale(density)
        val renderWidth = (size.width * nativePixelScale).toInt().coerceAtLeast(1)
        val renderHeight = (size.height * nativePixelScale).toInt().coerceAtLeast(1)
        val image = host.renderImage(
            width = renderWidth,
            height = renderHeight,
            bands = bands,
            active = active,
            visualizerColors = visualizerColors,
            colors = colors,
            timeSeconds = frameMillis / 1000f,
            tempoBpm = tempoBpm,
            lyricProgress = lyricLine?.progressToNext ?: 0f,
        )
        if (image != null) {
            drawIntoCanvas { canvas ->
                val paint = Paint()
                canvas.nativeCanvas.drawImageRect(
                    image,
                    Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
                    Rect.makeWH(size.width, size.height),
                    SamplingMode.LINEAR,
                    paint,
                    true,
                )
                paint.close()
            }
            image.close()
        } else {
            val fallback = fallbackRenderer
            if (fallback != null && visibleBands > 0 && size.width > 0f && size.height > 0f) {
                drawIntoCanvas { canvas ->
                    fallback.draw(
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
                        tempoBpm = tempoBpm,
                    )
                }
            } else {
                drawLine(
                    color = colors.primaryText.copy(alpha = 0.16f),
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = 1.4f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

private interface NativeDesktopVisualizerHost : AutoCloseable {
    fun updateAlbumArt(image: Image?)

    fun renderImage(
        width: Int,
        height: Int,
        bands: List<Float>,
        active: Boolean,
        visualizerColors: NaviampPlayerColors,
        colors: NaviampColors,
        timeSeconds: Float,
        tempoBpm: Int?,
        lyricProgress: Float,
    ): Image?
}

private fun createNativeDesktopVisualizerHost(
    visualizer: NaviampVisualizer,
    renderPolicy: VisualizerRenderPolicy,
): NativeDesktopVisualizerHost =
    if (jvmPlatformLabel() == "windows") {
        NativeOpenGlDesktopVisualizerHost(NativeOpenGlVisualizerHost(visualizer, renderPolicy))
    } else {
        NativeMetalDesktopVisualizerHost(NativeMetalVisualizerHost(visualizer, renderPolicy))
    }

private class NativeMetalDesktopVisualizerHost(
    private val host: NativeMetalVisualizerHost,
) : NativeDesktopVisualizerHost {
    override fun updateAlbumArt(image: Image?) = host.updateAlbumArt(image)

    override fun renderImage(
        width: Int,
        height: Int,
        bands: List<Float>,
        active: Boolean,
        visualizerColors: NaviampPlayerColors,
        colors: NaviampColors,
        timeSeconds: Float,
        tempoBpm: Int?,
        lyricProgress: Float,
    ): Image? = host.renderImage(width, height, bands, active, visualizerColors, colors, timeSeconds, tempoBpm)

    override fun close() = host.close()
}

private class NativeOpenGlDesktopVisualizerHost(
    private val host: NativeOpenGlVisualizerHost,
) : NativeDesktopVisualizerHost {
    override fun updateAlbumArt(image: Image?) = host.updateAlbumArt(image)

    override fun renderImage(
        width: Int,
        height: Int,
        bands: List<Float>,
        active: Boolean,
        visualizerColors: NaviampPlayerColors,
        colors: NaviampColors,
        timeSeconds: Float,
        tempoBpm: Int?,
        lyricProgress: Float,
    ): Image? = host.renderImage(width, height, bands, active, visualizerColors, colors, timeSeconds, tempoBpm, lyricProgress)

    override fun close() = host.close()
}

private class ShaderVisualizerRenderer(
    effect: RuntimeEffect,
    private val visualizer: NaviampVisualizer,
    private val renderPolicy: VisualizerRenderPolicy,
) : AutoCloseable {
    private val builder = RuntimeShaderBuilder(effect)
    private val paint = Paint()
    private val uniformBands = FloatArray(VisualizerFrameBandCount)
    private val smoothBands = FloatArray(VisualizerFrameBandCount)
    private val historyPixels = ByteArray(VisualizerHistoryColumns * VisualizerHistoryRows * 4)
    private val previousHistoryBands = FloatArray(VisualizerFrameBandCount)
    private var shader: Shader? = null
    private var historyShader: Shader? = null
    private var historyImage: Image? = null
    private var lastHistoryPushSeconds = -1f
    private val perfLogger = JvmVisualizerPerfLogger(
        renderer = "shader",
        visualizer = visualizer,
        renderPolicy = renderPolicy,
    )

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
        tempoBpm: Int?,
    ) {
        smoothVisualizerBands(bands, smoothBands, uniformBands)
        updateHistoryTexture(timeSeconds, active)
        val frameInput = buildVisualizerFrameInput(
            width = width,
            height = height,
            bands = bands,
            visibleBands = visibleBands,
            active = active,
            timeSeconds = timeSeconds,
            tempoBpm = tempoBpm,
            uniformBands = uniformBands,
        )

        builder.uniform("iResolution", frameInput.width, frameInput.height)
        builder.uniform("iTime", frameInput.timeSeconds)
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
        builder.uniform("iActive", if (frameInput.active) 1f else 0f)
        builder.uniform("iVisibleBands", frameInput.visibleBands)
        builder.uniform("iSourceBands", frameInput.sourceBands)
        builder.uniform(
            "iEnergy",
            frameInput.energy.bass,
            frameInput.energy.mids,
            frameInput.energy.highs,
            frameInput.energy.energy,
        )
        builder.uniform("iTempo", frameInput.tempoBpm)
        builder.uniform("iBands", frameInput.bands)
        builder.uniform(
            "iAlbumArtSize",
            (albumArtImage?.width ?: 1).toFloat(),
            (albumArtImage?.height ?: 1).toFloat(),
        )
        if (visualizer.usesHistoryShader) {
            historyShader?.let { builder.child("iHistory", it) }
        }
        var temporaryAlbumShader: Shader? = null
        if (visualizer.usesAlbumArtShader) {
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

    fun recordDrawNanos(drawNanos: Long, active: Boolean) {
        perfLogger.record(drawNanos, active)
    }

    private fun updateHistoryTexture(timeSeconds: Float, active: Boolean) {
        if (!active || !visualizer.usesHistoryShader) return
        if (lastHistoryPushSeconds >= 0f && timeSeconds - lastHistoryPushSeconds < renderPolicy.historyIntervalSeconds) return
        val changed = uniformBands.indices.any { index ->
            kotlin.math.abs(uniformBands[index] - previousHistoryBands[index]) > 0.006f
        }
        if (!changed && lastHistoryPushSeconds >= 0f) return
        lastHistoryPushSeconds = timeSeconds
        repeat(VisualizerFrameBandCount) { index ->
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
            val sourceIndex = ((column / (VisualizerHistoryColumns - 1f)) * (VisualizerFrameBandCount - 1)).toInt()
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
            visualizer.historySamplingMode,
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
    renderPolicy: VisualizerRenderPolicy,
    modifier: Modifier,
) {
    Canvas(modifier = modifier) {
        val bands = bandsProvider()
        val visibleBands = minOf(
            bands.size,
            renderPolicy.maxCanvasSamples,
            (size.width / 6f).toInt().coerceAtLeast(16),
        )
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

private const val VisualizerPerfLogIntervalMillis = 5_000L
private const val VisualizerPerfLogProperty = "naviamp.visualizer.perf"
private const val VisualizerProfileProperty = "naviamp.visualizer.profile"
private const val MacosMetalVisualizerProperty = "naviamp.visualizer.macosMetal"
private const val WindowsOpenGlVisualizerProperty = "naviamp.visualizer.windowsOpenGl"
private val NaviampVisualizer.usesHistoryShader: Boolean
    get() = this == NaviampVisualizer.SpectralRidge ||
        this == NaviampVisualizer.FftMountain ||
        this == NaviampVisualizer.PixelRidge ||
        this == NaviampVisualizer.PixelMountain

private val NaviampVisualizer.historySamplingMode: SamplingMode
    get() = when (this) {
        NaviampVisualizer.PixelRidge,
        NaviampVisualizer.PixelMountain -> SamplingMode.DEFAULT
        else -> SamplingMode.LINEAR
    }

private class JvmVisualizerPerfLogger(
    private val renderer: String,
    private val visualizer: NaviampVisualizer,
    private val renderPolicy: VisualizerRenderPolicy,
) {
    private val enabled = System.getProperty(VisualizerPerfLogProperty).equals("true", ignoreCase = true) ||
        System.getenv("NAVIAMP_VISUALIZER_PERF").equals("true", ignoreCase = true)
    private var windowStartedMillis = System.currentTimeMillis()
    private var frameCount = 0
    private var drawNanosTotal = 0L
    private var maxDrawNanos = 0L

    fun record(drawNanos: Long, active: Boolean) {
        if (!enabled) return
        if (!active) {
            reset()
            return
        }
        frameCount += 1
        drawNanosTotal += drawNanos
        maxDrawNanos = maxOf(maxDrawNanos, drawNanos)

        val nowMillis = System.currentTimeMillis()
        val elapsedMillis = nowMillis - windowStartedMillis
        if (elapsedMillis < VisualizerPerfLogIntervalMillis || frameCount <= 0) return

        val averageDrawMillis = drawNanosTotal / frameCount / 1_000_000.0
        val maxDrawMillis = maxDrawNanos / 1_000_000.0
        val fps = frameCount * 1_000.0 / elapsedMillis.toDouble()
        println(
            "NaviampVisualizerPerf ${visualizer.name} renderer=$renderer " +
                "platform=jvm-${jvmPlatformLabel()} " +
                "profile=${renderPolicy.tier.label} targetFps=${renderPolicy.targetFps} " +
                "fps=${fps.formatVisualizerMetric()} " +
                "avgDrawMs=${averageDrawMillis.formatVisualizerMetric()} " +
                "maxDrawMs=${maxDrawMillis.formatVisualizerMetric()} frames=$frameCount",
        )
        reset(nowMillis)
    }

    private fun reset(startMillis: Long = System.currentTimeMillis()) {
        windowStartedMillis = startMillis
        frameCount = 0
        drawNanosTotal = 0L
        maxDrawNanos = 0L
    }
}

private fun Double.formatVisualizerMetric(): String =
    String.format(Locale.US, "%.2f", this)

private fun jvmVisualizerRenderTier(): VisualizerRenderTier {
    val override = System.getProperty(VisualizerProfileProperty)
        ?: System.getenv("NAVIAMP_VISUALIZER_PROFILE")
    return when (override?.lowercase(Locale.US)) {
        "full" -> VisualizerRenderTier.Full
        "balanced" -> VisualizerRenderTier.Balanced
        "constrained" -> VisualizerRenderTier.Constrained
        else -> if (jvmPlatformLabel() == "macos") {
            VisualizerRenderTier.Full
        } else {
            VisualizerRenderTier.Balanced
        }
    }
}

private fun jvmPlatformLabel(): String {
    val osName = System.getProperty("os.name").lowercase(Locale.US)
    return when {
        "mac" in osName -> "macos"
        "win" in osName -> "windows"
        "linux" in osName -> "linux"
        else -> osName.replace(' ', '-')
    }
}

private fun nativeDesktopVisualizerPixelScale(density: Float): Float =
    if (jvmPlatformLabel() == "windows") {
        val override = System.getProperty("naviamp.visualizer.windowsPixelScale")
            ?: System.getenv("NAVIAMP_VISUALIZER_WINDOWS_PIXEL_SCALE")
        override
            ?.toFloatOrNull()
            ?.coerceIn(1f, 4f)
            ?: maxOf(density, 2f)
    } else {
        1f
    }

private fun jvmLyricMaskShaderImage(text: String): Image {
    val width = 768
    val height = 256
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    graphics.color = AwtColor(0, 0, 0, 0)
    graphics.fillRect(0, 0, width, height)
    graphics.color = AwtColor.WHITE
    val layout = selectJvmLyricMaskLayout(graphics, text.ifBlank { " " }, width - 92, height - 34)
    graphics.font = layout.font
    val lines = layout.lines
    val lineHeight = graphics.fontMetrics.height
    val totalHeight = lineHeight * lines.size
    var y = (height - totalHeight) / 2 + graphics.fontMetrics.ascent
    lines.forEach { line ->
        val x = (width - graphics.fontMetrics.stringWidth(line)) / 2
        graphics.drawString(line, x.coerceAtLeast(8), y)
        y += lineHeight
    }
    graphics.dispose()

    val rgba = ByteArray(width * height * 4)
    var offset = 0
    for (row in 0 until height) {
        for (column in 0 until width) {
            val argb = image.getRGB(column, row)
            val alpha = (argb ushr 24).toByte()
            rgba[offset] = 255.toByte()
            rgba[offset + 1] = 255.toByte()
            rgba[offset + 2] = 255.toByte()
            rgba[offset + 3] = alpha
            offset += 4
        }
    }
    return Image.makeRaster(
        ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.PREMUL),
        rgba,
        width * 4,
    )
}

private data class JvmLyricMaskLayout(
    val font: Font,
    val lines: List<String>,
)

private fun selectJvmLyricMaskLayout(
    graphics: java.awt.Graphics2D,
    text: String,
    maxWidth: Int,
    maxHeight: Int,
): JvmLyricMaskLayout {
    for (fontSize in 74 downTo 34 step 2) {
        val font = Font(Font.SANS_SERIF, Font.BOLD, fontSize)
        graphics.font = font
        val lines = text.wrapLyricMaskLines(graphics.fontMetrics, maxWidth).take(3)
        val fitsWidth = lines.all { graphics.fontMetrics.stringWidth(it) <= maxWidth }
        val fitsHeight = graphics.fontMetrics.height * lines.size <= maxHeight
        if (fitsWidth && fitsHeight) {
            return JvmLyricMaskLayout(font, lines)
        }
    }
    val fallback = Font(Font.SANS_SERIF, Font.BOLD, 34)
    graphics.font = fallback
    return JvmLyricMaskLayout(fallback, text.wrapLyricMaskLines(graphics.fontMetrics, maxWidth).take(3))
}

private fun String.wrapLyricMaskLines(
    fontMetrics: java.awt.FontMetrics,
    maxWidth: Int,
): List<String> {
    val words = trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (words.isEmpty()) return listOf(" ")
    val lines = mutableListOf<String>()
    var current = ""
    words.forEach { word ->
        val candidate = if (current.isBlank()) word else "$current $word"
        if (fontMetrics.stringWidth(candidate) <= maxWidth || current.isBlank()) {
            current = candidate
        } else {
            lines += current
            current = word
        }
    }
    if (current.isNotBlank()) lines += current
    return lines
}

internal fun jvmNativeVisualizerAvailable(
    visualizer: NaviampVisualizer,
    osName: String = System.getProperty("os.name"),
    macosMetalEnabledProperty: String? = System.getProperty(MacosMetalVisualizerProperty),
    windowsOpenGlEnabledProperty: String? = System.getProperty(WindowsOpenGlVisualizerProperty),
    metalLibraryAvailable: Boolean? = null,
    openGlLibraryAvailable: Boolean? = null,
): Boolean {
    val normalizedOs = osName.lowercase(Locale.US)
    return when {
        normalizedOs.contains("mac") -> jvmNativeMetalVisualizerAvailable(
            visualizer = visualizer,
            osName = osName,
            enabledProperty = macosMetalEnabledProperty,
            libraryAvailable = metalLibraryAvailable ?: NativeMetalVisualizerHost.libraryAvailable(),
        )
        normalizedOs.contains("win") -> jvmNativeOpenGlVisualizerAvailable(
            visualizer = visualizer,
            osName = osName,
            enabledProperty = windowsOpenGlEnabledProperty,
            libraryAvailable = openGlLibraryAvailable ?: NativeOpenGlVisualizerHost.libraryAvailable(),
        )
        else -> false
    }
}

internal fun jvmNativeMetalVisualizerAvailable(
    visualizer: NaviampVisualizer,
    osName: String = System.getProperty("os.name"),
    enabledProperty: String? = System.getProperty(MacosMetalVisualizerProperty),
    libraryAvailable: Boolean = NativeMetalVisualizerHost.libraryAvailable(),
): Boolean {
    val enabled = enabledProperty.equals("true", ignoreCase = true) ||
        visualizer == NaviampVisualizer.LyricMirrorTunnel
    val macos = osName.lowercase(Locale.US).contains("mac")
    val shaderDefinition = visualizer.nativeShaderDefinition
    val shaderTranslates = shaderDefinition?.let {
        runCatching {
            it.fragmentSourceForDialect(NativeShaderDialect.MetalShadingLanguage)
        }.isSuccess
    } ?: false
    val available = enabled && macos && libraryAvailable && shaderDefinition != null && shaderTranslates

    logNativeMetalAvailability(
        visualizer = visualizer,
        enabled = enabled,
        osName = osName,
        macos = macos,
        libraryAvailable = libraryAvailable,
        shaderDefinition = shaderDefinition != null,
        shaderTranslates = shaderTranslates,
        available = available,
    )
    return available
}

internal fun jvmNativeOpenGlVisualizerAvailable(
    visualizer: NaviampVisualizer,
    osName: String = System.getProperty("os.name"),
    enabledProperty: String? = System.getProperty(WindowsOpenGlVisualizerProperty),
    libraryAvailable: Boolean = NativeOpenGlVisualizerHost.libraryAvailable(),
): Boolean {
    val enabled = enabledProperty.equals("true", ignoreCase = true)
    val windows = osName.lowercase(Locale.US).contains("win")
    val shaderDefinition = visualizer.nativeShaderDefinition
    val shaderTranslates = shaderDefinition?.let {
        runCatching {
            it.fragmentSourceForDialect(NativeShaderDialect.DesktopGlsl330)
        }.isSuccess
    } ?: false
    val available = enabled && windows && libraryAvailable && shaderDefinition != null && shaderTranslates

    logNativeOpenGlAvailability(
        visualizer = visualizer,
        enabled = enabled,
        osName = osName,
        windows = windows,
        libraryAvailable = libraryAvailable,
        shaderDefinition = shaderDefinition != null,
        shaderTranslates = shaderTranslates,
        available = available,
    )
    return available
}

private val nativeOpenGlAvailabilityLogs = mutableSetOf<String>()

private fun logNativeOpenGlAvailability(
    visualizer: NaviampVisualizer,
    enabled: Boolean,
    osName: String,
    windows: Boolean,
    libraryAvailable: Boolean,
    shaderDefinition: Boolean,
    shaderTranslates: Boolean,
    available: Boolean,
) {
    if (!nativeOpenGlDiagnosticsEnabled()) return
    val key = "${visualizer.name}:$enabled:$windows:$libraryAvailable:$shaderDefinition:$shaderTranslates:$available"
    if (!nativeOpenGlAvailabilityLogs.add(key)) return
    val libraryError = NativeOpenGlVisualizerHost.libraryLoadFailureMessage()
        ?.let { " libraryError=$it" }
        .orEmpty()
    val libraryDiagnostics = if (!libraryAvailable) {
        " ${NativeOpenGlVisualizerHost.libraryDiagnostics()}"
    } else {
        ""
    }
    println(
        "NaviampVisualizerOpenGl availability visualizer=${visualizer.name} " +
            "available=$available enabled=$enabled osName=$osName windows=$windows " +
            "libraryAvailable=$libraryAvailable shaderDefinition=$shaderDefinition " +
            "shaderTranslates=$shaderTranslates$libraryError$libraryDiagnostics",
    )
}

private val nativeMetalAvailabilityLogs = mutableSetOf<String>()

private fun logNativeMetalAvailability(
    visualizer: NaviampVisualizer,
    enabled: Boolean,
    osName: String,
    macos: Boolean,
    libraryAvailable: Boolean,
    shaderDefinition: Boolean,
    shaderTranslates: Boolean,
    available: Boolean,
) {
    if (!nativeMetalDiagnosticsEnabled()) return
    val key = "${visualizer.name}:$enabled:$macos:$libraryAvailable:$shaderDefinition:$shaderTranslates:$available"
    if (!nativeMetalAvailabilityLogs.add(key)) return
    val libraryError = NativeMetalVisualizerHost.libraryLoadFailureMessage()
        ?.let { " libraryError=$it" }
        .orEmpty()
    val libraryDiagnostics = if (!libraryAvailable) {
        " ${NativeMetalVisualizerHost.libraryDiagnostics()}"
    } else {
        ""
    }
    println(
        "NaviampVisualizerMetal availability visualizer=${visualizer.name} " +
            "available=$available enabled=$enabled osName=$osName macos=$macos " +
            "libraryAvailable=$libraryAvailable shaderDefinition=$shaderDefinition " +
            "shaderTranslates=$shaderTranslates$libraryError$libraryDiagnostics",
    )
}
