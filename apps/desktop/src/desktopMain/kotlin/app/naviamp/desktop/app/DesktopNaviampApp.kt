package app.naviamp.desktop

import app.naviamp.domain.cache.StorageCacheStats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.app.NaviampApplicationControllers
import app.naviamp.app.NaviampApplicationServices
import app.naviamp.app.NaviampCacheMaintenanceController
import app.naviamp.app.NaviampApplicationStatusArea
import app.naviamp.app.NaviampApplicationStatusLevel
import app.naviamp.app.NaviampDownloadCoordinator
import app.naviamp.app.NaviampDownloadJobController
import app.naviamp.app.NaviampLivePlaybackState
import app.naviamp.app.NaviampPlaybackSessionController
import app.naviamp.app.NaviampCacheSettingsController
import app.naviamp.domain.app.NaviampNavigationState
import app.naviamp.domain.cache.ImageCacheRepository
import app.naviamp.domain.cache.DownloadJob
import app.naviamp.domain.cache.downloadedAudioQualityLabel
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.AudioOutputDevicePlaybackEngine
import app.naviamp.desktop.playback.PlaylistCallbacks
import app.naviamp.desktop.playback.desktopPlaylistCallbacks
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.SleepTimerState
import app.naviamp.domain.playback.SleepTimerController
import app.naviamp.domain.home.HomeContent
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.radio.InternetRadioStationManager
import app.naviamp.domain.radio.RadioTuningSettings
import app.naviamp.desktop.settings.PlaybackSettings
import app.naviamp.desktop.settings.PlaybackSessionSettings
import app.naviamp.desktop.settings.RecentRadioStream
import app.naviamp.desktop.settings.DesktopSettingsSyncDocumentStore
import app.naviamp.desktop.settings.DesktopSettingsSyncSettings
import app.naviamp.desktop.settings.VisualizerSettings
import app.naviamp.app.NaviampSettingsSyncController
import app.naviamp.app.settingsSyncAutoExportStatus
import app.naviamp.app.settingsSyncImportStatus
import app.naviamp.app.settingsSyncLocationStatus
import app.naviamp.app.settingsSyncReconciliationStatus
import app.naviamp.domain.settings.SettingsSyncDocument
import app.naviamp.domain.settings.SettingsSyncLocalSnapshot
import app.naviamp.domain.settings.SettingsSyncOperationKind
import app.naviamp.domain.settings.SettingsSyncRuntimeState
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.domain.settings.effectiveForEngine
import app.naviamp.domain.settings.PlaybackSettingsMaintenanceController
import app.naviamp.domain.settings.SavedInternetRadioStation
import app.naviamp.domain.settings.ConnectionFormMusicFolder
import app.naviamp.domain.settings.connectionFormMusicFolders
import app.naviamp.domain.settings.defaultSelectedMusicFolderIds
import app.naviamp.domain.settings.importSettingsSyncServerProfiles
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.playbackSettingsChange
import app.naviamp.domain.settings.restoredPlaybackQueue
import app.naviamp.domain.settings.restoredTrackSession
import app.naviamp.domain.settings.selectedMusicFolderSummary
import app.naviamp.domain.settings.toConnectionHeaderDefinitions
import app.naviamp.domain.settings.toConnectionSecondaryUrls
import app.naviamp.domain.source.ConnectionTlsSettings
import app.naviamp.domain.source.visibleServerConnections
import app.naviamp.domain.sonicautoplay.SonicAutoplayService
import app.naviamp.provider.navidrome.NavidromeConnection
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.provider.navidrome.toNavidromeConnection
import app.naviamp.provider.navidrome.withNativeTokenFromPassword
import app.naviamp.ui.NaviampSleepTimerUi
import app.naviamp.ui.NaviampConnectionCapabilitiesUi
import app.naviamp.ui.NaviampConnectionSettingsActions
import app.naviamp.ui.NaviampSavedConnectionUi
import app.naviamp.ui.NaviampLibraryScreenUi
import app.naviamp.ui.NaviampLibraryActions
import app.naviamp.ui.NaviampLibrarySyncStatusUi
import app.naviamp.ui.NaviampSearchScreenUi
import app.naviamp.ui.NaviampSearchActions
import app.naviamp.ui.NaviampAlbumDetailScreenUi
import app.naviamp.ui.NaviampAppShellActions
import app.naviamp.ui.NaviampAppShellUiState
import app.naviamp.ui.NaviampHomeScreenUi
import app.naviamp.ui.NaviampHomeActions
import app.naviamp.ui.NaviampDownloadsScreenUi
import app.naviamp.ui.NaviampOfflineDashboardUi
import app.naviamp.ui.NaviampShellChromeUi
import app.naviamp.ui.NaviampShellNavigationActions
import app.naviamp.ui.NaviampSettingsMaintenanceActions
import app.naviamp.ui.NaviampSettingsValueActions
import app.naviamp.ui.NaviampArtistDetailScreenUi
import app.naviamp.ui.NaviampPlaylistDetailScreenUi
import app.naviamp.ui.NaviampPlaylistsActions
import app.naviamp.ui.NaviampPlaylistsScreenUi
import app.naviamp.ui.NaviampShellCapabilitiesUi
import app.naviamp.ui.NaviampShellConnectionUi
import app.naviamp.ui.SharedAlbumMixBuilderActions
import app.naviamp.ui.SharedArtistMixBuilderActions
import app.naviamp.ui.SharedGenreMixBuilderActions
import app.naviamp.ui.SharedSonicMixBuilderActions
import app.naviamp.ui.SharedSonicPathBuilderActions
import app.naviamp.ui.NaviampSleepTimerExpiryEffect
import app.naviamp.ui.NowPlayingDisplayAction
import app.naviamp.ui.NowPlayingDisplayActionRequest
import app.naviamp.ui.NowPlayingPlaybackAction
import app.naviamp.ui.NowPlayingPlaybackActionRequest
import app.naviamp.ui.NowPlayingQueueAction
import app.naviamp.ui.NowPlayingQueueActionRequest
import app.naviamp.ui.NowPlayingSelectionAction
import app.naviamp.ui.NowPlayingSelectionActionRequest
import app.naviamp.ui.NowPlayingSleepTimerAction
import app.naviamp.ui.NowPlayingSleepTimerActionRequest
import app.naviamp.ui.naviampVisualizerFromName
import app.naviamp.ui.nowPlayingQueueIndex
import app.naviamp.ui.nowPlayingRelatedIndex
import app.naviamp.ui.toSharedMediaItemUi
import app.naviamp.ui.toSharedAlbumDetailUi
import app.naviamp.ui.toSharedArtistDetailUi
import app.naviamp.ui.toSharedPlaylistDetailUi
import app.naviamp.ui.toSharedSearchResultsUi
import app.naviamp.ui.toInternetRadioStationUi
import app.naviamp.ui.toCacheSettingsUi
import app.naviamp.ui.toConnectionSettingsUi
import app.naviamp.ui.toGeneralSettingsUi
import app.naviamp.ui.toPlaybackSettingsUi
import app.naviamp.ui.toSharedHomeUi
import app.naviamp.ui.toDownloadedTrackUi
import app.naviamp.ui.toDownloadJobUi
import app.naviamp.ui.totalDownloadBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path

@Composable
@NonRestartableComposable
fun NaviampApp(
    dependencies: DesktopAppDependencies = remember { DesktopAppDependencies() },
) {
    val appColors = DesktopAppColors.Dark
    val colorScheme = darkColorScheme()
    val settingsStore = dependencies.settingsStore
    val playbackSessions = remember(settingsStore) { NaviampPlaybackSessionController(settingsStore) }
    val about = remember { loadDesktopAboutUi() }
    val playbackEngine = dependencies.playbackEngine
    val storage = dependencies.storage
    val imageCacheRepository: ImageCacheRepository = dependencies.imageCacheRepository
    val savedMediaSource = remember { storage.latestMediaSource() }
    val savedSettingsConnection = remember { settingsStore.loadConnection()?.toConnection() }
    val savedConnection = remember {
        savedMediaSource?.toNavidromeConnection()
            ?.withNativeTokenFrom(savedSettingsConnection)
            ?: savedSettingsConnection
    }
    val savedPlaybackSession = remember { playbackSessions.load() }
    val savedVisualizer = remember { settingsStore.loadVisualizerSettings() }
    val savedNavigation = remember { settingsStore.loadNavigationSettings() }
    val savedSearch = remember { settingsStore.loadSearchSettings() }
    val savedRecentRadioStreams = remember { settingsStore.loadRecentRadioStreams() }
    val savedRecentPlaylistIds = remember { settingsStore.loadRecentPlaylistIds() }
    val savedRecentInternetRadioStations = remember { settingsStore.loadRecentInternetRadioStations() }
    val savedSettingsSync = remember { settingsStore.loadSettingsSync() }
    val savedPlaybackSettings = remember {
        val settings = settingsStore.loadPlaybackSettings()
        val storedDjs = storage.radioDjPresets()
        if (storedDjs.isEmpty() && settings.radioDjs.isNotEmpty()) {
            storage.replaceRadioDjPresets(settings.radioDjs)
            settings.copy(radioDjs = storage.radioDjPresets())
        } else {
            settings.copy(radioDjs = storedDjs)
        }
    }
    var cacheStats by remember { mutableStateOf(StorageCacheStats()) }
    var downloadJobs by remember { mutableStateOf<List<DownloadJob>>(emptyList()) }
    var connectedSourceId by remember { mutableStateOf(savedMediaSource?.id) }
    val desktopPlaybackAudioAssets = dependencies.playbackAudioAssets
    val audioMetadataSidecarService = dependencies.audioMetadataSidecarService
    val lyricsSidecarService = dependencies.lyricsSidecarService
    val audioWaveformService = dependencies.audioWaveformService
    val playbackSidecarService = dependencies.playbackSidecarService
    var cacheSettings by remember {
        mutableStateOf(settingsStore.loadCacheSettings().normalized())
    }
    var interfaceSettings by remember {
        mutableStateOf(settingsStore.loadInterfaceSettings().normalized())
    }
    var playbackSettings by remember {
        mutableStateOf(savedPlaybackSettings.effectiveForEngine(playbackEngine))
    }
    dependencies.waveformsEnabledProvider = { cacheSettings.waveformsEnabled }
    dependencies.waveformBucketCountProvider = { cacheSettings.normalized().waveformBucketCount }
    val libraryListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val restoredTrackSession = remember(savedPlaybackSession) { savedPlaybackSession?.restoredTrackSession() }
    val restoredTracks = remember(restoredTrackSession) { restoredTrackSession?.tracks.orEmpty() }
    val restoredInternetRadioStation = remember(savedPlaybackSession) {
        savedPlaybackSession?.internetRadioStation?.toStation()
    }
    val restoredTrack = remember(restoredTrackSession, savedPlaybackSession) {
        restoredTrackSession?.currentTrack ?: savedPlaybackSession?.currentTrack()
    }
    val connectionForm = remember { DesktopConnectionFormStateHolder(savedConnection) }
    var availableMusicFolders by remember { mutableStateOf(emptyList<ConnectionFormMusicFolder>()) }
    var musicFoldersStatus by remember { mutableStateOf<String?>(null) }
    var mediaSourcesRevision by remember { mutableIntStateOf(0) }
    var connectionStatus by remember { mutableStateOf<String?>(null) }
    var settingsSyncSettings by remember { mutableStateOf(savedSettingsSync.normalized()) }
    var settingsSyncStatus by remember { mutableStateOf<String?>(null) }
    var connectedProvider by remember { mutableStateOf<NavidromeProvider?>(null) }
    val sonicAutoplayService = remember {
        SonicAutoplayService(provider = { connectedProvider })
    }
    val popularTracksService = remember(dependencies) {
        dependencies.popularTracksService(
            sourceIdProvider = { connectedSourceId },
            providerProvider = { connectedProvider },
        )
    }
    val similarArtistsService = remember(dependencies) {
        dependencies.similarArtistsService(
            sourceIdProvider = { connectedSourceId },
            providerProvider = { connectedProvider },
        )
    }
    var homeContent by remember { mutableStateOf(HomeContent()) }
    var homeStatus by remember { mutableStateOf<String?>(null) }
    var recentRadioStreams by remember { mutableStateOf(savedRecentRadioStreams) }
    val applicationControllers = remember {
        NaviampApplicationControllers(
            initialNavigationState = NaviampNavigationState(
                route = restoredRoute(
                    savedRouteName = savedNavigation.route,
                    hasConnection = savedConnection != null,
                    hasRestoredTrack = restoredTrack != null,
                ).toNaviampRoute(),
                lastContentRoute = restoredLastContentRoute(savedNavigation.lastContentRoute).toNaviampRoute(),
            ),
            initialPlaybackState = NaviampLivePlaybackState(
                currentTrack = restoredTrack,
                currentStation = restoredInternetRadioStation,
                queue = savedPlaybackSession?.restoredPlaybackQueue() ?: PlaybackQueue(),
            ),
            pendingProviderActions = storage,
        )
    }
    val connectionRuntimeState by applicationControllers.connection.state.collectAsState()
    val applicationStatus by applicationControllers.status.state.collectAsState()
    val isConnecting = connectionRuntimeState.isConnecting
    LaunchedEffect(applicationStatus?.sequence) {
        applicationStatus?.let { connectionStatus = it.message }
    }
    val navigationController = applicationControllers.navigation
    val currentRouteProperty = remember {
        DesktopNavigationRouteProperty(navigationController, DesktopNavigationField.CurrentRoute)
    }
    var appRoute by currentRouteProperty
    val lastContentRouteProperty = remember {
        DesktopNavigationRouteProperty(navigationController, DesktopNavigationField.LastContentRoute)
    }
    var lastContentRoute by lastContentRouteProperty
    val livePlaybackController = applicationControllers.playback
    val queueCoordinator = applicationControllers.queue
    val playlistEngine = remember(dependencies, queueCoordinator) {
        dependencies.playlistEngine(
            queueCoordinator = queueCoordinator,
            sourceIdProvider = { connectedSourceId },
            audioCachingEnabledProvider = { cacheSettings.audioCachingEnabled },
            audioPrefetchDepthProvider = { cacheSettings.audioPrefetchDepth },
            playbackSettingsProvider = { playbackSettings },
        )
    }
    val currentTrackProperty = remember { desktopCurrentTrackProperty(livePlaybackController) }
    var nowPlayingTrack by currentTrackProperty
    var nowPlayingCoverArtUrl by remember { mutableStateOf<String?>(null) }
    var nowPlayingLyricsVisible by remember { mutableStateOf(false) }
    val currentStationProperty = remember { desktopCurrentStationProperty(livePlaybackController) }
    var nowPlayingInternetRadioStation by currentStationProperty
    var nowPlayingStreamMetadata by remember { mutableStateOf(PlaybackStreamMetadata()) }
    val playbackStateProperty = remember { desktopPlaybackStateProperty(livePlaybackController) }
    var playbackState by playbackStateProperty
    val playbackProgressProperty = remember { desktopPlaybackProgressProperty(livePlaybackController) }
    var playbackProgress by playbackProgressProperty
    val pendingSeekPositionProperty = remember { desktopPendingSeekPositionProperty(livePlaybackController) }
    var pendingSeekPositionSeconds by pendingSeekPositionProperty
    val pendingSeekIssuedAtProperty = remember { desktopPendingSeekIssuedAtProperty(livePlaybackController) }
    var pendingSeekIssuedAtMillis by pendingSeekIssuedAtProperty
    val playbackQueueProperty = remember { desktopPlaybackQueueProperty(livePlaybackController) }
    var playbackQueue by playbackQueueProperty
    var sleepTimer by remember { mutableStateOf<SleepTimerState?>(null) }
    var sleepTimerNowEpochMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    playlistEngine.setSonicAutoplayTracksProvider { queue ->
        val enabled = playbackSettings.sonicAutoplayEnabled &&
            connectedProvider?.capabilities?.supportsSonicSimilarity == true
        if (enabled) {
            sonicAutoplayService.continuationTracks(queue)
        } else {
            emptyList()
        }
    }
    var showStatsForNerds by remember { mutableStateOf(false) }
    var statsForNerdsRefreshTick by remember { mutableIntStateOf(0) }
    var openPlayerOnTrackStart by remember { mutableStateOf(false) }
    var shuffledUpNextSnapshot by remember { mutableStateOf<List<Track>?>(null) }
    val repeatModeProperty = remember { desktopRepeatModeProperty(livePlaybackController) }
    var repeatMode by repeatModeProperty
    var radioQueueActive by remember { mutableStateOf(false) }
    var isRadioRefilling by remember { mutableStateOf(false) }
    var lastRadioRefillSeedId by remember { mutableStateOf<TrackId?>(null) }
    var radioSessionId by remember { mutableStateOf(0) }
    var restoredPlaybackPositionSeconds by remember {
        mutableStateOf(savedPlaybackSession?.positionSeconds?.takeIf { it > 0.0 })
    }
    var lastSavedPlaybackPositionSeconds by remember {
        mutableStateOf(savedPlaybackSession?.positionSeconds?.takeIf { it > 0.0 })
    }
    var lastPlaybackProgressUiUpdateMillis by remember { mutableLongStateOf(0L) }
    var playReportSessionId by remember { mutableStateOf(0) }
    val nowPlayingPresentation = rememberDesktopNowPlayingPresentationState(
        initialVisualizerSettings = savedVisualizer,
        appColors = appColors,
        interfaceSettings = interfaceSettings,
        currentCoverArtUrl = nowPlayingCoverArtUrl,
        nowPlayingTrack = nowPlayingTrack,
        nowPlayingStation = nowPlayingInternetRadioStation,
        streamMetadata = nowPlayingStreamMetadata,
        provider = connectedProvider,
    )
    val nowPlayingVisualizerVisible = nowPlayingPresentation.isVisualizerVisible(playbackState)

    val playbackController = remember {
        DesktopPlaybackController(
            scope = coroutineScope,
            playbackSessions = playbackSessions,
            livePlayback = livePlaybackController,
            queueCoordinator = queueCoordinator,
            playbackEngine = playbackEngine,
            playlistEngine = playlistEngine,
            provider = { connectedProvider },
            sourceId = { connectedSourceId },
            providerActions = applicationControllers.providerActions,
            playbackSettings = { playbackSettings },
            playbackQueue = { playbackQueue },
            playbackProgress = { playbackProgress },
            setPlaybackProgress = { progress -> playbackProgress = progress },
            nowPlayingTrack = { nowPlayingTrack },
            repeatMode = { repeatMode },
            setRepeatMode = { mode -> repeatMode = mode },
            shuffledUpNextSnapshot = { shuffledUpNextSnapshot },
            setShuffledUpNextSnapshot = { snapshot -> shuffledUpNextSnapshot = snapshot },
            lastSavedPlaybackPositionSeconds = { lastSavedPlaybackPositionSeconds },
            setLastSavedPlaybackPositionSeconds = { position -> lastSavedPlaybackPositionSeconds = position },
            playReportSessionId = { playReportSessionId },
            setOpenPlayerOnTrackStart = { shouldOpen -> openPlayerOnTrackStart = shouldOpen },
            reporting = applicationControllers.playbackReporting,
        )
    }

    val sleepTimerController = remember {
        SleepTimerController(
        nowPlaying = { nowPlayingTrack },
        playbackQueue = { playbackQueue },
        playbackProgress = { playbackProgress },
        playbackState = { playbackState },
        setSleepTimer = { timer -> sleepTimer = timer },
        setSleepTimerNowEpochMillis = { millis -> sleepTimerNowEpochMillis = millis },
        setStatus = { status -> connectionStatus = status },
        stopPlayback = playbackController::stop,
        nowEpochMillis = { System.currentTimeMillis() },
    )
    }

    NaviampSleepTimerExpiryEffect(
        sleepTimer = sleepTimer,
        snapshot = sleepTimerController.snapshot(),
        onTick = sleepTimerController::tick,
        onExpired = sleepTimerController::expire,
    )

    val nowPlayingController = remember {
        DesktopNowPlayingController(
        audioWaveformService = audioWaveformService,
        lyricsSidecarService = lyricsSidecarService,
        audioMetadataSidecarService = audioMetadataSidecarService,
        localLibraryIndexRepository = storage,
        lyricsOffsetRepository = storage,
        playbackAudioAssets = desktopPlaybackAudioAssets,
        playbackEngine = playbackEngine,
        provider = { connectedProvider },
        sourceId = { connectedSourceId },
        playbackSettings = { playbackSettings },
        cacheSettings = { cacheSettings },
        appRoute = { appRoute },
        lyricsVisible = { nowPlayingLyricsVisible },
        selectedVisualizer = { nowPlayingPresentation.selectedVisualizer },
        playbackQueue = { playbackQueue },
        nowPlayingTrack = { nowPlayingTrack },
        nowPlayingCoverArtUrl = {
            nowPlayingPresentation.effectiveCoverArtUrl
        },
    )
    }

    fun handleQueueIndexSelected(queueIndex: Int) {
        handleDesktopQueueIndexSelected(
            playbackController = playbackController,
            queueIndex = queueIndex,
            upNextSelectionBehavior = playbackSettings.upNextSelectionBehavior,
        )
    }

    fun settingsSyncDirectory(): Path? =
        settingsSyncSettings.directoryPath?.let(Path::of)

    fun settingsSyncRuntimeState(): SettingsSyncRuntimeState =
        SettingsSyncRuntimeState(
            autoExportEnabled = settingsSyncSettings.autoExportEnabled,
            lastLocalUpdateEpochMillis = settingsSyncSettings.lastLocalUpdateEpochMillis,
            lastAppliedSyncUpdateEpochMillis = settingsSyncSettings.lastAppliedSyncUpdateEpochMillis,
        )

    fun saveSettingsSyncSettings(settings: DesktopSettingsSyncSettings) {
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

    fun applySettingsSyncDocument(document: SettingsSyncDocument) {
        val importedPlayback = document.preferences.playback.effectiveForEngine(playbackEngine)
        interfaceSettings = document.preferences.interfaceSettings.normalized()
        settingsStore.saveInterfaceSettings(interfaceSettings)
        storage.replaceRadioDjPresets(importedPlayback.radioDjs)
        playbackSettings = importedPlayback.copy(radioDjs = storage.radioDjPresets())
        settingsStore.savePlaybackSettings(playbackSettings.copy(radioDjs = emptyList()))
        settingsStore.saveVisualizerSettings(document.preferences.visualizer)
        nowPlayingPresentation.selectVisualizer(
            naviampVisualizerFromName(document.preferences.visualizer.selectedVisualizer),
        )
        recentRadioStreams = document.preferences.recentRadioStreams
        settingsStore.saveRecentRadioStreams(recentRadioStreams)
        settingsStore.saveRecentInternetRadioStations(document.preferences.recentInternetRadioStations)

        val importedProfiles = importSettingsSyncServerProfiles(
            serverProfiles = document.serverProfiles,
            repository = storage,
        )
        if (importedProfiles.importedCount > 0) {
            mediaSourcesRevision++
        }

        importedProfiles.firstConnectionForm?.let { form ->
            connectionForm.apply(
                DesktopConnectionFormState(
                    serverUrl = form.serverUrl,
                    connectionName = form.displayName,
                    username = form.username,
                    password = "",
                    insecureSkipTlsVerification = form.skipTlsVerification,
                    customCertificatePath = form.customCertificatePath,
                    clientCertificateKeyStorePath = form.clientCertificatePath,
                    clientCertificateKeyStorePassword = "",
                    secondaryUrls = form.secondaryUrls,
                    customHeaders = form.customHeaders,
                ),
            )
            connectionForm.isOpen = true
            appRoute = DesktopAppRoute.Settings
        }
    }

    val downloadJobsController = remember {
        NaviampDownloadJobController(
            jobs = { downloadJobs },
            setJobs = { jobs -> downloadJobs = jobs },
        )
    }
    val downloadCoordinator = remember(storage, downloadJobsController) {
        NaviampDownloadCoordinator(
            downloadRepository = storage,
            downloadReplacementRepository = storage,
            keepDownloadedRepository = storage,
            jobs = downloadJobsController,
            downloadedTrackId = { download: DownloadedTrack -> download.track.id.value },
            loadStats = { withContext(Dispatchers.IO) { storage.stats() } },
        )
    }
    val applicationServices = remember(
        storage,
        settingsStore,
        downloadJobsController,
        downloadCoordinator,
    ) {
        NaviampApplicationServices(
            settingsSync = NaviampSettingsSyncController(
                deviceId = DesktopSettingsSyncDeviceId,
                state = ::settingsSyncRuntimeState,
                saveState = ::saveSettingsSyncRuntimeState,
                nowEpochMillis = { System.currentTimeMillis() },
                snapshot = {
                    SettingsSyncLocalSnapshot(
                        serverProfiles = storage.mediaSources(),
                        interfaceSettings = interfaceSettings,
                        playback = playbackSettings,
                        visualizer = VisualizerSettings(
                            selectedVisualizer = nowPlayingPresentation.selectedVisualizer.name,
                        ),
                        recentRadioStreams = recentRadioStreams,
                        recentInternetRadioStations = settingsStore.loadRecentInternetRadioStations(),
                    )
                },
                applyDocument = ::applySettingsSyncDocument,
            ),
            cacheSettings = NaviampCacheSettingsController(
                setSettings = { settings -> cacheSettings = settings },
                saveSettings = settingsStore::saveCacheSettings,
            ),
            cacheMaintenance = NaviampCacheMaintenanceController(
                repository = storage,
                setStatus = { status ->
                    applicationControllers.status.publish(
                        area = NaviampApplicationStatusArea.CacheMaintenance,
                        level = NaviampApplicationStatusLevel.Information,
                        message = status,
                    )
                },
            ),
            downloadJobs = downloadJobsController,
            downloads = downloadCoordinator,
        )
    }
    val settingsSyncController = applicationServices.settingsSync
    val cacheSettingsController = applicationServices.cacheSettings

    fun publishSettingsSyncStatus(
        message: String,
        level: NaviampApplicationStatusLevel = NaviampApplicationStatusLevel.Information,
    ) {
        settingsSyncStatus = message
        applicationControllers.status.publish(
            area = NaviampApplicationStatusArea.SettingsSync,
            level = level,
            message = message,
        )
    }

    fun updateSettingsSyncDirectory(path: String?) {
        saveSettingsSyncSettings(DesktopSettingsSyncSettings(
            directoryPath = path,
            autoExportEnabled = settingsSyncSettings.autoExportEnabled && path != null,
            lastLocalUpdateEpochMillis = settingsSyncSettings.lastLocalUpdateEpochMillis,
            lastAppliedSyncUpdateEpochMillis = settingsSyncSettings.lastAppliedSyncUpdateEpochMillis,
        ))
        settingsSyncStatus = settingsSyncLocationStatus(settingsSyncSettings.directoryPath != null)
    }

    fun writeSettingsSync(
        document: SettingsSyncDocument,
        statusMessage: (String) -> String,
    ) {
        val directory = settingsSyncDirectory()
        if (directory == null) {
            settingsSyncStatus = "Choose a settings sync folder first."
            return
        }
        val documentStore = DesktopSettingsSyncDocumentStore(directory)
        runCatching {
            documentStore.write(document)
            documentStore.displayName
        }.onSuccess { fileName ->
            settingsSyncController.documentWritten(document)
            publishSettingsSyncStatus(statusMessage(fileName))
        }.onFailure { error ->
            publishSettingsSyncStatus(
                error.message ?: "Could not export settings sync file.",
                NaviampApplicationStatusLevel.Error,
            )
        }
    }

    fun exportSettingsSync() {
        settingsSyncController.exportCurrent(markChanged = true).documentToWrite?.let { document ->
            writeSettingsSync(document) { fileName -> "Settings exported to $fileName." }
        }
    }

    fun autoExportSettingsSync() {
        settingsSyncController.autoExport()?.documentToWrite?.let { document ->
            writeSettingsSync(document) { fileName -> "Settings auto-exported to $fileName." }
        }
    }

    fun updateSettingsSyncAutoExport(enabled: Boolean) {
        saveSettingsSyncSettings(settingsSyncSettings.copy(
            autoExportEnabled = enabled && settingsSyncSettings.directoryPath != null,
        ))
        settingsSyncStatus = settingsSyncAutoExportStatus(settingsSyncSettings.autoExportEnabled)
        if (settingsSyncSettings.autoExportEnabled) {
            autoExportSettingsSync()
        }
    }

    fun savePlaybackSettingsForSync(settings: PlaybackSettings) {
        settingsStore.savePlaybackSettings(settings)
        settingsSyncController.markLocalChanged()
        autoExportSettingsSync()
    }

    fun saveVisualizerSettingsForSync(settings: VisualizerSettings) {
        settingsStore.saveVisualizerSettings(settings)
        settingsSyncController.markLocalChanged()
        autoExportSettingsSync()
    }

    fun saveRecentRadioStreamsForSync(streams: List<RecentRadioStream>) {
        settingsStore.saveRecentRadioStreams(streams)
        settingsSyncController.markLocalChanged()
        autoExportSettingsSync()
    }

    fun saveRecentInternetRadioStationsForSync(stations: List<SavedInternetRadioStation>) {
        settingsStore.saveRecentInternetRadioStations(stations)
        settingsSyncController.markLocalChanged()
        autoExportSettingsSync()
    }

    fun markAndAutoExportSettingsSync() {
        settingsSyncController.markLocalChanged()
        autoExportSettingsSync()
    }

    fun rememberRadioStream(stream: RecentRadioStream) {
        rememberDesktopRadioStream(
            stream = stream,
            recentRadioStreams = recentRadioStreams,
            setRecentRadioStreams = { streams -> recentRadioStreams = streams },
            saveRecentRadioStreams = ::saveRecentRadioStreamsForSync,
            homeContent = homeContent,
            setHomeContent = { content -> homeContent = content },
        )
    }

    fun importSettingsSyncFromDirectory(directory: Path) {
        runCatching {
            val document = DesktopSettingsSyncDocumentStore(directory).read()
                ?: error("No settings sync file found in that folder.")
            settingsSyncController.applySyncedDocument(document)
        }.onSuccess { result ->
            publishSettingsSyncStatus(settingsSyncImportStatus(result.hasServerProfiles))
        }.onFailure { error ->
            publishSettingsSyncStatus(
                error.message ?: "Could not import settings sync file.",
                NaviampApplicationStatusLevel.Error,
            )
        }
    }

    fun importSettingsSync() {
        val directory = settingsSyncDirectory()
        if (directory == null) {
            settingsSyncStatus = "Choose a settings sync folder first."
            return
        }
        importSettingsSyncFromDirectory(directory)
    }

    fun selectSettingsSyncDirectoryAndImport(path: String) {
        saveSettingsSyncSettings(
            DesktopSettingsSyncSettings(
                directoryPath = path,
                autoExportEnabled = settingsSyncSettings.autoExportEnabled,
                lastLocalUpdateEpochMillis = settingsSyncSettings.lastLocalUpdateEpochMillis,
                lastAppliedSyncUpdateEpochMillis = settingsSyncSettings.lastAppliedSyncUpdateEpochMillis,
            ),
        )
        importSettingsSyncFromDirectory(Path.of(path))
    }

    LaunchedEffect(Unit) {
        val directory = settingsSyncDirectory() ?: return@LaunchedEffect
        runCatching {
            val providerDocument = DesktopSettingsSyncDocumentStore(directory).read()
            settingsSyncController.reconcileDocuments(
                localMirrorDocument = null,
                providerDocument = providerDocument,
                syncLocationConfigured = true,
            )
        }.onSuccess { reconciliation ->
            val result = reconciliation.result
            if (result.kind == SettingsSyncOperationKind.Exported) {
                result.documentToWrite?.let { document ->
                    writeSettingsSync(document) { fileName -> "Settings sync exported local settings to $fileName." }
                }
            } else {
                publishSettingsSyncStatus(settingsSyncReconciliationStatus(result))
            }
        }.onFailure { error ->
            publishSettingsSyncStatus(
                error.message ?: "Could not check settings sync folder.",
                NaviampApplicationStatusLevel.Error,
            )
        }
    }

    val playlistCallbacksRef = remember { mutableStateOf<PlaylistCallbacks?>(null) }
    val radioController = remember {
        DesktopRadioController(
        scope = coroutineScope,
        libraryIndexRepository = storage,
        providerResponseService = ProviderResponseService(storage),
        playlistEngine = playlistEngine,
        queueCoordinator = queueCoordinator,
        provider = { connectedProvider },
        sourceId = { connectedSourceId },
        streamQuality = { playbackSettings.streamQuality(playbackEngine) },
        replayGainMode = { playbackSettings.replayGainMode },
        replayGainPreampDb = { playbackSettings.replayGainPreampDb },
        preferSonicSimilarity = { playbackSettings.sonicSimilarityEnabled },
        radioTuning = { playbackSettings.radioTuning },
        repeatMode = { repeatMode },
        playlistCallbacks = { playlistCallbacksRef.value ?: error("Playlist callbacks are not ready.") },
        rememberRadioStream = ::rememberRadioStream,
        clearShuffleSnapshot = playbackController::clearShuffleSnapshot,
        resetNowPlayingSidecars = {
            nowPlayingController.resetAnalysis("Waiting")
            nowPlayingController.incrementWaveformReloadToken()
        },
        setConnectionStatus = { status -> connectionStatus = status },
        radioSessionId = { radioSessionId },
        setRadioSessionId = { sessionId -> radioSessionId = sessionId },
        isRadioQueueActive = { radioQueueActive },
        setRadioQueueActive = { isActive -> radioQueueActive = isActive },
        isRadioRefilling = { isRadioRefilling },
        setRadioRefilling = { isRefilling -> isRadioRefilling = isRefilling },
        lastRadioRefillSeedId = { lastRadioRefillSeedId },
        setLastRadioRefillSeedId = { trackId -> lastRadioRefillSeedId = trackId },
        setOpenPlayerOnTrackStart = { shouldOpen -> openPlayerOnTrackStart = shouldOpen },
    )
    }

    val internetRadioController = remember {
        DesktopInternetRadioController(
        scope = coroutineScope,
        settingsStore = settingsStore,
        playbackSessions = playbackSessions,
        playbackEngine = playbackEngine,
        playlistEngine = playlistEngine,
        provider = { connectedProvider },
        stationManager = InternetRadioStationManager(ProviderResponseService(storage)),
        homeContent = { homeContent },
        setHomeContent = { content -> homeContent = content },
        initialRecentStations = savedRecentInternetRadioStations.map { it.toStation() },
        saveRecentInternetRadioStations = ::saveRecentInternetRadioStationsForSync,
        stopRadioContinuation = radioController::stopContinuation,
        clearShuffleSnapshot = playbackController::clearShuffleSnapshot,
        setNowPlayingTrack = { track -> nowPlayingTrack = track },
        nowPlayingTrack = { nowPlayingTrack },
        setNowPlayingCoverArtUrl = { url -> nowPlayingCoverArtUrl = url },
        setNowPlayingWaveform = nowPlayingController::updateWaveform,
        setNowPlayingWaveformStatus = nowPlayingController::updateWaveformStatus,
        setNowPlayingAudioTags = nowPlayingController::updateAudioTags,
        setNowPlayingLyrics = nowPlayingController::setNowPlayingLyricsWithSavedOffset,
        setNowPlayingLyricsStatus = nowPlayingController::updateLyricsStatus,
        nowPlayingStation = { nowPlayingInternetRadioStation },
        setNowPlayingStation = { station -> nowPlayingInternetRadioStation = station },
        setNowPlayingStreamMetadata = { metadata -> nowPlayingStreamMetadata = metadata },
        playbackProgress = { playbackProgress },
        setPlaybackProgress = { progress -> playbackProgress = progress },
        setPlaybackQueue = { queue -> playbackQueue = queue },
        setPlaybackState = { state -> playbackState = state },
        lastProgressUiUpdateMillis = { lastPlaybackProgressUiUpdateMillis },
        setLastProgressUiUpdateMillis = { millis -> lastPlaybackProgressUiUpdateMillis = millis },
        restoredPlaybackPositionSeconds = { restoredPlaybackPositionSeconds },
        setRestoredPlaybackPositionSeconds = { position -> restoredPlaybackPositionSeconds = position },
        setAppRoute = { route -> appRoute = route },
    )
    }

    fun handlePlayPauseCommand() {
        playbackController.handlePlayPauseCommand {
            openPlayerOnTrackStart = false
            internetRadioController.playCurrentSelection()
            true
        }
    }

    val libraryController = remember {
        DesktopLibraryController(
            scope = coroutineScope,
            libraryIndexRepository = storage,
            cacheMaintenance = applicationServices.cacheMaintenance,
        provider = { connectedProvider },
        sourceId = { connectedSourceId },
        setConnectionStatus = { status -> connectionStatus = status },
        listState = libraryListState,
    )
    }

    val loadHomeContentAction = remember { mutableStateOf<(NavidromeProvider) -> Unit>({}) }
    val refreshPlaylistsAction = remember { mutableStateOf<() -> Unit>({}) }

    val connectionLifecycleController = remember {
        DesktopConnectionLifecycleController(
        scope = coroutineScope,
        cacheMaintenanceRepository = storage,
        mediaSourceRepository = storage,
        providerMediaSourceRepository = storage,
        settingsStore = settingsStore,
        playbackSessions = playbackSessions,
        connectionController = applicationControllers.connection,
        providerActions = applicationControllers.providerActions,
        playbackEngine = playbackEngine,
        playlistEngine = playlistEngine,
        stopRadioContinuation = radioController::stopContinuation,
        clearShuffleSnapshot = playbackController::clearShuffleSnapshot,
        applyClearedConnectionState = { state ->
            connectedProvider = state.connectedProvider
            connectedSourceId = state.connectedSourceId
            libraryController.applyClearedState(state.librarySnapshot, state.libraryStatus)
            homeContent = state.homeContent
            homeStatus = state.homeStatus
            nowPlayingTrack = state.nowPlayingTrack
            nowPlayingCoverArtUrl = state.nowPlayingCoverArtUrl
            nowPlayingController.updateLyricsStatus(state.nowPlayingLyricsStatus)
            playbackState = state.playbackState
            playbackProgress = state.playbackProgress
            playbackQueue = state.playbackQueue
        },
        serverUrl = { connectionForm.serverUrl },
        username = { connectionForm.username },
        password = { connectionForm.password },
        clearPassword = connectionForm::clearPassword,
        connectionName = { connectionForm.connectionName },
        insecureSkipTlsVerification = { connectionForm.insecureSkipTlsVerification },
        customCertificatePath = { connectionForm.customCertificatePath },
        clientCertificateKeyStorePath = { connectionForm.clientCertificateKeyStorePath },
        clientCertificateKeyStorePassword = { connectionForm.clientCertificateKeyStorePassword },
        secondaryUrls = { connectionForm.secondaryUrls.toConnectionSecondaryUrls() },
        customHeaders = { connectionForm.customHeaders.toConnectionHeaderDefinitions() },
        selectedMusicFolderIds = { connectionForm.selectedMusicFolderIds },
        playlistCallbacks = { playlistCallbacksRef.value ?: error("Playlist callbacks are not ready.") },
        streamQuality = { playbackSettings.streamQuality(playbackEngine) },
        replayGainMode = { playbackSettings.replayGainMode },
        replayGainPreampDb = { playbackSettings.replayGainPreampDb },
        startPlayingOnLaunch = { interfaceSettings.startPlayingOnLaunch },
        setConnectedProvider = { provider -> connectedProvider = provider },
        setConnectedSourceId = { sourceId -> connectedSourceId = sourceId },
        setHomeContent = { content -> homeContent = content },
        setHomeStatus = { status -> homeStatus = status },
        setNowPlayingInternetRadioStation = { station -> nowPlayingInternetRadioStation = station },
        setNowPlayingStreamMetadata = { metadata -> nowPlayingStreamMetadata = metadata },
        setNowPlayingTrack = { track -> nowPlayingTrack = track },
        setNowPlayingCoverArtUrl = { url -> nowPlayingCoverArtUrl = url },
        setNowPlayingWaveform = nowPlayingController::updateWaveform,
        setNowPlayingWaveformStatus = nowPlayingController::updateWaveformStatus,
        setNowPlayingAudioTags = nowPlayingController::updateAudioTags,
        setNowPlayingLyrics = nowPlayingController::setNowPlayingLyricsWithSavedOffset,
        setNowPlayingLyricsStatus = nowPlayingController::updateLyricsStatus,
        incrementNowPlayingWaveformReloadToken = nowPlayingController::incrementWaveformReloadToken,
        setPlaybackState = { state -> playbackState = state },
        setPlaybackProgress = { progress -> playbackProgress = progress },
        setPlaybackQueue = { queue -> playbackQueue = queue },
        refreshLibrarySnapshot = libraryController::refreshLibrarySnapshot,
        loadHomeContent = { provider -> loadHomeContentAction.value(provider) },
        refreshPlaylists = { refreshPlaylistsAction.value() },
        refreshInternetRadioStations = internetRadioController::refreshStations,
        startLibrarySync = { libraryController.refreshArtistIndex() },
        checkLibraryFreshness = {},
        connectedSourceId = { connectedSourceId },
        savedConnectionForLogin = { connectionForm.savedConnectionForLogin },
        setSavedConnectionForLogin = { connection -> connectionForm.savedConnectionForLogin = connection },
        incrementMediaSourcesRevision = { mediaSourcesRevision++ },
        applyConnectionFormState = connectionForm::apply,
        setConnectionFormOpen = { isOpen -> connectionForm.isOpen = isOpen },
        setConnectionStatus = { status -> connectionStatus = status },
        setAppRoute = { route -> appRoute = route },
        appRoute = { appRoute },
        onSyncedSettingsChanged = ::markAndAutoExportSettingsSync,
    )
    }

    val playlistCallbacks = desktopPlaylistCallbacks(
        provider = { connectedProvider },
        appRoute = { appRoute },
        setAppRoute = { route -> appRoute = route },
        openPlayerOnTrackStart = {
            openPlayerOnTrackStart.also { shouldOpen ->
                if (shouldOpen) openPlayerOnTrackStart = false
            }
        },
        nowPlayingTrack = { nowPlayingTrack },
        setNowPlayingTrack = { track -> nowPlayingTrack = track },
        setNowPlayingCoverArtUrl = { url -> nowPlayingCoverArtUrl = url },
        setNowPlayingWaveform = nowPlayingController::updateWaveform,
        setNowPlayingWaveformStatus = nowPlayingController::updateWaveformStatus,
        setNowPlayingAudioTags = nowPlayingController::updateAudioTags,
        setNowPlayingLyrics = nowPlayingController::setNowPlayingLyricsWithSavedOffset,
        setNowPlayingLyricsStatus = nowPlayingController::updateLyricsStatus,
        setNowPlayingInternetRadioStation = { station -> nowPlayingInternetRadioStation = station },
        setNowPlayingStreamMetadata = { metadata -> nowPlayingStreamMetadata = metadata },
        incrementPlayReportSessionId = { playReportSessionId++ },
        incrementNowPlayingWaveformReloadToken = nowPlayingController::incrementWaveformReloadToken,
        reportNowPlaying = playbackController::reportNowPlaying,
        maybeReportPlaybackState = playbackController::maybeReportPlaybackState,
        clearShuffleSnapshot = playbackController::clearShuffleSnapshot,
        refillRadioIfNeeded = radioController::refillIfNeeded,
        activeQueue = { playlistEngine.queue },
        setPlaybackQueue = { queue -> playbackQueue = queue },
        savePlaybackSession = playbackController::savePlaybackSession,
        playbackProgress = { playbackProgress },
        setPlaybackProgress = { progress -> playbackProgress = progress },
        setPlaybackState = { state -> playbackState = state },
        pendingSeekPositionSeconds = { pendingSeekPositionSeconds },
        setPendingSeekPositionSeconds = { position -> pendingSeekPositionSeconds = position },
        pendingSeekIssuedAtMillis = { pendingSeekIssuedAtMillis },
        setPendingSeekIssuedAtMillis = { millis -> pendingSeekIssuedAtMillis = millis },
        lastPlaybackProgressUiUpdateMillis = { lastPlaybackProgressUiUpdateMillis },
        setLastPlaybackProgressUiUpdateMillis = { millis -> lastPlaybackProgressUiUpdateMillis = millis },
        maybeSavePlaybackPosition = playbackController::maybeSavePlaybackPosition,
    )
    playlistCallbacksRef.value = playlistCallbacks

    val mixBuilderController = rememberDesktopMixBuilderController(
        scope = coroutineScope,
        storage = storage,
        sourceId = { connectedSourceId },
        provider = { connectedProvider },
        homeContent = { homeContent },
        popularTracksService = popularTracksService,
        similarArtistsService = similarArtistsService,
    )

    val sonicPathController = remember {
        DesktopSonicPathController(
            scope = coroutineScope,
            playbackEngine = playbackEngine,
            playlistEngine = playlistEngine,
            provider = { connectedProvider },
            playbackSettings = { playbackSettings },
            playlistCallbacks = { playlistCallbacksRef.value ?: error("Playlist callbacks are not ready.") },
            stopRadioContinuation = radioController::stopContinuation,
            clearShuffleSnapshot = playbackController::clearShuffleSnapshot,
            setOpenPlayerOnTrackStart = { shouldOpen -> openPlayerOnTrackStart = shouldOpen },
            setConnectionStatus = { status -> connectionStatus = status },
        )
    }

    val sonicMixController = remember {
        DesktopSonicMixController(
            scope = coroutineScope,
            playbackEngine = playbackEngine,
            playlistEngine = playlistEngine,
            provider = { connectedProvider },
            playbackSettings = { playbackSettings },
            playlistCallbacks = { playlistCallbacksRef.value ?: error("Playlist callbacks are not ready.") },
            stopRadioContinuation = radioController::stopContinuation,
            clearShuffleSnapshot = playbackController::clearShuffleSnapshot,
            setOpenPlayerOnTrackStart = { shouldOpen -> openPlayerOnTrackStart = shouldOpen },
            setConnectionStatus = { status -> connectionStatus = status },
        )
    }

    val sonicHomeDiscoveryController = remember {
        DesktopSonicHomeDiscoveryController(
            scope = coroutineScope,
            storage = storage,
            playbackEngine = playbackEngine,
            playlistEngine = playlistEngine,
            provider = { connectedProvider },
            sourceId = { connectedSourceId },
            recentTracks = {
                listOfNotNull(nowPlayingTrack) + playlistEngine.queue.tracks
                    .filterNot { track -> track.id == nowPlayingTrack?.id }
            },
            playbackSettings = { playbackSettings },
            playlistCallbacks = { playlistCallbacksRef.value ?: error("Playlist callbacks are not ready.") },
            stopRadioContinuation = radioController::stopContinuation,
            clearShuffleSnapshot = playbackController::clearShuffleSnapshot,
            setOpenPlayerOnTrackStart = { shouldOpen -> openPlayerOnTrackStart = shouldOpen },
            setConnectionStatus = { status -> connectionStatus = status },
        )
    }

    LaunchedEffect(
        connectedProvider,
        connectedSourceId,
        playbackSettings.sonicSimilarityEnabled,
        connectedProvider?.capabilities?.supportsSonicSimilarity,
        libraryController.syncing,
        nowPlayingTrack?.id,
        playbackQueue.tracks.size,
    ) {
        val enabled = playbackSettings.sonicSimilarityEnabled &&
            connectedProvider?.capabilities?.supportsSonicSimilarity == true &&
            !libraryController.syncing
        sonicHomeDiscoveryController.loadIfNeeded(enabled)
    }

    val searchController = remember {
        DesktopSearchController(
        settingsStore = settingsStore,
        providerResponseCacheRepository = storage,
        provider = { connectedProvider },
        cacheSettings = { cacheSettings },
        downloadedTracks = {
            connectedSourceId
                ?.let { storage.downloadedTracks(it) }
                .orEmpty()
                .map { it.track }
        },
        initialQuery = savedSearch.query,
    )
    }

    val downloadsController = remember {
        DesktopDownloadsController(
        scope = coroutineScope,
            downloadRepository = storage,
        keepDownloadedRepository = storage,
        cacheMaintenanceRepository = storage,
        jobController = applicationServices.downloadJobs,
        downloads = applicationServices.downloads,
        applicationStatus = applicationControllers.status,
        providerResponseCacheRepository = storage,
        playbackEngine = playbackEngine,
        playbackSettings = { playbackSettings },
        cacheSettings = { cacheSettings },
        provider = { connectedProvider },
        sourceId = { connectedSourceId },
        stopRadioContinuation = radioController::stopContinuation,
        clearShuffleSnapshot = playbackController::clearShuffleSnapshot,
        setOpenPlayerOnTrackStart = { shouldOpen -> openPlayerOnTrackStart = shouldOpen },
        playlistEngine = playlistEngine,
        playlistCallbacks = { playlistCallbacks },
        setCacheStats = { stats -> cacheStats = stats },
    )
    }

    val settingsMaintenanceController = remember {
        PlaybackSettingsMaintenanceController(
        playbackEngine = playbackEngine,
        playbackSettings = { playbackSettings },
        setPlaybackSettings = { settings -> playbackSettings = settings },
        savePlaybackSettings = ::savePlaybackSettingsForSync,
        reloadLyricsSidecars = nowPlayingController::clearLyricsAndReloadAnalysis,
        radioDjPresetRepository = storage,
        downloadedTracks = {
            connectedSourceId
                ?.let { storage.downloadedTracks(it) }
                .orEmpty()
                .map { it.track }
        },
        redownloadTracks = downloadsController::redownloadTracks,
    )
    }
    val playlistsController = remember {
        DesktopPlaylistsController(
        scope = coroutineScope,
        settingsStore = settingsStore,
        playbackEngine = playbackEngine,
        playlistEngine = playlistEngine,
        queueCoordinator = queueCoordinator,
        providerResponseService = ProviderResponseService(storage),
        provider = { connectedProvider },
        playbackSettings = { playbackSettings },
        playlistCallbacks = { playlistCallbacks },
        initialRecentPlaylistIds = savedRecentPlaylistIds,
        homeContent = { homeContent },
        setHomeContent = { content -> homeContent = content },
        setConnectionStatus = { status -> connectionStatus = status },
        setAppRoute = { route -> appRoute = route },
        stopRadioContinuation = radioController::stopContinuation,
        clearShuffleSnapshot = playbackController::clearShuffleSnapshot,
        setOpenPlayerOnTrackStart = { shouldOpen -> openPlayerOnTrackStart = shouldOpen },
    )
    }

    val smartPlaylistsController = remember {
        DesktopSmartPlaylistsController(
        providerMediaSourceRepository = storage,
        providerResponseCacheRepository = storage,
        settingsStore = settingsStore,
        provider = { connectedProvider },
        setProvider = { provider -> connectedProvider = provider },
        password = { connectionForm.password },
        clearPassword = connectionForm::clearPassword,
        savedConnectionForLogin = { connectionForm.savedConnectionForLogin },
        setSavedConnectionForLogin = { connection -> connectionForm.savedConnectionForLogin = connection },
        setConnectedSourceId = { sourceId -> connectedSourceId = sourceId },
        incrementMediaSourcesRevision = { mediaSourcesRevision++ },
        incrementStatsForNerdsRefreshTick = { statsForNerdsRefreshTick++ },
        playlistsController = playlistsController,
        setConnectionStatus = { status -> connectionStatus = status },
    )
    }

    val artistController = remember {
        DesktopArtistController(
        scope = coroutineScope,
        libraryIndexRepository = storage,
        providerResponseCacheRepository = storage,
        provider = { connectedProvider },
        sourceId = { connectedSourceId },
        currentRoute = { appRoute },
        lastContentRoute = { lastContentRoute },
        setRoute = { route -> appRoute = route },
        popularTracksService = popularTracksService,
        similarArtistsService = similarArtistsService,
    )
    }

    val albumController = remember {
        DesktopAlbumController(
        scope = coroutineScope,
        libraryIndexRepository = storage,
        providerResponseCacheRepository = storage,
        provider = { connectedProvider },
        sourceId = { connectedSourceId },
        currentRoute = { appRoute },
        lastContentRoute = { lastContentRoute },
        setRoute = { route -> appRoute = route },
    )
    }

    val providerActionController = applicationControllers.providerActions

    val mediaActionsController = remember {
        DesktopMediaActionsController(
        scope = coroutineScope,
        trackMetadataRepository = storage,
        playbackEngine = playbackEngine,
        playlistEngine = playlistEngine,
        queueCoordinator = queueCoordinator,
        provider = {
            connectedProvider?.let { provider ->
                providerActionController.offlineCapable(provider, connectedSourceId)
            }
        },
        playbackSettings = { playbackSettings },
        playlistCallbacks = { playlistCallbacks },
        albumTracks = { albumController.selectedAlbumDetails?.tracks.orEmpty() },
        searchTracks = { searchController.results.tracks },
        relatedTracks = { nowPlayingController.relatedTracks },
        nowPlayingTrack = { nowPlayingTrack },
        setNowPlayingTrack = { track -> nowPlayingTrack = track },
        searchResults = { searchController.results },
        setSearchResults = searchController::updateResults,
        homeContent = { homeContent },
        setHomeContent = { content -> homeContent = content },
        selectedAlbumDetails = { albumController.selectedAlbumDetails },
        setSelectedAlbumDetails = albumController::updateSelectedAlbumDetails,
        selectedArtistDetails = { artistController.selectedArtistDetails },
        setSelectedArtistDetails = artistController::updateSelectedArtistDetails,
        setArtistMixSelectedArtists = mixBuilderController::updateSelectedArtist,
        setArtistMixSuggestions = mixBuilderController::updateSuggestedArtist,
        setAlbumMixSelectedAlbums = mixBuilderController::updateSelectedAlbum,
        setAlbumMixSuggestions = mixBuilderController::updateSuggestedAlbum,
        stopRadioContinuation = radioController::stopContinuation,
        clearShuffleSnapshot = playbackController::clearShuffleSnapshot,
        setOpenPlayerOnTrackStart = { shouldOpen -> openPlayerOnTrackStart = shouldOpen },
        setConnectionStatus = { status -> connectionStatus = status },
    )
    }

    val homeController = remember {
        DesktopHomeController(
        scope = coroutineScope,
        providerResponseCacheRepository = storage,
        homeLibraryRepository = storage.asHomeLibraryRepository(),
        sourceId = { connectedSourceId },
        recentRadioStreams = { recentRadioStreams },
        recentInternetRadioStations = { internetRadioController.recentStations },
        setHomeContent = { content -> homeContent = content },
        setHomeStatus = { status -> homeStatus = status },
    )
    }

    val appActions = remember {
        DesktopAppActions(
        connectionLifecycleController = connectionLifecycleController,
        albumController = albumController,
        artistController = artistController,
        mediaActionsController = mediaActionsController,
        downloadsController = downloadsController,
        radioController = radioController,
        internetRadioController = internetRadioController,
        playlistsController = playlistsController,
        libraryController = libraryController,
        homeContent = { homeContent },
        playlists = { playlistsController.playlists },
        internetRadioStations = { internetRadioController.stations },
        selectedAlbum = { albumController.selectedAlbum },
        selectedAlbumDetails = { albumController.selectedAlbumDetails },
        selectedArtistPopularTracks = { artistController.selectedArtistPopularTracks },
        selectedPlaylist = { playlistsController.selectedPlaylist },
        selectedPlaylistTracks = { playlistsController.selectedPlaylistTracks },
    )
    }

    DesktopAppEffects(
        playbackEngine = playbackEngine,
        playlistEngine = playlistEngine,
        imageCacheRepository = imageCacheRepository,
        connectedProvider = connectedProvider,
        reportNowPlaying = { trackId -> playbackController.reportNowPlaying(trackId) },
        nowPlayingTrack = nowPlayingTrack,
        playbackState = playbackState,
        nowPlayingVisualizerVisible = nowPlayingVisualizerVisible,
        appRoute = appRoute,
        playbackSettings = playbackSettings,
        changePlaybackVolume = { percent -> playbackController.changeVolume(percent) },
        cacheSettings = cacheSettings,
        albumDetailBackRoute = albumController.albumDetailBackRoute,
        artistDetailBackRoute = artistController.artistDetailBackRoute,
        lastContentRoute = lastContentRoute,
        setLastContentRoute = { route -> lastContentRoute = route },
        setNowPlayingVisualizerFrame = nowPlayingPresentation::updateVisualizerFrame,
        updateAudioCacheLimit = { maxBytes -> storage.updateAudioCacheLimit(maxBytes) },
        updateAudioCacheDirectory = { path ->
            runCatching { storage.updateAudioCacheDirectory(path?.let(java.nio.file.Path::of) ?: defaultAudioCacheDirectory()) }
        },
        updateDownloadDirectory = { path ->
            runCatching {
                storage.updateDownloadDirectory(DesktopDownloadDirectories.fromSetting(path))
            }
        },
        cancelAudioPrefetch = { playlistEngine.cancelAudioPrefetch() },
        saveNavigationSettings = settingsStore::saveNavigationSettings,
    )

    DesktopAppControllerEffects(
        nowPlayingController = nowPlayingController,
        playlistsController = playlistsController,
        searchController = searchController,
        libraryController = libraryController,
        mixBuilderController = mixBuilderController,
        applicationControllers = applicationControllers,
        playbackSessions = playbackSessions,
        playbackExecution = playbackController,
        applicationServices = applicationServices,
        hasSavedConnection = savedConnection != null,
        connectToServer = { connectionLifecycleController.connectToServer(restoreSavedSession = true) },
        nowPlayingTrack = nowPlayingTrack,
        connectedSourceId = connectedSourceId,
        connectedProvider = connectedProvider,
        playbackEngine = playbackEngine,
        nowPlayingWaveformReloadToken = nowPlayingController.waveformReloadToken,
        cacheSettings = cacheSettings,
        playbackSettings = playbackSettings,
        nowPlayingLyricsVisible = nowPlayingLyricsVisible,
        selectedVisualizer = nowPlayingPresentation.selectedVisualizer,
        appRoute = appRoute,
        selectedPlaylist = playlistsController.selectedPlaylist,
        homeContent = homeContent,
        showStatsForNerds = showStatsForNerds,
        statsForNerdsRefreshTick = statsForNerdsRefreshTick,
        incrementStatsForNerdsRefreshTick = { statsForNerdsRefreshTick++ },
        downloadRefreshToken = downloadsController.refreshToken,
        mediaSourcesRevision = mediaSourcesRevision,
        loadStorageStats = { storage.stats() },
        setCacheStats = { stats -> cacheStats = stats },
    )

    LaunchedEffect(
        connectionForm.isOpen,
        connectionForm.serverUrl,
        connectionForm.username,
        connectionForm.password,
        connectionForm.insecureSkipTlsVerification,
        connectionForm.customCertificatePath,
        connectionForm.clientCertificateKeyStorePath,
        connectionForm.clientCertificateKeyStorePassword,
        connectionForm.secondaryUrls,
        connectionForm.customHeaders,
        connectionForm.savedConnectionForLogin,
    ) {
        if (!connectionForm.isOpen) {
            musicFoldersStatus = null
            return@LaunchedEffect
        }
        val baseUrl = connectionForm.serverUrl.trim()
        val username = connectionForm.username.trim()
        val savedLogin = connectionForm.savedConnectionForLogin
        val password = connectionForm.password
        if (baseUrl.isEmpty() || username.isEmpty() || (savedLogin == null && password.isBlank())) {
            availableMusicFolders = emptyList()
            musicFoldersStatus = "Enter connection details to load libraries."
            return@LaunchedEffect
        }

        musicFoldersStatus = "Loading libraries..."
        val tlsSettings = ConnectionTlsSettings(
            insecureSkipTlsVerification = connectionForm.insecureSkipTlsVerification,
            customCertificatePath = connectionForm.customCertificatePath.ifBlank { null },
            clientCertificateKeyStorePath = connectionForm.clientCertificateKeyStorePath.ifBlank { null },
            clientCertificateKeyStorePassword = connectionForm.clientCertificateKeyStorePassword.ifBlank { null },
        )
        val secondaryUrls = connectionForm.secondaryUrls.toConnectionSecondaryUrls()
        val customHeaders = connectionForm.customHeaders.toConnectionHeaderDefinitions()
        val lookupConnection = if (savedLogin != null && password.isBlank()) {
            savedLogin.copy(
                baseUrl = baseUrl,
                username = username,
                tlsSettings = tlsSettings,
                secondaryUrls = secondaryUrls,
                customHeaders = customHeaders,
            )
        } else {
            NavidromeConnection.fromPassword(
                baseUrl = baseUrl,
                username = username,
                password = password,
                displayName = connectionForm.connectionName.ifBlank { null },
                tlsSettings = tlsSettings,
                secondaryUrls = secondaryUrls,
                customHeaders = customHeaders,
            )
        }
        val result = runCatching {
            withContext(Dispatchers.IO) {
                NavidromeProvider(lookupConnection).musicFolders()
            }
        }
        result.fold(
            onSuccess = { folders ->
                val choices = connectionFormMusicFolders(folders.map { folder -> folder.id to folder.name })
                availableMusicFolders = choices
                musicFoldersStatus = when {
                    choices.isEmpty() -> "No libraries returned by the server."
                    else -> null
                }
                connectionForm.selectedMusicFolderIds = defaultSelectedMusicFolderIds(
                    selectedIds = connectionForm.selectedMusicFolderIds,
                    availableFolders = choices,
                )
            },
            onFailure = { error ->
                availableMusicFolders = emptyList()
                musicFoldersStatus = "Could not load libraries: ${error.message ?: error::class.simpleName}"
            },
        )
    }

    LaunchedEffect(connectedProvider, connectedSourceId, connectionForm.isOpen) {
        val provider = connectedProvider
        if (connectionForm.isOpen || provider == null) return@LaunchedEffect
        runCatching {
            withContext(Dispatchers.IO) {
                provider.musicFolders()
            }
        }.onSuccess { folders ->
            availableMusicFolders = connectionFormMusicFolders(folders.map { folder -> folder.id to folder.name })
        }
    }

    LaunchedEffect(connectedSourceId) {
        downloadsController.reloadKeepDownloadedPolicies()
    }
    LaunchedEffect(
        playlistsController.playlists.map { playlist -> Triple(playlist.id, playlist.trackCount, playlist.isSmart) },
        nowPlayingTrack?.favoritedAtIso8601,
    ) {
        if (downloadsController.keepDownloadedPolicies.isNotEmpty()) {
            downloadsController.reconcileKeepDownloadedCollections()
        }
    }

    loadHomeContentAction.value = homeController::loadHomeContent
    refreshPlaylistsAction.value = playlistsController::refreshPlaylists

    val savedMediaSources = mediaSourcesRevision.let {
        storage.mediaSources().visibleServerConnections(connectedSourceId)
    }
    val shellConnection = NaviampShellConnectionUi(
        status = connectionStatus,
        serverVersion = connectionRuntimeState.serverVersion,
        connected = connectionRuntimeState.connected,
        editingConnection = connectionForm.isOpen,
        restoringConnection = connectionRuntimeState.restoringConnection,
        isConnecting = connectionRuntimeState.isConnecting,
        form = ConnectionFormState(
            displayName = connectionForm.connectionName,
            serverUrl = connectionForm.serverUrl,
            username = connectionForm.username,
            password = connectionForm.password,
            skipTlsVerification = connectionForm.insecureSkipTlsVerification,
            customCertificatePath = connectionForm.customCertificatePath,
            clientCertificatePath = connectionForm.clientCertificateKeyStorePath,
            clientCertificatePassword = connectionForm.clientCertificateKeyStorePassword,
            secondaryUrls = connectionForm.secondaryUrls,
            customHeaders = connectionForm.customHeaders,
            selectedMusicFolderIds = connectionForm.selectedMusicFolderIds,
        ),
        availableMusicFolders = availableMusicFolders,
        musicFoldersStatus = musicFoldersStatus,
        savedConnections = savedMediaSources.map { source ->
            NaviampSavedConnectionUi(
                id = source.id,
                displayName = source.displayName,
                serverUrl = source.baseUrl,
                username = source.username,
                selectedLibrarySummary = selectedMusicFolderSummary(
                    selectedIds = source.selectedMusicFolderIds,
                    availableFolders = availableMusicFolders,
                ),
                current = source.id == connectedSourceId,
            )
        },
        hasSavedConnection = connectionForm.savedConnectionForLogin != null,
    )
    val shellCapabilities = NaviampShellCapabilitiesUi(
        replayGain = playbackEngine.supportsReplayGain,
        gapless = playbackEngine.supportsGapless,
        crossfade = playbackEngine.supportsCrossfade,
        equalizer = (playbackEngine as? app.naviamp.domain.playback.EqualizerPlaybackEngine)
            ?.supportsEqualizer == true,
        sonicSimilarity = connectedProvider?.capabilities?.supportsSonicSimilarity == true,
        downloads = DesktopCapabilityPresentation.downloads.visible,
        settingsImportExport = DesktopCapabilityPresentation.settingsImportExport.visible,
        applicationUpdates = DesktopCapabilityPresentation.applicationUpdates.visible,
        fileSelection = DesktopCapabilityPresentation.fileSelection.visible,
        connection = NaviampConnectionCapabilitiesUi(
            insecureServerVerification = DesktopCapabilityPresentation.insecureServerVerification.visible,
            customServerCertificates = DesktopCapabilityPresentation.customServerCertificates.visible,
            clientCertificates = DesktopCapabilityPresentation.clientCertificates.visible,
        ),
    )
    val statsForNerdsInfo = desktopStatsForNerdsInfoOrNull(
        showStatsForNerds = showStatsForNerds,
        appRoute = appRoute,
        connectionForm = connectionForm,
        connectedProvider = connectedProvider,
        connectedSourceId = connectedSourceId,
        availableMusicFolders = availableMusicFolders,
        storage = storage,
        connectionStatus = connectionStatus,
        isLibrarySyncing = libraryController.syncing,
        libraryStatus = libraryController.status,
        libraryTab = libraryController.tab,
        libraryQuery = libraryController.query,
        librarySnapshot = libraryController.snapshot,
        playbackEngine = playbackEngine,
        playlistEngine = playlistEngine,
        playbackQueue = playbackQueue,
        nowPlayingTrack = nowPlayingTrack,
        playbackState = playbackState,
        playbackProgress = playbackProgress,
        playbackSettings = playbackSettings,
        nowPlayingWaveform = nowPlayingController.waveform,
        nowPlayingWaveformStatus = nowPlayingController.waveformStatus,
        nowPlayingInternetRadioStation = nowPlayingInternetRadioStation,
        nowPlayingStreamMetadata = nowPlayingStreamMetadata,
        cacheStats = cacheStats,
    )
    val handleNowPlayingPlaybackAction: (NowPlayingPlaybackActionRequest) -> Unit = { request ->
        when (request.action) {
            NowPlayingPlaybackAction.Pause -> handlePlayPauseCommand()
            NowPlayingPlaybackAction.Resume -> handlePlayPauseCommand()
            NowPlayingPlaybackAction.PlayCurrent -> handlePlayPauseCommand()
            NowPlayingPlaybackAction.Seek -> request.seekSeconds?.let(playbackController::performSeek)
            NowPlayingPlaybackAction.Previous -> playbackController.handlePreviousButton()
            NowPlayingPlaybackAction.Next -> playbackController.handleNextButton()
            NowPlayingPlaybackAction.ToggleShuffle -> playbackController.toggleShuffle()
            NowPlayingPlaybackAction.CycleRepeatMode -> playbackController.cycleRepeatMode()
            NowPlayingPlaybackAction.ChangeVolume -> request.volumePercent?.let { volumePercent ->
                playbackSettings = playbackSettingsChange(
                    requested = playbackSettings.copy(volumePercent = volumePercent),
                    playbackEngine = playbackEngine,
                    previous = playbackSettings,
                ).settings
                savePlaybackSettingsForSync(playbackSettings.copy(radioDjs = emptyList()))
            }
        }
    }
    val handleNowPlayingDisplayAction: (NowPlayingDisplayActionRequest) -> Unit = { request ->
        when (request.action) {
            NowPlayingDisplayAction.ToggleLyrics -> nowPlayingLyricsVisible = !nowPlayingLyricsVisible
            NowPlayingDisplayAction.ChangeLyricsOffset ->
                request.lyricsOffsetMillis?.let(nowPlayingController::handleLyricsOffsetChanged)
            NowPlayingDisplayAction.ToggleVisualizer -> nowPlayingPresentation.toggleVisualizer()
            NowPlayingDisplayAction.SelectVisualizer -> request.visualizer?.let { visualizer ->
                nowPlayingPresentation.selectVisualizer(visualizer)
                saveVisualizerSettingsForSync(
                    VisualizerSettings(selectedVisualizer = visualizer.name),
                )
            }
            NowPlayingDisplayAction.SelectRadioDj -> {
                val selectedDj = request.radioDjId
                    ?.let { id -> playbackSettings.radioDjs.firstOrNull { it.id == id } }
                playbackSettings = playbackSettings.copy(
                    radioTuning = selectedDj?.tuning ?: RadioTuningSettings(),
                    activeRadioDjId = selectedDj?.id,
                )
                savePlaybackSettingsForSync(playbackSettings)
                nowPlayingTrack?.let { track ->
                    radioController.convertCurrentTrackToRadio(track, radioController::playTrack)
                }
                connectionStatus = selectedDj
                    ?.let { "Selected ${it.name} DJ. Rebuilding Up Next..." }
                    ?: "Default radio selected. Rebuilding Up Next..."
            }
            NowPlayingDisplayAction.Collapse -> appRoute = lastContentRoute
        }
    }
    val handleNowPlayingQueueAction: (NowPlayingQueueActionRequest) -> Unit = { request ->
        when (request.action) {
            NowPlayingQueueAction.SaveQueueAsPlaylist ->
                request.playlistName?.let(playlistsController::saveQueueAsPlaylist)
            NowPlayingQueueAction.MoveToNext ->
                request.queueIndex?.let { index ->
                    queueCoordinator.moveToNext(index).takeIf { it.changed }?.let { update ->
                        playlistEngine.replaceQueue(
                            update.queue,
                            clearPreparedNext = update.clearPreparedNext,
                        )
                    }
                }
            NowPlayingQueueAction.RemoveFromQueue ->
                request.queueIndex?.let { index ->
                    queueCoordinator.removeAt(index).takeIf { it.changed }?.let { update ->
                        playlistEngine.replaceQueue(update.queue)
                    }
                }
            NowPlayingQueueAction.EmptyQueue ->
                queueCoordinator.clearUpcoming().takeIf { it.changed }?.let { update ->
                    playlistEngine.replaceQueue(
                        update.queue,
                        clearPreparedNext = update.clearPreparedNext,
                    )
                }
        }
    }
    val handleNowPlayingSleepTimerAction: (NowPlayingSleepTimerActionRequest) -> Unit = { request ->
        when (request.action) {
            NowPlayingSleepTimerAction.Select -> request.request?.let(sleepTimerController::select)
            NowPlayingSleepTimerAction.Cancel -> sleepTimerController.cancel()
        }
    }
    val handleNowPlayingSelectionAction: (NowPlayingSelectionActionRequest) -> Unit = { request ->
        when (request.action) {
            NowPlayingSelectionAction.SelectQueueItem ->
                nowPlayingQueueIndex(request.item)?.let(::handleQueueIndexSelected)
            NowPlayingSelectionAction.SelectRelatedItem ->
                nowPlayingRelatedIndex(request.item)?.let(appActions::playRelatedTrack)
            NowPlayingSelectionAction.SelectRadioStation ->
                internetRadioController.stations.firstOrNull { it.id == request.item.id }
                    ?.let(internetRadioController::playStation)
        }
    }

    CompositionLocalProvider(
        app.naviamp.ui.LocalTrackSwipeSettings provides interfaceSettings.trackSwipes,
        app.naviamp.ui.LocalNaviampTooltipsEnabled provides interfaceSettings.showDesktopTooltips,
    ) {
    DesktopAppSurface(
            colorScheme = colorScheme,
            appColors = appColors,
            statsForNerdsInfo = statsForNerdsInfo,
            backgroundStart = nowPlayingPresentation.backgroundStart,
            backgroundMid = nowPlayingPresentation.backgroundMid,
            backgroundEnd = nowPlayingPresentation.backgroundEnd,
            targetBackgroundColors = nowPlayingPresentation.targetBackgroundColors,
            coverArtUrl = nowPlayingPresentation.effectiveCoverArtUrl,
            interfaceSettings = interfaceSettings,
            onCloseStatsForNerds = { showStatsForNerds = false },
    ) {
            Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    val playerTrack = nowPlayingTrack
                    if (appRoute == DesktopAppRoute.Player && playerTrack != null) {
                        DesktopPlayerRouteContent(
                            appColors = appColors,
                            connectedProvider = connectedProvider,
                            nowPlayingTrack = playerTrack,
                            nowPlayingController = nowPlayingController,
                            nowPlayingPresentation = nowPlayingPresentation,
                            nowPlayingStreamMetadata = nowPlayingStreamMetadata,
                            nowPlayingLyricsVisible = nowPlayingLyricsVisible,
                            nowPlayingVisualizerVisible = nowPlayingVisualizerVisible,
                            playbackQueue = playbackQueue,
                            internetRadioController = internetRadioController,
                            nowPlayingInternetRadioStationId = nowPlayingInternetRadioStation?.id,
                            playbackController = playbackController,
                            shuffledUpNextSnapshot = shuffledUpNextSnapshot,
                            repeatMode = repeatMode,
                            playbackState = playbackState,
                            playbackProgress = playbackProgress,
                            playbackSettings = playbackSettings,
                            interfaceSettings = interfaceSettings,
                            cacheSettings = cacheSettings,
                            sleepTimer = sleepTimer,
                            sleepTimerNowEpochMillis = sleepTimerNowEpochMillis,
                            onPlaybackAction = handleNowPlayingPlaybackAction,
                            onDisplayAction = handleNowPlayingDisplayAction,
                            onQueueAction = handleNowPlayingQueueAction,
                            onSleepTimerAction = handleNowPlayingSleepTimerAction,
                            onSelectionAction = handleNowPlayingSelectionAction,
                            appActions = appActions,
                            playbackEngine = playbackEngine,
                            playlistsController = playlistsController,
                        )
                    } else {
                        val desktopDownloadedTracks = remember(
                            connectedSourceId,
                            downloadsController.refreshToken,
                            cacheStats.downloadCount,
                        ) {
                            connectedSourceId?.let(storage::downloadedTracks).orEmpty()
                        }
                        val desktopDownloadItems = desktopDownloadedTracks.map { download ->
                            download.track.toDownloadedTrackUi(
                                id = download.path.toString(),
                                sizeBytes = download.sizeBytes,
                                qualityLabel = downloadedAudioQualityLabel(
                                    download.qualityKey,
                                    download.track.audioInfo,
                                    download.contentType,
                                ),
                                coverArtUrl = { coverArtId ->
                                    coverArtId?.let { connectedProvider?.coverArtUrl(it) }
                                },
                            )
                        }
                        val desktopShellState = NaviampAppShellUiState(
                            connectionSettings = shellConnection.toConnectionSettingsUi(
                                capabilities = shellCapabilities,
                                currentSourceId = connectedSourceId,
                            ),
                            general = interfaceSettings.toGeneralSettingsUi(about),
                            playback = playbackSettings.toPlaybackSettingsUi(
                                capabilities = shellCapabilities,
                                audioOutputDeviceSelectionAvailable =
                                    (playbackEngine as? AudioOutputDevicePlaybackEngine)
                                        ?.supportsAudioOutputDeviceSelection == true,
                                audioOutputDevices =
                                    (playbackEngine as? AudioOutputDevicePlaybackEngine)?.outputDevices().orEmpty(),
                                downloadBytes = cacheStats.downloadBytes,
                            ),
                            cache = cacheSettings.toCacheSettingsUi(cacheStats, shellCapabilities),
                            shellChrome = NaviampShellChromeUi(
                                selectedRoute = appRoute.toSharedRoute(),
                                supportsDownloads = shellCapabilities.downloads,
                                supportsApplicationUpdates = shellCapabilities.applicationUpdates,
                            ),
                            home = NaviampHomeScreenUi(
                                content = homeContent.toSharedHomeUi(
                                    coverArtUrl = { coverArtId ->
                                        coverArtId?.let { connectedProvider?.coverArtUrl(it) }
                                    },
                                    playlistTracksById = playlistsController.playlistTracksById,
                                    sonicDiscoveryRows = sonicHomeDiscoveryController.rows,
                                    canFavoriteAlbums = true,
                                    showSonicPathBuilder =
                                        playbackSettings.sonicSimilarityEnabled && shellCapabilities.sonicSimilarity,
                                    showSonicMixBuilder =
                                        playbackSettings.sonicSimilarityEnabled && shellCapabilities.sonicSimilarity,
                                ),
                                refreshing = homeController.refreshing,
                            ),
                            downloads = NaviampDownloadsScreenUi(
                                downloads = desktopDownloadItems,
                                status = downloadsController.status,
                                jobs = downloadsController.downloadJobs.map { it.toDownloadJobUi() },
                                downloadBytes = desktopDownloadItems.totalDownloadBytes(),
                                maxDownloadBytes = cacheSettings.maxDownloadBytes,
                                offlineDashboard = NaviampOfflineDashboardUi(
                                    audioCacheCount = cacheStats.audioCount,
                                    audioCacheBytes = cacheStats.audioBytes,
                                    maxAudioCacheBytes = cacheSettings.maxAudioCacheBytes,
                                ),
                                keepFavoritesDownloaded = downloadsController.keepDownloadedPolicies.any {
                                    it.kind == app.naviamp.domain.cache.KeepDownloadedCollectionKind.Favorites
                                },
                            ),
                            artistMixBuilder = mixBuilderController.artistUi(
                                coverArtUrl = { coverArtId -> coverArtId?.let { connectedProvider?.coverArtUrl(it) } },
                            ),
                            albumMixBuilder = mixBuilderController.albumUi(
                                coverArtUrl = { coverArtId -> coverArtId?.let { connectedProvider?.coverArtUrl(it) } },
                            ),
                            genreMixBuilder = mixBuilderController.genreUi(),
                            sonicPathBuilder = sonicPathController.ui(
                                coverArtUrl = { coverArtId -> coverArtId?.let { connectedProvider?.coverArtUrl(it) } },
                            ),
                            sonicMixBuilder = sonicMixController.ui(
                                coverArtUrl = { coverArtId -> coverArtId?.let { connectedProvider?.coverArtUrl(it) } },
                            ),
                            albumDetail = NaviampAlbumDetailScreenUi(
                                selectedAlbum = albumController.selectedAlbum?.toSharedMediaItemUi(
                                    coverArtUrl = { coverArtId -> coverArtId?.let { connectedProvider?.coverArtUrl(it) } },
                                    canFavorite = true,
                                ),
                                detail = albumController.selectedAlbumDetails?.toSharedAlbumDetailUi(
                                    coverArtUrl = { coverArtId -> coverArtId?.let { connectedProvider?.coverArtUrl(it) } },
                                    popularTrackIds = artistController.selectedArtistPopularTracks.mapTo(mutableSetOf()) { it.id.value },
                                    canFavoriteAlbum = true,
                                ),
                                status = albumController.selectedAlbumStatus,
                            ),
                            artistDetail = NaviampArtistDetailScreenUi(
                                selectedArtist = artistController.selectedArtist?.toSharedMediaItemUi(
                                    coverArtUrl = { coverArtId -> coverArtId?.let { connectedProvider?.coverArtUrl(it) } },
                                    canFavorite = true,
                                ),
                                detail = artistController.selectedArtistDetails?.toSharedArtistDetailUi(
                                    coverArtUrl = { coverArtId -> coverArtId?.let { connectedProvider?.coverArtUrl(it) } },
                                    popularTracks = artistController.selectedArtistPopularTracks,
                                    popularTracksStatus = artistController.selectedArtistPopularTracksStatus,
                                    similarArtists = artistController.selectedArtistSimilarArtists,
                                    similarArtistsStatus = artistController.selectedArtistSimilarArtistsStatus,
                                    canFavoriteArtist = true,
                                    canFavoriteAlbums = true,
                                ),
                                status = artistController.selectedArtistStatus,
                            ),
                            playlists = NaviampPlaylistsScreenUi(
                                playlists = playlistsController.playlists.map { playlist ->
                                    playlist.toSharedMediaItemUi(
                                        coverArtUrl = { coverArtId -> coverArtId?.let { connectedProvider?.coverArtUrl(it) } },
                                        tracks = playlistsController.playlistTracksById[playlist.id].orEmpty(),
                                        keepDownloadedActive = downloadsController.keepDownloadedPolicies.any { it.collectionId == playlist.id },
                                    )
                                },
                                recentPlaylistIds = playlistsController.recentPlaylistIds,
                                sortMode = playlistsController.sortMode,
                                status = playlistsController.status,
                            ),
                            playlistDetail = NaviampPlaylistDetailScreenUi(
                                selectedPlaylist = playlistsController.selectedPlaylist?.toSharedMediaItemUi(
                                    coverArtUrl = { coverArtId -> coverArtId?.let { connectedProvider?.coverArtUrl(it) } },
                                    tracks = playlistsController.selectedPlaylistTracks,
                                    keepDownloadedActive = playlistsController.selectedPlaylist?.id
                                        ?.let { id -> downloadsController.keepDownloadedPolicies.any { it.collectionId == id } } == true,
                                ),
                                detail = playlistsController.selectedPlaylist?.toSharedPlaylistDetailUi(
                                    tracks = playlistsController.selectedPlaylistTracks,
                                    coverArtUrl = { coverArtId -> coverArtId?.let { connectedProvider?.coverArtUrl(it) } },
                                    keepDownloadedActive = playlistsController.selectedPlaylist?.id
                                        ?.let { id -> downloadsController.keepDownloadedPolicies.any { it.collectionId == id } } == true,
                                ),
                                status = playlistsController.selectedPlaylistStatus,
                            ),
                            library = NaviampLibraryScreenUi(
                                artists = libraryController.snapshot.artists.map { artist ->
                                    artist.toSharedMediaItemUi(
                                        coverArtUrl = { coverArtId ->
                                            coverArtId?.let { connectedProvider?.coverArtUrl(it) }
                                        },
                                        canFavorite = true,
                                    )
                                },
                                query = libraryController.query,
                                syncStatus = NaviampLibrarySyncStatusUi(
                                    message = libraryController.status,
                                    isSyncing = libraryController.syncing,
                                ),
                            ),
                            search = NaviampSearchScreenUi(
                                query = searchController.query,
                                results = searchController.results.toSharedSearchResultsUi(
                                    coverArtUrl = { coverArtId ->
                                        coverArtId?.let { connectedProvider?.coverArtUrl(it) }
                                    },
                                    canFavoriteArtists = true,
                                    canFavoriteAlbums = true,
                                ),
                                status = searchController.status,
                                searching = searchController.searching,
                            ),
                            radio = app.naviamp.ui.NaviampInternetRadioScreenUi(
                                stations = internetRadioController.stations.map { it.toInternetRadioStationUi() },
                                status = internetRadioController.status,
                            ),
                        )
                        val playlistActionSources = DesktopPlaylistActionSources(
                            playlists = playlistsController.playlists,
                            playlistTracksById = playlistsController.playlistTracksById,
                            selectedPlaylist = playlistsController.selectedPlaylist,
                            selectedPlaylistTracks = playlistsController.selectedPlaylistTracks,
                        )
                        val internetRadioActionSources = DesktopInternetRadioActionSources(
                            stations = internetRadioController.stations,
                        )
                        val detailActionSources = DesktopDetailActionSources(
                            selectedAlbum = albumController.selectedAlbum,
                            albumDetail = albumController.selectedAlbumDetails,
                            selectedArtist = artistController.selectedArtist,
                            artistDetail = artistController.selectedArtistDetails,
                            artistPopularTracks = artistController.selectedArtistPopularTracks,
                            artistSimilarArtists = artistController.selectedArtistSimilarArtists,
                        )
                        val desktopShellActions = NaviampAppShellActions(
                            navigationActions = NaviampShellNavigationActions(
                                onRouteSelected = { route -> appRoute = route.toAppRoute() },
                            ),
                            homeActions = NaviampHomeActions(
                                onRefresh = { connectedProvider?.let(homeController::loadHomeContent) },
                                onRecentRadioSelected = { item -> appActions.playHomeRecentRadio(item.id) },
                                onMixBuilderSelected = { builder ->
                                    appRoute = when (builder.id) {
                                        "artist" -> DesktopAppRoute.ArtistMix
                                        "album" -> DesktopAppRoute.AlbumMix
                                        "genre" -> DesktopAppRoute.GenreMix
                                        "sonic-path" -> DesktopAppRoute.SonicPath
                                        "sonic-mix" -> DesktopAppRoute.SonicMix
                                        else -> appRoute
                                    }
                                },
                                onStationSelected = { station -> appActions.playHomeStation(station.id) },
                                onSonicDiscoveryTrackAction = { request ->
                                    val track = sonicHomeDiscoveryController.trackFor(request)
                                    when (request.action) {
                                        app.naviamp.ui.SharedTrackRowAction.ToggleFavorite ->
                                            track?.let(appActions::toggleTrackFavorite)
                                        app.naviamp.ui.SharedTrackRowAction.GoToAlbum ->
                                            track?.let(appActions::openTrackAlbumDetails)
                                        app.naviamp.ui.SharedTrackRowAction.GoToArtist ->
                                            track?.let(appActions::openTrackArtistDetails)
                                        else -> sonicHomeDiscoveryController.handleAction(request)
                                    }
                                },
                                onRecentlyPlayedTrackAction = { request ->
                                    val tracks = homeContent.recentlyPlayedTracks
                                    val index = tracks.indexOfFirst { track -> track.id.value == request.track.id }
                                    tracks.getOrNull(index)?.let { track ->
                                        when (request.action) {
                                            app.naviamp.ui.SharedTrackRowAction.Select ->
                                                appActions.playPopularTracks(tracks, index)
                                            app.naviamp.ui.SharedTrackRowAction.PlayNext ->
                                                playlistsController.playNext(track)
                                            app.naviamp.ui.SharedTrackRowAction.StartRadio ->
                                                appActions.playTrackRadio(track)
                                            app.naviamp.ui.SharedTrackRowAction.PlayTrackRadioNext ->
                                                appActions.playTrackRadioNext(track)
                                            app.naviamp.ui.SharedTrackRowAction.AddTrackRadioToQueue ->
                                                appActions.addTrackRadioToQueue(track)
                                            app.naviamp.ui.SharedTrackRowAction.Download ->
                                                appActions.downloadTrack(track)
                                            app.naviamp.ui.SharedTrackRowAction.AddToQueue ->
                                                playlistsController.addTrackToQueue(track)
                                            app.naviamp.ui.SharedTrackRowAction.AddToPlaylist ->
                                                playlistsController.openTrackAddToPlaylist(track)
                                            app.naviamp.ui.SharedTrackRowAction.CreatePlaylistAndAdd -> Unit
                                            app.naviamp.ui.SharedTrackRowAction.ToggleFavorite ->
                                                appActions.toggleTrackFavorite(track)
                                            app.naviamp.ui.SharedTrackRowAction.GoToAlbum ->
                                                appActions.openTrackAlbumDetails(track)
                                            app.naviamp.ui.SharedTrackRowAction.GoToArtist ->
                                                appActions.openTrackArtistDetails(
                                                    track,
                                                    artistId = request.artistId,
                                                    artistName = request.artistName,
                                                )
                                        }
                                    }
                                },
                            ),
                            searchActions = NaviampSearchActions(
                                onQueryChanged = searchController::updateQuery,
                                onClear = searchController::clearSearch,
                            ),
                            libraryActions = NaviampLibraryActions(
                                onQueryChanged = libraryController::updateQuery,
                                onRefresh = libraryController::refreshArtistIndex,
                            ),
                            playlistsActions = NaviampPlaylistsActions(
                                onRefresh = { playlistsController.refreshPlaylists(useCache = false) },
                                onSortModeChanged = playlistsController::updateSortMode,
                                onSmartPlaylistSave = smartPlaylistsController::saveSmartPlaylist,
                                onSmartPlaylistUpdate = { item, definition ->
                                    playlistActionSources.playlist(item.id)?.let { playlist ->
                                        smartPlaylistsController.updateSmartPlaylist(playlist, definition)
                                    }
                                },
                                onSmartPlaylistSaveWithPassword =
                                    smartPlaylistsController::saveSmartPlaylistWithPassword,
                                onSmartPlaylistUpdateWithPassword = { item, definition, password ->
                                    playlistActionSources.playlist(item.id)?.let { playlist ->
                                        smartPlaylistsController.updateSmartPlaylistWithPassword(
                                            playlist,
                                            definition,
                                            password,
                                        )
                                    }
                                },
                                onSmartPlaylistLoad = { item ->
                                    playlistActionSources.playlist(item.id)
                                        ?.let { smartPlaylistsController.loadSmartPlaylistDefinition(it) }
                                        ?: error("Playlist ${item.title} is no longer available.")
                                },
                            ),
                            radioActions = desktopInternetRadioActions(
                                actionSources = internetRadioActionSources,
                                onRefresh = internetRadioController::refreshStations,
                                onPlayStation = internetRadioController::playStation,
                                onSaveStation = internetRadioController::saveStation,
                                onDeleteStation = internetRadioController::deleteStation,
                            ),
                            albumDetailActions = desktopAlbumDetailActions(
                                actionSources = detailActionSources,
                                appActions = appActions,
                                playlistsController = playlistsController,
                                onBack = { appRoute = albumController.albumDetailBackRoute },
                            ),
                            artistDetailActions = desktopArtistDetailActions(
                                actionSources = detailActionSources,
                                appActions = appActions,
                                playlistsController = playlistsController,
                            ),
                            playlistDetailActions = desktopPlaylistDetailActions(
                                actionSources = playlistActionSources,
                                appActions = appActions,
                                playlistsController = playlistsController,
                                onBack = { appRoute = DesktopAppRoute.Playlists },
                            ),
                            mediaActions = desktopMediaActions(
                                playlistActionSources = playlistActionSources,
                                artists = if (appRoute == DesktopAppRoute.Search) {
                                    searchController.results.artists
                                } else {
                                    libraryController.snapshot.artists
                                },
                                albums = searchController.results.albums,
                                tracks = searchController.results.tracks,
                                appActions = appActions,
                                playlistsController = playlistsController,
                            ),
                            connectionActions = NaviampConnectionSettingsActions(
                                onFormChanged = { form ->
                                    connectionForm.connectionName = form.displayName
                                    connectionForm.updateServerUrl(form.serverUrl)
                                    connectionForm.updateUsername(form.username)
                                    connectionForm.password = form.password
                                    connectionForm.insecureSkipTlsVerification = form.skipTlsVerification
                                    connectionForm.customCertificatePath = form.customCertificatePath
                                    connectionForm.clientCertificateKeyStorePath = form.clientCertificatePath
                                    connectionForm.clientCertificateKeyStorePassword = form.clientCertificatePassword
                                    connectionForm.secondaryUrls = form.secondaryUrls
                                    connectionForm.customHeaders = form.customHeaders
                                    connectionForm.selectedMusicFolderIds = form.selectedMusicFolderIds
                                },
                                onConnect = { appActions.connectToServer() },
                                onNewConnection = connectionLifecycleController::openNewConnectionForm,
                                onEditConnection = { item ->
                                    savedMediaSources.firstOrNull { it.id == item.id }
                                        ?.let(connectionLifecycleController::openSavedConnectionForm)
                                },
                                onConnectSavedConnection = { item ->
                                    savedMediaSources.firstOrNull { it.id == item.id }
                                        ?.let(connectionLifecycleController::connectSavedConnection)
                                },
                                onDeleteConnection = { item ->
                                    savedMediaSources.firstOrNull { it.id == item.id }
                                        ?.let(appActions::deleteConnection)
                                },
                                onCancelConnectionForm = connectionLifecycleController::closeConnectionForm,
                            ),
                            valueActions = NaviampSettingsValueActions(
                                onInterfaceSettingsChanged = { settings: InterfaceSettings ->
                                    interfaceSettings = settings.normalized()
                                    settingsStore.saveInterfaceSettings(interfaceSettings)
                                    markAndAutoExportSettingsSync()
                                },
                                onPlaybackSettingsChanged = settingsMaintenanceController::applyPlaybackSettings,
                                onPlaybackSettingsChangedAndRedownload =
                                    settingsMaintenanceController::applyPlaybackSettingsAndRedownload,
                                onCacheSettingsChanged = cacheSettingsController::apply,
                            ),
                            maintenanceActions = NaviampSettingsMaintenanceActions(
                                onOpenStatsForNerds = { showStatsForNerds = true },
                                onClearCache = { appActions.clearCacheData() },
                                onClearLibrary = { appActions.clearLibraryData() },
                                onRefreshLibrary = libraryController::refreshLibrarySnapshot,
                                onResetDatabase = { appActions.resetDatabase() },
                            ),
                            artistMixActions = SharedArtistMixBuilderActions(
                                onQueryChanged = mixBuilderController::setArtistQuery,
                                onSearch = mixBuilderController::searchArtistSuggestions,
                                onArtistSelected = { item -> mixBuilderController.selectArtistByItemId(item.id) },
                                onArtistRemoved = { item -> mixBuilderController.removeArtistByItemId(item.id) },
                                onReset = mixBuilderController::resetArtistBuilder,
                                onPlay = { mixBuilderController.playArtistMix(radioController) },
                            ),
                            albumMixActions = SharedAlbumMixBuilderActions(
                                onQueryChanged = mixBuilderController::setAlbumQuery,
                                onSearch = mixBuilderController::searchAlbumSuggestions,
                                onAlbumSelected = { item -> mixBuilderController.selectAlbumByItemId(item.id) },
                                onAlbumRemoved = { item -> mixBuilderController.removeAlbumByItemId(item.id) },
                                onReset = mixBuilderController::resetAlbumBuilder,
                                onPlay = { mixBuilderController.playAlbumMix(radioController) },
                            ),
                            genreMixActions = SharedGenreMixBuilderActions(
                                onQueryChanged = mixBuilderController::setGenreQuery,
                                onSearch = mixBuilderController::refreshGenreSuggestions,
                                onGenreSelected = { item -> mixBuilderController.selectGenreByItemId(item.id) },
                                onGenreRemoved = { item -> mixBuilderController.removeGenreByItemId(item.id) },
                                onReset = mixBuilderController::resetGenreBuilder,
                                onPlay = { mixBuilderController.playGenreMix(radioController) },
                            ),
                            sonicPathActions = SharedSonicPathBuilderActions(
                                onStartQueryChanged = sonicPathController::updateStartQuery,
                                onEndQueryChanged = sonicPathController::updateEndQuery,
                                onStartSearch = sonicPathController::searchStartTracks,
                                onEndSearch = sonicPathController::searchEndTracks,
                                onStartTrackSelected = sonicPathController::selectStartTrack,
                                onEndTrackSelected = sonicPathController::selectEndTrack,
                                onStartTrackCleared = sonicPathController::clearStartTrack,
                                onEndTrackCleared = sonicPathController::clearEndTrack,
                                onCountChanged = sonicPathController::updateCount,
                                onBuild = sonicPathController::buildPath,
                                onReset = sonicPathController::reset,
                                onPlay = sonicPathController::playPath,
                                onAddToQueue = sonicPathController::addPathToQueue,
                                onSaveAsPlaylist = { name ->
                                    playlistsController.saveTracksAsPlaylist(
                                        name = name,
                                        tracks = sonicPathController.playlistTracks(),
                                        label = "sonic path",
                                    )
                                },
                            ),
                            sonicMixActions = SharedSonicMixBuilderActions(
                                onQueryChanged = sonicMixController::updateQuery,
                                onSearch = sonicMixController::searchTracks,
                                onTrackSelected = sonicMixController::selectTrack,
                                onTrackRemoved = sonicMixController::removeTrack,
                                onTargetLengthChanged = sonicMixController::updateTargetLength,
                                onBiasChanged = sonicMixController::updateBias,
                                onBuild = sonicMixController::buildMix,
                                onReset = sonicMixController::reset,
                                onPlay = sonicMixController::playMix,
                                onAddToQueue = sonicMixController::addMixToQueue,
                                onSaveAsPlaylist = { name ->
                                    playlistsController.saveTracksAsPlaylist(
                                        name = name,
                                        tracks = sonicMixController.playlistTracks(),
                                        label = "sonic mix",
                                    )
                                },
                            ),
                        )
                        DesktopAppRouteContent(
                            shellState = desktopShellState,
                            shellActions = desktopShellActions,
                            appColors = appColors,
                            appRoute = appRoute,
                            connection = shellConnection,
                            capabilities = shellCapabilities,
                            appActions = appActions,
                            playlistsController = playlistsController,
                            onLibraryJumpToLetter = libraryController::jumpLibraryToLetter,
                            libraryListState = libraryListState,
                            downloadedTracks = desktopDownloadedTracks,
                            interfaceSettings = interfaceSettings,
                            playbackSettings = playbackSettings,
                            settingsSyncDirectoryPath = settingsSyncSettings.directoryPath,
                            settingsSyncAutoExportEnabled = settingsSyncSettings.autoExportEnabled,
                            settingsSyncStatus = settingsSyncStatus,
                            onSettingsSyncDirectoryChanged = ::updateSettingsSyncDirectory,
                            onSettingsSyncDirectorySelectedForImport = ::selectSettingsSyncDirectoryAndImport,
                            onSettingsSyncAutoExportChanged = ::updateSettingsSyncAutoExport,
                            onSettingsSyncExport = ::exportSettingsSync,
                            onSettingsSyncImport = ::importSettingsSync,
                        )
                        DesktopAppDialogs(
                            appColors = appColors,
                            addToPlaylistTarget = playlistsController.addToPlaylistTarget,
                            playlists = playlistsController.playlists,
                            addToPlaylistStatus = playlistsController.addToPlaylistStatus,
                            playlistPendingRename = playlistsController.pendingRename,
                            playlistPendingDelete = playlistsController.pendingDelete,
                            onDismissAddToPlaylist = playlistsController::dismissAddToPlaylist,
                            onAddToExistingPlaylist = { target, playlist ->
                                playlistsController.addTargetToPlaylist(target, playlist = playlist)
                            },
                            onCreateAndAddToPlaylist = { target, name ->
                                playlistsController.addTargetToPlaylist(target, playlist = null, newPlaylistName = name)
                            },
                            onDismissRenamePlaylist = playlistsController::dismissRename,
                            onRenamePlaylist = appActions::renamePlaylist,
                            onDismissDeletePlaylist = playlistsController::dismissDelete,
                            onDeletePlaylist = appActions::deletePlaylist,
                        )
                        if (nowPlayingTrack != null && !connectionForm.isOpen) {
                            DesktopMiniPlayerPanel(
                                appColors = appColors,
                                nowPlayingTrack = nowPlayingTrack,
                                coverArtUrl = nowPlayingPresentation.effectiveCoverArtUrl,
                                hasPrevious = playbackController.canUsePreviousButton(),
                                hasNext = playbackQueue.hasNext(),
                                playbackState = playbackState,
                                onPlaybackAction = handleNowPlayingPlaybackAction,
                                onOpenPlayer = {
                                    appRoute = DesktopAppRoute.Player
                                },
                            )
                        }
                        DesktopBottomNavigationBar(
                            appColors = appColors,
                            supportsDownloads = DesktopCapabilityPresentation.downloads.visible,
                            selectedRoute = when (appRoute) {
                                DesktopAppRoute.AlbumDetail -> if (albumController.albumDetailBackRoute == DesktopAppRoute.ArtistDetail) {
                                    artistController.artistDetailBackRoute
                                } else {
                                    albumController.albumDetailBackRoute
                                }
                                DesktopAppRoute.ArtistDetail -> artistController.artistDetailBackRoute
                                DesktopAppRoute.PlaylistDetail -> DesktopAppRoute.Playlists
                                else -> appRoute
                            },
                            onRouteSelected = { route ->
                                appRoute = route
                            },
                        )
    }
}
}
}
}

private fun NavidromeConnection.withNativeTokenFrom(fallback: NavidromeConnection?): NavidromeConnection {
    if (nativeToken?.isNotBlank() == true) return this
    val fallbackToken = fallback?.nativeToken?.takeIf { it.isNotBlank() } ?: return this
    val matchesSavedConnection = fallback.baseUrl == baseUrl && fallback.username == username
    return if (matchesSavedConnection) copy(nativeToken = fallbackToken) else this
}

private const val DesktopSettingsSyncDeviceId = "desktop"
