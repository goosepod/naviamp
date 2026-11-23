package app.naviamp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
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
    }

    private fun sheetTest(
        initiallyOpen: Boolean = true,
        block: ComposeUiTest.(Harness) -> Unit,
    ) = runDesktopComposeUiTest(400, 800) {
        val harness = Harness().apply { open.value = initiallyOpen }
        setContent { SheetHarness(harness) }
        waitForIdle()
        block(harness)
    }

    @Composable
    private fun SheetHarness(harness: Harness) {
        val nowPlaying = NowPlayingUi(id = "song", title = "Song", subtitle = "Artist", stateLabel = "Paused")
        val actions = NaviampNowPlayingActions(
            {}, { harness.displayActions += it.action }, {}, {}, {}, {}, {},
        )
        val state = rememberNaviampNowPlayingSheetState(harness.open.value)
        NaviampNowPlayingSheetLayers(
            state = state,
            onClose = { harness.closes++; harness.open.value = false },
            base = {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxWidth().testTag("browse"))
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
                NaviampNowPlayingPanel(nowPlaying = nowPlaying, colors = NaviampColors(), actions = actions)
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
        onNodeWithTag("browse").assertIsNotDisplayed()
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
        onNodeWithTag("browse").assertIsNotDisplayed()
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
        onNodeWithTag("browse").assertIsNotDisplayed()
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

    private fun ComposeUiTest.onNodeWithText(text: String) =
        onNode(androidx.compose.ui.test.hasText(text) and hasAnyAncestor(hasTestTag("mini")))
}
