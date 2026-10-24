package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.time.TimeSource

/** Opt-in shared real-surface probe; native hosts provide only CPU, capture and reporting effects. */
@Composable
fun NaviampMobileVisualizerProbe(
    cpuNanos: () -> Long, report: (String) -> Unit,
    capture: suspend (String, Rect) -> Unit, finished: () -> Unit,
) {
    var phase by remember { mutableStateOf("static") }
    val counters = remember { LongArray(4) }
    val intervals = remember { mutableListOf<Long>() }
    val clock = remember { TimeSource.Monotonic.markNow() }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val bands = remember { mutableStateOf(List(32) { 0f }) }
    val direct = phase.contains("direct")
    val active = phase != "static" && !phase.startsWith("paused")
    val effect = when {
        phase.contains("analog") -> NaviampVisualizer.AnalogSignalFailure
        phase.contains("ocean") -> NaviampVisualizer.OceanOfInk
        else -> NaviampVisualizer.AudioSphere
    }
    val pixels = if (phase.contains("large")) 640 else 358
    val size = with(LocalDensity.current) { pixels.toDp() }
    val presenter = LocalNaviampGpuVisualizerPresenter.current
    LaunchedEffect(active) {
        if (active) {
            var tick = 0
            while (true) {
                bands.value = List(32) { index ->
                    (.38f + .25f * sin(tick * .19f + index * .37f) + .16f * sin(tick * .31f - index * .13f)).coerceIn(0f, 1f)
                }
                tick++; delay(50)
            }
        }
    }
    CompositionLocalProvider(
        LocalNaviampGpuVisualizerPresenter provides presenter.takeIf { direct },
        LocalNaviampVisualizerFps provides if (phase.contains("45")) 45 else 60,
        LocalNaviampVisualizerSubmissionObserver provides { accepted, _ ->
            if (accepted) {
                val now = clock.elapsedNow().inWholeNanoseconds
                if (counters[3] != 0L) intervals.add(now - counters[3])
                counters[3] = now; counters[1]++
            }
        },
    ) {
        Column(Modifier.fillMaxSize().background(Color(0xff24242b)).padding(24.dp)
            .drawWithContent { counters[0]++; drawContent() }, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Visualizer probe: $phase ($pixels pixels)", color = Color.White)
            Box(Modifier.size(size).background(Color(0xff191922)).onGloballyPositioned { bounds = it.boundsInWindow() }) {
                key(direct, effect) {
                    if (phase != "static") NaviampPresentedVisualizerSurface(
                        null, { bands.value }, effect,
                        NaviampPlayerColors(Color(0xff3d285c), Color(0xff176b72), Color(0xffa14b68), Color(0xff69e3e9)),
                        active, 120, NaviampColors(), LyricMirrorTunnelStage(), Modifier.fillMaxSize(),
                    )
                }
            }
            Text("Unchanging sibling", color = Color.White,
                modifier = Modifier.drawWithContent { counters[2]++; drawContent() })
        }
    }
    LaunchedEffect(Unit) {
        for (next in listOf("static", "inline-sphere", "direct-sphere", "direct45-sphere",
            "inline-analog", "direct-analog", "direct45-analog", "direct-large-analog",
            "inline-ocean", "direct-ocean", "direct45-ocean", "direct-large-ocean", "paused-direct-analog", "static")) {
            phase = next; delay(5_000)
            capture("$next-before", bounds)
            intervals.clear(); counters[3] = 0L
            val before = counters.copyOf()
            report("BEGIN $next")
            val cpu = cpuNanos(); val wall = TimeSource.Monotonic.markNow()
            delay(10_000)
            val seconds = wall.elapsedNow().inWholeNanoseconds / 1e9
            val percent = (cpuNanos() - cpu) / (seconds * 1e9) * 100
            val sorted = intervals.sorted()
            report("RESULT $next cpu_percent=$percent root_draws=${counters[0]-before[0]}" +
                " submissions=${counters[1]-before[1]} sibling_draws=${counters[2]-before[2]}" +
                " seconds=$seconds p50_interval_ms=${sorted.getOrNull(sorted.size / 2)?.div(1e6)}" +
                " p95_interval_ms=${sorted.getOrNull((sorted.size * .95).toInt())?.div(1e6)}")
            check(!next.contains("direct") || next.startsWith("paused") || counters[1] - before[1] > 0) {
                "Native visualizer probe fell back: $next"
            }
            capture("$next-after", bounds)
        }
        finished()
    }
}
