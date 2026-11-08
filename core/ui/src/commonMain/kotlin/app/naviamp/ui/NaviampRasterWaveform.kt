package app.naviamp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.time.TimeSource

@Composable
internal fun NaviampRasterWaveform(
    value: () -> Float, smooth: Boolean, durationSeconds: Double?, identity: String?,
    drawingKey: Any, draw: DrawScope.(Float) -> Unit,
) {
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val images = remember(viewport, density, direction, drawingKey) {
        if (viewport.width <= 0 || viewport.height <= 0) emptyList() else listOf(0f, 1f).map { progress ->
            ImageBitmap(viewport.width, viewport.height).also { image ->
                CanvasDrawScope().draw(density, direction, Canvas(image), Size(image.width.toFloat(), image.height.toFloat())) {
                    draw(progress)
                }
            }
        }
    }
    val clock = remember(identity) { TimeSource.Monotonic.markNow() }
    val prediction = remember(identity) { NaviampProgressPrediction() }
    val currentValue by rememberUpdatedState(value)
    val content = remember(images, smooth, durationSeconds, identity) {
        var revision = -1L
        var layers = emptyList<NaviampRasterLayer>()
        NaviampRasterContent(value = { currentValue() }, render = { value ->
            val start = prediction.update(value, smooth, durationSeconds, clock.elapsedNow().inWholeMilliseconds)
            // Telemetry remains live; an equivalent compositor timeline needs no new submission.
            if (revision == prediction.revision) return@NaviampRasterContent layers
            revision = prediction.revision
            val motion = if (smooth) progressLayerMotion(start, durationSeconds) else null
            val started = TimeSource.Monotonic.markNow()
            layers = if (images.isEmpty()) emptyList() else listOf(
                NaviampRasterLayer(images[0], reveal = start, revealMotion = motion, clipFromStart = true, revealStarted = started),
                NaviampRasterLayer(images[1], reveal = start, revealMotion = motion, revealStarted = started),
            )
            layers
        })
    }
    Box(Modifier.fillMaxSize().onSizeChanged { viewport = it }) {
        NaviampAnimatedRaster(content, Modifier.fillMaxSize(), with(density) { 4.dp.toPx() })
    }
}
