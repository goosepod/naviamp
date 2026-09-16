package app.naviamp.ui

import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.FrameMetrics
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.assertTrue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AnimationProbeActivity : ComponentActivity() {
    companion object { var done = CountDownLatch(1) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        var frames = 0L
        var gpu = 0L
        var total = 0L
        val metrics = android.view.Window.OnFrameMetricsAvailableListener { _, sample, _ ->
            frames++
            total += sample.getMetric(FrameMetrics.TOTAL_DURATION)
            if (android.os.Build.VERSION.SDK_INT >= 31) gpu += sample.getMetric(FrameMetrics.GPU_DURATION).coerceAtLeast(0)
        }
        window.addOnFrameMetricsAvailableListener(metrics, Handler(Looper.getMainLooper()))
        setContent {
            NaviampAndroidRasterHost {
                NaviampMobileAnimationProbe(
                    cpuNanos = { Process.getElapsedCpuTime() * 1_000_000L },
                    report = { line ->
                        if (line.startsWith("BEGIN")) { frames = 0; gpu = 0; total = 0 }
                        Log.i("NaviampAnimationProbe", "$line frames=$frames gpu_ns=$gpu total_frame_ns=$total")
                    },
                    finished = { window.removeOnFrameMetricsAvailableListener(metrics); done.countDown() },
                )
            }
        }
    }
}

class AndroidAnimationProbeTest {
    @Test fun visiblePlayerAnimationCpu() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        AnimationProbeActivity.done = CountDownLatch(1)
        val intent = Intent(instrumentation.context, AnimationProbeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(intent) as AnimationProbeActivity
        try {
            // Marquee warm-up starts at 15 seconds. These compositor captures prove that cached
            // pixels are visible and moving without contaminating the measured interval at 20s.
            Thread.sleep(16_000)
            val first = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            Thread.sleep(750)
            val second = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            assertTrue("Marquee compositor output changed", changedPixelCount(first, second) > 100)
            assertTrue("Probe completed", AnimationProbeActivity.done.await(120, TimeUnit.SECONDS))
        }
        finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    private fun changedPixelCount(first: android.graphics.Bitmap, second: android.graphics.Bitmap): Int {
        val width = minOf(first.width, second.width)
        val height = minOf(first.height, second.height)
        var changed = 0
        for (y in 0 until height step 2) {
            for (x in 0 until width step 2) {
                if (first.getPixel(x, y) != second.getPixel(x, y)) changed++
            }
        }
        return changed
    }
}
