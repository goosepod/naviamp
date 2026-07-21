package app.naviamp.ui

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumId
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.popular.SimilarArtistCandidate
import app.naviamp.domain.popular.SimilarArtistMatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SharedActionSourcesTest {
    @Test
    fun detailActionSourcesResolveSharedIdsWithoutLeakingDomainModelsToPanels() {
        val selectedAlbum = album("selected")
        val detailAlbum = album("detail")
        val artistAlbum = album("artist-album")
        val selectedArtist = Artist(ArtistId("selected-artist"), "Selected Artist")
        val detailArtist = Artist(ArtistId("detail-artist"), "Detail Artist")
        val detailTracks = listOf(track("first"), track("second"))
        val popularTrack = track("popular")
        val similarArtist = Artist(ArtistId("similar-local"), "Similar")
        val similarMatch = SimilarArtistMatch(
            candidate = SimilarArtistCandidate(
                source = "test",
                sourceArtistId = "similar-remote",
                name = similarArtist.name,
                externalUrl = "https://example.test/similar",
            ),
            matchedArtist = similarArtist,
        )
        val sources = SharedDetailActionSources(
            selectedAlbum = selectedAlbum,
            albumDetail = AlbumDetails(detailAlbum, detailTracks),
            selectedArtist = selectedArtist,
            artistDetail = ArtistDetails(detailArtist, listOf(artistAlbum)),
            artistPopularTracks = listOf(popularTrack),
            artistSimilarArtists = listOf(similarMatch),
        )

        assertEquals(detailAlbum, sources.album(detailAlbum.id.value))
        assertEquals(artistAlbum, sources.album(artistAlbum.id.value))
        assertEquals(detailArtist, sources.artist(detailArtist.id.value))
        assertEquals(1 to detailTracks[1], sources.albumTrack("second"))
        assertEquals(popularTrack, sources.popularTrack("popular"))
        assertEquals(listOf(artistAlbum), sources.artistAlbums(listOf("artist-album", "missing")))
        assertEquals(
            similarArtist to "https://example.test/similar",
            sources.similarArtist(
                SharedSimilarArtistUi(
                    id = "similar-remote",
                    title = "Similar",
                    subtitle = "In library",
                    localArtistId = "similar-local",
                ),
            ),
        )
    }

    @Test
    fun playlistActionSourcesPreserveTrackOrderAndRejectStaleRows() {
        val playlist = Playlist("playlist", "Playlist", trackCount = 3)
        val tracks = listOf(track("first"), track("second"), track("third"))
        val sources = SharedPlaylistActionSources(
            playlists = listOf(playlist),
            playlistTracksById = mapOf(playlist.id to tracks),
            selectedPlaylist = playlist,
            selectedPlaylistTracks = tracks,
        )

        assertEquals(playlist, sources.playlist("playlist"))
        assertEquals(1 to tracks[1], sources.selectedTrack("second"))
        assertEquals(
            listOf(tracks[2], tracks[0]),
            sources.selectedTracks(listOf(sharedTrack("third"), sharedTrack("first"))),
        )
        assertNull(sources.selectedTracks(listOf(sharedTrack("first"), sharedTrack("missing"))))
    }

    @Test
    fun internetRadioActionSourcesResolveCurrentIdsAndConvertEdits() {
        val station = InternetRadioStation(
            id = "station-1",
            name = "Station",
            streamUrl = "https://example.test/live",
        )
        val sources = SharedInternetRadioActionSources(listOf(station))

        assertEquals(station, sources.station("station-1"))
        assertNull(sources.station("stale-station"))
        assertEquals(
            InternetRadioStation(
                id = "station-1",
                name = "Updated",
                streamUrl = "https://example.test/updated",
            ),
            sources.station(
                NaviampInternetRadioStationEditUi(
                    id = "station-1",
                    name = " Updated ",
                    streamUrl = " https://example.test/updated ",
                ),
            ),
        )
    }

    @Test
    fun resolvedTrackActionsDispatchTheCurrentDomainTrackAndRequestMetadata() {
        val tracks = listOf(track("first"), track("second"))
        var selected: Pair<Int, Track>? = null
        var artistTarget: Triple<Track, String?, String?>? = null

        handleResolvedTrackRowAction(
            request = SharedTrackRowActionRequest(sharedTrack("second"), SharedTrackRowAction.Select),
            tracks = tracks,
            handlers = ResolvedTrackRowActionHandlers(onSelect = { index, track -> selected = index to track }),
        )
        handleResolvedTrackRowAction(
            request = SharedTrackRowActionRequest(
                track = sharedTrack("first"),
                action = SharedTrackRowAction.GoToArtist,
                artistId = "artist-id",
                artistName = "Artist Name",
            ),
            tracks = tracks,
            handlers = ResolvedTrackRowActionHandlers(
                onGoToArtist = { track, id, name -> artistTarget = Triple(track, id, name) },
            ),
        )

        assertEquals(1 to tracks[1], selected)
        assertEquals(Triple(tracks[0], "artist-id", "Artist Name"), artistTarget)
    }

    @Test
    fun resolvedMediaActionsPreserveShuffleDownloadAndDeduplicatedCopyIntent() {
        val playlist = Playlist("playlist", "Playlist", trackCount = 3)
        val item = SharedMediaItemUi(id = playlist.id, title = playlist.name, subtitle = "Playlist")
        var shuffle: Boolean? = null
        var downloadValue: String? = null
        var copy: Pair<String, Boolean>? = null
        val handlers = ResolvedMediaItemActionHandlers<Playlist>(
            onSelect = null,
            onPlay = { _, requestedShuffle -> shuffle = requestedShuffle },
            onStartRadio = null,
            onFindSimilar = null,
            onAddToQueue = null,
            onDownload = { _, value -> downloadValue = value },
            onAddToPlaylist = null,
            onCreatePlaylistAndAdd = null,
            onCopy = { _, name, deduplicate -> copy = name to deduplicate },
            onToggleFavorite = null,
            onRename = null,
            onEditSmartPlaylist = null,
            onDelete = null,
            onEditStation = null,
            onDeleteStation = null,
        )

        val shuffleResult = handleResolvedMediaItemAction(
            SharedMediaItemActionRequest(item, SharedMediaItemAction.Shuffle),
            playlist,
            handlers,
        )
        val downloadResult = handleResolvedMediaItemAction(
            SharedMediaItemActionRequest(
                item,
                SharedMediaItemAction.Download,
                textValue = KeepDownloadedActionValue,
            ),
            playlist,
            handlers,
        )
        val copyResult = handleResolvedMediaItemAction(
            SharedMediaItemActionRequest(
                item,
                SharedMediaItemAction.CopyPlaylistDeduplicated,
                playlistName = "Copy",
            ),
            playlist,
            handlers,
        )

        assertEquals(true, shuffle)
        assertEquals(KeepDownloadedActionValue, downloadValue)
        assertEquals("Copy" to true, copy)
        assertEquals(MediaItemActionDispatchResult.Dispatched, shuffleResult)
        assertEquals(MediaItemActionDispatchResult.Dispatched, downloadResult)
        assertEquals(MediaItemActionDispatchResult.Dispatched, copyResult)
        assertEquals(
            MediaItemActionDispatchResult.UnsupportedAction,
            handleResolvedMediaItemAction(
                SharedMediaItemActionRequest(item, SharedMediaItemAction.Delete),
                playlist,
                handlers,
            ),
        )
        assertEquals(
            MediaItemActionDispatchResult.InvalidValue,
            handleResolvedMediaItemAction(
                SharedMediaItemActionRequest(item, SharedMediaItemAction.CopyPlaylist, playlistName = " "),
                playlist,
                handlers,
            ),
        )
        assertEquals(
            MediaItemActionDispatchResult.MissingItem,
            handleResolvedMediaItemAction(
                SharedMediaItemActionRequest(item, SharedMediaItemAction.Play),
                null,
                handlers,
            ),
        )
    }

    @Test
    fun playlistDetailDispatcherRequiresAndPreservesEveryVisibleAction() {
        val playlist = Playlist("playlist", "Playlist", trackCount = 3)
        val item = SharedMediaItemUi(id = playlist.id, title = playlist.name, subtitle = "Playlist")
        val choice = NaviampPlaylistChoiceUi("target", "Target")
        val dispatched = mutableListOf<String>()
        val handlers = ResolvedPlaylistDetailActionHandlers<Playlist>(
            onPlay = { _, shuffle -> dispatched += "play:$shuffle" },
            onAddToQueue = { dispatched += "queue" },
            onDownload = { _, value -> dispatched += "download:$value" },
            onAddToPlaylist = { _, target -> dispatched += "add:${target.id}" },
            onCreatePlaylistAndAdd = { _, name -> dispatched += "create:$name" },
            onCopy = { _, name, deduplicate -> dispatched += "copy:$name:$deduplicate" },
            onRename = { _, name -> dispatched += "rename:$name" },
            onDelete = { dispatched += "delete" },
        )
        val requests = listOf(
            NaviampPlaylistDetailActionRequest(item, NaviampPlaylistDetailCommand.Play(shuffle = false)),
            NaviampPlaylistDetailActionRequest(item, NaviampPlaylistDetailCommand.Play(shuffle = true)),
            NaviampPlaylistDetailActionRequest(item, NaviampPlaylistDetailCommand.AddToQueue),
            NaviampPlaylistDetailActionRequest(
                item,
                NaviampPlaylistDetailCommand.Download(KeepDownloadedActionValue),
            ),
            NaviampPlaylistDetailActionRequest(item, NaviampPlaylistDetailCommand.AddToPlaylist(choice)),
            NaviampPlaylistDetailActionRequest(item, NaviampPlaylistDetailCommand.CreatePlaylistAndAdd("New")),
            NaviampPlaylistDetailActionRequest(item, NaviampPlaylistDetailCommand.Copy("Copy", false)),
            NaviampPlaylistDetailActionRequest(item, NaviampPlaylistDetailCommand.Copy("Unique", true)),
            NaviampPlaylistDetailActionRequest(item, NaviampPlaylistDetailCommand.Rename("Renamed")),
            NaviampPlaylistDetailActionRequest(item, NaviampPlaylistDetailCommand.Delete),
        )

        val results = requests.map { request ->
            dispatchResolvedPlaylistDetailAction(request, playlist, handlers)
        }

        assertEquals(List(requests.size) { PlaylistDetailActionDispatchResult.Dispatched }, results)
        assertEquals(
            listOf(
                "play:false",
                "play:true",
                "queue",
                "download:$KeepDownloadedActionValue",
                "add:target",
                "create:New",
                "copy:Copy:false",
                "copy:Unique:true",
                "rename:Renamed",
                "delete",
            ),
            dispatched,
        )
    }

    @Test
    fun playlistDetailDispatcherReportsMissingPlaylistsAndInvalidValues() {
        val playlist = Playlist("playlist", "Playlist", trackCount = 3)
        val item = SharedMediaItemUi(id = playlist.id, title = playlist.name, subtitle = "Playlist")
        val handlers = ResolvedPlaylistDetailActionHandlers<Playlist>(
            onPlay = { _, _ -> },
            onAddToQueue = {},
            onDownload = { _, _ -> },
            onAddToPlaylist = { _, _ -> },
            onCreatePlaylistAndAdd = { _, _ -> },
            onCopy = { _, _, _ -> },
            onRename = { _, _ -> },
            onDelete = {},
        )

        assertEquals(
            PlaylistDetailActionDispatchResult.MissingPlaylist,
            dispatchResolvedPlaylistDetailAction(
                NaviampPlaylistDetailActionRequest(item, NaviampPlaylistDetailCommand.Play(false)),
                null,
                handlers,
            ),
        )
        assertEquals(
            PlaylistDetailActionDispatchResult.InvalidValue,
            dispatchResolvedPlaylistDetailAction(
                NaviampPlaylistDetailActionRequest(item, NaviampPlaylistDetailCommand.CreatePlaylistAndAdd("")),
                playlist,
                handlers,
            ),
        )
        assertEquals(
            "Playlist not found.",
            playlistDetailActionDispatchStatus(PlaylistDetailActionDispatchResult.MissingPlaylist),
        )
    }

    @Test
    fun albumDetailDispatcherRequiresAndPreservesEveryVisibleAction() {
        val album = album("album")
        val item = SharedMediaItemUi(id = album.id.value, title = album.title, subtitle = "Artist")
        val choice = NaviampPlaylistChoiceUi("target", "Target")
        val dispatched = mutableListOf<String>()
        val handlers = ResolvedAlbumDetailActionHandlers<Album>(
            onPlay = { _, shuffle -> dispatched += "play:$shuffle" },
            onStartRadio = { dispatched += "radio" },
            onDownload = { dispatched += "download" },
            onAddToQueue = { dispatched += "queue" },
            onAddToPlaylist = { _, target -> dispatched += "add:${target.id}" },
            onCreatePlaylistAndAdd = { _, name -> dispatched += "create:$name" },
            onToggleFavorite = { dispatched += "favorite" },
        )
        val requests = listOf(
            NaviampAlbumDetailActionRequest(item, NaviampAlbumDetailCommand.Play(false)),
            NaviampAlbumDetailActionRequest(item, NaviampAlbumDetailCommand.Play(true)),
            NaviampAlbumDetailActionRequest(item, NaviampAlbumDetailCommand.StartRadio),
            NaviampAlbumDetailActionRequest(item, NaviampAlbumDetailCommand.Download),
            NaviampAlbumDetailActionRequest(item, NaviampAlbumDetailCommand.AddToQueue),
            NaviampAlbumDetailActionRequest(item, NaviampAlbumDetailCommand.AddToPlaylist(choice)),
            NaviampAlbumDetailActionRequest(item, NaviampAlbumDetailCommand.CreatePlaylistAndAdd("New")),
            NaviampAlbumDetailActionRequest(item, NaviampAlbumDetailCommand.ToggleFavorite),
        )

        val results = requests.map { dispatchResolvedAlbumDetailAction(it, album, handlers) }

        assertEquals(List(requests.size) { AlbumDetailActionDispatchResult.Dispatched }, results)
        assertEquals(
            listOf(
                "play:false",
                "play:true",
                "radio",
                "download",
                "queue",
                "add:target",
                "create:New",
                "favorite",
            ),
            dispatched,
        )
        assertEquals(
            AlbumDetailActionDispatchResult.MissingAlbum,
            dispatchResolvedAlbumDetailAction(requests.first(), null, handlers),
        )
        assertEquals(
            AlbumDetailActionDispatchResult.InvalidValue,
            dispatchResolvedAlbumDetailAction(
                NaviampAlbumDetailActionRequest(item, NaviampAlbumDetailCommand.CreatePlaylistAndAdd("")),
                album,
                handlers,
            ),
        )
    }

    @Test
    fun artistDetailDispatchersRequireEveryArtistAndDiscographyAction() {
        val artist = Artist(ArtistId("artist"), "Artist")
        val artistItem = SharedMediaItemUi("artist", "Artist", "")
        val album = album("album")
        val albumItem = SharedMediaItemUi("album", "Album", "Artist")
        val choice = NaviampPlaylistChoiceUi("target", "Target")
        val similar = SharedSimilarArtistUi("similar", "Similar", "")
        val artistActions = mutableListOf<String>()
        val artistHandlers = ResolvedArtistDetailActionHandlers<Artist>(
            onPlayCatalog = { _, albums, shuffle -> artistActions += "play:${albums.size}:$shuffle" },
            onStartRadio = { artistActions += "radio" },
            onAddToQueue = { artistActions += "queue" },
            onAddToPlaylist = { _, target -> artistActions += "add:${target.id}" },
            onCreatePlaylistAndAdd = { _, name -> artistActions += "create:$name" },
            onToggleFavorite = { artistActions += "favorite" },
            onPlayPopular = { artistActions += "popular-play" },
            onStartPopularRadio = { artistActions += "popular-radio" },
            onAddPopularToQueue = { artistActions += "popular-queue" },
            onFindSimilar = { artistActions += "find-similar" },
            onSelectSimilar = { _, item -> artistActions += "similar:${item.id}" },
            onOpenSimilarExternal = { _, url -> artistActions += "external:$url" },
        )
        val artistRequests = listOf(
            NaviampArtistDetailActionRequest(
                artistItem,
                NaviampArtistDetailCommand.PlayCatalog(listOf(albumItem), false),
            ),
            NaviampArtistDetailActionRequest(artistItem, NaviampArtistDetailCommand.StartRadio),
            NaviampArtistDetailActionRequest(artistItem, NaviampArtistDetailCommand.AddToQueue),
            NaviampArtistDetailActionRequest(artistItem, NaviampArtistDetailCommand.AddToPlaylist(choice)),
            NaviampArtistDetailActionRequest(artistItem, NaviampArtistDetailCommand.CreatePlaylistAndAdd("New")),
            NaviampArtistDetailActionRequest(artistItem, NaviampArtistDetailCommand.ToggleFavorite),
            NaviampArtistDetailActionRequest(artistItem, NaviampArtistDetailCommand.PlayPopular),
            NaviampArtistDetailActionRequest(artistItem, NaviampArtistDetailCommand.StartPopularRadio),
            NaviampArtistDetailActionRequest(artistItem, NaviampArtistDetailCommand.AddPopularToQueue),
            NaviampArtistDetailActionRequest(artistItem, NaviampArtistDetailCommand.FindSimilar),
            NaviampArtistDetailActionRequest(artistItem, NaviampArtistDetailCommand.SelectSimilar(similar)),
            NaviampArtistDetailActionRequest(
                artistItem,
                NaviampArtistDetailCommand.OpenSimilarExternal("https://example.test"),
            ),
        )

        assertEquals(
            List(artistRequests.size) { ArtistDetailActionDispatchResult.Dispatched },
            artistRequests.map { dispatchResolvedArtistDetailAction(it, artist, artistHandlers) },
        )
        assertEquals(12, artistActions.size)

        val albumActions = mutableListOf<String>()
        val albumHandlers = ResolvedArtistAlbumActionHandlers<Album>(
            onSelect = { albumActions += "select" },
            onStartRadio = { albumActions += "radio" },
            onDownload = { albumActions += "download" },
            onAddToQueue = { albumActions += "queue" },
            onAddToPlaylist = { _, target -> albumActions += "add:${target.id}" },
            onCreatePlaylistAndAdd = { _, name -> albumActions += "create:$name" },
            onToggleFavorite = { albumActions += "favorite" },
        )
        val albumRequests = listOf(
            NaviampArtistAlbumActionRequest(albumItem, NaviampArtistAlbumCommand.Select),
            NaviampArtistAlbumActionRequest(albumItem, NaviampArtistAlbumCommand.StartRadio),
            NaviampArtistAlbumActionRequest(albumItem, NaviampArtistAlbumCommand.Download),
            NaviampArtistAlbumActionRequest(albumItem, NaviampArtistAlbumCommand.AddToQueue),
            NaviampArtistAlbumActionRequest(albumItem, NaviampArtistAlbumCommand.AddToPlaylist(choice)),
            NaviampArtistAlbumActionRequest(albumItem, NaviampArtistAlbumCommand.CreatePlaylistAndAdd("New")),
            NaviampArtistAlbumActionRequest(albumItem, NaviampArtistAlbumCommand.ToggleFavorite),
        )

        assertEquals(
            List(albumRequests.size) { ArtistAlbumActionDispatchResult.Dispatched },
            albumRequests.map { dispatchResolvedArtistAlbumAction(it, album, albumHandlers) },
        )
        assertEquals(7, albumActions.size)
        assertEquals(
            ArtistDetailActionDispatchResult.MissingArtist,
            dispatchResolvedArtistDetailAction(artistRequests.first(), null, artistHandlers),
        )
        assertEquals(
            ArtistAlbumActionDispatchResult.MissingAlbum,
            dispatchResolvedArtistAlbumAction(albumRequests.first(), null, albumHandlers),
        )
    }

    private fun album(id: String): Album = Album(
        id = AlbumId(id),
        title = id,
        artistName = "Artist",
        coverArtId = null,
        recentlyAddedAtIso8601 = null,
    )

    private fun track(id: String): Track = Track(
        id = TrackId(id),
        title = id,
        artistName = "Artist",
        albumTitle = null,
        durationSeconds = null,
        coverArtId = null,
        audioInfo = null,
        replayGain = null,
    )

    private fun sharedTrack(id: String): SharedTrackRowUi =
        SharedTrackRowUi(id = id, title = id, subtitle = "Artist")
}
