package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.time.TimeSource

internal data class NaviampVisualizerMountSample(
    val visualizer: NaviampVisualizer,
    val cpuPercent: Double,
    val rootDraws: Long,
    val siblingDraws: Long,
)

/** Shared real-window fixture; hosts only supply CPU, screenshots and window observations. */
@Composable
internal fun NaviampVisualizerMountProbe(
    cpuNanos: () -> Long,
    onBegin: (NaviampVisualizer) -> Unit,
    onResult: (NaviampVisualizerMountSample) -> Unit,
    capture: suspend (String, Rect) -> Unit,
    finished: () -> Unit,
) {
    var selected by remember { mutableStateOf(NaviampVisualizer.AudioSphere) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val bands = remember { mutableStateOf(List(32) { 0f }) }
    val draws = remember { LongArray(2) }
    val size = with(LocalDensity.current) { 358.toDp() }
    LaunchedEffect(Unit) {
        var tick = 0
        while (true) {
            bands.value = List(32) { index ->
                (.38f + .25f * sin(tick * .19f + index * .37f) + .16f * sin(tick * .31f - index * .13f))
                    .coerceIn(0f, 1f)
            }
            tick++
            delay(50)
        }
    }
    Column(Modifier.fillMaxSize().background(Color(0xff24242b)).padding(24.dp)
        .drawWithContent { draws[0]++; drawContent() }, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Native mount: ${selected.label}", color = Color.White)
        Box(Modifier.size(size).background(Color(0xff191922)).onGloballyPositioned { bounds = it.boundsInWindow() }) {
            // Keep the call site, as the real player does, so replacement also exercises native
            // renderer/view resource ownership instead of replacing the entire composition.
            PlatformLiveVisualizerSurface(null, { bands.value }, selected,
                NaviampPlayerColors.fallback(NaviampColors.Dark), true, 120, NaviampColors.Dark,
                LyricMirrorTunnelStage(), Modifier.fillMaxSize())
        }
        Text("Unchanging sibling", color = Color.White,
            modifier = Modifier.drawWithContent { draws[1]++; drawContent() })
    }
    LaunchedEffect(Unit) {
        for (visualizer in listOf(NaviampVisualizer.AudioSphere, NaviampVisualizer.AnalogSignalFailure,
            NaviampVisualizer.OceanOfInk)) {
            selected = visualizer
            delay(5_000)
            capture("${visualizer.name}-before", bounds)
            val before = draws.copyOf()
            onBegin(visualizer)
            val cpu = cpuNanos()
            val wall = TimeSource.Monotonic.markNow()
            delay(10_000)
            val seconds = wall.elapsedNow().inWholeNanoseconds / 1e9
            onResult(NaviampVisualizerMountSample(visualizer, (cpuNanos() - cpu) / (seconds * 1e9) * 100,
                draws[0] - before[0], draws[1] - before[1]))
            capture("${visualizer.name}-after", bounds)
        }
        finished()
    }
}
