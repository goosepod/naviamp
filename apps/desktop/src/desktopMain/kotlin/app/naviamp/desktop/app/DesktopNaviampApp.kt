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
import app.naviamp.domain.cache.ImageCacheRepository
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.playback.PlaybackProgress
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
import app.naviamp.desktop.settings.DesktopSettingsSyncFile
import app.naviamp.desktop.settings.DesktopSettingsSyncSettings
import app.naviamp.desktop.settings.VisualizerSettings
import app.naviamp.domain.settings.SettingsSyncCoordinator
import app.naviamp.domain.settings.SettingsSyncDocument
import app.naviamp.domain.settings.SettingsSyncLocalSnapshot
import app.naviamp.domain.settings.SettingsSyncOperationKind
import app.naviamp.domain.settings.SettingsSyncOperationResult
import app.naviamp.domain.settings.SettingsSyncRuntimeState
import app.naviamp.domain.settings.buildSettingsSyncDocument
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
    val savedPlaybackSession = remember { settingsStore.loadPlaybackSession() }
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
    val playlistEngine = remember(dependencies) {
        dependencies.playlistEngine(
            sourceIdProvider = { connectedSourceId },
            audioCachingEnabledProvider = { cacheSettings.audioCachingEnabled },
            audioPrefetchDepthProvider = { cacheSettings.audioPrefetchDepth },
            playbackSettingsProvider = { playbackSettings },
        )
    }
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
    var isConnecting by remember { mutableStateOf(false) }
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
    var appRoute by remember {
        mutableStateOf(
            restoredRoute(
                savedRouteName = savedNavigation.route,
                hasConnection = savedConnection != null,
                hasRestoredTrack = restoredTrack != null,
            ),
        )
    }
    var lastContentRoute by remember {
        mutableStateOf(restoredLastContentRoute(savedNavigation.lastContentRoute))
    }
    var nowPlayingTrack by remember { mutableStateOf(restoredTrack) }
    var nowPlayingCoverArtUrl by remember { mutableStateOf<String?>(null) }
    var nowPlayingLyricsVisible by remember { mutableStateOf(false) }
    var nowPlayingInternetRadioStation by remember { mutableStateOf(restoredInternetRadioStation) }
    var nowPlayingStreamMetadata by remember { mutableStateOf(PlaybackStreamMetadata()) }
    var playbackState by remember { mutableStateOf<PlaybackState>(PlaybackState.Idle) }
    var playbackProgress by remember { mutableStateOf(PlaybackProgress.Unknown) }
    var playbackQueue by remember {
        mutableStateOf(savedPlaybackSession?.restoredPlaybackQueue() ?: PlaybackQueue())
    }
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
    var repeatMode by remember { mutableStateOf(RepeatMode.Off) }
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
    var pendingSeekPositionSeconds by remember { mutableStateOf<Double?>(null) }
    var pendingSeekIssuedAtMillis by remember { mutableStateOf<Long?>(null) }
    var lastPlaybackProgressUiUpdateMillis by remember { mutableLongStateOf(0L) }
    var playReportSessionId by remember { mutableStateOf(0) }
    var submittedPlayReportSessionId by remember { mutableStateOf<Int?>(null) }
    val nowPlayingPresentation = rememberDesktopNowPlayingPresentationState(
        initialVisualizerSettings = savedVisualizer,
        appColors = appColors,
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
        playbackSessionRepository = settingsStore,
        playbackEngine = playbackEngine,
        playlistEngine = playlistEngine,
        provider = { connectedProvider },
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
        submittedPlayReportSessionId = { submittedPlayReportSessionId },
        setSubmittedPlayReportSessionId = { sessionId -> submittedPlayReportSessionId = sessionId },
        setPendingSeekPositionSeconds = { position -> pendingSeekPositionSeconds = position },
        setPendingSeekIssuedAtMillis = { millis -> pendingSeekIssuedAtMillis = millis },
        setOpenPlayerOnTrackStart = { shouldOpen -> openPlayerOnTrackStart = shouldOpen },
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
        stopPlayback = playbackEngine::stop,
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

    fun buildLocalSettingsSyncDocument(updatedAtEpochMillis: Long): SettingsSyncDocument =
        buildSettingsSyncDocument(
            snapshot = SettingsSyncLocalSnapshot(
                serverProfiles = storage.mediaSources(),
                interfaceSettings = interfaceSettings,
                playback = playbackSettings,
                visualizer = VisualizerSettings(
                    selectedVisualizer = nowPlayingPresentation.selectedVisualizer.name,
                ),
                recentRadioStreams = recentRadioStreams,
                recentInternetRadioStations = settingsStore.loadRecentInternetRadioStations(),
            ),
            nowEpochMillis = updatedAtEpochMillis,
            deviceId = DesktopSettingsSyncDeviceId,
        )

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

    val settingsSyncCoordinator = SettingsSyncCoordinator(
        deviceId = DesktopSettingsSyncDeviceId,
        state = ::settingsSyncRuntimeState,
        saveState = ::saveSettingsSyncRuntimeState,
        nowEpochMillis = { System.currentTimeMillis() },
        buildLocalDocument = ::buildLocalSettingsSyncDocument,
        applyDocument = ::applySettingsSyncDocument,
    )

    fun updateSettingsSyncDirectory(path: String?) {
        saveSettingsSyncSettings(DesktopSettingsSyncSettings(
            directoryPath = path,
            autoExportEnabled = settingsSyncSettings.autoExportEnabled && path != null,
            lastLocalUpdateEpochMillis = settingsSyncSettings.lastLocalUpdateEpochMillis,
            lastAppliedSyncUpdateEpochMillis = settingsSyncSettings.lastAppliedSyncUpdateEpochMillis,
        ))
        settingsSyncStatus = if (settingsSyncSettings.directoryPath == null) {
            "Settings sync disabled."
        } else {
            "Settings sync folder selected."
        }
    }

    fun settingsSyncImportStatus(result: SettingsSyncOperationResult): String =
        if (result.hasServerProfiles) {
            "Settings imported. Enter the Navidrome password to finish connecting."
        } else {
            "Settings imported."
        }

    fun settingsSyncStartupStatus(result: SettingsSyncOperationResult): String =
        when (result.kind) {
            SettingsSyncOperationKind.Imported -> if (result.hasServerProfiles) {
                "Settings sync imported newer shared settings. Enter the Navidrome password to finish connecting."
            } else {
                "Settings sync imported newer shared settings."
            }
            SettingsSyncOperationKind.NoOp -> "Settings sync is up to date."
            SettingsSyncOperationKind.UnsupportedSyncFile ->
                "Settings sync file was created by a newer Naviamp version."
            SettingsSyncOperationKind.NeedsSetupChoice -> "Choose how to set up Naviamp."
            SettingsSyncOperationKind.MissingSyncLocation -> "Choose a settings sync folder first."
            SettingsSyncOperationKind.Exported -> "Settings sync exported local settings."
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
        runCatching {
            DesktopSettingsSyncFile.write(directory, document)
            DesktopSettingsSyncFile.syncFile(directory).fileName
        }.onSuccess { fileName ->
            settingsSyncCoordinator.documentWritten(document)
            settingsSyncStatus = statusMessage(fileName.toString())
        }.onFailure { error ->
            settingsSyncStatus = error.message ?: "Could not export settings sync file."
        }
    }

    fun exportSettingsSync() {
        settingsSyncCoordinator.exportCurrent(markChanged = true).documentToWrite?.let { document ->
            writeSettingsSync(document) { fileName -> "Settings exported to $fileName." }
        }
    }

    fun autoExportSettingsSync() {
        settingsSyncCoordinator.autoExport()?.documentToWrite?.let { document ->
            writeSettingsSync(document) { fileName -> "Settings auto-exported to $fileName." }
        }
    }

    fun updateSettingsSyncAutoExport(enabled: Boolean) {
        saveSettingsSyncSettings(settingsSyncSettings.copy(
            autoExportEnabled = enabled && settingsSyncSettings.directoryPath != null,
        ))
        settingsSyncStatus = if (settingsSyncSettings.autoExportEnabled) {
            "Auto-export enabled."
        } else {
            "Auto-export disabled."
        }
        if (settingsSyncSettings.autoExportEnabled) {
            autoExportSettingsSync()
        }
    }

    fun savePlaybackSettingsForSync(settings: PlaybackSettings) {
        settingsStore.savePlaybackSettings(settings)
        settingsSyncCoordinator.markLocalChanged()
        autoExportSettingsSync()
    }

    fun saveVisualizerSettingsForSync(settings: VisualizerSettings) {
        settingsStore.saveVisualizerSettings(settings)
        settingsSyncCoordinator.markLocalChanged()
        autoExportSettingsSync()
    }

    fun saveRecentRadioStreamsForSync(streams: List<RecentRadioStream>) {
        settingsStore.saveRecentRadioStreams(streams)
        settingsSyncCoordinator.markLocalChanged()
        autoExportSettingsSync()
    }

    fun saveRecentInternetRadioStationsForSync(stations: List<SavedInternetRadioStation>) {
        settingsStore.saveRecentInternetRadioStations(stations)
        settingsSyncCoordinator.markLocalChanged()
        autoExportSettingsSync()
    }

    fun markAndAutoExportSettingsSync() {
        settingsSyncCoordinator.markLocalChanged()
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
            val document = DesktopSettingsSyncFile.read(directory)
                ?: error("No settings sync file found in that folder.")
            settingsSyncCoordinator.applySyncedDocument(document)
        }.onSuccess { result ->
            settingsSyncStatus = settingsSyncImportStatus(result)
        }.onFailure { error ->
            settingsSyncStatus = error.message ?: "Could not import settings sync file."
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
            val syncedDocument = DesktopSettingsSyncFile.read(directory)
            settingsSyncCoordinator.reconcileStartup(
                syncedDocument = syncedDocument,
                syncLocationConfigured = true,
            )
        }.onSuccess { result ->
            if (result.kind == SettingsSyncOperationKind.Exported) {
                result.documentToWrite?.let { document ->
                    writeSettingsSync(document) { fileName -> "Settings sync exported local settings to $fileName." }
                }
            } else {
                settingsSyncStatus = settingsSyncStartupStatus(result)
            }
        }.onFailure { error ->
            settingsSyncStatus = error.message ?: "Could not check settings sync folder."
        }
    }

    val playlistCallbacksRef = remember { mutableStateOf<PlaylistCallbacks?>(null) }
    val radioController = remember {
        DesktopRadioController(
        scope = coroutineScope,
        libraryIndexRepository = storage,
        providerResponseService = ProviderResponseService(storage),
        playlistEngine = playlistEngine,
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
        playbackSessionRepository = settingsStore,
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
        handleDesktopPlayPauseCommand(
            playbackState = playbackState,
            hasPlaybackTarget = nowPlayingTrack != null || nowPlayingInternetRadioStation != null,
            playbackEngine = playbackEngine,
            startOrRestorePlayback = {
                openPlayerOnTrackStart = false
                internetRadioController.playCurrentSelection()
            },
        )
    }

    val libraryController = remember {
        DesktopLibraryController(
            scope = coroutineScope,
            libraryIndexRepository = storage,
            cacheMaintenanceRepository = storage,
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
        playbackSessionRepository = settingsStore,
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
        isConnecting = { isConnecting },
        setConnecting = { connecting -> isConnecting = connecting },
        savedPlaybackSession = { savedPlaybackSession },
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
        clearSubmittedPlayReportSessionId = { submittedPlayReportSessionId = null },
        incrementNowPlayingWaveformReloadToken = nowPlayingController::incrementWaveformReloadToken,
        reportNowPlaying = playbackController::reportNowPlaying,
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
        maybeReportPlayed = playbackController::maybeReportPlayed,
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
        downloadReplacementRepository = storage,
        cacheMaintenanceRepository = storage,
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

    val mediaActionsController = remember {
        DesktopMediaActionsController(
        scope = coroutineScope,
        trackMetadataRepository = storage,
        playbackEngine = playbackEngine,
        playlistEngine = playlistEngine,
        provider = { connectedProvider },
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
        nowPlayingTrack = nowPlayingTrack,
        playbackState = playbackState,
        nowPlayingVisualizerVisible = nowPlayingVisualizerVisible,
        appRoute = appRoute,
        playbackSettings = playbackSettings,
        cacheSettings = cacheSettings,
        albumDetailBackRoute = albumController.albumDetailBackRoute,
        artistDetailBackRoute = artistController.artistDetailBackRoute,
        lastContentRoute = lastContentRoute,
        setLastContentRoute = { route -> lastContentRoute = route },
        setNowPlayingVisualizerFrame = nowPlayingPresentation::updateVisualizerFrame,
        updateAudioCacheLimit = { maxBytes -> storage.updateAudioCacheLimit(maxBytes) },
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

    loadHomeContentAction.value = homeController::loadHomeContent
    refreshPlaylistsAction.value = playlistsController::refreshPlaylists

    val savedMediaSources = mediaSourcesRevision.let {
        storage.mediaSources().visibleServerConnections(connectedSourceId)
    }
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
                    playlistEngine.replaceQueue(playbackQueue.moveToNext(index))
                }
            NowPlayingQueueAction.RemoveFromQueue ->
                request.queueIndex?.let { index ->
                    playlistEngine.replaceQueue(playbackQueue.removeAt(index))
                }
            NowPlayingQueueAction.EmptyQueue ->
                playlistEngine.replaceQueue(playbackQueue.clearUpcoming())
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

    CompositionLocalProvider(app.naviamp.ui.LocalTrackSwipeSettings provides interfaceSettings.trackSwipes) {
    DesktopAppSurface(
            colorScheme = colorScheme,
            appColors = appColors,
            statsForNerdsInfo = statsForNerdsInfo,
            backgroundStart = nowPlayingPresentation.backgroundStart,
            backgroundMid = nowPlayingPresentation.backgroundMid,
            backgroundEnd = nowPlayingPresentation.backgroundEnd,
            targetBackgroundColors = nowPlayingPresentation.targetBackgroundColors,
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
                            playbackEngine = playbackEngine,
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
                            playlistsController = playlistsController,
                        )
                    } else {
                        DesktopAppRouteContent(
                            appColors = appColors,
                            appRoute = appRoute,
                            connectionStatus = connectionStatus,
                            about = about,
                            homeStatus = homeStatus,
                            homeContent = homeContent,
                            homeRefreshing = homeController.refreshing,
                            onRefreshHome = { connectedProvider?.let(homeController::loadHomeContent) },
                            sonicHomeDiscoveryRows = sonicHomeDiscoveryController.rows,
                            coverArtUrl = { coverArtId -> coverArtId?.let { connectedProvider?.coverArtUrl(it) } },
                            appActions = appActions,
                            playlistsController = playlistsController,
                            internetRadioController = internetRadioController,
                            libraryController = libraryController,
                            searchController = searchController,
                            smartPlaylistsController = smartPlaylistsController,
                            onRouteSelected = { route -> appRoute = route },
                            onOpenArtistMixBuilder = {
                                appRoute = DesktopAppRoute.ArtistMix
                            },
                            onOpenAlbumMixBuilder = {
                                appRoute = DesktopAppRoute.AlbumMix
                            },
                            selectedAlbum = albumController.selectedAlbum,
                            selectedAlbumDetails = albumController.selectedAlbumDetails,
                            selectedAlbumStatus = albumController.selectedAlbumStatus,
                            albumDetailBackRoute = albumController.albumDetailBackRoute,
                            selectedArtist = artistController.selectedArtist,
                            selectedArtistDetails = artistController.selectedArtistDetails,
                            selectedArtistPopularTracks = artistController.selectedArtistPopularTracks,
                            selectedArtistSimilarArtists = artistController.selectedArtistSimilarArtists,
                            selectedArtistStatus = artistController.selectedArtistStatus,
                            selectedArtistPopularTracksStatus = artistController.selectedArtistPopularTracksStatus,
                            selectedArtistSimilarArtistsStatus = artistController.selectedArtistSimilarArtistsStatus,
                            artistDetailBackRoute = artistController.artistDetailBackRoute,
                            playlists = playlistsController.playlists,
                            playlistTracksById = playlistsController.playlistTracksById,
                            recentPlaylistIds = playlistsController.recentPlaylistIds,
                            playlistSortMode = playlistsController.sortMode,
                            playlistStatus = playlistsController.status,
                            onPlaylistSortModeChanged = playlistsController::updateSortMode,
                            onPlaylistRenameRequested = playlistsController::requestPlaylistRename,
                            onPlaylistDeleteRequested = playlistsController::requestPlaylistDelete,
                            selectedPlaylist = playlistsController.selectedPlaylist,
                            selectedPlaylistTracks = playlistsController.selectedPlaylistTracks,
                            selectedPlaylistStatus = playlistsController.selectedPlaylistStatus,
                            librarySnapshot = libraryController.snapshot,
                            libraryQuery = libraryController.query,
                            libraryTab = libraryController.tab,
                            libraryStatus = libraryController.status,
                            isLibrarySyncing = libraryController.syncing,
                            libraryListState = libraryListState,
                            onLibraryQueryChanged = libraryController::updateQuery,
                            searchQuery = searchController.query,
                            searchResults = searchController.results,
                            searchStatus = searchController.status,
                            isSearching = searchController.searching,
                            artistMixBuilder = mixBuilderController.artistUi(
                                coverArtUrl = { coverArtId -> coverArtId?.let { connectedProvider?.coverArtUrl(it) } },
                            ),
                            onArtistMixQueryChanged = mixBuilderController::setArtistQuery,
                            onArtistMixSearch = mixBuilderController::searchArtistSuggestions,
                            onArtistMixArtistSelected = { item -> mixBuilderController.selectArtistByItemId(item.id) },
                            onArtistMixArtistRemoved = { item -> mixBuilderController.removeArtistByItemId(item.id) },
                            onArtistMixReset = mixBuilderController::resetArtistBuilder,
                            onArtistMixPlay = { mixBuilderController.playArtistMix(radioController) },
                            albumMixBuilder = mixBuilderController.albumUi(
                                coverArtUrl = { coverArtId -> coverArtId?.let { connectedProvider?.coverArtUrl(it) } },
                            ),
                            onAlbumMixQueryChanged = mixBuilderController::setAlbumQuery,
                            onAlbumMixSearch = mixBuilderController::searchAlbumSuggestions,
                            onAlbumMixAlbumSelected = { item -> mixBuilderController.selectAlbumByItemId(item.id) },
                            onAlbumMixAlbumRemoved = { item -> mixBuilderController.removeAlbumByItemId(item.id) },
                            onAlbumMixReset = mixBuilderController::resetAlbumBuilder,
                            onAlbumMixPlay = { mixBuilderController.playAlbumMix(radioController) },
                            genreMixBuilder = mixBuilderController.genreUi(),
                            onGenreMixQueryChanged = mixBuilderController::setGenreQuery,
                            onGenreMixSearch = mixBuilderController::refreshGenreSuggestions,
                            onGenreMixGenreSelected = { item -> mixBuilderController.selectGenreByItemId(item.id) },
                            onGenreMixGenreRemoved = { item -> mixBuilderController.removeGenreByItemId(item.id) },
                            onGenreMixReset = mixBuilderController::resetGenreBuilder,
                            onGenreMixPlay = { mixBuilderController.playGenreMix(radioController) },
                            sonicPathBuilder = sonicPathController.ui(
                                coverArtUrl = { coverArtId -> coverArtId?.let { connectedProvider?.coverArtUrl(it) } },
                            ),
                            onSonicPathStartQueryChanged = sonicPathController::updateStartQuery,
                            onSonicPathEndQueryChanged = sonicPathController::updateEndQuery,
                            onSonicPathStartSearch = sonicPathController::searchStartTracks,
                            onSonicPathEndSearch = sonicPathController::searchEndTracks,
                            onSonicPathStartTrackSelected = sonicPathController::selectStartTrack,
                            onSonicPathEndTrackSelected = sonicPathController::selectEndTrack,
                            onSonicPathStartTrackCleared = sonicPathController::clearStartTrack,
                            onSonicPathEndTrackCleared = sonicPathController::clearEndTrack,
                            onSonicPathCountChanged = sonicPathController::updateCount,
                            onSonicPathBuild = sonicPathController::buildPath,
                            onSonicPathReset = sonicPathController::reset,
                            onSonicPathPlay = sonicPathController::playPath,
                            onSonicPathAddToQueue = sonicPathController::addPathToQueue,
                            onSonicPathSaveAsPlaylist = { name ->
                                playlistsController.saveTracksAsPlaylist(
                                    name = name,
                                    tracks = sonicPathController.playlistTracks(),
                                    label = "sonic path",
                                )
                            },
                            sonicMixBuilder = sonicMixController.ui(
                                coverArtUrl = { coverArtId -> coverArtId?.let { connectedProvider?.coverArtUrl(it) } },
                            ),
                            onSonicMixQueryChanged = sonicMixController::updateQuery,
                            onSonicMixSearch = sonicMixController::searchTracks,
                            onSonicMixTrackSelected = sonicMixController::selectTrack,
                            onSonicMixTrackRemoved = sonicMixController::removeTrack,
                            onSonicMixTargetLengthChanged = sonicMixController::updateTargetLength,
                            onSonicMixBiasChanged = sonicMixController::updateBias,
                            onSonicMixBuild = sonicMixController::buildMix,
                            onSonicMixReset = sonicMixController::reset,
                            onSonicMixPlay = sonicMixController::playMix,
                            onSonicMixAddToQueue = sonicMixController::addMixToQueue,
                            onSonicMixSaveAsPlaylist = { name ->
                                playlistsController.saveTracksAsPlaylist(
                                    name = name,
                                    tracks = sonicMixController.playlistTracks(),
                                    label = "sonic mix",
                                )
                            },
                            internetRadioStations = internetRadioController.stations,
                            internetRadioStatus = internetRadioController.status,
                            onSaveInternetRadioStation = internetRadioController::saveStation,
                            onDeleteInternetRadioStation = internetRadioController::deleteStation,
                            connectedSourceId = connectedSourceId,
                            downloadRefreshToken = downloadsController.refreshToken,
                            downloadStatus = downloadsController.status,
                            cacheSettings = cacheSettings,
                            cacheStats = cacheStats,
                            downloadedTracks = storage::downloadedTracks,
                            connectionForm = connectionForm,
                            availableMusicFolders = availableMusicFolders,
                            musicFoldersStatus = musicFoldersStatus,
                            savedMediaSources = savedMediaSources,
                            isConnecting = isConnecting,
                            interfaceSettings = interfaceSettings,
                            playbackSettings = playbackSettings,
                            playbackEngine = playbackEngine,
                            supportsSonicSimilarity =
                                connectedProvider?.capabilities?.supportsSonicSimilarity == true,
                            settingsSyncDirectoryPath = settingsSyncSettings.directoryPath,
                            settingsSyncAutoExportEnabled = settingsSyncSettings.autoExportEnabled,
                            settingsSyncStatus = settingsSyncStatus,
                            onConnect = { appActions.connectToServer() },
                            onNewConnection = connectionLifecycleController::openNewConnectionForm,
                            onEditConnection = connectionLifecycleController::openSavedConnectionForm,
                            onConnectSavedConnection = connectionLifecycleController::connectSavedConnection,
                            onDeleteConnection = { source -> appActions.deleteConnection(source) },
                            onCancelConnectionForm = connectionLifecycleController::closeConnectionForm,
                            onSettingsSyncDirectoryChanged = ::updateSettingsSyncDirectory,
                            onSettingsSyncDirectorySelectedForImport = ::selectSettingsSyncDirectoryAndImport,
                            onSettingsSyncAutoExportChanged = ::updateSettingsSyncAutoExport,
                            onSettingsSyncExport = ::exportSettingsSync,
                            onSettingsSyncImport = ::importSettingsSync,
                            onInterfaceSettingsChanged = { settings: InterfaceSettings ->
                                interfaceSettings = settings.normalized()
                                settingsStore.saveInterfaceSettings(interfaceSettings)
                                markAndAutoExportSettingsSync()
                            },
                            onPlaybackSettingsChanged = settingsMaintenanceController::applyPlaybackSettings,
                            onPlaybackSettingsChangedAndRedownload =
                                settingsMaintenanceController::applyPlaybackSettingsAndRedownload,
                            onCacheSettingsChanged = { settings ->
                                cacheSettings = settings.normalized()
                                settingsStore.saveCacheSettings(cacheSettings)
                            },
                            onOpenStatsForNerds = { showStatsForNerds = true },
                            onClearCache = { appActions.clearCacheData() },
                            onClearLibrary = { appActions.clearLibraryData() },
                            onRefreshLibrary = libraryController::refreshLibrarySnapshot,
                            onResetDatabase = { appActions.resetDatabase() },
                            onSonicHomeDiscoveryTrackAction = { request ->
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
