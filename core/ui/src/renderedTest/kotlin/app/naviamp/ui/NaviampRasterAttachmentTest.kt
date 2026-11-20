package app.naviamp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NaviampRasterAttachmentTest {
    @Test fun lateNativeAttachmentRefreshesUnchangedContentOnce() = runComposeUiTest {
        mainClock.autoAdvance = false
        var ready: (() -> Unit)? = null
        var submissions = 0
        var draws = 0
        val region = object : NaviampRasterRegion {
            override fun present(layers: List<NaviampRasterLayer>, bounds: Rect, clip: Rect, cornerRadius: Float): Boolean {
                submissions++
                return true
            }
            override fun whenReady(callback: () -> Unit) { ready = callback }
            override fun close() {}
        }
        val presenter = object : NaviampRasterPresenter {
            override fun create() = region
        }
        val layers = listOf(NaviampRasterLayer(ImageBitmap(2, 2)))
        setContent {
            NaviampRasterEnvironment(presenter, true, false) {
                Box(Modifier.size(100.dp).drawWithContent { draws++; drawContent() }) {
                    NaviampAnimatedRaster(layers, Modifier.size(20.dp))
                }
            }
        }
        mainClock.advanceTimeBy(100)
        waitForIdle()
        var previousSubmissions = 0
        var previousDraws = 0
        runOnIdle {
            previousSubmissions = submissions
            previousDraws = draws
            assertNotNull(ready).invoke()
        }
        mainClock.advanceTimeBy(100)
        waitForIdle()
        runOnIdle {
            assertEquals(previousSubmissions + 1, submissions, "Attachment must resubmit unchanged pixels")
            assertTrue(draws > previousDraws, "Attachment must invalidate the parent after the initial draw")
            previousDraws = draws
        }
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        runOnIdle { assertEquals(previousDraws, draws, "A ready static attachment must not start a repaint loop") }
    }
}
