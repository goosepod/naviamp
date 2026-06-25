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
            nativeRendererAvailable = jvmNativeMetalVisualizerAvailable(visualizer),
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
    LaunchedEffect(coverArtUrl, visualizer) {
        albumArtImage = if (coverArtUrl != null &&
            (visualizer.usesAlbumArtShader || visualizer.nativeShaderDefinition != null)
        ) {
            runCatching { jvmPlatformCoverArtShaderImage(coverArtUrl) }.getOrNull()
        } else {
            null
        }
    }

    if (rendererMode == VisualizerRendererMode.NativeGpu) {
        NativeMetalVisualizerSurface(
            bandsProvider = bandsProvider,
            visualizer = visualizer,
            renderPolicy = renderPolicy,
            active = active,
            visualizerColors = visualizerColors,
            colors = colors,
            albumArtImage = albumArtImage,
            frameMillis = frameMillis,
            tempoBpm = tempoBpm,
            modifier = modifier,
        )
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
private fun NativeMetalVisualizerSurface(
    bandsProvider: () -> List<Float>,
    visualizer: NaviampVisualizer,
    renderPolicy: VisualizerRenderPolicy,
    active: Boolean,
    visualizerColors: NaviampPlayerColors,
    colors: NaviampColors,
    albumArtImage: Image?,
    frameMillis: Long,
    tempoBpm: Int?,
    modifier: Modifier,
) {
    val host = remember(visualizer, renderPolicy) {
        NativeMetalVisualizerHost(visualizer, renderPolicy)
    }
    DisposableEffect(host) {
        onDispose { host.close() }
    }
    LaunchedEffect(host, albumArtImage) {
        host.updateAlbumArt(albumArtImage)
    }

    Canvas(modifier = modifier) {
        val image = host.renderImage(
            width = size.width.toInt(),
            height = size.height.toInt(),
            bands = bandsProvider(),
            active = active,
            visualizerColors = visualizerColors,
            colors = colors,
            timeSeconds = frameMillis / 1000f,
            tempoBpm = tempoBpm,
        )
        if (image != null) {
            drawIntoCanvas { canvas ->
                val paint = Paint()
                canvas.nativeCanvas.drawImageRect(
                    image,
                    Rect.makeWH(size.width, size.height),
                    paint,
                )
                paint.close()
            }
            image.close()
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

internal fun jvmNativeMetalVisualizerAvailable(
    visualizer: NaviampVisualizer,
    osName: String = System.getProperty("os.name"),
    enabledProperty: String? = System.getProperty(MacosMetalVisualizerProperty),
    libraryAvailable: Boolean = NativeMetalVisualizerHost.libraryAvailable(),
): Boolean {
    val enabled = enabledProperty.equals("true", ignoreCase = true)
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
