package app.naviamp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.ceil
import kotlin.time.TimeSource

/** Native capability, not a second product controller. No pixels cross this boundary. */
internal interface NaviampGpuVisualizerPresenter {
    fun create(shader: NaviampGpuVisualizerShader): NaviampGpuVisualizerRegion?
    @Composable fun Content(region: NaviampGpuVisualizerRegion) {}
}

internal enum class NaviampGpuSubmission { Accepted, NotReady, Busy, Failed }

internal interface NaviampGpuVisualizerRegion {
    fun place(bounds: Rect, clip: Rect, cornerRadius: Float)
    fun setVisible(visible: Boolean)
    fun submit(frame: NaviampGpuVisualizerFrame): NaviampGpuSubmission
    fun close()
}

internal data class NaviampGpuVisualizerShader(val glsl: String, val metal: String)

/** Packed ABI values are owned here. Adapters upload them unchanged and present a drawable. */
internal data class NaviampGpuVisualizerFrame(val bands: FloatArray, val uniforms: IntArray)

/** Busy skips a deadline; an unattached/failed surface gets a bounded, shared fallback policy. */
internal class NaviampGpuPresentationSession {
    private var notReadySince: Long? = null
    var failed = false
        private set
    fun receive(result: NaviampGpuSubmission, nowNanos: Long): Boolean {
        when (result) {
            NaviampGpuSubmission.Accepted -> notReadySince = null
            NaviampGpuSubmission.Busy -> Unit
            NaviampGpuSubmission.Failed -> failed = true
            NaviampGpuSubmission.NotReady -> {
                val start = notReadySince ?: nowNanos.also { notReadySince = it }
                if (nowNanos - start >= 2_000_000_000L) failed = true
            }
        }
        return !failed
    }
}

internal val LocalNaviampGpuVisualizerPresenter = staticCompositionLocalOf<NaviampGpuVisualizerPresenter?> { null }
internal val LocalNaviampVisualizerSubmissionObserver = staticCompositionLocalOf<((Boolean, Long) -> Unit)?> { null }
internal val LocalNaviampGpuCreationFailureObserver = staticCompositionLocalOf<((Throwable) -> Unit)?> { null }

/** Injectable for probes; a product setting can later wrap this shared policy without host copies. */
internal val LocalNaviampVisualizerFps = staticCompositionLocalOf { 60 }

/** Absolute deadlines retain the requested average cadence; missed frames never build a backlog. */
internal class NaviampVisualizerPacer(fps: Int) {
    private val periodNanos = 1_000_000_000.0 / fps.coerceIn(1, 60)
    private var nextNanos = Double.NaN
    fun due(nowNanos: Long): Boolean = nextNanos.isNaN() || nowNanos >= nextNanos
    fun submitted(nowNanos: Long) {
        nextNanos = if (nextNanos.isNaN() || nowNanos - nextNanos >= periodNanos) {
            nowNanos + periodNanos
        } else nextNanos + periodNanos
    }
    fun delayMillis(nowNanos: Long): Long = if (nextNanos.isNaN()) 0L else
        ceil((nextNanos - nowNanos).coerceAtLeast(0.0) / 1_000_000.0).toLong()
}

/** Shader time starts at zero and excludes suspended presentation, rather than converting uptime. */
internal class NaviampVisualizerElapsedTime {
    private var resumedAt: Long? = null
    private var accumulated = 0L
    fun resume(nowNanos: Long) { if (resumedAt == null) resumedAt = nowNanos }
    fun pause(nowNanos: Long) {
        resumedAt?.let { accumulated += (nowNanos - it).coerceAtLeast(0L) }
        resumedAt = null
    }
    fun seconds(nowNanos: Long): Float =
        ((accumulated + (resumedAt?.let { (nowNanos - it).coerceAtLeast(0L) } ?: 0L)) / 1e9).toFloat()
}

internal class NaviampGpuFrameAssembler {
    private val smooth = FloatArray(VisualizerFrameBandCount)
    private val bands = FloatArray(VisualizerFrameBandCount)
    fun prepare(width: Int, height: Int, source: List<Float>, active: Boolean, seconds: Float,
        tempo: Int?, palette: NaviampPlayerColors, colors: NaviampColors): NaviampGpuVisualizerFrame {
        require(width > 0 && height > 0)
        val sanitized = source.map { if (it.isFinite()) it.coerceIn(0f, 1f) else 0f }
        smoothVisualizerBands(sanitized, smooth, bands)
        val input = buildVisualizerFrameInput(width.toFloat(), height.toFloat(), sanitized, sanitized.size,
            active, seconds, tempo, bands)
        val values = IntArray(39)
        fun f(index: Int, value: Float) { values[index] = value.toRawBits() }
        f(0, input.timeSeconds); f(1, input.width); f(2, input.height)
        f(3, input.energy.energy); f(4, input.energy.bass); f(5, input.energy.mids)
        f(6, input.energy.highs); f(7, input.energy.spectralCentroid); f(8, input.tempoBpm)
        f(9, input.energy.beatDetected); f(10, if (active) 1f else 0f); f(11, 1f)
        fun color(index: Int, value: androidx.compose.ui.graphics.Color, opaque: Boolean = false) {
            f(index, value.red); f(index + 1, value.green); f(index + 2, value.blue)
            f(index + 3, if (opaque) 1f else value.alpha)
        }
        color(13, palette.accent); color(17, colors.primaryText)
        color(21, palette.backgroundStart, true); color(25, palette.backgroundMid, true)
        color(29, palette.backgroundEnd, true); f(33, 1f); f(34, 1f)
        color(35, colors.primaryText.copy(alpha = colors.primaryText.alpha * .16f))
        return NaviampGpuVisualizerFrame(input.bands, values)
    }
}

/** Shared UI, input observation, cadence, lifetime, clipping and fallback for all three hosts. */
@Composable
internal fun NaviampPresentedVisualizerSurface(
    coverArtUrl: String?, bandsProvider: () -> List<Float>, visualizer: NaviampVisualizer,
    visualizerColors: NaviampPlayerColors, active: Boolean, tempoBpm: Int?, colors: NaviampColors,
    lyricStage: LyricMirrorTunnelStage, modifier: Modifier = Modifier,
    onFrameDemand: (Any, Boolean) -> Unit = { _, _ -> },
) {
    val presenter = LocalNaviampGpuVisualizerPresenter.current
    val visible = LocalNaviampWindowVisible.current && LocalNaviampAnimationVisible.current
    val visibility = LocalNaviampWindowVisibility.current
    val popups = LocalNaviampPopupRegistry.current
    val ownedPopups = LocalNaviampOwnedPopupWindows.current
    val permitted = presenter != null && visible && !(popups?.visible == true && !ownedPopups)
    val shader = remember(visualizer) { naviampGpuVisualizerShader(visualizer) }
    val creationFailureObserver = LocalNaviampGpuCreationFailureObserver.current
    var failed by remember(presenter, visualizer) { mutableStateOf(false) }
    val region = remember(presenter, shader, failed) {
        if (!failed && shader != null) runCatching { presenter?.create(shader) }
            .onFailure { creationFailureObserver?.invoke(it) }.getOrNull() else null
    }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    var clip by remember { mutableStateOf(Rect.Zero) }
    NaviampVisualizerFrameDemand(
        enabled = active && visible && !bounds.isEmpty && !clip.isEmpty && (region == null || permitted),
        onFrameDemand = onFrameDemand,
    )
    val origin = remember { TimeSource.Monotonic.markNow() }
    val elapsed = remember(visualizer) { NaviampVisualizerElapsedTime() }
    val assembler = remember(visualizer) { NaviampGpuFrameAssembler() }
    val provider by rememberUpdatedState(bandsProvider)
    val palette by rememberUpdatedState(visualizerColors)
    val theme by rememberUpdatedState(colors)
    val tempo by rememberUpdatedState(tempoBpm)
    val observer by rememberUpdatedState(LocalNaviampVisualizerSubmissionObserver.current)
    val fps = LocalNaviampVisualizerFps.current.coerceIn(1, 60)
    DisposableEffect(region) { onDispose { region?.close() } }
    SideEffect {
        region?.place(bounds, clip, 0f)
        region?.setVisible(permitted && !clip.isEmpty)
    }
    LaunchedEffect(region, permitted, active, bounds.size, clip.isEmpty, fps, visibility) {
        // Compose's UI dispatcher can defer a timer continuation until a later window
        // frame. Native presentation uses its own deadlines and must not inherit that
        // parent-window batching; native geometry and submission still run on Main.
        withContext(Dispatchers.Main.immediate) {
            visibility.collectLatest { windowVisible ->
                region?.setVisible(windowVisible && permitted && !clip.isEmpty)
                if (!windowVisible || region == null || !permitted || bounds.isEmpty || clip.isEmpty) return@collectLatest
                val pacer = NaviampVisualizerPacer(fps)
                val session = NaviampGpuPresentationSession()
                fun now() = origin.elapsedNow().inWholeNanoseconds
                if (active) elapsed.resume(now())
                try {
                    do {
                        val timestamp = now()
                        val source = Snapshot.withoutReadObservation { provider().toList() }
                        val frame = assembler.prepare(bounds.width.toInt().coerceAtLeast(1), bounds.height.toInt().coerceAtLeast(1), source,
                            active, elapsed.seconds(timestamp), tempo, palette, theme)
                        val result = region.submit(frame)
                        observer?.invoke(result == NaviampGpuSubmission.Accepted, now() - timestamp)
                        if (!session.receive(result, timestamp)) { failed = true; break }
                        pacer.submitted(timestamp)
                        if (!active && result == NaviampGpuSubmission.Accepted) break
                        delay(pacer.delayMillis(now()).coerceAtLeast(1L))
                    } while (true)
                } finally { elapsed.pause(now()) }
            }
        }
    }
    if (region == null) {
        PlatformLiveVisualizerSurface(coverArtUrl, bandsProvider, visualizer, visualizerColors,
            active && visible, tempoBpm, colors, lyricStage, modifier.onGloballyPositioned {
                bounds = Rect(it.positionInWindow(), Size(it.size.width.toFloat(), it.size.height.toFloat()))
                clip = it.boundsInWindow()
            })
    } else Box(modifier.onGloballyPositioned {
        bounds = Rect(it.positionInWindow(), Size(it.size.width.toFloat(), it.size.height.toFloat()))
        clip = it.boundsInWindow()
        region.place(bounds, clip, 0f)
        region.setVisible(permitted && !clip.isEmpty)
    }) {
        if (presenter != null) NaviampGpuVisualizerContent(presenter, region)
        if (!permitted && visible) PlatformLiveVisualizerSurface(coverArtUrl, bandsProvider, visualizer,
            visualizerColors, false, tempoBpm, colors, lyricStage, Modifier.fillMaxSize())
    }
}

/** Native view factories mount once; changing the region must replace that remembered view. */
@Composable
internal fun NaviampGpuVisualizerContent(
    presenter: NaviampGpuVisualizerPresenter,
    region: NaviampGpuVisualizerRegion,
) {
    key(region) { presenter.Content(region) }
}

/** A mounted surface owns demand only while it can display changing audio. */
@Composable
internal fun NaviampVisualizerFrameDemand(enabled: Boolean, onFrameDemand: (Any, Boolean) -> Unit) {
    val owner = remember { Any() }
    val visibility = LocalNaviampWindowVisibility.current
    val currentEnabled by rememberUpdatedState(enabled)
    LaunchedEffect(onFrameDemand, owner, visibility) {
        try {
            combine(visibility, snapshotFlow { currentEnabled }) { window, surface -> window && surface }
                .distinctUntilChanged().collect { onFrameDemand(owner, it) }
        } finally { onFrameDemand(owner, false) }
    }
}
