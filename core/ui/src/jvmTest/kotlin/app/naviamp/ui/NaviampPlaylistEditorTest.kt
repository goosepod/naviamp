package app.naviamp.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import app.naviamp.domain.settings.TrackSwipeAction
import app.naviamp.domain.settings.PlaylistEditSwipeActions
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NaviampPlaylistEditorTest {
    @Test
    fun playlistEditingSwipeContextOnlyOffersDraftEditingActions() {
        assertEquals(
            listOf(
                TrackSwipeAction.None,
                TrackSwipeAction.Remove,
                TrackSwipeAction.MoveUp,
                TrackSwipeAction.MoveDown,
                TrackSwipeAction.MoveToTop,
                TrackSwipeAction.MoveToBottom,
            ),
            PlaylistEditSwipeActions,
        )
    }

    @Test
    fun playlistEditingActionsReorderAndRemoveTracks() {
        val tracks = listOf("a", "b", "c", "d")

        assertEquals(listOf("c", "a", "b", "d"), applyPlaylistEditTrackAction(tracks, 2, TrackSwipeAction.MoveToTop))
        assertEquals(listOf("a", "c", "b", "d"), applyPlaylistEditTrackAction(tracks, 1, TrackSwipeAction.MoveDown))
        assertEquals(listOf("a", "c", "d"), applyPlaylistEditTrackAction(tracks, 1, TrackSwipeAction.Remove))
        assertEquals(listOf("a", "c", "d", "b"), applyPlaylistEditTrackAction(tracks, 1, TrackSwipeAction.MoveToBottom))
    }

    @Test
    fun editorUndoRestoresMostRecentDraftEdit() = runComposeUiTest {
        setContent {
            StandardPlaylistEditorDialog(
                colors = NaviampColors(),
                playlistName = "Road Mix",
                initialTracks = testTracks(),
                onDismissRequest = {},
                onSave = {},
            )
        }

        onAllNodesWithContentDescription("Remove")[0].performClick()
        assertEquals(0, onAllNodesWithText("Track A").fetchSemanticsNodes().size)
        onNodeWithText("Undo").performClick()
        onNodeWithText("Track A").assertExists()
    }

    @Test
    fun failedRemoteSaveKeepsEditedDraftOpen() = runComposeUiTest {
        setContent {
            StandardPlaylistEditorDialog(
                colors = NaviampColors(),
                playlistName = "Road Mix",
                initialTracks = testTracks(),
                onDismissRequest = {},
                onSave = { error("Playlist update failed.") },
            )
        }

        onAllNodesWithContentDescription("Remove")[0].performClick()
        onNodeWithTag(StandardPlaylistSaveTestTag).performClick()
        waitForIdle()

        onNodeWithText("Playlist update failed.").assertExists()
        assertEquals(0, onAllNodesWithText("Track A").fetchSemanticsNodes().size)
    }
}

private fun testTracks() = listOf(
    SharedTrackRowUi(id = "a", title = "Track A", subtitle = "Artist"),
    SharedTrackRowUi(id = "b", title = "Track B", subtitle = "Artist"),
)
