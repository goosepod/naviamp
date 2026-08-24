package app.naviamp.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class NaviampActionCatalogTest {
    @Test
    fun sharedMediaBaselineIncludesEveryCrossPlatformProductAction() {
        assertEquals(true, NaviampSharedMediaCapabilities.album.canAddToPlaylist)
        assertEquals(true, NaviampSharedMediaCapabilities.artist.canAddToQueue)
        assertEquals(true, NaviampSharedMediaCapabilities.artist.canAddToPlaylist)
        assertEquals(true, NaviampSharedMediaCapabilities.playlist.canEditSmartPlaylist)
    }

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
                NaviampAction.PlayNext,
                NaviampAction.PlayNextTrack,
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
        assertEquals("Start Radio", actions.first { it.action == NaviampAction.StartTrackRadio }.label)
    }

    @Test
    fun albumRadioAppearsBeforeFavoriteWithTheSharedLabel() {
        val actions = albumRowActions(
            canStartRadio = true,
            canFavorite = true,
        )

        assertEquals(
            listOf(NaviampAction.StartAlbumRadio, NaviampAction.ToggleFavorite),
            actions.map { it.action },
        )
        assertEquals("Start Radio", actions.first().label)
    }

    @Test
    fun trackRowActionsExposeAllCapabilityAwareSwipeActionsInTheMenu() {
        val actions = trackRowActions(
            canAddToQueue = true,
            canToggleFavorite = true,
            favoriteActive = true,
            hasAlbum = true,
            hasArtist = true,
            canShowDetails = false,
        )

        assertEquals(
            listOf(
                NaviampAction.PlayNext,
                NaviampAction.PlayNextTrack,
                NaviampAction.AddToQueue,
                NaviampAction.ToggleFavorite,
                NaviampAction.GoToAlbum,
                NaviampAction.GoToArtist,
            ),
            actions.map { it.action },
        )
        assertEquals("Unfavorite", actions.first { it.action == NaviampAction.ToggleFavorite }.label)
    }

    @Test
    fun queueRowActionsReplaceTrackDetailsWithArtistAndAlbumNavigation() {
        assertEquals(
            listOf(
                NaviampAction.PlayNext,
                NaviampAction.PlayNextTrack,
                NaviampAction.StartTrackRadio,
                NaviampAction.PlayTrackRadioNext,
                NaviampAction.AddTrackRadioToQueue,
                NaviampAction.DownloadTrack,
                NaviampAction.AddToPlaylist,
                NaviampAction.GoToArtist,
                NaviampAction.GoToAlbum,
            ),
            queueRowActions().map { it.action },
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

    @Test
    fun keepDownloadedPlaylistActionReflectsCurrentPolicy() {
        val inactive = playlistRowActions(canKeepDownloaded = true).single()
        val active = playlistRowActions(canKeepDownloaded = true, keepDownloadedActive = true).single()

        assertEquals("Keep downloaded", inactive.label)
        assertEquals("Stop keeping downloaded", active.label)
    }
}
