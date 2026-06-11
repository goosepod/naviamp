package app.naviamp.desktop

import app.naviamp.domain.cache.StorageCacheStats

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.Genre
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Lyrics
import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.internetRadioStationId
import app.naviamp.domain.isInternetRadioTrack
import app.naviamp.domain.cache.ImageCacheRepository
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackVisualizerFrame
import app.naviamp.domain.playback.VisualizerPlaybackEngine
import app.naviamp.desktop.playback.PlaylistCallbacks
import app.naviamp.desktop.playback.desktopPlaylistCallbacks
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.SleepTimerState
import app.naviamp.domain.playback.SleepTimerController
import app.naviamp.domain.home.HomeContent
import app.naviamp.domain.provider.PendingPlaybackAction
import app.naviamp.domain.popular.SimilarArtistMatch
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.radio.InternetRadioStationManager
import app.naviamp.domain.waveform.AudioWaveform
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
import app.naviamp.ui.NaviampPlayerColors
import app.naviamp.ui.NaviampSleepTimerUi
import app.naviamp.ui.NaviampSleepTimerExpiryEffect
import app.naviamp.ui.NaviampVisualizer
import app.naviamp.ui.radioArtworkNeedsTrackLookup
import app.naviamp.ui.radioTrackArtworkKey
import app.naviamp.ui.radioTrackArtworkQuery
import app.naviamp.ui.rememberPlatformCoverArtPlayerColors
import app.naviamp.ui.toNaviampSleepTimerUi

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
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var playlistStatus by remember { mutableStateOf<String?>(null) }
    var pendingPlaybackAction by remember { mutableStateOf<PendingPlaybackAction?>(null) }
    var playlistSortMode by remember { mutableStateOf(DesktopPlaylistSortMode.Alphabetical) }
    var recentPlaylistIds by remember { mutableStateOf(savedRecentPlaylistIds) }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var selectedPlaylistTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var playlistTracksById by remember { mutableStateOf<Map<String, List<Track>>>(emptyMap()) }
    var selectedPlaylistStatus by remember { mutableStateOf<String?>(null) }
    var playlistPendingRename by remember { mutableStateOf<Playlist?>(null) }
    var playlistPendingDelete by remember { mutableStateOf<Playlist?>(null) }
    var addToPlaylistTarget by remember { mutableStateOf<AddToPlaylistTarget?>(null) }
    var addToPlaylistStatus by remember { mutableStateOf<String?>(null) }
    var recentRadioStreams by remember { mutableStateOf(savedRecentRadioStreams) }
    var selectedAlbum by remember { mutableStateOf<Album?>(null) }
    var selectedAlbumDetails by remember { mutableStateOf<AlbumDetails?>(null) }
    var selectedAlbumStatus by remember { mutableStateOf<String?>(null) }
    var albumDetailBackRoute by remember { mutableStateOf(DesktopAppRoute.Home) }
    var selectedArtist by remember { mutableStateOf<Artist?>(null) }
    var selectedArtistDetails by remember { mutableStateOf<ArtistDetails?>(null) }
    var selectedArtistStatus by remember { mutableStateOf<String?>(null) }
    var selectedArtistPopularTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var selectedArtistPopularTracksStatus by remember { mutableStateOf<String?>(null) }
    var selectedArtistSimilarArtists by remember { mutableStateOf<List<SimilarArtistMatch>>(emptyList()) }
    var selectedArtistSimilarArtistsStatus by remember { mutableStateOf<String?>(null) }
    var artistDetailBackRoute by remember { mutableStateOf(DesktopAppRoute.Search) }
    var artistDetailBackStack by remember { mutableStateOf<List<Artist>>(emptyList()) }
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
    var nowPlayingWaveform by remember { mutableStateOf<AudioWaveform?>(null) }
    var nowPlayingWaveformStatus by remember { mutableStateOf("No track") }
    var nowPlayingWaveformReloadToken by remember { mutableStateOf(0) }
    var nowPlayingVisualizerFrame by remember { mutableStateOf<PlaybackVisualizerFrame?>(null) }
    var nowPlayingVisualizerRequestedVisible by remember { mutableStateOf(false) }
    var selectedVisualizer by remember {
        mutableStateOf(
            NaviampVisualizer.entries.firstOrNull { visualizer ->
                visualizer.name == savedVisualizer.selectedVisualizer
            } ?: NaviampVisualizer.AudioSphere,
        )
    }
    var nowPlayingAudioTags by remember { mutableStateOf<List<AudioTag>?>(null) }
    var nowPlayingLyrics by remember { mutableStateOf<Lyrics?>(null) }
    var nowPlayingLyricsStatus by remember { mutableStateOf<String?>(null) }
    var nowPlayingLyricsVisible by remember { mutableStateOf(false) }
    var nowPlayingInternetRadioStation by remember { mutableStateOf(restoredInternetRadioStation) }
    var nowPlayingStreamMetadata by remember { mutableStateOf(PlaybackStreamMetadata()) }
    var radioTrackArtworkByKey by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }
    var relatedTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var playbackState by remember { mutableStateOf<PlaybackState>(PlaybackState.Idle) }
    val nowPlayingVisualizerVisible = nowPlayingVisualizerRequestedVisible &&
        (playbackState == PlaybackState.Playing || playbackState == PlaybackState.Loading)
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
    val coverArtPlayerColors = rememberPlatformCoverArtPlayerColors(nowPlayingCoverArtUrl, appColors)
    val targetBackgroundColors = if (nowPlayingTrack != null) {
        coverArtPlayerColors
    } else {
        NaviampPlayerColors.solid(appColors.background)
    }
    DesktopRadioArtworkLookupEffect(
        station = nowPlayingInternetRadioStation,
        streamMetadata = nowPlayingStreamMetadata,
        provider = connectedProvider,
        artworkByKey = radioTrackArtworkByKey,
        onArtworkResolved = { key, artworkUrl ->
            radioTrackArtworkByKey = radioTrackArtworkByKey + (key to artworkUrl)
        },
    )
    val backgroundStart by animateColorAsState(
        targetValue = targetBackgroundColors.backgroundStart,
        animationSpec = tween(durationMillis = 180, easing = LinearEasing),
        label = "backgroundStart",
    )
    val backgroundMid by animateColorAsState(
        targetValue = targetBackgroundColors.backgroundMid,
        animationSpec = tween(durationMillis = 180, easing = LinearEasing),
        label = "backgroundMid",
    )
    val backgroundEnd by animateColorAsState(
        targetValue = targetBackgroundColors.backgroundEnd,
        animationSpec = tween(durationMillis = 180, easing = LinearEasing),
        label = "backgroundEnd",
    )

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
        albumDetailBackRoute = albumDetailBackRoute,
        artistDetailBackRoute = artistDetailBackRoute,
        lastContentRoute = lastContentRoute,
        setLastContentRoute = { route -> lastContentRoute = route },
        setNowPlayingVisualizerFrame = { frame -> nowPlayingVisualizerFrame = frame },
        updateAudioCacheLimit = { maxBytes -> storage.updateAudioCacheLimit(maxBytes) },
        cancelAudioPrefetch = { playlistEngine.cancelAudioPrefetch() },
        saveNavigationSettings = settingsStore::saveNavigationSettings,
    )

    val playbackController = DesktopPlaybackController(
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

    val sleepTimerController = SleepTimerController(
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

    NaviampSleepTimerExpiryEffect(
        sleepTimer = sleepTimer,
        snapshot = sleepTimerController.snapshot(),
        onTick = sleepTimerController::tick,
        onExpired = sleepTimerController::expire,
    )

    val nowPlayingController = DesktopNowPlayingController(
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
        nowPlayingCoverArtUrl = { nowPlayingCoverArtUrl },
        nowPlayingLyrics = { nowPlayingLyrics },
        setNowPlayingWaveform = { waveform -> nowPlayingWaveform = waveform },
        setNowPlayingWaveformStatus = { status -> nowPlayingWaveformStatus = status },
        setNowPlayingAudioTags = { tags -> nowPlayingAudioTags = tags },
        setNowPlayingLyrics = { lyrics -> nowPlayingLyrics = lyrics },
        setNowPlayingLyricsStatus = { status -> nowPlayingLyricsStatus = status },
        setRelatedTracks = { tracks -> relatedTracks = tracks },
    )

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

    var playlistCallbacksRef: PlaylistCallbacks? = null
    val radioController = DesktopRadioController(
        scope = coroutineScope,
        libraryIndexRepository = storage,
        providerResponseService = ProviderResponseService(storage),
        playlistEngine = playlistEngine,
        provider = { connectedProvider },
        sourceId = { connectedSourceId },
        streamQuality = { playbackSettings.streamQuality(playbackEngine) },
        replayGainMode = { playbackSettings.replayGainMode },
        repeatMode = { repeatMode },
        playlistCallbacks = { playlistCallbacksRef ?: error("Playlist callbacks are not ready.") },
        rememberRadioStream = ::rememberRadioStream,
        clearShuffleSnapshot = playbackController::clearShuffleSnapshot,
        resetNowPlayingSidecars = {
            nowPlayingWaveform = null
            nowPlayingWaveformStatus = "Waiting"
            nowPlayingWaveformReloadToken += 1
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

    val internetRadioController = DesktopInternetRadioController(
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
        setNowPlayingWaveform = { waveform -> nowPlayingWaveform = waveform },
        setNowPlayingWaveformStatus = { status -> nowPlayingWaveformStatus = status },
        setNowPlayingAudioTags = { tags -> nowPlayingAudioTags = tags },
        setNowPlayingLyrics = nowPlayingController::setNowPlayingLyricsWithSavedOffset,
        setNowPlayingLyricsStatus = { status -> nowPlayingLyricsStatus = status },
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

    val libraryController = DesktopLibraryController(
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

    var loadHomeContentAction: (NavidromeProvider) -> Unit = {}
    var refreshPlaylistsAction: () -> Unit = {}

    val connectionLifecycleController = DesktopConnectionLifecycleController(
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
            nowPlayingLyricsStatus = state.nowPlayingLyricsStatus
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
        playlistCallbacks = { playlistCallbacksRef ?: error("Playlist callbacks are not ready.") },
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
        setNowPlayingWaveform = { waveform -> nowPlayingWaveform = waveform },
        setNowPlayingWaveformStatus = { status -> nowPlayingWaveformStatus = status },
        setNowPlayingAudioTags = { tags -> nowPlayingAudioTags = tags },
        setNowPlayingLyrics = nowPlayingController::setNowPlayingLyricsWithSavedOffset,
        setNowPlayingLyricsStatus = { status -> nowPlayingLyricsStatus = status },
        setPlaybackState = { state -> playbackState = state },
        setPlaybackProgress = { progress -> playbackProgress = progress },
        setPlaybackQueue = { queue -> playbackQueue = queue },
        refreshLibrarySnapshot = libraryController::refreshLibrarySnapshot,
        loadHomeContent = { provider -> loadHomeContentAction(provider) },
        refreshPlaylists = { refreshPlaylistsAction() },
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

    val playlistCallbacks = desktopPlaylistCallbacks(
        provider = { connectedProvider },
        appRoute = { appRoute },
        setAppRoute = { route -> appRoute = route },
        openPlayerOnTrackStart = { openPlayerOnTrackStart },
        nowPlayingTrack = { nowPlayingTrack },
        setNowPlayingTrack = { track -> nowPlayingTrack = track },
        setNowPlayingCoverArtUrl = { url -> nowPlayingCoverArtUrl = url },
        setNowPlayingWaveform = { waveform -> nowPlayingWaveform = waveform },
        setNowPlayingWaveformStatus = { status -> nowPlayingWaveformStatus = status },
        setNowPlayingAudioTags = { tags -> nowPlayingAudioTags = tags },
        setNowPlayingLyrics = nowPlayingController::setNowPlayingLyricsWithSavedOffset,
        setNowPlayingLyricsStatus = { status -> nowPlayingLyricsStatus = status },
        setNowPlayingInternetRadioStation = { station -> nowPlayingInternetRadioStation = station },
        setNowPlayingStreamMetadata = { metadata -> nowPlayingStreamMetadata = metadata },
        incrementPlayReportSessionId = { playReportSessionId++ },
        clearSubmittedPlayReportSessionId = { submittedPlayReportSessionId = null },
        incrementNowPlayingWaveformReloadToken = { nowPlayingWaveformReloadToken++ },
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
    playlistCallbacksRef = playlistCallbacks

    val artistMixBuilderService = rememberDesktopArtistMixBuilderService(
        storage = storage,
        sourceId = { connectedSourceId },
        provider = { connectedProvider },
        homeContent = { homeContent },
        popularTracksService = popularTracksService,
        similarArtistsService = similarArtistsService,
    )
    val albumMixBuilderService = rememberDesktopAlbumMixBuilderService(
        storage = storage,
        sourceId = { connectedSourceId },
        provider = { connectedProvider },
        homeContent = { homeContent },
        similarArtistsService = similarArtistsService,
    )
    val genreMixBuilderService = rememberDesktopGenreMixBuilderService(
        provider = { connectedProvider },
        homeContent = { homeContent },
    )

    val mixBuilderController = DesktopMixBuilderController(
        scope = coroutineScope,
        artistMixBuilderService = { artistMixBuilderService },
        albumMixBuilderService = { albumMixBuilderService },
        genreMixBuilderService = { genreMixBuilderService },
    )

    val searchController = DesktopSearchController(
        settingsStore = settingsStore,
        providerResponseCacheRepository = storage,
        provider = { connectedProvider },
        initialQuery = savedSearch.query,
    )

    val mediaActionsController = DesktopMediaActionsController(
        scope = coroutineScope,
        trackMetadataRepository = storage,
        playbackEngine = playbackEngine,
        playlistEngine = playlistEngine,
        provider = { connectedProvider },
        playbackSettings = { playbackSettings },
        playlistCallbacks = { playlistCallbacks },
        albumTracks = { selectedAlbumDetails?.tracks.orEmpty() },
        searchTracks = { searchController.results.tracks },
        relatedTracks = { relatedTracks },
        nowPlayingTrack = { nowPlayingTrack },
        setNowPlayingTrack = { track -> nowPlayingTrack = track },
        searchResults = { searchController.results },
        setSearchResults = searchController::updateResults,
        homeContent = { homeContent },
        setHomeContent = { content -> homeContent = content },
        selectedAlbumDetails = { selectedAlbumDetails },
        setSelectedAlbumDetails = { details -> selectedAlbumDetails = details },
        selectedArtistDetails = { selectedArtistDetails },
        setSelectedArtistDetails = { details -> selectedArtistDetails = details },
        setArtistMixSelectedArtists = mixBuilderController::updateSelectedArtist,
        setArtistMixSuggestions = mixBuilderController::updateSuggestedArtist,
        setAlbumMixSelectedAlbums = mixBuilderController::updateSelectedAlbum,
        setAlbumMixSuggestions = mixBuilderController::updateSuggestedAlbum,
        stopRadioContinuation = radioController::stopContinuation,
        clearShuffleSnapshot = playbackController::clearShuffleSnapshot,
        setOpenPlayerOnTrackStart = { shouldOpen -> openPlayerOnTrackStart = shouldOpen },
        setConnectionStatus = { status -> connectionStatus = status },
    )

    val downloadsController = DesktopDownloadsController(
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

    val settingsMaintenanceController = PlaybackSettingsMaintenanceController(
        playbackEngine = playbackEngine,
        playbackSettings = { playbackSettings },
        setPlaybackSettings = { settings -> playbackSettings = settings },
        savePlaybackSettings = settingsStore::savePlaybackSettings,
        reloadLyricsSidecars = {
            nowPlayingLyrics = null
            nowPlayingLyricsStatus = null
            nowPlayingWaveformReloadToken += 1
        },
        downloadedTracks = {
            connectedSourceId
                ?.let { storage.downloadedTracks(it) }
                .orEmpty()
                .map { it.track }
        },
        redownloadTracks = downloadsController::redownloadTracks,
    )

    val playlistsController = DesktopPlaylistsController(
        scope = coroutineScope,
        settingsStore = settingsStore,
        playbackEngine = playbackEngine,
        playlistEngine = playlistEngine,
        providerResponseService = ProviderResponseService(storage),
        provider = { connectedProvider },
        playbackSettings = { playbackSettings },
        playlistCallbacks = { playlistCallbacks },
        playlists = { playlists },
        setPlaylists = { value -> playlists = value },
        recentPlaylistIds = { recentPlaylistIds },
        setRecentPlaylistIds = { ids -> recentPlaylistIds = ids },
        homeContent = { homeContent },
        setHomeContent = { content -> homeContent = content },
        playlistTracksById = { playlistTracksById },
        setPlaylistTracksById = { tracksById -> playlistTracksById = tracksById },
        selectedPlaylist = { selectedPlaylist },
        setSelectedPlaylist = { playlist -> selectedPlaylist = playlist },
        selectedPlaylistTracks = { selectedPlaylistTracks },
        setSelectedPlaylistTracks = { tracks -> selectedPlaylistTracks = tracks },
        pendingPlaybackAction = { pendingPlaybackAction },
        setPendingPlaybackAction = { action -> pendingPlaybackAction = action },
        setSelectedPlaylistStatus = { status -> selectedPlaylistStatus = status },
        setPlaylistStatus = { status -> playlistStatus = status },
        setAddToPlaylistTarget = { target -> addToPlaylistTarget = target },
        setAddToPlaylistStatus = { status -> addToPlaylistStatus = status },
        setPlaylistPendingRename = { playlist -> playlistPendingRename = playlist },
        setPlaylistPendingDelete = { playlist -> playlistPendingDelete = playlist },
        setConnectionStatus = { status -> connectionStatus = status },
        setAppRoute = { route -> appRoute = route },
        stopRadioContinuation = radioController::stopContinuation,
        clearShuffleSnapshot = playbackController::clearShuffleSnapshot,
        setOpenPlayerOnTrackStart = { shouldOpen -> openPlayerOnTrackStart = shouldOpen },
    )

    val smartPlaylistsController = DesktopSmartPlaylistsController(
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
        setPlaylists = { value -> playlists = value },
        recentPlaylistIds = { recentPlaylistIds },
        homeContent = { homeContent },
        setHomeContent = { content -> homeContent = content },
        playlistTracksById = { playlistTracksById },
        setPlaylistTracksById = { tracksById -> playlistTracksById = tracksById },
        selectedPlaylist = { selectedPlaylist },
        setSelectedPlaylist = { playlist -> selectedPlaylist = playlist },
        setSelectedPlaylistTracks = { tracks -> selectedPlaylistTracks = tracks },
        setSelectedPlaylistStatus = { status -> selectedPlaylistStatus = status },
        setPlaylistStatus = { status -> playlistStatus = status },
        setConnectionStatus = { status -> connectionStatus = status },
    )

    val artistController = DesktopArtistController(
        scope = coroutineScope,
        libraryIndexRepository = storage,
        providerResponseCacheRepository = storage,
        provider = { connectedProvider },
        sourceId = { connectedSourceId },
        currentRoute = { appRoute },
        lastContentRoute = { lastContentRoute },
        setRoute = { route -> appRoute = route },
        selectedArtist = { selectedArtist },
        setSelectedArtist = { artist -> selectedArtist = artist },
        setSelectedArtistDetails = { details -> selectedArtistDetails = details },
        setSelectedArtistStatus = { status -> selectedArtistStatus = status },
        setSelectedArtistPopularTracks = { tracks -> selectedArtistPopularTracks = tracks },
        setSelectedArtistPopularTracksStatus = { status -> selectedArtistPopularTracksStatus = status },
        selectedArtistSimilarArtists = { selectedArtistSimilarArtists },
        selectedArtistSimilarArtistsStatus = { selectedArtistSimilarArtistsStatus },
        setSelectedArtistSimilarArtists = { artists -> selectedArtistSimilarArtists = artists },
        setSelectedArtistSimilarArtistsStatus = { status -> selectedArtistSimilarArtistsStatus = status },
        artistDetailBackRoute = { artistDetailBackRoute },
        setArtistDetailBackRoute = { route -> artistDetailBackRoute = route },
        artistDetailBackStack = { artistDetailBackStack },
        setArtistDetailBackStack = { stack -> artistDetailBackStack = stack },
        popularTracksService = popularTracksService,
        similarArtistsService = similarArtistsService,
    )

    val albumController = DesktopAlbumController(
        scope = coroutineScope,
        libraryIndexRepository = storage,
        providerResponseCacheRepository = storage,
        provider = { connectedProvider },
        sourceId = { connectedSourceId },
        currentRoute = { appRoute },
        lastContentRoute = { lastContentRoute },
        albumDetailBackRoute = { albumDetailBackRoute },
        setAlbumDetailBackRoute = { route -> albumDetailBackRoute = route },
        setRoute = { route -> appRoute = route },
        setSelectedAlbum = { album -> selectedAlbum = album },
        setSelectedAlbumDetails = { details -> selectedAlbumDetails = details },
        setSelectedAlbumStatus = { status -> selectedAlbumStatus = status },
    )

    val homeController = DesktopHomeController(
        scope = coroutineScope,
        providerResponseCacheRepository = storage,
        homeLibraryRepository = storage.asHomeLibraryRepository(),
        sourceId = { connectedSourceId },
        recentRadioStreams = { recentRadioStreams },
        recentInternetRadioStations = { internetRadioController.recentStations },
        setHomeContent = { content -> homeContent = content },
        setHomeStatus = { status -> homeStatus = status },
    )

    val appActions = DesktopAppActions(
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
        playlists = { playlists },
        internetRadioStations = { internetRadioController.stations },
        selectedAlbum = { selectedAlbum },
        selectedAlbumDetails = { selectedAlbumDetails },
        selectedArtistPopularTracks = { selectedArtistPopularTracks },
        selectedPlaylist = { selectedPlaylist },
        selectedPlaylistTracks = { selectedPlaylistTracks },
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
        nowPlayingWaveformReloadToken = nowPlayingWaveformReloadToken,
        cacheSettings = cacheSettings,
        playbackSettings = playbackSettings,
        nowPlayingLyricsVisible = nowPlayingLyricsVisible,
        appRoute = appRoute,
        selectedPlaylist = selectedPlaylist,
        homeContent = homeContent,
        showStatsForNerds = showStatsForNerds,
        statsForNerdsRefreshTick = statsForNerdsRefreshTick,
        incrementStatsForNerdsRefreshTick = { statsForNerdsRefreshTick++ },
        downloadRefreshToken = downloadsController.refreshToken,
        mediaSourcesRevision = mediaSourcesRevision,
        loadStorageStats = { storage.stats() },
        setCacheStats = { stats -> cacheStats = stats },
    )

    loadHomeContentAction = homeController::loadHomeContent
    refreshPlaylistsAction = playlistsController::refreshPlaylists

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
        nowPlayingWaveform = nowPlayingWaveform,
        nowPlayingWaveformStatus = nowPlayingWaveformStatus,
        nowPlayingInternetRadioStation = nowPlayingInternetRadioStation,
        nowPlayingStreamMetadata = nowPlayingStreamMetadata,
        cacheStats = cacheStats,
    )

    DesktopAppSurface(
            colorScheme = colorScheme,
            appColors = appColors,
            statsForNerdsInfo = statsForNerdsInfo,
            backgroundStart = backgroundStart,
            backgroundMid = backgroundMid,
            backgroundEnd = backgroundEnd,
            targetBackgroundColors = targetBackgroundColors,
            onCloseStatsForNerds = { showStatsForNerds = false },
    ) {
            Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (appRoute == DesktopAppRoute.Player && nowPlayingTrack != null) {
                        DesktopNowPlayingPanel(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            appColors = appColors,
                            playbackEngineName = playbackEngine.name,
                            supportsPause = playbackEngine.supportsPause,
                            supportsSoftwareVolume = playbackEngine.supportsSoftwareVolume,
                            supportsTrackFavorites = connectedProvider?.capabilities?.supportsTrackFavorites == true,
                            supportsTrackRatings = connectedProvider?.capabilities?.supportsTrackRatings == true,
                            nowPlayingTrack = nowPlayingTrack,
                            nowPlayingWaveform = nowPlayingWaveform,
                            visualizerFrame = nowPlayingVisualizerFrame,
                            selectedVisualizer = selectedVisualizer,
                            visualizerColors = targetBackgroundColors,
                            nowPlayingAudioTags = nowPlayingAudioTags,
                            nowPlayingLyrics = nowPlayingLyrics,
                            nowPlayingLyricsStatus = nowPlayingLyricsStatus,
                            nowPlayingStreamMetadata = nowPlayingStreamMetadata,
                            lyricsVisible = nowPlayingLyricsVisible,
                            visualizerAvailable = (playbackEngine as? VisualizerPlaybackEngine)?.supportsVisualizer == true,
                            visualizerVisible = nowPlayingVisualizerVisible,
                            coverArtUrl = nowPlayingCoverArtUrl,
                            playbackQueue = playbackQueue,
                            internetRadioStations = internetRadioController.stations,
                            currentInternetRadioStationId =
                                nowPlayingInternetRadioStation?.id ?: nowPlayingTrack?.internetRadioStationId(),
                            radioTrackArtworkByKey = radioTrackArtworkByKey,
                            relatedTracks = relatedTracks,
                            coverArtUrlForTrack = { track -> track.coverArtId?.let { connectedProvider?.coverArtUrl(it) } },
                            hasPrevious = playbackController.canUsePreviousButton(),
                            hasNext = playbackController.canUseNextButton(),
                            shuffleActive = shuffledUpNextSnapshot != null,
                            repeatMode = repeatMode,
                            playbackState = playbackState,
                            playbackProgress = playbackProgress,
                            volumePercent = playbackSettings.volumePercent,
                            sleepTimer = sleepTimer.toNaviampSleepTimerUi(sleepTimerNowEpochMillis),
                            streamQuality = playbackSettings.streamQuality(playbackEngine),
                            supportsSeek = playbackEngine.supportsSeek && nowPlayingTrack?.isInternetRadioTrack() != true,
                            onPause = ::handlePlayPauseCommand,
                            onResume = ::handlePlayPauseCommand,
                            onPlayCurrent = ::handlePlayPauseCommand,
                            onSeek = playbackController::performSeek,
                            onPrevious = playbackController::handlePreviousButton,
                            onNext = playbackController::handleNextButton,
                            onToggleShuffle = playbackController::toggleShuffle,
                            onCycleRepeatMode = playbackController::cycleRepeatMode,
                            onVolumeChanged = { volumePercent ->
                                playbackSettings = playbackSettingsChange(
                                    requested = playbackSettings.copy(volumePercent = volumePercent),
                                    playbackEngine = playbackEngine,
                                    previous = playbackSettings,
                                ).settings
                                settingsStore.savePlaybackSettings(playbackSettings)
                            },
                            onToggleLyrics = { nowPlayingLyricsVisible = !nowPlayingLyricsVisible },
                            onLyricsOffsetChanged = nowPlayingController::handleLyricsOffsetChanged,
                            onToggleVisualizer = {
                                nowPlayingVisualizerRequestedVisible = !nowPlayingVisualizerRequestedVisible
                            },
                            onVisualizerSelected = { visualizer ->
                                selectedVisualizer = visualizer
                                settingsStore.saveVisualizerSettings(
                                    VisualizerSettings(selectedVisualizer = visualizer.name),
                                )
                                nowPlayingVisualizerRequestedVisible = true
                            },
                            onToggleTrackFavorite = appActions::toggleTrackFavorite,
                            onTrackRatingSelected = appActions::setTrackRating,
                            onArtistSelected = appActions::openTrackArtistDetails,
                            onAlbumSelected = appActions::openTrackAlbumDetails,
                            onTrackRadioSelected = appActions::convertCurrentTrackToRadio,
                            onDownloadTrackSelected = appActions::downloadTrack,
                            onAddTrackToPlaylist = playlistsController::openTrackAddToPlaylist,
                            onSaveQueueAsPlaylist = playlistsController::saveQueueAsPlaylist,
                            onSleepTimerSelected = sleepTimerController::select,
                            onCancelSleepTimer = sleepTimerController::cancel,
                            onInternetRadioStationSelected = internetRadioController::playStation,
                            onQueueIndexSelected = ::handleQueueIndexSelected,
                            onUpNextTrackRadioSelected = appActions::playTrackRadio,
                            onUpNextTrackDownloadSelected = appActions::downloadTrack,
                            onUpNextTrackAddToPlaylist = playlistsController::openTrackAddToPlaylist,
                            onRelatedTrackSelected = appActions::playRelatedTrack,
                            onRelatedTrackPlayNext = playlistsController::playNext,
                            onRelatedTrackAddToQueue = playlistsController::addTrackToQueue,
                            onRelatedTrackRadioSelected = appActions::playTrackRadio,
                            onRelatedTrackDownloadSelected = appActions::downloadTrack,
                            onRelatedTrackAddToPlaylist = playlistsController::openTrackAddToPlaylist,
                            onCollapseToHome = { appRoute = lastContentRoute },
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
                            selectedAlbum = selectedAlbum,
                            selectedAlbumDetails = selectedAlbumDetails,
                            selectedAlbumStatus = selectedAlbumStatus,
                            albumDetailBackRoute = albumDetailBackRoute,
                            selectedArtist = selectedArtist,
                            selectedArtistDetails = selectedArtistDetails,
                            selectedArtistPopularTracks = selectedArtistPopularTracks,
                            selectedArtistSimilarArtists = selectedArtistSimilarArtists,
                            selectedArtistStatus = selectedArtistStatus,
                            selectedArtistPopularTracksStatus = selectedArtistPopularTracksStatus,
                            selectedArtistSimilarArtistsStatus = selectedArtistSimilarArtistsStatus,
                            artistDetailBackRoute = artistDetailBackRoute,
                            playlists = playlists,
                            playlistTracksById = playlistTracksById,
                            recentPlaylistIds = recentPlaylistIds,
                            playlistSortMode = playlistSortMode,
                            playlistStatus = playlistStatus,
                            onPlaylistSortModeChanged = { playlistSortMode = it },
                            onPlaylistRenameRequested = { playlistPendingRename = it },
                            onPlaylistDeleteRequested = { playlistPendingDelete = it },
                            selectedPlaylist = selectedPlaylist,
                            selectedPlaylistTracks = selectedPlaylistTracks,
                            selectedPlaylistStatus = selectedPlaylistStatus,
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
                            addToPlaylistTarget = addToPlaylistTarget,
                            playlists = playlists,
                            addToPlaylistStatus = addToPlaylistStatus,
                            playlistPendingRename = playlistPendingRename,
                            playlistPendingDelete = playlistPendingDelete,
                            onDismissAddToPlaylist = {
                                addToPlaylistTarget = null
                                addToPlaylistStatus = null
                            },
                            onAddToExistingPlaylist = { target, playlist ->
                                playlistsController.addTargetToPlaylist(target, playlist = playlist)
                            },
                            onCreateAndAddToPlaylist = { target, name ->
                                playlistsController.addTargetToPlaylist(target, playlist = null, newPlaylistName = name)
                            },
                            onDismissRenamePlaylist = { playlistPendingRename = null },
                            onRenamePlaylist = appActions::renamePlaylist,
                            onDismissDeletePlaylist = { playlistPendingDelete = null },
                            onDeletePlaylist = appActions::deletePlaylist,
                        )
                        if (nowPlayingTrack != null) {
                            DesktopMiniPlayerPanel(
                                appColors = appColors,
                                nowPlayingTrack = nowPlayingTrack,
                                coverArtUrl = nowPlayingCoverArtUrl,
                                hasPrevious = playbackController.canUsePreviousButton(),
                                hasNext = playbackQueue.hasNext(),
                                playbackState = playbackState,
                                onPause = ::handlePlayPauseCommand,
                                onResume = ::handlePlayPauseCommand,
                                onPlayCurrent = ::handlePlayPauseCommand,
                                onPrevious = playbackController::handlePreviousButton,
                                onNext = playbackController::handleNextButton,
                                onOpenPlayer = {
                                    appRoute = DesktopAppRoute.Player
                                },
                            )
                        }
                        DesktopBottomNavigationBar(
                            appColors = appColors,
                            selectedRoute = when (appRoute) {
                                DesktopAppRoute.AlbumDetail -> if (albumDetailBackRoute == DesktopAppRoute.ArtistDetail) {
                                    artistDetailBackRoute
                                } else {
                                    albumDetailBackRoute
                                }
                                DesktopAppRoute.ArtistDetail -> artistDetailBackRoute
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
