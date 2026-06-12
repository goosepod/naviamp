package app.naviamp.desktop

import app.naviamp.domain.cache.StorageCacheStats

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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
import app.naviamp.desktop.settings.PlaybackSettings
import app.naviamp.desktop.settings.PlaybackSessionSettings
import app.naviamp.desktop.settings.RecentRadioStream
import app.naviamp.desktop.settings.VisualizerSettings
import app.naviamp.domain.settings.effectiveForEngine
import app.naviamp.domain.settings.PlaybackSettingsMaintenanceController
import app.naviamp.domain.settings.playbackSettingsChange
import app.naviamp.domain.settings.restoredPlaybackQueue
import app.naviamp.domain.settings.restoredTrackSession
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
import app.naviamp.ui.nowPlayingQueueIndex
import app.naviamp.ui.nowPlayingRelatedIndex

@Composable
@NonRestartableComposable
fun NaviampApp(
    dependencies: DesktopAppDependencies = remember { DesktopAppDependencies() },
) {
    val isDark = isSystemInDarkTheme()
    val appColors = if (isDark) DesktopAppColors.Dark else DesktopAppColors.Light
    val colorScheme = if (isDark) darkColorScheme() else lightColorScheme()
    val settingsStore = dependencies.settingsStore
    val playbackEngine = dependencies.playbackEngine
    val storage = dependencies.storage
    val imageCacheRepository: ImageCacheRepository = dependencies.imageCacheRepository
    val savedMediaSource = remember { storage.latestMediaSource() }
    val savedConnection = remember {
        savedMediaSource?.toNavidromeConnection() ?: settingsStore.loadConnection()?.toConnection()
    }
    val savedPlaybackSession = remember { settingsStore.loadPlaybackSession() }
    val savedVisualizer = remember { settingsStore.loadVisualizerSettings() }
    val savedNavigation = remember { settingsStore.loadNavigationSettings() }
    val savedSearch = remember { settingsStore.loadSearchSettings() }
    val savedRecentRadioStreams = remember { settingsStore.loadRecentRadioStreams() }
    val savedRecentPlaylistIds = remember { settingsStore.loadRecentPlaylistIds() }
    val savedRecentInternetRadioStations = remember { settingsStore.loadRecentInternetRadioStations() }
    var cacheStats by remember { mutableStateOf(StorageCacheStats()) }
    var connectedSourceId by remember { mutableStateOf(savedMediaSource?.id) }
    val popularTracksService = remember(dependencies) { dependencies.popularTracksService { connectedSourceId } }
    val similarArtistsService = remember(dependencies) { dependencies.similarArtistsService { connectedSourceId } }
    val desktopPlaybackAudioAssets = dependencies.playbackAudioAssets
    val audioMetadataSidecarService = dependencies.audioMetadataSidecarService
    val lyricsSidecarService = dependencies.lyricsSidecarService
    val audioWaveformService = dependencies.audioWaveformService
    val playbackSidecarService = dependencies.playbackSidecarService
    var cacheSettings by remember {
        mutableStateOf(settingsStore.loadCacheSettings().normalized())
    }
    val playlistEngine = remember(dependencies) {
        dependencies.playlistEngine(
            sourceIdProvider = { connectedSourceId },
            audioCachingEnabledProvider = { cacheSettings.audioCachingEnabled },
            audioPrefetchDepthProvider = { cacheSettings.audioPrefetchDepth },
        )
    }
    val librarySync = remember(dependencies) { dependencies.librarySync() }
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
    var mediaSourcesRevision by remember { mutableIntStateOf(0) }
    var isConnecting by remember { mutableStateOf(false) }
    var connectionStatus by remember { mutableStateOf<String?>(null) }
    var connectedProvider by remember { mutableStateOf<NavidromeProvider?>(null) }
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
    var playbackSettings by remember {
        mutableStateOf(settingsStore.loadPlaybackSettings().effectiveForEngine(playbackEngine))
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

    fun rememberRadioStream(stream: RecentRadioStream) {
        rememberDesktopRadioStream(
            stream = stream,
            recentRadioStreams = recentRadioStreams,
            setRecentRadioStreams = { streams -> recentRadioStreams = streams },
            saveRecentRadioStreams = settingsStore::saveRecentRadioStreams,
            homeContent = homeContent,
            setHomeContent = { content -> homeContent = content },
        )
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
        mediaSourceRepository = storage,
        cacheMaintenanceRepository = storage,
        libraryOffsetForLetter = storage::libraryOffsetForLetter,
        librarySync = librarySync,
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
        isConnecting = { isConnecting },
        setConnecting = { connecting -> isConnecting = connecting },
        savedPlaybackSession = { savedPlaybackSession },
        playlistCallbacks = { playlistCallbacksRef.value ?: error("Playlist callbacks are not ready.") },
        streamQuality = { playbackSettings.streamQuality(playbackEngine) },
        replayGainMode = { playbackSettings.replayGainMode },
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
        setPlaybackState = { state -> playbackState = state },
        setPlaybackProgress = { progress -> playbackProgress = progress },
        setPlaybackQueue = { queue -> playbackQueue = queue },
        refreshLibrarySnapshot = libraryController::refreshLibrarySnapshot,
        loadHomeContent = { provider -> loadHomeContentAction.value(provider) },
        refreshPlaylists = { refreshPlaylistsAction.value() },
        refreshInternetRadioStations = internetRadioController::refreshStations,
        startLibrarySync = libraryController::startLibrarySync,
        checkLibraryFreshness = libraryController::checkLibraryFreshness,
        connectedSourceId = { connectedSourceId },
        savedConnectionForLogin = { connectionForm.savedConnectionForLogin },
        setSavedConnectionForLogin = { connection -> connectionForm.savedConnectionForLogin = connection },
        incrementMediaSourcesRevision = { mediaSourcesRevision++ },
        applyConnectionFormState = connectionForm::apply,
        setConnectionFormOpen = { isOpen -> connectionForm.isOpen = isOpen },
        setConnectionStatus = { status -> connectionStatus = status },
        setAppRoute = { route -> appRoute = route },
        appRoute = { appRoute },
    )
    }

    val playlistCallbacks = desktopPlaylistCallbacks(
        provider = { connectedProvider },
        appRoute = { appRoute },
        setAppRoute = { route -> appRoute = route },
        openPlayerOnTrackStart = { openPlayerOnTrackStart },
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

    val searchController = remember {
        DesktopSearchController(
        settingsStore = settingsStore,
        providerResponseCacheRepository = storage,
        provider = { connectedProvider },
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
        savePlaybackSettings = settingsStore::savePlaybackSettings,
        reloadLyricsSidecars = nowPlayingController::clearLyricsAndReloadAnalysis,
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

    loadHomeContentAction.value = homeController::loadHomeContent
    refreshPlaylistsAction.value = playlistsController::refreshPlaylists

    val savedMediaSources = mediaSourcesRevision.let { storage.mediaSources() }
    val statsForNerdsInfo = desktopStatsForNerdsInfoOrNull(
        showStatsForNerds = showStatsForNerds,
        appRoute = appRoute,
        connectionForm = connectionForm,
        connectedProvider = connectedProvider,
        connectedSourceId = connectedSourceId,
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
                settingsStore.savePlaybackSettings(playbackSettings)
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
                settingsStore.saveVisualizerSettings(
                    VisualizerSettings(selectedVisualizer = visualizer.name),
                )
            }
            NowPlayingDisplayAction.Collapse -> appRoute = lastContentRoute
        }
    }
    val handleNowPlayingQueueAction: (NowPlayingQueueActionRequest) -> Unit = { request ->
        when (request.action) {
            NowPlayingQueueAction.SaveQueueAsPlaylist -> playlistsController.saveQueueAsPlaylist(request.playlistName)
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
                            homeStatus = homeStatus,
                            homeContent = homeContent,
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
                            savedMediaSources = savedMediaSources,
                            isConnecting = isConnecting,
                            playbackSettings = playbackSettings,
                            playbackEngine = playbackEngine,
                            supportsSonicSimilarity =
                                connectedProvider?.capabilities?.supportsSonicSimilarity == true,
                            onConnect = { appActions.connectToServer() },
                            onNewConnection = connectionLifecycleController::openNewConnectionForm,
                            onEditConnection = connectionLifecycleController::openSavedConnectionForm,
                            onConnectSavedConnection = connectionLifecycleController::connectSavedConnection,
                            onDeleteConnection = { source -> appActions.deleteConnection(source) },
                            onCancelConnectionForm = connectionLifecycleController::closeConnectionForm,
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
                            onRefreshLibrary = { libraryController.startLibrarySync(force = true) },
                            onResetDatabase = { appActions.resetDatabase() },
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
                        if (nowPlayingTrack != null) {
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
