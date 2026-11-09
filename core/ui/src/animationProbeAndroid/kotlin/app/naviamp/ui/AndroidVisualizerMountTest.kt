package app.naviamp.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Android window/CPU/capture operations; shared code owns the complete fixture plan. */
class VisualizerMountActivity : ComponentActivity() {
    companion object { var done = CountDownLatch(1) }
    internal val results = mutableListOf<Pair<NaviampVisualizerMountSample, Long>>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        var windowFrames = 0L
        val metrics = android.view.Window.OnFrameMetricsAvailableListener { _, _, _ -> windowFrames++ }
        window.addOnFrameMetricsAvailableListener(metrics, Handler(Looper.getMainLooper()))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(filesDir, "visualizer-mount").apply { mkdirs() }
        setContent {
            NaviampAndroidRasterHost {
                NaviampVisualizerMountProbe(
                    cpuNanos = { Process.getElapsedCpuTime() * 1_000_000L },
                    onBegin = { windowFrames = 0L },
                    onResult = { sample ->
                        results += sample to windowFrames
                        val message = "${sample.visualizer.name} cpu=${sample.cpuPercent} root=${sample.rootDraws} " +
                            "sibling=${sample.siblingDraws} window=$windowFrames"
                        Log.i("NaviampVisualizerMount", message)
                        File(directory, "results.txt").appendText(message + "\n")
                    },
                    capture = { name, bounds -> withContext(Dispatchers.IO) {
                        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                        try {
                            File(directory, "$name.png").outputStream().use {
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                            }
                            File(directory, "$name-bounds.txt").writeText(
                                "${bounds.left},${bounds.top},${bounds.width},${bounds.height}")
                        } finally { bitmap.recycle() }
                    } },
                    finished = { window.removeOnFrameMetricsAvailableListener(metrics); done.countDown() },
                )
            }
        }
    }
}

class AndroidVisualizerMountTest {
    @Test fun replacementPresentsMovingNativePixelsWithoutRecurringParentFrames() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        VisualizerMountActivity.done = CountDownLatch(1)
        val activity = instrumentation.startActivitySync(Intent(instrumentation.context, VisualizerMountActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as VisualizerMountActivity
        try {
            assertTrue("Native mount fixture completed", VisualizerMountActivity.done.await(180, TimeUnit.SECONDS))
            assertEquals("Every shared phase must run", 3, activity.results.size)
            val directory = File(activity.filesDir, "visualizer-mount")
            for ((sample, windowFrames) in activity.results) {
                val phase = sample.visualizer.name
                if (sample.visualizer.usesNativeRenderer) {
                    assertEquals("$phase must keep the parent cached", 0L, sample.rootDraws)
                    assertEquals("$phase must keep its sibling cached", 0L, sample.siblingDraws)
                    assertEquals("$phase must not continually repaint the window", 0L, windowFrames)
                }
                val bounds = File(directory, "$phase-before-bounds.txt").readText().split(',').map(String::toFloat)
                val left = bounds[0].toInt() + 4
                val top = bounds[1].toInt() + 4
                val width = bounds[2].toInt() - 8
                val height = bounds[3].toInt() - 8
                val before = requireNotNull(BitmapFactory.decodeFile(File(directory, "$phase-before.png").path))
                val after = requireNotNull(BitmapFactory.decodeFile(File(directory, "$phase-after.png").path))
                try {
                    assertTrue("$phase has valid shared capture bounds", width > 0 && height > 0 && left >= 0 && top >= 0 &&
                        left + width <= before.width && top + height <= before.height &&
                        before.width == after.width && before.height == after.height)
                    val first = IntArray(width * height)
                    val second = IntArray(width * height)
                    before.getPixels(first, 0, width, left, top, width, height)
                    after.getPixels(second, 0, width, left, top, width, height)
                    assertTrue("$phase must contain rendered pixels before the measurement", first.any { it != first[0] })
                    assertTrue("$phase must contain rendered pixels after the measurement", second.any { it != second[0] })
                    assertTrue("$phase must present changing pixels instead of a blank/frozen surface",
                        first.indices.count { first[it] != second[it] } > 100)
                } finally { before.recycle(); after.recycle() }
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
}
