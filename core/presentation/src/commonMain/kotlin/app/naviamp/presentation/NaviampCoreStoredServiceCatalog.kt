package app.naviamp.presentation

import app.naviamp.app.NaviampSettingsSyncController
import app.naviamp.domain.cache.CacheMaintenanceRepository
import app.naviamp.domain.cache.KeepDownloadedRepository
import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.domain.cache.MediaSourceRepository
import app.naviamp.domain.cache.ProviderMediaSourceRepository
import app.naviamp.domain.cache.ProviderResponseCacheRepository
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.cache.StorageCacheStats
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.provider.PendingProviderActionRepository
import app.naviamp.domain.radio.RadioDjPresetRepository
import app.naviamp.domain.settings.CacheSettings
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.settings.RecentRadioStream
import app.naviamp.domain.settings.SavedInternetRadioStation
import app.naviamp.domain.settings.SettingsSyncLocalSnapshot
import app.naviamp.domain.settings.SettingsSyncRuntimeState
import app.naviamp.domain.settings.VisualizerSettings
import app.naviamp.domain.settings.applySettingsSyncDocument
import app.naviamp.domain.settings.normalized
import app.naviamp.ui.NaviampShellCapabilitiesUi
import app.naviamp.ui.NaviampStorageLocationUi
import app.naviamp.ui.naviampVisualizerFromName
import app.naviamp.ui.toCacheSettingsUi

/** Portable settings persistence surface consumed by the complete Core catalog. */
data class NaviampCoreStoredSettings(
    val loadInterface: () -> InterfaceSettings,
    val saveInterface: (InterfaceSettings) -> Unit,
    val loadPlayback: () -> PlaybackSettings,
    val loadCache: () -> CacheSettings,
    val saveCache: (CacheSettings) -> Unit,
    val loadVisualizer: () -> VisualizerSettings,
    val saveVisualizer: (VisualizerSettings) -> Unit,
    val loadRecentRadioStreams: () -> List<RecentRadioStream>,
    val saveRecentRadioStreams: (List<RecentRadioStream>) -> Unit,
    val loadRecentInternetRadioStations: () -> List<SavedInternetRadioStation>,
    val saveRecentInternetRadioStations: (List<SavedInternetRadioStation>) -> Unit,
    val loadSyncRuntime: () -> SettingsSyncRuntimeState,
    val saveSyncRuntime: (SettingsSyncRuntimeState) -> Unit,
    val loadRecentPlaylistIds: () -> List<String> = { emptyList() },
    val saveRecentPlaylistIds: (List<String>) -> Unit = {},
)

/** Adds shared repository-backed settings without making a host reconstruct product state. */
fun NaviampCoreStoredSettings.withStorageBackedSettings(
    radioDjPresetRepository: RadioDjPresetRepository,
    onCacheSettingsSaved: (CacheSettings) -> Unit = {},
): NaviampCoreStoredSettings {
    val delegate = this
    return copy(
        loadPlayback = {
            delegate.loadPlayback().copy(radioDjs = radioDjPresetRepository.radioDjPresets())
        },
        saveCache = { settings ->
            onCacheSettingsSaved(settings)
            delegate.saveCache(settings)
        },
    )
}

/** Shared repositories needed by storage-backed Core service families. */
data class NaviampCoreStoredRepositories(
    val mediaSources: MediaSourceRepository,
    val providerMediaSources: ProviderMediaSourceRepository,
    val libraryIndex: LocalLibraryIndexRepository,
    val providerResponses: ProviderResponseCacheRepository,
    val keepDownloaded: KeepDownloadedRepository,
    val radioDjPresets: RadioDjPresetRepository,
    val maintenance: CacheMaintenanceRepository<StorageCacheStats>,
    val pendingProviderActions: PendingProviderActionRepository,
    val updateAudioCacheLimit: (Long) -> Unit = {},
)

data class NaviampCoreStoredServiceCatalog(
    val services: NaviampCoreServices,
    val initialState: NaviampCoreInitialState,
)

/**
 * Assembles all provider-, settings-, and storage-backed product services in common code.
 * Hosts supply repositories and native effects; they do not choose feature policy or state wiring.
 */
fun naviampCoreStoredServiceCatalog(
    providerSessions: NaviampCoreProviderSessionPort,
    providerSource: NaviampCoreMediaProviderSource,
    playback: NaviampCorePlaybackServices,
    downloads: NaviampCoreDownloadServices,
    playbackEngine: PlaybackEngine,
    settingsSyncPort: NaviampCoreSettingsSyncPort,
    settings: NaviampCoreStoredSettings,
    repositories: NaviampCoreStoredRepositories,
    externalUri: NaviampCoreExternalUriPort,
    homeDate: NaviampCoreHomeDateSource,
    shellCapabilities: NaviampShellCapabilitiesUi,
    settingsSyncDeviceId: String,
    downloadLocations: List<NaviampStorageLocationUi> = emptyList(),
    audioCacheLocations: List<NaviampStorageLocationUi> = emptyList(),
    selectedDownloadLocationId: String? = null,
    selectedAudioCacheLocationId: String? = null,
    sourceId: () -> String?,
    clockEpochMillis: () -> Long,
    favoritedAtIso8601: () -> String,
    diagnostics: NaviampCoreDiagnosticsPort = emptyNaviampCoreDiagnosticsPort(),
): NaviampCoreStoredServiceCatalog {
    val initialInterfaceSettings = settings.loadInterface()
    val initialPlaybackSettings = settings.loadPlayback()
    val initialCacheSettings = settings.loadCache()
    val internetRadioRecents = naviampCoreInternetRadioRecentsPort(
        load = settings.loadRecentInternetRadioStations,
        persist = settings.saveRecentInternetRadioStations,
    )
    val sync = NaviampCoreSettingsSyncServices(
        controller = NaviampSettingsSyncController(
            deviceId = settingsSyncDeviceId,
            state = settings.loadSyncRuntime,
            saveState = settings.saveSyncRuntime,
            nowEpochMillis = clockEpochMillis,
            snapshot = {
                SettingsSyncLocalSnapshot(
                    serverProfiles = repositories.mediaSources.mediaSources(),
                    interfaceSettings = settings.loadInterface(),
                    playback = settings.loadPlayback().copy(
                        radioDjs = repositories.radioDjPresets.radioDjPresets(),
                    ),
                    visualizer = settings.loadVisualizer(),
                    recentRadioStreams = settings.loadRecentRadioStreams(),
                    recentInternetRadioStations = settings.loadRecentInternetRadioStations(),
                )
            },
            applyDocument = { document ->
                val applied = applySettingsSyncDocument(
                    document = document,
                    playbackEngine = playbackEngine,
                    mediaSourceRepository = repositories.providerMediaSources,
                    radioDjPresetRepository = repositories.radioDjPresets,
                )
                settings.saveInterface(applied.interfaceSettings)
                playback.settings.apply(applied.playbackSettings, redownload = false)
                settings.saveVisualizer(applied.visualizer)
                settings.saveRecentRadioStreams(applied.recentRadioStreams)
                settings.saveRecentInternetRadioStations(applied.recentInternetRadioStations)
            },
        ),
        port = settingsSyncPort,
    )
    val defaults = naviampCoreServiceDefaults(
        providerSource = providerSource,
        connection = providerSessions,
        playback = playback,
        settingsSync = sync,
        externalUri = externalUri,
        homeDate = homeDate,
        sourceId = sourceId,
        libraryIndex = repositories.libraryIndex,
        pendingProviderActions = repositories.pendingProviderActions,
        clockEpochMillis = clockEpochMillis,
        favoritedAtIso8601 = favoritedAtIso8601,
    )
    val services = defaults.copy(
        content = defaults.content.copy(
            homeSupplement = NaviampCoreHomeSupplementSource {
                NaviampCoreHomeSupplement(
                    sourceId = sourceId(),
                    recentRadioStreams = settings.loadRecentRadioStreams(),
                    recentInternetRadioStations = internetRadioRecents.current(),
                )
            },
            artistDiscovery = providerArtistDiscoveryServices(
                providerSource = providerSource,
                sourceId = sourceId,
                libraryIndex = repositories.libraryIndex,
                nowEpochMillis = clockEpochMillis,
            ),
            providerResponses = ProviderResponseService(repositories.providerResponses),
            homeLibrary = localLibraryHomeRepository(repositories.libraryIndex),
            playlistSupplement = naviampCorePlaylistBrowseSupplementSource(
                recentPlaylistIds = settings.loadRecentPlaylistIds,
                sourceId = sourceId,
                keepDownloadedRepository = repositories.keepDownloaded,
            ),
        ),
        settings = defaults.settings.copy(
            interfaceSettings = NaviampCoreInterfaceSettingsStore(settings.saveInterface),
            cacheSettings = NaviampCoreCacheSettingsPort { requested ->
                requested.normalized().also { effective ->
                    repositories.updateAudioCacheLimit(effective.maxAudioCacheBytes)
                    settings.saveCache(effective)
                }
            },
            maintenance = naviampCoreRepositoryMaintenancePort(
                repository = repositories.maintenance,
                libraryIndex = repositories.libraryIndex,
                sourceId = sourceId,
            ),
            sync = sync,
        ),
        downloads = downloads,
        playlists = defaults.playlists.copy(
            history = naviampCorePlaylistHistoryPort(settings.saveRecentPlaylistIds),
        ),
        radio = defaults.radio.copy(
            recents = internetRadioRecents,
            generatedRecents = NaviampCoreGeneratedRadioRecentsPort(
                load = settings.loadRecentRadioStreams,
                save = settings.saveRecentRadioStreams,
            ),
        ),
        diagnostics = diagnostics,
    )
    val initialState = NaviampCoreInitialState().withShellCapabilities(shellCapabilities).let { initial ->
        initial.copy(
            product = initial.product.copy(
                shell = initial.product.shell.copy(
                    general = initial.product.shell.general.copy(interfaceSettings = initialInterfaceSettings),
                    playback = initial.product.shell.playback.copy(settings = initialPlaybackSettings),
                    cache = initialCacheSettings.toCacheSettingsUi(
                        stats = repositories.maintenance.stats(),
                        capabilities = shellCapabilities,
                    ).copy(
                        downloadLocations = downloadLocations,
                        audioCacheLocations = audioCacheLocations,
                        selectedDownloadLocationId = selectedDownloadLocationId,
                        selectedAudioCacheLocationId = selectedAudioCacheLocationId,
                    ),
                    shellChrome = initial.product.shell.shellChrome.copy(
                        selectedVisualizer = naviampVisualizerFromName(
                            settings.loadVisualizer().selectedVisualizer,
                        ),
                    ),
                ),
            ),
            connectionInventory = providerSessions.initialInventory(),
        )
    }
    return NaviampCoreStoredServiceCatalog(services, initialState)
}
