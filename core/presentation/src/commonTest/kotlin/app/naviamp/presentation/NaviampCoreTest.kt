package app.naviamp.presentation

import app.naviamp.app.NaviampKeepDownloadedReconciliationApplication
import app.naviamp.app.NaviampKeepDownloadedToggleResult
import app.naviamp.app.NaviampConnectionPhase
import app.naviamp.app.NaviampConnectionRuntimeState
import app.naviamp.app.NaviampLivePlaybackState
import app.naviamp.app.NaviampPlaybackSessionController
import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.Genre
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Track
import app.naviamp.domain.cache.DownloadJobUpdate
import app.naviamp.domain.cache.KeepDownloadedCollectionPolicy
import app.naviamp.domain.cache.PlaybackSessionRepository
import app.naviamp.domain.home.HomeDate
import app.naviamp.domain.playback.PlaybackQueueNavigationCommand
import app.naviamp.domain.playback.PlaybackSource
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.app.NaviampNavigationState
import app.naviamp.domain.app.NaviampRoute
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.domain.settings.PlaybackSessionSettings
import app.naviamp.ui.NaviampSettingsSyncUi
import app.naviamp.ui.NaviampAppShellUiState
import app.naviamp.ui.NaviampSearchScreenUi
import app.naviamp.ui.NaviampAlbumDetailActionRequest
import app.naviamp.ui.NaviampAlbumDetailCommand
import app.naviamp.ui.NaviampArtistAlbumActionRequest
import app.naviamp.ui.NaviampArtistAlbumCommand
import app.naviamp.ui.NaviampArtistDetailActionRequest
import app.naviamp.ui.NaviampArtistDetailCommand
import app.naviamp.ui.NaviampMediaItemActionRequest
import app.naviamp.ui.NaviampMediaItemCommand
import app.naviamp.ui.NaviampArtistMediaCommand
import app.naviamp.ui.NaviampVisualizer
import app.naviamp.ui.SharedHomeDiscoveryTrackActionRequest
import app.naviamp.ui.SharedHomeStationUi
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.SharedRoute
import app.naviamp.ui.SharedTrackRowAction
import app.naviamp.ui.SharedTrackRowActionRequest
import app.naviamp.ui.SharedTrackRowUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NaviampCoreTest {
    @Test
    fun constructsOneProductGraphAndRoutesActionsWithoutHostControllers() = runTest {
        val failures = mutableListOf<Throwable>()
        val core = NaviampCore.create(
            scope = this,
            services = fakeCoreServices(),
            onAsyncFailure = { _, cause -> failures += cause },
        )

        core.actions.shell.navigationActions.onRouteSelected(SharedRoute.Library)
        core.actions.shell.searchActions.onQueryChanged("ambient")
        core.actions.settingsSync.onExport()
        advanceUntilIdle()

        assertEquals(SharedRoute.Library, core.state.value.shell.shellChrome.selectedRoute)
        assertEquals("ambient", core.state.value.shell.search.query)
        assertEquals("Settings exported to settings.json.", core.state.value.settingsSync.status)
        assertNotNull(core.state.value.shell.nowPlaying)
        assertTrue(failures.isEmpty())
    }

    @Test
    fun nativePlaybackObservationsEnterThroughTheCoreFacade() = runTest {
        val core = NaviampCore.create(this, fakeCoreServices())

        core.updateLivePlayback { state ->
            state.copy(playbackState = app.naviamp.domain.playback.PlaybackState.Playing)
        }
        core.onTrackChanged(coreTrack("observed"))

        assertEquals("observed", core.state.value.shell.nowPlaying?.id)
        assertEquals("Playing", core.state.value.shell.nowPlaying?.stateLabel)
    }

    @Test
    fun selectingProviderContentStartsPlaybackAndOpensNowPlayingInCore() = runTest {
        val provider = FakeCoreMediaProvider()
        val core = NaviampCore.create(this, fakeCoreServices(provider))
        core.dispatch(NaviampCoreCommand.Search.ChangeQuery("core"))
        core.execute(NaviampCoreCommand.Search.Submit)
        val track = core.state.value.shell.search.results.tracks.single()

        core.execute(
            NaviampCoreCommand.Media.TrackAction(
                SharedTrackRowActionRequest(track, SharedTrackRowAction.Select),
            ),
        )

        assertTrue(core.state.value.shell.shellChrome.nowPlayingOpen)
        assertEquals(provider.track.id.value, core.state.value.shell.nowPlaying?.id)
    }

    @Test
    fun successfulConnectionPopulatesProviderBackedScreensWithoutHostRefreshCommands() = runTest {
        val provider = FakeCoreMediaProvider()
        val record = NaviampCoreSavedConnectionRecord(
            id = "source-1",
            displayName = "Home Music",
            serverUrl = "https://music.example",
            username = "demo",
        )
        val services = fakeCoreServices(provider).copy(
            connection = object : NaviampCoreProviderSessionPort {
                override suspend fun connect(
                    request: NaviampCoreConnectionRequest,
                    plan: app.naviamp.app.NaviampConnectionAttemptPlan,
                ) = NaviampCoreConnectedSession(
                    sourceId = record.id,
                    displayName = record.displayName,
                    inventory = NaviampCoreConnectionInventory(listOf(record), record.id),
                )

                override suspend fun editableConnection(id: String) = error("Not used")
                override suspend fun deleteConnection(id: String) = NaviampCoreConnectionInventory()
            },
        )
        val core = NaviampCore.create(this, services)
        core.dispatch(
            NaviampCoreCommand.Connection.ChangeForm(
                ConnectionFormState(
                    serverUrl = record.serverUrl,
                    username = record.username,
                    password = "secret",
                ),
            ),
        )

        core.execute(NaviampCoreCommand.Connection.Connect)
        advanceUntilIdle()

        assertEquals(listOf(provider.playlist.name), core.state.value.shell.home.content.playlists.map { it.title })
        assertEquals(listOf(provider.artist.name), core.state.value.shell.library.artists.map { it.title })
        assertEquals(listOf(provider.playlist.name), core.state.value.shell.playlists.playlists.map { it.title })
    }

    @Test
    fun restoredHostNeutralStateRehydratesNavigationConnectionPlaybackAndProductState() = runTest {
        val restoredTrack = coreTrack("restored")
        val core = NaviampCore.create(
            scope = this,
            services = fakeCoreServices(),
            initialState = NaviampCoreInitialState(
                product = NaviampCoreState(
                    shell = NaviampAppShellUiState(
                        search = NaviampSearchScreenUi(query = "restored query"),
                    ),
                ),
                navigation = NaviampNavigationState(
                    route = NaviampRoute.Search,
                    lastContentRoute = NaviampRoute.Search,
                ),
                playback = NaviampLivePlaybackState(currentTrack = restoredTrack),
                connection = NaviampConnectionRuntimeState(
                    phase = NaviampConnectionPhase.Connected,
                    sourceId = "restored-source",
                    status = "Restored.",
                ),
            ),
        )

        assertEquals(SharedRoute.Search, core.state.value.shell.shellChrome.selectedRoute)
        assertEquals("restored query", core.state.value.shell.search.query)
        assertEquals("restored-source", core.state.value.shell.connectionSettings.currentSourceId)
        assertTrue(core.state.value.shell.connectionSettings.connection.connected)
        assertEquals(restoredTrack.id.value, core.state.value.shell.nowPlaying?.id)
    }

    @Test
    fun composedCoreOwnsEveryPreviouslyMissingMediaActionFamily() = runTest {
        val core = NaviampCore.create(this, fakeCoreServices())
        val item = SharedMediaItemUi("missing", "Missing", "")
        val track = SharedTrackRowUi("missing", "Missing", "Artist")
        val row = SharedTrackRowActionRequest(track, SharedTrackRowAction.Select)
        val commands = listOf(
            NaviampCoreCommand.Home.SelectStation(SharedHomeStationUi("library", "Library", "")),
            NaviampCoreCommand.Home.SonicTrackAction(
                SharedHomeDiscoveryTrackActionRequest("row", track, SharedTrackRowAction.Select),
            ),
            NaviampCoreCommand.Home.RecentTrackAction(row),
            NaviampCoreCommand.Media.TrackAction(row),
            NaviampCoreCommand.Media.ItemAction(
                NaviampMediaItemActionRequest(item, NaviampMediaItemCommand.Album(NaviampArtistAlbumCommand.ToggleFavorite)),
            ),
            NaviampCoreCommand.Media.ItemAction(
                NaviampMediaItemActionRequest(item, NaviampMediaItemCommand.Artist(NaviampArtistMediaCommand.ToggleFavorite)),
            ),
            NaviampCoreCommand.Media.ItemAction(
                NaviampMediaItemActionRequest(item, NaviampMediaItemCommand.Artist(NaviampArtistMediaCommand.StartRadio)),
            ),
            NaviampCoreCommand.Detail.Album(
                NaviampAlbumDetailActionRequest(item, NaviampAlbumDetailCommand.Play(shuffle = false)),
            ),
            NaviampCoreCommand.Detail.Artist(
                NaviampArtistDetailActionRequest(item, NaviampArtistDetailCommand.StartRadio),
            ),
            NaviampCoreCommand.Detail.ArtistAlbum(
                NaviampArtistAlbumActionRequest(item, NaviampArtistAlbumCommand.AddToQueue),
            ),
            NaviampCoreCommand.Detail.AlbumTrack(row),
            NaviampCoreCommand.Detail.ArtistPopularTrack(row),
            NaviampCoreCommand.Detail.PlaylistTrack(row),
        )

        commands.forEach { command ->
            assertEquals(NaviampCoreCommandResult.Completed, core.execute(command), command.toString())
        }
    }
}

internal fun fakeCoreServices(provider: MediaProvider? = null) = NaviampCoreServices(
    content = NaviampCoreContentServices(
        providerSource = NaviampCoreMediaProviderSource { provider },
        homeDate = NaviampCoreHomeDateSource { HomeDate(2026, 202) },
        homeSupplement = NaviampCoreHomeSupplementSource { NaviampCoreHomeSupplement() },
        playlistSupplement = NaviampCorePlaylistBrowseSupplementSource { NaviampCorePlaylistBrowseSupplement() },
        artistDiscovery = NaviampCoreArtistDiscoveryServices(),
        externalUri = NaviampCoreExternalUriPort {},
    ),
    connection = object : NaviampCoreProviderSessionPort {
        override suspend fun connect(
            request: NaviampCoreConnectionRequest,
            plan: app.naviamp.app.NaviampConnectionAttemptPlan,
        ): NaviampCoreConnectedSession = error("Not used")

        override suspend fun editableConnection(id: String): NaviampCoreEditableConnection = error("Not used")
        override suspend fun deleteConnection(id: String) = NaviampCoreConnectionInventory()
    },
    settings = NaviampCoreSettingsServices(
        interfaceSettings = NaviampCoreInterfaceSettingsStore {},
        cacheSettings = NaviampCoreCacheSettingsPort { it.normalized() },
        maintenance = NaviampCoreMaintenancePort { NaviampCoreMaintenanceResult("complete") },
        sync = fakeCoreSettingsSyncServices(),
    ),
    downloads = NaviampCoreDownloadServices(
        storage = object : NaviampCoreDownloadStoragePort {
            override suspend fun snapshot(sourceId: String) = NaviampCoreDownloadStorageSnapshot()
            override suspend fun pruneMissing(sourceId: String) = 0
            override suspend fun remove(sourceId: String, track: Track) = Unit
            override suspend fun deleteAll(sourceId: String) = 0
        },
        transfer = NaviampCoreDownloadTransferPort { _, _, update ->
            update(DownloadJobUpdate.Completed)
            NaviampCoreDownloadTransferResult(refreshDownloads = false)
        },
        keepDownloaded = object : NaviampCoreKeepDownloadedPort {
            override fun policies(sourceId: String) = emptyList<KeepDownloadedCollectionPolicy>()
            override fun toggle(policy: KeepDownloadedCollectionPolicy) = NaviampKeepDownloadedToggleResult.Enable
            override fun reconcile(policy: KeepDownloadedCollectionPolicy, tracks: List<Track>) =
                NaviampKeepDownloadedReconciliationApplication(emptyList(), null, null, false)
        },
        playback = NaviampCoreDownloadedPlaybackPort { _, _ -> },
        network = NaviampCoreMobileNetworkPort { false },
    ),
    playlists = NaviampCorePlaylistServices(
        playback = NaviampCorePlaylistPlaybackPort { _, _, _ -> },
        queue = NaviampCorePlaylistQueuePort { _, _ -> },
        downloads = NaviampCorePlaylistDownloadPort { _, _, _ -> },
        history = NaviampCorePlaylistHistoryPort { current, _ -> current },
        smartProviderSource = NaviampCoreSmartPlaylistProviderSource { null },
    ),
    radio = NaviampCoreRadioServices(
        playback = NaviampCoreInternetRadioPlaybackPort {},
        recents = object : NaviampCoreInternetRadioRecentsPort {
            override fun current() = emptyList<InternetRadioStation>()
            override suspend fun record(station: InternetRadioStation) = listOf(station)
        },
    ),
    mixes = NaviampCoreMixServices(
        artist = { error("Artist mix service is lazy") },
        album = { error("Album mix service is lazy") },
        genre = { error("Genre mix service is lazy") },
        standardPlayback = object : NaviampCoreStandardMixPlaybackPort {
            override suspend fun playArtistMix(artists: List<Artist>, seedTracks: List<Track>) = Unit
            override suspend fun playAlbumMix(albums: List<Album>, seedTracks: List<Track>) = Unit
            override suspend fun playGenreMix(genres: List<Genre>) = Unit
        },
        sonicPlayback = NaviampCoreSonicPlaybackPort { _, _ -> },
        sonicQueue = NaviampCoreSonicQueuePort { _, _ -> },
    ),
    playback = NaviampCorePlaybackServices(
        effects = FakeCorePlaybackEffects(),
        settings = NaviampCorePlaybackSettingsPort { settings, _ -> settings },
        sidecars = object : NaviampCoreNowPlayingSidecarPort {
            override fun snapshot() = NaviampCoreNowPlayingSidecars()
            override suspend fun loadForTrack(track: Track) = Unit
            override suspend fun loadLyrics(track: Track) = Unit
            override suspend fun changeLyricsOffset(track: Track, offsetMillis: Int) = Unit
        },
        visualizerSettings = object : NaviampCoreVisualizerSettingsPort {
            override fun save(visualizer: NaviampVisualizer) = Unit
        },
        sessions = NaviampPlaybackSessionController(object : PlaybackSessionRepository {
            override fun loadPlaybackSession(sourceId: String?): PlaybackSessionSettings? = null
            override fun savePlaybackSession(session: PlaybackSessionSettings?, sourceId: String?) = Unit
        }),
    ),
    clockEpochMillis = { 1_000L },
    favoritedAtIso8601 = { "2026-07-21T00:00:00Z" },
)

private fun fakeCoreSettingsSyncServices(): NaviampCoreSettingsSyncServices {
    var runtime = app.naviamp.domain.settings.SettingsSyncRuntimeState()
    return NaviampCoreSettingsSyncServices(
        controller = app.naviamp.app.NaviampSettingsSyncController(
            deviceId = "test",
            state = { runtime },
            saveState = { runtime = it },
            nowEpochMillis = { 1L },
            snapshot = { app.naviamp.domain.settings.SettingsSyncLocalSnapshot() },
            applyDocument = {},
        ),
        port = object : NaviampCoreSettingsSyncPort {
            private var configuration = NaviampCoreSettingsSyncConfiguration(directoryPath = "/sync")
            override fun configuration() = configuration
            override fun saveConfiguration(configuration: NaviampCoreSettingsSyncConfiguration) {
                this.configuration = configuration
            }
            override suspend fun readDocument(directoryPath: String) = null
            override suspend fun writeDocument(
                directoryPath: String,
                document: app.naviamp.domain.settings.SettingsSyncDocument,
            ) = "settings.json"
            override suspend fun chooseDirectory(currentPath: String?, title: String) = "/sync"
            override fun defaultDirectory() = "/home"
            override val available = true
        },
    )
}

private class FakeCorePlaybackEffects : NaviampCorePlaybackEffectPort {
    override val capabilities = NaviampCorePlaybackCapabilities()
    override val playbackSource = PlaybackSource.ProviderStream
    override fun pause() = Unit
    override fun resume() = Unit
    override fun startOrRestore() = false
    override fun seek(positionSeconds: Double) = Unit
    override fun replayCurrent(positionSeconds: Double) = Unit
    override fun setVolume(percent: Int) = Unit
    override fun stop() = Unit
    override fun applyQueue(queue: PlaybackQueue, clearPreparedNext: Boolean) = Unit
    override fun applyNavigation(command: PlaybackQueueNavigationCommand) = Unit
    override fun applyRepeatMode(mode: RepeatMode) = Unit
    override fun playQueueSelection(queue: PlaybackQueue, index: Int) = Unit
}

private fun coreTrack(id: String) = Track(
    id = app.naviamp.domain.TrackId(id),
    title = id,
    artistName = "Artist",
    albumTitle = "Album",
    durationSeconds = 180,
    coverArtId = id,
    audioInfo = null,
    replayGain = null,
)
