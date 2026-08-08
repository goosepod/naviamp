package app.naviamp.presentation

import app.naviamp.app.NaviampNavigationController
import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.AlbumInfo
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.ArtistInfo
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.ProviderId
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.provider.ProviderCapabilities
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.SharedRoute
import app.naviamp.ui.NaviampPlaylistDetailScreenUi
import app.naviamp.ui.NaviampArtistAlbumCommand
import app.naviamp.ui.NaviampArtistMediaCommand
import app.naviamp.ui.albumActionRequest
import app.naviamp.ui.artistActionRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NaviampCoreMediaDetailControllerTest {
    @Test
    fun albumSelectionOwnsNavigationLoadingMappingAndCapabilities() = runTest {
        val provider = MediaDetailTestProvider()
        val (store, controller) = controller(provider)

        controller.execute(NaviampCoreCommand.Media.ItemAction(mediaItem("album-1", "Requested album").albumActionRequest(NaviampArtistAlbumCommand.Select)))

        val state = store.state.value
        assertEquals(SharedRoute.Home, state.shell.shellChrome.selectedRoute)
        assertEquals("Canonical album", state.shell.albumDetail.selectedAlbum?.title)
        assertTrue(state.shell.albumDetail.selectedAlbum?.canFavorite == true)
        assertEquals("Track one", state.shell.albumDetail.detail?.tracks?.single()?.title)
        assertTrue(state.shell.albumDetail.detail?.tracks?.single()?.popular == true)
        assertEquals("Connected.", state.shell.albumDetail.status)
    }

    @Test
    fun artistSelectionOwnsEnrichmentMappingAndNestedBackHistory() = runTest {
        val provider = MediaDetailTestProvider()
        val (store, controller) = controller(provider)

        controller.execute(NaviampCoreCommand.Media.ItemAction(mediaItem("artist-a", "Artist A").artistActionRequest(NaviampArtistMediaCommand.Select)))
        controller.execute(NaviampCoreCommand.Media.ItemAction(mediaItem("artist-b", "Artist B").artistActionRequest(NaviampArtistMediaCommand.Select)))
        assertEquals("Artist B", store.state.value.shell.artistDetail.detail?.artist?.title)

        controller.navigation.dispatch(NaviampCoreCommand.Navigation.BackFromArtist)
        runCurrent()

        val detail = assertNotNull(store.state.value.shell.artistDetail.detail)
        assertEquals("Artist A", detail.artist.title)
        assertEquals("Popular Artist A", detail.popularTracks.single().title)
        assertTrue(detail.similarArtists.isEmpty())
        assertTrue(!detail.similarArtistsExpanded)

        controller.media.toggleSimilarArtists(mediaItem("artist-a", "Artist A"))
        val expanded = assertNotNull(store.state.value.shell.artistDetail.detail)
        assertEquals("Similar Artist A", expanded.similarArtists.single().title)
        assertTrue(expanded.similarArtistsExpanded)

        controller.media.toggleSimilarArtists(mediaItem("artist-a", "Artist A"))
        val collapsed = assertNotNull(store.state.value.shell.artistDetail.detail)
        assertTrue(collapsed.similarArtists.isEmpty())
        assertTrue(!collapsed.similarArtistsExpanded)
        assertEquals(SharedRoute.Home, store.state.value.shell.shellChrome.selectedRoute)
    }

    @Test
    fun staleArtistLoadCannotOverwriteANewerSelection() = runTest {
        val firstArtistGate = CompletableDeferred<Unit>()
        val provider = MediaDetailTestProvider(firstArtistGate)
        val (store, controller) = controller(provider)

        val first = launch {
            controller.media.execute(NaviampCoreCommand.Media.ItemAction(mediaItem("artist-a", "Artist A").artistActionRequest(NaviampArtistMediaCommand.Select)))
        }
        runCurrent()
        controller.media.execute(NaviampCoreCommand.Media.ItemAction(mediaItem("artist-b", "Artist B").artistActionRequest(NaviampArtistMediaCommand.Select)))
        firstArtistGate.complete(Unit)
        first.join()

        assertEquals("Artist B", store.state.value.shell.artistDetail.detail?.artist?.title)
    }

    @Test
    fun missingProviderStillNavigatesAndPublishesACompleteFailureState() = runTest {
        val (store, controller) = controller(null)

        controller.media.execute(NaviampCoreCommand.Media.ItemAction(mediaItem("album-1", "Album").albumActionRequest(NaviampArtistAlbumCommand.Select)))
        assertEquals(SharedRoute.Home, store.state.value.shell.shellChrome.selectedRoute)
        assertNull(store.state.value.shell.albumDetail.detail)
        assertEquals("Connect to Navidrome to load an album.", store.state.value.shell.albumDetail.status)

        controller.media.execute(NaviampCoreCommand.Media.ItemAction(mediaItem("artist-a", "Artist").artistActionRequest(NaviampArtistMediaCommand.Select)))

        assertNull(store.state.value.shell.albumDetail.selectedAlbum)
        assertNull(store.state.value.shell.artistDetail.detail)
        assertEquals("Connect to Navidrome to load an artist.", store.state.value.shell.artistDetail.status)
    }

    @Test
    fun artistSelectionClearsCompetingAlbumAndPlaylistPresentation() = runTest {
        val provider = MediaDetailTestProvider()
        val (store, controller) = controller(provider)
        store.updateShell { shell ->
            shell.copy(
                albumDetail = shell.albumDetail.copy(selectedAlbum = mediaItem("old-album", "Old Album")),
                playlistDetail = NaviampPlaylistDetailScreenUi(
                    selectedPlaylist = mediaItem("old-playlist", "Old Playlist"),
                ),
            )
        }

        controller.execute(
            NaviampCoreCommand.Media.ItemAction(
                mediaItem("artist-a", "Artist A").artistActionRequest(NaviampArtistMediaCommand.Select),
            ),
        )

        assertNull(store.state.value.shell.albumDetail.selectedAlbum)
        assertNull(store.state.value.shell.playlistDetail.selectedPlaylist)
        assertNotNull(store.state.value.shell.artistDetail.detail)
    }

    @Test
    fun informationVisibilityChangesRemapLoadedDetailsImmediatelyAndIndependently() = runTest {
        val provider = MediaDetailTestProvider()
        val (store, controller) = controller(provider)

        controller.execute(
            NaviampCoreCommand.Media.ItemAction(
                mediaItem("album-1", "Album").albumActionRequest(NaviampArtistAlbumCommand.Select),
            ),
        )
        runCurrent()
        assertEquals("Album notes", store.state.value.shell.albumDetail.detail?.information)

        controller.media.interfaceSettingsChanged(
            InterfaceSettings(showArtistInformation = true, showAlbumInformation = false),
        )
        assertNull(store.state.value.shell.albumDetail.detail?.information)

        controller.media.interfaceSettingsChanged(
            InterfaceSettings(showArtistInformation = false, showAlbumInformation = true),
        )
        assertEquals("Album notes", store.state.value.shell.albumDetail.detail?.information)

        controller.execute(
            NaviampCoreCommand.Media.ItemAction(
                mediaItem("artist-b", "Artist B").artistActionRequest(NaviampArtistMediaCommand.Select),
            ),
        )
        assertEquals("Artist biography", store.state.value.shell.artistDetail.detail?.biography)

        controller.media.interfaceSettingsChanged(
            InterfaceSettings(showArtistInformation = false, showAlbumInformation = true),
        )
        assertNull(store.state.value.shell.artistDetail.detail?.biography)

        controller.media.interfaceSettingsChanged(
            InterfaceSettings(showArtistInformation = true, showAlbumInformation = false),
        )
        assertEquals("Artist biography", store.state.value.shell.artistDetail.detail?.biography)
    }

    @Test
    fun albumInformationEnrichesAnAlreadyVisibleAlbumAfterAProviderDelay() = runTest {
        val albumInfoGate = CompletableDeferred<Unit>()
        val provider = MediaDetailTestProvider(albumInfoGate = albumInfoGate)
        val (store, controller) = controller(provider)

        val load = launch {
            controller.execute(
                NaviampCoreCommand.Media.ItemAction(
                    mediaItem("album-1", "Album").albumActionRequest(NaviampArtistAlbumCommand.Select),
                ),
            )
        }
        runCurrent()

        assertEquals("Canonical album", store.state.value.shell.albumDetail.detail?.album?.title)
        assertNull(store.state.value.shell.albumDetail.detail?.information)

        albumInfoGate.complete(Unit)
        runCurrent()
        load.join()

        assertEquals("Album notes", store.state.value.shell.albumDetail.detail?.information)
    }

    @Test
    fun popularTrackEnrichmentCannotDelayAlbumInformation() = runTest {
        val popularTracksGate = CompletableDeferred<Unit>()
        val provider = MediaDetailTestProvider()
        val (store, controller) = controller(provider, popularTracksGate)

        val load = launch {
            controller.execute(
                NaviampCoreCommand.Media.ItemAction(
                    mediaItem("album-1", "Album").albumActionRequest(NaviampArtistAlbumCommand.Select),
                ),
            )
        }
        runCurrent()

        assertEquals("Album notes", store.state.value.shell.albumDetail.detail?.information)

        popularTracksGate.complete(Unit)
        load.join()

        assertEquals("Album notes", store.state.value.shell.albumDetail.detail?.information)
        assertTrue(store.state.value.shell.albumDetail.detail?.tracks?.single()?.popular == true)
    }

    private fun kotlinx.coroutines.test.TestScope.controller(
        provider: MediaProvider?,
        popularTracksGate: CompletableDeferred<Unit>? = null,
    ): Pair<NaviampCoreStateStore, MediaDetailControllers> {
        val store = NaviampCoreStateStore()
        lateinit var media: NaviampCoreMediaDetailController
        val navigation = NaviampCoreNavigationController(
            navigation = NaviampNavigationController(),
            stateStore = store,
            artistNavigator = NaviampCoreArtistNavigator { media.openArtist(it) },
        )
        media = NaviampCoreMediaDetailController(
            stateStore = store,
            providerSource = NaviampCoreMediaProviderSource { provider },
            navigationController = navigation,
            scope = backgroundScope,
            discovery = NaviampCoreArtistDiscoveryServices(
                sourceId = { "source" },
                popularTracks = { _, artist, _ ->
                    popularTracksGate?.await()
                    val popularId = if (artist.name == "Artist") "track-1" else "popular-${artist.id.value}"
                    listOf(
                        app.naviamp.domain.popular.ArtistPopularTrackMatch(
                            candidate = app.naviamp.domain.popular.ArtistPopularTrackCandidate(
                                source = "test",
                                sourceTrackId = popularId,
                                rank = 1,
                                title = "Popular ${artist.name}",
                            ),
                            matchedTrack = track(popularId, "Popular ${artist.name}"),
                            fetchedAtEpochMillis = 0,
                        ),
                    )
                },
                similarArtists = { artist, _ ->
                    listOf(
                        app.naviamp.domain.popular.SimilarArtistMatch(
                            candidate = app.naviamp.domain.popular.SimilarArtistCandidate(
                                source = "test",
                                sourceArtistId = "similar-${artist.id.value}",
                                name = "Similar ${artist.name}",
                            ),
                        ),
                    )
                },
            ),
        )
        return store to MediaDetailControllers(media, navigation)
    }

    private fun mediaItem(id: String, title: String) = SharedMediaItemUi(id = id, title = title, subtitle = "")
}

private data class MediaDetailControllers(
    val media: NaviampCoreMediaDetailController,
    val navigation: NaviampCoreNavigationController,
) {
    suspend fun execute(command: NaviampCoreCommand) = media.execute(command)
}

private class MediaDetailTestProvider(
    private val firstArtistGate: CompletableDeferred<Unit>? = null,
    private val albumInfoGate: CompletableDeferred<Unit>? = null,
) : MediaProvider {
    override val id = ProviderId("media-detail")
    override val displayName = "Media detail"
    override val capabilities = ProviderCapabilities(
        supportsStreamingTranscode = false,
        supportsDownloadTranscode = false,
        supportsArtistRadio = false,
        supportsAlbumRadio = false,
        supportsTrackRadio = false,
        supportsArtistFavorites = true,
        supportsAlbumFavorites = true,
    )

    override suspend fun validateConnection() = ConnectionValidation(null, null)
    override suspend fun recentlyAddedAlbums(limit: Int) = emptyList<Album>()
    override suspend fun album(albumId: AlbumId) = AlbumDetails(
        album = Album(albumId, "Canonical album", "Artist", "cover", null),
        tracks = listOf(track("track-1", "Track one", albumId)),
    )

    override suspend fun albumInfo(albumId: AlbumId): AlbumInfo {
        albumInfoGate?.await()
        return AlbumInfo(notes = "Album notes", largeImageUrl = "https://info.example/album.jpg")
    }

    override suspend fun artist(artistId: ArtistId): ArtistDetails {
        if (artistId.value == "artist-a") firstArtistGate?.await()
        val artist = Artist(
            artistId,
            artistId.value.split('-').joinToString(" ") { part -> part.replaceFirstChar(Char::uppercase) },
        )
        return ArtistDetails(
            artist = artist,
            albums = listOf(Album(AlbumId("album-${artistId.value}"), "Album", artist.name, null, null)),
            info = ArtistInfo("Artist biography", null, null, "https://info.example/artist.jpg"),
        )
    }

    override suspend fun artists(limit: Int) = emptyList<Artist>()
    override suspend fun tracks(limit: Int) = emptyList<Track>()
    override suspend fun search(query: String, limit: Int) = MediaSearchResults()
    override suspend fun streamUrl(request: StreamRequest) = "https://stream.example"
    override fun coverArtUrl(coverArtId: String) = "https://art.example/$coverArtId"
}

private fun track(id: String, title: String, albumId: AlbumId? = null) = Track(
    id = app.naviamp.domain.TrackId(id),
    title = title,
    artistName = "Artist",
    albumId = albumId,
    albumTitle = "Album",
    durationSeconds = 180,
    coverArtId = "cover",
    audioInfo = null,
    replayGain = null,
)
