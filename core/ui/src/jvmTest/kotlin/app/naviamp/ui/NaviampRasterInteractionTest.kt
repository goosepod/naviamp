package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.style.TextAlign
import app.naviamp.domain.playback.PlaybackProgress
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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class NaviampRasterInteractionTest {
    @Test fun popupTransitionsRetainCachedPixelsWithoutRerasterizingUnchangedContent() = runComposeUiTest {
        val presenter = RecordingPresenter(contentBelowOwnedWindows = true)
        val popup = mutableStateOf(false)
        val color = mutableStateOf(Color.White)
        var renders = 0
        val content = NaviampRasterContent({ color.value }) { value ->
            renders++
            val image = androidx.compose.ui.graphics.ImageBitmap(24, 24)
            androidx.compose.ui.graphics.Canvas(image).drawRect(Rect(0f, 0f, 24f, 24f),
                androidx.compose.ui.graphics.Paint().apply { this.color = value })
            listOf(NaviampRasterLayer(image))
        }
        setContent {
            NaviampRasterEnvironment(presenter, true, false) {
                Box(Modifier.size(24.dp).background(Color.Black).testTag("cached")) {
                    NaviampAnimatedRaster(content, Modifier.size(24.dp))
                }
                if (popup.value) NaviampPopupPresence()
            }
        }
        waitForIdle()
        val image = presenter.layers.single().image
        repeat(12) {
            runOnIdle { popup.value = true }
            waitForIdle()
            assertEquals(Color.White, onNodeWithTag("cached").captureToImage().toPixelMap()[10, 10])
            runOnIdle { popup.value = false }
            waitForIdle()
            runOnIdle { assertSame(image, presenter.layers.single().image); assertEquals(1, renders) }
        }
        runOnIdle { popup.value = true; color.value = Color.Magenta }
        waitForIdle()
        assertEquals(Color.Magenta, onNodeWithTag("cached").captureToImage().toPixelMap()[10, 10])
        runOnIdle { popup.value = false }
        waitForIdle()
        runOnIdle { assertEquals(2, renders); assertNotSame(image, presenter.layers.single().image) }
    }

    @Test fun waveformKeyboardUsesLiveProgressAndClampsSeeks() = runComposeUiTest {
        val progress = mutableFloatStateOf(.2f)
        val seeks = mutableListOf<Float>()
        setContent {
            WaveformScrubber(List(64) { .7f }, .2f, drawValue = { progress.floatValue }, enabled = true,
                colors = NaviampColors(), onValueChange = { progress.floatValue = it },
                onValueChangeFinished = { seeks += it }, modifier = Modifier.width(200.dp).height(30.dp).testTag("progress"))
        }
        val node = onNodeWithTag("progress")
        node.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        node.performKeyInput { pressKey(Key.DirectionRight); pressKey(Key.MoveEnd); pressKey(Key.DirectionRight); pressKey(Key.MoveHome) }
        runOnIdle {
            assertEquals(4, seeks.size)
            assertEquals(.21f, seeks[0], .0001f)
            assertEquals(listOf(1f, 1f, 0f), seeks.drop(1))
        }
    }
    @Test fun changingPositionBindingKeepsFallbackPixelsAndAccessibleTextCurrent() = runComposeUiTest {
        val presenter = RecordingPresenter().apply { ready = false }
        val progress = mutableStateOf(PlaybackProgress(59.0, 600.0))
        setContent {
            CompositionLocalProvider(LocalNaviampRasterPresenter provides presenter) {
                NowPlayingPositionLabel(NowPlayingUi(title = "Fixture", subtitle = "Artist", stateLabel = "Playing"),
                    progress, NaviampColors(), 11.sp, 42.dp)
            }
        }
        val before = onNodeWithText("0:59").captureToImage().toPixelMap()
        runOnIdle { progress.value = PlaybackProgress(60.0, 600.0) }
        waitForIdle()
        val after = onNodeWithText("1:00").captureToImage().toPixelMap()
        var changed = 0
        for (y in 0 until before.height) for (x in 0 until before.width) if (before[x, y] != after[x, y]) changed++
        assertTrue(changed > 5, "Fallback position text froze")
    }
    @Test fun positionUpdatesSubmitCachedTextAndRetainNativeAccessibility() = runComposeUiTest {
        val presenter = RecordingPresenter()
        val progress = mutableStateOf(PlaybackProgress(59.0, 600.0))
        var compositions = 0
        var parentDraws = 0
        setContent {
            CompositionLocalProvider(LocalNaviampRasterPresenter provides presenter) {
                Box(Modifier.drawWithContent { parentDraws++; drawContent() }) {
                    SideEffect { compositions++ }
                    NowPlayingPositionLabel(NowPlayingUi(title = "Fixture", subtitle = "Artist", stateLabel = "Playing"),
                        progress, NaviampColors(), 11.sp, 42.dp)
                }
            }
        }
        onNodeWithText("0:59").assertExists()
        val before = presenter.layers.single().image
        val beforeBounds = presenter.bounds
        val beforeDraws = parentDraws
        val beforeCompositions = compositions
        runOnIdle { progress.value = PlaybackProgress(60.0, 600.0) }
        waitForIdle()
        onNodeWithText("1:00").assertExists()
        runOnIdle {
            assertNotSame(before, presenter.layers.single().image)
            assertEquals(beforeBounds, presenter.bounds)
            assertNull(presenter.layers.single().translation)
            assertEquals(beforeDraws, parentDraws, "Immediate native text update requested a drawing pass")
            assertEquals(beforeCompositions, compositions, "Changing playback text recomposed the surrounding player")
        }
    }

    @Test fun deferredNativeUpdatesStillCommitInTheSharedDrawingPass() = runComposeUiTest {
        val presenter = RecordingPresenter(requiresDrawSynchronization = true)
        val progress = mutableFloatStateOf(.2f)
        setContent {
            CompositionLocalProvider(LocalNaviampRasterPresenter provides presenter) {
                WaveformScrubber(List(64) { .7f }, progress.floatValue, enabled = true,
                    colors = NaviampColors(), onValueChange = {}, onValueChangeFinished = {},
                    modifier = Modifier.width(200.dp).height(30.dp))
            }
        }
        waitForIdle()
        val before = presenter.synchronizations
        runOnIdle { progress.floatValue = .4f }
        waitForIdle()
        runOnIdle { assertTrue(presenter.synchronizations > before) }
    }

    @Test fun changingProgressBindingUpdatesNativeLayersAndAccessibilityWithoutRecomposition() = runComposeUiTest {
        val presenter = RecordingPresenter()
        val progress = mutableFloatStateOf(.2f)
        var compositions = 0
        setContent {
            CompositionLocalProvider(LocalNaviampRasterPresenter provides presenter) {
                SideEffect { compositions++ }
                WaveformScrubber(List(64) { .7f }, .2f, drawValue = { progress.floatValue }, enabled = true,
                    colors = NaviampColors(), onValueChange = {}, onValueChangeFinished = {},
                    modifier = Modifier.width(200.dp).height(30.dp).testTag("progress"))
            }
        }
        waitForIdle()
        val before = compositions
        val images = presenter.layers.map { it.image }
        runOnIdle { progress.floatValue = .4f }
        waitForIdle()
        val range = onNodeWithTag("progress").fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        runOnIdle {
            assertEquals(.4f, range.current)
            assertEquals(.4f, presenter.layers.last().reveal)
            assertTrue(images.zip(presenter.layers).all { (image, layer) -> image === layer.image })
            assertEquals(before, compositions)
        }
    }

    @Test fun positionLabelPreservesWrappedTextHeightAtLargeFontScale() = runComposeUiTest {
        var labelBounds = Rect.Zero
        var referenceBounds = Rect.Zero
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                Box {
                    Text("12:34", fontSize = 11.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.width(42.dp).onGloballyPositioned { referenceBounds = it.boundsInWindow() })
                    Box(Modifier.onGloballyPositioned { labelBounds = it.boundsInWindow() }) {
                        NowPlayingPositionLabel(NowPlayingUi(title = "Fixture", subtitle = "Artist", stateLabel = "Playing",
                            positionSeconds = 754.0), null, NaviampColors(), 11.sp, 42.dp)
                    }
                }
            }
        }
        waitForIdle()
        runOnIdle { assertEquals(referenceBounds.size, labelBounds.size) }
        onAllNodesWithText("12:34").assertCountEquals(2)
    }

    @Test fun unavailableNativePresentationRetainsSharedPixelsAndAccessibilityActions() = runComposeUiTest {
        val presenter = RecordingPresenter().apply { ready = false }
        var activations = 0
        setContent {
            Box(Modifier.size(200.dp, 30.dp).background(Color.Black)) {
                CompositionLocalProvider(LocalNaviampRasterPresenter provides presenter) {
                    NaviampRasterText(AnnotatedString("Artist"), TextStyle(color = Color.White, fontSize = 16.sp),
                        25.dp, false, true, Modifier.width(120.dp).testTag("fallback"),
                        listOf(NaviampTextLink(0, 6, "Artist") { activations++ }))
                }
            }
        }
        val node = onNodeWithTag("fallback")
        val pixels = node.captureToImage().toPixelMap()
        var visible = 0
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            if (pixels[x, y].red > .5f) visible++
        }
        assertTrue(visible > 20, "Shared fallback text is blank")
        node.assertTextEquals("Artist")
        val actions = node.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        runOnIdle {
            assertTrue(actions.single().action())
            assertEquals(1, activations)
            assertTrue(presenter.presentations > 0)
        }
    }

    @Test fun restoringVisibilityRecreatesNativePresentationWithSharedMotionAndPixels() = runComposeUiTest {
        val presenter = RecordingPresenter()
        val visible = mutableStateOf(true)
        setContent {
            NaviampRasterEnvironment(presenter, visible.value, false) {
                BouncingTitleText("A long cached title that needs to keep moving after restore", Color.White, 14,
                    marqueeEnabled = true, modifier = Modifier.width(100.dp))
            }
        }
        waitForIdle()
        val before = presenter.presentations
        val image = presenter.layers.single().image
        runOnIdle { assertNotNull(presenter.layers.single().translation); visible.value = false }
        waitForIdle()
        runOnIdle { assertEquals(1, presenter.closed); visible.value = true }
        waitForIdle()
        runOnIdle {
            assertTrue(presenter.presentations > before)
            assertSame(image, presenter.layers.single().image)
            assertNotNull(presenter.layers.single().translation)
        }
    }

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
                BouncingTitleText("A very long title which needs scrolling", Color.Magenta, 14,
                    marqueeEnabled = true, modifier = Modifier.width(100.dp))
            }
        }
        waitForIdle()
        runOnIdle { assertTrue(presenter.presentations > 0); overlay.value = true }
        waitForIdle()
        val covered = onNodeWithText("A very long title which needs scrolling").captureToImage().toPixelMap()
        var textPixels = 0
        for (y in 0 until covered.height) for (x in 0 until covered.width) {
            val pixel = covered[x, y]
            if (pixel.red > .8f && pixel.blue > .8f && pixel.green < .2f) textPixels++
        }
        assertTrue(textPixels > 10, "Covered visible metadata disappeared instead of drawing its shared fallback")
        runOnIdle { assertEquals(1, presenter.closed); overlay.value = false }
        waitForIdle()
        runOnIdle { visible.value = false }
        waitForIdle()
        runOnIdle { assertEquals(2, presenter.closed) }
    }

    @Test fun playbackUpdatesReuseWaveformPixels() = runComposeUiTest {
        val presenter = RecordingPresenter()
        val progress = mutableFloatStateOf(.2f)
        var parentDraws = 0
        setContent {
            Box(Modifier.drawWithContent { parentDraws++; drawContent() }) {
                CompositionLocalProvider(LocalNaviampRasterPresenter provides presenter) {
                    WaveformScrubber(List(64) { .7f }, progress.floatValue, enabled = true, smoothProgress = true,
                        durationSeconds = 300.0, colors = NaviampColors(), onValueChange = {}, onValueChangeFinished = {},
                        modifier = Modifier.width(200.dp).height(30.dp))
                }
            }
        }
        waitForIdle()
        val before = presenter.layers.map { it.image }
        val drawsBefore = parentDraws
        runOnIdle { progress.floatValue = .204f }
        waitForIdle()
        runOnIdle {
            assertEquals(2, before.size)
            assertTrue(before.zip(presenter.layers).all { (image, layer) -> image === layer.image })
            assertEquals(drawsBefore, parentDraws, "Native progress submission repainted unrelated content")
        }
    }

    @Test fun ownedPopupDoesNotReplacePresentationWhenNativeStackingKeepsItBelowThePopup() = runComposeUiTest {
        val presenter = RecordingPresenter(contentBelowOwnedWindows = true)
        val visible = mutableStateOf(true)
        val overlay = mutableStateOf(false)
        setContent {
            NaviampRasterEnvironment(presenter, visible.value, overlay.value) {
                BouncingTitleText("A very long title which needs scrolling", Color.White, 14,
                    marqueeEnabled = true, modifier = Modifier.width(100.dp))
            }
        }
        waitForIdle()
        val before = presenter.presentations
        runOnIdle { overlay.value = true }
        waitForIdle()
        runOnIdle { assertEquals(0, presenter.closed); assertEquals(before, presenter.presentations); overlay.value = false }
        waitForIdle()
        runOnIdle { assertEquals(0, presenter.closed); visible.value = false }
        waitForIdle()
        runOnIdle { assertEquals(1, presenter.closed) }
    }

    @Test fun sharedPopupReleasesNativeSurfaceAndRestoresItAfterDismissal() = runComposeUiTest {
        val presenter = RecordingPresenter(contentBelowOwnedWindows = true)
        val popup = mutableStateOf(false)
        setContent {
            NaviampRasterEnvironment(presenter, true, false) {
                BouncingTitleText("A long title rendered beneath the popup", Color.White, 14,
                    marqueeEnabled = false, modifier = Modifier.width(100.dp))
                if (popup.value) NaviampPopupPresence()
            }
        }
        waitForIdle()
        runOnIdle { assertTrue(presenter.presentations > 0); popup.value = true }
        waitForIdle()
        val beforeRestore = presenter.presentations
        runOnIdle { assertEquals(1, presenter.closed); popup.value = false }
        waitForIdle()
        runOnIdle { assertTrue(presenter.presentations > beforeRestore) }
    }

    private class RecordingPresenter(override val contentBelowOwnedWindows: Boolean = false,
        val requiresDrawSynchronization: Boolean = false) : NaviampRasterPresenter {
        var offset = 0f
        var presentations = 0
        var closed = 0
        var synchronizations = 0
        var layers = emptyList<NaviampRasterLayer>()
        var bounds = Rect.Zero
        var ready = true
        var notifyReady: () -> Unit = {}
        override fun create() = object : NaviampRasterRegion {
            override val requiresDrawSynchronization = this@RecordingPresenter.requiresDrawSynchronization
            override fun synchronizeDraw() { synchronizations++ }
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
