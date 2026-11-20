package app.naviamp.bootstrap

import app.naviamp.presentation.NaviampCoreEnvironment
import app.naviamp.presentation.createNaviampCore
import app.naviamp.presentation.naviampCoreSettingsValueCatalog
import app.naviamp.presentation.naviampCoreProviderIdentitySettingsMigrationRepository
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
import app.naviamp.domain.cache.MaximumPersistentArtworkCacheBytes
import app.naviamp.domain.lyrics.naviampOnlineLyricsProviders
import app.naviamp.domain.network.KtorSharedHttpClient
import app.naviamp.domain.waveform.BassAudioWaveformAnalyzer
import app.naviamp.provider.navidrome.NavidromeCoreProviderSessionPort
import app.naviamp.provider.navidrome.navidromeProviderSessionOpener
import app.naviamp.provider.navidrome.subsonicFamilyProviderSessionRouter
import app.naviamp.provider.navidrome.createDefaultNavidromeKtorClient
import app.naviamp.provider.jellyfin.JellyfinCoreProviderSessionPort
import app.naviamp.provider.jellyfin.JellyfinSessionService
import app.naviamp.provider.jellyfin.JellyfinSessionServiceFactory
import app.naviamp.provider.jellyfin.KtorJellyfinHttpClient
import app.naviamp.provider.jellyfin.jellyfinClientIdentity
import app.naviamp.provider.jellyfin.jellyfinProviderSessionOpener
import app.naviamp.domain.provider.ProviderIdJellyfin
import app.naviamp.presentation.NaviampCoreProviderSessionPort
import app.naviamp.presentation.NaviampCoreProviderSessionRoute
import app.naviamp.storage.NaviampStorageDatabase
import app.naviamp.storage.StorageCoreRepositoryCatalog
import app.naviamp.storage.StorageAudioStore
import app.naviamp.storage.StorageCredentialProtector
import app.naviamp.storage.StorageImageCacheRepository
import app.naviamp.storage.StorageObjectByteStore
import app.naviamp.ui.NaviampStorageLocationUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import app.naviamp.domain.audio.AudioTagReader
import app.naviamp.domain.bass.BassAudioBackend
import app.naviamp.domain.network.SharedHttpClient
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.storage.StorageAudioDirectory
import app.naviamp.storage.StoragePlaybackAudioAssets
import app.naviamp.storage.StorageRecoveryState
import app.naviamp.presentation.NaviampCoreSettingsValueStore
import app.naviamp.presentation.NaviampCoreSettingsSyncPort
import app.naviamp.presentation.NaviampCoreCertificateFilePort
import app.naviamp.presentation.NaviampCoreExternalUriPort
import app.naviamp.presentation.NaviampCoreHomeDateSource
import app.naviamp.presentation.NaviampCoreActionAvailability
import app.naviamp.ui.NaviampShellCapabilitiesUi


/** Complete portable assembly shared by thin hosts. Native resource lifetime remains with the host. */
class NaviampStoredApplication(
    scope: CoroutineScope,
    database: NaviampStorageDatabase,
    credentialProtector: StorageCredentialProtector,
    settingsValueStore: NaviampCoreSettingsValueStore,
    settingsSyncPort: NaviampCoreSettingsSyncPort,
    certificateFiles: NaviampCoreCertificateFilePort?,
    databaseLabel: String,
    private val audioCacheDirectory: StorageAudioDirectory,
    private val downloadDirectory: StorageAudioDirectory,
    private val playbackEngine: PlaybackEngine,
    waveformBass: BassAudioBackend,
    localFilePath: (String) -> String?,
    audioTagReader: AudioTagReader,
    externalUri: NaviampCoreExternalUriPort,
    homeDate: NaviampCoreHomeDateSource,
    shellCapabilities: NaviampShellCapabilitiesUi,
    actionAvailability: NaviampCoreActionAvailability,
    providerDeviceId: String,
    providerDeviceName: String,
    settingsSyncDeviceId: String,
    downloadLocations: List<NaviampStorageLocationUi>,
    audioCacheLocations: List<NaviampStorageLocationUi>,
    selectedDownloadLocationId: String?,
    selectedAudioCacheLocationId: String?,
    private val httpClient: SharedHttpClient = KtorSharedHttpClient(),
    recoveryState: StorageRecoveryState? = null,
    onAsyncFailure: ((app.naviamp.presentation.NaviampCoreCommand, Throwable) -> Unit)? = null,
) {
    init {
        require(recoveryState == null || (settingsValueStore as? NaviampRecoverySettingsValueStore)?.recovery === recoveryState)
    }
    private val settings = naviampCoreSettingsValueCatalog(settingsValueStore)
    private var cacheSettings = settings.storedSettings.loadCache()
    private val repositories = StorageCoreRepositoryCatalog(
        database = database,
        credentialProtector = credentialProtector,
        nowEpochMillis = ::naviampNowEpochMillis,
        databaseLabel = databaseLabel,
        deleteKnownAudioCacheFile = audioCacheDirectory::delete,
        deleteKnownDownloadFile = downloadDirectory::delete,
        maxAudioBytes = cacheSettings.maxAudioCacheBytes,
        recoveryState = recoveryState,
    )
    private val audioStore = StorageAudioStore(
        queries = database.naviampStorageQueries,
        audioCacheByteStoreService = AudioByteStoreService(audioCacheDirectory.byteStore, httpClient),
        downloadAudioByteStoreService = AudioByteStoreService(downloadDirectory.byteStore, httpClient),
        nowEpochMillis = ::naviampNowEpochMillis,
        cachedAudioFileExists = audioCacheDirectory::exists,
        downloadedAudioFileExists = downloadDirectory::exists,
        deleteKnownAudioCacheFile = audioCacheDirectory::delete,
        deleteKnownDownloadFile = downloadDirectory::delete,
        workContext = Dispatchers.Default,
        maxAudioCacheBytes = cacheSettings.maxAudioCacheBytes,
        protectedTrackIds = repositories::protectedCachedAudioTrackIds,
    )
    private val jellyfinServices = JellyfinSessionServiceFactory { tlsSettings ->
        JellyfinSessionService(
            httpClient = KtorJellyfinHttpClient(createDefaultNavidromeKtorClient(tlsSettings)),
            identity = jellyfinClientIdentity(
                deviceId = providerDeviceId,
                deviceName = providerDeviceName,
            ),
        )
    }
    private val sessions: NaviampCoreProviderSessionPort = subsonicFamilyProviderSessionRouter(
        NavidromeCoreProviderSessionPort(
            mediaSources = repositories.mediaSources,
            initialSource = repositories.mediaSources.latestMediaSource(),
            sessionOpener = navidromeProviderSessionOpener(
                cacheMaintenanceRepository = repositories.maintenance,
                providerMediaSourceRepository = repositories.mediaSources,
                nowEpochMillis = ::naviampNowEpochMillis,
            ),
            identitySettingsMigrations = naviampCoreProviderIdentitySettingsMigrationRepository(settingsValueStore),
        ),
        additionalRoutes = listOf(
            NaviampCoreProviderSessionRoute(
                providerIds = setOf(ProviderIdJellyfin),
                sessionPort = JellyfinCoreProviderSessionPort(
                    mediaSources = repositories.mediaSources,
                    initialSource = repositories.mediaSources.latestMediaSource(),
                    sessionOpener = jellyfinProviderSessionOpener(
                        sessionServices = jellyfinServices,
                        cacheMaintenanceRepository = repositories.maintenance,
                        providerMediaSourceRepository = repositories.mediaSources,
                        nowEpochMillis = ::naviampNowEpochMillis,
                    ),
                    sessionServices = jellyfinServices,
                    deviceId = providerDeviceId,
                ),
            ),
        ),
    )
    private val artworkCache = StorageImageCacheRepository(
        store = StorageObjectByteStore(
            queries = database.naviampStorageQueries,
            nowMillis = ::naviampNowEpochMillis,
            maxImageCacheBytes = MaximumPersistentArtworkCacheBytes,
            workContext = Dispatchers.Default,
        ),
        fetch = { url ->
            sessions.currentProvider()?.bytesForOwnedUrl(url)
                ?: httpClient.getBytes(url)
                ?: throw IllegalStateException("Could not load artwork.")
        },
    )
    private val verifyProviderNetworkCertificates = {
        repositories.mediaSources.latestMediaSource()?.tlsSettings?.insecureSkipTlsVerification != true
    }
    private val playback = naviampCorePlaybackServiceCatalog(
        scope = scope,
        engine = playbackEngine,
        providerSource = sessions.providerSource,
        initialPlaybackSettings = settings.storedSettings.loadPlayback(),
        persistPlaybackSettings = settings.savePlayback,
        cacheSettings = { cacheSettings },
        activeSourceId = { repositories.mediaSources.latestMediaSource()?.id },
        audioAssets = StoragePlaybackAudioAssets(
            downloads = audioStore,
            cache = audioStore,
            downloadDirectory = downloadDirectory,
            cacheDirectory = audioCacheDirectory,
        ),
        cacheAudio = { sourceId, provider, track, quality ->
            audioStore.cacheAudioTrack(sourceId, provider, track, quality).let {
                audioCacheDirectory.playbackAudio(it.filePath, it.sizeBytes, it.streamQuality)
            }
        },
        waveformRepository = repositories.audioWaveforms,
        waveformAnalyzer = BassAudioWaveformAnalyzer(
            bass = waveformBass,
            verifyNetworkCertificates = verifyProviderNetworkCertificates,
            localFilePath = localFilePath,
        ),
        audioTagReader = audioTagReader,
        lyricsRepository = CachedLyricsSidecarRepository(
            cache = LyricsSidecarCacheService(repositories.lyricsSidecars, ::naviampNowEpochMillis),
            onlineProviders = naviampOnlineLyricsProviders(httpClient),
        ),
        lyricsOffsetRepository = repositories.lyricsOffsets,
        sidecarStatusRepository = SidecarStatusService(repositories.sidecarStatuses, ::naviampNowEpochMillis),
        playbackSessionRepository = repositories.playbackSessions,
        playbackProfileRepository = repositories.playbackProfiles,
        saveVisualizerSettings = settings.storedSettings.saveVisualizer,
        verifyProviderNetworkCertificates = verifyProviderNetworkCertificates,
        prepareWaveformAnalysis = { waveformBass.init().getOrThrow() },
        waveformWorkContext = Dispatchers.Default,
    )
    private val downloads = repositoryNaviampCoreDownloadServices(
        downloadRepository = audioStore,
        replacementRepository = audioStore,
        keepDownloadedRepository = repositories.keepDownloaded,
        artworkCacheRepository = artworkCache,
        toCoreDownload = { stored ->
            NaviampCoreDownloadedTrack(
                storageId = stored.filePath,
                track = stored.track,
                sizeBytes = stored.sizeBytes,
                qualityLabel = stored.qualityKey,
            )
        },
        isStoredDownloadAvailable = { stored ->
            downloadDirectory.exists(stored.filePath)
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
        certificateFiles = certificateFiles,
        settingsPersistenceTransaction = { block ->
            if (recoveryState == null) block() else recoveryState.transaction(database.naviampStorageQueries, block)
        },
        persistImportedPlayback = if (recoveryState == null) null else settings.savePlayback,
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
            discoveryAcquisitions = repositories.discoveryAcquisitions,
            updateAudioCacheLimit = audioStore::updateAudioCacheLimit,
        ),
        externalUri = externalUri,
        homeDate = homeDate,
        shellCapabilities = shellCapabilities,
        settingsSyncDeviceId = settingsSyncDeviceId,
        downloadLocations = downloadLocations,
        audioCacheLocations = audioCacheLocations,
        selectedDownloadLocationId = selectedDownloadLocationId,
        selectedAudioCacheLocationId = selectedAudioCacheLocationId,
        sourceId = { repositories.mediaSources.latestMediaSource()?.id },
        clockEpochMillis = ::naviampNowEpochMillis,
        favoritedAtIso8601 = ::naviampNowIso8601,
    )
    val environment = NaviampCoreEnvironment(
        services = storedCatalog.services.let { services ->
            services.copy(content = services.content.copy(
                artwork = app.naviamp.presentation.NaviampCoreArtworkServices(artworkCache, httpClient),
            ))
        },
        initialState = storedCatalog.initialState,
        actionAvailability = actionAvailability,
    ).let { if (onAsyncFailure == null) it else it.copy(onAsyncFailure = onAsyncFailure) }
    val core = createNaviampCore(scope, environment)
    private val releaseArtwork = app.naviamp.ui.bindNaviampCoverArtByteLoader(::loadProviderArtwork)
    private var closed = false

    fun close() {
        if (closed) return
        closed = true
        try { core.close() } finally { releaseArtwork() }
    }

    suspend fun loadProviderArtwork(url: String): ByteArray? = runCatching {
        val provider = sessions.currentProvider()
        if (provider == null) {
            artworkCache.imageBytes(url)
        } else {
            artworkCache.imageBytesForProvider(provider, url) {
                provider.bytesForOwnedUrl(url)
                    ?: httpClient.getBytes(url)
                    ?: throw IllegalStateException("Could not load artwork.")
            }
        }
    }.getOrNull()
}
