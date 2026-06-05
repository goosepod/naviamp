package app.naviamp.desktop

import app.naviamp.domain.cache.StorageCacheStats

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
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
import app.naviamp.domain.cache.DownloadService
import app.naviamp.domain.cache.ImageCacheRepository
import app.naviamp.domain.cache.LibrarySnapshot
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.cache.shouldRefreshDownloadsAfter
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackVisualizerFrame
import app.naviamp.domain.playback.VisualizerPlaybackEngine
import app.naviamp.desktop.playback.PlaylistCallbacks
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.label
import app.naviamp.domain.playback.mergeWith
import app.naviamp.domain.playback.planPlaybackTrackStartEffects
import app.naviamp.domain.playback.planPlaybackTrackStarted
import app.naviamp.domain.playback.shouldClearPendingSeek
import app.naviamp.domain.playback.shouldIgnoreProgressForPendingSeek
import app.naviamp.domain.playback.shouldUpdatePlaybackProgressUi
import app.naviamp.domain.home.HomeContent
import app.naviamp.domain.provider.PendingPlaybackAction
import app.naviamp.domain.popular.SimilarArtistMatch
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.radio.RadioRequest
import app.naviamp.domain.radio.RecentRadioAction
import app.naviamp.domain.radio.SeededRadioRequest
import app.naviamp.domain.radio.albumSeededRadioRequest
import app.naviamp.domain.radio.artistSeededRadioRequest
import app.naviamp.domain.radio.decadeRadioRequest
import app.naviamp.domain.radio.genreRadioRequest
import app.naviamp.domain.radio.libraryRadioRequest
import app.naviamp.domain.radio.popularTracksRadioRequest
import app.naviamp.domain.radio.randomAlbumSeededRadioRequest
import app.naviamp.domain.radio.recentRadioAction
import app.naviamp.domain.radio.recentRadioStreamsWith
import app.naviamp.domain.radio.trackRadioRequest
import app.naviamp.domain.source.SavedMediaSource
import app.naviamp.domain.smartplaylist.SmartPlaylistDefinition
import app.naviamp.domain.waveform.AudioWaveform
import app.naviamp.desktop.settings.PlaybackSettings
import app.naviamp.desktop.settings.PlaybackSessionSettings
import app.naviamp.desktop.settings.RecentRadioStream
import app.naviamp.desktop.settings.UpNextSelectionBehavior
import app.naviamp.desktop.settings.VisualizerSettings
import app.naviamp.domain.provider.AlbumListType
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.provider.addToPlaylistMutationUpdate
import app.naviamp.domain.provider.normalizedPlaylistName
import app.naviamp.domain.provider.playlistDeleteErrorMessage
import app.naviamp.domain.provider.playlistDeleteLoadingStatus
import app.naviamp.domain.provider.playlistDeletedStatus
import app.naviamp.domain.provider.playlistRenameErrorMessage
import app.naviamp.domain.provider.playlistRenameLoadingStatus
import app.naviamp.domain.provider.playlistRenamedStatus
import app.naviamp.domain.provider.homePlaylists
import app.naviamp.domain.provider.queueAppendPlan
import app.naviamp.domain.provider.recentPlaylistIdsAfterDelete
import app.naviamp.domain.provider.renamedSelectedPlaylist
import app.naviamp.domain.provider.refreshPlaylistDetails
import app.naviamp.domain.provider.saveSmartPlaylistAndRefresh
import app.naviamp.domain.provider.selectedPlaylistAfterDelete
import app.naviamp.domain.provider.smartPlaylistLoadErrorMessage
import app.naviamp.domain.provider.smartPlaylistLoadingRulesStatus
import app.naviamp.domain.provider.smartPlaylistSaveErrorMessage
import app.naviamp.domain.provider.smartPlaylistSavedStatus
import app.naviamp.domain.provider.smartPlaylistSavingStatus
import app.naviamp.domain.provider.smartPlaylistUpdateErrorMessage
import app.naviamp.domain.provider.smartPlaylistUpdatedStatus
import app.naviamp.domain.provider.smartPlaylistUpdatingStatus
import app.naviamp.domain.provider.updateSmartPlaylistAndRefresh
import app.naviamp.domain.settings.effectiveForEngine
import app.naviamp.domain.settings.playbackSettingsChange
import app.naviamp.domain.settings.restoredPlaybackQueue
import app.naviamp.domain.settings.restoredTrackSession
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.provider.navidrome.toNavidromeConnection
import app.naviamp.provider.navidrome.withNativeTokenFromPassword
import app.naviamp.ui.NaviampPlayerColors
import app.naviamp.ui.NaviampVisualizer
import app.naviamp.ui.rememberPlatformCoverArtPlayerColors
import app.naviamp.ui.toDownloadedTrackUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
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
    var serverUrl by remember { mutableStateOf(savedConnection?.baseUrl.orEmpty()) }
    var connectionName by remember { mutableStateOf(savedConnection?.displayName.orEmpty()) }
    var username by remember { mutableStateOf(savedConnection?.username.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var insecureSkipTlsVerification by remember {
        mutableStateOf(savedConnection?.tlsSettings?.insecureSkipTlsVerification ?: false)
    }
    var customCertificatePath by remember {
        mutableStateOf(savedConnection?.tlsSettings?.customCertificatePath.orEmpty())
    }
    var clientCertificateKeyStorePath by remember {
        mutableStateOf(savedConnection?.tlsSettings?.clientCertificateKeyStorePath.orEmpty())
    }
    var clientCertificateKeyStorePassword by remember {
        mutableStateOf(savedConnection?.tlsSettings?.clientCertificateKeyStorePassword.orEmpty())
    }
    var savedConnectionForLogin by remember { mutableStateOf(savedConnection) }
    var isConnectionFormOpen by remember { mutableStateOf(false) }
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
    var internetRadioStations by remember { mutableStateOf<List<InternetRadioStation>>(emptyList()) }
    var internetRadioStatus by remember { mutableStateOf<String?>(null) }
    var internetRadioStationPendingEdit by remember { mutableStateOf<InternetRadioStation?>(null) }
    var internetRadioStationPendingDelete by remember { mutableStateOf<InternetRadioStation?>(null) }
    var isNewInternetRadioStationDialogOpen by remember { mutableStateOf(false) }
    var recentInternetRadioStations by remember {
        mutableStateOf(savedRecentInternetRadioStations.map { it.toStation() })
    }
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
    var searchQuery by remember { mutableStateOf(savedSearch.query) }
    var searchResults by remember { mutableStateOf(MediaSearchResults()) }
    var searchStatus by remember { mutableStateOf<String?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var downloadStatus by remember { mutableStateOf<String?>(null) }
    var downloadRefreshToken by remember { mutableStateOf(0) }
    var libraryQuery by remember { mutableStateOf("") }
    var librarySnapshot by remember { mutableStateOf(LibrarySnapshot()) }
    var libraryTab by remember { mutableStateOf(DesktopLibraryTab.Artists) }
    var libraryLimit by remember { mutableStateOf(LibraryPageSize) }
    var libraryStatus by remember { mutableStateOf<String?>(null) }
    var isLibrarySyncing by remember { mutableStateOf(false) }
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
    var relatedTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var playbackState by remember { mutableStateOf<PlaybackState>(PlaybackState.Idle) }
    val nowPlayingVisualizerVisible = nowPlayingVisualizerRequestedVisible &&
        (playbackState == PlaybackState.Playing || playbackState == PlaybackState.Loading)
    var playbackProgress by remember { mutableStateOf(PlaybackProgress.Unknown) }
    var playbackQueue by remember {
        mutableStateOf(savedPlaybackSession?.restoredPlaybackQueue() ?: PlaybackQueue())
    }
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

    fun savePlaybackSession(
        queue: PlaybackQueue,
        positionSeconds: Double? = playbackProgress.positionSeconds,
    ) {
        playbackController.savePlaybackSession(queue, positionSeconds)
    }

    fun clearShuffleSnapshot() {
        playbackController.clearShuffleSnapshot()
    }

    fun toggleShuffle() {
        playbackController.toggleShuffle()
    }

    fun cycleRepeatMode() {
        playbackController.cycleRepeatMode()
    }

    fun maybeSavePlaybackPosition(progress: PlaybackProgress) {
        playbackController.maybeSavePlaybackPosition(progress)
    }

    fun performSeek(positionSeconds: Double) {
        playbackController.performSeek(positionSeconds)
    }

    fun canUsePreviousButton(): Boolean =
        playbackController.canUsePreviousButton()

    fun canUseNextButton(): Boolean =
        playbackController.canUseNextButton()

    fun handlePreviousButton() {
        playbackController.handlePreviousButton()
    }

    fun reportNowPlaying(track: Track) {
        playbackController.reportNowPlaying(track)
    }

    fun maybeReportPlayed(progress: PlaybackProgress) {
        playbackController.maybeReportPlayed(progress)
    }

    fun rememberRadioStream(stream: RecentRadioStream) {
        recentRadioStreams = recentRadioStreamsWith(recentRadioStreams, stream)
        settingsStore.saveRecentRadioStreams(recentRadioStreams)
        homeContent = homeContent.copy(recentRadioStreams = recentRadioStreams)
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
        clearShuffleSnapshot = ::clearShuffleSnapshot,
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
        providerResponseService = ProviderResponseService(storage),
        homeContent = { homeContent },
        setHomeContent = { content -> homeContent = content },
        recentStations = { recentInternetRadioStations },
        setRecentStations = { stations -> recentInternetRadioStations = stations },
        setStations = { stations -> internetRadioStations = stations },
        setStatus = { status -> internetRadioStatus = status },
        setNewStationDialogOpen = { isOpen -> isNewInternetRadioStationDialogOpen = isOpen },
        setPendingEdit = { station -> internetRadioStationPendingEdit = station },
        setPendingDelete = { station -> internetRadioStationPendingDelete = station },
        stopRadioContinuation = { radioController.stopContinuation() },
        clearShuffleSnapshot = ::clearShuffleSnapshot,
        setNowPlayingTrack = { track -> nowPlayingTrack = track },
        nowPlayingTrack = { nowPlayingTrack },
        setNowPlayingCoverArtUrl = { url -> nowPlayingCoverArtUrl = url },
        setNowPlayingWaveform = { waveform -> nowPlayingWaveform = waveform },
        setNowPlayingWaveformStatus = { status -> nowPlayingWaveformStatus = status },
        setNowPlayingAudioTags = { tags -> nowPlayingAudioTags = tags },
        setNowPlayingLyrics = { lyrics -> nowPlayingLyrics = lyrics },
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

    fun stopRadioContinuation() {
        radioController.stopContinuation()
    }

    var refreshLibrarySnapshotAction: () -> Unit = {}
    var loadHomeContentAction: (NavidromeProvider) -> Unit = {}
    var refreshPlaylistsAction: () -> Unit = {}
    var refreshInternetRadioStationsAction: () -> Unit = {}
    var startLibrarySyncAction: (Boolean) -> Unit = {}
    var checkLibraryFreshnessAction: () -> Unit = {}

    val connectionLifecycleController = DesktopConnectionLifecycleController(
        scope = coroutineScope,
        cacheMaintenanceRepository = storage,
        mediaSourceRepository = storage,
        providerMediaSourceRepository = storage,
        settingsStore = settingsStore,
        playbackSessionRepository = settingsStore,
        playbackEngine = playbackEngine,
        playlistEngine = playlistEngine,
        stopRadioContinuation = ::stopRadioContinuation,
        clearShuffleSnapshot = ::clearShuffleSnapshot,
        applyClearedConnectionState = { state ->
            connectedProvider = state.connectedProvider
            connectedSourceId = state.connectedSourceId
            librarySnapshot = state.librarySnapshot
            libraryStatus = state.libraryStatus
            homeContent = state.homeContent
            homeStatus = state.homeStatus
            nowPlayingTrack = state.nowPlayingTrack
            nowPlayingCoverArtUrl = state.nowPlayingCoverArtUrl
            nowPlayingLyricsStatus = state.nowPlayingLyricsStatus
            playbackState = state.playbackState
            playbackProgress = state.playbackProgress
            playbackQueue = state.playbackQueue
        },
        serverUrl = { serverUrl },
        username = { username },
        password = { password },
        clearPassword = { password = "" },
        connectionName = { connectionName },
        insecureSkipTlsVerification = { insecureSkipTlsVerification },
        customCertificatePath = { customCertificatePath },
        clientCertificateKeyStorePath = { clientCertificateKeyStorePath },
        clientCertificateKeyStorePassword = { clientCertificateKeyStorePassword },
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
        setNowPlayingLyrics = { lyrics -> nowPlayingLyrics = lyrics },
        setNowPlayingLyricsStatus = { status -> nowPlayingLyricsStatus = status },
        setPlaybackState = { state -> playbackState = state },
        setPlaybackProgress = { progress -> playbackProgress = progress },
        setPlaybackQueue = { queue -> playbackQueue = queue },
        refreshLibrarySnapshot = { refreshLibrarySnapshotAction() },
        loadHomeContent = { provider -> loadHomeContentAction(provider) },
        refreshPlaylists = { refreshPlaylistsAction() },
        refreshInternetRadioStations = { refreshInternetRadioStationsAction() },
        startLibrarySync = { force -> startLibrarySyncAction(force) },
        checkLibraryFreshness = { checkLibraryFreshnessAction() },
        connectedSourceId = { connectedSourceId },
        savedConnectionForLogin = { savedConnectionForLogin },
        setSavedConnectionForLogin = { connection -> savedConnectionForLogin = connection },
        incrementMediaSourcesRevision = { mediaSourcesRevision++ },
        setConnectionFormOpen = { isOpen -> isConnectionFormOpen = isOpen },
        setConnectionStatus = { status -> connectionStatus = status },
        setAppRoute = { route -> appRoute = route },
        appRoute = { appRoute },
    )

    fun refillRadioIfNeeded(queue: PlaybackQueue) {
        radioController.refillIfNeeded(queue)
    }

    val playlistCallbacks = PlaylistCallbacks(
        onTrackStarted = { track, coverArtUrl ->
            val trackStartedPlan = planPlaybackTrackStarted(
                previousTrack = nowPlayingTrack,
                track = track,
                openNowPlaying = openPlayerOnTrackStart,
                nowPlayingOpen = appRoute == DesktopAppRoute.Player,
                lyricsVisible = false,
                supportsTrackFavorites = connectedProvider?.capabilities?.supportsTrackFavorites == true,
            )
            val effectsPlan = planPlaybackTrackStartEffects(
                track = track,
                presentation = trackStartedPlan,
                keepRadioQueueActive = true,
            )
            if (effectsPlan.presentation.clearShuffleSnapshot) {
                clearShuffleSnapshot()
            }
            if (effectsPlan.presentation.clearInternetRadioNowPlaying) nowPlayingInternetRadioStation = null
            if (effectsPlan.presentation.resetStreamMetadata) nowPlayingStreamMetadata = PlaybackStreamMetadata()
            nowPlayingTrack = track
            nowPlayingCoverArtUrl = coverArtUrl
            playReportSessionId += 1
            submittedPlayReportSessionId = null
            if (effectsPlan.presentation.shouldReportNowPlaying) reportNowPlaying(track)
            if (effectsPlan.presentation.resetSidecars) {
                nowPlayingWaveform = null
                nowPlayingWaveformStatus = "Waiting"
                nowPlayingAudioTags = null
                nowPlayingLyrics = null
                nowPlayingLyricsStatus = null
                nowPlayingWaveformReloadToken += 1
            }
            if (effectsPlan.presentation.resetProgress) playbackProgress = PlaybackProgress.Unknown
            if (effectsPlan.refillRadioQueue) refillRadioIfNeeded(playlistEngine.queue)
            if (effectsPlan.presentation.shouldOpenNowPlaying) {
                appRoute = DesktopAppRoute.Player
            }
        },
        onQueueChanged = { queue ->
            playbackQueue = queue
            savePlaybackSession(queue, playbackProgress.positionSeconds)
        },
        onPlaybackStateChanged = { state ->
            playbackState = state
        },
        onPlaybackProgressChanged = progressChanged@{ progress ->
            val pendingSeek = pendingSeekPositionSeconds
            val pendingSeekIssuedAt = pendingSeekIssuedAtMillis
            val progressPosition = progress.positionSeconds
            val now = System.currentTimeMillis()
            if (shouldIgnoreProgressForPendingSeek(pendingSeek, pendingSeekIssuedAt, progressPosition, now)) {
                return@progressChanged
            }
            if (shouldClearPendingSeek(pendingSeek, pendingSeekIssuedAt, progressPosition, now)) {
                pendingSeekPositionSeconds = null
                pendingSeekIssuedAtMillis = null
            }
            val currentProgress = playbackProgress
            val mergedProgress = progress.mergeWith(currentProgress)
            maybeSavePlaybackPosition(mergedProgress)
            maybeReportPlayed(mergedProgress)
            if (
                shouldUpdatePlaybackProgressUi(
                    pendingSeekPositionSeconds = pendingSeek,
                    currentProgress = currentProgress,
                    mergedProgress = mergedProgress,
                    nowMillis = now,
                    lastUiUpdateMillis = lastPlaybackProgressUiUpdateMillis,
                    positionThresholdSeconds = PlaybackProgressUiUpdateThresholdSeconds,
                    updateIntervalMillis = PlaybackProgressUiUpdateIntervalMillis,
                )
            ) {
                playbackProgress = mergedProgress
                lastPlaybackProgressUiUpdateMillis = now
            }
        },
        onMetadataChanged = { metadata ->
            nowPlayingStreamMetadata = metadata
        },
        onCurrentTrackSidecarsReady = { track ->
            if (nowPlayingTrack?.id == track.id) {
                nowPlayingWaveformReloadToken += 1
            }
        },
    )
    playlistCallbacksRef = playlistCallbacks

    val mediaActionsController = DesktopMediaActionsController(
        scope = coroutineScope,
        trackMetadataRepository = storage,
        playbackEngine = playbackEngine,
        playlistEngine = playlistEngine,
        provider = { connectedProvider },
        playbackSettings = { playbackSettings },
        playlistCallbacks = { playlistCallbacks },
        albumTracks = { selectedAlbumDetails?.tracks.orEmpty() },
        searchTracks = { searchResults.tracks },
        relatedTracks = { relatedTracks },
        nowPlayingTrack = { nowPlayingTrack },
        setNowPlayingTrack = { track -> nowPlayingTrack = track },
        searchResults = { searchResults },
        setSearchResults = { results -> searchResults = results },
        selectedAlbumDetails = { selectedAlbumDetails },
        setSelectedAlbumDetails = { details -> selectedAlbumDetails = details },
        stopRadioContinuation = ::stopRadioContinuation,
        clearShuffleSnapshot = ::clearShuffleSnapshot,
        setOpenPlayerOnTrackStart = { shouldOpen -> openPlayerOnTrackStart = shouldOpen },
        setConnectionStatus = { status -> connectionStatus = status },
    )

    val searchController = DesktopSearchController(
        settingsStore = settingsStore,
        providerResponseCacheRepository = storage,
        provider = { connectedProvider },
        setQuery = { query -> searchQuery = query },
        setResults = { results -> searchResults = results },
        setStatus = { status -> searchStatus = status },
        setSearching = { searching -> isSearching = searching },
    )

    val downloadsController = DesktopDownloadsController(
        scope = coroutineScope,
        downloadRepository = storage,
        downloadReplacementRepository = storage,
        providerResponseCacheRepository = storage,
        playbackEngine = playbackEngine,
        playbackSettings = { playbackSettings },
        cacheSettings = { cacheSettings },
        provider = { connectedProvider },
        sourceId = { connectedSourceId },
        stopRadioContinuation = ::stopRadioContinuation,
        clearShuffleSnapshot = ::clearShuffleSnapshot,
        setOpenPlayerOnTrackStart = { shouldOpen -> openPlayerOnTrackStart = shouldOpen },
        playlistEngine = playlistEngine,
        playlistCallbacks = { playlistCallbacks },
        setDownloadStatus = { status -> downloadStatus = status },
        incrementDownloadRefreshToken = { downloadRefreshToken += 1 },
    )

    fun applyPlaybackSettings(settings: PlaybackSettings) {
        val change = playbackSettingsChange(settings, playbackEngine, previous = playbackSettings)
        playbackSettings = change.settings
        if (change.shouldReloadLyricsSidecars) {
            nowPlayingLyrics = null
            nowPlayingLyricsStatus = null
            nowPlayingWaveformReloadToken += 1
        }
        settingsStore.savePlaybackSettings(playbackSettings)
    }

    fun applyPlaybackSettingsAndRedownload(settings: PlaybackSettings) {
        val activeProvider = connectedProvider
        val activeSourceId = connectedSourceId
        val tracksToRedownload = activeSourceId
            ?.let { storage.downloadedTracks(it) }
            .orEmpty()
            .map { it.track }
        applyPlaybackSettings(settings)
        if (tracksToRedownload.isEmpty()) return
        coroutineScope.launch {
            val downloadService = DownloadService(storage, storage)
            val quality = playbackSettings.streamQuality(playbackEngine)
            val maxDownloadBytes = cacheSettings.maxDownloadBytes
            val result = downloadService.redownloadTracksWithStatus(
                tracks = tracksToRedownload,
                sourceId = activeSourceId,
                provider = activeProvider,
                quality = quality,
                maxDownloadBytes = maxDownloadBytes,
                setStatus = { status -> downloadStatus = status },
            )
            if (shouldRefreshDownloadsAfter(result)) {
                downloadRefreshToken += 1
                cacheStats = withContext(Dispatchers.IO) { storage.stats() }
            }
        }
    }

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
        recentRadioStreams = { recentRadioStreams },
        recentInternetRadioStations = { recentInternetRadioStations },
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
        stopRadioContinuation = ::stopRadioContinuation,
        clearShuffleSnapshot = ::clearShuffleSnapshot,
        setOpenPlayerOnTrackStart = { shouldOpen -> openPlayerOnTrackStart = shouldOpen },
    )

    val smartPlaylistsController = DesktopSmartPlaylistsController(
        providerMediaSourceRepository = storage,
        providerResponseCacheRepository = storage,
        provider = { connectedProvider },
        setProvider = { provider -> connectedProvider = provider },
        password = { password },
        clearPassword = { password = "" },
        savedConnectionForLogin = { savedConnectionForLogin },
        setSavedConnectionForLogin = { connection -> savedConnectionForLogin = connection },
        setConnectedSourceId = { sourceId -> connectedSourceId = sourceId },
        incrementMediaSourcesRevision = { mediaSourcesRevision++ },
        incrementStatsForNerdsRefreshTick = { statsForNerdsRefreshTick++ },
        playlists = { playlists },
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
        recentInternetRadioStations = { recentInternetRadioStations },
        setHomeContent = { content -> homeContent = content },
        setHomeStatus = { status -> homeStatus = status },
    )

    val nowPlayingController = DesktopNowPlayingController(
        audioWaveformService = audioWaveformService,
        lyricsSidecarService = lyricsSidecarService,
        audioMetadataSidecarService = audioMetadataSidecarService,
        localLibraryIndexRepository = storage,
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
        setNowPlayingWaveform = { waveform -> nowPlayingWaveform = waveform },
        setNowPlayingWaveformStatus = { status -> nowPlayingWaveformStatus = status },
        setNowPlayingAudioTags = { tags -> nowPlayingAudioTags = tags },
        setNowPlayingLyrics = { lyrics -> nowPlayingLyrics = lyrics },
        setNowPlayingLyricsStatus = { status -> nowPlayingLyricsStatus = status },
        setRelatedTracks = { tracks -> relatedTracks = tracks },
    )

    val libraryController = DesktopLibraryController(
        scope = coroutineScope,
        libraryIndexRepository = storage,
        mediaSourceRepository = storage,
        cacheMaintenanceRepository = storage,
        libraryOffsetForLetter = storage::libraryOffsetForLetter,
        librarySync = librarySync,
        provider = { connectedProvider },
        sourceId = { connectedSourceId },
        libraryQuery = { libraryQuery },
        libraryTab = { libraryTab },
        libraryLimit = { libraryLimit },
        setLibraryLimit = { limit -> libraryLimit = limit },
        librarySnapshot = { librarySnapshot },
        setLibrarySnapshot = { snapshot -> librarySnapshot = snapshot },
        libraryStatus = { libraryStatus },
        setLibraryStatus = { status -> libraryStatus = status },
        setConnectionStatus = { status -> connectionStatus = status },
        isLibrarySyncing = { isLibrarySyncing },
        setLibrarySyncing = { syncing -> isLibrarySyncing = syncing },
        listState = libraryListState,
    )

    DesktopAppControllerEffects(
        nowPlayingController = nowPlayingController,
        playlistsController = playlistsController,
        searchController = searchController,
        libraryController = libraryController,
        libraryListState = libraryListState,
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
        searchQuery = searchQuery,
        libraryQuery = libraryQuery,
        setLibraryLimit = { limit -> libraryLimit = limit },
        showStatsForNerds = showStatsForNerds,
        statsForNerdsRefreshTick = statsForNerdsRefreshTick,
        incrementStatsForNerdsRefreshTick = { statsForNerdsRefreshTick++ },
        downloadRefreshToken = downloadRefreshToken,
        mediaSourcesRevision = mediaSourcesRevision,
        loadStorageStats = { storage.stats() },
        setCacheStats = { stats -> cacheStats = stats },
    )

    fun applyConnectionFormState(formState: DesktopConnectionFormState) {
        savedConnectionForLogin = formState.savedConnectionForLogin
        serverUrl = formState.serverUrl
        connectionName = formState.connectionName
        username = formState.username
        password = formState.password
        insecureSkipTlsVerification = formState.insecureSkipTlsVerification
        customCertificatePath = formState.customCertificatePath
        clientCertificateKeyStorePath = formState.clientCertificateKeyStorePath
        clientCertificateKeyStorePassword = formState.clientCertificateKeyStorePassword
    }

    val appActions = DesktopAppActions(
        connectionLifecycleController = connectionLifecycleController,
        albumController = albumController,
        artistController = artistController,
        mediaActionsController = mediaActionsController,
        downloadsController = downloadsController,
        radioController = radioController,
        playlistsController = playlistsController,
        libraryController = libraryController,
        selectedAlbum = { selectedAlbum },
        selectedAlbumDetails = { selectedAlbumDetails },
    )

    refreshLibrarySnapshotAction = libraryController::refreshLibrarySnapshot
    loadHomeContentAction = homeController::loadHomeContent
    refreshPlaylistsAction = playlistsController::refreshPlaylists
    refreshInternetRadioStationsAction = internetRadioController::refreshStations
    startLibrarySyncAction = libraryController::startLibrarySync
    checkLibraryFreshnessAction = libraryController::checkLibraryFreshness

    val statsMediaSource = connectedSourceId?.let { storage.mediaSource(it) } ?: storage.latestMediaSource()
    val savedMediaSources = mediaSourcesRevision.let { storage.mediaSources() }
    val streamQuality = playbackSettings.streamQuality(playbackEngine)
    val currentAudioCacheMetadata = connectedSourceId
        ?.let { sourceId ->
            nowPlayingTrack?.let { track ->
                storage.cachedAudioMetadata(sourceId, track.id, streamQuality)
            }
        }
    val statsForNerdsInfo = if (showStatsForNerds) {
        buildDesktopStatsForNerdsInfo(
            route = appRoute.name,
            serverUrl = serverUrl,
            username = username,
            connectedProvider = connectedProvider,
            mediaSource = statsMediaSource,
            connectionStatus = connectionStatus,
            isLibrarySyncing = isLibrarySyncing,
            libraryStatus = libraryStatus,
            libraryTabLabel = libraryTab.label,
            libraryQuery = libraryQuery,
            librarySnapshot = librarySnapshot,
            playbackEngine = playbackEngine,
            playlistEngine = playlistEngine,
            playbackQueue = playbackQueue,
            nowPlayingTrack = nowPlayingTrack,
            playbackState = playbackState,
            playbackProgress = playbackProgress,
            playbackSettings = playbackSettings,
            streamQuality = streamQuality,
            nowPlayingWaveform = nowPlayingWaveform,
            nowPlayingWaveformStatus = nowPlayingWaveformStatus,
            cachedAudio = currentAudioCacheMetadata,
            nowPlayingInternetRadioStation = nowPlayingInternetRadioStation,
            nowPlayingStreamMetadata = nowPlayingStreamMetadata,
            cacheStats = cacheStats,
        )
    } else {
        null
    }

    MaterialTheme(colorScheme = colorScheme) {
        statsForNerdsInfo?.let { info ->
            DesktopStatsForNerdsWindow(
                appColors = appColors,
                info = info,
                onClose = { showStatsForNerds = false },
            )
        }
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize(),
            ) {
                val density = LocalDensity.current
                val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
                val heightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
                val narrowLayout = maxWidth < 520.dp
                val gradientEnd = if (narrowLayout) {
                    Offset(widthPx * 0.85f, heightPx * 1.05f)
                } else {
                    Offset(widthPx * 1.08f, heightPx * 0.82f)
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            NaviampPlayerColors(
                                backgroundStart = backgroundStart,
                                backgroundMid = backgroundMid,
                                backgroundEnd = backgroundEnd,
                                accent = targetBackgroundColors.accent,
                            ).backgroundBrush(gradientEnd),
                        ),
                ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (appRoute == DesktopAppRoute.Player && nowPlayingTrack != null) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        ) {
                            DesktopNowPlayingPanel(
                                appColors = appColors,
                                playbackEngineName = playbackEngine.name,
                                supportsPause = playbackEngine.supportsPause,
                                supportsSoftwareVolume = playbackEngine.supportsSoftwareVolume,
                                supportsTrackFavorites = connectedProvider?.capabilities?.supportsTrackFavorites == true,
                                supportsTrackRatings = connectedProvider?.capabilities?.supportsTrackRatings == true,
                                nowPlayingTrack = nowPlayingTrack,
                                nowPlayingWaveform = nowPlayingWaveform,
                                visualizerBandsProvider = { nowPlayingVisualizerFrame?.bands.orEmpty() },
                                selectedVisualizer = selectedVisualizer,
                                visualizerColors = targetBackgroundColors,
                                nowPlayingAudioTags = nowPlayingAudioTags,
                                nowPlayingLyrics = nowPlayingLyrics,
                                nowPlayingLyricsStatus = nowPlayingLyricsStatus,
                                lyricsVisible = nowPlayingLyricsVisible,
                                visualizerAvailable = (playbackEngine as? VisualizerPlaybackEngine)?.supportsVisualizer == true,
                                visualizerVisible = nowPlayingVisualizerVisible,
                                coverArtUrl = nowPlayingCoverArtUrl,
                                backTo = playbackQueue.backTo(),
                                upNext = playbackQueue.upNext(),
                                internetRadioStations = internetRadioStations,
                                currentInternetRadioStationId =
                                    nowPlayingInternetRadioStation?.id ?: nowPlayingTrack?.internetRadioStationId(),
                                firstBackToQueueIndex = playbackQueue.currentIndex - 1,
                                firstUpNextQueueIndex = playbackQueue.currentIndex + 1,
                                upNextCoverArtUrl = { track ->
                                    track.coverArtId?.let { connectedProvider?.coverArtUrl(it) }
                                },
                                relatedTracks = relatedTracks,
                                relatedCoverArtUrl = { track ->
                                    track.coverArtId?.let { connectedProvider?.coverArtUrl(it) }
                                },
                                hasPrevious = canUsePreviousButton(),
                                hasNext = canUseNextButton(),
                                shuffleEnabled = playbackQueue.upNext().size > 1,
                                shuffleActive = shuffledUpNextSnapshot != null,
                                repeatMode = repeatMode,
                                playbackState = playbackState,
                                playbackProgress = playbackProgress,
                                volumePercent = playbackSettings.volumePercent,
                                streamQuality = playbackSettings.streamQuality(playbackEngine),
                                supportsSeek = playbackEngine.supportsSeek &&
                                    nowPlayingTrack?.isInternetRadioTrack() != true,
                                onPause = {
                                    playbackEngine.pause()
                                },
                                onResume = {
                                    playbackEngine.resume()
                                },
                                onPlayCurrent = {
                                    internetRadioController.playCurrentSelection()
                                },
                                onSeek = { positionSeconds ->
                                    performSeek(positionSeconds)
                                },
                                onPrevious = {
                                    handlePreviousButton()
                                },
                                onNext = {
                                    openPlayerOnTrackStart = false
                                    playlistEngine.next(coroutineScope)
                                },
                                onToggleShuffle = {
                                    toggleShuffle()
                                },
                                onCycleRepeatMode = {
                                    cycleRepeatMode()
                                },
                                onVolumeChanged = { volumePercent ->
                                    playbackSettings = playbackSettingsChange(
                                        requested = playbackSettings.copy(volumePercent = volumePercent),
                                        playbackEngine = playbackEngine,
                                        previous = playbackSettings,
                                    ).settings
                                    settingsStore.savePlaybackSettings(playbackSettings)
                                },
                                onToggleLyrics = {
                                    nowPlayingLyricsVisible = !nowPlayingLyricsVisible
                                },
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
                                onToggleTrackFavorite = { track ->
                                    appActions.toggleTrackFavorite(track)
                                },
                                onTrackRatingSelected = { track, rating ->
                                    appActions.setTrackRating(track, rating)
                                },
                                onArtistSelected = { track ->
                                    appActions.openTrackArtistDetails(track)
                                },
                                onAlbumSelected = { track ->
                                    appActions.openTrackAlbumDetails(track)
                                },
                                onTrackRadioSelected = { track ->
                                    appActions.convertCurrentTrackToRadio(track)
                                },
                                onDownloadTrackSelected = { track ->
                                    appActions.downloadTrack(track)
                                },
                                onAddTrackToPlaylist = { track ->
                                    playlistsController.openAddToPlaylist(AddToPlaylistTarget.TrackTarget(track))
                                },
                                onInternetRadioStationSelected = { station ->
                                    internetRadioController.playStation(station)
                                },
                                onQueueIndexSelected = { queueIndex ->
                                    openPlayerOnTrackStart = false
                                    playlistEngine.jumpTo(
                                        scope = coroutineScope,
                                        index = queueIndex,
                                        moveSelectedToCurrent =
                                            playbackSettings.upNextSelectionBehavior ==
                                                UpNextSelectionBehavior.MoveSelectedToCurrent,
                                    )
                                },
                                onUpNextTrackRadioSelected = { track ->
                                    appActions.playTrackRadio(track)
                                },
                                onUpNextTrackDownloadSelected = { track ->
                                    appActions.downloadTrack(track)
                                },
                                onUpNextTrackAddToPlaylist = { track ->
                                    playlistsController.openAddToPlaylist(AddToPlaylistTarget.TrackTarget(track))
                                },
                                onRelatedTrackSelected = { index ->
                                    appActions.playRelatedTrack(index)
                                },
                                onRelatedTrackRadioSelected = { track ->
                                    appActions.playTrackRadio(track)
                                },
                                onRelatedTrackDownloadSelected = { track ->
                                    appActions.downloadTrack(track)
                                },
                                onRelatedTrackAddToPlaylist = { track ->
                                    playlistsController.openAddToPlaylist(AddToPlaylistTarget.TrackTarget(track))
                                },
                                onCollapseToHome = {
                                    appRoute = lastContentRoute
                                },
                            )
                        }
                    } else {
                        val contentScrollState = rememberScrollState()
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .then(
                                    if (
                                        appRoute == DesktopAppRoute.Library ||
                                            appRoute == DesktopAppRoute.Settings ||
                                            appRoute == DesktopAppRoute.AlbumDetail ||
                                            appRoute == DesktopAppRoute.ArtistDetail
                                    ) {
                                        Modifier
                                    } else {
                                        Modifier.verticalScroll(contentScrollState)
                                    },
                                ),
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                when (appRoute) {
                                    DesktopAppRoute.Player -> Unit
                                DesktopAppRoute.Home -> DesktopHomePanel(
                                    appColors = appColors,
                                    connectionStatus = homeStatus ?: connectionStatus,
                                    homeContent = homeContent,
                                    coverArtUrl = { coverArtId ->
                                        coverArtId?.let { connectedProvider?.coverArtUrl(it) }
                                    },
                                    onAlbumSelected = { album ->
                                        appActions.openAlbumDetails(album)
                                    },
                                    onAlbumRadioSelected = { album ->
                                        appActions.playAlbumRadio(album)
                                    },
                                    onAlbumDownloadSelected = { album ->
                                        appActions.downloadAlbum(album)
                                    },
                                    onAlbumAddToQueue = { album ->
                                        playlistsController.addTargetToQueue(AddToPlaylistTarget.AlbumTarget(album))
                                    },
                                    onAlbumAddToPlaylist = { album ->
                                        playlistsController.openAddToPlaylist(AddToPlaylistTarget.AlbumTarget(album))
                                    },
                                    onPlaylistSelected = { playlist ->
                                        appActions.openPlaylistDetails(playlist)
                                    },
                                    onPlaylistDownloadSelected = { playlist ->
                                        appActions.downloadPlaylist(playlist)
                                    },
                                    onPlaylistAddToQueue = { playlist ->
                                        playlistsController.addTargetToQueue(AddToPlaylistTarget.PlaylistTarget(playlist))
                                    },
                                    onPlaylistAddToPlaylist = { playlist ->
                                        playlistsController.openAddToPlaylist(AddToPlaylistTarget.PlaylistTarget(playlist))
                                    },
                                    onRecentRadioSelected = { stream ->
                                        appActions.playRecentRadio(stream)
                                    },
                                    onInternetRadioStationSelected = { station ->
                                        internetRadioController.playStation(station)
                                    },
                                    onLibraryRadioSelected = {
                                        appActions.playLibraryRadio()
                                    },
                                    onRandomAlbumRadioSelected = {
                                        appActions.playRandomAlbumRadio()
                                    },
                                    onGenreRadioSelected = { genre ->
                                        appActions.playGenreRadio(genre)
                                    },
                                    onDecadeRadioSelected = { fromYear, toYear ->
                                        appActions.playDecadeRadio(fromYear, toYear)
                                    },
                                    onOpenArtistMixBuilder = {
                                        libraryTab = DesktopLibraryTab.Artists
                                        appRoute = DesktopAppRoute.Library
                                    },
                                    onOpenAlbumMixBuilder = {
                                        libraryTab = DesktopLibraryTab.Albums
                                        appRoute = DesktopAppRoute.Library
                                    },
                                )
                                DesktopAppRoute.AlbumDetail -> DesktopAlbumDetailPanel(
                                    appColors = appColors,
                                    album = selectedAlbum,
                                    albumDetails = selectedAlbumDetails,
                                    status = selectedAlbumStatus,
                                    coverArtUrl = (
                                        selectedAlbumDetails?.album?.coverArtId ?: selectedAlbum?.coverArtId
                                        )?.let { connectedProvider?.coverArtUrl(it) },
                                    popularTrackIds = selectedArtistPopularTracks.map { it.id.value }.toSet(),
                                    onBack = { appRoute = albumDetailBackRoute },
                                    onPlayAlbum = { appActions.playAlbumDetails() },
                                    onShuffleAlbum = { appActions.playAlbumDetails(shuffle = true) },
                                    onAlbumRadio = {
                                        selectedAlbumDetails?.album?.let { appActions.playAlbumRadio(it) }
                                            ?: selectedAlbum?.let { appActions.playAlbumRadio(it) }
                                    },
                                    onDownloadAlbum = {
                                        selectedAlbumDetails?.let { appActions.downloadTracks(it.album.title, it.tracks) }
                                            ?: selectedAlbum?.let { appActions.downloadAlbum(it) }
                                    },
                                    onAddAlbumToQueue = {
                                        selectedAlbumDetails?.album?.let {
                                            playlistsController.addTargetToQueue(AddToPlaylistTarget.AlbumTarget(it))
                                        } ?: selectedAlbum?.let {
                                            playlistsController.addTargetToQueue(AddToPlaylistTarget.AlbumTarget(it))
                                        }
                                    },
                                    onPlayTrack = { index -> appActions.playAlbumDetails(index = index) },
                                    onTrackRadio = { track -> appActions.playTrackRadio(track) },
                                    onDownloadTrack = { track -> appActions.downloadTrack(track) },
                                    onAddTrackToQueue = { track ->
                                        playlistsController.addTargetToQueue(AddToPlaylistTarget.TrackTarget(track))
                                    },
                                    onAddAlbumToPlaylist = {
                                        selectedAlbumDetails?.album?.let {
                                            playlistsController.openAddToPlaylist(AddToPlaylistTarget.AlbumTarget(it))
                                        } ?: selectedAlbum?.let {
                                            playlistsController.openAddToPlaylist(AddToPlaylistTarget.AlbumTarget(it))
                                        }
                                    },
                                    onAddTrackToPlaylist = { track ->
                                        playlistsController.openAddToPlaylist(AddToPlaylistTarget.TrackTarget(track))
                                    },
                                    onArtistSelected = { track ->
                                        appActions.openTrackArtistDetails(track, backRouteOverride = DesktopAppRoute.AlbumDetail)
                                    },
                                )
                                DesktopAppRoute.ArtistDetail -> DesktopArtistDetailPanel(
                                    appColors = appColors,
                                    artist = selectedArtist,
                                    artistDetails = selectedArtistDetails,
                                    popularTracks = selectedArtistPopularTracks,
                                    similarArtists = selectedArtistSimilarArtists,
                                    status = selectedArtistStatus,
                                    popularTracksStatus = selectedArtistPopularTracksStatus,
                                    similarArtistsStatus = selectedArtistSimilarArtistsStatus,
                                    coverArtUrl = { coverArtId ->
                                        coverArtId?.let { connectedProvider?.coverArtUrl(it) }
                                    },
                                    onBack = { appActions.closeArtistDetails() },
                                    onArtistRadio = { artist -> appActions.playArtistRadio(artist) },
                                    onFindSimilarArtists = { artist -> appActions.findSimilarArtists(artist) },
                                    onSimilarArtistSelected = { artist ->
                                        appActions.openArtistDetails(artist)
                                    },
                                    onSimilarArtistExternalSelected = { url -> appActions.openExternalArtistUrl(url) },
                                    onPopularTracksPlay = { tracks -> appActions.playPopularTracks(tracks) },
                                    onPopularTracksRadio = { tracks -> appActions.playPopularTracksRadio(tracks) },
                                    onPopularTracksAddToQueue = { tracks -> appActions.addPopularTracksToQueue(tracks) },
                                    onPopularTrackSelected = { track ->
                                        val index = selectedArtistPopularTracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
                                        appActions.playPopularTracks(selectedArtistPopularTracks, index)
                                    },
                                    onPopularTrackAddToQueue = { track ->
                                        playlistsController.addTargetToQueue(AddToPlaylistTarget.TrackTarget(track))
                                    },
                                    onAddArtistToQueue = { artist ->
                                        playlistsController.addTargetToQueue(AddToPlaylistTarget.ArtistTarget(artist))
                                    },
                                    onAddArtistToPlaylist = { artist ->
                                        playlistsController.openAddToPlaylist(AddToPlaylistTarget.ArtistTarget(artist))
                                    },
                                    onAlbumSelected = { album -> appActions.openAlbumDetails(album) },
                                    onAlbumRadioSelected = { album -> appActions.playAlbumRadio(album) },
                                    onAlbumDownloadSelected = { album -> appActions.downloadAlbum(album) },
                                    onAlbumAddToQueue = { album ->
                                        playlistsController.addTargetToQueue(AddToPlaylistTarget.AlbumTarget(album))
                                    },
                                    onAlbumAddToPlaylist = { album ->
                                        playlistsController.openAddToPlaylist(AddToPlaylistTarget.AlbumTarget(album))
                                    },
                                )
                                DesktopAppRoute.Playlists -> DesktopPlaylistsPanel(
                                    appColors = appColors,
                                    playlists = playlists,
                                    playlistTracks = { playlist -> playlistTracksById[playlist.id].orEmpty() },
                                    recentPlaylistIds = recentPlaylistIds,
                                    sortMode = playlistSortMode,
                                    status = playlistStatus ?: connectionStatus,
                                    coverArtUrl = { coverArtId ->
                                        coverArtId?.let { connectedProvider?.coverArtUrl(it) }
                                    },
                                    onSortModeChanged = { playlistSortMode = it },
                                    onPlaylistSelected = { playlist -> appActions.openPlaylistDetails(playlist) },
                                    onPlayPlaylist = { playlist, shuffle -> appActions.playPlaylist(playlist, shuffle) },
                                    onRenamePlaylist = { playlist -> playlistPendingRename = playlist },
                                    onDeletePlaylist = { playlist -> playlistPendingDelete = playlist },
                                    onDownloadPlaylist = { playlist -> appActions.downloadPlaylist(playlist) },
                                    onAddPlaylistToQueue = { playlist ->
                                        playlistsController.addTargetToQueue(AddToPlaylistTarget.PlaylistTarget(playlist))
                                    },
                                    onAddPlaylistToPlaylist = { playlist ->
                                        playlistsController.openAddToPlaylist(AddToPlaylistTarget.PlaylistTarget(playlist))
                                    },
                                    onSmartPlaylistSave = { definition -> smartPlaylistsController.saveSmartPlaylist(definition) },
                                    onSmartPlaylistUpdate = { playlist, definition ->
                                        smartPlaylistsController.updateSmartPlaylist(playlist, definition)
                                    },
                                    onSmartPlaylistLoad = { playlist ->
                                        smartPlaylistsController.loadSmartPlaylistDefinition(playlist)
                                    },
                                )
                                DesktopAppRoute.PlaylistDetail -> DesktopPlaylistDetailPanel(
                                    appColors = appColors,
                                    playlist = selectedPlaylist,
                                    tracks = selectedPlaylistTracks,
                                    status = selectedPlaylistStatus ?: playlistStatus,
                                    playlistCoverArtUrl = selectedPlaylist?.coverArtId?.let { connectedProvider?.coverArtUrl(it) },
                                    coverArtUrl = { coverArtId ->
                                        coverArtId?.let { connectedProvider?.coverArtUrl(it) }
                                    },
                                    onBack = { appRoute = DesktopAppRoute.Playlists },
                                    onPlayPlaylist = { appActions.playPlaylistDetails() },
                                    onShufflePlaylist = { appActions.playPlaylistDetails(shuffle = true) },
                                    onRenamePlaylist = { selectedPlaylist?.let { playlistPendingRename = it } },
                                    onDeletePlaylist = { selectedPlaylist?.let { playlistPendingDelete = it } },
                                    onDownloadPlaylist = {
                                        selectedPlaylist?.let { appActions.downloadTracks(it.name, selectedPlaylistTracks) }
                                    },
                                    onAddPlaylistToQueue = {
                                        selectedPlaylist?.let {
                                            playlistsController.addTargetToQueue(AddToPlaylistTarget.PlaylistTarget(it))
                                        }
                                    },
                                    onAddPlaylistToPlaylist = {
                                        selectedPlaylist?.let {
                                            playlistsController.openAddToPlaylist(AddToPlaylistTarget.PlaylistTarget(it))
                                        }
                                    },
                                    onPlayTrack = { index -> appActions.playPlaylistDetails(index = index) },
                                    onTrackRadio = { track -> appActions.playTrackRadio(track) },
                                    onDownloadTrack = { track -> appActions.downloadTrack(track) },
                                    onAddTrackToQueue = { track ->
                                        playlistsController.addTargetToQueue(AddToPlaylistTarget.TrackTarget(track))
                                    },
                                    onAddTrackToPlaylist = { track ->
                                        playlistsController.openAddToPlaylist(AddToPlaylistTarget.TrackTarget(track))
                                    },
                                )
                                    DesktopAppRoute.Library -> {
                                        DesktopLibraryListLoadMoreEffect(
                                            selectedTab = libraryTab,
                                            snapshot = librarySnapshot,
                                            listState = libraryListState,
                                            onLoadMore = { libraryController.loadMoreLibraryRows() },
                                        )
                                        DesktopLibraryPanel(
                                            appColors = appColors,
                                            snapshot = librarySnapshot,
                                            query = libraryQuery,
                                            selectedTab = libraryTab,
                                            status = libraryStatus ?: connectionStatus,
                                            isSyncing = isLibrarySyncing,
                                            listState = libraryListState,
                                            coverArtUrl = { coverArtId ->
                                                coverArtId?.let { connectedProvider?.coverArtUrl(it) }
                                            },
                                            onQueryChanged = { libraryQuery = it },
                                            onTabSelected = {
                                                libraryTab = it
                                                libraryLimit = LibraryPageSize
                                                libraryController.refreshLibrarySnapshot()
                                                coroutineScope.launch {
                                                    libraryListState.scrollToItem(0)
                                                }
                                            },
                                            onLoadMore = { libraryController.loadMoreLibraryRows() },
                                            onJumpToLetter = { letter -> libraryController.jumpLibraryToLetter(letter) },
                                            onArtistSelected = { artist -> appActions.openArtistDetails(artist) },
                                            onArtistRadioSelected = { artist -> appActions.playArtistRadio(artist) },
                                            onArtistAddToQueue = { artist ->
                                                playlistsController.addTargetToQueue(AddToPlaylistTarget.ArtistTarget(artist))
                                            },
                                            onArtistAddToPlaylist = { artist ->
                                                playlistsController.openAddToPlaylist(AddToPlaylistTarget.ArtistTarget(artist))
                                            },
                                            onAlbumSelected = { album -> appActions.openAlbumDetails(album) },
                                            onAlbumRadioSelected = { album -> appActions.playAlbumRadio(album) },
                                            onAlbumDownloadSelected = { album -> appActions.downloadAlbum(album) },
                                            onAlbumAddToQueue = { album ->
                                                playlistsController.addTargetToQueue(AddToPlaylistTarget.AlbumTarget(album))
                                            },
                                            onAlbumAddToPlaylist = { album ->
                                                playlistsController.openAddToPlaylist(AddToPlaylistTarget.AlbumTarget(album))
                                            },
                                            onRefreshLibrary = { libraryController.startLibrarySync(force = true) },
                                        )
                                    }
                                    DesktopAppRoute.Search -> DesktopSearchPanel(
                                        appColors = appColors,
                                        query = searchQuery,
                                        results = searchResults,
                                        status = searchStatus,
                                        isSearching = isSearching,
                                        coverArtUrl = { coverArtId ->
                                            coverArtId?.let { connectedProvider?.coverArtUrl(it) }
                                        },
                                        onQueryChanged = searchController::updateQuery,
                                        onClearSearch = searchController::clearSearch,
                                        onArtistSelected = { artist -> appActions.openArtistDetails(artist) },
                                        onArtistRadioSelected = { artist -> appActions.playArtistRadio(artist) },
                                        onArtistAddToQueue = { artist ->
                                            playlistsController.addTargetToQueue(AddToPlaylistTarget.ArtistTarget(artist))
                                        },
                                        onArtistAddToPlaylist = { artist ->
                                            playlistsController.openAddToPlaylist(AddToPlaylistTarget.ArtistTarget(artist))
                                        },
                                        onAlbumSelected = { album -> appActions.openAlbumDetails(album) },
                                        onAlbumRadioSelected = { album -> appActions.playAlbumRadio(album) },
                                        onAlbumDownloadSelected = { album -> appActions.downloadAlbum(album) },
                                        onAlbumAddToQueue = { album ->
                                            playlistsController.addTargetToQueue(AddToPlaylistTarget.AlbumTarget(album))
                                        },
                                        onAlbumAddToPlaylist = { album ->
                                            playlistsController.openAddToPlaylist(AddToPlaylistTarget.AlbumTarget(album))
                                        },
                                        onTrackSelected = { index -> appActions.playSearchTrack(index) },
                                        onTrackRadioSelected = { index ->
                                            searchResults.tracks.getOrNull(index)?.let { appActions.playTrackRadio(it) }
                                        },
                                        onTrackDownloadSelected = { index ->
                                            searchResults.tracks.getOrNull(index)?.let { appActions.downloadTrack(it) }
                                        },
                                        onTrackAddToQueue = { index ->
                                            searchResults.tracks.getOrNull(index)?.let {
                                                playlistsController.addTargetToQueue(AddToPlaylistTarget.TrackTarget(it))
                                            }
                                        },
                                        onTrackAddToPlaylist = { index ->
                                            searchResults.tracks.getOrNull(index)?.let {
                                                playlistsController.openAddToPlaylist(AddToPlaylistTarget.TrackTarget(it))
                                            }
                                        },
                                    )
                                    DesktopAppRoute.InternetRadio -> DesktopInternetRadioPanel(
                                        appColors = appColors,
                                        stations = internetRadioStations,
                                        status = internetRadioStatus ?: connectionStatus,
                                        onPlayStation = { station -> internetRadioController.playStation(station) },
                                        onNewStation = { isNewInternetRadioStationDialogOpen = true },
                                        onEditStation = { station -> internetRadioStationPendingEdit = station },
                                        onDeleteStation = { station -> internetRadioStationPendingDelete = station },
                                    )
                                    DesktopAppRoute.Downloads -> {
                                        val downloads = remember(
                                            connectedSourceId,
                                            downloadRefreshToken,
                                            cacheStats.downloadCount,
                                        ) {
                                            connectedSourceId
                                                ?.let { storage.downloadedTracks(it) }
                                                .orEmpty()
                                        }
                                        val downloadedTrackById = remember(downloads) {
                                            downloads.associateBy { it.path.toString() }
                                        }
                                        val downloadItems = remember(downloads, connectedProvider) {
                                            downloads.map { download ->
                                                download.track.toDownloadedTrackUi(
                                                    id = download.path.toString(),
                                                    sizeBytes = download.sizeBytes,
                                                    coverArtUrl = { coverArtId ->
                                                        coverArtId?.let { connectedProvider?.coverArtUrl(it) }
                                                    },
                                                )
                                            }
                                        }
                                        DesktopDownloadsPanel(
                                            appColors = appColors,
                                            downloads = downloadItems,
                                            status = downloadStatus ?: connectionStatus,
                                            downloadBytes = cacheStats.downloadBytes,
                                            maxDownloadBytes = cacheSettings.maxDownloadBytes,
                                            onTrackSelected = { download ->
                                                val index = downloadItems.indexOfFirst { it.id == download.id }
                                                if (index >= 0) appActions.playDownloadedTrack(downloads, index)
                                            },
                                            onRemoveDownload = { download ->
                                                downloadedTrackById[download.id]?.let(appActions::removeDownloadedTrack)
                                            },
                                            onTrackAddToPlaylist = { download ->
                                                downloadedTrackById[download.id]?.let {
                                                    playlistsController.openAddToPlaylist(AddToPlaylistTarget.TrackTarget(it.track))
                                                }
                                            },
                                        )
                                    }
                                    DesktopAppRoute.Settings -> DesktopSettingsPanel(
                                        appColors = appColors,
                                        serverUrl = serverUrl,
                                        connectionName = connectionName,
                                        username = username,
                                        password = password,
                                        insecureSkipTlsVerification = insecureSkipTlsVerification,
                                        customCertificatePath = customCertificatePath,
                                        clientCertificateKeyStorePath = clientCertificateKeyStorePath,
                                        clientCertificateKeyStorePassword = clientCertificateKeyStorePassword,
                                        savedConnections = savedMediaSources,
                                        currentSourceId = connectedSourceId,
                                        hasSavedConnection = savedConnectionForLogin != null,
                                        isConnectionFormOpen = isConnectionFormOpen,
                                        isConnecting = isConnecting,
                                        connectionStatus = connectionStatus,
                                        playbackSettings = playbackSettings,
                                        cacheSettings = cacheSettings,
                                        cacheStats = cacheStats,
                                        supportsReplayGain = playbackEngine.supportsReplayGain,
                                        supportsGapless = playbackEngine.supportsGapless,
                                        supportsCrossfade = playbackEngine.supportsCrossfade,
                                        onServerUrlChanged = {
                                            serverUrl = it
                                            if (savedConnectionForLogin?.baseUrl != it) {
                                                savedConnectionForLogin = null
                                            }
                                        },
                                        onConnectionNameChanged = { connectionName = it },
                                        onUsernameChanged = {
                                            username = it
                                            if (savedConnectionForLogin?.username != it) {
                                                savedConnectionForLogin = null
                                            }
                                        },
                                        onPasswordChanged = { password = it },
                                        onInsecureSkipTlsVerificationChanged = {
                                            insecureSkipTlsVerification = it
                                        },
                                        onCustomCertificatePathChanged = {
                                            customCertificatePath = it
                                        },
                                        onClientCertificateKeyStorePathChanged = {
                                            clientCertificateKeyStorePath = it
                                        },
                                        onClientCertificateKeyStorePasswordChanged = {
                                            clientCertificateKeyStorePassword = it
                                        },
                                        onConnect = { appActions.connectToServer() },
                                        onNewConnection = {
                                            applyConnectionFormState(newDesktopConnectionFormState())
                                            isConnectionFormOpen = true
                                            connectionStatus = null
                                        },
                                        onEditConnection = { source ->
                                            applyConnectionFormState(savedDesktopConnectionFormState(source))
                                            isConnectionFormOpen = true
                                            connectionStatus = "Editing saved connection. Leave password blank to reuse it."
                                        },
                                        onConnectSavedConnection = { source ->
                                            applyConnectionFormState(savedDesktopConnectionFormState(source))
                                            isConnectionFormOpen = false
                                            appActions.connectToServer()
                                        },
                                        onDeleteConnection = { source -> appActions.deleteConnection(source) },
                                        onCancelConnectionForm = { isConnectionFormOpen = false },
                                        onPlaybackSettingsChanged = ::applyPlaybackSettings,
                                        onPlaybackSettingsChangedAndRedownload = ::applyPlaybackSettingsAndRedownload,
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
                                }
                            }
                        }
                        addToPlaylistTarget?.let { target ->
                            DesktopAddToPlaylistDialog(
                                appColors = appColors,
                                target = target,
                                playlists = playlists,
                                status = addToPlaylistStatus,
                                onDismiss = {
                                    addToPlaylistTarget = null
                                    addToPlaylistStatus = null
                                },
                                onAddToExisting = { playlist ->
                                    playlistsController.addTargetToPlaylist(target, playlist = playlist)
                                },
                                onCreateAndAdd = { name ->
                                    playlistsController.addTargetToPlaylist(target, playlist = null, newPlaylistName = name)
                                },
                            )
                        }
                        playlistPendingRename?.let { playlist ->
                            DesktopRenamePlaylistDialog(
                                playlist = playlist,
                                onDismiss = { playlistPendingRename = null },
                                onConfirm = { name -> appActions.renamePlaylist(playlist, name) },
                            )
                        }
                        playlistPendingDelete?.let { playlist ->
                            DesktopDeletePlaylistDialog(
                                playlist = playlist,
                                onDismiss = { playlistPendingDelete = null },
                                onConfirm = { appActions.deletePlaylist(playlist) },
                            )
                        }
                        if (isNewInternetRadioStationDialogOpen) {
                            DesktopInternetRadioStationDialog(
                                initialStation = null,
                                onDismiss = { isNewInternetRadioStationDialogOpen = false },
                                onConfirm = { station -> internetRadioController.saveStation(station) },
                            )
                        }
                        internetRadioStationPendingEdit?.let { station ->
                            DesktopInternetRadioStationDialog(
                                initialStation = station,
                                onDismiss = { internetRadioStationPendingEdit = null },
                                onConfirm = { updatedStation -> internetRadioController.saveStation(updatedStation) },
                            )
                        }
                        internetRadioStationPendingDelete?.let { station ->
                            DesktopDeleteInternetRadioStationDialog(
                                station = station,
                                onDismiss = { internetRadioStationPendingDelete = null },
                                onConfirm = { internetRadioController.deleteStation(station) },
                            )
                        }
                        if (nowPlayingTrack != null) {
                            DesktopMiniPlayerPanel(
                                appColors = appColors,
                                nowPlayingTrack = nowPlayingTrack,
                                coverArtUrl = nowPlayingCoverArtUrl,
                                hasPrevious = canUsePreviousButton(),
                                hasNext = playbackQueue.hasNext(),
                                playbackState = playbackState,
                                onPause = {
                                    playbackEngine.pause()
                                },
                                onResume = {
                                    playbackEngine.resume()
                                },
                                onPlayCurrent = {
                                    openPlayerOnTrackStart = false
                                    internetRadioController.playCurrentSelection()
                                },
                                onPrevious = {
                                    handlePreviousButton()
                                },
                                onNext = {
                                    openPlayerOnTrackStart = false
                                    playlistEngine.next(coroutineScope)
                                },
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
        }
    }
}
