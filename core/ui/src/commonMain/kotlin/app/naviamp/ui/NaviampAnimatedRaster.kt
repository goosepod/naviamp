package app.naviamp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow

/** Shared pixels and motion; hosts only present these declarative layers. Coordinates are pixels. */
internal data class NaviampRasterLayer(
    val image: ImageBitmap,
    val origin: Offset = Offset.Zero,
    val translation: NaviampLayerMotion? = null,
    val reveal: Float = 1f,
    val revealMotion: NaviampLayerMotion? = null,
    val clipFromStart: Boolean = false,
)

internal interface NaviampRasterPresenter {
    /** True only when native pixels are intrinsically below the host's owned popup windows. */
    val contentBelowOwnedWindows: Boolean get() = false
    fun create(): NaviampRasterRegion
    /** Optional native anchor hosted at the shared region's layout position. */
    @Composable fun Content(region: NaviampRasterRegion) {}
}

internal interface NaviampRasterRegion {
    /** True when native presentation prepares changes that must be committed in a drawing pass. */
    val requiresDrawSynchronization: Boolean get() = false
    /**
     * Bounds and clipping are in window pixels, including a non-zero window origin.
     * Adapters translate these into their native parent coordinate system and own the layers
     * they mutate; a host-owned parent is only an attachment point.
     */
    fun present(layers: List<NaviampRasterLayer>, bounds: Rect, clip: Rect, cornerRadius: Float): Boolean
    /** Notify the shared owner when an asynchronous native attachment can replace its fallback. */
    fun whenReady(callback: () -> Unit) {}
    /** Commit prepared native changes; only drawing-dependent regions defer this to a draw pass. */
    fun synchronizeDraw() {}
    fun translationX(layerIndex: Int): Float? = null
    fun close()
}

internal val LocalNaviampRasterPresenter = staticCompositionLocalOf<NaviampRasterPresenter?> { null }
internal val LocalNaviampAnimationVisible = staticCompositionLocalOf { true }
private val LocalNaviampWindowVisible = staticCompositionLocalOf { true }

internal class NaviampRasterPosition {
    var translationX: () -> Float = { 0f }
}

private data class RasterSubmission(
    val layers: List<NaviampRasterLayer>, val bounds: Rect, val clip: Rect, val cornerRadius: Float,
)
private class RasterSubmissionState { var latest: RasterSubmission? = null }
private class RasterContentState { var layers: List<NaviampRasterLayer> = emptyList() }

/** Read changing shared values without subscribing the surrounding composition to them. */
internal class NaviampRasterContent<T>(val value: () -> T, val render: (T) -> List<NaviampRasterLayer>)

/** Compose retains all input/semantics. Presentation effects never install an input surface. */
@Composable
internal fun NaviampAnimatedRaster(layers: List<NaviampRasterLayer>, modifier: Modifier, cornerRadius: Float = 0f, position: NaviampRasterPosition? = null) {
    val content = remember(layers) { NaviampRasterContent({ layers }, { it }) }
    NaviampAnimatedRaster(content, modifier, cornerRadius, position)
}

@Composable
internal fun <T> NaviampAnimatedRaster(content: NaviampRasterContent<T>, modifier: Modifier, cornerRadius: Float = 0f, position: NaviampRasterPosition? = null) {
    val visible = LocalNaviampAnimationVisible.current
    val windowVisible = LocalNaviampWindowVisible.current
    val presenter = LocalNaviampRasterPresenter.current.takeIf { visible }
    val region = remember(presenter) { presenter?.create() }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    var clip by remember { mutableStateOf(Rect.Zero) }
    var presented by remember(region) { mutableStateOf(false) }
    var elapsed by remember { mutableLongStateOf(0L) }
    // Pixels belong to the shared component, so changing its native binding must not discard
    // the content needed by the very first fallback frame (for example when opening a popup).
    val rasterContent = remember { RasterContentState() }
    val submission = remember(region) { RasterSubmissionState() }
    var readyRevision by remember(region) { mutableIntStateOf(0) }
    var drawRevision by remember(region) { mutableIntStateOf(0) }
    var fallbackRevision by remember(region) { mutableIntStateOf(0) }
    val currentContent by rememberUpdatedState(content)
    SideEffect { position?.translationX = {
        if (presented) region?.translationX(0) ?: 0f else rasterContent.layers.firstOrNull()?.translation?.valueAt(elapsed) ?: 0f
    } }
    val submit by rememberUpdatedState {
        val next = RasterSubmission(rasterContent.layers, bounds, clip, cornerRadius)
        if (submission.latest != next) {
            submission.latest = next
            presented = region?.present(rasterContent.layers, bounds, clip, cornerRadius) == true
            if (region?.requiresDrawSynchronization == true) drawRevision++
            else region?.synchronizeDraw()
            if (!presented) { elapsed = 0L; fallbackRevision++ }
        }
    }
    DisposableEffect(region) {
        region?.whenReady { submission.latest = null; submit(); readyRevision++ }
        onDispose { region?.close() }
    }
    SideEffect { submit() }
    LaunchedEffect(Unit) {
        snapshotFlow { currentContent.let { it to it.value() } }.collect { (source, value) ->
            // Rasterization happens outside snapshotFlow's read-only snapshot. Native updates
            // do not change composition/drawing state unless a fallback or deferred commit needs it.
            rasterContent.layers = source.render(value)
            submit()
        }
    }
    val presentationModifier = remember(region) { Modifier.onGloballyPositioned {
        bounds = Rect(it.positionInWindow(), androidx.compose.ui.geometry.Size(it.size.width.toFloat(), it.size.height.toFloat()))
        clip = it.boundsInWindow()
        // Layout must reach the presenter before this frame is drawn, not via a later coroutine.
        submit()
    }.drawWithContent {
        // A native surface may become ready without changing pixels or layout.
        readyRevision
        drawRevision
        // An immediate native update needs no parent repaint. Keep the callback stable and
        // avoid observing its changing submission closure as drawing state. Deferred native
        // backends explicitly request the drawing pass needed to commit their changes.
        Snapshot.withoutReadObservation {
            submit()
            region?.synchronizeDraw()
        }
        drawContent()
    } }
    Box(modifier.then(presentationModifier)) {
        if (region != null) presenter?.Content(region)
        if (!presented && windowVisible) {
            LaunchedEffect(fallbackRevision, visible, clip.isEmpty) {
                if (!visible || clip.isEmpty) return@LaunchedEffect
                val motions = rasterContent.layers.flatMap { listOfNotNull(it.translation, it.revealMotion) }
                if (motions.isEmpty()) return@LaunchedEffect
                val start = withFrameNanos { it }
                do {
                    elapsed = withFrameNanos { (it - start) / 1_000_000L }
                } while (motions.any { it.repeat || elapsed < it.durationMillis })
            }
            Canvas(Modifier.fillMaxSize()) {
                fallbackRevision
                rasterContent.layers.forEach { layer ->
                    val fraction = layer.revealMotion?.valueAt(elapsed) ?: layer.reveal
                    clipRect(left = if (layer.clipFromStart) size.width * fraction else 0f,
                        right = if (layer.clipFromStart) size.width else size.width * fraction) {
                        drawImage(layer.image, topLeft = layer.origin + Offset(layer.translation?.valueAt(elapsed) ?: 0f, 0f))
                    }
                }
            }
        }
    }
}

/** Visibility policy stays shared; native hosts publish only window/overlay facts. */
@Composable
internal fun NaviampRasterEnvironment(
    presenter: NaviampRasterPresenter?, windowVisible: Boolean, overlayVisible: Boolean,
    content: @Composable () -> Unit,
) = NaviampRasterEnvironment(presenter, windowVisible, overlayVisible, false, content)

@Composable
internal fun NaviampRasterEnvironment(
    presenter: NaviampRasterPresenter?, windowVisible: Boolean, overlayVisible: Boolean,
    popupsInOwnedWindows: Boolean,
    content: @Composable () -> Unit,
) {
    val popups = remember { NaviampPopupRegistry() }
    CompositionLocalProvider(
        LocalNaviampPopupRegistry provides popups,
        LocalNaviampWindowVisible provides windowVisible,
        LocalNaviampOwnedPopupWindows provides popupsInOwnedWindows,
        // Same-canvas overlays require the shared fallback. Owned popup windows intrinsically
        // stack above native pixels and draw their own scrim, so the original scene can continue.
        LocalNaviampRasterPresenter provides presenter.takeUnless { popups.visible && !popupsInOwnedWindows },
        LocalNaviampAnimationVisible provides (windowVisible && (!overlayVisible || presenter?.contentBelowOwnedWindows == true)),
        content = content,
    )
}
