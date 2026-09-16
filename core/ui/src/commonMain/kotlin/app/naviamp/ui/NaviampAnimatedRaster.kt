package app.naviamp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
    fun create(): NaviampRasterRegion
}

internal interface NaviampRasterRegion {
    fun present(layers: List<NaviampRasterLayer>, bounds: Rect, clip: Rect, cornerRadius: Float): Boolean
    fun translationX(layerIndex: Int): Float? = null
    fun close()
}

internal val LocalNaviampRasterPresenter = staticCompositionLocalOf<NaviampRasterPresenter?> { null }
internal val LocalNaviampAnimationVisible = staticCompositionLocalOf { true }

internal class NaviampRasterPosition {
    var translationX: () -> Float = { 0f }
}

/** Compose retains all input/semantics. Presentation effects never install an input surface. */
@Composable
internal fun NaviampAnimatedRaster(layers: List<NaviampRasterLayer>, modifier: Modifier, cornerRadius: Float = 0f, position: NaviampRasterPosition? = null) {
    val visible = LocalNaviampAnimationVisible.current
    val presenter = LocalNaviampRasterPresenter.current.takeIf { visible }
    val region = remember(presenter) { presenter?.create() }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    var clip by remember { mutableStateOf(Rect.Zero) }
    var presented by remember(region) { mutableStateOf(false) }
    var elapsed by remember(layers) { mutableLongStateOf(0L) }
    SideEffect { position?.translationX = {
        if (presented) region?.translationX(0) ?: 0f else layers.firstOrNull()?.translation?.valueAt(elapsed) ?: 0f
    } }
    DisposableEffect(region) { onDispose { region?.close() } }
    LaunchedEffect(region, layers, bounds, clip, cornerRadius) {
        presented = region?.present(layers, bounds, clip, cornerRadius) == true
    }
    Box(modifier.onGloballyPositioned {
        bounds = Rect(it.positionInWindow(), androidx.compose.ui.geometry.Size(it.size.width.toFloat(), it.size.height.toFloat()))
        clip = it.boundsInWindow()
    }) {
        if (!presented) {
            LaunchedEffect(layers, visible, clip.isEmpty) {
                if (!visible || clip.isEmpty) return@LaunchedEffect
                val motions = layers.flatMap { listOfNotNull(it.translation, it.revealMotion) }
                if (motions.isEmpty()) return@LaunchedEffect
                val start = withFrameNanos { it }
                do {
                    elapsed = withFrameNanos { (it - start) / 1_000_000L }
                } while (motions.any { it.repeat || elapsed < it.durationMillis })
            }
            Canvas(Modifier.fillMaxSize()) {
                layers.forEach { layer ->
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
) {
    CompositionLocalProvider(
        LocalNaviampRasterPresenter provides presenter,
        LocalNaviampAnimationVisible provides (windowVisible && !overlayVisible),
        content = content,
    )
}
