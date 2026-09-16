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
    value: Float, smooth: Boolean, durationSeconds: Double?, identity: String?,
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
    val layers = remember(images, value, smooth, durationSeconds, identity) {
        val start = prediction.update(value, smooth, durationSeconds, clock.elapsedNow().inWholeMilliseconds)
        val motion = if (smooth) progressLayerMotion(start, durationSeconds) else null
        if (images.isEmpty()) emptyList() else listOf(
            NaviampRasterLayer(images[0], reveal = start, revealMotion = motion, clipFromStart = true),
            NaviampRasterLayer(images[1], reveal = start, revealMotion = motion),
        )
    }
    Box(Modifier.fillMaxSize().onSizeChanged { viewport = it }) {
        NaviampAnimatedRaster(layers, Modifier.fillMaxSize(), with(density) { 4.dp.toPx() })
    }
}
