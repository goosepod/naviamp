package app.naviamp.ui

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NaviampOfflinePlaylistUiTest {
    private val actions = NaviampPlaylistsActions(
        onRefresh = {},
        onSortModeChanged = {},
        smartPlaylist = NaviampSmartPlaylistActions(
            onSave = {},
            onUpdate = { _, _ -> },
            onSaveWithPassword = { _, _ -> },
            onUpdateWithPassword = { _, _, _ -> },
            onLoad = { error("Not used") },
            onLoadWithPassword = { _, _ -> error("Not used") },
        ),
    )

    private fun row(id: String, title: String, unavailable: Boolean = false) =
        SharedTrackRowUi(id = id, title = title, subtitle = "Artist", meta = "3:00", unavailableOffline = unavailable)

    @Test fun savedPlaylistShowsEveryRowAvailabilityAndOnlyOfflineActions() = runComposeUiTest {
        val playlist = SharedMediaItemUi("road", "Road Trip", "4 tracks", canEditPlaylist = false, canManagePlaylist = false,
            offlineAvailability = NaviampOfflineAvailabilityUi(availableCount = 3, totalCount = 4))
        val tracks = listOf(row("a", "Alpha"), row("b", "Bravo", unavailable = true), row("a", "Alpha"), row("c", "Charlie"))
        val selected = mutableListOf<String>()
        setContent {
            NaviampPlaylistDetailContent(
                colors = NaviampColors(),
                screen = NaviampPlaylistDetailScreenUi(
                    selectedPlaylist = playlist,
                    detail = SharedPlaylistDetailUi(playlist, tracks),
                    trackCapabilities = PlaylistTrackCapabilities(canAddToQueue = true),
                ),
                actions = NaviampPlaylistDetailActions(
                    onBack = {},
                    onPlaylistAction = {},
                    onUpdateStandardPlaylist = { _, _, _ -> error("Edited offline") },
                    onTrackAction = { if (it.action == SharedTrackRowAction.Select) selected += it.track.title },
                ),
                playlistsActions = actions,
                playlistChoices = emptyList(),
            )
        }

        onNodeWithText("3 of 4 tracks available offline").assertExists()
        onNodeWithText("Showing the downloaded copy. Reconnect to edit this playlist.").assertExists()
        assertEquals(2, onAllNodesWithText("Alpha").fetchSemanticsNodes().size)
        onNodeWithText("Not available offline").assertExists()
        onAllNodesWithContentDescription("Play playlist")[0].assertIsEnabled()
        listOf("Rename playlist", "Delete playlist", "Download playlist", "Add playlist to playlist").forEach { label ->
            assertTrue(onAllNodesWithContentDescription(label).fetchSemanticsNodes().isEmpty(), label)
        }

        onNodeWithText("Bravo").performClick()
        onNodeWithText("Charlie").performClick()
        // Core answers an unavailable row with a notice instead of playing it.
        assertEquals(listOf("Bravo", "Charlie"), selected)
    }

    @Test fun playlistNoticeIsDisplayedAndAnnouncedPolitely() = runComposeUiTest {
        val playlist = SharedMediaItemUi("road", "Road Trip", "1 track", canEditPlaylist = false,
            offlineAvailability = NaviampOfflineAvailabilityUi(availableCount = 0, totalCount = 1))
        val notice = "This track is not downloaded. Reconnect to play it."
        setContent {
            NaviampPlaylistDetailContent(
                colors = NaviampColors(),
                screen = NaviampPlaylistDetailScreenUi(
                    selectedPlaylist = playlist,
                    detail = SharedPlaylistDetailUi(playlist, listOf(row("x", "Xray", unavailable = true))),
                    status = notice,
                ),
                actions = NaviampPlaylistDetailActions({}, {}, { _, _, _ -> }, {}),
                playlistsActions = actions,
                playlistChoices = emptyList(),
            )
        }

        onNodeWithText(notice).assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    @Test fun zeroAvailablePlaylistStaysVisibleWithPlaybackDisabled() = runComposeUiTest {
        val playlist = SharedMediaItemUi("quiet", "Quiet", "1 track", canEditPlaylist = false,
            offlineAvailability = NaviampOfflineAvailabilityUi(availableCount = 0, totalCount = 1))
        setContent {
            NaviampPlaylistDetailContent(
                colors = NaviampColors(),
                screen = NaviampPlaylistDetailScreenUi(
                    selectedPlaylist = playlist,
                    detail = SharedPlaylistDetailUi(playlist, listOf(row("x", "Xray", unavailable = true))),
                ),
                actions = NaviampPlaylistDetailActions({}, {}, { _, _, _ -> }, {}),
                playlistsActions = actions,
                playlistChoices = emptyList(),
            )
        }

        onNodeWithText("0 of 1 track available offline").assertExists()
        onAllNodesWithContentDescription("Play playlist")[0].assertIsNotEnabled()
    }
}
