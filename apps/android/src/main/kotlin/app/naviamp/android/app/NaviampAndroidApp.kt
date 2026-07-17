package app.naviamp.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.naviamp.app.NaviampPlaybackSessionController
import app.naviamp.app.NaviampApplicationServices
import app.naviamp.app.NaviampCacheMaintenanceController
import app.naviamp.app.NaviampCacheSettingsController
import app.naviamp.app.NaviampApplicationStatusArea
import app.naviamp.app.NaviampApplicationStatusLevel
import app.naviamp.app.NaviampDownloadCoordinator
import app.naviamp.app.NaviampDownloadJobController
import app.naviamp.android.playback.AndroidPlaybackEngine
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.Lyrics
import app.naviamp.domain.app.NaviampRoute
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.home.HomeDate
import app.naviamp.domain.home.HomeService
import app.naviamp.domain.playback.PlaybackQueueController
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.PlaybackVisualizerFrame
import app.naviamp.domain.playback.ReplayGainMode
import app.naviamp.domain.playback.SleepTimerController
import app.naviamp.domain.playback.VisualizerPlaybackEngine
import app.naviamp.domain.playback.label
import app.naviamp.domain.popular.SimilarArtistMatch
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.settings.effectiveForEngine
import app.naviamp.app.NaviampSettingsSyncController
import app.naviamp.app.settingsSyncAutoExportStatus
import app.naviamp.app.settingsSyncImportStatus
import app.naviamp.app.settingsSyncLocationStatus
import app.naviamp.app.settingsSyncReconciliationStatus
import app.naviamp.domain.settings.SettingsSyncDocument
import app.naviamp.domain.settings.SettingsSyncLocalSnapshot
import app.naviamp.domain.settings.SettingsSyncOperationKind
import app.naviamp.domain.settings.SettingsSyncRuntimeState
import app.naviamp.domain.settings.SettingsSyncMirrorDocumentSource
import app.naviamp.domain.settings.VisualizerSettings
import app.naviamp.domain.sonicautoplay.SonicAutoplayService
import app.naviamp.provider.navidrome.NavidromeApiCall
import app.naviamp.provider.navidrome.NavidromeApiCallHistory
import app.naviamp.provider.navidrome.toNavidromeConnection
import app.naviamp.domain.source.visibleServerConnections
import app.naviamp.ui.SharedTrackRowUi
import app.naviamp.ui.NaviampDiagnosticsSectionUi
import app.naviamp.ui.NaviampDiagnosticsUi
import app.naviamp.ui.NaviampLibrarySyncStatusUi
import app.naviamp.ui.NaviampSharedAppShell
import app.naviamp.ui.NaviampRadioArtworkLookupEffect
import app.naviamp.ui.naviampVisualizerFromName
import app.naviamp.ui.NowPlayingUi
import app.naviamp.ui.SharedAlbumDetailUi
import app.naviamp.ui.SharedHomeUi
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.SharedPlaylistDetailUi
import app.naviamp.ui.SharedPlaylistSortMode
import app.naviamp.ui.SharedRoute
import app.naviamp.ui.SharedSearchResultsUi
import app.naviamp.ui.toSharedTrackRowUi
import app.naviamp.ui.toNowPlayingItemUi
import app.naviamp.ui.toPlaylistChoiceUi
import app.naviamp.ui.toSharedAlbumDetailUi
import app.naviamp.ui.toSharedArtistDetailUi
import app.naviamp.ui.toSharedHomeUi
import app.naviamp.ui.toSharedMediaItemUi
import app.naviamp.ui.toSharedPlaylistDetailUi
import app.naviamp.ui.bytesLabel
import app.naviamp.ui.label as streamQualityLabel
import app.naviamp.ui.resetAndroidPlatformCoverArtByteLoader
import app.naviamp.ui.setAndroidPlatformCoverArtByteLoader
import app.naviamp.ui.toSharedSearchResultsUi
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun NaviampAndroidApp(
    openNowPlayingRequest: Int = 0,
    autoPlayMediaIdRequest: String? = null,
    onAutoPlayMediaIdConsumed: () -> Unit = {},
    autoCommandRequest: String? = null,
    onAutoCommandConsumed: () -> Unit = {},
    settingsSyncImportUriRequest: Uri? = null,
    onSettingsSyncImportUriConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dependencies = remember(context) { AndroidAppDependencyStore.get(context) }
    val playbackRuntime = dependencies.playbackRuntime
    val scope = playbackRuntime.scope
    val bassLoadReport = playbackRuntime.bassLoadReport
    val playbackEngine: AndroidPlaybackEngine = playbackRuntime.playbackEngine
    val waveformAnalyzer = playbackRuntime.waveformAnalyzer
    val storage = dependencies.storage
    val playbackSessions = remember(storage) { NaviampPlaybackSessionController(storage) }
    val sidecarStatusRepository = dependencies.sidecarStatusRepository
    val playbackAudioAssets = dependencies.playbackAudioAssets
    val audioMetadataSidecarService = dependencies.audioMetadataSidecarService
    val lyricsSidecarService = dependencies.lyricsSidecarService
    val settingsStore = dependencies.settingsStore
    val savedProviderSource = remember { storage.latestNavidromeSource() }
    val savedMediaSources = remember { storage.mediaSources().visibleServerConnections(savedProviderSource?.id) }
    val savedProviderConnection = savedProviderSource?.toNavidromeConnection()
    val savedConnection = remember { settingsStore.loadConnection(savedProviderConnection) }
    val savedPlaybackSettings = remember {
        val settings = settingsStore.loadPlaybackSettings()
        val storedDjs = storage.radioDjPresets()
        val hydrated = if (storedDjs.isEmpty() && settings.radioDjs.isNotEmpty()) {
            storage.replaceRadioDjPresets(settings.radioDjs)
            settings.copy(radioDjs = storage.radioDjPresets())
        } else {
            settings.copy(radioDjs = storedDjs)
        }
        hydrated.effectiveForEngine(playbackEngine)
    }
    val savedCacheSettings = remember { settingsStore.loadCacheSettings() }
    val savedInterfaceSettings = remember { settingsStore.loadInterfaceSettings() }
    val appState = rememberAndroidAppState(
        savedConnection = savedConnection,
        savedInterfaceSettings = savedInterfaceSettings,
        savedPlaybackSettings = savedPlaybackSettings,
        savedCacheSettings = savedCacheSettings,
        pendingProviderActions = storage,
        savedSourceId = savedProviderSource?.id,
        initialSavedMediaSources = savedMediaSources,
        initialSavedConnectionForLogin = savedProviderConnection,
        initialStorageStats = storage.stats(),
        initialOpenNowPlayingRequest = openNowPlayingRequest,
        initialAutoPlayMediaIdRequest = autoPlayMediaIdRequest,
        initialAutoCommandRequest = autoCommandRequest,
        initialSelectedVisualizer = naviampVisualizerFromName(settingsStore.loadVisualizerSettings().selectedVisualizer),
    )
    val playbackQueueController = remember { PlaybackQueueController(appState.playbackQueue) }
    var onSyncedSettingsChanged: () -> Unit = {}
    with(appState) {
    DisposableEffect(provider) {
        setAndroidPlatformCoverArtByteLoader { url ->
            provider?.takeIf { it.ownsUrl(url) }?.let { activeProvider ->
                dependencies.imageCacheRepository.imageBytes(url) {
                    activeProvider.bytes(url) ?: throw IllegalStateException("Could not download cover art.")
                }
            } ?: dependencies.imageCacheRepository.imageBytes(url)
        }
        onDispose { resetAndroidPlatformCoverArtByteLoader() }
    }
    val popularTracksService = remember(dependencies) {
        dependencies.popularTracksService(
            activeSourceIdProvider = { appState.activeSourceId },
            providerProvider = { appState.provider },
        )
    }
    val similarArtistsService = remember(dependencies) {
        dependencies.similarArtistsService(
            activeSourceIdProvider = { appState.activeSourceId },
            providerProvider = { appState.provider },
        )
    }
    val mediaAppController = remember(appState, storage, popularTracksService) {
        AndroidMediaAppController(
            scope = scope,
            state = appState,
            storage = storage,
            playbackEngine = playbackEngine,
            queueController = playbackQueueController,
            popularTracksService = popularTracksService,
        )
    }
    val playbackQualityController = remember(appState, context) {
        AndroidPlaybackQualityController(context, appState)
    }

    AndroidAppRuntimeEffects(
        state = appState,
        bassLoadReport = bassLoadReport,
        playbackEngine = playbackEngine,
        providerActions = appState.sharedControllers.providerActions,
        openNowPlayingRequest = openNowPlayingRequest,
        autoPlayMediaIdRequest = autoPlayMediaIdRequest,
        autoCommandRequest = autoCommandRequest,
    )

    val nowPlayingSidecarController = remember(appState, dependencies) {
        AndroidNowPlayingSidecarController(
            scope = scope,
            state = appState,
            lyricsSidecarService = lyricsSidecarService,
            audioMetadataSidecarService = audioMetadataSidecarService,
            sidecarStatusRepository = sidecarStatusRepository,
            lyricsOffsetRepository = storage,
            cacheAudioTrack = dependencies::cacheAudioTrack,
            currentStreamQuality = playbackQualityController::currentStreamQuality,
        )
    }
    val playbackReportController = remember(appState) {
        AndroidPlaybackReportController(scope, appState, appState.sharedControllers.providerActions)
    }

    val searchController = remember(appState, storage) { AndroidSearchController(appState, storage) }

    NaviampRadioArtworkLookupEffect(
        station = nowPlayingStation,
        streamMetadata = nowPlayingStreamMetadata,
        provider = provider,
        artworkByKey = radioTrackArtworkByKey,
        onArtworkResolved = { key, artworkUrl ->
            radioTrackArtworkByKey = radioTrackArtworkByKey + (key to artworkUrl)
        },
    )

    val androidPlaylistEngine = remember(
        appState,
        dependencies,
        playbackQueueController,
    ) {
        dependencies.playlistEngine(
            state = appState,
            playbackQueueController = playbackQueueController,
            activeQueue = mediaAppController::activeQueue,
            currentStreamQuality = playbackQualityController::currentStreamQuality,
        )
    }

    var restorePlaybackSessionAction: (String) -> Boolean = { false }
    val sonicAutoplayService = remember(appState) {
        SonicAutoplayService(provider = { appState.provider })
    }

    val playbackAppController = remember(appState, storage, playbackSessions, settingsStore, context) {
        AndroidPlaybackAppController(
            context = context,
            scope = scope,
            state = appState,
            storage = storage,
            playbackSessions = playbackSessions,
            queueCoordinator = appState.sharedQueueCoordinator,
            settingsStore = settingsStore,
            audioAssets = playbackAudioAssets,
            playbackEngine = playbackEngine,
            queueController = playbackQueueController,
            playlistEngine = androidPlaylistEngine,
            playbackReportController = playbackReportController,
            sidecarController = nowPlayingSidecarController,
            activeQueue = mediaAppController::activeQueue,
            currentStreamQuality = playbackQualityController::currentStreamQuality,
            loadRelatedTracks = mediaAppController::loadRelatedTracks,
            sonicAutoplayService = sonicAutoplayService,
            onSyncedSettingsChanged = { onSyncedSettingsChanged() },
        )
    }

    val androidAutoController = remember(appState, storage) {
        AndroidAutoAppController(
            scope = scope,
            state = appState,
            storage = storage,
            playbackEngine = playbackEngine,
            restorePlaybackSession = { sourceId -> restorePlaybackSessionAction(sourceId) },
            playTrack = { track, queue, openNowPlaying, startPositionSeconds ->
                playbackAppController.playTrack(
                    track = track,
                    queue = queue,
                    openNowPlaying = openNowPlaying,
                    startPositionSeconds = startPositionSeconds,
                )
            },
            playInternetRadioStation = playbackAppController::playInternetRadioStation,
            playAdjacentTrack = playbackAppController::playAdjacentTrack,
            performSeek = playbackAppController::performSeek,
            toggleCurrentFavorite = mediaAppController::toggleCurrentFavorite,
            startCurrentTrackRadio = { appState.nowPlaying?.let(playbackAppController::startTrackRadio) },
            savePlaybackSessionThrottled = playbackAppController::savePlaybackSessionThrottled,
        )
    }
    androidAutoController.installNotificationControls()

    val navigationController = remember(appState) {
        AndroidNavigationController(appState, mediaAppController::openArtistDetails)
    }

    val artistActionController = remember(appState, storage, context) {
        AndroidArtistActionController(
            context = context,
            scope = scope,
            state = appState,
            queueController = playbackQueueController,
            libraryIndexRepository = storage,
            providerResponseCacheRepository = storage,
            similarArtistsService = similarArtistsService,
            activeQueue = mediaAppController::activeQueue,
            openArtistDetails = { artistId, fallbackName ->
                mediaAppController.openArtistDetails(artistId, fallbackName)
            },
            playTrack = { track, queue -> playbackAppController.playTrack(track, queue) },
            playRadioTrack = { track, queue -> playbackAppController.playTrack(track, queue, keepRadioQueueActive = true) },
            startTrackRadio = playbackAppController::startTrackRadio,
            startAlbumRadio = playbackAppController::startAlbumRadio,
            rememberRecentRadioStream = playbackAppController::rememberRecentRadioStream,
        )
    }

    val mixBuilderController = rememberAndroidMixBuilderController(
        scope = scope,
        state = appState,
        queueController = playbackQueueController,
        storage = storage,
        sourceId = { activeSourceId },
        provider = { provider },
        homeContent = { homeState },
        popularTracksService = popularTracksService,
        similarArtistsService = similarArtistsService,
        playTrack = { track, queue -> playbackAppController.playTrack(track, queue, keepRadioQueueActive = true) },
        rememberRecentRadioStream = playbackAppController::rememberRecentRadioStream,
    )

    val playlistActionController = remember(appState, storage) {
        AndroidPlaylistActionController(
            scope = scope,
            state = appState,
            storage = storage,
            playTrack = { track, queue -> playbackAppController.playTrack(track, queue) },
            appendTracksToQueue = mediaAppController::appendTracksToQueue,
        )
    }

    val sleepTimerController = remember(appState) {
        SleepTimerController(
            nowPlaying = { appState.nowPlaying },
            playbackQueue = { appState.playbackQueue },
            playbackProgress = { appState.playbackProgress },
            playbackState = { appState.playbackState },
            setSleepTimer = { timer -> appState.sleepTimer = timer },
            setSleepTimerNowEpochMillis = { millis -> appState.sleepTimerNowEpochMillis = millis },
            setStatus = { status -> appState.status = status },
            stopPlayback = playbackAppController::stop,
            nowEpochMillis = { System.currentTimeMillis() },
        )
    }

    val cacheSettingsController = remember(appState, storage, settingsStore, context) {
        NaviampCacheSettingsController(
            setSettings = { settings -> appState.cacheSettings = settings },
            saveSettings = settingsStore::saveCacheSettings,
            applyPlatformSettings = { settings ->
                storage.updateAudioCacheLimit(settings.maxAudioCacheBytes)
                storage.updateDownloadDirectory(
                    settings.customDownloadDirectory?.let(::File)
                        ?: File(context.filesDir, "downloads"),
                )
                storage.updateAudioCacheDirectory(
                    settings.customAudioCacheDirectory?.let(::File)
                        ?: File(context.cacheDir, "audio-cache"),
                )
                appState.storageStats = storage.stats()
            },
        )
    }
    val cacheMaintenanceController = remember(appState, storage, context) {
        NaviampCacheMaintenanceController(
            repository = storage,
            clearPlatformCaches = { clearAndroidFileCaches(context) },
            clearDerivedState = { clearAndroidDerivedMediaState(appState) },
            setStatus = { status ->
                appState.sharedControllers.status.publish(
                    area = NaviampApplicationStatusArea.CacheMaintenance,
                    level = NaviampApplicationStatusLevel.Information,
                    message = status,
                )
            },
        )
    }
    val downloadJobsController = remember(appState) {
        NaviampDownloadJobController(
            jobs = { appState.downloadJobs },
            setJobs = { jobs -> appState.downloadJobs = jobs },
        )
    }
    val downloadCoordinator = remember(storage, downloadJobsController) {
        NaviampDownloadCoordinator(
            downloadRepository = storage,
            downloadReplacementRepository = storage,
            keepDownloadedRepository = storage,
            jobs = downloadJobsController,
            downloadedTrackId = { download: AndroidDownloadedTrack -> download.track.id.value },
            loadStats = { withContext(Dispatchers.IO) { storage.stats() } },
        )
    }

    val apiLibraryController = remember(appState, storage) {
        AndroidApiLibraryController(scope = scope, state = appState, libraryIndexRepository = storage)
    }

    val connectionSessionController = remember(
        appState,
        storage,
        playbackSessions,
        settingsStore,
        playbackQueueController,
    ) {
        AndroidConnectionSessionController(
            scope = scope,
            state = appState,
            storage = storage,
            playbackSessions = playbackSessions,
            settingsStore = settingsStore,
            savedConnection = savedConnection,
            playbackEngine = playbackEngine,
            queueController = playbackQueueController,
            preloadPlaylistTracks = playlistActionController::preloadPlaylistTracks,
            loadRelatedTracks = mediaAppController::loadRelatedTracks,
            startRestoredTrackPlayback = {
                appState.nowPlaying?.let { track ->
                    playbackAppController.playTrack(
                        track = track,
                        queue = appState.playbackQueue.tracks,
                        startPositionSeconds = appState.restoredStartPositionSeconds,
                    )
                }
            },
            prepareRestoredTrackSidecars = {
                appState.provider?.let { activeProvider ->
                    androidPlaylistEngine.startSidecarPrep(
                        sessionToken = appState.playbackSessionToken,
                        activeProvider = activeProvider,
                        queue = appState.playbackQueue,
                    )
                }
            },
            startAndroidLibrarySync = { apiLibraryController.refresh() },
            checkAndroidLibraryFreshness = {},
        )
    }
    restorePlaybackSessionAction = connectionSessionController::restorePlaybackSession

    LaunchedEffect(appState.provider) {
        apiLibraryController.refresh()
    }

    AndroidAppPersistenceEffects(
        state = appState,
        downloadRepository = storage,
        cacheMaintenanceRepository = storage,
        updateAudioCacheLimit = storage::updateAudioCacheLimit,
        savePlaybackSessionThrottled = playbackAppController::savePlaybackSessionThrottled,
        checkAndroidLibraryFreshness = {},
    )

    val sonicPathController = remember(appState) {
        AndroidSonicPathController(
            scope = scope,
            state = appState,
            queueController = playbackQueueController,
            playTrack = { track, queue -> playbackAppController.playTrack(track, queue) },
        )
    }
    val sonicMixController = remember(appState) {
        AndroidSonicMixController(
            scope = scope,
            state = appState,
            queueController = playbackQueueController,
            playTrack = { track, queue -> playbackAppController.playTrack(track, queue) },
        )
    }
    val sonicHomeDiscoveryController = remember(appState, storage) {
        AndroidSonicHomeDiscoveryController(
            scope = scope,
            state = appState,
            storage = storage,
            queueController = playbackQueueController,
            playTrack = { track, queue -> playbackAppController.playTrack(track, queue) },
        )
    }
    val coverArtUrlForUi: (String?) -> String? = { coverArtId -> coverArtId?.let { appState.provider?.coverArtUrl(it) } }
    var settingsSyncSettings by remember { mutableStateOf(settingsStore.loadSettingsSync()) }
    var settingsSyncStatus by remember { mutableStateOf<String?>(null) }
    val settingsSyncMirrorStore = remember(context) { AndroidSettingsSyncMirrorStore(context) }

    fun settingsSyncRuntimeState(): SettingsSyncRuntimeState =
        SettingsSyncRuntimeState(
            autoExportEnabled = settingsSyncSettings.autoExportEnabled,
            lastLocalUpdateEpochMillis = settingsSyncSettings.lastLocalUpdateEpochMillis,
            lastAppliedSyncUpdateEpochMillis = settingsSyncSettings.lastAppliedSyncUpdateEpochMillis,
        )

    fun saveSettingsSyncSettings(settings: AndroidSettingsSyncSettings) {
        val normalized = settings.normalized()
        settingsSyncSettings = normalized
        settingsStore.saveSettingsSync(normalized)
    }

    fun saveSettingsSyncRuntimeState(runtimeState: SettingsSyncRuntimeState) {
        saveSettingsSyncSettings(
            settingsSyncSettings.copy(
                autoExportEnabled = runtimeState.autoExportEnabled,
                lastLocalUpdateEpochMillis = runtimeState.lastLocalUpdateEpochMillis,
                lastAppliedSyncUpdateEpochMillis = runtimeState.lastAppliedSyncUpdateEpochMillis,
            ),
        )
    }

    fun settingsSyncTreeUri(): Uri? =
        settingsSyncSettings.treeUri?.let(Uri::parse)

    val applicationServices = remember(
        appState,
        storage,
        settingsStore,
        playbackEngine,
        cacheSettingsController,
        cacheMaintenanceController,
        downloadJobsController,
        downloadCoordinator,
    ) {
        NaviampApplicationServices(
            settingsSync = NaviampSettingsSyncController(
                deviceId = AndroidSettingsSyncDeviceId,
                state = ::settingsSyncRuntimeState,
                saveState = ::saveSettingsSyncRuntimeState,
                nowEpochMillis = { System.currentTimeMillis() },
                snapshot = {
                    SettingsSyncLocalSnapshot(
                        serverProfiles = storage.mediaSources(),
                        interfaceSettings = appState.interfaceSettings,
                        playback = appState.playbackSettings,
                        visualizer = VisualizerSettings(
                            selectedVisualizer = appState.selectedVisualizer.name,
                        ),
                        recentRadioStreams = settingsStore.loadRecentRadioStreams(),
                        recentInternetRadioStations = settingsStore.loadRecentInternetRadioStations(),
                    )
                },
                applyDocument = { document ->
                    applyAndroidSettingsSyncDocument(
                        document = document,
                        state = appState,
                        settingsStore = settingsStore,
                        storage = storage,
                        playbackEngine = playbackEngine,
                    )
                },
            ),
            cacheSettings = cacheSettingsController,
            cacheMaintenance = cacheMaintenanceController,
            downloadJobs = downloadJobsController,
            downloads = downloadCoordinator,
        )
    }
    val settingsSyncController = applicationServices.settingsSync

    val downloadActionController = remember(appState, storage, context, applicationServices) {
        AndroidDownloadActionController(
            context = context,
            scope = scope,
            state = appState,
            storage = storage,
            findKnownTrack = mediaAppController::findKnownTrack,
            downloadJobs = applicationServices.downloadJobs,
            downloads = applicationServices.downloads,
        )
    }
    LaunchedEffect(appState.activeSourceId) {
        downloadActionController.reloadKeepDownloadedPolicies()
    }
    LaunchedEffect(
        appState.homeState.playlists.map { playlist -> Triple(playlist.id, playlist.trackCount, playlist.isSmart) },
        appState.nowPlaying?.favoritedAtIso8601,
    ) {
        if (appState.keepDownloadedPolicies.isNotEmpty()) {
            downloadActionController.reconcileKeepDownloadedCollections()
        }
    }

    val settingsMaintenanceController = remember(appState, storage, settingsStore, context) {
        AndroidSettingsMaintenanceController(
            context = context,
            state = appState,
            storage = storage,
            settingsStore = settingsStore,
            playbackEngine = playbackEngine,
            queueController = playbackQueueController,
            reloadVisibleLyrics = nowPlayingSidecarController::reloadVisibleLyrics,
            redownloadTracks = downloadActionController::redownloadTracks,
            cacheSettingsController = applicationServices.cacheSettings,
            cacheMaintenanceController = applicationServices.cacheMaintenance,
            onSyncedSettingsChanged = { onSyncedSettingsChanged() },
        )
    }

    fun saveSettingsSyncMirror(document: SettingsSyncDocument) {
        settingsSyncMirrorStore.write(document)
        settingsSyncController.documentWritten(document)
        saveSettingsSyncSettings(
            settingsSyncSettings.copy(
                lastMirrorUpdateEpochMillis = document.updatedAtEpochMillis,
                lastProviderError = null,
            ),
        )
    }

    fun markProviderPullSucceeded() {
        saveSettingsSyncSettings(
            settingsSyncSettings.copy(
                lastProviderPullEpochMillis = System.currentTimeMillis(),
                lastProviderError = null,
            ),
        )
    }

    fun markProviderPushSucceeded() {
        saveSettingsSyncSettings(
            settingsSyncSettings.copy(
                lastProviderPushEpochMillis = System.currentTimeMillis(),
                lastProviderError = null,
            ),
        )
    }

    fun markProviderSyncFailed(message: String) {
        saveSettingsSyncSettings(settingsSyncSettings.copy(lastProviderError = message))
    }

    fun writeProviderSettingsSync(treeUri: Uri, document: SettingsSyncDocument, statusMessage: () -> String) {
        runCatching {
            AndroidSettingsSyncFile.write(context, treeUri, document)
        }.onSuccess {
            markProviderPushSucceeded()
            settingsSyncStatus = statusMessage()
        }.onFailure { error ->
            val message = error.message ?: "Could not sync settings with provider."
            markProviderSyncFailed(message)
            settingsSyncStatus = "Settings saved locally. Provider sync pending: $message"
        }
    }

    fun writeMirrorAndTryProvider(document: SettingsSyncDocument, statusMessage: () -> String) {
        runCatching {
            saveSettingsSyncMirror(document)
        }.onFailure { error ->
            val message = error.message ?: "Could not save local settings mirror."
            settingsSyncStatus = message
            appState.status = message
            return
        }
        val treeUri = settingsSyncTreeUri()
        if (treeUri == null) {
            settingsSyncStatus = "Settings saved locally. Choose a sync folder to sync provider."
            return
        }
        writeProviderSettingsSync(treeUri = treeUri, document = document, statusMessage = statusMessage)
    }

    fun writeCurrentSettingsSync(statusMessage: () -> String) {
        settingsSyncController.exportCurrent().documentToWrite?.let { document ->
            writeMirrorAndTryProvider(document = document, statusMessage = statusMessage)
        }
    }

    fun autoExportSettingsSync() {
        settingsSyncController.autoExport()?.documentToWrite?.let { document ->
            writeMirrorAndTryProvider(document) { "Settings auto-synced to provider." }
        }
    }

    fun markAndAutoExportSettingsSync() {
        settingsSyncController.markLocalChanged()
        settingsSyncController.exportCurrent().documentToWrite?.let { document ->
            if (settingsSyncSettings.autoExportEnabled) {
                writeMirrorAndTryProvider(document) { "Settings auto-synced to provider." }
            } else {
                runCatching {
                    saveSettingsSyncMirror(document)
                }.onSuccess {
                    settingsSyncStatus = "Settings saved locally. Sync now when ready."
                }.onFailure { error ->
                    val message = error.message ?: "Could not save local settings mirror."
                    settingsSyncStatus = message
                    appState.status = message
                }
            }
        }
    }
    onSyncedSettingsChanged = ::markAndAutoExportSettingsSync

    fun readLocalSettingsSyncMirror(): SettingsSyncDocument? =
        runCatching { settingsSyncMirrorStore.read() }
            .onFailure { error ->
                val message = error.message ?: "Could not read local settings mirror."
                settingsSyncStatus = message
                appState.status = message
            }
            .getOrNull()

    fun syncSettingsNow(statusPrefix: String = "Settings sync") {
        val treeUri = settingsSyncTreeUri()
        val localMirrorDocument = readLocalSettingsSyncMirror()
        var providerDocument: SettingsSyncDocument? = null
        var providerFileMissing = false
        var providerReadError: String? = null
        if (treeUri != null) {
            runCatching {
                AndroidSettingsSyncFile.read(context, treeUri)
            }.onSuccess { document ->
                providerDocument = document
                providerFileMissing = document == null
                if (document != null) markProviderPullSucceeded()
            }.onFailure { error ->
                providerReadError = error.message ?: "Could not read settings sync provider."
                markProviderSyncFailed(providerReadError.orEmpty())
            }
        }

        val reconciliation = settingsSyncController.reconcileDocuments(
            localMirrorDocument = localMirrorDocument,
            providerDocument = providerDocument,
            syncLocationConfigured = true,
        )
        val selection = reconciliation.selection
        val result = reconciliation.result
        when (result.kind) {
            SettingsSyncOperationKind.Imported -> {
                selection.document?.let { document ->
                    runCatching {
                        saveSettingsSyncMirror(document)
                    }.onFailure { error ->
                        val message = error.message ?: "Could not save local settings mirror."
                        settingsSyncStatus = message
                        appState.status = message
                        return
                    }
                    if (selection.source == SettingsSyncMirrorDocumentSource.Provider) {
                        markProviderPullSucceeded()
                    }
                }
                settingsSyncStatus = settingsSyncImportStatus(result.hasServerProfiles)
            }
            SettingsSyncOperationKind.Exported -> {
                result.documentToWrite?.let { document ->
                    writeMirrorAndTryProvider(document) { "$statusPrefix exported local settings." }
                }
            }
            SettingsSyncOperationKind.NoOp -> {
                if (providerFileMissing && localMirrorDocument != null && treeUri != null) {
                    writeProviderSettingsSync(treeUri, localMirrorDocument) { "$statusPrefix created provider file." }
                } else if (providerReadError != null && localMirrorDocument != null) {
                    settingsSyncStatus = "Local settings mirror is ready. Provider sync pending: $providerReadError"
                } else {
                    settingsSyncStatus = "$statusPrefix is up to date."
                }
            }
            SettingsSyncOperationKind.UnsupportedSyncFile,
            SettingsSyncOperationKind.NeedsSetupChoice,
            SettingsSyncOperationKind.MissingSyncLocation,
            -> settingsSyncStatus = settingsSyncReconciliationStatus(result)
        }
        if (providerReadError != null && selection.document == null) {
            settingsSyncStatus = providerReadError
        }
    }

    fun importSettingsSyncFolder() {
        syncSettingsNow(statusPrefix = "Manual sync")
    }

    fun exportSettingsSyncFolder() {
        settingsSyncController.markLocalChanged()
        writeCurrentSettingsSync { "Settings exported to sync provider." }
    }

    fun updateSettingsSyncAutoExport(enabled: Boolean) {
        saveSettingsSyncSettings(
            settingsSyncSettings.copy(
                autoExportEnabled = enabled && settingsSyncSettings.treeUri != null,
            ),
        )
        settingsSyncStatus = settingsSyncAutoExportStatus(settingsSyncSettings.autoExportEnabled)
        if (settingsSyncSettings.autoExportEnabled) {
            autoExportSettingsSync()
        }
    }

    val settingsSyncImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) {
            settingsSyncStatus = "Settings import cancelled."
        } else {
            settingsSyncStatus = "Importing settings..."
            runCatching {
                importAndroidSettingsSyncDocument(
                    context = context,
                    uri = uri,
                    state = appState,
                    settingsStore = settingsStore,
                    storage = storage,
                    playbackEngine = playbackEngine,
                )
            }.onSuccess { message ->
                settingsSyncStatus = message
            }.onFailure { error ->
                val message = error.message ?: "Could not import settings file."
                settingsSyncStatus = message
                appState.status = message
            }
        }
    }
    val settingsSyncFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) {
            settingsSyncStatus = "Settings sync folder selection cancelled."
        } else {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            saveSettingsSyncSettings(settingsSyncSettings.copy(treeUri = uri.toString()))
            settingsSyncStatus = settingsSyncLocationStatus(configured = true)
            syncSettingsNow(statusPrefix = "Settings sync")
        }
    }
    val openSettingsSyncImport = {
        settingsSyncImportLauncher.launch("*/*")
    }
    val chooseSettingsSyncFolder = {
        settingsSyncFolderLauncher.launch(null)
    }
    LaunchedEffect(Unit) {
        syncSettingsNow(statusPrefix = "Settings sync")
    }
    LaunchedEffect(settingsSyncImportUriRequest) {
        val uri = settingsSyncImportUriRequest ?: return@LaunchedEffect
        settingsSyncStatus = "Importing settings..."
        runCatching {
            importAndroidSettingsSyncDocument(
                context = context,
                uri = uri,
                state = appState,
                settingsStore = settingsStore,
                storage = storage,
                playbackEngine = playbackEngine,
            )
        }.onSuccess { message ->
            settingsSyncStatus = message
        }.onFailure { error ->
            val message = error.message ?: "Could not import settings file."
            settingsSyncStatus = message
            appState.status = message
        }
        onSettingsSyncImportUriConsumed()
    }

    val shellUiState = rememberAndroidAppShellUiState(
        state = appState,
        modifier = modifier,
        context = context,
        bassLoadReport = bassLoadReport,
        playbackEngine = playbackEngine,
        sonicPathBuilder = sonicPathController.ui(coverArtUrlForUi),
        sonicMixBuilder = sonicMixController.ui(coverArtUrlForUi),
    )

    val shellPlaybackController = remember(appState) {
        AndroidShellPlaybackController(
            scope = scope,
            state = appState,
            playbackEngine = playbackEngine,
            playbackQueueController = playbackQueueController,
            activeQueue = mediaAppController::activeQueue,
            playTrack = playbackAppController::playTrack,
            playInternetRadioStation = playbackAppController::playInternetRadioStation,
            rememberRecentRadioStream = playbackAppController::rememberRecentRadioStream,
        )
    }

    val shellMediaController = remember(appState, storage, settingsStore) {
        AndroidShellMediaController(
            scope = scope,
            state = appState,
            storage = storage,
            settingsStore = settingsStore,
            queueController = playbackQueueController,
            playbackEngine = playbackEngine,
            internetRadioStationManager = dependencies.internetRadioStationManager,
            activeQueue = mediaAppController::activeQueue,
            findKnownTrack = mediaAppController::findKnownTrack,
            playTrack = { track, queue -> playbackAppController.playTrack(track, queue) },
            playRadioTrack = { track, queue -> playbackAppController.playTrack(track, queue, keepRadioQueueActive = true) },
            playInternetRadioStation = playbackAppController::playInternetRadioStation,
            startTrackRadio = playbackAppController::startTrackRadio,
            startAlbumRadio = playbackAppController::startAlbumRadio,
            openArtistDetails = { artistId, fallbackName ->
                mediaAppController.openArtistDetails(artistId, fallbackName)
            },
            rememberRecentRadioStream = playbackAppController::rememberRecentRadioStream,
        )
    }

    val trackActionController = remember(appState) {
        AndroidTrackActionController(
            scope = scope,
            state = appState,
            activeQueue = mediaAppController::activeQueue,
            findKnownTrack = mediaAppController::findKnownTrack,
            playTrack = { track, queue -> playbackAppController.playTrack(track, queue) },
            playNextTracks = mediaAppController::playNextTracks,
            appendTracksToQueue = mediaAppController::appendTracksToQueue,
            downloadTrack = downloadActionController::downloadTrack,
            addTrackToPlaylist = playlistActionController::addTrackToPlaylist,
            removeDownload = downloadActionController::removeDownload,
            toggleTrackFavorite = { track ->
                toggleAndroidTrackFavorite(
                    scope = scope,
                    state = appState,
                    playbackEngine = playbackEngine,
                    track = track,
                    playbackQueueController = playbackQueueController,
                    providerActions = appState.sharedControllers.providerActions,
                )
            },
            openTrackAlbum = shellMediaController::handleTrackGoToAlbum,
            openTrackArtist = { track, artistId, artistName ->
                shellMediaController.handleTrackGoToArtist(track, artistId, artistName)
            },
        )
    }

    AndroidMainEffects(
        state = appState,
        searchController = searchController,
        nowPlayingSidecarController = nowPlayingSidecarController,
        androidAutoController = androidAutoController,
        navigationController = navigationController,
        mediaAppController = mediaAppController,
        mixBuilderController = mixBuilderController,
        sonicHomeDiscoveryController = sonicHomeDiscoveryController,
        connectionSessionController = connectionSessionController,
        playbackSessions = playbackSessions,
        playbackExecution = playbackAppController,
        sleepTimerController = sleepTimerController,
        providerResponseCacheRepository = storage,
        onAutoPlayMediaIdConsumed = onAutoPlayMediaIdConsumed,
        onAutoCommandConsumed = onAutoCommandConsumed,
    )

    val shellActions = androidMainShellActions(
        scope = scope,
        state = appState,
        storage = storage,
        settingsStore = settingsStore,
        searchController = searchController,
        mediaAppController = mediaAppController,
        playbackAppController = playbackAppController,
        navigationController = navigationController,
        artistActionController = artistActionController,
        mixBuilderController = mixBuilderController,
        playlistActionController = playlistActionController,
        sleepTimerController = sleepTimerController,
        downloadActionController = downloadActionController,
        settingsMaintenanceController = settingsMaintenanceController,
        connectionSessionController = connectionSessionController,
        shellPlaybackController = shellPlaybackController,
        shellMediaController = shellMediaController,
        trackActionController = trackActionController,
        sonicPathController = sonicPathController,
        sonicMixController = sonicMixController,
        sonicHomeDiscoveryController = sonicHomeDiscoveryController,
        nowPlayingSidecarController = nowPlayingSidecarController,
        apiLibraryController = apiLibraryController,
        onSyncedSettingsChanged = { onSyncedSettingsChanged() },
    )

    AndroidAppShellContent(
        state = shellUiState,
        actions = shellActions,
        settingsSyncStatus = settingsSyncStatus,
        onImportSettingsSyncFile = openSettingsSyncImport,
        onChooseSettingsSyncFolder = chooseSettingsSyncFolder,
        onImportSettingsSyncFolder = ::importSettingsSyncFolder,
        onExportSettingsSyncFolder = ::exportSettingsSyncFolder,
        settingsSyncAutoExportEnabled = settingsSyncSettings.autoExportEnabled,
        onSettingsSyncAutoExportChanged = ::updateSettingsSyncAutoExport,
    )
    }
}

private const val AndroidSettingsSyncDeviceId = "android"
