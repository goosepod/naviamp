package app.naviamp.presentation

import app.naviamp.domain.connect.NaviampConnectMediaType
import app.naviamp.domain.connect.NaviampConnectPlaybackContext
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NaviampCoreConnectCommandRoutingTest {
    @Test
    fun mainPlaybackCommandsStopSamplesBeforeRoutingButSampleInitiatedPauseDoesNot() = kotlinx.coroutines.test.runTest {
        val events = mutableListOf<String>()
        val delegate = object : NaviampCoreCommandHandler {
            override fun dispatch(command: NaviampCoreCommand) { events += "dispatch" }
            override suspend fun execute(command: NaviampCoreCommand): NaviampCoreCommandResult {
                events += "execute"
                return NaviampCoreCommandResult.Completed
            }
        }
        val handler = NaviampCoreConnectCommandHandler(delegate, null) { events += "stop sample" }
        fun playback(action: app.naviamp.ui.NowPlayingPlaybackAction) = NaviampCoreCommand.NowPlaying.Playback(
            app.naviamp.ui.NowPlayingPlaybackActionRequest(action))
        handler.dispatch(playback(app.naviamp.ui.NowPlayingPlaybackAction.Pause))
        assertEquals(listOf("dispatch"), events)
        events.clear()
        handler.dispatch(playback(app.naviamp.ui.NowPlayingPlaybackAction.Resume))
        assertEquals(listOf("stop sample", "dispatch"), events)
        events.clear()
        handler.execute(NaviampCoreCommand.Detail.Album(
            NaviampAlbumDetailActionRequest(album, NaviampAlbumDetailCommand.Play(shuffle = false))))
        assertEquals(listOf("stop sample", "execute"), events)
    }

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
    fun homeAlbumMixReachesConnectOnlyAsSeedTrackRadioNeverAsTheAlbum() {
        val tile = NaviampCoreCommand.Media.ItemAction(
            NaviampMediaItemActionRequest(album, NaviampMediaItemCommand.PlayAlbumMix),
        )
        val seed = app.naviamp.domain.Track(
            id = app.naviamp.domain.TrackId("seed"), title = "Seed", artistName = "Artist",
            albumId = app.naviamp.domain.AlbumId("album"), albumTitle = "Album",
            durationSeconds = 180, coverArtId = null, audioInfo = null, replayGain = null,
        )
        val resolved = NaviampCoreCommand.Home.StartAlbumMix(
            app.naviamp.domain.Album(app.naviamp.domain.AlbumId("album"), "Album", "Artist", null, null),
            seed,
        )

        // The tile stays local until Core resolves its seed, so it can never be sent as an album.
        assertNull(tile.connectMediaSelectionOrNull())
        assertNull(tile.connectQueueSelectionOrNull())
        val selection = resolved.connectMediaSelectionOrNull()
        assertEquals(NaviampConnectMediaType.Track, selection?.type)
        assertEquals("seed", selection?.id)
        assertEquals(true, selection?.startRadio)
        assertNull(resolved.connectQueueSelectionOrNull())
    }

    @Test
    fun playSomethingNewPicksLocallyThenReachesConnectAsSeedTrackRadio() {
        val seed = app.naviamp.domain.Track(
            id = app.naviamp.domain.TrackId("fresh"), title = "Fresh", artistName = "Artist",
            albumTitle = "Album", durationSeconds = 180, coverArtId = null, audioInfo = null, replayGain = null,
        )

        assertNull(NaviampCoreCommand.Home.PlaySomethingNew.connectMediaSelectionOrNull())
        val selection = NaviampCoreCommand.Home.StartSomethingNewRadio(seed).connectMediaSelectionOrNull()
        assertEquals(NaviampConnectMediaType.Track, selection?.type)
        assertEquals("fresh", selection?.id)
        assertEquals(true, selection?.startRadio)
        assertEquals(false, NaviampCoreCommand.Home.StartSomethingNewRadio(seed, startRadio = false)
            .connectMediaSelectionOrNull()?.startRadio)
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

    @Test
    fun albumTrackSelectionNamesTheVisibleAlbumSoTheTargetQueuesItFromTheChosenTrack() {
        val registry = NaviampCoreMediaRegistry()
        val tracks = (1..4).map { domainTrack("album-$it") }
        registry.updateAlbum(app.naviamp.domain.AlbumDetails(domainAlbum("album"), tracks))
        val command = NaviampCoreCommand.Detail.AlbumTrack(
            SharedTrackRowActionRequest(SharedTrackRowUi("album-3", "Three", "Artist"), SharedTrackRowAction.Select),
        )

        val selection = assertNotNull(command.connectMediaSelectionOrNull())
            .withTrackListContext(registry.connectTrackSelectionContext(command))
        val sent = selection.toCommand(identity)

        assertEquals(NaviampConnectMediaType.Track, sent.mediaType)
        assertEquals("album-3", sent.mediaId)
        assertEquals(NaviampConnectPlaybackContext(NaviampConnectMediaType.Album, "album"), sent.context)
        assertEquals(false, sent.shuffle)
        assertEquals(false, sent.startRadio)
    }

    @Test
    fun playlistTrackSelectionNamesTheVisiblePlaylist() {
        val registry = NaviampCoreMediaRegistry()
        registry.updateSelectedPlaylist(app.naviamp.domain.Playlist("mix", "Mix", 2),
            listOf(domainTrack("p-1"), domainTrack("p-2")))
        val command = NaviampCoreCommand.Detail.PlaylistTrack(
            SharedTrackRowActionRequest(SharedTrackRowUi("p-2", "Two", "Artist"), SharedTrackRowAction.Select),
        )

        assertEquals(
            NaviampConnectPlaybackContext(NaviampConnectMediaType.Playlist, "mix"),
            registry.connectTrackSelectionContext(command),
        )
    }

    @Test
    fun trackListContextIsOnlyAttachedToPlainSelectionsFromAKnownList() {
        val registry = NaviampCoreMediaRegistry()
        registry.updateAlbum(app.naviamp.domain.AlbumDetails(domainAlbum("album"), listOf(domainTrack("album-1"))))
        fun albumTrack(id: String, action: SharedTrackRowAction) = NaviampCoreCommand.Detail.AlbumTrack(
            SharedTrackRowActionRequest(SharedTrackRowUi(id, id, "Artist"), action))

        assertNull(registry.connectTrackSelectionContext(albumTrack("album-1", SharedTrackRowAction.StartRadio)))
        assertNull(registry.connectTrackSelectionContext(albumTrack("album-1", SharedTrackRowAction.AddToQueue)))
        assertNull(registry.connectTrackSelectionContext(albumTrack("stale", SharedTrackRowAction.Select)))
        assertNull(NaviampCoreMediaRegistry().connectTrackSelectionContext(albumTrack("album-1", SharedTrackRowAction.Select)))
        assertNull(registry.connectTrackSelectionContext(NaviampCoreCommand.Media.TrackAction(
            SharedTrackRowActionRequest(SharedTrackRowUi("album-1", "One", "Artist"), SharedTrackRowAction.Select))))

        val context = NaviampConnectPlaybackContext(NaviampConnectMediaType.Album, "album")
        val radio = NaviampCoreConnectMediaSelection(NaviampConnectMediaType.Track, "album-1", startRadio = true)
        val albumPlay = NaviampCoreConnectMediaSelection(NaviampConnectMediaType.Album, "album")
        assertNull(radio.withTrackListContext(context).context)
        assertNull(albumPlay.withTrackListContext(context).context)
        assertNull(radio.withTrackListContext(context).toCommand(identity).context)
    }

    private val identity = app.naviamp.domain.connect.NaviampConnectSourceIdentity("navidrome", "https://music.example", "listener")
    private fun domainAlbum(id: String) = app.naviamp.domain.Album(app.naviamp.domain.AlbumId(id), "Album", "Artist",
        coverArtId = null, recentlyAddedAtIso8601 = null)
    private fun domainTrack(id: String) = app.naviamp.domain.Track(id = app.naviamp.domain.TrackId(id), title = id,
        artistName = "Artist", albumId = app.naviamp.domain.AlbumId("album"), albumTitle = "Album", durationSeconds = 180,
        coverArtId = null, audioInfo = null, replayGain = null)

    private val album = SharedMediaItemUi("album", "Album", "Artist")
    private val artist = SharedMediaItemUi("artist", "Artist", "")
    private val station = SharedMediaItemUi("station", "Station", "")
    private val track = SharedTrackRowUi("track", "Track", "Artist")
}
