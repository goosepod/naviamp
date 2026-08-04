package app.naviamp.ios

import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import app.cash.sqldelight.db.SqlDriver
import app.naviamp.ios.platform.IosCapabilityPresentation
import app.naviamp.ios.platform.IosCoreExternalUriPort
import app.naviamp.ios.platform.IosHomeDateSource
import app.naviamp.ios.playback.IosNativePlaybackIntegration
import app.naviamp.ios.playback.IosBassAudioBackend
import app.naviamp.ios.playback.IosAudioTagReader
import app.naviamp.ios.playback.createIosBassPlaybackEngine
import app.naviamp.ios.playback.iosPlaybackLocalFilePath
import app.naviamp.ios.settings.IosCoreSettingsValueStore
import app.naviamp.ios.settings.IosCoreSettingsSyncPort
import app.naviamp.ios.storage.IosAudioByteStore
import app.naviamp.ios.storage.IosAudioFileSystem
import app.naviamp.ios.storage.IosPlaybackAudioAssets
import app.naviamp.ios.storage.toPlaybackLocalAudio
import app.naviamp.presentation.NaviampCoreApp
import app.naviamp.presentation.NaviampCoreEnvironment
import app.naviamp.presentation.createNaviampCore
import app.naviamp.presentation.externalPlaybackBridge
import app.naviamp.presentation.naviampCoreSettingsValueCatalog
import app.naviamp.presentation.naviampCorePlaybackServiceCatalog
import app.naviamp.presentation.naviampCoreStoredServiceCatalog
import app.naviamp.presentation.naviampNowEpochMillis
import app.naviamp.presentation.naviampNowIso8601
import app.naviamp.presentation.NaviampCoreDownloadedTrack
import app.naviamp.presentation.NaviampCoreDownloadStorageSnapshot
import app.naviamp.presentation.repositoryNaviampCoreDownloadServices
import app.naviamp.presentation.withStorageBackedSettings
import app.naviamp.domain.cache.AudioByteStoreService
import app.naviamp.domain.cache.CachedLyricsSidecarRepository
import app.naviamp.domain.cache.LyricsSidecarCacheService
import app.naviamp.domain.cache.SidecarStatusService
import app.naviamp.domain.lyrics.LrclibLyricsProvider
import app.naviamp.domain.network.KtorSharedHttpClient
import app.naviamp.domain.waveform.BassAudioWaveformAnalyzer
import app.naviamp.provider.navidrome.NavidromeCoreProviderSessionPort
import app.naviamp.provider.navidrome.navidromeProviderSessionOpener
import app.naviamp.storage.IosStorageDriverFactory
import app.naviamp.storage.NaviampStorageDatabase
import app.naviamp.storage.StorageCoreRepositoryCatalog
import app.naviamp.storage.StorageAudioStore
import app.naviamp.storage.StorageCredentialProtector
import app.naviamp.storage.StorageDatabaseLocation
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.ui.resetIosPlatformCoverArtByteLoader
import app.naviamp.ui.setIosPlatformCoverArtByteLoader
import app.naviamp.ui.NaviampStorageLocationUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import platform.UIKit.UIViewController

/**
 * Process-level iOS owner for native database lifetime and the shared Core environment.
 * Product state, actions, navigation, provider policy, and UI remain in common code.
 */
class NaviampIosApplication(
    applicationSupportDirectory: String,
    credentialProtector: StorageCredentialProtector,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val databaseLocation = StorageDatabaseLocation(applicationSupportDirectory)
    private val driver: SqlDriver = IosStorageDriverFactory(
        databaseLocation,
    ).createDriver()
    private val database = NaviampStorageDatabase(driver)
    private val settingsValueStore = IosCoreSettingsValueStore()
    private val settings = naviampCoreSettingsValueCatalog(settingsValueStore)
    private var contentViewController: UIViewController? = null
    private val settingsSyncPort = IosCoreSettingsSyncPort(
        settingsStore = settingsValueStore,
        presenter = { contentViewController },
    )
    private var cacheSettings = settings.storedSettings.loadCache()
    private val audioCacheDirectory = "${databaseLocation.directoryPath}/audio-cache"
    private val downloadDirectory = "${databaseLocation.directoryPath}/downloads"
    private val repositories = StorageCoreRepositoryCatalog(
        database = database,
        credentialProtector = credentialProtector,
        nowEpochMillis = ::naviampNowEpochMillis,
        databaseLabel = "${databaseLocation.directoryPath}/${databaseLocation.fileName}",
        deleteKnownAudioCacheFile = { IosAudioFileSystem.deleteKnownRegularFile(audioCacheDirectory, it) },
        deleteKnownDownloadFile = { IosAudioFileSystem.deleteKnownRegularFile(downloadDirectory, it) },
        maxAudioBytes = cacheSettings.maxAudioCacheBytes,
    )
    private val httpClient = KtorSharedHttpClient()
    private val audioStore = StorageAudioStore(
        queries = database.naviampStorageQueries,
        audioCacheByteStoreService = AudioByteStoreService(IosAudioByteStore(audioCacheDirectory), httpClient),
        downloadAudioByteStoreService = AudioByteStoreService(IosAudioByteStore(downloadDirectory), httpClient),
        nowEpochMillis = ::naviampNowEpochMillis,
        cachedAudioFileExists = { IosAudioFileSystem.isStoredRegularFile(audioCacheDirectory, it) },
        downloadedAudioFileExists = { IosAudioFileSystem.isStoredRegularFile(downloadDirectory, it) },
        deleteKnownAudioCacheFile = { IosAudioFileSystem.deleteKnownRegularFile(audioCacheDirectory, it) },
        deleteKnownDownloadFile = { IosAudioFileSystem.deleteKnownRegularFile(downloadDirectory, it) },
        workContext = Dispatchers.Default,
        maxAudioCacheBytes = cacheSettings.maxAudioCacheBytes,
        protectedTrackIds = repositories::protectedCachedAudioTrackIds,
    )
    private val sessions = NavidromeCoreProviderSessionPort(
        mediaSources = repositories.mediaSources,
        initialSource = repositories.mediaSources.latestMediaSource(),
        sessionOpener = navidromeProviderSessionOpener(
            cacheMaintenanceRepository = repositories.maintenance,
            providerMediaSourceRepository = repositories.mediaSources,
            nowEpochMillis = ::naviampNowEpochMillis,
        ),
    )
    init {
        setIosPlatformCoverArtByteLoader(::loadProviderArtwork)
    }
    private val playbackEngine = createIosBassPlaybackEngine()
    private val verifyProviderNetworkCertificates = {
        repositories.mediaSources.latestMediaSource()?.tlsSettings?.insecureSkipTlsVerification != true
    }
    private val waveformBass = IosBassAudioBackend()
    private val playback = naviampCorePlaybackServiceCatalog(
        scope = scope,
        engine = playbackEngine,
        providerSource = sessions.providerSource,
        initialPlaybackSettings = settings.storedSettings.loadPlayback(),
        persistPlaybackSettings = settings.savePlayback,
        cacheSettings = { cacheSettings },
        activeSourceId = { repositories.mediaSources.latestMediaSource()?.id },
        audioAssets = IosPlaybackAudioAssets(
            downloads = audioStore,
            cache = audioStore,
            downloadDirectoryPath = downloadDirectory,
            cacheDirectoryPath = audioCacheDirectory,
        ),
        cacheAudio = { sourceId, provider, track, quality ->
            audioStore.cacheAudioTrack(sourceId, provider, track, quality).toPlaybackLocalAudio(audioCacheDirectory)
        },
        waveformRepository = repositories.audioWaveforms,
        waveformAnalyzer = BassAudioWaveformAnalyzer(
            bass = waveformBass,
            verifyNetworkCertificates = verifyProviderNetworkCertificates,
            localFilePath = ::iosPlaybackLocalFilePath,
        ),
        audioTagReader = IosAudioTagReader(),
        lyricsRepository = CachedLyricsSidecarRepository(
            cache = LyricsSidecarCacheService(repositories.lyricsSidecars, ::naviampNowEpochMillis),
            onlineProvider = LrclibLyricsProvider(KtorSharedHttpClient()),
        ),
        lyricsOffsetRepository = repositories.lyricsOffsets,
        sidecarStatusRepository = SidecarStatusService(repositories.sidecarStatuses, ::naviampNowEpochMillis),
        playbackSessionRepository = repositories.playbackSessions,
        saveVisualizerSettings = settings.storedSettings.saveVisualizer,
        verifyProviderNetworkCertificates = verifyProviderNetworkCertificates,
        prepareWaveformAnalysis = { waveformBass.init().getOrThrow() },
        waveformWorkContext = Dispatchers.Default,
    )
    private val downloads = repositoryNaviampCoreDownloadServices(
        downloadRepository = audioStore,
        replacementRepository = audioStore,
        keepDownloadedRepository = repositories.keepDownloaded,
        toCoreDownload = { stored ->
            NaviampCoreDownloadedTrack(
                storageId = stored.filePath,
                track = stored.track,
                sizeBytes = stored.sizeBytes,
                qualityLabel = stored.qualityKey,
            )
        },
        isStoredDownloadAvailable = { stored ->
            IosAudioFileSystem.isStoredRegularFile(downloadDirectory, stored.filePath)
        },
        storageStats = {
            repositories.maintenance.stats().let { stats ->
                NaviampCoreDownloadStorageSnapshot(
                    audioCacheCount = stats.audioCount,
                    audioCacheBytes = stats.audioBytes,
                    pendingProviderActionCount = stats.pendingProviderActionCount,
                )
            }
        },
    )
    private val storedCatalog = naviampCoreStoredServiceCatalog(
        providerSessions = sessions,
        providerSource = sessions.providerSource,
        playback = playback,
        downloads = downloads,
        playbackEngine = playbackEngine,
        settingsSyncPort = settingsSyncPort,
        settings = settings.storedSettings.withStorageBackedSettings(
            radioDjPresetRepository = repositories.radioDjPresets,
            onCacheSettingsSaved = { effective -> cacheSettings = effective },
        ),
        repositories = app.naviamp.presentation.NaviampCoreStoredRepositories(
            mediaSources = repositories.mediaSources,
            providerMediaSources = repositories.mediaSources,
            libraryIndex = repositories.libraryIndex,
            providerResponses = repositories.providerResponses,
            keepDownloaded = repositories.keepDownloaded,
            radioDjPresets = repositories.radioDjPresets,
            maintenance = repositories.maintenance,
            pendingProviderActions = repositories.pendingProviderActions,
            updateAudioCacheLimit = audioStore::updateAudioCacheLimit,
        ),
        externalUri = IosCoreExternalUriPort(),
        homeDate = IosHomeDateSource,
        shellCapabilities = IosCapabilityPresentation.shell(playbackEngine),
        settingsSyncDeviceId = "ios",
        downloadLocations = listOf(
            NaviampStorageLocationUi("app-storage", "App storage", downloadDirectory),
        ),
        audioCacheLocations = listOf(
            NaviampStorageLocationUi("app-storage", "App storage", audioCacheDirectory),
        ),
        selectedDownloadLocationId = "app-storage",
        selectedAudioCacheLocationId = "app-storage",
        sourceId = { repositories.mediaSources.latestMediaSource()?.id },
        clockEpochMillis = ::naviampNowEpochMillis,
        favoritedAtIso8601 = ::naviampNowIso8601,
    )
    private val environment = NaviampCoreEnvironment(
        services = storedCatalog.services,
        initialState = storedCatalog.initialState,
        actionAvailability = IosCapabilityPresentation.actionAvailability,
        onAsyncFailure = { command, cause ->
            throw IllegalStateException("iOS Core command failed: $command", cause)
        },
    )
    private val core = createNaviampCore(scope, environment)
    private val nativePlayback = IosNativePlaybackIntegration(
        scope = scope,
        bridge = core.externalPlaybackBridge(),
        artworkBytes = ::loadProviderArtwork,
    )

    fun viewController(): UIViewController = contentViewController ?: ComposeUIViewController {
        NaviampCoreApp(
            core = core,
            modifier = Modifier.safeDrawingPadding().imePadding(),
            applicationUpdateChecker = environment.applicationUpdateChecker,
        )
    }.also { contentViewController = it }

    fun close() {
        resetIosPlatformCoverArtByteLoader()
        nativePlayback.close()
        playbackEngine.release()
        scope.cancel()
        driver.close()
    }

    private suspend fun loadProviderArtwork(url: String): ByteArray? =
        (sessions.currentProvider() as? NavidromeProvider)
            ?.takeIf { provider -> provider.ownsUrl(url) }
            ?.bytes(url)
}
