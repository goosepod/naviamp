package app.naviamp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
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
}
