package app.naviamp.testkit

import app.naviamp.app.NaviampKeepDownloadedReconciliationApplication
import app.naviamp.app.NaviampKeepDownloadedToggleResult
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
import app.naviamp.domain.settings.PlaybackSessionSettings
import app.naviamp.presentation.*
import app.naviamp.ui.NaviampSettingsSyncUi
import app.naviamp.ui.NaviampVisualizer

/** Complete fake service catalog for testing any thin host against the real Core composition. */
fun naviampCoreTestServices(provider: MediaProvider? = null): NaviampCoreServices = NaviampCoreServices(
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
        ): NaviampCoreConnectedSession = error("Connection was not configured by this test host.")

        override suspend fun editableConnection(id: String): NaviampCoreEditableConnection =
            error("Connection editing was not configured by this test host.")

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
        sync = testSettingsSyncServices(),
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
        effects = TestPlaybackEffects(),
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
        sessions = NaviampPlaybackSessionController(EmptyPlaybackSessionRepository),
    ),
    clockEpochMillis = { 1_000L },
    favoritedAtIso8601 = { "2026-07-21T00:00:00Z" },
)

private object EmptyPlaybackSessionRepository : PlaybackSessionRepository {
    override fun loadPlaybackSession(sourceId: String?): PlaybackSessionSettings? = null
    override fun savePlaybackSession(session: PlaybackSessionSettings?, sourceId: String?) = Unit
}

private fun testSettingsSyncServices(): NaviampCoreSettingsSyncServices {
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
            private var configuration = NaviampCoreSettingsSyncConfiguration()
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

private class TestPlaybackEffects : NaviampCorePlaybackEffectPort {
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
