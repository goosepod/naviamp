package app.naviamp.ui

import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Typeface
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

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
    val performanceLoggingEnabled = LocalContext.current.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    val rendererMode = selectedVisualizerRendererMode(
        visualizer = visualizer,
        nativeRendererAvailable = true,
    )
    if (rendererMode == VisualizerRendererMode.Canvas) {
        val renderPolicy = visualizerRenderPolicy(visualizer, VisualizerRenderTier.Balanced)
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
    if (rendererMode == VisualizerRendererMode.NativeGpu) {
        val renderPolicy = visualizerRenderPolicy(visualizer, VisualizerRenderTier.Balanced)
        AndroidNativeGlslVisualizerSurface(
            coverArtUrl = coverArtUrl,
            bandsProvider = bandsProvider,
            visualizer = visualizer,
            visualizerColors = visualizerColors,
            active = active,
            lyricLine = lyricStage.primaryLineForNativeMask(),
            tempoBpm = tempoBpm,
            colors = colors,
            renderPolicy = renderPolicy,
            performanceLoggingEnabled = performanceLoggingEnabled,
            modifier = modifier,
        )
        return
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val renderPolicy = visualizerRenderPolicy(visualizer, VisualizerRenderTier.Balanced)
        AndroidShaderVisualizerSurface(
            coverArtUrl = coverArtUrl,
            bandsProvider = bandsProvider,
            visualizer = visualizer,
            visualizerColors = visualizerColors,
            active = active,
            tempoBpm = tempoBpm,
            colors = colors,
            renderPolicy = renderPolicy,
            performanceLoggingEnabled = performanceLoggingEnabled,
            modifier = modifier,
        )
        return
    }
    val renderPolicy = visualizerRenderPolicy(visualizer, VisualizerRenderTier.Constrained)
    AndroidCanvasVisualizerSurface(
        bandsProvider = bandsProvider,
        visualizer = visualizer,
        visualizerColors = visualizerColors,
        active = active,
        colors = colors,
        renderPolicy = renderPolicy,
        performanceLoggingEnabled = performanceLoggingEnabled,
        modifier = modifier,
    )
}

@Composable
private fun AndroidNativeGlslVisualizerSurface(
    coverArtUrl: String?,
    bandsProvider: () -> List<Float>,
    visualizer: NaviampVisualizer,
    visualizerColors: NaviampPlayerColors,
    active: Boolean,
    lyricLine: LyricMirrorTunnelLine?,
    tempoBpm: Int?,
    colors: NaviampColors,
    renderPolicy: VisualizerRenderPolicy,
    performanceLoggingEnabled: Boolean,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val renderer = remember(visualizer, renderPolicy, performanceLoggingEnabled) {
        AndroidNativeGlslVisualizerRenderer(visualizer, renderPolicy, performanceLoggingEnabled)
    }
    val glViewState: MutableState<GLSurfaceView?> = remember { mutableStateOf(null) }
    val albumArtOwner = remember(visualizer, renderPolicy) {
        NaviampOwnedResource<Bitmap> { bitmap ->
            val glView = glViewState.value
            if (glView == null) bitmap.recycle() else glView.queueEvent { bitmap.recycle() }
        }
    }
    val uniformBands = remember(visualizer) { FloatArray(VisualizerFrameBandCount) }
    val smoothBands = remember(visualizer) { FloatArray(VisualizerFrameBandCount) }
    val latestBandsProvider by rememberUpdatedState(bandsProvider)
    val latestVisualizerColors by rememberUpdatedState(visualizerColors)
    val latestColors by rememberUpdatedState(colors)
    val latestTempoBpm by rememberUpdatedState(tempoBpm)
    val latestActive by rememberUpdatedState(active)
    val latestLyricProgress by rememberUpdatedState(lyricLine?.progressToNext ?: 0f)
    var surfaceWidth by remember { mutableStateOf(0) }
    var surfaceHeight by remember { mutableStateOf(0) }

    LaunchedEffect(coverArtUrl, renderPolicy.albumArtSidePx, visualizer, lyricLine?.text) {
        val next = if (visualizer == NaviampVisualizer.LyricMirrorTunnel) {
            androidLyricMaskBitmap(lyricLine?.text.orEmpty())
        } else if (coverArtUrl == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    androidPlatformCoverArtBytes(coverArtUrl)
                        ?.let { decodeSampledBitmap(it, renderPolicy.albumArtSidePx) }
                }.getOrNull()
            }
        }
        renderer.updateAlbumArt(albumArtOwner.replace(next))
        glViewState.value?.requestRender()
    }

    DisposableEffect(renderer) {
        onDispose {
            renderer.updateAlbumArt(null)
            albumArtOwner.close()
            glViewState.value?.queueEvent { renderer.close() }
            glViewState.value?.onPause()
        }
    }

    LaunchedEffect(visualizer, renderPolicy.targetFrameIntervalMillis, surfaceWidth, surfaceHeight) {
        glViewState.value?.onResume()
        runVisualizerFrameClock(active = true, targetFrameIntervalMillis = renderPolicy.targetFrameIntervalMillis) { nextFrameMillis ->
            val bands = latestBandsProvider()
            smoothVisualizerBands(bands, smoothBands, uniformBands)
            val frameInput = buildVisualizerFrameInput(
                width = surfaceWidth.toFloat(),
                height = surfaceHeight.toFloat(),
                bands = bands,
                visibleBands = minOf(bands.size, VisualizerFrameBandCount).coerceAtLeast(1),
                active = latestActive,
                timeSeconds = nextFrameMillis / 1000f,
                tempoBpm = latestTempoBpm,
                uniformBands = uniformBands,
            )
            renderer.updateFrame(frameInput, latestVisualizerColors, latestColors, latestLyricProgress)
            glViewState.value?.requestRender()
        }
    }

    key(visualizer, renderPolicy.tier) {
        AndroidView(
            modifier = modifier.onSizeChanged { size ->
                surfaceWidth = size.width
                surfaceHeight = size.height
            },
            factory = {
                object : GLSurfaceView(context) {
                    override fun onAttachedToWindow() {
                        super.onAttachedToWindow()
                        // SurfaceView needs a root pre-draw to create its native surface even
                        // when Compose has retained the surrounding player drawing. Request
                        // one traversal on attachment, never a traversal per rendered frame.
                        post {
                            if (isAttachedToWindow) {
                                rootView.requestLayout()
                                rootView.invalidate()
                            }
                        }
                    }
                }.apply {
                    setEGLContextClientVersion(3)
                    preserveEGLContextOnPause = true
                    setRenderer(renderer)
                    renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                    glViewState.value = this
                }
            },
            update = { view ->
                glViewState.value = view
                view.onResume()
            },
        )
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun AndroidShaderVisualizerSurface(
    coverArtUrl: String?,
    bandsProvider: () -> List<Float>,
    visualizer: NaviampVisualizer,
    visualizerColors: NaviampPlayerColors,
    active: Boolean,
    tempoBpm: Int?,
    colors: NaviampColors,
    renderPolicy: VisualizerRenderPolicy,
    performanceLoggingEnabled: Boolean,
    modifier: Modifier,
) {
    val frameMillis by rememberVisualizerFrameClock(active, renderPolicy.targetFrameIntervalMillis, visualizer)
    val albumArtOwner = remember(visualizer, renderPolicy) {
        NaviampOwnedResource<Bitmap>(Bitmap::recycle)
    }
    val albumArtRetirementScope = rememberCoroutineScope()
    var albumArtBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(coverArtUrl, visualizer) {
        val next = if (coverArtUrl != null && visualizer.usesAlbumArtShader) {
            withContext(Dispatchers.IO) {
                runCatching {
                    androidPlatformCoverArtBytes(coverArtUrl)
                        ?.let { decodeSampledBitmap(it, renderPolicy.albumArtSidePx) }
                }.getOrNull()
            }
        } else {
            null
        }
        albumArtOwner.replaceForRendering(next, albumArtRetirementScope) { albumArtBitmap = it }
    }
    val renderer = remember(visualizer, renderPolicy, performanceLoggingEnabled) {
        AndroidShaderVisualizerRenderer(visualizer, renderPolicy, performanceLoggingEnabled)
    }
    DisposableEffect(renderer) {
        onDispose {
            renderer.close()
            albumArtOwner.close()
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
                albumArtBitmap = albumArtBitmap,
                timeSeconds = frameMillis / 1000f,
                tempoBpm = tempoBpm,
            )
        }
        renderer.recordDrawNanos(System.nanoTime() - drawStartedNanos, active)
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class AndroidShaderVisualizerRenderer(
    private val visualizer: NaviampVisualizer,
    private val renderPolicy: VisualizerRenderPolicy,
    performanceLoggingEnabled: Boolean,
) : AutoCloseable {
    private val runtimeShader = RuntimeShader(visualizer.shaderSource)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val uniformBands = FloatArray(VisualizerFrameBandCount)
    private val smoothBands = FloatArray(VisualizerFrameBandCount)
    private val historyPixels = IntArray(VisualizerHistoryColumns * VisualizerHistoryRows)
    private val history = VisualizerHistoryBuffer(visualizer, renderPolicy)
    private val historyBitmap = Bitmap.createBitmap(VisualizerHistoryColumns, VisualizerHistoryRows, Bitmap.Config.ARGB_8888)
    private var historyShader = historyBitmap.newClampShader(visualizer.historyFilterMode)
    private val fallbackAlbumArtBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
        eraseColor(android.graphics.Color.WHITE)
    }
    private val fallbackAlbumArtShader = fallbackAlbumArtBitmap.newClampShader()
    private var albumArtBitmap: Bitmap? = null
    private var albumArtShader: BitmapShader? = null
    private val perfLogger = AndroidVisualizerPerfLogger("shader", visualizer, renderPolicy, performanceLoggingEnabled)

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
        tempoBpm: Int?,
    ) {
        smoothVisualizerBands(bands, smoothBands, uniformBands)
        if (visualizer.usesHistoryShader) {
            updateHistoryTexture(timeSeconds, active)
        }
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

        runtimeShader.setFloatUniform("iResolution", frameInput.width, frameInput.height)
        runtimeShader.setFloatUniform("iTime", frameInput.timeSeconds)
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
        runtimeShader.setFloatUniform("iActive", if (frameInput.active) 1f else 0f)
        runtimeShader.setFloatUniform("iVisibleBands", frameInput.visibleBands)
        runtimeShader.setFloatUniform("iSourceBands", frameInput.sourceBands)
        runtimeShader.setFloatUniform(
            "iEnergy",
            frameInput.energy.bass,
            frameInput.energy.mids,
            frameInput.energy.highs,
            frameInput.energy.energy,
        )
        runtimeShader.setFloatUniform("iTempo", frameInput.tempoBpm)
        runtimeShader.setFloatUniform("iBands", frameInput.bands)
        val albumBitmap = albumArtBitmap ?: fallbackAlbumArtBitmap
        runtimeShader.setFloatUniform("iAlbumArtSize", albumBitmap.width.toFloat(), albumBitmap.height.toFloat())
        if (visualizer.usesHistoryShader) {
            runtimeShader.setInputShader("iHistory", historyShader)
        }
        if (visualizer.usesAlbumArtShader) {
            runtimeShader.setInputShader("iAlbumArt", albumShaderFor(albumArtBitmap))
        }
        paint.shader = runtimeShader
        canvas.drawRect(0f, 0f, width, height, paint)
    }

    override fun close() {
        historyBitmap.recycle()
        fallbackAlbumArtBitmap.recycle()
    }

    fun recordDrawNanos(drawNanos: Long, active: Boolean) {
        perfLogger.record(drawNanos, active)
    }

    private fun albumShaderFor(bitmap: Bitmap?): Shader {
        if (bitmap == null) return fallbackAlbumArtShader
        if (albumArtBitmap !== bitmap) {
            albumArtBitmap = bitmap
            albumArtShader = bitmap.newClampShader()
        }
        return albumArtShader ?: fallbackAlbumArtShader
    }

    private fun updateHistoryTexture(timeSeconds: Float, active: Boolean) {
        if (!history.update(timeSeconds, active, uniformBands)) return
        history.copyArgbTo(historyPixels)
        val rowWidth = VisualizerHistoryColumns
        historyBitmap.setPixels(historyPixels, 0, rowWidth, 0, 0, VisualizerHistoryColumns, VisualizerHistoryRows)
        historyShader = historyBitmap.newClampShader(visualizer.historyFilterMode)
    }
}

private class AndroidNativeGlslVisualizerRenderer(
    private val visualizer: NaviampVisualizer,
    private val renderPolicy: VisualizerRenderPolicy,
    performanceLoggingEnabled: Boolean,
) : GLSurfaceView.Renderer, AutoCloseable {
    private val shaderSpec = visualizer.nativeGlslShaderSpec(renderPolicy)
    private val perfLogger = AndroidVisualizerPerfLogger("native-gl", visualizer, renderPolicy, performanceLoggingEnabled)
    private val frameLock = Any()
    private var latestFrame: VisualizerFrameInput? = null
    private var latestLyricProgress = 0f
    private var latestPalette = NativeGlslPalette()
    private var latestAlbumArtBitmap: Bitmap? = null
    private var uploadedAlbumArtBitmap: Bitmap? = null
    private var program = 0
    private var screenProgram = 0
    private var frequencyTexture = 0
    private var albumArtTexture = 0
    private var albumArtWidth = 1
    private var albumArtHeight = 1
    private var framebuffer = 0
    private var framebufferTexture = 0
    private var framebufferWidth = 0
    private var framebufferHeight = 0
    private var surfaceWidth = 1
    private var surfaceHeight = 1
    private val vertexBuffer = nativeFloatBuffer(
        floatArrayOf(
            -1f, -1f, 0f, 0f,
            1f, -1f, 1f, 0f,
            -1f, 1f, 0f, 1f,
            1f, 1f, 1f, 1f,
        ),
    )
    private val frequencyPixels = ByteArray(VisualizerFrameBandCount * 4)

    fun updateFrame(
        frameInput: VisualizerFrameInput,
        visualizerColors: NaviampPlayerColors,
        colors: NaviampColors,
        lyricProgress: Float,
    ) {
        synchronized(frameLock) {
            latestFrame = frameInput
            latestLyricProgress = lyricProgress.coerceIn(0f, 1f)
            latestPalette = NativeGlslPalette(
                accent = floatArrayOf(
                    visualizerColors.accent.red,
                    visualizerColors.accent.green,
                    visualizerColors.accent.blue,
                    visualizerColors.accent.alpha,
                ),
                readable = floatArrayOf(
                    colors.primaryText.red,
                    colors.primaryText.green,
                    colors.primaryText.blue,
                    colors.primaryText.alpha,
                ),
                colorA = floatArrayOf(
                    visualizerColors.backgroundStart.red,
                    visualizerColors.backgroundStart.green,
                    visualizerColors.backgroundStart.blue,
                    1f,
                ),
                colorB = floatArrayOf(
                    visualizerColors.backgroundMid.red,
                    visualizerColors.backgroundMid.green,
                    visualizerColors.backgroundMid.blue,
                    1f,
                ),
                colorC = floatArrayOf(
                    visualizerColors.backgroundEnd.red,
                    visualizerColors.backgroundEnd.green,
                    visualizerColors.backgroundEnd.blue,
                    1f,
                ),
            )
        }
    }

    fun updateAlbumArt(bitmap: Bitmap?) {
        synchronized(frameLock) {
            latestAlbumArtBitmap = bitmap
        }
    }

    fun updateActive(active: Boolean) {
        synchronized(frameLock) {
            latestFrame = latestFrame?.copy(active = active)
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = buildProgram(NativeGlslVertexShader, shaderSpec.fragmentSource)
        screenProgram = buildProgram(NativeGlslVertexShader, NativeGlslBlitFragmentShader)
        frequencyTexture = createFrequencyTexture()
        albumArtTexture = createAlbumArtTexture()
        uploadedAlbumArtBitmap = null
        GLES20.glClearColor(0f, 0f, 0f, 0f)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width.coerceAtLeast(1)
        surfaceHeight = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight)
    }

    override fun onDrawFrame(gl: GL10?) {
        val drawStartedNanos = System.nanoTime()
        val frame: VisualizerFrameInput?
        val lyricProgress: Float
        val palette: NativeGlslPalette
        val albumArtBitmap: Bitmap?
        synchronized(frameLock) {
            frame = latestFrame
            lyricProgress = latestLyricProgress
            palette = latestPalette
            albumArtBitmap = latestAlbumArtBitmap
        }
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (program == 0 || frequencyTexture == 0 || albumArtTexture == 0 || frame == null) {
            perfLogger.record(System.nanoTime() - drawStartedNanos, active = false)
            return
        }
        ensureFramebuffer()
        uploadFrequencyTexture(frame.bands)
        uploadAlbumArtTextureIfNeeded(albumArtBitmap)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer)
        GLES20.glViewport(0, 0, framebufferWidth, framebufferHeight)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)
        val shaderQuad = bindShaderQuad(program)

        GLES20.glUniform1f(uniform(program, "u_time"), frame.timeSeconds)
        GLES20.glUniform2f(uniform(program, "u_resolution"), framebufferWidth.toFloat(), framebufferHeight.toFloat())
        GLES20.glUniform1f(uniform(program, "u_energyLevel"), frame.energy.energy)
        GLES20.glUniform1f(uniform(program, "u_bassLevel"), frame.energy.bass)
        GLES20.glUniform1f(uniform(program, "u_midLevel"), frame.energy.mids)
        GLES20.glUniform1f(uniform(program, "u_trebleLevel"), frame.energy.highs)
        GLES20.glUniform1f(uniform(program, "u_spectralCentroid"), frame.energy.spectralCentroid)
        GLES20.glUniform1f(uniform(program, "u_tempoBpm"), frame.tempoBpm)
        GLES20.glUniform1f(uniform(program, "u_lyricProgress"), lyricProgress)
        GLES20.glUniform1f(uniform(program, "u_beatDetected"), frame.energy.beatDetected)
        GLES20.glUniform1f(uniform(program, "u_active"), if (frame.active) 1f else 0f)
        GLES20.glUniform1f(uniform(program, "u_renderScale"), shaderSpec.renderScale)
        GLES20.glUniform1i(uniform(program, "u_maxRaymarchSteps"), shaderSpec.maxRaymarchSteps)
        GLES20.glUniform2f(uniform(program, "u_albumArtSize"), albumArtWidth.toFloat(), albumArtHeight.toFloat())
        GLES20.glUniform4fv(uniform(program, "u_accent"), 1, palette.accent, 0)
        GLES20.glUniform4fv(uniform(program, "u_readable"), 1, palette.readable, 0)
        GLES20.glUniform4fv(uniform(program, "u_colorA"), 1, palette.colorA, 0)
        GLES20.glUniform4fv(uniform(program, "u_colorB"), 1, palette.colorB, 0)
        GLES20.glUniform4fv(uniform(program, "u_colorC"), 1, palette.colorC, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, frequencyTexture)
        GLES20.glUniform1i(uniform(program, "u_frequencyTexture"), 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, albumArtTexture)
        GLES20.glUniform1i(uniform(program, "u_albumArtTexture"), 1)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        shaderQuad.disable()

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight)
        GLES20.glUseProgram(screenProgram)
        val screenQuad = bindShaderQuad(screenProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, framebufferTexture)
        GLES20.glUniform1i(uniform(screenProgram, "u_texture"), 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        screenQuad.disable()
        perfLogger.record(System.nanoTime() - drawStartedNanos, frame.active)
    }

    private fun bindShaderQuad(drawProgram: Int): NativeGlslQuadBinding {
        val drawPositionHandle = GLES20.glGetAttribLocation(drawProgram, "a_position")
        val drawUvHandle = GLES20.glGetAttribLocation(drawProgram, "a_uv")
        vertexBuffer.position(0)
        GLES20.glEnableVertexAttribArray(drawPositionHandle)
        GLES20.glVertexAttribPointer(drawPositionHandle, 2, GLES20.GL_FLOAT, false, NativeGlslVertexStrideBytes, vertexBuffer)
        vertexBuffer.position(2)
        GLES20.glEnableVertexAttribArray(drawUvHandle)
        GLES20.glVertexAttribPointer(drawUvHandle, 2, GLES20.GL_FLOAT, false, NativeGlslVertexStrideBytes, vertexBuffer)
        return NativeGlslQuadBinding(drawPositionHandle, drawUvHandle)
    }

    override fun close() {
        if (program != 0) {
            GLES20.glDeleteProgram(program)
            program = 0
        }
        if (screenProgram != 0) {
            GLES20.glDeleteProgram(screenProgram)
            screenProgram = 0
        }
        if (frequencyTexture != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(frequencyTexture), 0)
            frequencyTexture = 0
        }
        if (albumArtTexture != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(albumArtTexture), 0)
            albumArtTexture = 0
        }
        deleteFramebuffer()
    }

    private fun uploadFrequencyTexture(bands: FloatArray) {
        repeat(VisualizerFrameBandCount) { index ->
            val value = (bands[index].coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255).toByte()
            val pixel = index * 4
            frequencyPixels[pixel] = value
            frequencyPixels[pixel + 1] = value
            frequencyPixels[pixel + 2] = value
            frequencyPixels[pixel + 3] = 255.toByte()
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, frequencyTexture)
        GLES20.glTexSubImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            0,
            0,
            VisualizerFrameBandCount,
            1,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            ByteBuffer.wrap(frequencyPixels),
        )
    }

    private fun createFrequencyTexture(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        val texture = textures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            GLES20.GL_RGBA,
            VisualizerFrameBandCount,
            1,
            0,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            ByteBuffer.allocateDirect(VisualizerFrameBandCount * 4),
        )
        return texture
    }

    private fun createAlbumArtTexture(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        val texture = textures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            GLES20.GL_RGBA,
            1,
            1,
            0,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            ByteBuffer.wrap(byteArrayOf(255.toByte(), 255.toByte(), 255.toByte(), 255.toByte())),
        )
        return texture
    }

    private fun uploadAlbumArtTextureIfNeeded(bitmap: Bitmap?) {
        if (uploadedAlbumArtBitmap === bitmap) return
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, albumArtTexture)
        if (bitmap == null || bitmap.isRecycled) {
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                GLES20.GL_RGBA,
                1,
                1,
                0,
                GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE,
                ByteBuffer.wrap(byteArrayOf(255.toByte(), 255.toByte(), 255.toByte(), 255.toByte())),
            )
            albumArtWidth = 1
            albumArtHeight = 1
        } else {
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            albumArtWidth = bitmap.width.coerceAtLeast(1)
            albumArtHeight = bitmap.height.coerceAtLeast(1)
        }
        uploadedAlbumArtBitmap = bitmap
    }

    private fun ensureFramebuffer() {
        val targetWidth = (surfaceWidth * shaderSpec.renderScale).toInt().coerceAtLeast(64)
        val targetHeight = (surfaceHeight * shaderSpec.renderScale).toInt().coerceAtLeast(64)
        if (framebuffer != 0 && framebufferWidth == targetWidth && framebufferHeight == targetHeight) return
        deleteFramebuffer()
        framebufferWidth = targetWidth
        framebufferHeight = targetHeight
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        framebufferTexture = textures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, framebufferTexture)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            GLES20.GL_RGBA,
            framebufferWidth,
            framebufferHeight,
            0,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            null,
        )
        val framebuffers = IntArray(1)
        GLES20.glGenFramebuffers(1, framebuffers, 0)
        framebuffer = framebuffers[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer)
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER,
            GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D,
            framebufferTexture,
            0,
        )
        if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            Log.e(AndroidVisualizerPerfLogTag, "Native GLSL framebuffer is incomplete for ${visualizer.name}")
            deleteFramebuffer()
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    private fun deleteFramebuffer() {
        if (framebufferTexture != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(framebufferTexture), 0)
            framebufferTexture = 0
        }
        if (framebuffer != 0) {
            GLES20.glDeleteFramebuffers(1, intArrayOf(framebuffer), 0)
            framebuffer = 0
        }
        framebufferWidth = 0
        framebufferHeight = 0
    }

    private fun uniform(programHandle: Int, name: String): Int = GLES20.glGetUniformLocation(programHandle, name)

    private fun buildProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (vertexShader == 0 || fragmentShader == 0) return 0
        val nextProgram = GLES20.glCreateProgram()
        GLES20.glAttachShader(nextProgram, vertexShader)
        GLES20.glAttachShader(nextProgram, fragmentShader)
        GLES20.glLinkProgram(nextProgram)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(nextProgram, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            Log.e(AndroidVisualizerPerfLogTag, "Failed to link native GLSL visualizer: ${GLES20.glGetProgramInfoLog(nextProgram)}")
            GLES20.glDeleteProgram(nextProgram)
            return 0
        }
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
        return nextProgram
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            Log.e(
                AndroidVisualizerPerfLogTag,
                "Failed to compile native GLSL visualizer type=$type firstLine=${source.lineSequence().firstOrNull()} " +
                    "error=${GLES20.glGetShaderInfoLog(shader)}",
            )
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }
}

private data class NativeGlslPalette(
    val accent: FloatArray = floatArrayOf(1f, 1f, 1f, 1f),
    val readable: FloatArray = floatArrayOf(1f, 1f, 1f, 1f),
    val colorA: FloatArray = floatArrayOf(0f, 0f, 0f, 1f),
    val colorB: FloatArray = floatArrayOf(0f, 0f, 0f, 1f),
    val colorC: FloatArray = floatArrayOf(0f, 0f, 0f, 1f),
)

private data class NativeGlslShaderSpec(
    val fragmentSource: String,
    val renderScale: Float,
    val maxRaymarchSteps: Int = 0,
)

private data class NativeGlslQuadBinding(
    val positionHandle: Int,
    val uvHandle: Int,
) {
    fun disable() {
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(uvHandle)
    }
}

private fun NaviampVisualizer.nativeGlslShaderSpec(renderPolicy: VisualizerRenderPolicy): NativeGlslShaderSpec =
    when (this) {
        NaviampVisualizer.AnalogSignalFailure -> NativeGlslShaderSpec(
            fragmentSource = requireNotNull(nativeShaderDefinition).fragmentSource,
            renderScale = when (renderPolicy.tier) {
                VisualizerRenderTier.Full -> 1.0f
                VisualizerRenderTier.Balanced -> 0.82f
                VisualizerRenderTier.Constrained -> 0.65f
            },
        )
        NaviampVisualizer.AudioTunnel -> NativeGlslShaderSpec(
            fragmentSource = requireNotNull(nativeShaderDefinition).fragmentSource,
            renderScale = when (renderPolicy.tier) {
                VisualizerRenderTier.Full -> 1.0f
                VisualizerRenderTier.Balanced -> 0.82f
                VisualizerRenderTier.Constrained -> 0.62f
            },
            maxRaymarchSteps = when (renderPolicy.tier) {
                VisualizerRenderTier.Full -> 64
                VisualizerRenderTier.Balanced -> 52
                VisualizerRenderTier.Constrained -> 38
            },
        )
        NaviampVisualizer.FluidicNebulae -> NativeGlslShaderSpec(
            fragmentSource = requireNotNull(nativeShaderDefinition).fragmentSource,
            renderScale = when (renderPolicy.tier) {
                VisualizerRenderTier.Full -> 1.0f
                VisualizerRenderTier.Balanced -> 0.82f
                VisualizerRenderTier.Constrained -> 0.65f
            },
        )
        NaviampVisualizer.LyricMirrorTunnel -> NativeGlslShaderSpec(
            fragmentSource = requireNotNull(nativeShaderDefinition).fragmentSource,
            renderScale = 1.0f,
        )
        NaviampVisualizer.OceanHorizon -> NativeGlslShaderSpec(
            fragmentSource = requireNotNull(nativeShaderDefinition).fragmentSource,
            renderScale = when (renderPolicy.tier) {
                VisualizerRenderTier.Full -> 0.82f
                VisualizerRenderTier.Balanced -> 0.62f
                VisualizerRenderTier.Constrained -> 0.48f
            },
            maxRaymarchSteps = when (renderPolicy.tier) {
                VisualizerRenderTier.Full -> 60
                VisualizerRenderTier.Balanced -> 60
                VisualizerRenderTier.Constrained -> 42
            },
        )
        NaviampVisualizer.OceanOfInk -> NativeGlslShaderSpec(
            fragmentSource = requireNotNull(nativeShaderDefinition).fragmentSource,
            renderScale = when (renderPolicy.tier) {
                VisualizerRenderTier.Full -> 1.0f
                VisualizerRenderTier.Balanced -> 0.82f
                VisualizerRenderTier.Constrained -> 0.65f
            },
        )
        NaviampVisualizer.RaymarchedSphereLiquid -> NativeGlslShaderSpec(
            fragmentSource = requireNotNull(nativeShaderDefinition).fragmentSource,
            renderScale = when (renderPolicy.tier) {
                VisualizerRenderTier.Full -> 1.0f
                VisualizerRenderTier.Balanced -> 0.82f
                VisualizerRenderTier.Constrained -> 0.65f
            },
            maxRaymarchSteps = when (renderPolicy.tier) {
                VisualizerRenderTier.Full -> 80
                VisualizerRenderTier.Balanced -> 64
                VisualizerRenderTier.Constrained -> 48
            },
        )
        else -> NativeGlslShaderSpec(
            fragmentSource = NativeGlslShaderSources.FluidicNebulae,
            renderScale = 1.0f,
        )
    }

private fun nativeFloatBuffer(values: FloatArray): FloatBuffer =
    ByteBuffer
        .allocateDirect(values.size * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(values)
            position(0)
        }

private val NaviampVisualizer.historyFilterMode: Int
    get() = if (historyUsesNearestSampling) BitmapShader.FILTER_MODE_NEAREST else BitmapShader.FILTER_MODE_LINEAR

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun Bitmap.newClampShader(filterMode: Int = BitmapShader.FILTER_MODE_LINEAR): BitmapShader =
    BitmapShader(this, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
        setFilterMode(filterMode)
    }

private fun androidLyricMaskBitmap(text: String): Bitmap {
    val width = 768
    val height = 256
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    canvas.drawColor(android.graphics.Color.TRANSPARENT)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    val lines = paint.selectAndroidLyricMaskLayout(text.ifBlank { " " }, width - 92, height - 34)
    val fontMetrics = paint.fontMetrics
    val lineHeight = fontMetrics.descent - fontMetrics.ascent
    var baseline = height / 2f - (lineHeight * lines.size) / 2f - fontMetrics.ascent
    lines.forEach { line ->
        canvas.drawText(line, width / 2f, baseline, paint)
        baseline += lineHeight
    }
    return bitmap
}

private fun Paint.selectAndroidLyricMaskLayout(
    text: String,
    maxWidth: Int,
    maxHeight: Int,
): List<String> {
    for (fontSize in 74 downTo 34 step 2) {
        textSize = fontSize.toFloat()
        val lines = text.androidWrapLyricMaskLines(this, maxWidth).take(3)
        val metrics = fontMetrics
        val lineHeight = metrics.descent - metrics.ascent
        val fitsWidth = lines.all { measureText(it) <= maxWidth }
        val fitsHeight = lineHeight * lines.size <= maxHeight
        if (fitsWidth && fitsHeight) return lines
    }
    textSize = 34f
    return text.androidWrapLyricMaskLines(this, maxWidth).take(3)
}

private fun String.androidWrapLyricMaskLines(
    paint: Paint,
    maxWidth: Int,
): List<String> {
    val words = trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (words.isEmpty()) return listOf(" ")
    val lines = mutableListOf<String>()
    var current = ""
    words.forEach { word ->
        val candidate = if (current.isBlank()) word else "$current $word"
        if (paint.measureText(candidate) <= maxWidth || current.isBlank()) {
            current = candidate
        } else {
            lines += current
            current = word
        }
    }
    if (current.isNotBlank()) lines += current
    return lines
}

@Composable
private fun AndroidCanvasVisualizerSurface(
    bandsProvider: () -> List<Float>,
    visualizer: NaviampVisualizer,
    visualizerColors: NaviampPlayerColors,
    active: Boolean,
    colors: NaviampColors,
    renderPolicy: VisualizerRenderPolicy,
    performanceLoggingEnabled: Boolean,
    modifier: Modifier,
) {
    val perfLogger = remember(visualizer, renderPolicy, performanceLoggingEnabled) {
        AndroidVisualizerPerfLogger("canvas", visualizer, renderPolicy, performanceLoggingEnabled)
    }
    NaviampCanvasPresetFallbackSurface(
        bandsProvider, visualizer, visualizerColors, active, colors, renderPolicy, modifier,
        onDrawMeasured = perfLogger::record,
    )
}

private class AndroidVisualizerPerfLogger(
    private val renderer: String,
    private val visualizer: NaviampVisualizer,
    private val renderPolicy: VisualizerRenderPolicy,
    private val enabled: Boolean,
) {
    private var windowStartedMillis = SystemClock.elapsedRealtime()
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

        val nowMillis = SystemClock.elapsedRealtime()
        val elapsedMillis = nowMillis - windowStartedMillis
        if (elapsedMillis < AndroidVisualizerPerfLogIntervalMillis || frameCount <= 0) return

        val averageDrawMillis = drawNanosTotal / frameCount / 1_000_000.0
        val maxDrawMillis = maxDrawNanos / 1_000_000.0
        val fps = frameCount * 1_000.0 / elapsedMillis.toDouble()
        Log.i(
            AndroidVisualizerPerfLogTag,
            "${visualizer.name} renderer=$renderer platform=android " +
                "profile=${renderPolicy.tier.label} targetFps=${renderPolicy.targetFps} " +
                "fps=${fps.formatVisualizerMetric()} " +
                "avgDrawMs=${averageDrawMillis.formatVisualizerMetric()} " +
                "maxDrawMs=${maxDrawMillis.formatVisualizerMetric()} frames=$frameCount",
        )
        reset(nowMillis)
    }

    private fun reset(startMillis: Long = SystemClock.elapsedRealtime()) {
        windowStartedMillis = startMillis
        frameCount = 0
        drawNanosTotal = 0L
        maxDrawNanos = 0L
    }
}

private const val AndroidVisualizerPerfLogIntervalMillis = 5_000L
private const val AndroidVisualizerPerfLogTag = "NaviampVisualizerPerf"
private const val NativeGlslVertexStrideBytes = 4 * Float.SIZE_BYTES

private const val NativeGlslVertexShader = """#version 300 es

in vec2 a_position;
in vec2 a_uv;
out vec2 v_uv;

void main() {
    v_uv = a_uv;
    gl_Position = vec4(a_position, 0.0, 1.0);
}
"""

private const val NativeGlslBlitFragmentShader = """#version 300 es
precision mediump float;

uniform sampler2D u_texture;
in vec2 v_uv;
out vec4 outColor;

void main() {
    outColor = texture(u_texture, v_uv);
}
"""

private fun Double.formatVisualizerMetric(): String =
    String.format(Locale.US, "%.2f", this)
