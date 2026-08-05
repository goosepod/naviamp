package app.naviamp.desktop

import app.naviamp.desktop.platform.desktopCoreDiagnosticsPort
import app.naviamp.desktop.playback.bass.DesktopBassPlaybackEngineRuntime
import app.naviamp.desktop.playback.bass.loadDesktopBassAudioBackend
import app.naviamp.desktop.settings.DesktopCoreSettingsSyncPort
import app.naviamp.desktop.settings.DesktopCoreSettingsValueStore
import app.naviamp.desktop.settings.defaultDesktopDataDirectory
import app.naviamp.domain.cache.CachedLyricsSidecarRepository
import app.naviamp.domain.cache.LyricsSidecarCacheService
import app.naviamp.domain.cache.SidecarStatusService
import app.naviamp.domain.home.HomeDate
import app.naviamp.domain.lyrics.naviampOnlineLyricsProviders
import app.naviamp.domain.network.KtorSharedHttpClient
import app.naviamp.domain.playback.AudioOutputDevicePlaybackEngine
import app.naviamp.domain.playback.CoreBassPlaybackEngine
import app.naviamp.domain.playback.ReleasablePlaybackEngine
import app.naviamp.presentation.NaviampCoreDownloadStorageSnapshot
import app.naviamp.presentation.NaviampCoreDownloadedTrack
import app.naviamp.presentation.NaviampCoreHomeDateSource
import app.naviamp.presentation.NaviampCoreStoredRepositories
import app.naviamp.presentation.migrateLegacyNaviampPlaybackSession
import app.naviamp.presentation.naviampCorePlaybackServiceCatalog
import app.naviamp.presentation.naviampCoreSettingsValueCatalog
import app.naviamp.presentation.naviampCoreStoredServiceCatalog
import app.naviamp.presentation.repositoryNaviampCoreDownloadServices
import app.naviamp.presentation.toShellCapabilitiesUi
import app.naviamp.presentation.withStorageBackedSettings
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.storage.StorageDatabaseLocation
import app.naviamp.ui.NaviampStorageLocationUi
import app.naviamp.ui.jvmGeneratedCoverArtBytes
import app.naviamp.ui.resetJvmPlatformCoverArtByteLoader
import app.naviamp.ui.setJvmPlatformCoverArtByteLoader
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/** Owns only Desktop filesystem/native resources required by the shared Core app. */
internal class DesktopComposition private constructor(
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
        fun create(scope: CoroutineScope): DesktopComposition {
            val nowEpochMillis = System::currentTimeMillis
            val dataDirectory = defaultDesktopDataDirectory()
            Files.createDirectories(dataDirectory)
            val settingsValues = DesktopCoreSettingsValueStore()
            val settingsCatalog = naviampCoreSettingsValueCatalog(settingsValues)
            var cacheSettings = settingsCatalog.storedSettings.loadCache()
            val audioCacheDirectory = cacheSettings.customAudioCacheDirectory
                ?.let { path -> runCatching { Path.of(path) }.getOrNull() }
                ?: dataDirectory.resolve("audio-cache")
            val downloadDirectory = cacheSettings.customDownloadDirectory
                ?.let { path -> runCatching { Path.of(path) }.getOrNull() }
                ?: dataDirectory.resolve("downloads")
            val storage = DesktopStorageRepositories.open(
                location = StorageDatabaseLocation(dataDirectory.toString()),
                audioCacheDirectory = audioCacheDirectory,
                downloadDirectory = downloadDirectory,
                nowEpochMillis = nowEpochMillis,
                maxAudioBytes = cacheSettings.maxAudioCacheBytes,
                legacyDatabaseFilesOnReset = listOf(
                    dataDirectory.resolve("cache.db"),
                    dataDirectory.resolve("cache.db-wal"),
                    dataDirectory.resolve("cache.db-shm"),
                    dataDirectory.resolve("storage.db"),
                    dataDirectory.resolve("storage.db-wal"),
                    dataDirectory.resolve("storage.db-shm"),
                ),
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
            storage.mediaSources.latestMediaSource()?.id?.let { sourceId ->
                migrateLegacyNaviampPlaybackSession(
                    values = settingsValues,
                    sourceId = sourceId,
                    loadCurrent = storage.playbackSessions::loadPlaybackSession,
                    save = storage.playbackSessions::savePlaybackSession,
                )
            }
            val playbackAudioAssets = DesktopPlaybackAudioAssets(
                downloadRepository = storage.audioStore,
                audioCacheRepository = storage.audioStore,
            )
            val playback = naviampCorePlaybackServiceCatalog(
                scope = scope,
                engine = engine,
                providerSource = sessions.providerSource,
                initialPlaybackSettings = settingsCatalog.storedSettings.loadPlayback(),
                persistPlaybackSettings = settingsCatalog.savePlayback,
                cacheSettings = { cacheSettings },
                activeSourceId = { storage.mediaSources.latestMediaSource()?.id },
                audioAssets = playbackAudioAssets,
                cacheAudio = { sourceId, provider, track, quality ->
                    storage.audioStore.cacheAudioTrack(sourceId, provider, track, quality)
                        .filePath
                        .let(Path::of)
                        .toPlaybackLocalAudio()
                },
                waveformRepository = storage.audioWaveforms,
                waveformAnalyzer = DesktopAudioWaveformAnalyzer(),
                audioTagReader = DesktopAudioTagReader(),
                lyricsRepository = CachedLyricsSidecarRepository(
                    cache = LyricsSidecarCacheService(storage.lyricsSidecars, nowEpochMillis),
                    onlineProviders = naviampOnlineLyricsProviders(sharedHttpClient, nowEpochMillis),
                ),
                lyricsOffsetRepository = storage.lyricsOffsets,
                sidecarStatusRepository = SidecarStatusService(storage.sidecarStatuses, nowEpochMillis),
                playbackSessionRepository = storage.playbackSessions,
                saveVisualizerSettings = settingsCatalog.storedSettings.saveVisualizer,
                waveformWorkContext = Dispatchers.IO,
            )
            val downloads = repositoryNaviampCoreDownloadServices(
                downloadRepository = storage.audioStore,
                replacementRepository = storage.audioStore,
                keepDownloadedRepository = storage.keepDownloaded,
                toCoreDownload = { stored ->
                    NaviampCoreDownloadedTrack(
                        storageId = stored.filePath,
                        track = stored.track,
                        sizeBytes = stored.sizeBytes,
                        qualityLabel = stored.qualityKey,
                    )
                },
                isStoredDownloadAvailable = { stored -> Files.isRegularFile(Path.of(stored.filePath)) },
                storageStats = {
                    storage.maintenance.stats().let { stats ->
                        NaviampCoreDownloadStorageSnapshot(
                            audioCacheCount = stats.audioCount,
                            audioCacheBytes = stats.audioBytes,
                            pendingProviderActionCount = stats.pendingProviderActionCount,
                        )
                    }
                },
            )
            val shellCapabilities = DesktopCapabilityPresentation.toShellCapabilitiesUi(
                playbackEngine = engine,
                sonicSimilarityAvailable = false,
            )
            val settingsSyncPort = DesktopCoreSettingsSyncPort(settingsValues)
            val downloadLocation = NaviampStorageLocationUi(
                id = "active-download-directory",
                label = if (cacheSettings.customDownloadDirectory == null) "App storage" else "Custom",
                path = downloadDirectory.toAbsolutePath().toString(),
            )
            val audioCacheLocation = NaviampStorageLocationUi(
                id = "active-audio-cache-directory",
                label = if (cacheSettings.customAudioCacheDirectory == null) "App storage" else "Custom",
                path = audioCacheDirectory.toAbsolutePath().toString(),
            )
            val catalog = naviampCoreStoredServiceCatalog(
                providerSessions = sessions,
                providerSource = sessions.providerSource,
                playback = playback,
                downloads = downloads,
                playbackEngine = engine,
                settingsSyncPort = settingsSyncPort,
                settings = settingsCatalog.storedSettings.withStorageBackedSettings(
                    radioDjPresetRepository = storage.radioDjPresets,
                    onCacheSettingsSaved = { effective -> cacheSettings = effective },
                ),
                repositories = NaviampCoreStoredRepositories(
                    mediaSources = storage.mediaSources,
                    providerMediaSources = storage.mediaSources,
                    libraryIndex = storage.libraryIndex,
                    providerResponses = storage.providerResponses,
                    keepDownloaded = storage.keepDownloaded,
                    radioDjPresets = storage.radioDjPresets,
                    maintenance = storage.maintenance,
                    pendingProviderActions = storage.pendingProviderActions,
                    updateAudioCacheLimit = storage::updateAudioCacheLimit,
                ),
                externalUri = DesktopExternalUriPort(),
                homeDate = NaviampCoreHomeDateSource {
                    LocalDate.now().let { date -> HomeDate(date.year, date.dayOfYear) }
                },
                shellCapabilities = shellCapabilities,
                settingsSyncDeviceId = DesktopSettingsSyncDeviceId,
                downloadLocations = listOf(downloadLocation),
                audioCacheLocations = listOf(audioCacheLocation),
                selectedDownloadLocationId = downloadLocation.id,
                selectedAudioCacheLocationId = audioCacheLocation.id,
                sourceId = { storage.mediaSources.latestMediaSource()?.id },
                clockEpochMillis = nowEpochMillis,
                favoritedAtIso8601 = { Instant.now().toString() },
                diagnostics = desktopCoreDiagnosticsPort(storage.maintenance::stats, nowEpochMillis),
            )
            return DesktopComposition(
                environment = desktopNaviampCoreEnvironment(
                    services = catalog.services,
                    providerSessions = sessions,
                    initialState = catalog.initialState,
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

private const val DesktopSettingsSyncDeviceId = "desktop"
