package app.naviamp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.MotionDurationScale
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.click
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NaviampNowPlayingSheetUiTest {
    private class Harness {
        val open = mutableStateOf(true)
        var closes = 0
        var opens = 0
        val displayActions = mutableListOf<NowPlayingDisplayAction>()
        val backDispatcher = NaviampSystemBackDispatcher()
        var routeBacks = 0

        /** Delivers one system Back press the way the hosts do: to the deepest shared handler. */
        fun pressBack() = backDispatcher.currentHandler?.invoke()
    }

    private fun sheetTest(
        initiallyOpen: Boolean = true,
        reducedMotion: Boolean = false,
        solidLayers: Boolean = false,
        sheetColor: Color = Color.Red,
        animationDurationScale: Float = 1f,
        block: ComposeUiTest.(Harness) -> Unit,
    ) = runDesktopComposeUiTest(
        400,
        800,
        effectContext = object : MotionDurationScale {
            override val scaleFactor = animationDurationScale
        },
    ) {
        val harness = Harness().apply { open.value = initiallyOpen }
        setContent {
            CompositionLocalProvider(
                LocalNaviampReducedMotion provides reducedMotion,
                LocalNaviampSystemBackDispatcher provides harness.backDispatcher,
            ) {
                // Stands in for the browse route's own Back handling underneath the player.
                NaviampSystemBackHandler(enabled = true) { harness.routeBacks++ }
                SheetHarness(harness, solidLayers, sheetColor)
            }
        }
        waitForIdle()
        block(harness)
    }

    @Composable
    private fun SheetHarness(harness: Harness, solidLayers: Boolean, sheetColor: Color) {
        val nowPlaying = NowPlayingUi(id = "song", title = "Song", subtitle = "Artist", stateLabel = "Paused")
        val actions = NaviampNowPlayingActions(
            {},
            {
                harness.displayActions += it.action
                // Like the shared navigation controller, Collapse closes and is a no-op when closed.
                if (it.action == NowPlayingDisplayAction.Collapse) harness.open.value = false
            },
            {}, {}, {}, {}, {},
        )
        val state = rememberNaviampNowPlayingSheetState(harness.open.value)
        NaviampNowPlayingSheetLayers(
            state = state,
            onClose = { harness.closes++; harness.open.value = false },
            base = {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxWidth().background(if (solidLayers) Color.Blue else Color.Transparent).testTag("browse"))
                    NaviampMiniNowPlaying(
                        nowPlaying = nowPlaying,
                        colors = NaviampColors(),
                        onOpen = { harness.open.value = true },
                        actions = actions,
                        modifier = Modifier
                            .naviampNowPlayingOpenGesture(state) { harness.opens++; harness.open.value = true }
                            .testTag("mini"),
                    )
                }
            },
            sheet = {
                if (solidLayers) {
                    Box(Modifier.fillMaxSize().background(sheetColor))
                } else {
                    NaviampNowPlayingPanel(nowPlaying = nowPlaying, colors = NaviampColors(), actions = actions)
                }
            },
        )
    }

    private fun ComposeUiTest.sheet() = onNodeWithTag(NaviampNowPlayingSheetTestTag)

    private fun ComposeUiTest.dragSheet(fromY: Float, toY: Float, durationMillis: Long) {
        sheet().performTouchInput { swipe(Offset(centerX, fromY), Offset(centerX, toY), durationMillis) }
        waitForIdle()
    }

    private fun SemanticsNodeInteraction.top() = getBoundsInRoot().top.value

    @Test
    fun dragPastThresholdClosesThroughTheCloseCommand() = sheetTest { harness ->
        onNodeWithTag("browse").assertDoesNotExist()
        dragSheet(fromY = 150f, toY = 600f, durationMillis = 900)
        assertEquals(1, harness.closes)
        sheet().assertDoesNotExist()
        onNodeWithTag("browse").assertIsDisplayed()
        onNodeWithTag("mini").assertIsDisplayed()
    }

    @Test
    fun shortSlowDragSpringsBack() = sheetTest { harness ->
        dragSheet(fromY = 150f, toY = 250f, durationMillis = 1200)
        assertEquals(0, harness.closes)
        sheet().assertIsDisplayed()
        assertEquals(0f, sheet().top())
        onNodeWithTag("browse").assertDoesNotExist()
    }

    @Test
    fun shortFastFlingCloses() = sheetTest { harness ->
        dragSheet(fromY = 150f, toY = 260f, durationMillis = 60)
        assertEquals(1, harness.closes)
        sheet().assertDoesNotExist()
    }

    @Test
    fun dragOnScrolledContentScrollsInsteadOfClosing() = sheetTest { harness ->
        val scrollable = onNode(hasScrollAction() and hasAnyAncestor(hasTestTag(NaviampNowPlayingSheetTestTag)))
        dragSheet(fromY = 380f, toY = 230f, durationMillis = 600)
        val scrolled = scrollable.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue(scrolled > 0f)
        dragSheet(fromY = 100f, toY = 760f, durationMillis = 900)
        assertEquals(0, harness.closes)
        assertEquals(0f, sheet().top())
        val after = scrollable.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue(after < scrolled)
    }

    @Test
    fun horizontalSwipeDoesNotClose() = sheetTest { harness ->
        sheet().performTouchInput { swipe(Offset(40f, 200f), Offset(360f, 210f), 400) }
        waitForIdle()
        assertEquals(0, harness.closes)
        assertEquals(0f, sheet().top())
    }

    @Test
    fun mouseDragDoesNotClose() = sheetTest { harness ->
        sheet().performMouseInput {
            press()
            moveTo(Offset(centerX, 150f))
            moveBy(Offset(0f, 450f), delayMillis = 600)
            release()
        }
        waitForIdle()
        assertEquals(0, harness.closes)
        assertEquals(0f, sheet().top())
    }

    @Test
    fun closeCommandFromOutsideSlidesTheSheetAway() = sheetTest { harness ->
        harness.open.value = false
        waitForIdle()
        sheet().assertDoesNotExist()
        onNodeWithTag("browse").assertIsDisplayed()
    }

    @Test
    fun collapseControlStaysAvailableForAccessibility() = sheetTest { harness ->
        onNodeWithContentDescription("Collapse player").assertIsDisplayed().performClick()
        assertEquals(listOf(NowPlayingDisplayAction.Collapse), harness.displayActions)
    }

    @Test
    fun swipeUpOnTheMiniPlayerOpens() = sheetTest(initiallyOpen = false) { harness ->
        sheet().assertDoesNotExist()
        onNodeWithTag("mini").performTouchInput { swipe(Offset(centerX, centerY), Offset(centerX, centerY - 500f), 800) }
        waitForIdle()
        assertEquals(1, harness.opens)
        assertEquals(0f, sheet().top())
        onNodeWithTag("browse").assertDoesNotExist()
    }

    @Test
    fun shortSwipeUpOnTheMiniPlayerSpringsBack() = sheetTest(initiallyOpen = false) { harness ->
        onNodeWithTag("mini").performTouchInput { swipe(Offset(centerX, centerY), Offset(centerX, centerY - 80f), 1200) }
        waitForIdle()
        assertEquals(0, harness.opens)
        sheet().assertDoesNotExist()
        onNodeWithTag("mini").assertIsDisplayed()
    }

    @Test
    fun tappingTheMiniPlayerStillOpens() = sheetTest(initiallyOpen = false) { harness ->
        onNodeWithText("Song").performClick()
        waitForIdle()
        assertEquals(true, harness.open.value)
        assertEquals(0f, sheet().top())
    }

    private fun ComposeUiTest.pixel(x: Int, y: Int): Color = Color(onRoot().captureToImage().toPixelMap()[x, y].toArgb())

    /** Advances part way into an open or close transition so its intermediate frame can be inspected. */
    private fun ComposeUiTest.midTransition(change: () -> Unit) {
        mainClock.autoAdvance = false
        change()
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(80)
    }

    private fun ComposeUiTest.finishTransition() {
        mainClock.autoAdvance = true
        waitForIdle()
    }

    @Test
    fun coveredBrowseStaysHiddenAndOutOfTheSemanticsTree() = sheetTest(solidLayers = true, sheetColor = Color.Transparent) { harness ->
        onNodeWithTag("browse").assertDoesNotExist()
        onNodeWithTag("mini").assertDoesNotExist()
        val covered = pixel(200, 400)
        assertTrue(covered.blue < 0.05f, "expected the covered browse layer to be invisible, got $covered")
        harness.open.value = false
        waitForIdle()
        onNodeWithTag("browse").assertIsDisplayed()
        assertEquals(Color.Blue, pixel(200, 400))
    }

    @Test
    fun backDuringASwipeOpenSettleReturnsToMinimized() = sheetTest(initiallyOpen = false) { harness ->
        mainClock.autoAdvance = false
        onNodeWithTag("mini").performTouchInput { swipe(Offset(centerX, centerY), Offset(centerX, centerY - 500f), 300) }
        repeat(3) { mainClock.advanceTimeByFrame() }
        assertTrue(sheet().top() > 0f, "expected the player to still be settling")
        runOnUiThread { harness.pressBack() }
        finishTransition()
        assertEquals(0, harness.opens)
        assertEquals(false, harness.open.value)
        assertEquals(0, harness.routeBacks)
        sheet().assertDoesNotExist()
        onNodeWithTag("browse").assertIsDisplayed()
    }

    @Test
    fun backDuringASwipeCloseSettleIsConsumed() = sheetTest { harness ->
        mainClock.autoAdvance = false
        sheet().performTouchInput { swipe(Offset(centerX, 150f), Offset(centerX, 600f), 300) }
        repeat(3) { mainClock.advanceTimeByFrame() }
        assertEquals(0, harness.closes)
        runOnUiThread { harness.pressBack() }
        finishTransition()
        assertEquals(1, harness.closes)
        assertEquals(0, harness.routeBacks)
        sheet().assertDoesNotExist()
        onNodeWithTag("browse").assertIsDisplayed()
        harness.pressBack()
        assertEquals(1, harness.routeBacks)
    }

    @Test
    fun tapInsideThePlayerRightAfterASwipeOpenUsesTheOpenState() = sheetTest(initiallyOpen = false) { harness ->
        mainClock.autoAdvance = false
        onNodeWithTag("mini").performTouchInput { swipe(Offset(centerX, centerY), Offset(centerX, centerY - 500f), 300) }
        var frames = 0
        // The visible end of the spring: the player has arrived but has not settled to 0 yet.
        while (sheet().top() > 3f && frames++ < 120) mainClock.advanceTimeByFrame()
        assertTrue(sheet().top() > 0f, "expected the player to still be settling")
        assertEquals(0, harness.opens)
        onNodeWithContentDescription("Collapse player").performTouchInput { click() }
        finishTransition()
        assertEquals(1, harness.opens)
        assertEquals(listOf(NowPlayingDisplayAction.Collapse), harness.displayActions)
        assertEquals(false, harness.open.value)
        sheet().assertDoesNotExist()
    }

    @Test
    fun withMotionTheCloseCommandSlides() = sheetTest(solidLayers = true) { harness ->
        midTransition { harness.open.value = false }
        assertTrue(sheet().top() > 0f)
        val revealedBrowse = pixel(200, 5)
        assertTrue(revealedBrowse.blue > 0.3f && revealedBrowse.red < 0.05f, "expected the browse layer, got $revealedBrowse")
        assertEquals(Color.Red, pixel(200, 795))
        finishTransition()
        sheet().assertDoesNotExist()
    }

    @Test
    fun reducedMotionCloseCrossfadesWithoutTranslation() = sheetTest(reducedMotion = true, solidLayers = true) { harness ->
        midTransition { harness.open.value = false }
        assertEquals(0f, sheet().top())
        listOf(pixel(200, 5), pixel(200, 600)).forEach { blended ->
            assertTrue(blended.red > 0.05f && blended.blue > 0.05f, "expected a crossfade, got $blended")
        }
        finishTransition()
        sheet().assertDoesNotExist()
        onNodeWithTag("browse").assertIsDisplayed()
    }

    @Test
    fun reducedMotionOpenCrossfadesWithoutTranslation() = sheetTest(initiallyOpen = false, reducedMotion = true, solidLayers = true) { harness ->
        midTransition { harness.open.value = true }
        assertEquals(0f, sheet().top())
        val blended = pixel(200, 400)
        assertTrue(blended.red > 0.05f && blended.blue > 0.05f, "expected a crossfade, got $blended")
        finishTransition()
        assertEquals(Color.Red, pixel(200, 400))
        onNodeWithTag("browse").assertDoesNotExist()
    }

    /**
     * Android "Remove animations" both reports reduced motion and sets the animation duration scale
     * to 0. The owner chose an instant cut there, so no translated or blended frame may appear.
     */
    @Test
    fun removeAnimationsCutsWithoutAnIntermediateFrame() = sheetTest(
        reducedMotion = true,
        solidLayers = true,
        animationDurationScale = 0f,
    ) { harness ->
        mainClock.autoAdvance = false
        harness.open.value = false
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        assertEquals(Color.Blue, pixel(200, 400))
        harness.open.value = true
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        assertEquals(Color.Red, pixel(200, 400))
        assertEquals(0f, sheet().top())
        finishTransition()
    }

    @Test
    fun reducedMotionDragDoesNotMoveThePlayerButStillCloses() = sheetTest(reducedMotion = true) { harness ->
        sheet().performTouchInput {
            down(Offset(centerX, 150f))
            moveTo(Offset(centerX, 400f), delayMillis = 300)
            moveTo(Offset(centerX, 600f), delayMillis = 300)
        }
        assertEquals(0f, sheet().top())
        sheet().performTouchInput { up() }
        waitForIdle()
        assertEquals(1, harness.closes)
        sheet().assertDoesNotExist()
    }

    @Test
    fun reducedMotionMiniPlayerSwipeOpensWithoutSliding() = sheetTest(initiallyOpen = false, reducedMotion = true) { harness ->
        onNodeWithTag("mini").performTouchInput { swipe(Offset(centerX, centerY), Offset(centerX, centerY - 500f), 800) }
        waitForIdle()
        assertEquals(1, harness.opens)
        assertEquals(0f, sheet().top())
        onNodeWithTag("browse").assertDoesNotExist()
    }

    private fun ComposeUiTest.onNodeWithText(text: String) =
        onNode(androidx.compose.ui.test.hasText(text) and hasAnyAncestor(hasTestTag("mini")))
}
