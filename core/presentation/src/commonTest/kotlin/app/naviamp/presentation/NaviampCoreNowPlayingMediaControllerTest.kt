package app.naviamp.presentation

import app.naviamp.app.NaviampKeepDownloadedReconciliationApplication
import app.naviamp.app.NaviampKeepDownloadedToggleResult
import app.naviamp.app.NaviampLivePlaybackController
import app.naviamp.app.NaviampLivePlaybackState
import app.naviamp.app.NaviampPlaybackQueueCoordinator
import app.naviamp.app.NaviampRecentRadioStreamController
import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Playlist
import app.naviamp.domain.ProviderId
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.DownloadJobUpdate
import app.naviamp.domain.cache.KeepDownloadedCollectionPolicy
import app.naviamp.domain.media.RelatedTracksSource
import app.naviamp.domain.playback.PlaybackQueueNavigationCommand
import app.naviamp.domain.playback.PlaybackSource
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackVisualizerFrame
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.provider.ProviderCapabilities
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.settings.LyricsDisplayPreference
import app.naviamp.ui.NaviampConnectionSettingsUi
import app.naviamp.ui.NaviampPlaylistChoiceUi
import app.naviamp.ui.NaviampVisualizer
import app.naviamp.ui.NowPlayingCurrentTrackAction
import app.naviamp.ui.NowPlayingCurrentTrackUiActionRequest
import app.naviamp.ui.NowPlayingDisplayAction
import app.naviamp.ui.NowPlayingDisplayActionRequest
import app.naviamp.ui.NowPlayingItemAction
import app.naviamp.ui.NowPlayingItemActionRequest
import app.naviamp.ui.NowPlayingItemTarget
import app.naviamp.ui.NowPlayingSelectionAction
import app.naviamp.ui.NowPlayingSelectionActionRequest
import app.naviamp.ui.SharedRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NaviampCoreNowPlayingMediaControllerTest {
    @Test
    fun presenterPublishesCompleteQueueRelatedAndCapabilityState() = runTest {
        val fixture = mediaFixture(this)
        fixture.presenter.publish()

        val ui = fixture.store.state.value.shell.nowPlaying
        assertEquals("current", ui?.id)
        assertEquals(listOf("queue:0"), ui?.backTo?.map { it.id })
        assertEquals(listOf("queue:2"), ui?.upNext?.map { it.id })
        assertEquals(listOf("related:0"), ui?.related?.map { it.id })
        assertEquals(listOf(0.25f, 0.75f), ui?.visualizerFrame?.bands)
        assertTrue(ui?.canStartRadio == true)
        assertTrue(ui.canFavorite)
        assertTrue(ui.canRate)
    }

    @Test
    fun presenterPublishesCompleteRadioCatalogWithActiveStation() = runTest {
        val fixture = mediaFixture(this)
        val stations = listOf(
            InternetRadioStation("one", "One", "https://one.example"),
            InternetRadioStation("two", "Two", "https://two.example"),
        )
        fixture.live.updateCurrentStation(stations[1])
        val presenter = NaviampCoreNowPlayingPresenter(
            fixture.store,
            { fixture.provider },
            fixture.live,
            NaviampPlaybackQueueCoordinator(fixture.live),
            fixture.effects,
            fixture.sidecars,
            internetRadioStations = { stations },
        )

        presenter.publish()

        val ui = fixture.store.state.value.shell.nowPlaying
        assertEquals(listOf("one", "station", "two"), ui?.radioStations?.map { it.id })
        assertTrue(ui?.isLive == true)
    }

    @Test
    fun displayPlaylistMetadataAndDownloadActionsAreOwnedByCore() = runTest {
        val fixture = mediaFixture(this)

        fixture.controller.execute(displayCommand(NowPlayingDisplayAction.ToggleLyrics))
        fixture.controller.execute(
            NaviampCoreCommand.NowPlaying.Display(
                NowPlayingDisplayActionRequest(
                    NowPlayingDisplayAction.SelectVisualizer,
                    visualizer = NaviampVisualizer.LyricMirrorTunnel,
                ),
            ),
        )
        fixture.controller.execute(
            NaviampCoreCommand.NowPlaying.Display(
                NowPlayingDisplayActionRequest(
                    NowPlayingDisplayAction.SelectLyricsDisplayTiming,
                    lyricsDisplayPreference = LyricsDisplayPreference.LineSynced,
                ),
            ),
        )
        fixture.controller.execute(
            currentCommand(
                NowPlayingCurrentTrackAction.AddToPlaylist,
                playlistChoice = NaviampPlaylistChoiceUi("playlist", "Playlist"),
            ),
        )
        fixture.controller.execute(currentCommand(NowPlayingCurrentTrackAction.ToggleFavorite))
        fixture.controller.execute(currentCommand(NowPlayingCurrentTrackAction.SetRating, rating = 4))
        fixture.controller.execute(currentCommand(NowPlayingCurrentTrackAction.Download))
        advanceUntilIdle()

        assertEquals(listOf("current", "current", "current"), fixture.sidecars.lyricsLoads)
        assertEquals(
            LyricsDisplayPreference.LineSynced,
            fixture.store.state.value.shell.playback.settings.lyricsDisplayPreference,
        )
        assertEquals(listOf(NaviampVisualizer.LyricMirrorTunnel), fixture.visualizers)
        assertEquals(listOf("playlist:current"), fixture.provider.added)
        assertEquals(listOf("current:true"), fixture.provider.favorites)
        assertEquals(listOf("current:4"), fixture.provider.ratings)
        assertTrue(fixture.store.state.value.shell.nowPlaying?.favoriteActive == true)
        assertEquals(4, fixture.store.state.value.shell.nowPlaying?.userRating)
        assertEquals(listOf("current"), fixture.downloads)
    }

    @Test
    fun relatedQueueAndRadioSelectionsUseOneCoreQueuePolicy() = runTest {
        val fixture = mediaFixture(this)
        fixture.controller.execute(currentCommand(NowPlayingCurrentTrackAction.StartRadio))
        assertEquals("current", fixture.live.state.value.queue.current?.id?.value)
        assertTrue(fixture.live.state.value.queue.tracks.any { it.id.value == "radio" })
        assertTrue(fixture.effects.selections.isEmpty())
        assertEquals("Playing track radio.", fixture.store.state.value.overlays.status)
        val recentSection = fixture.store.state.value.shell.home.content.collectionSections
            .single { it.id == app.naviamp.domain.settings.HomeSectionIds.RecentRadio }
        assertTrue(recentSection.items.single().mediaItem.id.startsWith("track:current:session:"))
        assertEquals("2 tracks", recentSection.items.single().mediaItem.subtitle)

        fixture.live.replace(
            fixture.live.state.value.copy(
                currentTrack = nowPlayingTrack("current"),
                queue = PlaybackQueue(
                    listOf(nowPlayingTrack("past"), nowPlayingTrack("current"), nowPlayingTrack("next")),
                    1,
                ),
            ),
        )
        fixture.presenter.publish()
        val related = fixture.store.state.value.shell.nowPlaying!!.related.single()

        fixture.controller.execute(
            NaviampCoreCommand.NowPlaying.Selection(
                NowPlayingSelectionActionRequest(related, NowPlayingSelectionAction.SelectRelatedItem),
            ),
        )
        assertEquals("related", fixture.live.state.value.currentTrack?.id?.value)
        assertEquals(listOf("related:0"), fixture.effects.selections)

        fixture.controller.execute(
            NaviampCoreCommand.NowPlaying.QueueItem(
                NowPlayingItemActionRequest(
                    item = related,
                    target = NowPlayingItemTarget.RelatedIndex(0),
                    action = NowPlayingItemAction.PlayNext,
                ),
            ),
        )
        assertTrue(fixture.live.state.value.queue.upNext().any { it.id.value == "related" })

    }

    @Test
    fun currentTrackArtistAndAlbumLinksCloseNowPlayingAndOpenSharedDetails() = runTest {
        val albumFixture = mediaFixture(this)
        albumFixture.store.updateShell { shell ->
            shell.copy(shellChrome = shell.shellChrome.copy(nowPlayingOpen = true))
        }
        albumFixture.controller.execute(currentCommand(NowPlayingCurrentTrackAction.GoToAlbum))
        assertEquals(SharedRoute.Home, albumFixture.store.state.value.shell.shellChrome.selectedRoute)
        assertFalse(albumFixture.store.state.value.shell.shellChrome.nowPlayingOpen)

        val artistFixture = mediaFixture(this)
        artistFixture.store.updateShell { shell ->
            shell.copy(shellChrome = shell.shellChrome.copy(nowPlayingOpen = true))
        }
        artistFixture.controller.execute(currentCommand(NowPlayingCurrentTrackAction.GoToArtist))
        assertEquals(SharedRoute.Home, artistFixture.store.state.value.shell.shellChrome.selectedRoute)
        assertFalse(artistFixture.store.state.value.shell.shellChrome.nowPlayingOpen)
    }

    @Test
    fun individualArtistNameResolvesBeforeOpeningSharedArtistDetails() = runTest {
        val fixture = mediaFixture(this)
        fixture.live.updateCurrentTrack(
            nowPlayingTrack("current").copy(
                artistId = ArtistId("combined"),
                artistName = "HUGEL, David Guetta, Kehlani, Daecolm",
            ),
        )
        fixture.presenter.publish()

        fixture.controller.execute(
            currentCommand(
                action = NowPlayingCurrentTrackAction.GoToArtist,
                artistName = "David Guetta",
            ),
        )

        assertEquals("david-guetta", fixture.store.state.value.shell.artistDetail.selectedArtist?.id)
        assertEquals("David Guetta", fixture.store.state.value.shell.artistDetail.selectedArtist?.title)
    }

    @Test
    fun nameOnlyCreditOpensVirtualArtistCatalogInsteadOfDoingNothing() = runTest {
        val fixture = mediaFixture(this)
        fixture.live.updateCurrentTrack(
            nowPlayingTrack("current").copy(
                artistId = ArtistId("combined"),
                artistName = "HUGEL, David Guetta, Kehlani, Daecolm",
            ),
        )
        fixture.presenter.publish()

        fixture.controller.execute(
            currentCommand(
                action = NowPlayingCurrentTrackAction.GoToArtist,
                artistName = "HUGEL",
            ),
        )

        val detail = assertNotNull(fixture.store.state.value.shell.artistDetail.detail)
        assertEquals("HUGEL", detail.artist.title)
        assertTrue(detail.albums.isNotEmpty())
        assertFalse(detail.artist.canFavorite)
    }

    @Test
    fun staleTargetsAndMissingPayloadsNeverBecomeSilentActions() = runTest {
        val fixture = mediaFixture(this)
        fixture.controller.execute(
            NaviampCoreCommand.NowPlaying.Selection(
                NowPlayingSelectionActionRequest(
                    app.naviamp.ui.NaviampNowPlayingItemUi("queue:99", "Missing", ""),
                    NowPlayingSelectionAction.SelectQueueItem,
                ),
            ),
        )
        assertEquals("Queue item is no longer available.", fixture.store.state.value.overlays.status)

        fixture.controller.execute(currentCommand(NowPlayingCurrentTrackAction.CreatePlaylistAndAdd))
        assertEquals("Playlist name cannot be blank.", fixture.store.state.value.overlays.status)
    }
}

private data class MediaFixture(
    val store: NaviampCoreStateStore,
    val provider: NowPlayingTestProvider,
    val live: NaviampLivePlaybackController,
    val effects: NowPlayingTestEffects,
    val sidecars: NowPlayingTestSidecars,
    val presenter: NaviampCoreNowPlayingPresenter,
    val controller: NaviampCoreNowPlayingMediaController,
    val visualizers: List<NaviampVisualizer>,
    val downloads: List<String>,
)

private fun mediaFixture(scope: kotlinx.coroutines.CoroutineScope): MediaFixture {
    val provider = NowPlayingTestProvider()
    val store = NaviampCoreStateStore()
    store.updateShell { shell ->
        shell.copy(
            connectionSettings = NaviampConnectionSettingsUi(currentSourceId = "source"),
            playlistChoices = listOf(NaviampPlaylistChoiceUi("playlist", "Playlist")),
        )
    }
    val tracks = listOf(nowPlayingTrack("past"), nowPlayingTrack("current"), nowPlayingTrack("next"))
    val live = NaviampLivePlaybackController(
        NaviampLivePlaybackState(
            currentTrack = tracks[1],
            queue = PlaybackQueue(tracks, 1),
            playbackState = PlaybackState.Playing,
        ),
    )
    val queue = NaviampPlaybackQueueCoordinator(live)
    val effects = NowPlayingTestEffects()
    val sidecars = NowPlayingTestSidecars()
    val presenter = NaviampCoreNowPlayingPresenter(store, { provider }, live, queue, effects, sidecars)
    val settings = NaviampCorePlaybackSettingsPort { updated, _ ->
        store.updateShell { shell -> shell.copy(playback = shell.playback.copy(settings = updated)) }
        updated
    }
    val transport = NaviampCorePlaybackController(
        scope,
        store,
        { provider },
        live,
        queue,
        effects,
        settings,
        sidecars,
        emptyPlaybackSessions(),
        presenter,
        nowEpochMillis = { 1_000L },
    )
    val visualizers = mutableListOf<NaviampVisualizer>()
    val navigation = NaviampCoreNavigationController(
        app.naviamp.app.NaviampNavigationController(),
        store,
        NaviampCoreArtistNavigator { error("Not expected") },
    )
    val mediaDetails = NaviampCoreMediaDetailController(store, { provider }, navigation, scope)
    val playedStations = mutableListOf<String>()
    val radio = NaviampCoreInternetRadioController(
        store,
        { provider },
        NaviampCoreInternetRadioPlaybackPort { station -> playedStations += station.id },
        object : NaviampCoreInternetRadioRecentsPort {
            override fun current() = emptyList<InternetRadioStation>()
            override suspend fun record(station: InternetRadioStation) = listOf(station)
        },
    )
    val downloaded = mutableListOf<String>()
    val downloads = NaviampCoreDownloadsController(
        scope,
        store,
        { provider },
        object : NaviampCoreDownloadStoragePort {
            override suspend fun snapshot(sourceId: String) = NaviampCoreDownloadStorageSnapshot()
            override suspend fun pruneMissing(sourceId: String) = 0
            override suspend fun remove(sourceId: String, track: Track) = Unit
            override suspend fun deleteAll(sourceId: String) = 0
        },
        NaviampCoreDownloadTransferPort { request, _, update ->
            downloaded += request.tracks.map { it.id.value }
            update(DownloadJobUpdate.Completed)
            NaviampCoreDownloadTransferResult(false)
        },
        object : NaviampCoreKeepDownloadedPort {
            override fun policies(sourceId: String) = emptyList<KeepDownloadedCollectionPolicy>()
            override fun toggle(policy: KeepDownloadedCollectionPolicy) = NaviampKeepDownloadedToggleResult.Enable
            override fun reconcile(policy: KeepDownloadedCollectionPolicy, tracks: List<Track>) =
                NaviampKeepDownloadedReconciliationApplication(emptyList(), null, null, false)
        },
        NaviampCoreDownloadedPlaybackPort { _, _ -> },
    )
    var recentRadioStreams = emptyList<app.naviamp.domain.settings.RecentRadioStream>()
    val generatedRadio = NaviampCoreMediaTransactions(
        stateStore = store,
        busyIndicator = NaviampCoreBusyIndicator(store),
        providerSource = { provider },
        registry = NaviampCoreMediaRegistry(),
        playback = live,
        queue = queue,
        effects = effects,
        queuePlayback = NaviampCoreQueuePlaybackController(live, queue, effects, { presenter.publish() }, {}),
        downloads = downloads,
        mediaDetails = mediaDetails,
        recentRadioStreams = NaviampRecentRadioStreamController(
            load = { recentRadioStreams },
            save = { recentRadioStreams = it },
        ),
        externalUri = NaviampCoreExternalUriPort {},
        favoritedAtIso8601 = { "now" },
        publishNowPlaying = { presenter.publish() },
        openNowPlaying = {},
    )
    val controller = NaviampCoreNowPlayingMediaController(
        stateStore = store,
        providerSource = { provider },
        playback = live,
        queue = queue,
        effects = effects,
        presenter = presenter,
        playbackController = transport,
        settings = settings,
        visualizerSettings = object : NaviampCoreVisualizerSettingsPort {
            override fun save(visualizer: NaviampVisualizer) { visualizers += visualizer }
        },
        sidecars = sidecars,
        downloads = downloads,
        mediaDetails = mediaDetails,
        navigation = navigation,
        radio = radio,
        generatedRadio = generatedRadio,
        favoritedAtIso8601 = { "now" },
    )
    presenter.publish()
    return MediaFixture(store, provider, live, effects, sidecars, presenter, controller, visualizers, downloaded)
}

private class NowPlayingTestEffects : NaviampCorePlaybackEffectPort {
    override val capabilities = NaviampCorePlaybackCapabilities(supportsVisualizer = true)
    override val playbackSource = PlaybackSource.ProviderStream
    val selections = mutableListOf<String>()
    override fun pause() = Unit
    override fun resume() = Unit
    override fun startOrRestore() = true
    override fun seek(positionSeconds: Double) = Unit
    override fun replayCurrent(positionSeconds: Double) = Unit
    override fun setVolume(percent: Int) = Unit
    override fun stop() = Unit
    override fun applyQueue(queue: PlaybackQueue, clearPreparedNext: Boolean) = Unit
    override fun applyNavigation(command: PlaybackQueueNavigationCommand) = Unit
    override fun applyRepeatMode(mode: RepeatMode) = Unit
    override fun playQueueSelection(queue: PlaybackQueue, index: Int) {
        selections += "${queue.tracks[index].id.value}:$index"
    }
}

private class NowPlayingTestSidecars : NaviampCoreNowPlayingSidecarPort {
    val lyricsLoads = mutableListOf<String>()
    override fun snapshot() = NaviampCoreNowPlayingSidecars(
        visualizerFrame = PlaybackVisualizerFrame(listOf(0.25f, 0.75f), 1L),
        relatedTracks = listOf(nowPlayingTrack("related")),
        relatedTracksSource = RelatedTracksSource.ProviderRadio,
        internetRadioStations = listOf(InternetRadioStation("station", "Station", "https://radio")),
    )
    override suspend fun loadForTrack(track: Track) = Unit
    override suspend fun loadLyrics(track: Track) { lyricsLoads += track.id.value }
    override suspend fun changeLyricsOffset(track: Track, offsetMillis: Int) = Unit
}

private class NowPlayingTestProvider : MediaProvider {
    override val id = ProviderId("now-playing")
    override val displayName = "Now Playing"
    override val capabilities = ProviderCapabilities(
        false, false, true, true, true,
        supportsTrackFavorites = true,
        supportsTrackRatings = true,
    )
    val added = mutableListOf<String>()
    val favorites = mutableListOf<String>()
    val ratings = mutableListOf<String>()
    override suspend fun validateConnection() = ConnectionValidation(null, null)
    override suspend fun recentlyAddedAlbums(limit: Int) = emptyList<Album>()
    override suspend fun album(albumId: AlbumId) = AlbumDetails(
        Album(
            id = albumId,
            title = "Album",
            artistName = "Artist",
            coverArtId = albumId.value,
            recentlyAddedAtIso8601 = null,
            releaseYear = 2026,
        ),
        listOf(nowPlayingTrack("album-track")),
    )
    override suspend fun artist(artistId: ArtistId) = ArtistDetails(
        Artist(artistId, if (artistId.value == "david-guetta") "David Guetta" else "Artist"),
        emptyList(),
    )
    override suspend fun artists(limit: Int) = emptyList<Artist>()
    override suspend fun tracks(limit: Int) = emptyList<Track>()
    override suspend fun search(query: String, limit: Int) = when {
        query.equals("David Guetta", ignoreCase = true) -> MediaSearchResults(
            artists = listOf(Artist(ArtistId("david-guetta"), "David Guetta")),
        )
        query.equals("HUGEL", ignoreCase = true) -> MediaSearchResults(
            tracks = listOf(
                nowPlayingTrack("hugel-credit").copy(
                    artistId = ArtistId("combined"),
                    artistName = "HUGEL, David Guetta, Kehlani, Daecolm",
                ),
            ),
        )
        else -> MediaSearchResults()
    }
    override suspend fun trackRadio(trackId: TrackId, count: Int) = listOf(nowPlayingTrack("radio"))
    override suspend fun internetRadioStations() = listOf(InternetRadioStation("station", "Station", "https://radio"))
    override suspend fun addTracksToPlaylist(playlistId: String, trackIds: List<TrackId>) {
        added += "$playlistId:${trackIds.joinToString(",") { it.value }}"
    }
    override suspend fun createPlaylist(name: String, trackIds: List<TrackId>) = Playlist("created", name, trackIds.size)
    override suspend fun setTrackFavorite(trackId: TrackId, favorite: Boolean) { favorites += "${trackId.value}:$favorite" }
    override suspend fun setTrackRating(trackId: TrackId, rating: Int?) { ratings += "${trackId.value}:$rating" }
    override suspend fun streamUrl(request: StreamRequest) = "https://stream.example"
    override fun coverArtUrl(coverArtId: String) = "https://art.example/$coverArtId"
}

private fun displayCommand(action: NowPlayingDisplayAction) =
    NaviampCoreCommand.NowPlaying.Display(NowPlayingDisplayActionRequest(action))

private fun currentCommand(
    action: NowPlayingCurrentTrackAction,
    playlistChoice: NaviampPlaylistChoiceUi? = null,
    playlistName: String? = null,
    rating: Int? = null,
    artistId: String? = null,
    artistName: String? = null,
) = NaviampCoreCommand.NowPlaying.CurrentTrack(
    NowPlayingCurrentTrackUiActionRequest(action, playlistChoice, playlistName, rating, artistId, artistName),
)

private fun nowPlayingTrack(id: String) = Track(
    id = TrackId(id),
    title = id,
    artistId = ArtistId("artist"),
    artistName = "Artist",
    albumId = AlbumId("album"),
    albumTitle = "Album",
    durationSeconds = 180,
    coverArtId = id,
    audioInfo = null,
    replayGain = null,
)
