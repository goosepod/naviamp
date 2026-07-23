package app.naviamp.desktop

import app.naviamp.desktop.playback.bass.DesktopBassPlaybackEngineRuntime
import app.naviamp.desktop.playback.bass.loadDesktopBassAudioBackend
import app.naviamp.domain.home.HomeDate
import app.naviamp.domain.network.KtorSharedHttpClient
import app.naviamp.domain.audio.AudioMetadataSidecarService
import app.naviamp.domain.cache.CachedLyricsSidecarRepository
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.cache.ProviderResponseCacheService
import app.naviamp.domain.cache.LyricsSidecarCacheService
import app.naviamp.domain.lyrics.LrclibLyricsProvider
import app.naviamp.domain.lyrics.LyricsOffsetController
import app.naviamp.domain.lyrics.LyricsSidecarService
import app.naviamp.domain.playback.CoreBassPlaybackEngine
import app.naviamp.domain.playback.ReleasablePlaybackEngine
import app.naviamp.domain.playback.AudioOutputDevicePlaybackEngine
import app.naviamp.presentation.NaviampCoreHomeDateSource
import app.naviamp.presentation.NaviampCoreHomeSupplement
import app.naviamp.presentation.NaviampCoreHomeSupplementSource
import app.naviamp.presentation.NaviampCoreDownloadStorageSnapshot
import app.naviamp.presentation.NaviampCoreDownloadedTrack
import app.naviamp.presentation.NaviampCoreInitialState
import app.naviamp.presentation.NaviampCoreInterfaceSettingsStore
import app.naviamp.presentation.NaviampCoreCacheSettingsPort
import app.naviamp.presentation.NaviampCoreGeneratedRadioRecentsPort
import app.naviamp.presentation.NaviampCoreProviderNowPlayingSidecars
import app.naviamp.presentation.NaviampCorePlaybackEngineAdapter
import app.naviamp.presentation.NaviampCorePlaybackEngineSettings
import app.naviamp.presentation.NaviampCorePlaybackServices
import app.naviamp.presentation.NaviampCoreSettingsSyncServices
import app.naviamp.presentation.NaviampCoreVisualizerSettingsPort
import app.naviamp.presentation.naviampCoreServiceDefaults
import app.naviamp.presentation.naviampCorePlaylistHistoryPort
import app.naviamp.presentation.naviampCoreInternetRadioRecentsPort
import app.naviamp.presentation.naviampCoreRepositoryMaintenancePort
import app.naviamp.presentation.localLibraryHomeRepository
import app.naviamp.presentation.naviampCorePlaylistBrowseSupplementSource
import app.naviamp.presentation.repositoryNaviampCoreDownloadServices
import app.naviamp.presentation.toShellCapabilitiesUi
import app.naviamp.presentation.providerArtistDiscoveryServices
import app.naviamp.storage.StorageDatabaseLocation
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.ui.jvmGeneratedCoverArtBytes
import app.naviamp.ui.NaviampStorageLocationUi
import app.naviamp.ui.resetJvmPlatformCoverArtByteLoader
import app.naviamp.ui.setJvmPlatformCoverArtByteLoader
import app.naviamp.ui.naviampVisualizerFromName
import app.naviamp.ui.toCacheSettingsUi
import app.naviamp.domain.settings.VisualizerSettings
import app.naviamp.domain.settings.SettingsSyncLocalSnapshot
import app.naviamp.domain.settings.applySettingsSyncDocument
import app.naviamp.domain.waveform.AudioWaveformService
import app.naviamp.app.NaviampPlaybackSessionController
import app.naviamp.app.NaviampSettingsSyncController
import app.naviamp.desktop.DesktopAudioWaveformAnalyzer
import app.naviamp.desktop.DesktopPlaybackAudioAssets
import app.naviamp.desktop.toPlaybackLocalAudio
import app.naviamp.desktop.settings.DesktopCoreSettingsStore
import app.naviamp.desktop.settings.DesktopCoreSettingsSyncPort
import app.naviamp.desktop.settings.defaultDesktopCoreSettingsPath
import app.naviamp.desktop.platform.DesktopCoreDiagnosticsPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate

/** Owns only Desktop filesystem/native resources required by the shared Core app. */
internal class DesktopV2Composition private constructor(
    val environment: DesktopNaviampCoreEnvironment,
    private val engine: ReleasablePlaybackEngine,
    private val storage: DesktopStorageRepositories,
) : AutoCloseable {
    override fun close() {
        resetJvmPlatformCoverArtByteLoader()
        engine.release()
        storage.close()
    }

    companion object {
        fun create(scope: CoroutineScope): DesktopV2Composition {
            val nowEpochMillis = System::currentTimeMillis
            val dataDirectory = desktopV2DataDirectory()
            Files.createDirectories(dataDirectory)
            val settingsStore = DesktopCoreSettingsStore(defaultDesktopCoreSettingsPath())
            val initialInterfaceSettings = settingsStore.loadInterfaceSettings()
            val initialPlaybackSettings = settingsStore.loadPlaybackSettings()
            val initialCacheSettings = settingsStore.loadCacheSettings()
            var activeCacheSettings = initialCacheSettings
            val audioCacheDirectory = initialCacheSettings.customAudioCacheDirectory
                ?.let { path -> runCatching { Path.of(path) }.getOrNull() }
                ?: dataDirectory.resolve("audio-cache")
            val downloadDirectory = initialCacheSettings.customDownloadDirectory
                ?.let { path -> runCatching { Path.of(path) }.getOrNull() }
                ?: dataDirectory.resolve("downloads")
            val storage = DesktopStorageRepositories.open(
                location = StorageDatabaseLocation(dataDirectory.toString()),
                audioCacheDirectory = audioCacheDirectory,
                downloadDirectory = downloadDirectory,
                nowEpochMillis = nowEpochMillis,
                clearUntrackedDownloadsOnReset = initialCacheSettings.customDownloadDirectory == null,
            )
            val sessions = desktopCoreProviderSessionPort(
                storage = storage.mediaSources,
                cacheMaintenanceRepository = storage.maintenance,
                nowEpochMillis = nowEpochMillis,
            )
            val sharedHttpClient = KtorSharedHttpClient()
            setJvmPlatformCoverArtByteLoader { url ->
                jvmGeneratedCoverArtBytes(url)
                    ?: (sessions.currentProvider() as? NavidromeProvider)
                        ?.takeIf { provider -> provider.ownsUrl(url) }
                        ?.bytes(url)
                    ?: sharedHttpClient.getBytes(url)
                    ?: ByteArray(0)
            }
            val engine = CoreBassPlaybackEngine(
                backendResult = loadDesktopBassAudioBackend(),
                runtime = DesktopBassPlaybackEngineRuntime(),
            )
            val playbackSessions = NaviampPlaybackSessionController(storage.playbackSessions)
            storage.mediaSources.latestMediaSource()?.id?.let { sourceId ->
                if (playbackSessions.load(sourceId) == null) {
                    settingsStore.loadLegacyPlaybackSession()?.let { legacy ->
                        playbackSessions.save(legacy, sourceId)
                        settingsStore.removeLegacyPlaybackSession()
                    }
                }
            }
            val initialVisualizer = naviampVisualizerFromName(
                settingsStore.loadVisualizerSettings().selectedVisualizer,
            )
            val engineSettings = NaviampCorePlaybackEngineSettings(
                engine = engine,
                initial = initialPlaybackSettings,
                persist = settingsStore::savePlaybackSettings,
            )
            val playbackEffects = NaviampCorePlaybackEngineAdapter(
                scope = scope,
                engine = engine,
                providerSource = sessions.providerSource,
                settings = engineSettings::current,
            )
            val playbackAudioAssets = DesktopPlaybackAudioAssets(
                downloadRepository = storage.audioStore,
                audioCacheRepository = storage.audioStore,
            )
            val waveformService = AudioWaveformService(
                waveformRepository = storage.audioWaveforms,
                audioAssets = playbackAudioAssets,
                analyzer = DesktopAudioWaveformAnalyzer(),
                waveformsEnabled = { activeCacheSettings.waveformsEnabled },
                waveformBucketCount = { activeCacheSettings.waveformBucketCount },
                cacheAudioBeforeAnalysis = { true },
                workContext = Dispatchers.IO,
                cacheAudioForWaveform = { sourceId, provider, track, quality ->
                    storage.audioStore.cacheAudioTrack(sourceId, provider, track, quality)
                        .path
                        .toPlaybackLocalAudio()
                },
            )
            val audioMetadataSidecarService = AudioMetadataSidecarService(
                playbackAudioAssets = playbackAudioAssets,
                audioTagReader = DesktopAudioTagReader(),
            )
            val lyricsSidecarService = LyricsSidecarService(
                lyricsRepository = CachedLyricsSidecarRepository(
                    cache = LyricsSidecarCacheService(storage.lyricsSidecars, nowEpochMillis),
                    onlineProvider = LrclibLyricsProvider(sharedHttpClient),
                ),
                playbackAudioAssets = playbackAudioAssets,
                audioMetadataSidecarService = audioMetadataSidecarService,
            )
            val playback = NaviampCorePlaybackServices(
                effects = playbackEffects,
                settings = engineSettings,
                sidecars = NaviampCoreProviderNowPlayingSidecars(
                    providerSource = sessions.providerSource,
                    waveformService = waveformService,
                    playbackSettings = engineSettings::current,
                    audioCachingEnabled = { activeCacheSettings.audioCachingEnabled },
                    audioMetadataSidecarService = audioMetadataSidecarService,
                    lyricsSidecarService = lyricsSidecarService,
                    lyricsOffsetController = LyricsOffsetController(storage.lyricsOffsets),
                ),
                visualizerSettings = object : NaviampCoreVisualizerSettingsPort {
                    override fun save(visualizer: app.naviamp.ui.NaviampVisualizer) {
                        settingsStore.saveVisualizerSettings(VisualizerSettings(visualizer.name))
                    }
                },
                sessions = playbackSessions,
            )
            val internetRadioRecents = naviampCoreInternetRadioRecentsPort(
                load = settingsStore::loadRecentInternetRadioStations,
                persist = settingsStore::saveRecentInternetRadioStations,
            )
            val sync = NaviampCoreSettingsSyncServices(
                controller = NaviampSettingsSyncController(
                    deviceId = DesktopSettingsSyncDeviceId,
                    state = settingsStore::loadSettingsSyncRuntimeState,
                    saveState = settingsStore::saveSettingsSyncRuntimeState,
                    nowEpochMillis = nowEpochMillis,
                    snapshot = {
                        SettingsSyncLocalSnapshot(
                            serverProfiles = storage.mediaSources.mediaSources(),
                            interfaceSettings = settingsStore.loadInterfaceSettings(),
                            playback = engineSettings.current(),
                            visualizer = settingsStore.loadVisualizerSettings(),
                            recentRadioStreams = settingsStore.loadRecentRadioStreams(),
                            recentInternetRadioStations = settingsStore.loadRecentInternetRadioStations(),
                        )
                    },
                    applyDocument = { document ->
                        val applied = applySettingsSyncDocument(
                            document = document,
                            playbackEngine = engine,
                            mediaSourceRepository = storage.mediaSources,
                            radioDjPresetRepository = storage.radioDjPresets,
                        )
                        settingsStore.saveInterfaceSettings(applied.interfaceSettings)
                        engineSettings.apply(applied.playbackSettings, redownload = false)
                        settingsStore.saveVisualizerSettings(applied.visualizer)
                        settingsStore.saveRecentRadioStreams(applied.recentRadioStreams)
                        settingsStore.saveRecentInternetRadioStations(applied.recentInternetRadioStations)
                    },
                ),
                port = DesktopCoreSettingsSyncPort(
                    configurationState = settingsStore::loadSettingsSyncConfiguration,
                    saveConfigurationState = settingsStore::saveSettingsSyncConfiguration,
                ),
            )
            val serviceDefaults = naviampCoreServiceDefaults(
                providerSource = sessions.providerSource,
                connection = sessions,
                playback = playback,
                settingsSync = sync,
                externalUri = DesktopExternalUriPort(),
                homeDate = NaviampCoreHomeDateSource {
                    LocalDate.now().let { HomeDate(it.year, it.dayOfYear) }
                },
                sourceId = { storage.mediaSources.latestMediaSource()?.id },
                libraryIndex = storage.libraryIndex,
                clockEpochMillis = nowEpochMillis,
                favoritedAtIso8601 = { Instant.now().toString() },
            )
            val services = serviceDefaults.copy(
                content = serviceDefaults.content.copy(
                    homeSupplement = NaviampCoreHomeSupplementSource {
                        NaviampCoreHomeSupplement(
                            sourceId = storage.mediaSources.latestMediaSource()?.id,
                            recentRadioStreams = settingsStore.loadRecentRadioStreams(),
                            recentInternetRadioStations = internetRadioRecents.current(),
                        )
                    },
                    artistDiscovery = providerArtistDiscoveryServices(
                        providerSource = sessions.providerSource,
                        sourceId = { storage.mediaSources.latestMediaSource()?.id },
                        libraryIndex = storage.libraryIndex,
                        nowEpochMillis = nowEpochMillis,
                    ),
                    providerResponses = ProviderResponseService(
                        ProviderResponseCacheService(storage.providerResponses, nowEpochMillis),
                    ),
                    homeLibrary = localLibraryHomeRepository(storage.libraryIndex),
                    playlistSupplement = naviampCorePlaylistBrowseSupplementSource(
                        recentPlaylistIds = settingsStore::loadRecentPlaylistIds,
                        sourceId = { storage.mediaSources.latestMediaSource()?.id },
                        keepDownloadedRepository = storage.keepDownloaded,
                    ),
                ),
                settings = serviceDefaults.settings.copy(
                    interfaceSettings = NaviampCoreInterfaceSettingsStore(settingsStore::saveInterfaceSettings),
                    cacheSettings = NaviampCoreCacheSettingsPort { requested ->
                        requested.normalized().also { effective ->
                            activeCacheSettings = effective
                            settingsStore.saveCacheSettings(effective)
                        }
                    },
                    maintenance = naviampCoreRepositoryMaintenancePort(
                        repository = storage.maintenance,
                        libraryIndex = storage.libraryIndex,
                        sourceId = { storage.mediaSources.latestMediaSource()?.id },
                    ),
                ),
                downloads = repositoryNaviampCoreDownloadServices(
                    downloadRepository = storage.audioStore,
                    replacementRepository = storage.audioStore,
                    keepDownloadedRepository = storage.keepDownloaded,
                    toCoreDownload = { stored ->
                        NaviampCoreDownloadedTrack(
                            storageId = stored.path.toString(),
                            track = stored.track,
                            sizeBytes = stored.sizeBytes,
                            qualityLabel = stored.qualityKey,
                        )
                    },
                    isStoredDownloadAvailable = { stored -> Files.exists(stored.path) },
                    storageStats = {
                        storage.maintenance.stats().let { stats ->
                            NaviampCoreDownloadStorageSnapshot(
                                audioCacheCount = stats.audioCount,
                                audioCacheBytes = stats.audioBytes,
                                pendingProviderActionCount = stats.pendingProviderActionCount,
                            )
                        }
                    },
                ),
                playlists = serviceDefaults.playlists.copy(
                    history = naviampCorePlaylistHistoryPort(settingsStore::saveRecentPlaylistIds),
                ),
                radio = serviceDefaults.radio.copy(
                    recents = internetRadioRecents,
                    generatedRecents = NaviampCoreGeneratedRadioRecentsPort(
                        load = settingsStore::loadRecentRadioStreams,
                        save = settingsStore::saveRecentRadioStreams,
                    ),
                ),
                diagnostics = DesktopCoreDiagnosticsPort(
                    storageStats = storage.maintenance::stats,
                    nowEpochMillis = nowEpochMillis,
                ),
            )
            val shellCapabilities = DesktopCapabilityPresentation.toShellCapabilitiesUi(
                playbackEngine = engine,
                sonicSimilarityAvailable = false,
            )
            val storageStats = storage.maintenance.stats()
            val downloadLocation = NaviampStorageLocationUi(
                id = "active-download-directory",
                label = if (initialCacheSettings.customDownloadDirectory == null) "App storage" else "Custom",
                path = downloadDirectory.toAbsolutePath().toString(),
            )
            val audioCacheLocation = NaviampStorageLocationUi(
                id = "active-audio-cache-directory",
                label = if (initialCacheSettings.customAudioCacheDirectory == null) "App storage" else "Custom",
                path = audioCacheDirectory.toAbsolutePath().toString(),
            )
            val initialState = NaviampCoreInitialState().let { initial ->
                initial.copy(
                    product = initial.product.copy(
                        shell = initial.product.shell.copy(
                            general = initial.product.shell.general.copy(interfaceSettings = initialInterfaceSettings),
                            playback = initial.product.shell.playback.copy(settings = initialPlaybackSettings),
                            cache = initialCacheSettings.toCacheSettingsUi(
                                stats = storageStats,
                                capabilities = shellCapabilities,
                            ).copy(
                                downloadLocations = listOf(downloadLocation),
                                audioCacheLocations = listOf(audioCacheLocation),
                                selectedDownloadLocationId = downloadLocation.id,
                                selectedAudioCacheLocationId = audioCacheLocation.id,
                            ),
                            shellChrome = initial.product.shell.shellChrome.copy(
                                selectedVisualizer = initialVisualizer,
                            ),
                        ),
                    ),
                )
            }
            return DesktopV2Composition(
                environment = desktopNaviampCoreEnvironment(
                    services = services,
                    providerSessions = sessions,
                    settingsSync = sync,
                    initialState = initialState,
                    shellCapabilities = shellCapabilities,
                    audioOutputDeviceSelectionAvailable =
                        (engine as? AudioOutputDevicePlaybackEngine)
                            ?.supportsAudioOutputDeviceSelection == true,
                    audioOutputDevices =
                        (engine as? AudioOutputDevicePlaybackEngine)?.outputDevices().orEmpty(),
                    onAsyncFailure = { command, failure ->
                        System.err.println("Naviamp Core command failed: $command: ${failure.message}")
                        failure.printStackTrace()
                    },
                ),
                engine = engine,
                storage = storage,
            )
        }
    }
}

private fun desktopV2DataDirectory(): Path {
    val home = Path.of(System.getProperty("user.home"))
    val os = System.getProperty("os.name").lowercase()
    return when {
        os.contains("mac") || os.contains("darwin") ->
            home.resolve("Library/Application Support/Naviamp")
        os.contains("win") ->
            Path.of(System.getenv("APPDATA") ?: home.resolve("AppData/Roaming").toString()).resolve("Naviamp")
        else -> home.resolve(".local/share/naviamp")
    }
}

private const val DesktopSettingsSyncDeviceId = "desktop"
