package app.naviamp.desktop

import app.naviamp.domain.app.NaviampRoute

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
import app.naviamp.app.NaviampApplicationControllers
import app.naviamp.app.NaviampApplicationStatusArea
import app.naviamp.app.NaviampApplicationStatusLevel
import app.naviamp.app.NaviampLivePlaybackState
import app.naviamp.app.NaviampPlaybackSessionController
import app.naviamp.app.NaviampRecentRadioStreamController
import app.naviamp.app.NaviampRadioContinuationController
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
import app.naviamp.domain.home.HomeContent
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.radio.InternetRadioStationManager
import app.naviamp.domain.radio.RadioTuningSettings
import app.naviamp.desktop.settings.PlaybackSettings
import app.naviamp.desktop.settings.PlaybackSessionSettings
import app.naviamp.desktop.settings.RecentRadioStream
import app.naviamp.desktop.settings.DesktopSettingsSyncSettings
import app.naviamp.desktop.settings.VisualizerSettings
import app.naviamp.domain.settings.SettingsSyncLocalSnapshot
import app.naviamp.domain.settings.SettingsSyncRuntimeState
import app.naviamp.domain.settings.effectiveForEngine
import app.naviamp.domain.settings.PlaybackSettingsMaintenanceController
import app.naviamp.domain.settings.SavedInternetRadioStation
import app.naviamp.domain.settings.ConnectionFormMusicFolder
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.playbackSettingsChange
import app.naviamp.domain.settings.restoredPlaybackQueue
import app.naviamp.domain.settings.restoredTrackSession
import app.naviamp.domain.settings.toConnectionHeaderDefinitions
import app.naviamp.domain.settings.toConnectionSecondaryUrls
import app.naviamp.domain.source.visibleServerConnections
import app.naviamp.domain.sonicautoplay.SonicAutoplayService
import app.naviamp.provider.navidrome.NavidromeConnection
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.provider.navidrome.withNativeTokenFromPassword
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
import app.naviamp.ui.NaviampSettingsSyncActions
import app.naviamp.ui.NaviampArtistDetailScreenUi
import app.naviamp.ui.NaviampPlaylistDetailScreenUi
import app.naviamp.ui.NaviampPlaylistsActions
import app.naviamp.ui.NaviampPlaylistsScreenUi
import app.naviamp.ui.SharedAlbumMixBuilderActions
import app.naviamp.ui.SharedArtistMixBuilderActions
import app.naviamp.ui.SharedGenreMixBuilderActions
import app.naviamp.ui.SharedSonicMixBuilderActions
import app.naviamp.ui.SharedSonicPathBuilderActions
import app.naviamp.ui.SharedDetailActionSources
import app.naviamp.ui.SharedInternetRadioActionSources
import app.naviamp.ui.SharedPlaylistActionSources
import app.naviamp.ui.ResolvedTrackRowActionHandlers
import app.naviamp.ui.handleResolvedTrackRowAction
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
import app.naviamp.ui.settingsSyncUi
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
    val restoredAppState = remember(storage, settingsStore, playbackSessions) {
        loadDesktopRestoredAppState(storage, settingsStore, playbackSessions)
    }
    val savedMediaSource = restoredAppState.mediaSource
    val savedConnection = restoredAppState.connection
    val savedPlaybackSession = restoredAppState.playbackSession
    var cacheStats by remember { mutableStateOf(StorageCacheStats()) }
    var downloadJobs by remember { mutableStateOf<List<DownloadJob>>(emptyList()) }
    var connectedSourceId by remember { mutableStateOf(savedMediaSource?.id) }
    var cacheSettings by remember {
        mutableStateOf(settingsStore.loadCacheSettings().normalized())
    }
    var interfaceSettings by remember {
        mutableStateOf(settingsStore.loadInterfaceSettings().normalized())
    }
    var playbackSettings by remember {
        mutableStateOf(restoredAppState.playbackSettings.effectiveForEngine(playbackEngine))
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
    var settingsSyncSettings by remember { mutableStateOf(restoredAppState.settingsSync.normalized()) }
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
    var recentRadioStreams by remember { mutableStateOf(restoredAppState.recentRadioStreams) }
    val applicationControllers = remember {
        NaviampApplicationControllers(
            initialNavigationState = NaviampNavigationState(
                route = restoredRoute(
                    savedRouteName = restoredAppState.navigation.route,
                    hasConnection = savedConnection != null,
                    hasRestoredTrack = restoredTrack != null,
                ),
                lastContentRoute = restoredLastContentRoute(restoredAppState.navigation.lastContentRoute),
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
    var sleepTimerNowEpochMillis by remember { mutableLongStateOf(DesktopSystemClock.nowEpochMillis()) }
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
    val shuffledUpNextSnapshotProperty = remember {
        desktopShuffledUpNextSnapshotProperty(livePlaybackController)
    }
    var shuffledUpNextSnapshot by shuffledUpNextSnapshotProperty
    val repeatModeProperty = remember { desktopRepeatModeProperty(livePlaybackController) }
    var repeatMode by repeatModeProperty
    val radioContinuation = remember { NaviampRadioContinuationController() }
    var restoredPlaybackPositionSeconds by remember {
        mutableStateOf(savedPlaybackSession?.positionSeconds?.takeIf { it > 0.0 })
    }
    var lastPlaybackProgressUiUpdateMillis by remember { mutableLongStateOf(0L) }
    var playReportSessionId by remember { mutableStateOf(0) }
    val playbackGraph = rememberDesktopPlaybackControllerGraph(
        dependencies = dependencies,
        scope = coroutineScope,
        playbackSessions = playbackSessions,
        applicationControllers = applicationControllers,
        livePlayback = livePlaybackController,
        queueCoordinator = queueCoordinator,
        playlistEngine = playlistEngine,
        bindings = DesktopPlaybackGraphBindings(
            provider = { connectedProvider },
            sourceId = { connectedSourceId },
            playbackSettings = { playbackSettings },
            cacheSettings = { cacheSettings },
            route = { appRoute },
            lyricsVisible = { nowPlayingLyricsVisible },
            playbackQueue = { playbackQueue },
            playbackProgress = { playbackProgress },
            setPlaybackProgress = { progress -> playbackProgress = progress },
            playbackState = { playbackState },
            nowPlayingTrack = { nowPlayingTrack },
            nowPlayingCoverArtUrl = { nowPlayingCoverArtUrl },
            playReportSessionId = { playReportSessionId },
            setRepeatMode = { mode -> repeatMode = mode },
            setOpenPlayerOnTrackStart = { shouldOpen -> openPlayerOnTrackStart = shouldOpen },
            setSleepTimer = { timer -> sleepTimer = timer },
            setSleepTimerNowEpochMillis = { millis -> sleepTimerNowEpochMillis = millis },
            setStatus = { status -> connectionStatus = status },
        ),
        snapshot = DesktopNowPlayingSnapshot(
            initialVisualizerSettings = restoredAppState.visualizer,
            appColors = appColors,
            interfaceSettings = interfaceSettings,
            currentCoverArtUrl = nowPlayingCoverArtUrl,
            track = nowPlayingTrack,
            station = nowPlayingInternetRadioStation,
            streamMetadata = nowPlayingStreamMetadata,
            provider = connectedProvider,
            playbackState = playbackState,
            sleepTimer = sleepTimer,
        ),
    )
    val nowPlayingPresentation = playbackGraph.presentation
    val nowPlayingVisualizerVisible = playbackGraph.visualizerVisible
    val playbackController = playbackGraph.playback
    val sleepTimerController = playbackGraph.sleepTimer
    val nowPlayingController = playbackGraph.nowPlaying

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

    val settingsSyncDocumentApplicator = remember(storage, settingsStore, playbackEngine, connectionForm) {
        DesktopSettingsSyncDocumentApplicator(
            settingsStore = settingsStore,
            storage = storage,
            playbackEngine = playbackEngine,
            setInterfaceSettings = { settings -> interfaceSettings = settings },
            setPlaybackSettings = { settings -> playbackSettings = settings },
            selectVisualizer = nowPlayingPresentation::selectVisualizer,
            setRecentRadioStreams = { streams -> recentRadioStreams = streams },
            connectionForm = connectionForm,
            onServerProfilesImported = { mediaSourcesRevision++ },
            setRoute = { route -> appRoute = route },
        )
    }

    val applicationServices = remember(storage, settingsStore) {
        desktopApplicationServices(
            storage = storage,
            downloadJobs = { downloadJobs },
            setDownloadJobs = { jobs -> downloadJobs = jobs },
            settingsSyncState = ::settingsSyncRuntimeState,
            saveSettingsSyncState = ::saveSettingsSyncRuntimeState,
            settingsSyncSnapshot = {
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
            applySettingsSyncDocument = settingsSyncDocumentApplicator::apply,
            setCacheSettings = { settings -> cacheSettings = settings },
            saveCacheSettings = settingsStore::saveCacheSettings,
            publishCacheStatus = { status, level ->
                applicationControllers.status.publish(
                    area = NaviampApplicationStatusArea.CacheMaintenance,
                    level = level,
                    message = status,
                )
            },
        )
    }
    val settingsSyncController = applicationServices.settingsSync
    val cacheSettingsController = applicationServices.cacheSettings

    val settingsSyncHost = remember(settingsSyncController, applicationControllers) {
        DesktopSettingsSyncHost(
            settingsStore = settingsStore,
            controller = settingsSyncController,
            settings = { settingsSyncSettings },
            setSettings = { settings -> settingsSyncSettings = settings },
            setStatus = { status ->
                settingsSyncStatus = status
                applicationControllers.status.publish(
                    area = NaviampApplicationStatusArea.SettingsSync,
                    level = NaviampApplicationStatusLevel.Information,
                    message = status,
                )
            },
            publishError = { status ->
                settingsSyncStatus = status
                applicationControllers.status.publish(
                    area = NaviampApplicationStatusArea.SettingsSync,
                    level = NaviampApplicationStatusLevel.Error,
                    message = status,
                )
            },
        )
    }

    val recentRadioStreamController = remember(settingsStore, settingsSyncHost) {
        NaviampRecentRadioStreamController(
            load = settingsStore::loadRecentRadioStreams,
            save = settingsSyncHost::saveRecentRadioStreams,
        )
    }

    fun rememberRadioStream(stream: RecentRadioStream) {
        val updatedStreams = recentRadioStreamController.remember(stream)
        recentRadioStreams = updatedStreams
        homeContent = homeContent.copy(recentRadioStreams = updatedStreams)
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
        continuation = radioContinuation,
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
        initialRecentStations = restoredAppState.recentInternetRadioStations.map { it.toStation() },
        saveRecentInternetRadioStations = settingsSyncHost::saveRecentInternetRadioStations,
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
        onSyncedSettingsChanged = settingsSyncHost::markChangedAndAutoExport,
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
        livePlayback = applicationControllers.playback,
        savePlaybackSession = playbackController::savePlaybackSession,
        playbackProgress = { playbackProgress },
        setPlaybackProgress = { progress -> playbackProgress = progress },
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
        initialQuery = restoredAppState.search.query,
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
        savePlaybackSettings = settingsSyncHost::savePlaybackSettings,
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
        initialRecentPlaylistIds = restoredAppState.recentPlaylistIds,
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

    DesktopHostEffects(
        applicationStatusSequence = applicationStatus?.sequence,
        applicationStatusMessage = applicationStatus?.message,
        setConnectionStatus = { status -> connectionStatus = status },
        settingsSyncHost = settingsSyncHost,
        sonicHomeDiscoveryController = sonicHomeDiscoveryController,
        sonicDiscoveryProvider = connectedProvider,
        sonicDiscoveryEnabled = playbackSettings.sonicSimilarityEnabled &&
            connectedProvider?.capabilities?.supportsSonicSimilarity == true &&
            !libraryController.syncing,
        sonicDiscoverySourceId = connectedSourceId,
        sonicDiscoveryTrackId = nowPlayingTrack?.id?.value,
        sonicDiscoveryQueueSize = playbackQueue.tracks.size,
        connectionForm = connectionForm,
        connectedProvider = connectedProvider,
        connectedSourceId = connectedSourceId,
        setAvailableMusicFolders = { folders -> availableMusicFolders = folders },
        setMusicFoldersStatus = { status -> musicFoldersStatus = status },
        downloadsController = downloadsController,
        playlistSignatures = playlistsController.playlists.map { playlist ->
            "${playlist.id}:${playlist.trackCount}:${playlist.isSmart}"
        },
        nowPlayingFavoriteTimestamp = nowPlayingTrack?.favoritedAtIso8601,
    )

    loadHomeContentAction.value = homeController::loadHomeContent
    refreshPlaylistsAction.value = playlistsController::refreshPlaylists

    val savedMediaSources = mediaSourcesRevision.let {
        storage.mediaSources().visibleServerConnections(connectedSourceId)
    }
    val shellConnection = desktopShellConnection(
        status = connectionStatus,
        runtimeState = connectionRuntimeState,
        connectionForm = connectionForm,
        availableMusicFolders = availableMusicFolders,
        musicFoldersStatus = musicFoldersStatus,
        savedMediaSources = savedMediaSources,
        connectedSourceId = connectedSourceId,
    )
    val shellCapabilities = desktopShellCapabilities(
        playbackEngine = playbackEngine,
        connectedProvider = connectedProvider,
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
                settingsSyncHost.savePlaybackSettings(playbackSettings.copy(radioDjs = emptyList()))
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
                settingsSyncHost.saveVisualizerSettings(
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
                settingsSyncHost.savePlaybackSettings(playbackSettings)
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
                request.queueIndex?.let(playbackController::moveQueueTrackNext)
            NowPlayingQueueAction.RemoveFromQueue ->
                request.queueIndex?.let(playbackController::removeFromQueue)
            NowPlayingQueueAction.EmptyQueue ->
                playbackController.emptyQueue()
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
    val desktopNowPlayingPresentation = rememberDesktopNowPlayingPresentation(
        playbackEngine = playbackEngine,
        connectedProvider = connectedProvider,
        nowPlayingTrack = nowPlayingTrack,
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
    )
    val desktopNowPlayingActions = desktopNowPlayingActions(
        nowPlayingTrack = nowPlayingTrack,
        playbackQueue = playbackQueue,
        relatedTracks = nowPlayingController.relatedTracks,
        appActions = appActions,
        playlistsController = playlistsController,
        onPlaybackAction = handleNowPlayingPlaybackAction,
        onDisplayAction = handleNowPlayingDisplayAction,
        onQueueAction = handleNowPlayingQueueAction,
        onSleepTimerAction = handleNowPlayingSleepTimerAction,
        onSelectionAction = handleNowPlayingSelectionAction,
    )

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
                    if (appRoute == NaviampRoute.Player && playerTrack != null) {
                        DesktopPlayerRouteContent(
                            appColors = appColors,
                            presentation = desktopNowPlayingPresentation,
                            actions = desktopNowPlayingActions,
                        )
                    } else {
                        val desktopDownloadedTracks = remember(
                            connectedSourceId,
                            downloadsController.refreshToken,
                            cacheStats.downloadCount,
                        ) {
                            connectedSourceId?.let(storage::downloadedTracks).orEmpty()
                        }
                        val desktopSettingsSync = settingsSyncUi(
                            directoryPath = settingsSyncSettings.directoryPath,
                            autoExportEnabled = settingsSyncSettings.autoExportEnabled,
                            status = settingsSyncStatus,
                            capabilities = shellCapabilities,
                        )
                        val desktopSettingsSyncActions = settingsSyncHost.actions()
                        val desktopShellState = desktopAppShellUiState(
                            DesktopAppShellStateContext(
                                capabilities = shellCapabilities,
                                connection = shellConnection,
                                connectedSourceId = connectedSourceId,
                                provider = connectedProvider,
                                route = appRoute,
                                about = about,
                                playbackEngine = playbackEngine,
                                interfaceSettings = interfaceSettings,
                                playbackSettings = playbackSettings,
                                cacheSettings = cacheSettings,
                                cacheStats = cacheStats,
                                homeContent = homeContent,
                                homeController = homeController,
                                downloadsController = downloadsController,
                                downloadedTracks = desktopDownloadedTracks,
                                mixBuilderController = mixBuilderController,
                                sonicPathController = sonicPathController,
                                sonicMixController = sonicMixController,
                                sonicHomeDiscoveryController = sonicHomeDiscoveryController,
                                albumController = albumController,
                                artistController = artistController,
                                playlistsController = playlistsController,
                                libraryController = libraryController,
                                searchController = searchController,
                                internetRadioController = internetRadioController,
                            ),
                        )
                        val connectionActions = desktopConnectionSettingsActions(
                            connectionForm = connectionForm,
                            savedMediaSources = savedMediaSources,
                            appActions = appActions,
                            connectionLifecycleController = connectionLifecycleController,
                        )
                        val valueActions = desktopSettingsValueActions(
                            onInterfaceSettingsChanged = { settings ->
                                interfaceSettings = settings.normalized()
                                settingsStore.saveInterfaceSettings(interfaceSettings)
                                settingsSyncHost.markChangedAndAutoExport()
                            },
                            settingsMaintenanceController = settingsMaintenanceController,
                            cacheSettingsController = cacheSettingsController,
                        )
                        val maintenanceActions = desktopSettingsMaintenanceActions(
                            onOpenStatsForNerds = { showStatsForNerds = true },
                            appActions = appActions,
                            libraryController = libraryController,
                        )
                        val desktopShellActions = desktopAppShellActions(
                            DesktopAppShellActionContext(
                                route = appRoute,
                                setRoute = { route -> appRoute = route },
                                provider = connectedProvider,
                                homeContent = homeContent,
                                downloadedTracks = desktopDownloadedTracks,
                                appActions = appActions,
                                homeController = homeController,
                                sonicHomeDiscoveryController = sonicHomeDiscoveryController,
                                searchController = searchController,
                                libraryController = libraryController,
                                playlistsController = playlistsController,
                                smartPlaylistsController = smartPlaylistsController,
                                internetRadioController = internetRadioController,
                                albumController = albumController,
                                artistController = artistController,
                                mixBuilderController = mixBuilderController,
                                radioController = radioController,
                                sonicPathController = sonicPathController,
                                sonicMixController = sonicMixController,
                                connectionActions = connectionActions,
                                valueActions = valueActions,
                                maintenanceActions = maintenanceActions,
                            ),
                        )
                        DesktopRouteContent(
                            shellState = desktopShellState,
                            shellActions = desktopShellActions,
                            appColors = appColors,
                            appRoute = appRoute,
                            libraryListState = libraryListState,
                            settingsSync = desktopSettingsSync,
                            settingsSyncActions = desktopSettingsSyncActions,
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
                        DesktopShellChrome(
                            appColors = appColors,
                            presentation = desktopNowPlayingPresentation,
                            nowPlayingActions = desktopNowPlayingActions,
                            showMiniPlayer = nowPlayingTrack != null && !connectionForm.isOpen,
                            supportsDownloads = DesktopCapabilityPresentation.downloads.visible,
                            selectedRoute = appRoute,
                            albumDetailBackRoute = albumController.albumDetailBackRoute,
                            artistDetailBackRoute = artistController.artistDetailBackRoute,
                            onOpenPlayer = { appRoute = NaviampRoute.Player },
                            onRouteSelected = { route -> appRoute = route },
                        )
    }
}
}
}
}
