package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.lang.management.ManagementFactory
import kotlinx.coroutines.delay

/** Synthetic real-window rendering probe. No provider, audio engine, or user data is loaded. */
fun main() = application {
    var phase by remember { mutableStateOf("static") }
    val rowCount = remember { System.getenv("NAVIAMP_PROBE_ROWS")?.toIntOrNull()?.coerceIn(0, 1000) ?: 150 }
    val cpu = remember { ManagementFactory.getOperatingSystemMXBean() as com.sun.management.OperatingSystemMXBean }
    Window(
        onCloseRequest = ::exitApplication,
        title = "Naviamp animation CPU probe",
        state = rememberWindowState(width = 1000.dp, height = 740.dp),
    ) {
        val marquee = phase == "marquee" || phase == "combined"
        val smooth = phase == "waveform" || phase == "combined"
        Row(Modifier.fillMaxSize().background(Color(0xff24242b)).padding(24.dp)) {
            Column(Modifier.width(280.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(280.dp).background(Color(0xff775533)))
                repeat(3) { index ->
                    BouncingTitleText(
                        "Long scrolling player metadata $index with enough text to overflow its viewport",
                        color = Color.White, fontSize = 16, marqueeEnabled = marquee,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                WaveformScrubber(
                    amplitudes = List(512) { ((it * 17) % 101) / 100f },
                    value = 0.2f, enabled = true, smoothProgress = smooth,
                    durationSeconds = 300.0, colors = NaviampColors(),
                    onValueChange = {}, onValueChangeFinished = {},
                    modifier = Modifier.fillMaxWidth().height(32.dp),
                )
                Text(phase, color = Color.White)
            }
            Column(Modifier.weight(1f).padding(start = 24.dp).verticalScroll(rememberScrollState())) {
                repeat(rowCount) { index ->
                    Text("Library row $index — artist and album metadata", color = Color.White,
                        modifier = Modifier.fillMaxWidth().padding(12.dp).background(Color(0xff303039)))
                }
            }
        }
        LaunchedEffect(Unit) {
            println("ANIMATION_PROBE rows=$rowCount phase,cpu_percent")
            for (next in listOf("static", "marquee", "waveform", "combined")) {
                phase = next
                delay(5_000)
                val startCpu = cpu.processCpuTime
                val start = System.nanoTime()
                delay(10_000)
                val percent = (cpu.processCpuTime - startCpu).toDouble() / (System.nanoTime() - start) * 100.0
                println("ANIMATION_PROBE $next,$percent")
            }
            exitApplication()
        }
    }
}
