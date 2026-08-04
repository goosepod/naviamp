package app.naviamp.presentation

import app.naviamp.app.NaviampNavigationController
import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.Playlist
import app.naviamp.domain.ProviderId
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.provider.ProviderCapabilities
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.SharedPlaylistSortMode
import app.naviamp.ui.NaviampPlaylistMediaCommand
import app.naviamp.ui.NaviampAlbumDetailScreenUi
import app.naviamp.ui.NaviampArtistDetailScreenUi
import app.naviamp.ui.playlistActionRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NaviampCorePlaylistBrowseControllerTest {
    @Test
    fun refreshMapsPlaylistSupplementsAndSortIsImmediateCoreState() = runTest {
        val provider = PlaylistBrowseTestProvider()
        val (store, controller) = controller(provider)

        controller.execute(NaviampCoreCommand.Playlists.Refresh)
        controller.dispatch(NaviampCoreCommand.Playlists.ChangeSort(SharedPlaylistSortMode.RecentlyPlayed))

        val state = store.state.value.shell.playlists
        assertFalse(state.refreshing)
        assertEquals(listOf("playlist-a", "playlist-b"), state.playlists.map { it.id })
        assertEquals(listOf("playlist-b"), state.recentPlaylistIds)
        assertTrue(state.playlists.first().keepDownloadedActive)
        assertEquals(SharedPlaylistSortMode.RecentlyPlayed, state.sortMode)
        assertEquals(
            listOf("playlist-a"),
            store.state.value.shell.playlistChoices.map { it.id },
            "Only mutable server playlists should be offered as add-to-playlist targets.",
        )
    }

    @Test
    fun selectionLoadsAndMapsPlaylistDetailInsideCore() = runTest {
        val provider = PlaylistBrowseTestProvider()
        val (store, controller) = controller(provider)
        controller.execute(NaviampCoreCommand.Playlists.Refresh)

        controller.execute(
            NaviampCoreCommand.Media.ItemAction(
                SharedMediaItemUi("playlist-a", "Playlist A", "2 tracks")
                    .playlistActionRequest(NaviampPlaylistMediaCommand.Select),
            ),
        )

        val state = store.state.value.shell.playlistDetail
        assertEquals("Playlist A", state.selectedPlaylist?.title)
        assertEquals(listOf("Track 1", "Track 2"), state.detail?.tracks?.map { it.title })
        assertEquals(2, state.detail?.playlist?.trackCount)
        assertEquals("Connected.", state.status)
    }

    @Test
    fun stalePlaylistDetailCannotOverwriteANewerSelection() = runTest {
        val firstGate = CompletableDeferred<Unit>()
        val provider = PlaylistBrowseTestProvider(firstGate)
        val (store, controller) = controller(provider)

        val first = launch {
            controller.execute(
                NaviampCoreCommand.Media.ItemAction(
                    SharedMediaItemUi("playlist-a", "Playlist A", "").playlistActionRequest(NaviampPlaylistMediaCommand.Select),
                ),
            )
        }
        runCurrent()
        controller.execute(
            NaviampCoreCommand.Media.ItemAction(
                SharedMediaItemUi("playlist-b", "Playlist B", "").playlistActionRequest(NaviampPlaylistMediaCommand.Select),
            ),
        )
        firstGate.complete(Unit)
        first.join()

        assertEquals("playlist-b", store.state.value.shell.playlistDetail.detail?.playlist?.id)
    }

    @Test
    fun disconnectedBrowsePublishesListAndDetailFailures() = runTest {
        val (store, controller) = controller(null)

        controller.execute(NaviampCoreCommand.Playlists.Refresh)
        controller.execute(
            NaviampCoreCommand.Media.ItemAction(
                SharedMediaItemUi("playlist-a", "Playlist A", "").playlistActionRequest(NaviampPlaylistMediaCommand.Select),
            ),
        )

        assertEquals("Connect to Navidrome to load playlists.", store.state.value.shell.playlists.status)
        assertNull(store.state.value.shell.playlistDetail.detail)
        assertEquals(
            "Connect to Navidrome to load a playlist.",
            store.state.value.shell.playlistDetail.status,
        )
    }

    @Test
    fun playlistSelectionClearsCompetingAlbumAndArtistPresentation() = runTest {
        val provider = PlaylistBrowseTestProvider()
        val (store, controller) = controller(provider)
        store.updateShell { shell ->
            shell.copy(
                albumDetail = NaviampAlbumDetailScreenUi(
                    selectedAlbum = SharedMediaItemUi("old-album", "Old Album", ""),
                ),
                artistDetail = NaviampArtistDetailScreenUi(
                    selectedArtist = SharedMediaItemUi("old-artist", "Old Artist", ""),
                ),
            )
        }

        controller.execute(
            NaviampCoreCommand.Media.ItemAction(
                SharedMediaItemUi("playlist-a", "Playlist A", "")
                    .playlistActionRequest(NaviampPlaylistMediaCommand.Select),
            ),
        )

        assertNull(store.state.value.shell.albumDetail.selectedAlbum)
        assertNull(store.state.value.shell.artistDetail.selectedArtist)
        assertEquals("playlist-a", store.state.value.shell.playlistDetail.detail?.playlist?.id)
    }

    private fun controller(
        provider: MediaProvider?,
    ): Pair<NaviampCoreStateStore, NaviampCorePlaylistBrowseController> {
        val store = NaviampCoreStateStore()
        val navigation = NaviampCoreNavigationController(
            NaviampNavigationController(),
            store,
            NaviampCoreArtistNavigator { error("Not expected") },
        )
        return store to NaviampCorePlaylistBrowseController(
            stateStore = store,
            providerSource = NaviampCoreMediaProviderSource { provider },
            navigationController = navigation,
            supplementSource = NaviampCorePlaylistBrowseSupplementSource {
                NaviampCorePlaylistBrowseSupplement(
                    recentPlaylistIds = listOf("playlist-b"),
                    keepDownloadedPlaylistIds = setOf("playlist-a"),
                )
            },
        )
    }
}

private class PlaylistBrowseTestProvider(
    private val firstDetailGate: CompletableDeferred<Unit>? = null,
) : MediaProvider {
    override val id = ProviderId("playlists")
    override val displayName = "Playlists"
    override val capabilities = ProviderCapabilities(false, false, false, false, false)

    override suspend fun validateConnection() = ConnectionValidation(null, null)
    override suspend fun recentlyAddedAlbums(limit: Int) = emptyList<Album>()
    override suspend fun album(albumId: AlbumId): AlbumDetails = error("Not used")
    override suspend fun artist(artistId: ArtistId): ArtistDetails = error("Not used")
    override suspend fun artists(limit: Int) = emptyList<Artist>()
    override suspend fun tracks(limit: Int) = emptyList<Track>()
    override suspend fun search(query: String, limit: Int) = MediaSearchResults()
    override suspend fun playlists(limit: Int) = listOf(
        Playlist("playlist-a", "Playlist A", 2),
        Playlist("playlist-b", "Playlist B", 1, isSmart = true),
    )

    override suspend fun playlistTracks(playlistId: String): List<Track> {
        if (playlistId == "playlist-a") firstDetailGate?.await()
        return if (playlistId == "playlist-a") {
            listOf(track("track-1", "Track 1"), track("track-2", "Track 2"))
        } else {
            listOf(track("track-b", "Track B"))
        }
    }

    override suspend fun streamUrl(request: StreamRequest) = "https://stream.example"
    override fun coverArtUrl(coverArtId: String) = "https://art.example/$coverArtId"

    private fun track(id: String, title: String) = Track(
        id = TrackId(id),
        title = title,
        artistName = "Artist",
        albumTitle = "Album",
        durationSeconds = 180,
        coverArtId = "cover",
        audioInfo = null,
        replayGain = null,
    )
}
