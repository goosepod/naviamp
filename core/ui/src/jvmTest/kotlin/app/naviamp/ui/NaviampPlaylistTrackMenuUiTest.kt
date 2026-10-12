package app.naviamp.ui

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NaviampPlaylistTrackMenuUiTest {
    @Test
    fun editableMenuMovesSelectedDuplicateAndPreservesSaveUndoWorkflow() = runComposeUiTest {
        var selected = 0
        var saved = emptyList<String>()
        setContent {
            StandardPlaylistManagementList(
                colors = NaviampColors(),
                initialTracks = rows(),
                onTrackSelected = { selected++ },
                onSave = { saved = it.map { row -> row.id } },
                capabilities = PlaylistTrackCapabilities(canAddToQueue = true, canAddToPlaylist = true),
            )
        }
        onAllNodesWithContentDescription("More actions")[2].performClick()
        onNodeWithText("Move to Top").performClick()
        assertEquals(0, selected)
        onNodeWithText("Save changes").performClick()
        waitForIdle()
        assertEquals(listOf("same", "same", "other"), saved)
        onAllNodesWithContentDescription("More actions")[1].performClick()
        onNodeWithText("Remove from This Playlist").performClick()
        onNodeWithText("Undo").performClick()
        assertEquals(2, onAllNodesWithText("Duplicate").fetchSemanticsNodes().size)
    }

    @Test
    fun movePositionRejectsInvalidInputAndMovesTheSelectedOccurrence() = runComposeUiTest {
        var saved = emptyList<String>()
        setContent {
            StandardPlaylistManagementList(NaviampColors(), rows(), {}, { saved = it.map { row -> row.id } })
        }
        onAllNodesWithContentDescription("More actions")[2].performClick()
        onNodeWithText("Move to Position…").performClick()
        onNode(hasSetTextAction()).performTextReplacement("0")
        onNodeWithText("Move").assertIsNotEnabled()
        onNode(hasSetTextAction()).performTextReplacement("1")
        onNodeWithText("Move").performClick()
        onNodeWithText("Save changes").performClick()
        waitForIdle()
        assertEquals(listOf("same", "same", "other"), saved)
    }

    @Test
    fun readOnlyMenuOffersCopyAndSongActionsWithoutEditingAndDispatchesCorrectly() = runComposeUiTest {
        val requests = mutableListOf<SharedTrackRowActionRequest>()
        val smart = mutableStateOf(true)
        var selected = 0
        setContent {
            SmartPlaylistTrackList(
                NaviampColors(), rows().take(1), { selected++ },
                onTrackAction = { requests += it },
                capabilities = PlaylistTrackCapabilities(canAddToQueue = true, canAddToPlaylist = true),
                playlistChoices = listOf(NaviampPlaylistChoiceUi("destination", "Destination")),
                isSmartPlaylist = smart.value,
            )
        }
        onNodeWithContentDescription("More actions").performClick()
        onNodeWithText("Move to Top").assertDoesNotExist()
        onNodeWithText("Remove from This Playlist").assertDoesNotExist()
        onNodeWithContentDescription("Drag to reorder").assertDoesNotExist()
        val copyBounds = onNodeWithText("Add to Playlist…").fetchSemanticsNode().boundsInRoot
        val nextBounds = onNodeWithText("Play Immediately Next").fetchSemanticsNode().boundsInRoot
        kotlin.test.assertTrue(copyBounds.top < nextBounds.top)
        onNodeWithText("Play Immediately Next").performClick()
        assertEquals(SharedTrackRowAction.PlayNextTrack, requests.single().action)
        assertEquals(0, selected)
        onNodeWithContentDescription("More actions").performClick()
        onNodeWithText("Add to Playlist…").performClick()
        onNodeWithText("Destination").performClick()
        assertEquals("destination", requests.last().playlistChoice?.id)
        runOnIdle { smart.value = false }
        onNodeWithText("Generated tracks - edit the smart playlist rules to change this list").assertDoesNotExist()
        onNodeWithContentDescription("More actions").performClick()
        onNodeWithText("Add to Playlist…").assertExists()
        onNodeWithText("Remove from This Playlist").assertDoesNotExist()
    }

    @Test
    fun favoriteRefreshKeepsDraftAndNextSaveUsesAdvancedBaseline() = runComposeUiTest {
        val tracks = mutableStateOf(rows())
        val baselines = mutableListOf<List<String>>()
        var fail = true
        setContent {
            StandardPlaylistManagementList(
                NaviampColors(), tracks.value, {}, {},
                onSaveWithBaseline = { baseline, _ ->
                    if (fail) error("Server unavailable")
                    baselines += baseline.map { it.id }
                },
            )
        }
        onAllNodesWithContentDescription("More actions")[2].performClick()
        onNodeWithText("Remove from This Playlist").performClick()
        runOnIdle { tracks.value = tracks.value.map { it.copy(favoriteActive = true) } }
        assertEquals(1, onAllNodesWithText("Duplicate").fetchSemanticsNodes().size)
        onNodeWithText("Save changes").performClick()
        waitForIdle()
        onNodeWithText("Server unavailable").assertExists()
        onNodeWithText("Save changes").assertIsEnabled()
        runOnIdle { fail = false }
        onNodeWithText("Save changes").performClick()
        waitForIdle()
        onNodeWithText("Save changes").assertIsNotEnabled()
        onAllNodesWithContentDescription("More actions")[0].performClick()
        onNodeWithText("Remove from This Playlist").performClick()
        onNodeWithText("Save changes").performClick()
        waitForIdle()
        assertEquals(listOf(listOf("same", "other", "same"), listOf("same", "other")), baselines)
    }

    @Test
    fun compactRenderedMenuFitsAndSitsAfterTheDragHandle() = runDesktopComposeUiTest(420, 720) {
        setContent {
            androidx.compose.material3.MaterialTheme {
                StandardPlaylistManagementList(
                    NaviampColors(), rows().take(2), {}, {},
                    capabilities = PlaylistTrackCapabilities(true, true, true, true),
                )
            }
        }
        val handle = onAllNodesWithContentDescription("Drag to reorder")[0].assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val menu = onAllNodesWithContentDescription("More actions")[0].assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        kotlin.test.assertTrue(menu.center.x > handle.center.x)
        onAllNodesWithContentDescription("More actions")[0].performClick()
        onNodeWithText("Move to Top").assertIsNotEnabled()
        onNodeWithText("Move to Bottom").assertIsDisplayed()
        onNodeWithText("Add to Playlist…").assertIsDisplayed()
        onNodeWithText("Add to Queue").assertIsDisplayed()
        onNodeWithText("Download Track").assertIsDisplayed()
        val image = org.jetbrains.skia.Image.makeFromBitmap(
            onAllNodes(isRoot()).onLast().captureToImage().asSkiaBitmap(),
        )
        image.encodeToData()?.use { png ->
            java.io.File("build/playlist-item-menu-compact.png").writeBytes(png.bytes)
        }
        image.close()
    }

    private fun rows() = listOf(
        SharedTrackRowUi("same", "Duplicate", "Artist"),
        SharedTrackRowUi("other", "Other song", "Artist"),
        SharedTrackRowUi("same", "Duplicate", "Artist"),
    )
}
