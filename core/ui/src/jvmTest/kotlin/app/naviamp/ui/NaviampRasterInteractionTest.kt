package app.naviamp.ui

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class NaviampRasterInteractionTest {
    @Test fun nativeReadinessResubmitsTheLatestLayoutWithoutAContentChange() = runComposeUiTest {
        val presenter = RecordingPresenter().apply { ready = false }
        setContent {
            CompositionLocalProvider(LocalNaviampRasterPresenter provides presenter) {
                BouncingTitleText("Cached title", Color.White, 14,
                    marqueeEnabled = false, modifier = Modifier.offset(30.dp, 40.dp).width(100.dp))
            }
        }
        waitForIdle()
        var before = 0
        runOnIdle {
            before = presenter.presentations
            presenter.ready = true
            presenter.notifyReady()
        }
        waitForIdle()
        runOnIdle {
            assertTrue(presenter.presentations > before)
            assertTrue(presenter.bounds.left > 0 && presenter.bounds.top > 0)
        }
    }

    @Test fun nativePresentationReceivesWindowBoundsAfterMovingAndResizing() = runComposeUiTest {
        val presenter = RecordingPresenter()
        val left = mutableStateOf(40.dp)
        val width = mutableStateOf(150.dp)
        var expected = Rect.Zero
        val drawn = mutableListOf<Pair<Rect, Rect>>()
        setContent {
            Box(Modifier.size(500.dp)) {
                CompositionLocalProvider(LocalNaviampRasterPresenter provides presenter) {
                    BouncingTitleText("A long cached title with enough text to scroll", Color.White, 14,
                        marqueeEnabled = true, modifier = Modifier.offset(left.value, 100.dp)
                            .width(width.value).onGloballyPositioned { expected = it.boundsInWindow() }
                            .drawWithContent { drawn += expected to presenter.bounds; drawContent() })
                }
            }
        }
        waitForIdle()
        runOnIdle {
            assertTrue(expected.left > 0f && expected.top > 0f)
            assertEquals(expected, presenter.bounds)
            left.value = 80.dp
            width.value = 100.dp
        }
        waitForIdle()
        runOnIdle {
            assertEquals(expected, presenter.bounds)
            assertTrue(drawn.isNotEmpty())
            drawn.filter { !it.first.isEmpty }.forEach { (layout, native) -> assertEquals(layout, native) }
        }
    }

    @Test fun movingArtistHitTestUsesPresentedPosition() = runComposeUiTest {
        val presenter = RecordingPresenter()
        val selected = mutableListOf<String>()
        var secondX = 0f
        setContent {
            val text = AnnotatedString("First Artist     Second Artist")
            val style = TextStyle(color = Color.White, fontSize = 16.sp)
            secondX = rememberTextMeasurer().measure(text, style, softWrap = false).getBoundingBox(17).left
            CompositionLocalProvider(LocalNaviampRasterPresenter provides presenter) {
                NaviampRasterText(text, style, 25.dp, true, true, Modifier.width(120.dp).testTag("artists"),
                    listOf(NaviampTextLink(0, 12, "First Artist") { selected += "first" },
                        NaviampTextLink(17, 30, "Second Artist") { selected += "second" }))
            }
        }
        waitForIdle()
        runOnIdle { presenter.offset = -secondX }
        onNodeWithTag("artists").performTouchInput { click(Offset(2f, center.y)) }
        runOnIdle { assertEquals(listOf("second"), selected) }
    }

    @Test fun artistLinksRemainAvailableToAccessibilityAndKeyboard() = runComposeUiTest {
        val selected = mutableListOf<String>()
        setContent {
            NaviampRasterText(AnnotatedString("First     Second"), TextStyle(fontSize = 16.sp), 25.dp,
                true, true, Modifier.width(120.dp).testTag("artists"),
                listOf(NaviampTextLink(0, 5, "First") { selected += "first" },
                    NaviampTextLink(10, 16, "Second") { selected += "second" }))
        }
        val node = onNodeWithTag("artists")
        val actions = node.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        runOnIdle {
            assertEquals(listOf("First", "Second"), actions.map { it.label })
            actions[1].action()
        }
        node.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        node.performKeyInput { pressKey(Key.Enter); pressKey(Key.Tab); pressKey(Key.Enter) }
        runOnIdle { assertEquals(listOf("second", "first", "second"), selected) }
    }

    @Test fun hidingOrCoveringWindowReleasesNativePresentation() = runComposeUiTest {
        val presenter = RecordingPresenter()
        val visible = mutableStateOf(true)
        val overlay = mutableStateOf(false)
        setContent {
            NaviampRasterEnvironment(presenter, visible.value, overlay.value) {
                BouncingTitleText("A very long title which needs scrolling", Color.White, 14,
                    marqueeEnabled = true, modifier = Modifier.width(100.dp))
            }
        }
        waitForIdle()
        runOnIdle { assertTrue(presenter.presentations > 0); overlay.value = true }
        waitForIdle()
        runOnIdle { assertEquals(1, presenter.closed); overlay.value = false }
        waitForIdle()
        runOnIdle { visible.value = false }
        waitForIdle()
        runOnIdle { assertEquals(2, presenter.closed) }
    }

    @Test fun playbackUpdatesReuseWaveformPixels() = runComposeUiTest {
        val presenter = RecordingPresenter()
        val progress = mutableFloatStateOf(.2f)
        setContent {
            CompositionLocalProvider(LocalNaviampRasterPresenter provides presenter) {
                WaveformScrubber(List(64) { .7f }, progress.floatValue, enabled = true, smoothProgress = true,
                    durationSeconds = 300.0, colors = NaviampColors(), onValueChange = {}, onValueChangeFinished = {},
                    modifier = Modifier.width(200.dp).height(30.dp))
            }
        }
        waitForIdle()
        val before = presenter.layers.map { it.image }
        runOnIdle { progress.floatValue = .204f }
        waitForIdle()
        runOnIdle {
            assertEquals(2, before.size)
            assertTrue(before.zip(presenter.layers).all { (image, layer) -> image === layer.image })
        }
    }

    private class RecordingPresenter : NaviampRasterPresenter {
        var offset = 0f
        var presentations = 0
        var closed = 0
        var layers = emptyList<NaviampRasterLayer>()
        var bounds = Rect.Zero
        var ready = true
        var notifyReady: () -> Unit = {}
        override fun create() = object : NaviampRasterRegion {
            override fun whenReady(callback: () -> Unit) { notifyReady = callback }
            override fun present(layers: List<NaviampRasterLayer>, bounds: Rect, clip: Rect, cornerRadius: Float): Boolean {
                this@RecordingPresenter.layers = layers
                this@RecordingPresenter.bounds = bounds
                presentations++
                return ready
            }
            override fun translationX(layerIndex: Int) = offset
            override fun close() { closed++ }
        }
    }
}
