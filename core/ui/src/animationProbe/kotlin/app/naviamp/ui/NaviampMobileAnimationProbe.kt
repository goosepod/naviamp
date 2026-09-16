package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.time.TimeSource

/** Opt-in diagnostic fixture, excluded from ordinary builds; uses real shared player components. */
@Composable
fun NaviampMobileAnimationProbe(cpuNanos: () -> Long, report: (String) -> Unit, finished: () -> Unit) {
    var phase by remember { mutableStateOf("static") }
    val counts = remember { longArrayOf(0, 0) }
    val marquee = phase == "marquee" || phase == "combined"
    val smooth = phase == "waveform" || phase == "combined"
    Column(Modifier.fillMaxSize().background(Color(0xff24242b)).padding(24.dp)
        .drawWithContent { counts[0]++; drawContent() }) {
        Box(Modifier.size(120.dp).background(Color(0xff775533)))
        repeat(3) { index ->
            BouncingTitleText("Long scrolling player metadata $index with enough text to overflow its viewport",
                color = Color.White, fontSize = 16, marqueeEnabled = marquee,
                modifier = Modifier.width(280.dp))
        }
        WaveformScrubber(List(512) { ((it * 17) % 101) / 100f }, value = .2f,
            enabled = true, smoothProgress = smooth, durationSeconds = 300.0,
            colors = NaviampColors(), onValueChange = {}, onValueChangeFinished = {},
            modifier = Modifier.width(280.dp).height(32.dp))
        Text(phase, color = Color.White)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())
            .drawWithContent { counts[1]++; drawContent() }) {
            repeat(150) { Text("Library row $it — artist and album metadata", color = Color.White,
                modifier = Modifier.fillMaxWidth().padding(12.dp)) }
        }
    }
    LaunchedEffect(Unit) {
        for (next in listOf("static", "marquee", "waveform", "combined", "restored-static")) {
            phase = next
            delay(5_000)
            report("BEGIN $next")
            val before = counts.copyOf()
            val cpu = cpuNanos()
            val wall = TimeSource.Monotonic.markNow()
            delay(10_000)
            val percent = (cpuNanos() - cpu).toDouble() / wall.elapsedNow().inWholeNanoseconds * 100
            report("RESULT $next cpu_percent=$percent root_draws=${counts[0]-before[0]} sibling_draws=${counts[1]-before[1]}")
        }
        finished()
    }
}
