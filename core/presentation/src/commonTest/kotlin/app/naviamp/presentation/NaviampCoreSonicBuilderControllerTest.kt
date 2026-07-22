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
import app.naviamp.domain.provider.SonicPathMatch
import app.naviamp.domain.provider.SonicSimilarTrack
import app.naviamp.ui.SharedSonicMixBiasUi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class NaviampCoreSonicBuilderControllerTest {
    @Test
    fun sonicPathOwnsSearchSelectionBuildPlaybackQueueAndSave() = runTest {
        val fixture = fixture()
        val controller = fixture.controller
        controller.dispatch(path(NaviampCoreCommand.SonicPathAction.ChangeStartQuery("start")))
        controller.execute(path(NaviampCoreCommand.SonicPathAction.SearchStart))
        controller.dispatch(
            path(
                NaviampCoreCommand.SonicPathAction.SelectStart(
                    fixture.store.state.value.shell.sonicPathBuilder.startSuggestions.first(),
                ),
            ),
        )
        controller.dispatch(path(NaviampCoreCommand.SonicPathAction.ChangeEndQuery("end")))
        controller.execute(path(NaviampCoreCommand.SonicPathAction.SearchEnd))
        controller.dispatch(
            path(
                NaviampCoreCommand.SonicPathAction.SelectEnd(
                    fixture.store.state.value.shell.sonicPathBuilder.endSuggestions.last(),
                ),
            ),
        )

        controller.execute(path(NaviampCoreCommand.SonicPathAction.Build))
        controller.execute(path(NaviampCoreCommand.SonicPathAction.Play))
        controller.execute(path(NaviampCoreCommand.SonicPathAction.AddToQueue))
        controller.execute(path(NaviampCoreCommand.SonicPathAction.SaveAsPlaylist("My Path")))

        assertEquals(listOf("start", "middle", "end"), fixture.store.state.value.shell.sonicPathBuilder.pathTracks.map { it.id })
        assertEquals(listOf("sonic path:start,middle,end"), fixture.played)
        assertEquals(listOf("sonic path:start,middle,end"), fixture.queued)
        assertEquals(listOf("My Path:start,middle,end"), fixture.provider.created)
        assertEquals("Saved My Path.", fixture.store.state.value.shell.playlists.status)
    }

    @Test
    fun sonicMixOwnsSeedsBiasLengthBuildPlaybackQueueAndSave() = runTest {
        val fixture = fixture()
        val controller = fixture.controller
        controller.dispatch(mix(NaviampCoreCommand.SonicMixAction.ChangeQuery("seed")))
        controller.execute(mix(NaviampCoreCommand.SonicMixAction.Search))
        val suggestions = fixture.store.state.value.shell.sonicMixBuilder.suggestedTracks
        controller.dispatch(mix(NaviampCoreCommand.SonicMixAction.Select(suggestions[0])))
        controller.dispatch(mix(NaviampCoreCommand.SonicMixAction.Select(suggestions[1])))
        controller.dispatch(mix(NaviampCoreCommand.SonicMixAction.ChangeLength(5)))
        controller.dispatch(mix(NaviampCoreCommand.SonicMixAction.ChangeBias(SharedSonicMixBiasUi.Favorites)))
        assertFalse(fixture.store.state.value.shell.sonicMixBuilder.includeSeeds)
        controller.dispatch(mix(NaviampCoreCommand.SonicMixAction.ChangeIncludeSeeds(true)))

        controller.execute(mix(NaviampCoreCommand.SonicMixAction.Build))
        controller.execute(mix(NaviampCoreCommand.SonicMixAction.Play))
        controller.execute(mix(NaviampCoreCommand.SonicMixAction.AddToQueue))
        controller.execute(mix(NaviampCoreCommand.SonicMixAction.SaveAsPlaylist("My Mix")))

        assertEquals(
            listOf("seed-a", "seed-b", "mix-a", "mix-b"),
            fixture.store.state.value.shell.sonicMixBuilder.mixTracks.map { it.id },
        )
        assertEquals("Favorites", fixture.store.state.value.shell.sonicMixBuilder.bias.label)
        assertEquals(listOf("sonic mix:seed-a,seed-b,mix-a,mix-b"), fixture.played)
        assertEquals(listOf("sonic mix:seed-a,seed-b,mix-a,mix-b"), fixture.queued)
        assertEquals("My Mix:seed-a,seed-b,mix-a,mix-b", fixture.provider.created.last())
    }

    @Test
    fun disconnectedAndUnsupportedSonicFeaturesPublishCommonFailures() = runTest {
        val disconnected = fixture(provider = null)
        disconnected.controller.dispatch(path(NaviampCoreCommand.SonicPathAction.ChangeStartQuery("track")))
        disconnected.controller.execute(path(NaviampCoreCommand.SonicPathAction.SearchStart))
        assertEquals(
            "Connect to Navidrome to use Sonic features.",
            disconnected.store.state.value.shell.sonicPathBuilder.status,
        )

        val unsupported = fixture(SonicTestProvider(supportsSonic = false))
        unsupported.controller.dispatch(mix(NaviampCoreCommand.SonicMixAction.ChangeQuery("track")))
        unsupported.controller.execute(mix(NaviampCoreCommand.SonicMixAction.Search))
        assertEquals(
            "The connected server does not support Sonic features.",
            unsupported.store.state.value.shell.sonicMixBuilder.status,
        )
    }

    private fun fixture(provider: SonicTestProvider? = SonicTestProvider()): SonicFixture {
        val store = NaviampCoreStateStore()
        val source = NaviampCoreMediaProviderSource { provider }
        val navigation = NaviampCoreNavigationController(
            NaviampNavigationController(),
            store,
            NaviampCoreArtistNavigator { error("Not expected") },
        )
        val browse = NaviampCorePlaylistBrowseController(store, source, navigation)
        val played = mutableListOf<String>()
        val queued = mutableListOf<String>()
        return SonicFixture(
            store = store,
            provider = provider ?: SonicTestProvider(),
            controller = NaviampCoreSonicBuilderController(
                stateStore = store,
                providerSource = source,
                playlistBrowseController = browse,
                playback = NaviampCoreSonicPlaybackPort { tracks, label ->
                    played += "$label:${tracks.joinToString(",") { it.id.value }}"
                },
                queue = NaviampCoreSonicQueuePort { tracks, label ->
                    queued += "$label:${tracks.joinToString(",") { it.id.value }}"
                },
            ),
            played = played,
            queued = queued,
        )
    }

    private fun path(action: NaviampCoreCommand.SonicPathAction) = NaviampCoreCommand.MixBuilder.SonicPath(action)
    private fun mix(action: NaviampCoreCommand.SonicMixAction) = NaviampCoreCommand.MixBuilder.SonicMix(action)
}

private data class SonicFixture(
    val store: NaviampCoreStateStore,
    val provider: SonicTestProvider,
    val controller: NaviampCoreSonicBuilderController,
    val played: List<String>,
    val queued: List<String>,
)

private class SonicTestProvider(
    supportsSonic: Boolean = true,
) : MediaProvider {
    override val id = ProviderId("sonic")
    override val displayName = "Sonic"
    override val capabilities = ProviderCapabilities(
        false,
        false,
        false,
        false,
        false,
        supportsSonicSimilarity = supportsSonic,
    )
    val created = mutableListOf<String>()
    private val start = sonicTrack("start")
    private val end = sonicTrack("end")

    override suspend fun validateConnection() = ConnectionValidation(null, null)
    override suspend fun recentlyAddedAlbums(limit: Int) = emptyList<Album>()
    override suspend fun album(albumId: AlbumId): AlbumDetails = error("Not used")
    override suspend fun artist(artistId: ArtistId): ArtistDetails = error("Not used")
    override suspend fun artists(limit: Int) = emptyList<Artist>()
    override suspend fun tracks(limit: Int) = emptyList<Track>()
    override suspend fun search(query: String, limit: Int) = when (query) {
        "start" -> MediaSearchResults(tracks = listOf(start, sonicTrack("other-start")))
        "end" -> MediaSearchResults(tracks = listOf(sonicTrack("other-end"), end))
        else -> MediaSearchResults(tracks = listOf(sonicTrack("seed-a"), sonicTrack("seed-b")))
    }

    override suspend fun findSonicPath(startTrackId: TrackId, endTrackId: TrackId, count: Int) =
        listOf(SonicPathMatch(start), SonicPathMatch(sonicTrack("middle")), SonicPathMatch(end))

    override suspend fun sonicSimilarTrackMatches(trackId: TrackId, count: Int) = when (trackId.value) {
        "seed-a" -> listOf(SonicSimilarTrack(sonicTrack("mix-a"), 0.9))
        else -> listOf(SonicSimilarTrack(sonicTrack("mix-b"), 0.8))
    }

    override suspend fun createPlaylist(name: String, trackIds: List<TrackId>): Playlist {
        created += "$name:${trackIds.joinToString(",") { it.value }}"
        return Playlist("created", name, trackIds.size)
    }

    override suspend fun playlists(limit: Int) = emptyList<Playlist>()
    override suspend fun streamUrl(request: StreamRequest) = "https://stream.example"
    override fun coverArtUrl(coverArtId: String) = "https://art.example/$coverArtId"
}

private fun sonicTrack(id: String) = Track(
    id = TrackId(id),
    title = id,
    artistName = "Artist $id",
    albumTitle = "Album $id",
    durationSeconds = 180,
    coverArtId = null,
    audioInfo = null,
    replayGain = null,
)
