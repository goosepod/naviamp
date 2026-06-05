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
import app.naviamp.desktop.playback.desktopPlaylistCallbacks
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
import app.naviamp.ui.radioArtworkNeedsTrackLookup
import app.naviamp.ui.radioTrackArtworkKey
import app.naviamp.ui.radioTrackArtworkQuery
import app.naviamp.ui.rememberPlatformCoverArtPlayerColors
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
    var internetRadioStations by remember { mutableStateOf<List<InternetRadioStation>>(emptyList()) }
    var internetRadioStatus by remember { mutableStateOf<String?>(null) }
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
    var radioTrackArtworkByKey by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }
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
    LaunchedEffect(nowPlayingInternetRadioStation?.id, nowPlayingStreamMetadata.title, nowPlayingStreamMetadata.properties, connectedProvider) {
        val station = nowPlayingInternetRadioStation ?: return@LaunchedEffect
        if (!radioArtworkNeedsTrackLookup(station, nowPlayingStreamMetadata.title, nowPlayingStreamMetadata.properties)) {
            return@LaunchedEffect
        }
        val key = radioTrackArtworkKey(station, nowPlayingStreamMetadata.title) ?: return@LaunchedEffect
        if (radioTrackArtworkByKey.containsKey(key)) return@LaunchedEffect
        val provider = connectedProvider ?: return@LaunchedEffect
        val query = radioTrackArtworkQuery(nowPlayingStreamMetadata.title) ?: return@LaunchedEffect
        val artworkUrl = withContext(Dispatchers.IO) {
            runCatching {
                provider
                    .search(query, limit = 5)
                    .tracks
                    .firstOrNull { it.coverArtId != null }
                    ?.coverArtId
                    ?.let(provider::coverArtUrl)
            }.getOrNull()
        }
        radioTrackArtworkByKey = radioTrackArtworkByKey + (key to artworkUrl)
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
        savedConnectionForLogin = { connectionForm.savedConnectionForLogin },
        setSavedConnectionForLogin = { connection -> connectionForm.savedConnectionForLogin = connection },
        incrementMediaSourcesRevision = { mediaSourcesRevision++ },
        setConnectionFormOpen = { isOpen -> connectionForm.isOpen = isOpen },
        setConnectionStatus = { status -> connectionStatus = status },
        setAppRoute = { route -> appRoute = route },
        appRoute = { appRoute },
    )

    fun refillRadioIfNeeded(queue: PlaybackQueue) {
        radioController.refillIfNeeded(queue)
    }

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
        setNowPlayingLyrics = { lyrics -> nowPlayingLyrics = lyrics },
        setNowPlayingLyricsStatus = { status -> nowPlayingLyricsStatus = status },
        setNowPlayingInternetRadioStation = { station -> nowPlayingInternetRadioStation = station },
        setNowPlayingStreamMetadata = { metadata -> nowPlayingStreamMetadata = metadata },
        incrementPlayReportSessionId = { playReportSessionId++ },
        clearSubmittedPlayReportSessionId = { submittedPlayReportSessionId = null },
        incrementNowPlayingWaveformReloadToken = { nowPlayingWaveformReloadToken++ },
        reportNowPlaying = ::reportNowPlaying,
        clearShuffleSnapshot = ::clearShuffleSnapshot,
        refillRadioIfNeeded = ::refillRadioIfNeeded,
        activeQueue = { playlistEngine.queue },
        setPlaybackQueue = { queue -> playbackQueue = queue },
        savePlaybackSession = ::savePlaybackSession,
        playbackProgress = { playbackProgress },
        setPlaybackProgress = { progress -> playbackProgress = progress },
        setPlaybackState = { state -> playbackState = state },
        pendingSeekPositionSeconds = { pendingSeekPositionSeconds },
        setPendingSeekPositionSeconds = { position -> pendingSeekPositionSeconds = position },
        pendingSeekIssuedAtMillis = { pendingSeekIssuedAtMillis },
        setPendingSeekIssuedAtMillis = { millis -> pendingSeekIssuedAtMillis = millis },
        lastPlaybackProgressUiUpdateMillis = { lastPlaybackProgressUiUpdateMillis },
        setLastPlaybackProgressUiUpdateMillis = { millis -> lastPlaybackProgressUiUpdateMillis = millis },
        maybeSavePlaybackPosition = ::maybeSavePlaybackPosition,
        maybeReportPlayed = ::maybeReportPlayed,
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
        password = { connectionForm.password },
        clearPassword = connectionForm::clearPassword,
        savedConnectionForLogin = { connectionForm.savedConnectionForLogin },
        setSavedConnectionForLogin = { connection -> connectionForm.savedConnectionForLogin = connection },
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
        connectionForm.apply(formState)
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
            serverUrl = connectionForm.serverUrl,
            username = connectionForm.username,
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
                                nowPlayingStreamMetadata = nowPlayingStreamMetadata,
                                lyricsVisible = nowPlayingLyricsVisible,
                                visualizerAvailable = (playbackEngine as? VisualizerPlaybackEngine)?.supportsVisualizer == true,
                                visualizerVisible = nowPlayingVisualizerVisible,
                                coverArtUrl = nowPlayingCoverArtUrl,
                                backTo = playbackQueue.backTo(),
                                upNext = playbackQueue.upNext(),
                                internetRadioStations = internetRadioStations,
                                currentInternetRadioStationId =
                                    nowPlayingInternetRadioStation?.id ?: nowPlayingTrack?.internetRadioStationId(),
                                radioTrackArtworkByKey = radioTrackArtworkByKey,
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
                            coroutineScope = coroutineScope,
                            onRouteSelected = { route -> appRoute = route },
                            onOpenArtistMixBuilder = {
                                libraryTab = DesktopLibraryTab.Artists
                                appRoute = DesktopAppRoute.Library
                            },
                            onOpenAlbumMixBuilder = {
                                libraryTab = DesktopLibraryTab.Albums
                                appRoute = DesktopAppRoute.Library
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
                            librarySnapshot = librarySnapshot,
                            libraryQuery = libraryQuery,
                            libraryTab = libraryTab,
                            libraryStatus = libraryStatus,
                            isLibrarySyncing = isLibrarySyncing,
                            libraryListState = libraryListState,
                            onLibraryQueryChanged = { libraryQuery = it },
                            onLibraryTabSelected = { tab ->
                                libraryTab = tab
                                libraryLimit = LibraryPageSize
                            },
                            searchQuery = searchQuery,
                            searchResults = searchResults,
                            searchStatus = searchStatus,
                            isSearching = isSearching,
                            internetRadioStations = internetRadioStations,
                            internetRadioStatus = internetRadioStatus,
                            onSaveInternetRadioStation = internetRadioController::saveStation,
                            onDeleteInternetRadioStation = internetRadioController::deleteStation,
                            connectedSourceId = connectedSourceId,
                            downloadRefreshToken = downloadRefreshToken,
                            downloadStatus = downloadStatus,
                            cacheSettings = cacheSettings,
                            cacheStats = cacheStats,
                            downloadedTracks = storage::downloadedTracks,
                            connectionForm = connectionForm,
                            savedMediaSources = savedMediaSources,
                            isConnecting = isConnecting,
                            playbackSettings = playbackSettings,
                            playbackEngine = playbackEngine,
                            onConnect = { appActions.connectToServer() },
                            onNewConnection = {
                                applyConnectionFormState(newDesktopConnectionFormState())
                                connectionForm.isOpen = true
                                connectionStatus = null
                            },
                            onEditConnection = { source ->
                                applyConnectionFormState(savedDesktopConnectionFormState(source))
                                connectionForm.isOpen = true
                                connectionStatus = "Editing saved connection. Leave password blank to reuse it."
                            },
                            onConnectSavedConnection = { source ->
                                applyConnectionFormState(savedDesktopConnectionFormState(source))
                                connectionForm.isOpen = false
                                appActions.connectToServer()
                            },
                            onDeleteConnection = { source -> appActions.deleteConnection(source) },
                            onCancelConnectionForm = { connectionForm.isOpen = false },
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
