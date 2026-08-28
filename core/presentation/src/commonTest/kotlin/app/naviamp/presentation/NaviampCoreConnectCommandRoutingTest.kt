package app.naviamp.presentation

import app.naviamp.domain.connect.NaviampConnectMediaType
import app.naviamp.ui.NaviampAlbumDetailActionRequest
import app.naviamp.ui.NaviampAlbumDetailCommand
import app.naviamp.ui.NaviampArtistMediaCommand
import app.naviamp.ui.NaviampMediaItemActionRequest
import app.naviamp.ui.NaviampMediaItemCommand
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.SharedTrackRowAction
import app.naviamp.ui.SharedTrackRowActionRequest
import app.naviamp.ui.SharedTrackRowUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NaviampCoreConnectCommandRoutingTest {
    @Test
    fun albumPlayPreservesShuffleIntent() {
        val selection = NaviampCoreCommand.Detail.Album(
            NaviampAlbumDetailActionRequest(album, NaviampAlbumDetailCommand.Play(shuffle = true)),
        ).connectMediaSelectionOrNull()

        assertEquals(NaviampConnectMediaType.Album, selection?.type)
        assertEquals("album", selection?.id)
        assertEquals(true, selection?.shuffle)
        assertEquals(false, selection?.startRadio)
    }

    @Test
    fun trackRadioIsRoutedButCatalogNavigationStaysLocal() {
        val radio = NaviampCoreCommand.Media.TrackAction(
            SharedTrackRowActionRequest(track, SharedTrackRowAction.StartRadio),
        ).connectMediaSelectionOrNull()
        val openArtist = NaviampCoreCommand.Media.ItemAction(
            NaviampMediaItemActionRequest(
                artist,
                NaviampMediaItemCommand.Artist(NaviampArtistMediaCommand.Select),
            ),
        ).connectMediaSelectionOrNull()

        assertEquals(NaviampConnectMediaType.Track, radio?.type)
        assertEquals(true, radio?.startRadio)
        assertNull(openArtist)
    }

    @Test
    fun internetRadioSelectionUsesTheStationCatalogId() {
        val selection = NaviampCoreCommand.Home.SelectInternetRadio(station)
            .connectMediaSelectionOrNull()

        assertEquals(NaviampConnectMediaType.InternetRadioStation, selection?.type)
        assertEquals("station", selection?.id)
    }

    private val album = SharedMediaItemUi("album", "Album", "Artist")
    private val artist = SharedMediaItemUi("artist", "Artist", "")
    private val station = SharedMediaItemUi("station", "Station", "")
    private val track = SharedTrackRowUi("track", "Track", "Artist")
}
