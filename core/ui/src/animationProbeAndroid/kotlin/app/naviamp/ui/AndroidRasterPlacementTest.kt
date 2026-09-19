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
    @Volatile var expectedBounds = Rect.Zero

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            val bitmap = remember {
                ImageBitmap(800, 80).also { image ->
                    val canvas = Canvas(image)
                    repeat(20) { stripe ->
                        canvas.drawRect(Rect(stripe * 40f, 0f, (stripe + 1) * 40f, 80f),
                            Paint().apply { color = if (stripe % 2 == 0) Color.Red else Color.Blue })
                    }
                }
            }
            NaviampAndroidRasterHost {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    if (shown.value) {
                        val motion = if (moving.value) NaviampLayerMotion(
                            listOf(0f, -80f), listOf(0L, 2000L), repeat = true,
                        ) else null
                        NaviampAnimatedRaster(listOf(NaviampRasterLayer(bitmap, translation = motion)),
                            Modifier.offset(if (shifted.value) 110.dp else 60.dp, if (shifted.value) 240.dp else 160.dp)
                                .size(if (shifted.value) 100.dp else 180.dp, 60.dp)
                                .onGloballyPositioned { expectedBounds = it.boundsInWindow() })
                    }
                }
            }
        }
    }
}

class AndroidRasterPlacementTest {
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
