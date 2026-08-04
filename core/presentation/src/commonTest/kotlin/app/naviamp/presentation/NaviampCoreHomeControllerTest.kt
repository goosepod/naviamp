package app.naviamp.presentation

import app.naviamp.app.NaviampNavigationController
import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.Genre
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Playlist
import app.naviamp.domain.ProviderId
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.home.HomeDate
import app.naviamp.domain.provider.AlbumListType
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.provider.ProviderCapabilities
import app.naviamp.domain.sonichome.SonicHomeDiscoveryRow
import app.naviamp.domain.sonichome.SonicHomeDiscoveryRowId
import app.naviamp.domain.sonichome.SonicHomeDiscoveryRows
import app.naviamp.ui.SharedMixBuilderUi
import app.naviamp.ui.SharedRoute
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NaviampCoreHomeControllerTest {
    @Test
    fun refreshLoadsAndMapsTheCompleteHomeSnapshotInCore() = runTest {
        val provider = HomeTestProvider()
        val store = NaviampCoreStateStore()
        val navigation = NaviampCoreNavigationController(
            navigation = NaviampNavigationController(),
            stateStore = store,
            artistNavigator = NaviampCoreArtistNavigator { error("Not expected") },
        )
        val controller = NaviampCoreHomeController(
            stateStore = store,
            providerSource = NaviampCoreMediaProviderSource { provider },
            navigationController = navigation,
            dateSource = NaviampCoreHomeDateSource { HomeDate(2026, 100) },
            supplementSource = NaviampCoreHomeSupplementSource {
                NaviampCoreHomeSupplement(
                    keepDownloadedPlaylistIds = setOf("playlist-1"),
                    recentInternetRadioStations = listOf(
                        InternetRadioStation("station-1", "Station", "https://radio.example"),
                    ),
                )
            },
        )

        controller.execute(NaviampCoreCommand.Home.Refresh)

        val home = store.state.value.shell.home
        assertFalse(home.refreshing)
        assertEquals("Newest", home.content.recentlyAddedAlbums.single().title)
        assertEquals("playlist-1", home.content.playlists.single().id)
        assertTrue(home.content.playlists.single().keepDownloadedActive)
        assertEquals("Station", home.content.radioStations.single().title)
        assertEquals(listOf("artist", "album", "genre"), home.content.mixBuilders.map { it.id })
        assertEquals(null, store.state.value.overlays.status)
    }

    @Test
    fun builderSelectionUsesTheSharedNavigationController() {
        val store = NaviampCoreStateStore()
        val navigation = NaviampCoreNavigationController(
            navigation = NaviampNavigationController(),
            stateStore = store,
            artistNavigator = NaviampCoreArtistNavigator { error("Not expected") },
        )
        val controller = NaviampCoreHomeController(
            stateStore = store,
            providerSource = NaviampCoreMediaProviderSource { HomeTestProvider() },
            navigationController = navigation,
            dateSource = NaviampCoreHomeDateSource { HomeDate(2026, 100) },
        )

        controller.dispatch(
            NaviampCoreCommand.Home.SelectMixBuilder(
                SharedMixBuilderUi("sonic-path", "Sonic Path", ""),
            ),
        )

        assertEquals(SharedRoute.SonicPath, store.state.value.shell.shellChrome.selectedRoute)
    }

    @Test
    fun refreshBuildsDynamicSonicRowsThroughTheCommonDiscoverySource() = runTest {
        val provider = HomeTestProvider(supportsSonicSimilarity = true)
        val store = NaviampCoreStateStore()
        store.updateShell { shell ->
            shell.copy(
                playback = shell.playback.copy(
                    settings = shell.playback.settings.copy(sonicSimilarityEnabled = true),
                ),
            )
        }
        var loadedSourceId: String? = null
        val controller = NaviampCoreHomeController(
            stateStore = store,
            providerSource = NaviampCoreMediaProviderSource { provider },
            navigationController = NaviampCoreNavigationController(
                NaviampNavigationController(),
                store,
                NaviampCoreArtistNavigator { error("Not expected") },
            ),
            dateSource = NaviampCoreHomeDateSource { HomeDate(2026, 100) },
            supplementSource = NaviampCoreHomeSupplementSource {
                NaviampCoreHomeSupplement(sourceId = "source-1")
            },
            sonicDiscoverySource = NaviampCoreSonicHomeDiscoverySource { _, sourceId ->
                loadedSourceId = sourceId
                SonicHomeDiscoveryRows(
                    listOf(
                        SonicHomeDiscoveryRow(
                            SonicHomeDiscoveryRowId.MoreLikeRecentPlays,
                            "More Like Recent Plays",
                            listOf(track("sonic-track")),
                        ),
                    ),
                )
            },
        )

        controller.execute(NaviampCoreCommand.Home.Refresh)

        assertEquals("source-1", loadedSourceId)
        assertEquals("sonic-track", store.state.value.shell.home.content.sonicDiscoveryRows.single().tracks.single().id)
        assertEquals(
            listOf("artist", "album", "genre", "sonic-path", "sonic-mix"),
            store.state.value.shell.home.content.mixBuilders.map { it.id },
        )
    }

    @Test
    fun disconnectedHomePublishesACommonResult() = runTest {
        val store = NaviampCoreStateStore()
        val navigation = NaviampCoreNavigationController(
            NaviampNavigationController(),
            store,
            NaviampCoreArtistNavigator { error("Not expected") },
        )
        val controller = NaviampCoreHomeController(
            stateStore = store,
            providerSource = NaviampCoreMediaProviderSource { null },
            navigationController = navigation,
            dateSource = NaviampCoreHomeDateSource { HomeDate(2026, 100) },
        )

        controller.execute(NaviampCoreCommand.Home.Refresh)

        assertFalse(store.state.value.shell.home.refreshing)
        assertEquals("Connect to Navidrome to load Home.", store.state.value.overlays.status)
    }

    @Test
    fun sourceSwitchImmediatelyRemovesThePreviousHomesContent() = runTest {
        val store = NaviampCoreStateStore()
        val registry = NaviampCoreMediaRegistry()
        val controller = NaviampCoreHomeController(
            stateStore = store,
            providerSource = NaviampCoreMediaProviderSource { HomeTestProvider() },
            navigationController = NaviampCoreNavigationController(
                NaviampNavigationController(),
                store,
                NaviampCoreArtistNavigator { error("Not expected") },
            ),
            dateSource = NaviampCoreHomeDateSource { HomeDate(2026, 100) },
            mediaRegistry = registry,
        )
        controller.execute(NaviampCoreCommand.Home.Refresh)
        assertFalse(store.state.value.shell.home.content.isEmpty)

        controller.resetForSourceChange()

        assertTrue(store.state.value.shell.home.content.isEmpty)
        assertTrue(registry.home.isEmpty)
    }
}

private class HomeTestProvider(
    supportsSonicSimilarity: Boolean = false,
) : MediaProvider {
    override val id = ProviderId("home")
    override val displayName = "Home"
    override val capabilities = ProviderCapabilities(
        supportsStreamingTranscode = false,
        supportsDownloadTranscode = false,
        supportsArtistRadio = false,
        supportsAlbumRadio = false,
        supportsTrackRadio = false,
        supportsAlbumFavorites = true,
        supportsSonicSimilarity = supportsSonicSimilarity,
    )

    override suspend fun validateConnection() = ConnectionValidation(null, null)
    override suspend fun recentlyAddedAlbums(limit: Int) = listOf(album("Newest"))
    override suspend fun album(albumId: AlbumId): AlbumDetails = error("Not used")
    override suspend fun artist(artistId: ArtistId): ArtistDetails = error("Not used")
    override suspend fun artists(limit: Int) = listOf(Artist(ArtistId("artist-1"), "Artist"))
    override suspend fun albumList(type: AlbumListType, limit: Int) = listOf(album(type.name))
    override suspend fun albumsByGenre(genre: String, limit: Int) = listOf(album("Genre"))
    override suspend fun albumsByYear(fromYear: Int, toYear: Int, limit: Int) = listOf(album("Decade"))
    override suspend fun tracks(limit: Int) = emptyList<Track>()
    override suspend fun search(query: String, limit: Int) = MediaSearchResults()
    override suspend fun playlists(limit: Int) = listOf(Playlist("playlist-1", "Playlist", 10))
    override suspend fun genres(limit: Int) = listOf(Genre("Ambient", 1, 10))
    override suspend fun internetRadioStations() =
        listOf(InternetRadioStation("station-1", "Station", "https://radio.example"))
    override suspend fun streamUrl(request: StreamRequest) = "https://stream.example"
    override fun coverArtUrl(coverArtId: String) = "https://art.example/$coverArtId"

    private fun album(title: String) = Album(
        id = AlbumId(title.lowercase()),
        title = title,
        artistName = "Artist",
        coverArtId = title.lowercase(),
        recentlyAddedAtIso8601 = null,
    )
}

private fun track(id: String) = Track(
    id = TrackId(id),
    title = id,
    artistName = "Artist",
    albumTitle = "Album",
    durationSeconds = 180,
    coverArtId = null,
    audioInfo = null,
    replayGain = null,
)
