package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sin
import kotlinx.coroutines.delay

/** Visible fixture exercising production surfaces, without accounts, audio devices or user data. */
fun main() {
    val output = Path.of(requireNotNull(System.getenv("NAVIAMP_VISUALIZER_PROBE_OUTPUT")
        ?: System.getProperty("naviamp.visualizer.probe.output")))
    Files.createDirectories(output)
    configureNaviampDesktopRasterLayers()
    application {
        var phase by remember { mutableStateOf("static") }
        val draws = remember { AtomicLong() }
        val drawNanos = remember { AtomicLong() }
        val intervals = remember { mutableListOf<Long>() }
        val lastDraw = remember { AtomicLong() }
        val animatedBands = remember { mutableStateOf(List(32) { 0f }) }
        val progress = remember { mutableFloatStateOf(.2f) }
        val state = rememberWindowState(
            width = 1000.dp, height = 740.dp,
            position = WindowPosition.Absolute(800.dp, 400.dp),
        )
        Window(onCloseRequest = ::exitApplication, title = "Naviamp visualizer performance probe", state = state, alwaysOnTop = true) {
            val active = !phase.startsWith("paused") && phase != "static"
            val combined = phase.contains("combined")
            val visualizer = when {
                phase.contains("ocean-ink") -> NaviampVisualizer.OceanOfInk
                phase.contains("analog") -> NaviampVisualizer.AnalogSignalFailure
                else -> NaviampVisualizer.AudioSphere
            }
            // Test-only A/B override. Recreate the production surface when the requested backend changes.
            val backend = when {
                phase.startsWith("native-") -> "true"
                phase.startsWith("skia-") -> "false"
                else -> null
            }
            remember(backend) { backend?.let { System.setProperty("naviamp.visualizer.macosMetal", it) } }
            LaunchedEffect(active) {
                if (!active) return@LaunchedEffect
                var tick = 0
                while (true) {
                    // Deliberately independent of rendering cadence, like upstream FFT sampling.
                    animatedBands.value = List(32) { index ->
                        (.38f + .25f * sin(tick * .19f + index * .37f) + .16f * sin(tick * .31f - index * .13f)).coerceIn(0f, 1f)
                    }
                    tick++
                    delay(50)
                }
            }
            LaunchedEffect(combined) {
                if (combined) while (true) { delay(1000); progress.floatValue += 1f / 600f }
            }
            LaunchedEffect(Unit) {
                var previous = ""
                while (true) {
                    val command = output.resolve("state")
                    val next = if (Files.exists(command)) Files.readString(command).trim() else "static"
                    if (next != previous) {
                        require(next.matches(Regex("[a-zA-Z0-9_-]+")))
                        previous = next
                        phase = next
                        draws.set(0); drawNanos.set(0); intervals.clear(); lastDraw.set(0L)
                        Files.writeString(output.resolve("state-ready"), next)
                    }
                    val sorted = intervals.sorted()
                    val count = draws.get()
                    Files.writeString(output.resolve("draw-metrics"),
                        "phase=$phase draws=$count mean_draw_ms=${drawNanos.get() / count.coerceAtLeast(1) / 1e6}" +
                            " intervals=${sorted.size} p50_interval_ms=${sorted.getOrNull(sorted.size / 2)?.div(1e6)}" +
                            " p95_interval_ms=${sorted.getOrNull((sorted.size * .95).toInt())?.div(1e6)}\n")
                    delay(500)
                }
            }
            Column(Modifier.fillMaxSize().background(Color(0xff24242b)).padding(32.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("Visualizer performance fixture: $phase", color = Color.White)
                Box(Modifier.size(358.dp).background(Color(0xff191922))) {
                    key(backend) {
                        if (phase != "static") PlatformLiveVisualizerSurface(
                            coverArtUrl = null, bandsProvider = { animatedBands.value }, visualizer = visualizer,
                            visualizerColors = NaviampPlayerColors(Color(0xff3d285c), Color(0xff176b72), Color(0xffa14b68), Color(0xff69e3e9)), active = active, tempoBpm = 120,
                            colors = NaviampColors(), lyricStage = LyricMirrorTunnelStage(),
                            modifier = Modifier.fillMaxSize().drawWithContent {
                                val started = System.nanoTime()
                                val previousDraw = lastDraw.getAndSet(started)
                                if (previousDraw != 0L && intervals.size < 20_000) intervals.add(started - previousDraw)
                                drawContent()
                                draws.incrementAndGet(); drawNanos.addAndGet(System.nanoTime() - started)
                            },
                        )
                    }
                }
                NaviampAnimationRegion(Modifier.width(600.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        BouncingTitleText("Long scrolling player metadata with enough text to overflow the fixture viewport and exercise combined animation",
                            color = Color.White, fontSize = 16, marqueeEnabled = combined, modifier = Modifier.width(358.dp))
                        WaveformScrubber(amplitudes = List(512) { ((it * 17) % 101) / 100f }, value = .2f,
                            drawValue = { progress.floatValue }, enabled = true, smoothProgress = combined,
                            durationSeconds = 600.0, colors = NaviampColors(), onValueChange = {}, onValueChangeFinished = {},
                            modifier = Modifier.fillMaxWidth().height(32.dp))
                    }
                }
                Text("Unchanging sibling content", color = Color.White)
            }
        }
    }
}
