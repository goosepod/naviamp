package app.naviamp.presentation

import app.naviamp.app.NaviampNavigationController
import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.ProviderId
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.provider.ProviderCapabilities
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.SharedRoute
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
        assertEquals("Similar Artist A", detail.similarArtists.single().title)
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
        controller.media.execute(NaviampCoreCommand.Media.ItemAction(mediaItem("artist-a", "Artist").artistActionRequest(NaviampArtistMediaCommand.Select)))

        assertEquals(SharedRoute.Home, store.state.value.shell.shellChrome.selectedRoute)
        assertNull(store.state.value.shell.albumDetail.detail)
        assertEquals("Connect to Navidrome to load an album.", store.state.value.shell.albumDetail.status)
        assertNull(store.state.value.shell.artistDetail.detail)
        assertEquals("Connect to Navidrome to load an artist.", store.state.value.shell.artistDetail.status)
    }

    private fun kotlinx.coroutines.test.TestScope.controller(
        provider: MediaProvider?,
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
                    listOf(
                        app.naviamp.domain.popular.ArtistPopularTrackMatch(
                            candidate = app.naviamp.domain.popular.ArtistPopularTrackCandidate(
                                source = "test",
                                sourceTrackId = "popular-${artist.id.value}",
                                rank = 1,
                                title = "Popular ${artist.name}",
                            ),
                            matchedTrack = track("popular-${artist.id.value}", "Popular ${artist.name}"),
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

    override suspend fun artist(artistId: ArtistId): ArtistDetails {
        if (artistId.value == "artist-a") firstArtistGate?.await()
        val artist = Artist(
            artistId,
            artistId.value.split('-').joinToString(" ") { part -> part.replaceFirstChar(Char::uppercase) },
        )
        return ArtistDetails(
            artist = artist,
            albums = listOf(Album(AlbumId("album-${artistId.value}"), "Album", artist.name, null, null)),
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
