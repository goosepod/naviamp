package app.naviamp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeRight
import app.naviamp.domain.settings.TrackSwipeAction
import app.naviamp.domain.settings.PlaylistEditSwipeActions
import app.naviamp.domain.settings.TrackSwipeSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    fun dragPreviewMovesRowsAsideForTheDropGap() {
        assertEquals(3, playlistDragTargetIndex(fromIndex = 1, dragOffsetY = 90f, rowStepPx = 44f, lastIndex = 5))
        assertEquals(-44f, playlistDragGapOffset(rowIndex = 2, fromIndex = 1, targetIndex = 3, rowStepPx = 44f))
        assertEquals(-44f, playlistDragGapOffset(rowIndex = 3, fromIndex = 1, targetIndex = 3, rowStepPx = 44f))
        assertEquals(0f, playlistDragGapOffset(rowIndex = 4, fromIndex = 1, targetIndex = 3, rowStepPx = 44f))

        assertEquals(1, playlistDragTargetIndex(fromIndex = 3, dragOffsetY = -90f, rowStepPx = 44f, lastIndex = 5))
        assertEquals(44f, playlistDragGapOffset(rowIndex = 1, fromIndex = 3, targetIndex = 1, rowStepPx = 44f))
        assertEquals(44f, playlistDragGapOffset(rowIndex = 2, fromIndex = 3, targetIndex = 1, rowStepPx = 44f))
    }

    @Test
    fun editablePlaylistRowsKeepBothMetadataLinesVisibleAtLargeFontScale() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(
                    density = 1f,
                    fontScale = 2f,
                ),
            ) {
                StandardPlaylistManagementList(
                    colors = NaviampColors(),
                    initialTracks = listOf(
                        SharedTrackRowUi(id = "with-artist", title = "Track with artist", subtitle = "Visible Artist"),
                    ),
                    onTrackSelected = {},
                    onSave = {},
                )
            }
        }

        val titleBounds = onNodeWithText("Track with artist", useUnmergedTree = true)
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val artistBounds = onNodeWithText("Visible Artist", useUnmergedTree = true)
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(artistBounds.center.y > titleBounds.center.y)
    }

    @Test
    fun smartAndEditablePlaylistRowsShareArtistAndMissingMetadataPolicy() = runComposeUiTest {
        setContent {
            Column {
                StandardPlaylistManagementList(
                    colors = NaviampColors(),
                    initialTracks = listOf(
                        SharedTrackRowUi(id = "standard", title = "Standard track", subtitle = "Standard Artist"),
                        SharedTrackRowUi(id = "standard-missing", title = "Standard missing artist", subtitle = ""),
                    ),
                    onTrackSelected = {},
                    onSave = {},
                )
                SmartPlaylistTrackList(
                    colors = NaviampColors(),
                    tracks = listOf(
                        SharedTrackRowUi(id = "smart", title = "Smart track", subtitle = "Smart Artist"),
                        SharedTrackRowUi(id = "smart-missing", title = "Smart missing artist", subtitle = ""),
                    ),
                    onTrackSelected = {},
                )
            }
        }

        onNodeWithText("Standard Artist").assertIsDisplayed()
        onNodeWithText("Smart Artist").assertIsDisplayed()
        onNodeWithText("Standard missing artist").assertIsDisplayed()
        onNodeWithText("Smart missing artist").assertIsDisplayed()
    }

    @Test
    fun editablePlaylistRowStepAccountsForScaledTitleAndArtistLines() {
        assertEquals(52f, playlistManagementRowStepPx(density = 1f, fontScale = 1f))
        assertEquals(68f, playlistManagementRowStepPx(density = 1f, fontScale = 2f))
    }

    @Test
    fun playlistRowsOmitMissingArtistMetadataInsteadOfRenderingABlankLine() {
        assertEquals("Artist", playlistTrackSubtitle(SharedTrackRowUi("artist", "Track", " Artist ")))
        assertEquals(null, playlistTrackSubtitle(SharedTrackRowUi("missing", "Track", "   ")))
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

    @Test
    fun successfulInlineSaveClearsSavingAndAdvancesSavedBaseline() = runComposeUiTest {
        var savedTrackIds = emptyList<String>()
        setContent {
            CompositionLocalProvider(
                LocalTrackSwipeSettings provides TrackSwipeSettings(
                    playlistEditRight = TrackSwipeAction.Remove,
                ),
            ) {
                StandardPlaylistManagementList(
                    colors = NaviampColors(),
                    initialTracks = testTracks(),
                    onTrackSelected = {},
                    onSave = { tracks -> savedTrackIds = tracks.map { it.id } },
                )
            }
        }

        onNodeWithText("Track A").performTouchInput { swipeRight() }
        onNodeWithText("Save changes").performClick()
        waitForIdle()

        assertEquals(listOf("b"), savedTrackIds)
        onNodeWithText("Save changes").assertIsNotEnabled()
        assertEquals(0, onAllNodesWithText("Saving...").fetchSemanticsNodes().size)
    }

    @Test
    fun inlineSaveDoesNotRepeatAnExternallyDisplayedFailure() = runComposeUiTest {
        val failure = "Bandcamp playlist update failed."
        setContent {
            CompositionLocalProvider(
                LocalTrackSwipeSettings provides TrackSwipeSettings(
                    playlistEditRight = TrackSwipeAction.Remove,
                ),
            ) {
                Column {
                    Text(failure)
                    StandardPlaylistManagementList(
                        colors = NaviampColors(),
                        initialTracks = testTracks(),
                        onTrackSelected = {},
                        onSave = { throw IllegalStateException(failure) },
                        externallyDisplayedStatus = failure,
                    )
                }
            }
        }

        onNodeWithText("Track A").performTouchInput { swipeRight() }
        onNodeWithText("Save changes").performClick()
        waitForIdle()

        assertEquals(1, onAllNodesWithText(failure).fetchSemanticsNodes().size)
    }
}

private fun testTracks() = listOf(
    SharedTrackRowUi(id = "a", title = "Track A", subtitle = "Artist"),
    SharedTrackRowUi(id = "b", title = "Track B", subtitle = "Artist"),
)
