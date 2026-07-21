package app.naviamp.presentation

import app.naviamp.app.NaviampKeepDownloadedReconciliationApplication
import app.naviamp.app.NaviampKeepDownloadedToggleResult
import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.Genre
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Track
import app.naviamp.domain.cache.DownloadJobUpdate
import app.naviamp.domain.cache.KeepDownloadedCollectionPolicy
import app.naviamp.domain.home.HomeDate
import app.naviamp.domain.playback.PlaybackQueueNavigationCommand
import app.naviamp.domain.playback.PlaybackSource
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.ui.NaviampSettingsSyncUi
import app.naviamp.ui.NaviampVisualizer
import app.naviamp.ui.SharedRoute
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
        assertEquals("exported", core.state.value.settingsSync.status)
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
}

private fun fakeCoreServices() = NaviampCoreServices(
    content = NaviampCoreContentServices(
        providerSource = NaviampCoreMediaProviderSource { null },
        homeDate = NaviampCoreHomeDateSource { HomeDate(2026, 202) },
        homeSupplement = NaviampCoreHomeSupplementSource { NaviampCoreHomeSupplement() },
        playlistSupplement = NaviampCorePlaylistBrowseSupplementSource { NaviampCorePlaylistBrowseSupplement() },
        artistDiscovery = NaviampCoreArtistDiscoveryServices(),
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
        sync = FakeCoreSettingsSyncPort(),
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
    ),
    clockEpochMillis = { 1_000L },
    favoritedAtIso8601 = { "2026-07-21T00:00:00Z" },
)

private class FakeCoreSettingsSyncPort : NaviampCoreSettingsSyncPort {
    override fun current() = syncState("ready")
    override suspend fun changeDirectory(path: String?) = syncState("directory")
    override suspend fun selectImportDirectory(path: String) = syncState("import-directory")
    override suspend fun changeAutoExport(enabled: Boolean) = syncState("auto-export")
    override suspend fun export() = syncState("exported")
    override suspend fun import() = syncState("imported")
    override suspend fun importFile() = syncState("file-imported")
    override suspend fun chooseFolder() = syncState("folder")
    override suspend fun importFolder() = syncState("folder-imported")
    override suspend fun exportFolder() = syncState("folder-exported")

    private fun syncState(status: String) = NaviampSettingsSyncUi(status = status, available = true)
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
