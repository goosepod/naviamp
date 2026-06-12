package app.naviamp.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class NaviampActionCatalogTest {
    @Test
    fun trackRowActionsIncludeRadioQueueActionsWhenRadioIsAvailable() {
        val actions = trackRowActions(
            canStartRadio = true,
            canDownload = true,
            canAddToQueue = true,
            canAddToPlaylist = true,
        )

        assertEquals(
            listOf(
                NaviampAction.StartTrackRadio,
                NaviampAction.PlayTrackRadioNext,
                NaviampAction.AddTrackRadioToQueue,
                NaviampAction.DownloadTrack,
                NaviampAction.AddToQueue,
                NaviampAction.AddToPlaylist,
                NaviampAction.TrackDetails,
            ),
            actions.map { it.action },
        )
    }

    @Test
    fun playlistRowActionsIncludeOnlyAvailablePlaylistActionsInMenuOrder() {
        val actions = playlistRowActions(
            canDownload = true,
            canAddToQueue = true,
            canAddToPlaylist = true,
            canRename = true,
            canEditSmartPlaylist = true,
            canDelete = true,
        )

        assertEquals(
            listOf(
                NaviampAction.DownloadPlaylist,
                NaviampAction.AddToQueue,
                NaviampAction.AddPlaylistToPlaylist,
                NaviampAction.RenamePlaylist,
                NaviampAction.EditSmartPlaylist,
                NaviampAction.DeletePlaylist,
            ),
            actions.map { it.action },
        )
    }

    @Test
    fun playlistRowActionsOmitUnavailableSmartPlaylistAction() {
        val actions = playlistRowActions(canRename = true, canDelete = true)

        assertEquals(
            listOf(NaviampAction.RenamePlaylist, NaviampAction.DeletePlaylist),
            actions.map { it.action },
        )
    }
}
