package app.naviamp.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import android.view.FrameMetrics
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Instrumentation/window/CPU/screenshot APIs only; the fixture plan and cadence are shared. */
class VisualizerProbeActivity : ComponentActivity() {
    companion object { var done = CountDownLatch(1) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        var frames = 0L
        var gpu = 0L
        val metrics = android.view.Window.OnFrameMetricsAvailableListener { _, sample, _ ->
            frames++
            if (android.os.Build.VERSION.SDK_INT >= 31) gpu += sample.getMetric(FrameMetrics.GPU_DURATION).coerceAtLeast(0)
        }
        window.addOnFrameMetricsAvailableListener(metrics, Handler(Looper.getMainLooper()))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = File(filesDir, "visualizer-probe").apply { mkdirs() }
        setContent {
            NaviampAndroidRasterHost {
                checkNotNull(LocalNaviampGpuVisualizerPresenter.current) { "Native GPU presenter unavailable in fixture" }
                CompositionLocalProvider(LocalNaviampGpuCreationFailureObserver provides {
                    Log.e("NaviampGpuVisualizer", "Native region creation failed", it)
                }) {
                NaviampMobileVisualizerProbe(
                    cpuNanos = { Process.getElapsedCpuTime() * 1_000_000L },
                    report = { line ->
                        if (line.startsWith("BEGIN")) { frames = 0; gpu = 0 }
                        val message = "$line frames=$frames parent_gpu_ns=$gpu"
                        Log.i("NaviampVisualizerProbe", message)
                        File(output, "results.txt").appendText(message + "\n")
                    },
                    capture = { name, bounds -> withContext(Dispatchers.IO) {
                        val screen = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                        File(output, "$name.png").outputStream().use {
                            screen.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                        }
                        File(output, "$name-bounds.txt").writeText("${bounds.left},${bounds.top},${bounds.width},${bounds.height}")
                        screen.recycle()
                    } },
                    finished = { window.removeOnFrameMetricsAvailableListener(metrics); done.countDown() },
                )
                }
            }
        }
    }
}

class AndroidVisualizerProbeTest {
    @Test fun visibleVisualizerCpu() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        VisualizerProbeActivity.done = CountDownLatch(1)
        val activity = instrumentation.startActivitySync(Intent(instrumentation.context, VisualizerProbeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as VisualizerProbeActivity
        try { assertTrue("Visualizer probe completed", VisualizerProbeActivity.done.await(300, TimeUnit.SECONDS)) }
        finally { instrumentation.runOnMainSync { activity.finish() } }
    }
}
