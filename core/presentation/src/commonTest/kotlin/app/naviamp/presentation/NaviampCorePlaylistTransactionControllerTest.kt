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
import app.naviamp.domain.smartplaylist.SmartPlaylistCondition
import app.naviamp.domain.smartplaylist.SmartPlaylistDefinition
import app.naviamp.domain.smartplaylist.SmartPlaylistOperator
import app.naviamp.domain.smartplaylist.SmartPlaylistValue
import app.naviamp.ui.NaviampPlaylistChoiceUi
import app.naviamp.ui.NaviampPlaylistDetailActionRequest
import app.naviamp.ui.NaviampPlaylistDetailCommand
import app.naviamp.ui.NaviampPlaylistMediaCommand
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.SharedTrackRowUi
import app.naviamp.ui.playlistActionRequest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NaviampCorePlaylistTransactionControllerTest {
    @Test
    fun playbackQueueAndDownloadReceiveResolvedDomainTransactions() = runTest {
        val fixture = fixture()
        val item = playlistItem("playlist-a", "Playlist A")

        fixture.controller.execute(detail(item, NaviampPlaylistDetailCommand.Play(shuffle = true)))
        fixture.controller.execute(detail(item, NaviampPlaylistDetailCommand.AddToQueue))
        fixture.controller.execute(detail(item, NaviampPlaylistDetailCommand.Download("lossless")))

        assertEquals(listOf("playlist-a:true"), fixture.effects.plays)
        assertEquals(listOf("track-1", "track-2", "track-2"), fixture.effects.lastTracks.map { it.id.value })
        assertEquals(listOf("playlist-a"), fixture.effects.queued)
        assertEquals(listOf("playlist-a:lossless"), fixture.effects.downloads)
        assertEquals(listOf("playlist-a"), fixture.store.state.value.shell.playlists.recentPlaylistIds)
        assertTrue(fixture.store.state.value.shell.shellChrome.nowPlayingOpen)
        assertEquals("Connected.", fixture.store.state.value.shell.playlistDetail.status)
    }

    @Test
    fun providerMutationsAreResolvedAndPublishedByCore() = runTest {
        val fixture = fixture()
        fixture.browse.execute(NaviampCoreCommand.Playlists.Refresh)
        val item = playlistItem("playlist-a", "Playlist A")

        fixture.controller.execute(
            detail(
                item,
                NaviampPlaylistDetailCommand.AddToPlaylist(NaviampPlaylistChoiceUi("playlist-b", "Playlist B")),
            ),
        )
        fixture.controller.execute(detail(item, NaviampPlaylistDetailCommand.CreatePlaylistAndAdd("Created")))
        fixture.controller.execute(detail(item, NaviampPlaylistDetailCommand.Copy("Copy", deduplicate = true)))
        fixture.controller.execute(
            NaviampCoreCommand.Playlists.UpdateTracks(
                item,
                listOf(trackRow("track-2"), trackRow("track-1")),
            ),
        )

        assertEquals(listOf("track-1", "track-2", "track-2"), fixture.provider.addedTrackIds.map(TrackId::value))
        assertEquals(listOf("track-1", "track-2"), fixture.provider.created.last().second.map(TrackId::value))
        assertEquals(listOf("track-2", "track-1"), fixture.provider.replacementTrackIds.map(TrackId::value))
        assertEquals("Updated playlist.", fixture.store.state.value.shell.playlists.status)
    }

    @Test
    fun renameAndDeleteKeepCoreSelectionConsistent() = runTest {
        val fixture = fixture()
        fixture.browse.execute(NaviampCoreCommand.Playlists.Refresh)
        val item = playlistItem("playlist-a", "Playlist A")
        fixture.browse.execute(
            NaviampCoreCommand.Media.ItemAction(item.playlistActionRequest(NaviampPlaylistMediaCommand.Select)),
        )

        fixture.controller.execute(detail(item, NaviampPlaylistDetailCommand.Rename("Renamed")))
        assertEquals("Renamed", fixture.store.state.value.shell.playlistDetail.selectedPlaylist?.title)
        fixture.controller.execute(
            detail(playlistItem("playlist-a", "Renamed"), NaviampPlaylistDetailCommand.Delete),
        )

        assertEquals(listOf("playlist-a"), fixture.provider.deleted)
        assertNull(fixture.store.state.value.shell.playlistDetail.selectedPlaylist)
        assertEquals("Deleted playlist.", fixture.store.state.value.shell.playlists.status)
    }

    @Test
    fun smartPlaylistAuthenticationAndDefinitionsFlowThroughOneCoreController() = runTest {
        val fixture = fixture()
        fixture.browse.execute(NaviampCoreCommand.Playlists.Refresh)
        val definition = smartDefinition("Smart Mix")
        val item = playlistItem("smart", "Smart", smart = true)

        fixture.controller.execute(NaviampCoreCommand.SmartPlaylist.Save(definition, "secret"))
        fixture.controller.execute(NaviampCoreCommand.SmartPlaylist.Update(item, definition, "secret-2"))
        val result = fixture.controller.execute(NaviampCoreCommand.SmartPlaylist.Load(item, "secret-3"))

        assertEquals(listOf("secret", "secret-2", "secret-3"), fixture.smartPasswords)
        assertEquals(listOf("Smart Mix"), fixture.provider.smartCreates)
        assertEquals(listOf("smart:Smart Mix"), fixture.provider.smartUpdates)
        assertEquals(NaviampCoreCommandResult.SmartPlaylistLoaded(definition), result)
        assertNull(fixture.store.state.value.shell.playlists.status)
    }

    @Test
    fun disconnectedTransactionsProduceSharedFailureStateWithoutCallingEffects() = runTest {
        val fixture = fixture(provider = null)

        fixture.controller.execute(
            detail(playlistItem("playlist-a", "Playlist A"), NaviampPlaylistDetailCommand.Play(false)),
        )

        assertTrue(fixture.effects.plays.isEmpty())
        assertEquals(
            "Connect to Navidrome to use playlists.",
            fixture.store.state.value.shell.playlistDetail.status,
        )
    }

    private fun fixture(provider: TransactionTestProvider? = TransactionTestProvider()): TransactionFixture {
        val store = NaviampCoreStateStore()
        val navigation = NaviampCoreNavigationController(
            NaviampNavigationController(),
            store,
            NaviampCoreArtistNavigator { error("Not expected") },
        )
        val source = NaviampCoreMediaProviderSource { provider }
        val browse = NaviampCorePlaylistBrowseController(store, source, navigation)
        val effects = TransactionTestEffects()
        val passwords = mutableListOf<String?>()
        val controller = NaviampCorePlaylistTransactionController(
            stateStore = store,
            providerSource = source,
            browseController = browse,
            playback = effects,
            queue = effects,
            downloads = effects,
            smartProviderSource = NaviampCoreSmartPlaylistProviderSource { password ->
                passwords += password
                provider
            },
            openNowPlaying = navigation::openNowPlaying,
        )
        return TransactionFixture(store, provider ?: TransactionTestProvider(), browse, controller, effects, passwords)
    }

    private fun detail(item: SharedMediaItemUi, command: NaviampPlaylistDetailCommand) =
        NaviampCoreCommand.Playlists.Detail(NaviampPlaylistDetailActionRequest(item, command))

    private fun playlistItem(id: String, title: String, smart: Boolean = false) =
        SharedMediaItemUi(id, title, "", isSmartPlaylist = smart)

    private fun trackRow(id: String) = SharedTrackRowUi(id = id, title = id, subtitle = "")

    private fun smartDefinition(name: String) = SmartPlaylistDefinition(
        name = name,
        rules = listOf(
            SmartPlaylistCondition(
                operator = SmartPlaylistOperator.Contains,
                field = "genre",
                value = SmartPlaylistValue.Text("ambient"),
            ),
        ),
    )
}

private data class TransactionFixture(
    val store: NaviampCoreStateStore,
    val provider: TransactionTestProvider,
    val browse: NaviampCorePlaylistBrowseController,
    val controller: NaviampCorePlaylistTransactionController,
    val effects: TransactionTestEffects,
    val smartPasswords: List<String?>,
)

private class TransactionTestEffects :
    NaviampCorePlaylistPlaybackPort,
    NaviampCorePlaylistQueuePort,
    NaviampCorePlaylistDownloadPort {
    val plays = mutableListOf<String>()
    val queued = mutableListOf<String>()
    val downloads = mutableListOf<String>()
    var lastTracks = emptyList<Track>()

    override suspend fun play(playlist: Playlist, tracks: List<Track>, shuffle: Boolean) {
        plays += "${playlist.id}:$shuffle"
        lastTracks = tracks
    }

    override suspend fun addToQueue(playlist: Playlist, tracks: List<Track>) {
        queued += playlist.id
        lastTracks = tracks
    }

    override suspend fun download(playlist: Playlist, tracks: List<Track>, option: String?) {
        downloads += "${playlist.id}:$option"
        lastTracks = tracks
    }
}

private class TransactionTestProvider : MediaProvider {
    override val id = ProviderId("transactions")
    override val displayName = "Transactions"
    override val capabilities = ProviderCapabilities(
        false,
        false,
        false,
        false,
        false,
        supportsSmartPlaylists = true,
    )
    private val smartDefinition = SmartPlaylistDefinition(
        name = "Smart Mix",
        rules = listOf(
            SmartPlaylistCondition(
                SmartPlaylistOperator.Contains,
                "genre",
                SmartPlaylistValue.Text("ambient"),
            ),
        ),
    )
    private val playlistItems = mutableListOf(
        Playlist("playlist-a", "Playlist A", 3),
        Playlist("playlist-b", "Playlist B", 0),
        Playlist("smart", "Smart", 1, isSmart = true),
    )
    val addedTrackIds = mutableListOf<TrackId>()
    val created = mutableListOf<Pair<String, List<TrackId>>>()
    var replacementTrackIds = emptyList<TrackId>()
    val deleted = mutableListOf<String>()
    val smartCreates = mutableListOf<String>()
    val smartUpdates = mutableListOf<String>()

    override suspend fun validateConnection() = ConnectionValidation(null, null)
    override suspend fun recentlyAddedAlbums(limit: Int) = emptyList<Album>()
    override suspend fun album(albumId: AlbumId): AlbumDetails = error("Not used")
    override suspend fun artist(artistId: ArtistId): ArtistDetails = error("Not used")
    override suspend fun artists(limit: Int) = emptyList<Artist>()
    override suspend fun tracks(limit: Int) = emptyList<Track>()
    override suspend fun search(query: String, limit: Int) = MediaSearchResults()
    override suspend fun playlists(limit: Int) = playlistItems.toList()
    override suspend fun playlistTracks(playlistId: String) = when (playlistId) {
        "playlist-a" -> listOf(track("track-1"), track("track-2"), track("track-2"))
        "smart" -> listOf(track("smart-track"))
        else -> emptyList()
    }

    override suspend fun addTracksToPlaylist(playlistId: String, trackIds: List<TrackId>) {
        addedTrackIds += trackIds
    }

    override suspend fun createPlaylist(name: String, trackIds: List<TrackId>): Playlist {
        created += name to trackIds
        return Playlist("created-${created.size}", name, trackIds.size).also(playlistItems::add)
    }

    override suspend fun replacePlaylistTracks(playlistId: String, currentTrackCount: Int, trackIds: List<TrackId>) {
        replacementTrackIds = trackIds
    }

    override suspend fun renamePlaylist(playlistId: String, name: String) {
        val index = playlistItems.indexOfFirst { it.id == playlistId }
        playlistItems[index] = playlistItems[index].copy(name = name)
    }

    override suspend fun deletePlaylist(playlistId: String) {
        deleted += playlistId
        playlistItems.removeAll { it.id == playlistId }
    }

    override suspend fun createSmartPlaylist(definition: SmartPlaylistDefinition): Playlist {
        smartCreates += definition.name
        return Playlist("smart-created", definition.name, 0, isSmart = true).also(playlistItems::add)
    }

    override suspend fun updateSmartPlaylist(playlistId: String, definition: SmartPlaylistDefinition) {
        smartUpdates += "$playlistId:${definition.name}"
    }

    override suspend fun smartPlaylistDefinition(playlistId: String) = smartDefinition
    override suspend fun streamUrl(request: StreamRequest) = "https://stream.example"
    override fun coverArtUrl(coverArtId: String) = "https://art.example/$coverArtId"

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
}
