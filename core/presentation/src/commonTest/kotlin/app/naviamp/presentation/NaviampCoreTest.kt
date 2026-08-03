package app.naviamp.presentation

import app.naviamp.app.NaviampKeepDownloadedReconciliationApplication
import app.naviamp.app.NaviampKeepDownloadedToggleResult
import app.naviamp.app.NaviampConnectionPhase
import app.naviamp.app.NaviampConnectionRuntimeState
import app.naviamp.app.NaviampLivePlaybackState
import app.naviamp.app.NaviampPlaybackSessionController
import app.naviamp.app.NaviampProviderActionController
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
import app.naviamp.domain.provider.PendingProviderAction
import app.naviamp.domain.provider.PendingProviderActionRepository
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.radio.internetRadioTrack
import app.naviamp.domain.radio.libraryRecentRadioStream
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
import app.naviamp.ui.NaviampPlaylistDetailActionRequest
import app.naviamp.ui.NaviampPlaylistDetailCommand
import app.naviamp.ui.NaviampArtistMediaCommand
import app.naviamp.ui.NaviampVisualizer
import app.naviamp.ui.DownloadedTrackAction
import app.naviamp.ui.DownloadedTrackActionRequest
import app.naviamp.ui.SharedHomeDiscoveryTrackActionRequest
import app.naviamp.ui.SharedHomeStationUi
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.SharedRoute
import app.naviamp.ui.SharedTrackRowAction
import app.naviamp.ui.SharedTrackRowActionRequest
import app.naviamp.ui.SharedTrackRowUi
import app.naviamp.ui.toSharedMediaItemUi
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
    fun failedNowPlayingReportEntersTheSharedPendingActionQueue() = runTest {
        val provider = FakeCoreMediaProvider(
            supportsPlayReporting = true,
            failNowPlayingReports = true,
        )
        val pending = RecordingCorePendingProviderActionRepository()
        val effects = FakeCorePlaybackEffects()
        val services = fakeCoreServices(
            provider = provider,
            playbackEffects = effects,
            providerActions = NaviampProviderActionController(pending),
        )
        val initial = NaviampCoreInitialState().let { state ->
            state.copy(
                connectionInventory = NaviampCoreConnectionInventory(currentSourceId = "source"),
                product = state.product.copy(
                    shell = state.product.shell.copy(
                        connectionSettings = state.product.shell.connectionSettings.copy(
                            currentSourceId = "source",
                        ),
                    ),
                ),
            )
        }
        val core = NaviampCore.create(this, services, initialState = initial)
        core.updateLivePlayback { live ->
            live.copy(
                currentTrack = provider.track,
                queue = PlaybackQueue(listOf(provider.track), 0),
            )
        }

        assertTrue(provider.capabilities.supportsPlayReporting)
        assertEquals("source", core.state.value.shell.connectionSettings.currentSourceId)
        assertNotNull(effects.observer).onStateChanged(app.naviamp.domain.playback.PlaybackState.Playing)
        advanceUntilIdle()

        assertEquals(
            listOf("source:${app.naviamp.domain.provider.PendingActionReportNowPlaying}:core-track"),
            pending.enqueued,
        )
    }

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
    fun selectingDownloadedContentUsesTheCoreQueuePlaybackTransaction() = runTest {
        val provider = FakeCoreMediaProvider()
        val effects = FakeCorePlaybackEffects()
        val downloads = listOf(
            NaviampCoreDownloadedTrack("file-one", coreTrack("download-one"), 1_000, "FLAC"),
            NaviampCoreDownloadedTrack("file-two", coreTrack("download-two"), 2_000, "FLAC"),
        )
        val defaults = fakeCoreServices(provider, playbackEffects = effects)
        val services = defaults.copy(
            downloads = defaults.downloads.copy(
                storage = object : NaviampCoreDownloadStoragePort {
                    override suspend fun snapshot(sourceId: String) =
                        NaviampCoreDownloadStorageSnapshot(downloads = downloads)

                    override suspend fun pruneMissing(sourceId: String) = 0
                    override suspend fun remove(sourceId: String, track: Track) = Unit
                    override suspend fun deleteAll(sourceId: String) = 0
                },
            ),
        )
        val initial = NaviampCoreInitialState().let { state ->
            state.copy(
                connection = NaviampConnectionRuntimeState(
                    phase = NaviampConnectionPhase.Connected,
                    sourceId = "source",
                ),
                connectionInventory = NaviampCoreConnectionInventory(currentSourceId = "source"),
                product = state.product.copy(
                    shell = state.product.shell.copy(
                        connectionSettings = state.product.shell.connectionSettings.copy(
                            currentSourceId = "source",
                        ),
                    ),
                ),
            )
        }
        val core = NaviampCore.create(this, services, initialState = initial)
        core.execute(NaviampCoreCommand.Downloads.Refresh)

        core.execute(
            NaviampCoreCommand.Downloads.TrackAction(
                DownloadedTrackActionRequest(
                    download = core.state.value.shell.downloads.downloads[1],
                    action = DownloadedTrackAction.Select,
                ),
            ),
        )

        val queue = effects.selections.single()
        assertEquals(listOf("download-one", "download-two"), queue.tracks.map { it.id.value })
        assertEquals(1, queue.currentIndex)
        assertEquals("download-two", core.state.value.shell.nowPlaying?.id)
        assertTrue(core.state.value.shell.shellChrome.nowPlayingOpen)
    }

    @Test
    fun playlistDownloadUsesTheSharedDownloadsControllerRatherThanTheLegacyPlaylistPlaceholder() = runTest {
        val provider = FakeCoreMediaProvider()
        var transferredTrackIds = emptyList<String>()
        val defaults = fakeCoreServices(provider)
        val services = defaults.copy(
            downloads = defaults.downloads.copy(
                transfer = NaviampCoreDownloadTransferPort { request, _, update ->
                    transferredTrackIds = request.tracks.map { it.id.value }
                    update(DownloadJobUpdate.Completed)
                    NaviampCoreDownloadTransferResult(refreshDownloads = false)
                },
            ),
        )
        val initial = NaviampCoreInitialState().let { state ->
            state.copy(
                connection = NaviampConnectionRuntimeState(
                    phase = NaviampConnectionPhase.Connected,
                    sourceId = "source",
                ),
                product = state.product.copy(
                    shell = state.product.shell.copy(
                        connectionSettings = state.product.shell.connectionSettings.copy(
                            currentSourceId = "source",
                        ),
                    ),
                ),
            )
        }
        val core = NaviampCore.create(this, services, initialState = initial)

        core.execute(
            NaviampCoreCommand.Playlists.Detail(
                NaviampPlaylistDetailActionRequest(
                    playlist = provider.playlist.toSharedMediaItemUi(
                        coverArtUrl = { id: String? -> id?.let(provider::coverArtUrl) },
                    ),
                    command = NaviampPlaylistDetailCommand.Download(null),
                ),
            ),
        )
        advanceUntilIdle()

        assertEquals(listOf(provider.track.id.value), transferredTrackIds)
        assertEquals("Starting download for Core Playlist...", core.state.value.shell.playlists.status)
    }

    @Test
    fun playlistAddToQueueUsesTheCoreQueueCoordinator() = runTest {
        val provider = FakeCoreMediaProvider()
        val effects = FakeCorePlaybackEffects()
        val current = coreTrack("current")
        val defaults = fakeCoreServices(provider)
        val core = NaviampCore.create(
            scope = this,
            services = defaults.copy(
                playback = defaults.playback.copy(effects = effects),
            ),
            initialState = NaviampCoreInitialState(
                playback = NaviampLivePlaybackState(
                    currentTrack = current,
                    queue = PlaybackQueue(listOf(current), currentIndex = 0),
                ),
            ),
        )

        core.execute(
            NaviampCoreCommand.Playlists.Detail(
                NaviampPlaylistDetailActionRequest(
                    playlist = provider.playlist.toSharedMediaItemUi(
                        coverArtUrl = { id: String? -> id?.let(provider::coverArtUrl) },
                    ),
                    command = NaviampPlaylistDetailCommand.AddToQueue,
                ),
            ),
        )

        assertEquals(
            listOf(current.id.value, provider.track.id.value),
            effects.appliedQueues.single().tracks.map { it.id.value },
        )
        assertEquals(
            listOf(provider.track.title),
            assertNotNull(core.state.value.shell.nowPlaying).upNext.map { it.title },
        )
        assertEquals("Connected.", core.state.value.shell.playlistDetail.status)
    }

    @Test
    fun generatedRadioRecentsReplayAndPersistThroughCore() = runTest {
        val provider = FakeCoreMediaProvider()
        val effects = FakeCorePlaybackEffects()
        var persisted = listOf(libraryRecentRadioStream())
        val defaults = fakeCoreServices(provider)
        val core = NaviampCore.create(
            scope = this,
            services = defaults.copy(
                playback = defaults.playback.copy(effects = effects),
                radio = defaults.radio.copy(
                    generatedRecents = NaviampCoreGeneratedRadioRecentsPort(
                        load = { persisted },
                        save = { persisted = it },
                    ),
                ),
            ),
        )

        core.execute(
            NaviampCoreCommand.Home.SelectRecentRadio(
                SharedMediaItemUi("library", "Library Radio", "Radio"),
            ),
        )

        assertEquals(listOf(provider.track.id.value), effects.selections.single().tracks.map { it.id.value })
        assertEquals("library", persisted.single().id)
        assertEquals("library", core.state.value.shell.home.content.recentRadioStreams.single().id)
    }

    @Test
    fun sonicPlaybackUsesCoreTrackTransitionAfterInternetRadio() = runTest {
        val provider = FakeCoreMediaProvider(supportsSonicSimilarity = true)
        val effects = FakeCorePlaybackEffects()
        val services = fakeCoreServices(provider).let { defaults ->
            defaults.copy(playback = defaults.playback.copy(effects = effects))
        }
        val station = InternetRadioStation("radio", "Radio", "https://radio.example")
        val radioTrack = internetRadioTrack(station)
        val core = NaviampCore.create(
            scope = this,
            services = services,
            initialState = NaviampCoreInitialState(
                playback = NaviampLivePlaybackState(
                    currentTrack = radioTrack,
                    currentStation = station,
                    queue = PlaybackQueue(listOf(radioTrack), 0),
                ),
            ),
        )

        core.dispatch(
            NaviampCoreCommand.MixBuilder.SonicPath(
                NaviampCoreCommand.SonicPathAction.ChangeStartQuery("start"),
            ),
        )
        core.execute(
            NaviampCoreCommand.MixBuilder.SonicPath(NaviampCoreCommand.SonicPathAction.SearchStart),
        )
        core.dispatch(
            NaviampCoreCommand.MixBuilder.SonicPath(
                NaviampCoreCommand.SonicPathAction.SelectStart(
                    core.state.value.shell.sonicPathBuilder.startSuggestions.single(),
                ),
            ),
        )
        core.dispatch(
            NaviampCoreCommand.MixBuilder.SonicPath(
                NaviampCoreCommand.SonicPathAction.ChangeEndQuery("end"),
            ),
        )
        core.execute(
            NaviampCoreCommand.MixBuilder.SonicPath(NaviampCoreCommand.SonicPathAction.SearchEnd),
        )
        core.dispatch(
            NaviampCoreCommand.MixBuilder.SonicPath(
                NaviampCoreCommand.SonicPathAction.SelectEnd(
                    core.state.value.shell.sonicPathBuilder.endSuggestions.single(),
                ),
            ),
        )
        core.execute(NaviampCoreCommand.MixBuilder.SonicPath(NaviampCoreCommand.SonicPathAction.Build))
        core.execute(NaviampCoreCommand.MixBuilder.SonicPath(NaviampCoreCommand.SonicPathAction.Play))

        assertTrue(core.state.value.shell.shellChrome.nowPlayingOpen)
        assertEquals("start", core.state.value.shell.nowPlaying?.id)
        assertTrue(core.state.value.shell.nowPlaying?.isLive == false)
        assertEquals(listOf("start"), effects.selections.map { it.current?.id?.value })
    }

    @Test
    fun successfulConnectionPopulatesProviderBackedScreensWithoutHostRefreshCommands() = runTest {
        val provider = FakeCoreMediaProvider(supportsSonicSimilarity = true)
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
                override suspend fun smartPlaylistProvider(password: String?) = provider
                override suspend fun refreshActiveSession() = true
                override suspend fun persistActiveSession() = Unit
                override suspend fun clearActiveSession() = Unit
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
        assertTrue(core.state.value.shell.playback.sonicSimilarityAvailable)
        assertTrue(core.state.value.shell.capabilities.sonicSimilarity)
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

internal fun fakeCoreServices(
    provider: MediaProvider? = null,
    playbackEffects: NaviampCorePlaybackEffectPort = FakeCorePlaybackEffects(),
    providerActions: NaviampProviderActionController = NaviampProviderActionController(
        EmptyCorePendingProviderActionRepository,
    ),
) = NaviampCoreServices(
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
        override suspend fun smartPlaylistProvider(password: String?) = provider
        override suspend fun refreshActiveSession() = false
        override suspend fun persistActiveSession() = Unit
        override suspend fun clearActiveSession() = Unit
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
        network = NaviampCoreMobileNetworkPort { false },
    ),
    playlists = NaviampCorePlaylistServices(
        history = NaviampCorePlaylistHistoryPort { current, _ -> current },
    ),
    radio = NaviampCoreRadioServices(
        playback = NaviampCoreInternetRadioPlaybackPort {},
        recents = object : NaviampCoreInternetRadioRecentsPort {
            override fun current() = emptyList<InternetRadioStation>()
            override suspend fun record(station: InternetRadioStation) = listOf(station)
        },
        generatedRecents = NaviampCoreGeneratedRadioRecentsPort(
            load = { emptyList() },
            save = {},
        ),
    ),
    mixes = NaviampCoreMixServices(
        artist = { error("Artist mix service is lazy") },
        album = { error("Album mix service is lazy") },
        genre = { error("Genre mix service is lazy") },
    ),
    playback = NaviampCorePlaybackServices(
        effects = playbackEffects,
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
    providerActions = providerActions,
    clockEpochMillis = { 1_000L },
    favoritedAtIso8601 = { "2026-07-21T00:00:00Z" },
)

private object EmptyCorePendingProviderActionRepository : PendingProviderActionRepository {
    override fun enqueuePendingProviderAction(
        sourceId: String,
        actionType: String,
        entityId: String,
        boolValue: Boolean?,
        longValue: Long?,
        replaceMatchingEntityAction: Boolean,
    ) = Unit

    override fun pendingProviderActions(sourceId: String, limit: Int): List<PendingProviderAction> = emptyList()
    override fun deletePendingProviderAction(id: Long) = Unit
    override fun markPendingProviderActionFailed(id: Long, errorMessage: String?) = Unit
}

private class RecordingCorePendingProviderActionRepository : PendingProviderActionRepository {
    val enqueued = mutableListOf<String>()

    override fun enqueuePendingProviderAction(
        sourceId: String,
        actionType: String,
        entityId: String,
        boolValue: Boolean?,
        longValue: Long?,
        replaceMatchingEntityAction: Boolean,
    ) {
        enqueued += "$sourceId:$actionType:$entityId"
    }

    override fun pendingProviderActions(sourceId: String, limit: Int): List<PendingProviderAction> = emptyList()
    override fun deletePendingProviderAction(id: Long) = Unit
    override fun markPendingProviderActionFailed(id: Long, errorMessage: String?) = Unit
}

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
            override suspend fun readDocumentFile(filePath: String) = null
            override suspend fun writeDocument(
                directoryPath: String,
                document: app.naviamp.domain.settings.SettingsSyncDocument,
            ) = "settings.json"
            override suspend fun chooseDirectory(currentPath: String?, title: String) = "/sync"
            override suspend fun chooseDocument(currentPath: String?, title: String) = "/sync/naviamp-settings.json"
            override fun defaultDirectory() = "/home"
            override val available = true
        },
    )
}

private class FakeCorePlaybackEffects : NaviampCorePlaybackEffectPort {
    override val capabilities = NaviampCorePlaybackCapabilities()
    override val playbackSource = PlaybackSource.ProviderStream
    val selections = mutableListOf<PlaybackQueue>()
    val appliedQueues = mutableListOf<PlaybackQueue>()
    var observer: NaviampCorePlaybackObserver? = null
    override fun attach(observer: NaviampCorePlaybackObserver) {
        this.observer = observer
    }
    override fun pause() = Unit
    override fun resume() = Unit
    override fun startOrRestore() = false
    override fun seek(positionSeconds: Double) = Unit
    override fun replayCurrent(positionSeconds: Double) = Unit
    override fun setVolume(percent: Int) = Unit
    override fun stop() = Unit
    override fun applyQueue(queue: PlaybackQueue, clearPreparedNext: Boolean) {
        appliedQueues += queue
    }
    override fun applyNavigation(command: PlaybackQueueNavigationCommand) = Unit
    override fun applyRepeatMode(mode: RepeatMode) = Unit
    override fun playQueueSelection(queue: PlaybackQueue, index: Int) {
        selections += queue.jumpTo(index)
    }
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
