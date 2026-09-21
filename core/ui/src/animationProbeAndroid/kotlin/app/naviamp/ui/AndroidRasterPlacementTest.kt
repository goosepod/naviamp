package app.naviamp.ui

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test

/** Native-boundary fixture: arbitrary cached pixels at a non-zero shared window position. */
class RasterPlacementActivity : ComponentActivity() {
    val shifted = mutableStateOf(false)
    val shown = mutableStateOf(true)
    val moving = mutableStateOf(false)
    val scrolling = mutableStateOf(false)
    val replacing = mutableStateOf(false)
    val frame = mutableStateOf(0L)
    @Volatile var expectedBounds = Rect.Zero

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            LaunchedEffect(scrolling.value, replacing.value) {
                if (scrolling.value || replacing.value) {
                    val start = withFrameNanos { it }
                    while (true) frame.value = withFrameNanos { (it - start) / 1_000_000L }
                }
            }
            val generation = if (replacing.value) frame.value / 80 else 0L
            val travel = if (scrolling.value) {
                val phase = (frame.value % 1600).toFloat() / 800f
                (if (phase < 1) phase else 2 - phase) * 180
            } else 0f
            val bitmap = remember(generation) {
                // Exercise both buffer reuse and replacement when a new title has a different width.
                ImageBitmap(if (generation % 3 == 0L) 800 else 840, 80).also { image ->
                    val canvas = Canvas(image)
                    repeat(21) { stripe ->
                        canvas.drawRect(Rect(stripe * 40f, 0f, (stripe + 1) * 40f, 80f),
                            Paint().apply { color = if ((stripe + generation) % 2 == 0L) Color.Red else Color.Blue })
                    }
                }
            }
            NaviampAndroidRasterHost {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    if (scrolling.value || replacing.value) Box(Modifier.offset(20.dp, (160 + travel).dp)
                        .size(10.dp, 30.dp).background(Color.Green))
                    if (shown.value) {
                        val motion = if (moving.value) NaviampLayerMotion(
                            listOf(0f, -80f), listOf(0L, 2000L), repeat = true,
                        ) else null
                        NaviampAnimatedRaster(listOf(NaviampRasterLayer(bitmap, translation = motion)),
                            Modifier.offset(if (shifted.value) 110.dp else 60.dp, if (shifted.value) 240.dp else (160 + travel).dp)
                                .size(if (shifted.value) 100.dp else 180.dp, 60.dp)
                                .onGloballyPositioned { expectedBounds = it.boundsInWindow() })
                    }
                }
            }
        }
    }
}

class AndroidRasterPlacementTest {
    @Test fun scrollingAndImageReplacementStayAttachedWithoutBlankFrames() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.context, RasterPlacementActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as RasterPlacementActivity
        try {
            Thread.sleep(1200)
            instrumentation.runOnMainSync { activity.scrolling.value = true; activity.replacing.value = true }
            Thread.sleep(400)
            val positions = mutableSetOf<Int>()
            repeat(45) {
                val capture = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                var reference = -1
                var raster = -1
                for (y in 100 until capture.height - 100) {
                    for (x in 30 until capture.width / 2 step 3) {
                        val pixel = capture.getPixel(x, y)
                        val r = android.graphics.Color.red(pixel)
                        val g = android.graphics.Color.green(pixel)
                        val b = android.graphics.Color.blue(pixel)
                        if (reference < 0 && g > 220 && r < 30 && b < 30) reference = y
                        if (raster < 0 && g < 30 && ((r > 220 && b < 30) || (b > 220 && r < 30))) raster = y
                    }
                    if (reference >= 0 && raster >= 0) break
                }
                capture.recycle()
                assertTrue("The composed reference must remain visible", reference >= 0)
                assertTrue("Raster must not blink during replacement", raster >= 0)
                assertTrue("Native pixels drifted from the scrolling layout: $raster vs $reference",
                    kotlin.math.abs(raster - reference) <= 2)
                positions += reference
            }
            assertTrue("Scrolling must actually move the content", positions.size > 10)
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun cachedPixelsKeepTheirWindowPlacementClippingAndLifecycle() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.context, RasterPlacementActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as RasterPlacementActivity
        fun screenshot(): Bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        fun colored(pixel: Int): Boolean =
            (android.graphics.Color.red(pixel) > 220 && android.graphics.Color.green(pixel) < 30 && android.graphics.Color.blue(pixel) < 30) ||
                (android.graphics.Color.blue(pixel) > 220 && android.graphics.Color.red(pixel) < 30 && android.graphics.Color.green(pixel) < 30)
        fun verifyPlacement() {
            Thread.sleep(1200)
            val bounds = activity.expectedBounds
            val capture = screenshot()
            assertTrue("Fixture must be positioned away from the window origin", bounds.left > 100 && bounds.top > 100)
            assertTrue("Cached pixels must appear inside shared layout bounds: $bounds",
                colored(capture.getPixel(bounds.left.toInt() + 8, bounds.top.toInt() + 8)))
            var escaped = 0
            for (y in 0 until capture.height step 4) for (x in 0 until capture.width step 4) {
                if (colored(capture.getPixel(x, y)) &&
                    (x < bounds.left - 1 || x > bounds.right + 1 || y < bounds.top - 1 || y > bounds.bottom + 1)) escaped++
            }
            assertTrue("Cached pixels must not escape their shared clip ($escaped samples)", escaped == 0)
            capture.recycle()
        }
        try {
            verifyPlacement()
            instrumentation.runOnMainSync { activity.shifted.value = true }
            verifyPlacement()
            instrumentation.runOnMainSync { activity.shown.value = false }
            Thread.sleep(400)
            val hidden = screenshot()
            assertTrue("Closing a region removes its pixels", !colored(hidden.getPixel(
                activity.expectedBounds.left.toInt() + 8, activity.expectedBounds.top.toInt() + 8)))
            hidden.recycle()
            instrumentation.runOnMainSync { activity.shown.value = true }
            verifyPlacement()
            instrumentation.runOnMainSync { activity.moving.value = true }
            Thread.sleep(400)
            val first = screenshot()
            Thread.sleep(500)
            val second = screenshot()
            val bounds = activity.expectedBounds
            var changed = 0
            for (y in bounds.top.toInt() until minOf(bounds.bottom.toInt(), bounds.top.toInt() + 80) step 4) {
                for (x in bounds.left.toInt() until bounds.right.toInt() step 4) {
                    if (first.getPixel(x, y) != second.getPixel(x, y)) changed++
                }
            }
            assertTrue("Motion must change pixels inside the intended region", changed > 100)
            first.recycle(); second.recycle()
            verifyPlacement()
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
}
