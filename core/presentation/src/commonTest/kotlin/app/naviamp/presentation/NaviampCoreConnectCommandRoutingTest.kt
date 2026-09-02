package app.naviamp.presentation

import app.naviamp.domain.connect.NaviampConnectMediaType
import app.naviamp.domain.connect.NaviampConnectQueuePlacement
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

    @Test
    fun trackQueueActionsPreserveTheirRemotePlacement() {
        val commands = listOf(
            SharedTrackRowAction.AddToQueue to NaviampConnectQueuePlacement.AddToQueue,
            SharedTrackRowAction.PlayNext to NaviampConnectQueuePlacement.PlayNext,
            SharedTrackRowAction.PlayNextTrack to NaviampConnectQueuePlacement.PlayNextTrack,
        )

        commands.forEach { (action, expectedPlacement) ->
            val selection = NaviampCoreCommand.Media.TrackAction(
                SharedTrackRowActionRequest(track, action),
            ).connectQueueSelectionOrNull()
            assertEquals(NaviampConnectMediaType.Track, selection?.type)
            assertEquals("track", selection?.id)
            assertEquals(expectedPlacement, selection?.placement)
        }
    }

    @Test
    fun playbackAndNonQueueTrackActionsDoNotBecomeQueueEdits() {
        assertNull(
            NaviampCoreCommand.Media.TrackAction(
                SharedTrackRowActionRequest(track, SharedTrackRowAction.Select),
            ).connectQueueSelectionOrNull(),
        )
        assertNull(
            NaviampCoreCommand.Media.TrackAction(
                SharedTrackRowActionRequest(track, SharedTrackRowAction.StartRadio),
            ).connectQueueSelectionOrNull(),
        )
    }

    @Test
    fun collectionAddToQueueActionsTargetTheRemoteCatalog() {
        val selections = listOf(
            NaviampCoreCommand.Detail.Album(
                NaviampAlbumDetailActionRequest(album, NaviampAlbumDetailCommand.AddToQueue),
            ).connectQueueSelectionOrNull(),
            NaviampCoreCommand.Media.ItemAction(
                NaviampMediaItemActionRequest(
                    artist,
                    NaviampMediaItemCommand.Artist(NaviampArtistMediaCommand.AddToQueue),
                ),
            ).connectQueueSelectionOrNull(),
        )

        assertEquals(NaviampConnectMediaType.Album, selections[0]?.type)
        assertEquals("album", selections[0]?.id)
        assertEquals(NaviampConnectMediaType.Artist, selections[1]?.type)
        assertEquals("artist", selections[1]?.id)
        assertEquals(
            listOf(NaviampConnectQueuePlacement.AddToQueue, NaviampConnectQueuePlacement.AddToQueue),
            selections.map { it?.placement },
        )
    }

    private val album = SharedMediaItemUi("album", "Album", "Artist")
    private val artist = SharedMediaItemUi("artist", "Artist", "")
    private val station = SharedMediaItemUi("station", "Station", "")
    private val track = SharedTrackRowUi("track", "Track", "Artist")
}
