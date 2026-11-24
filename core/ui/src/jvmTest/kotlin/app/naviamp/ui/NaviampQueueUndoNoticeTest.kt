package app.naviamp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isEnabled
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NaviampQueueUndoNoticeTest {
    private val removed = NaviampQueueUndoUi(id = 7, kind = NaviampQueueUndoKind.RemovedTrack, subject = "Blue Monday")
    // Wraps to two lines on a phone, as it does on device.
    private val replacedWrapping = NaviampQueueUndoUi(id = 8, kind = NaviampQueueUndoKind.ReplacedQueue,
        subject = "Halcyon (30 Something Years Later Mix)")

    @Test fun announcesTheEditAndExposesALabelledUndoButtonToScreenReaders() = runDesktopComposeUiTest {
        val actions = mutableListOf<NaviampQueueUndoActionRequest>()
        setContent { NaviampQueueUndoNotice(NaviampColors.Dark, removed, actions::add) }

        onNodeWithText("Removed Blue Monday from the queue.")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        onNodeWithTag("queue-undo-action", useUnmergedTree = true)
            .assertHasClickAction()
            .assertContentDescriptionEquals("Undo: Removed Blue Monday from the queue.")
            .performClick()
        onNodeWithTag("queue-undo-dismiss", useUnmergedTree = true).performClick()

        assertEquals(
            listOf(
                NaviampQueueUndoActionRequest(7, NaviampQueueUndoAction.Presented, NaviampQueueUndoBaseTimeoutMillis),
                NaviampQueueUndoActionRequest(7, NaviampQueueUndoAction.Undo),
                NaviampQueueUndoActionRequest(7, NaviampQueueUndoAction.Dismiss),
            ),
            actions.filterNot { it.action == NaviampQueueUndoAction.FocusGained || it.action == NaviampQueueUndoAction.FocusLost },
        )
    }

    @Test fun keyboardAndDpadFocusHoldsTheWindowAndActivatesUndo() = runDesktopComposeUiTest {
        val actions = mutableListOf<NaviampQueueUndoActionRequest>()
        setContent { NaviampQueueUndoNotice(NaviampColors.Dark, removed, actions::add) }

        val undo = onNodeWithTag("queue-undo-action", useUnmergedTree = true)
        undo.requestFocus()
        undo.assertIsFocused()
        undo.performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        undo.performKeyInput { pressKey(Key.DirectionCenter) }
        waitForIdle()

        assertTrue(NaviampQueueUndoActionRequest(7, NaviampQueueUndoAction.FocusGained) in actions)
        assertEquals(2, actions.count { it.action == NaviampQueueUndoAction.Undo })
    }

    @Test fun replacementSpellsOutThatUndoReturnsToThePreviousTrack() = runDesktopComposeUiTest {
        setContent {
            NaviampQueueUndoNotice(NaviampColors.Dark,
                NaviampQueueUndoUi(1, NaviampQueueUndoKind.ReplacedQueue, "Atmosphere"), {})
        }
        onNodeWithText("Replaced the queue. Undo returns to Atmosphere.").assertExists()
    }

    @Test fun noticeStaysAboveSystemBarsAndTheKeyboard() = runDesktopComposeUiTest(width = 412, height = 800) {
        setContent {
            Box(Modifier.fillMaxSize().testTag("window")) {
                NaviampBottomNoticeStack(Modifier.align(Alignment.BottomCenter), WindowInsets(bottom = 48)) {
                    NaviampQueueUndoNotice(NaviampColors.Dark, removed, {})
                }
            }
        }
        val window = onNodeWithTag("window").fetchSemanticsNode().boundsInRoot
        val notice = onNodeWithTag("queue-undo-notice").fetchSemanticsNode().boundsInRoot
        assertTrue(notice.bottom <= window.bottom - 48f + 0.5f, "notice bottom ${notice.bottom}")
        assertTrue(notice.top >= window.top)
    }

    @Test fun noticeNeverCoversAnyNowPlayingControlOnPhoneOrDesktop() {
        // Larger text makes the notice taller, as a wider device font does, so placement cannot rely on one line.
        val layouts = listOf(412 to 818, 412 to 923, 1280 to 800).flatMap { size ->
            listOf(false, true).flatMap { filled -> listOf(1f, 1.5f).map { Triple(size, filled, it) } }
        }
        layouts.forEach { (size, filledArtwork, fontScale) ->
            val (width, height) = size
            runDesktopComposeUiTest(width, height) {
                val anchor = NaviampNowPlayingNoticeAnchor()
                setContent {
                    val density = LocalDensity.current
                    CompositionLocalProvider(
                        LocalNaviampNowPlayingNoticeAnchor provides anchor,
                        LocalDensity provides Density(density.density, fontScale),
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            NaviampNowPlayingPanel(
                                NowPlayingUi(id = "track", title = "Track", subtitle = "Artist",
                                    stateLabel = "Paused", isPaused = true, canPlayPause = true,
                                    canSeek = true, durationSeconds = 200.0),
                                colors = NaviampColors.Dark,
                                actions = NaviampNowPlayingActions({}, {}, {}, {}, {}, {}, {}),
                                displaySettings = app.naviamp.domain.settings.NowPlayingDisplaySettings(fillArtworkArea = filledArtwork),
                                contentInsets = WindowInsets(top = 24, bottom = 32),
                            )
                            NaviampBottomNoticeStack(Modifier.align(Alignment.BottomCenter), WindowInsets(bottom = 32),
                                bottomClearance = NaviampNowPlayingNoticeClearance,
                                avoidAboveInRoot = anchor.controlsTop) {
                                NaviampQueueUndoNotice(NaviampColors.Dark, replacedWrapping, {})
                            }
                        }
                    }
                }
                waitForIdle()
                val notice = onNodeWithTag("queue-undo-notice").fetchSemanticsNode().boundsInRoot
                // Artwork may sit under the notice; only usable controls must stay uncovered.
                val covered = onAllNodes(hasClickAction() and isEnabled()).fetchSemanticsNodes()
                    .filterNot { notice.contains(it.boundsInRoot.center) && it.boundsInRoot.width <= notice.width &&
                        it.boundsInRoot.top >= notice.top - 0.5f && it.boundsInRoot.bottom <= notice.bottom + 0.5f }
                    .filter { it.boundsInRoot.overlaps(notice) }
                    .map { node -> node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
                        .joinToString().ifEmpty { node.boundsInRoot.toString() } }
                assertTrue(covered.isEmpty(), "${width}x$height filled=$filledArtwork font=$fontScale notice $notice covers $covered")
            }
        }
    }
}
