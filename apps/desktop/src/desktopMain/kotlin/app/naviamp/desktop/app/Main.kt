package app.naviamp.desktop

import androidx.compose.desktop.ui.tooling.preview.Preview
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.Genre
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Lyrics
import app.naviamp.domain.Playlist
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.internetRadioStationId
import app.naviamp.domain.internetRadioTrackId
import app.naviamp.domain.isInternetRadioTrack
import app.naviamp.domain.cache.LibrarySnapshot
import app.naviamp.domain.playback.CrossfadeSettings
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackRequest
import app.naviamp.domain.playback.PlaybackVisualizerFrame
import app.naviamp.domain.playback.VisualizerPlaybackEngine
import app.naviamp.desktop.playback.PlaybackEngineFactory
import app.naviamp.desktop.playback.PlaybackEngineDiagnostics
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.desktop.playback.PlaylistCallbacks
import app.naviamp.desktop.playback.PlaylistEngine
import app.naviamp.desktop.playback.PlaybackSource
import app.naviamp.domain.playback.ReplayGainMode
import app.naviamp.domain.playback.label
import app.naviamp.domain.playback.mergeWith
import app.naviamp.domain.home.HomeContent
import app.naviamp.domain.home.HomeDate
import app.naviamp.domain.home.HomeService
import app.naviamp.domain.lyrics.selectPreferredLyrics
import app.naviamp.domain.popular.ArtistPopularTracksService
import app.naviamp.domain.popular.DeezerPopularTracksClient
import app.naviamp.domain.popular.SimilarArtistMatch
import app.naviamp.domain.popular.SimilarArtistsService
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.radio.RadioService
import app.naviamp.domain.radio.generatedRadioTracksToAppend
import app.naviamp.domain.radio.generatedRadioUpcomingTracks
import app.naviamp.domain.radio.generatedRadioUpcomingTracksToAppend
import app.naviamp.domain.radio.radioRefillSeedTrack
import app.naviamp.domain.radio.radioTracksNotAlreadyQueued
import app.naviamp.domain.source.SavedMediaSource
import app.naviamp.domain.smartplaylist.SmartPlaylistDefinition
import app.naviamp.domain.waveform.AudioWaveform
import app.naviamp.desktop.settings.DesktopSettingsStore
import app.naviamp.desktop.settings.NavigationSettings
import app.naviamp.desktop.settings.PlaybackSettings
import app.naviamp.desktop.settings.PlaybackSessionSettings
import app.naviamp.desktop.settings.PreviousButtonBehavior
import app.naviamp.desktop.settings.RecentRadioKind
import app.naviamp.desktop.settings.RecentRadioStream
import app.naviamp.desktop.settings.SavedAlbum
import app.naviamp.desktop.settings.SavedArtist
import app.naviamp.desktop.settings.SavedInternetRadioStation
import app.naviamp.desktop.settings.SavedTrack
import app.naviamp.desktop.settings.SearchSettings
import app.naviamp.desktop.settings.UpNextSelectionBehavior
import app.naviamp.desktop.settings.VisualizerSettings
import app.naviamp.domain.provider.AlbumListType
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.provider.createPlaylistOrAddMissingTracks
import app.naviamp.domain.settings.playbackSessionFromQueue
import app.naviamp.domain.settings.restoredPlaybackQueue
import app.naviamp.domain.settings.restoredTrackSession
import app.naviamp.provider.navidrome.NavidromeApiCallHistory
import app.naviamp.provider.navidrome.NavidromeConnection
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.provider.navidrome.NavidromeTls
import app.naviamp.provider.navidrome.NavidromeTlsSettings
import app.naviamp.provider.navidrome.toNavidromeConnection
import app.naviamp.provider.navidrome.withNativeTokenFromPassword
import app.naviamp.ui.NaviampPlayerColors
import app.naviamp.ui.NaviampVisualizer
import app.naviamp.ui.preloadJvmPlatformCoverArt
import app.naviamp.ui.resetJvmPlatformCoverArtByteLoader
import app.naviamp.ui.rememberPlatformCoverArtPlayerColors
import app.naviamp.ui.setJvmPlatformCoverArtByteLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Dimension
import java.awt.Desktop
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import kotlin.math.abs

fun main() {
    configureDesktopApplicationName()
    configureDesktopAppearance()
    configureDesktopIcon()

    application {
        val settingsStore = remember { DesktopSettingsStore() }
        val windowSettings = remember { settingsStore.loadWindowSettings() }
        val windowState = rememberWindowState(
            size = DpSize(windowSettings.widthDp.dp, windowSettings.heightDp.dp),
        )
        val playbackEngine = remember { PlaybackEngineFactory.createDefault() }
        val appIcon = painterResource("icons/naviamp.png")
        LaunchedEffect(windowState) {
            snapshotFlow { windowState.size }
                .distinctUntilChanged()
                .collect { size ->
                    if (size.width.value >= 320f && size.height.value >= 420f) {
                        settingsStore.saveWindowSettings(windowState.toWindowSettings())
                    }
                }
        }
        Window(
            state = windowState,
            icon = appIcon,
            onCloseRequest = {
                settingsStore.saveWindowSettings(windowState.toWindowSettings())
                runCatching {
                    playbackEngine.stop()
                }
                exitApplication()
            },
            title = "Naviamp",
        ) {
            val darkTitleBar = isSystemInDarkTheme()
            LaunchedEffect(window, darkTitleBar) {
                configureNativeTitleBar(window, darkTitleBar)
            }
            window.minimumSize = Dimension(320, 430)
            NaviampApp(
                playbackEngine = playbackEngine,
                settingsStore = settingsStore,
            )
        }
    }
}

@Composable
@Preview
fun NaviampApp(
    playbackEngine: PlaybackEngine = remember { PlaybackEngineFactory.createDefault() },
    settingsStore: DesktopSettingsStore = remember { DesktopSettingsStore() },
) {
    val isDark = isSystemInDarkTheme()
    val appColors = if (isDark) AppColors.Dark else AppColors.Light
    val colorScheme = if (isDark) darkColorScheme() else lightColorScheme()
    val sessionCache = remember { DesktopCaches.session }
    val savedMediaSource = remember { sessionCache.latestMediaSource() }
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
    var cacheStats by remember { mutableStateOf(CacheStats.empty()) }
    var connectedSourceId by remember { mutableStateOf(savedMediaSource?.id) }
    val deezerDiscoveryClient = remember { DeezerPopularTracksClient(DesktopPopularTracksHttpClient()) }
    val popularTracksService = remember(sessionCache) {
        ArtistPopularTracksService(
            repository = sessionCache,
            libraryTracksForArtist = { artist, limit ->
                val sourceId = connectedSourceId.orEmpty()
                sessionCache.libraryTracksForArtist(sourceId, artist.id, limit)
                    .ifEmpty { sessionCache.libraryTracksForArtistName(sourceId, artist.name, limit) }
            },
            client = deezerDiscoveryClient,
        )
    }
    val similarArtistsService = remember(sessionCache) {
        SimilarArtistsService(
            libraryArtistsSearch = { query, limit -> sessionCache.searchLibrary(connectedSourceId.orEmpty(), query, limit).artists },
            client = deezerDiscoveryClient,
        )
    }
    var cacheSettings by remember {
        mutableStateOf(settingsStore.loadCacheSettings().normalized())
    }
    val playlistEngine = remember(playbackEngine, sessionCache) {
        PlaylistEngine(
            playbackEngine = playbackEngine,
            cache = sessionCache,
            sourceIdProvider = { connectedSourceId },
            audioCachingEnabledProvider = { cacheSettings.audioCachingEnabled },
            audioPrefetchDepthProvider = { cacheSettings.audioPrefetchDepth },
        )
    }
    val librarySync = remember(sessionCache) { LibrarySync(sessionCache) }
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
    var playlistSortMode by remember { mutableStateOf(PlaylistSortMode.Alphabetical) }
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
    var albumDetailBackRoute by remember { mutableStateOf(AppRoute.Home) }
    var selectedArtist by remember { mutableStateOf<Artist?>(null) }
    var selectedArtistDetails by remember { mutableStateOf<ArtistDetails?>(null) }
    var selectedArtistStatus by remember { mutableStateOf<String?>(null) }
    var selectedArtistPopularTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var selectedArtistPopularTracksStatus by remember { mutableStateOf<String?>(null) }
    var selectedArtistSimilarArtists by remember { mutableStateOf<List<SimilarArtistMatch>>(emptyList()) }
    var selectedArtistSimilarArtistsStatus by remember { mutableStateOf<String?>(null) }
    var artistDetailBackRoute by remember { mutableStateOf(AppRoute.Search) }
    var artistDetailBackStack by remember { mutableStateOf<List<Artist>>(emptyList()) }
    var searchQuery by remember { mutableStateOf(savedSearch.query) }
    var searchResults by remember { mutableStateOf(MediaSearchResults()) }
    var searchStatus by remember { mutableStateOf<String?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var downloadStatus by remember { mutableStateOf<String?>(null) }
    var downloadRefreshToken by remember { mutableStateOf(0) }
    var libraryQuery by remember { mutableStateOf("") }
    var librarySnapshot by remember { mutableStateOf(LibrarySnapshot()) }
    var libraryTab by remember { mutableStateOf(LibraryTab.Artists) }
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
        mutableStateOf(settingsStore.loadPlaybackSettings().forEngine(playbackEngine))
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

    DisposableEffect(playbackEngine) {
        onDispose {
            playbackEngine.stop()
        }
    }

    DisposableEffect(sessionCache) {
        setJvmPlatformCoverArtByteLoader { url -> sessionCache.imageBytes(url) }
        onDispose {
            resetJvmPlatformCoverArtByteLoader()
        }
    }

    LaunchedEffect(playbackEngine, playbackSettings.gaplessEnabled, playbackSettings.crossfadeDurationSeconds) {
        val crossfadeDurationSeconds = playbackSettings.crossfadeDurationSeconds.coerceIn(0, 12)
        playlistEngine.setPlaybackTransitionSettings(
            gaplessEnabled = playbackSettings.gaplessEnabled,
            crossfadeSettings = CrossfadeSettings(
                enabled = !playbackSettings.gaplessEnabled && crossfadeDurationSeconds > 0,
                durationSeconds = crossfadeDurationSeconds,
            ),
        )
    }

    LaunchedEffect(playbackEngine, playbackSettings.volumePercent) {
        playbackEngine.setVolume(playbackSettings.volumePercent.coerceIn(0, 100))
    }

    LaunchedEffect(nowPlayingTrack?.id) {
        nowPlayingVisualizerFrame = null
    }

    LaunchedEffect(connectedProvider, nowPlayingTrack?.id, playbackState) {
        val provider = connectedProvider ?: return@LaunchedEffect
        val track = nowPlayingTrack ?: return@LaunchedEffect
        if (!provider.capabilities.supportsPlayReporting || track.isInternetRadioTrack()) return@LaunchedEffect
        if (playbackState != PlaybackState.Playing) return@LaunchedEffect

        while (true) {
            runCatching {
                withContext(Dispatchers.IO) {
                    provider.reportNowPlaying(track.id)
                }
            }
            delay(NowPlayingHeartbeatIntervalMillis)
        }
    }

    LaunchedEffect(playbackEngine, playbackState, nowPlayingVisualizerVisible, appRoute) {
        val visualizerEngine = playbackEngine as? VisualizerPlaybackEngine
        if (visualizerEngine?.supportsVisualizer != true) {
            nowPlayingVisualizerFrame = null
            return@LaunchedEffect
        }
        if (!nowPlayingVisualizerVisible || appRoute != AppRoute.Player) {
            nowPlayingVisualizerFrame = null
            return@LaunchedEffect
        }
        while (nowPlayingVisualizerVisible && appRoute == AppRoute.Player && (playbackState == PlaybackState.Playing || playbackState == PlaybackState.Loading)) {
            nowPlayingVisualizerFrame = visualizerEngine.visualizerFrame()
            kotlinx.coroutines.delay(VisualizerFrameIntervalMillis)
        }
        nowPlayingVisualizerFrame = null
    }

    LaunchedEffect(cacheSettings.maxAudioCacheBytes) {
        sessionCache.updateAudioCacheLimit(cacheSettings.maxAudioCacheBytes)
    }

    LaunchedEffect(cacheSettings.audioCachingEnabled, cacheSettings.audioPrefetchDepth) {
        if (!cacheSettings.audioCachingEnabled || cacheSettings.audioPrefetchDepth <= 0) {
            playlistEngine.cancelAudioPrefetch()
        }
    }

    LaunchedEffect(appRoute, albumDetailBackRoute, artistDetailBackRoute) {
        if (
            appRoute == AppRoute.Player ||
            appRoute == AppRoute.AlbumDetail ||
            appRoute == AppRoute.ArtistDetail ||
            appRoute == AppRoute.PlaylistDetail
        ) {
            settingsStore.saveNavigationSettings(
                NavigationSettings(
                    route = appRoute.name,
                    lastContentRoute = if (appRoute == AppRoute.AlbumDetail) {
                        albumDetailBackRoute.name
                    } else if (appRoute == AppRoute.ArtistDetail) {
                        artistDetailBackRoute.name
                    } else {
                        lastContentRoute.name
                    },
                ),
            )
        } else {
            lastContentRoute = appRoute
            settingsStore.saveNavigationSettings(
                NavigationSettings(
                    route = appRoute.name,
                    lastContentRoute = appRoute.name,
                ),
            )
        }
    }

    fun savePlaybackSession(
        queue: PlaybackQueue,
        positionSeconds: Double? = playbackProgress.positionSeconds,
    ) {
        settingsStore.savePlaybackSession(
            playbackSessionFromQueue(queue, positionSeconds),
        )
    }

    fun clearShuffleSnapshot() {
        shuffledUpNextSnapshot = null
    }

    fun toggleShuffle() {
        val snapshot = shuffledUpNextSnapshot
        if (snapshot == null) {
            shuffledUpNextSnapshot = playlistEngine.shuffleUpcoming()
        } else {
            playlistEngine.restoreUpcoming(snapshot)
            shuffledUpNextSnapshot = null
        }
    }

    fun cycleRepeatMode() {
        repeatMode = when (repeatMode) {
            RepeatMode.Off -> RepeatMode.Queue
            RepeatMode.Queue -> RepeatMode.Track
            RepeatMode.Track -> RepeatMode.Off
        }
        playlistEngine.setRepeatMode(repeatMode)
    }

    fun maybeSavePlaybackPosition(progress: PlaybackProgress) {
        val positionSeconds = progress.positionSeconds ?: return
        if (playbackQueue.currentIndex !in playbackQueue.tracks.indices) return
        val lastSaved = lastSavedPlaybackPositionSeconds
        if (lastSaved != null && abs(positionSeconds - lastSaved) < PlaybackPositionSaveThresholdSeconds) return
        lastSavedPlaybackPositionSeconds = positionSeconds
        savePlaybackSession(playbackQueue, positionSeconds)
    }

    fun performSeek(positionSeconds: Double) {
        if (nowPlayingTrack?.isInternetRadioTrack() == true) return
        val durationSeconds = playbackProgress.durationSeconds ?: nowPlayingTrack?.durationSeconds?.toDouble()
        val seekProgress = PlaybackProgress(
            positionSeconds = positionSeconds,
            durationSeconds = durationSeconds,
        )
        pendingSeekPositionSeconds = positionSeconds
        pendingSeekIssuedAtMillis = System.currentTimeMillis()
        playbackProgress = seekProgress
        maybeSavePlaybackPosition(seekProgress)
        val streamQuality = playbackSettings.streamQuality(playbackEngine)
        val playbackSource = playlistEngine.cacheRuntimeStats().playbackSource
        if (
            streamQuality is StreamQuality.Transcoded &&
            (
                playbackSource == PlaybackSource.ProviderStream ||
                    playbackSource == PlaybackSource.ProviderStreamCacheDisabled
                )
        ) {
            playlistEngine.playCurrent(coroutineScope, positionSeconds)
            return
        }
        playbackEngine.seek(positionSeconds)
    }

    fun canUsePreviousButton(): Boolean =
        playbackQueue.hasPrevious() ||
            (
                playbackSettings.previousButtonBehavior == PreviousButtonBehavior.RestartThenPrevious &&
                    (playbackProgress.positionSeconds ?: 0.0) > PreviousRestartThresholdSeconds
                )

    fun canUseNextButton(): Boolean =
        playbackQueue.hasNext() ||
            (repeatMode == RepeatMode.Queue && playbackQueue.tracks.isNotEmpty())

    fun handlePreviousButton() {
        openPlayerOnTrackStart = false
        val positionSeconds = playbackProgress.positionSeconds ?: 0.0
        if (
            playbackSettings.previousButtonBehavior == PreviousButtonBehavior.RestartThenPrevious &&
            positionSeconds > PreviousRestartThresholdSeconds
        ) {
            performSeek(0.0)
            return
        }
        playlistEngine.previous(coroutineScope)
    }

    fun reportNowPlaying(track: Track) {
        val provider = connectedProvider ?: return
        if (!provider.capabilities.supportsPlayReporting) return
        if (track.isInternetRadioTrack()) return
        coroutineScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    provider.reportNowPlaying(track.id)
                }
            }
        }
    }

    fun maybeReportPlayed(progress: PlaybackProgress) {
        val provider = connectedProvider ?: return
        if (!provider.capabilities.supportsPlayReporting) return
        if (submittedPlayReportSessionId == playReportSessionId) return
        val track = nowPlayingTrack ?: return
        val positionSeconds = progress.positionSeconds ?: return
        val durationSeconds = progress.durationSeconds ?: track.durationSeconds?.toDouble()
        if (positionSeconds < playReportThresholdSeconds(durationSeconds)) return

        val activeSessionId = playReportSessionId
        submittedPlayReportSessionId = activeSessionId
        coroutineScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    provider.reportPlayed(track.id, System.currentTimeMillis())
                }
            }.onFailure {
                if (submittedPlayReportSessionId == activeSessionId) {
                    submittedPlayReportSessionId = null
                }
            }
        }
    }

    fun stopRadioContinuation() {
        radioSessionId += 1
        radioQueueActive = false
        isRadioRefilling = false
        lastRadioRefillSeedId = null
    }

    fun refillRadioIfNeeded(queue: PlaybackQueue) {
        val seedTrack = radioRefillSeedTrack(
            queue = queue,
            refillThreshold = RadioRefillThreshold,
            isActive = radioQueueActive,
            isRefilling = isRadioRefilling,
            lastRefillSeedTrackId = lastRadioRefillSeedId,
        ) ?: return
        val provider = connectedProvider ?: return

        isRadioRefilling = true
        lastRadioRefillSeedId = seedTrack.id
        val activeRadioSessionId = radioSessionId
        coroutineScope.launch {
            try {
                val radioService = RadioService(provider)
                val fetchedTracks = withContext(Dispatchers.IO) {
                    radioService.trackRadio(seedTrack.id)
                }
                val newTracks = radioTracksNotAlreadyQueued(fetchedTracks, playlistEngine.queue.tracks)
                if (radioQueueActive && activeRadioSessionId == radioSessionId && newTracks.isNotEmpty()) {
                    playlistEngine.appendTracks(
                        tracks = newTracks,
                        maxHistory = RadioQueueHistoryLimit,
                    )
                }
            } catch (exception: Exception) {
                connectionStatus = exception.message ?: "Could not extend radio."
            } finally {
                if (activeRadioSessionId == radioSessionId) {
                    isRadioRefilling = false
                }
            }
        }
    }

    val playlistCallbacks = PlaylistCallbacks(
        onTrackStarted = { track, coverArtUrl ->
            val trackChanged = nowPlayingTrack?.id != track.id
            if (trackChanged) {
                clearShuffleSnapshot()
            }
            nowPlayingInternetRadioStation = null
            nowPlayingStreamMetadata = PlaybackStreamMetadata()
            nowPlayingTrack = track
            nowPlayingCoverArtUrl = coverArtUrl
            playReportSessionId += 1
            submittedPlayReportSessionId = null
            reportNowPlaying(track)
            if (trackChanged) {
                nowPlayingWaveform = null
                nowPlayingWaveformStatus = "Waiting"
                nowPlayingAudioTags = null
                nowPlayingLyrics = null
                nowPlayingLyricsStatus = null
                nowPlayingWaveformReloadToken += 1
            }
            playbackProgress = PlaybackProgress.Unknown
            refillRadioIfNeeded(playlistEngine.queue)
            if (openPlayerOnTrackStart) {
                appRoute = AppRoute.Player
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
            if (
                pendingSeek != null &&
                pendingSeekIssuedAt != null &&
                progressPosition != null &&
                abs(progressPosition - pendingSeek) > PendingSeekToleranceSeconds &&
                System.currentTimeMillis() - pendingSeekIssuedAt < PendingSeekStaleProgressWindowMillis
            ) {
                return@progressChanged
            }
            if (
                pendingSeek != null &&
                (progressPosition == null ||
                    pendingSeekIssuedAt == null ||
                    abs(progressPosition - pendingSeek) <= PendingSeekToleranceSeconds ||
                    System.currentTimeMillis() - pendingSeekIssuedAt >= PendingSeekStaleProgressWindowMillis)
            ) {
                pendingSeekPositionSeconds = null
                pendingSeekIssuedAtMillis = null
            }
            val currentProgress = playbackProgress
            val mergedProgress = progress.mergeWith(currentProgress)
            maybeSavePlaybackPosition(mergedProgress)
            maybeReportPlayed(mergedProgress)
            val now = System.currentTimeMillis()
            val currentPosition = currentProgress.positionSeconds
            val mergedPosition = mergedProgress.positionSeconds
            val shouldUpdateUiProgress =
                pendingSeek != null ||
                    mergedProgress.durationSeconds != currentProgress.durationSeconds ||
                    currentPosition == null ||
                    mergedPosition == null ||
                    abs(mergedPosition - currentPosition) >= PlaybackProgressUiUpdateThresholdSeconds ||
                    now - lastPlaybackProgressUiUpdateMillis >= PlaybackProgressUiUpdateIntervalMillis
            if (shouldUpdateUiProgress) {
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

    LaunchedEffect(
        nowPlayingTrack?.id,
        connectedSourceId,
        connectedProvider,
        playbackEngine,
        nowPlayingWaveformReloadToken,
        cacheSettings.audioCachingEnabled,
        playbackSettings.lrclibLyricsEnabled,
        nowPlayingLyricsVisible,
        appRoute,
    ) {
        val lyricsVisibleForWork = nowPlayingLyricsVisible && appRoute == AppRoute.Player
        val track = nowPlayingTrack ?: run {
            nowPlayingWaveform = null
            nowPlayingWaveformStatus = "No track"
            nowPlayingLyricsStatus = null
            return@LaunchedEffect
        }
        if (track.isInternetRadioTrack()) {
            nowPlayingWaveform = null
            nowPlayingWaveformStatus = "Internet radio"
            nowPlayingAudioTags = null
            nowPlayingLyrics = null
            nowPlayingLyricsStatus = null
            return@LaunchedEffect
        }
        val sourceId = connectedSourceId ?: run {
            nowPlayingWaveform = null
            nowPlayingWaveformStatus = "No source"
            nowPlayingLyricsStatus = null
            return@LaunchedEffect
        }
        val provider = connectedProvider ?: run {
            nowPlayingWaveform = null
            nowPlayingWaveformStatus = "No provider"
            nowPlayingLyricsStatus = null
            return@LaunchedEffect
        }
        val quality = playbackSettings.streamQuality(playbackEngine)
        nowPlayingWaveformStatus = "Loading"
        nowPlayingLyricsStatus = if (!lyricsVisibleForWork) {
            null
        } else if (playbackSettings.lrclibLyricsEnabled) {
            "Grabbing lyrics..."
        } else {
            "Loading lyrics..."
        }

        val waveformTagsAndLyrics = withContext(Dispatchers.IO) {
            runCatching {
                val cachedWaveformBeforeAudio = sessionCache.cachedAudioWaveform(
                    sourceId = sourceId,
                    trackId = track.id,
                    quality = quality,
                )
                val downloadedFile = sessionCache.downloadedAudioFile(sourceId, track.id, quality)
                val cachedFile = sessionCache.cachedAudioFile(sourceId, track.id, quality)
                val audioPath = downloadedFile?.path ?: cachedFile?.path
                val cachedWaveform = cachedWaveformBeforeAudio
                    ?: if (audioPath != null) {
                        sessionCache.cachedAudioWaveform(
                            sourceId = sourceId,
                            trackId = track.id,
                            quality = quality,
                        )
                    } else {
                        null
                    }
                val waveform = cachedWaveform ?: if (audioPath != null && cacheSettings.audioCachingEnabled) {
                    sessionCache.ensureAudioWaveform(
                        sourceId = sourceId,
                        trackId = track.id,
                        quality = quality,
                    )
                } else {
                    null
                }
                val waveformStatus = when {
                    cachedWaveform != null -> "Cached"
                    waveform != null -> "Generated"
                    audioPath == null && !cacheSettings.audioCachingEnabled -> "Cache disabled"
                    audioPath == null -> "Preparing"
                    else -> "Unavailable"
                }
                val tags = audioPath
                    ?.let { path -> runCatching { AudioTagReader().read(path) }.getOrDefault(emptyList()) }
                    .orEmpty()
                val providerLyrics = if (lyricsVisibleForWork) {
                    sessionCache.providerLyrics(sourceId, provider, track.id)
                } else {
                    null
                }
                val embeddedLyrics = if (lyricsVisibleForWork) lyricsFromAudioTags(tags) else null
                val localLyrics = providerLyrics ?: embeddedLyrics
                val lrclibLyrics = if (
                    lyricsVisibleForWork &&
                    playbackSettings.lrclibLyricsEnabled &&
                    (localLyrics == null || !localLyrics.synced)
                ) {
                    sessionCache.lrclibLyrics(sourceId, track)
                } else {
                    null
                }
                val lyrics = selectPreferredLyrics(
                    providerLyrics = providerLyrics,
                    embeddedLyrics = embeddedLyrics,
                    onlineLyrics = lrclibLyrics,
                )
                NowPlayingAnalysis(waveform, waveformStatus, tags, lyrics)
            }.getOrNull()
        }
        nowPlayingWaveform = waveformTagsAndLyrics?.waveform
        nowPlayingWaveformStatus = waveformTagsAndLyrics?.waveformStatus ?: "Unavailable"
        nowPlayingAudioTags = waveformTagsAndLyrics?.audioTags
        nowPlayingLyrics = waveformTagsAndLyrics?.lyrics
        nowPlayingLyricsStatus = null
    }

    LaunchedEffect(nowPlayingTrack?.id, connectedSourceId) {
        val track = nowPlayingTrack
        val sourceId = connectedSourceId
        if (track == null || sourceId == null || track.isInternetRadioTrack()) {
            relatedTracks = emptyList()
            return@LaunchedEffect
        }
        relatedTracks = withContext(Dispatchers.IO) {
            sessionCache.relatedLibraryTracks(sourceId, track, limit = 40)
        }
    }

    LaunchedEffect(nowPlayingCoverArtUrl, playbackQueue, connectedProvider) {
        val provider = connectedProvider ?: return@LaunchedEffect
        val urls = buildList {
            nowPlayingCoverArtUrl?.let(::add)
            playbackQueue.backTo().take(CoverArtPreloadHistoryLimit).forEach { track ->
                track.coverArtId?.let { add(provider.coverArtUrl(it)) }
            }
            playbackQueue.upNext().take(CoverArtPreloadUpcomingLimit).forEach { track ->
                track.coverArtId?.let { add(provider.coverArtUrl(it)) }
            }
        }
        withContext(Dispatchers.IO) {
            preloadJvmPlatformCoverArt(urls)
        }
    }

    fun refreshLibrarySnapshot() {
        val sourceId = connectedSourceId
        if (sourceId == null) {
            librarySnapshot = LibrarySnapshot()
            libraryStatus = "Connect to Navidrome to import your library."
            return
        }
        librarySnapshot = sessionCache.librarySnapshotFor(sourceId, libraryQuery, libraryLimit)
    }

    fun loadMoreLibraryRows() {
        val nextLimit = nextLibraryLimit(librarySnapshot, libraryTab, libraryLimit, LibraryPageSize)
        if (nextLimit == libraryLimit) return
        libraryLimit = nextLimit
        refreshLibrarySnapshot()
    }

    fun jumpLibraryToLetter(letter: Char) {
        val sourceId = connectedSourceId ?: return
        if (libraryQuery.isNotBlank()) return
        val offset = sessionCache.libraryOffsetForLetter(sourceId, libraryTab, letter).toInt()
        libraryLimit = libraryLimitForOffset(offset, LibraryPageSize)
        refreshLibrarySnapshot()
        coroutineScope.launch {
            libraryListState.scrollToItem((offset + 1).coerceAtLeast(0))
        }
    }

    fun loadHomeContent(provider: NavidromeProvider) {
        val sourceId = connectedSourceId
        homeStatus = "Loading home..."
        coroutineScope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    val today = LocalDate.now()
                    HomeService(
                        provider = provider,
                        libraryRepository = sessionCache.asHomeLibraryRepository(),
                        sourceId = sourceId,
                        date = HomeDate(year = today.year, dayOfYear = today.dayOfYear),
                    ).load(
                        recentRadioStreams = recentRadioStreams,
                        recentInternetRadioStations = recentInternetRadioStations,
                    )
                }
                homeContent = content
                homeStatus = null
            } catch (exception: Exception) {
                homeStatus = exception.message ?: "Could not load home."
            }
        }
    }

    fun refreshPlaylists() {
        val provider = connectedProvider ?: return
        playlistStatus = "Loading playlists..."
        coroutineScope.launch {
            try {
                playlists = withContext(Dispatchers.IO) {
                    provider.playlists(limit = 500)
                }
                playlistStatus = null
                homeContent = homeContent.copy(
                    playlists = playlists.sortedWith(
                        compareBy<Playlist> {
                            val index = recentPlaylistIds.indexOf(it.id)
                            if (index == -1) Int.MAX_VALUE else index
                        }.thenBy { it.name.lowercase() },
                    ).take(6),
                    recentRadioStreams = recentRadioStreams,
                    recentInternetRadioStations = recentInternetRadioStations,
                )
                playlists.take(100).forEach { playlist ->
                    if (playlistTracksById[playlist.id].isNullOrEmpty()) {
                        runCatching {
                            withContext(Dispatchers.IO) { provider.playlistTracks(playlist.id) }
                        }.onSuccess { tracks ->
                            playlistTracksById = playlistTracksById + (playlist.id to tracks)
                        }
                    }
                }
            } catch (exception: Exception) {
                playlistStatus = exception.message ?: "Could not load playlists."
            }
        }
    }

    suspend fun refreshPlaylistDetailsFromServer(
        provider: MediaProvider,
        playlist: Playlist,
        showLoadingStatus: Boolean,
    ) {
        if (showLoadingStatus) selectedPlaylistStatus = "Loading ${playlist.name}..."
        val refreshedPlaylists = withContext(Dispatchers.IO) { provider.playlists(limit = 500) }
        val refreshedPlaylist = refreshedPlaylists.firstOrNull { it.id == playlist.id } ?: playlist
        val refreshedTracks = withContext(Dispatchers.IO) { provider.playlistTracks(refreshedPlaylist.id) }
        val displayPlaylist = refreshedPlaylist.copy(trackCount = refreshedTracks.size)
        playlists = refreshedPlaylists.map {
            if (it.id == displayPlaylist.id) displayPlaylist else it
        }
        homeContent = homeContent.copy(
            playlists = playlists.sortedWith(
                compareBy<Playlist> {
                    val index = recentPlaylistIds.indexOf(it.id)
                    if (index == -1) Int.MAX_VALUE else index
                }.thenBy { it.name.lowercase() },
            ).take(6),
            recentRadioStreams = recentRadioStreams,
            recentInternetRadioStations = recentInternetRadioStations,
        )
        playlistTracksById = playlistTracksById + (displayPlaylist.id to refreshedTracks)
        if (selectedPlaylist?.id == playlist.id) {
            selectedPlaylist = displayPlaylist
            selectedPlaylistTracks = refreshedTracks
            selectedPlaylistStatus = null
        }
    }

    LaunchedEffect(connectedProvider, appRoute, selectedPlaylist?.id) {
        val provider = connectedProvider ?: return@LaunchedEffect
        val playlist = selectedPlaylist ?: return@LaunchedEffect
        if (appRoute != AppRoute.PlaylistDetail) return@LaunchedEffect

        while (true) {
            delay(PlaylistDetailRefreshIntervalMillis)
            runCatching {
                refreshPlaylistDetailsFromServer(
                    provider = provider,
                    playlist = playlist,
                    showLoadingStatus = false,
                )
            }
        }
    }

    suspend fun saveSmartPlaylist(definition: SmartPlaylistDefinition) {
        var provider = connectedProvider
            ?: throw IllegalStateException("Connect to Navidrome before saving smart playlists.")
        playlistStatus = "Saving ${definition.name}..."
        try {
            val savedConnection = savedConnectionForLogin
            if (savedConnection?.nativeToken.isNullOrBlank() && password.isNotBlank()) {
                val refreshedConnection = withContext(Dispatchers.IO) {
                    savedConnection!!.withNativeTokenFromPassword(password, required = true)
                }
                val refreshedProvider = NavidromeProvider(refreshedConnection)
                connectedProvider = refreshedProvider
                connectedSourceId = sessionCache.upsertNavidromeSource(refreshedConnection, refreshedProvider).id
                savedConnectionForLogin = refreshedConnection
                mediaSourcesRevision++
                password = ""
                provider = refreshedProvider
                connectionStatus = "Smart playlist authentication refreshed."
            }
            val playlist = withContext(Dispatchers.IO) { provider.createSmartPlaylist(definition) }
            val refreshedPlaylists = withContext(Dispatchers.IO) { provider.playlists(limit = 500) }
            val refreshedPlaylist = refreshedPlaylists.firstOrNull { it.id == playlist.id }
                ?: refreshedPlaylists.firstOrNull { it.name == playlist.name }
                ?: playlist
            val refreshedTracks = withContext(Dispatchers.IO) { provider.playlistTracks(refreshedPlaylist.id) }
            val displayPlaylist = refreshedPlaylist.copy(trackCount = refreshedTracks.size)
            playlistTracksById = playlistTracksById + (displayPlaylist.id to refreshedTracks)
            playlists = (refreshedPlaylists.filterNot { it.id == displayPlaylist.id } + displayPlaylist)
                .sortedBy { it.name.lowercase() }
            homeContent = homeContent.copy(
                playlists = playlists.sortedWith(
                    compareBy<Playlist> {
                        val index = recentPlaylistIds.indexOf(it.id)
                        if (index == -1) Int.MAX_VALUE else index
                    }.thenBy { it.name.lowercase() },
                ).take(6),
            )
            playlistStatus = "Saved smart playlist ${displayPlaylist.name} with ${refreshedTracks.size} tracks."
        } catch (error: Exception) {
            val message = if (error.message == "Reconnect to Navidrome with your password before saving smart playlists.") {
                "Edit this saved connection, enter your Navidrome password, then Save and connect before saving smart playlists."
            } else {
                error.message ?: "Could not save smart playlist."
            }
            playlistStatus = message
            if (message != error.message) throw IllegalStateException(message)
            throw error
        } finally {
            statsForNerdsRefreshTick++
        }
    }

    suspend fun updateSmartPlaylist(playlist: Playlist, definition: SmartPlaylistDefinition) {
        val provider = connectedProvider
            ?: throw IllegalStateException("Connect to Navidrome before updating smart playlists.")
        playlistStatus = "Updating ${definition.name}..."
        try {
            withContext(Dispatchers.IO) { provider.updateSmartPlaylist(playlist.id, definition) }
            val refreshedPlaylists = withContext(Dispatchers.IO) { provider.playlists(limit = 500) }
            val refreshedPlaylist = refreshedPlaylists.firstOrNull { it.id == playlist.id }
                ?: playlist.copy(name = definition.name)
            val refreshedTracks = withContext(Dispatchers.IO) { provider.playlistTracks(refreshedPlaylist.id) }
            val displayPlaylist = refreshedPlaylist.copy(trackCount = refreshedTracks.size)
            playlistTracksById = playlistTracksById + (displayPlaylist.id to refreshedTracks)
            playlists = (refreshedPlaylists.filterNot { it.id == displayPlaylist.id } + displayPlaylist)
                .sortedBy { it.name.lowercase() }
            if (selectedPlaylist?.id == displayPlaylist.id) {
                selectedPlaylist = displayPlaylist
                selectedPlaylistTracks = refreshedTracks
                selectedPlaylistStatus = null
            }
            playlistStatus = "Updated smart playlist ${displayPlaylist.name} with ${refreshedTracks.size} tracks."
        } catch (error: Exception) {
            playlistStatus = error.message ?: "Could not update smart playlist."
            throw error
        } finally {
            statsForNerdsRefreshTick++
        }
    }

    suspend fun loadSmartPlaylistDefinition(playlist: Playlist): SmartPlaylistDefinition {
        val provider = connectedProvider
            ?: throw IllegalStateException("Connect to Navidrome before editing smart playlists.")
        playlistStatus = "Loading ${playlist.name} rules..."
        return try {
            withContext(Dispatchers.IO) { provider.smartPlaylistDefinition(playlist.id) }
                .also { playlistStatus = null }
        } catch (error: Exception) {
            playlistStatus = error.message ?: "Could not load smart playlist rules."
            throw error
        } finally {
            statsForNerdsRefreshTick++
        }
    }

    suspend fun resolveTargetTracks(
        provider: MediaProvider,
        target: AddToPlaylistTarget,
    ): List<Track> =
        when (target) {
            is AddToPlaylistTarget.TrackTarget -> listOf(target.track)
            is AddToPlaylistTarget.AlbumTarget -> provider.album(target.album.id).tracks
            is AddToPlaylistTarget.ArtistTarget -> provider.artist(target.artist.id).albums.flatMap { album ->
                provider.album(album.id).tracks
            }
            is AddToPlaylistTarget.PlaylistTarget -> provider.playlistTracks(target.playlist.id)
        }

    fun openAddToPlaylist(target: AddToPlaylistTarget) {
        addToPlaylistTarget = target
        addToPlaylistStatus = null
        if (playlists.isEmpty()) refreshPlaylists()
    }

    fun addTargetToPlaylist(target: AddToPlaylistTarget, playlist: Playlist?, newPlaylistName: String? = null) {
        val provider = connectedProvider ?: return
        addToPlaylistStatus = "Loading tracks..."
        coroutineScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val targetIds = resolveTargetTracks(provider, target).map { it.id }.distinct()
                    provider.createPlaylistOrAddMissingTracks(
                        playlistId = playlist?.id,
                        newPlaylistName = newPlaylistName,
                        trackIds = targetIds,
                    )
                }
                if (result.requestedTrackCount == 0) {
                    addToPlaylistStatus = if (playlist == null) {
                        "No tracks found."
                    } else {
                        "Everything is already in ${playlist.name}."
                    }
                    return@launch
                }
                if (result.addedTrackIds.isEmpty()) {
                    addToPlaylistStatus = "Everything is already in ${playlist?.name.orEmpty()}."
                    return@launch
                }
                addToPlaylistTarget = null
                addToPlaylistStatus = null
                connectionStatus = "Added ${result.addedTrackIds.size} track${if (result.addedTrackIds.size == 1) "" else "s"} to playlist."
                refreshPlaylists()
            } catch (exception: Exception) {
                addToPlaylistStatus = exception.message ?: "Could not add to playlist."
            }
        }
    }

    fun addTargetToQueue(target: AddToPlaylistTarget) {
        val provider = connectedProvider ?: return
        connectionStatus = "Loading tracks..."
        coroutineScope.launch {
            try {
                val tracksToAdd = withContext(Dispatchers.IO) {
                    resolveTargetTracks(provider, target)
                }
                if (tracksToAdd.isEmpty()) {
                    connectionStatus = "No tracks found."
                    return@launch
                }
                playlistEngine.appendTracks(tracksToAdd)
                connectionStatus = "Added ${tracksToAdd.size} track${if (tracksToAdd.size == 1) "" else "s"} to queue."
            } catch (exception: Exception) {
                connectionStatus = exception.message ?: "Could not add to queue."
            }
        }
    }

    fun markPlaylistPlayed(playlist: Playlist) {
        recentPlaylistIds = (listOf(playlist.id) + recentPlaylistIds).distinct().take(50)
        settingsStore.saveRecentPlaylistIds(recentPlaylistIds)
    }

    fun rememberRadioStream(stream: RecentRadioStream) {
        recentRadioStreams = (listOf(stream) + recentRadioStreams.filterNot { it.id == stream.id }).take(12)
        settingsStore.saveRecentRadioStreams(recentRadioStreams)
        homeContent = homeContent.copy(recentRadioStreams = recentRadioStreams)
    }

    fun refreshInternetRadioStations() {
        val provider = connectedProvider ?: return
        internetRadioStatus = "Loading internet radio..."
        coroutineScope.launch {
            try {
                internetRadioStations = withContext(Dispatchers.IO) {
                    provider.internetRadioStations()
                }
                internetRadioStatus = null
            } catch (exception: Exception) {
                internetRadioStatus = exception.message ?: "Could not load internet radio stations."
            }
        }
    }

    fun rememberInternetRadioStation(station: InternetRadioStation) {
        recentInternetRadioStations = (listOf(station) + recentInternetRadioStations.filterNot { it.id == station.id })
            .take(12)
        settingsStore.saveRecentInternetRadioStations(
            recentInternetRadioStations.map { SavedInternetRadioStation.fromStation(it) },
        )
        homeContent = homeContent.copy(recentInternetRadioStations = recentInternetRadioStations)
    }

    fun playInternetRadioStation(station: InternetRadioStation) {
        rememberInternetRadioStation(station)
        stopRadioContinuation()
        clearShuffleSnapshot()
        playlistEngine.clear()
        val radioTrack = Track(
            id = internetRadioTrackId(station.id),
            title = station.name,
            artistName = "Internet Radio",
            albumTitle = station.homePageUrl ?: station.streamUrl,
            durationSeconds = null,
            coverArtId = null,
            audioInfo = null,
            replayGain = null,
        )
        nowPlayingTrack = radioTrack
        nowPlayingCoverArtUrl = null
        nowPlayingWaveform = null
        nowPlayingWaveformStatus = "Internet radio"
        nowPlayingAudioTags = null
        nowPlayingLyrics = null
        nowPlayingLyricsStatus = null
        nowPlayingInternetRadioStation = station
        nowPlayingStreamMetadata = PlaybackStreamMetadata()
        playbackProgress = PlaybackProgress.Unknown
        playbackQueue = PlaybackQueue()
        settingsStore.savePlaybackSession(PlaybackSessionSettings.fromInternetRadioStation(station))
        appRoute = AppRoute.Player
        playbackEngine.play(
            scope = coroutineScope,
            request = PlaybackRequest(
                url = station.streamUrl,
                mediaId = station.id,
                replayGainMode = ReplayGainMode.Off,
            ),
            onStateChanged = { state ->
                playbackState = state
            },
            onProgressChanged = { progress ->
                val now = System.currentTimeMillis()
                val liveProgress = progress.copy(durationSeconds = null)
                val currentPosition = playbackProgress.positionSeconds
                val livePosition = liveProgress.positionSeconds
                if (
                    currentPosition == null ||
                    livePosition == null ||
                    abs(livePosition - currentPosition) >= PlaybackProgressUiUpdateThresholdSeconds ||
                    now - lastPlaybackProgressUiUpdateMillis >= PlaybackProgressUiUpdateIntervalMillis
                ) {
                    playbackProgress = liveProgress
                    lastPlaybackProgressUiUpdateMillis = now
                }
            },
            onMetadataChanged = { metadata ->
                nowPlayingStreamMetadata = metadata
                metadata.title?.takeIf { it.isNotBlank() }?.let { streamTitle ->
                    nowPlayingTrack = radioTrack.copy(
                        title = streamTitle,
                        artistName = station.name,
                        albumTitle = "Internet Radio",
                    )
                }
            },
        )
    }

    fun playCurrentSelection() {
        val station = nowPlayingInternetRadioStation
        if (station != null || nowPlayingTrack?.isInternetRadioTrack() == true) {
            station?.let { playInternetRadioStation(it) }
            return
        }
        val restoredPosition = restoredPlaybackPositionSeconds
        restoredPlaybackPositionSeconds = null
        playlistEngine.playCurrent(coroutineScope, restoredPosition)
    }

    fun saveInternetRadioStation(station: InternetRadioStation) {
        val provider = connectedProvider ?: return
        internetRadioStatus = "Saving ${station.name}..."
        coroutineScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (station.id == station.streamUrl) {
                        provider.createInternetRadioStation(
                            name = station.name,
                            streamUrl = station.streamUrl,
                            homePageUrl = station.homePageUrl,
                        )
                    } else {
                        provider.updateInternetRadioStation(station)
                    }
                }
                isNewInternetRadioStationDialogOpen = false
                internetRadioStationPendingEdit = null
                internetRadioStatus = null
                refreshInternetRadioStations()
            } catch (exception: Exception) {
                internetRadioStatus = exception.message ?: "Could not save station."
            }
        }
    }

    fun deleteInternetRadioStation(station: InternetRadioStation) {
        val provider = connectedProvider ?: return
        internetRadioStatus = "Deleting ${station.name}..."
        coroutineScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    provider.deleteInternetRadioStation(station.id)
                }
                internetRadioStationPendingDelete = null
                internetRadioStatus = null
                refreshInternetRadioStations()
            } catch (exception: Exception) {
                internetRadioStatus = exception.message ?: "Could not delete station."
            }
        }
    }

    fun startLibrarySync(force: Boolean = false) {
        val provider = connectedProvider ?: return
        val sourceId = connectedSourceId ?: return
        if (isLibrarySyncing) return
        if (!force && !shouldAutoSyncLibrary(sourceId, sessionCache)) {
            libraryStatus = null
            return
        }
        isLibrarySyncing = true
        libraryStatus = "Starting library import..."
        coroutineScope.launch {
            val uiContext = coroutineContext
            try {
                withContext(Dispatchers.IO) {
                    librarySync.sync(
                        sourceId = sourceId,
                        provider = provider,
                        onProgress = { progress ->
                            withContext(uiContext) {
                                libraryStatus = progress.label()
                            }
                        },
                    )
                    provider.libraryScanStatus()?.signature?.let { signature ->
                        sessionCache.markLibraryScanChecked(sourceId, signature)
                    }
                }
                refreshLibrarySnapshot()
                libraryStatus = null
            } catch (exception: Exception) {
                libraryStatus = exception.message ?: "Could not import library."
            } finally {
                isLibrarySyncing = false
            }
        }
    }

    fun checkLibraryFreshness() {
        val provider = connectedProvider ?: return
        val sourceId = connectedSourceId ?: return
        if (isLibrarySyncing) return
        coroutineScope.launch {
            val freshness = withContext(Dispatchers.IO) {
                val scanStatus = provider.libraryScanStatus()
                val signature = scanStatus?.signature
                val source = sessionCache.mediaSource(sourceId)
                LibraryFreshness(
                    signature = signature,
                    previousSignature = source?.lastLibraryScanSignature,
                    scanning = scanStatus?.scanning == true,
                )
            }
            val update = freshness.evaluateLibraryFreshness(libraryStatus)
            update.signatureToMarkChecked?.let { signature ->
                withContext(Dispatchers.IO) {
                    sessionCache.markLibraryScanChecked(sourceId, signature)
                }
            }
            update.status?.let { status ->
                libraryStatus = status
            }
            if (update.clearStatus) {
                libraryStatus = null
            }
        }
    }

    fun connectionTlsSettings(): NavidromeTlsSettings =
        NavidromeTlsSettings(
            insecureSkipTlsVerification = insecureSkipTlsVerification,
            customCertificatePath = customCertificatePath.trim().takeIf { !insecureSkipTlsVerification && it.isNotEmpty() },
            clientCertificateKeyStorePath = clientCertificateKeyStorePath.trim().takeIf { it.isNotEmpty() },
            clientCertificateKeyStorePassword = clientCertificateKeyStorePassword
                .takeIf { clientCertificateKeyStorePath.trim().isNotEmpty() },
        )

    fun resolvedConnectionDisplayName(): String =
        connectionName.trim().takeIf { it.isNotEmpty() } ?: serverUrl.trim().trimEnd('/')

    fun connectToServer(restoreSavedSession: Boolean = false) {
        if (isConnecting) return
        if (serverUrl.isBlank() || username.isBlank()) {
            connectionStatus = "Enter a server URL and username."
            appRoute = AppRoute.Settings
            return
        }
        if (password.isBlank() && savedConnectionForLogin == null) {
            connectionStatus = "Enter a password for first-time setup."
            appRoute = AppRoute.Settings
            return
        }

        isConnecting = true
        connectionStatus = "Connecting to Navidrome..."
        if (!restoreSavedSession) {
            homeContent = HomeContent()
            homeStatus = null
            stopRadioContinuation()
            clearShuffleSnapshot()
            playlistEngine.clear()
            playbackEngine.stop()
            nowPlayingTrack = null
            nowPlayingCoverArtUrl = null
            nowPlayingLyricsStatus = null
            playbackState = PlaybackState.Idle
            playbackProgress = PlaybackProgress.Unknown
            playbackQueue = PlaybackQueue()
            settingsStore.savePlaybackSession(null)
        }

        coroutineScope.launch {
            try {
                val tlsSettings = connectionTlsSettings()
                val reusableCredentials = savedConnectionForLogin?.takeIf {
                    it.baseUrl == serverUrl && it.username == username && password.isBlank()
                }
                val connectionWithoutNativeRefresh = reusableCredentials?.copy(
                    displayName = resolvedConnectionDisplayName(),
                    tlsSettings = tlsSettings,
                ) ?: NavidromeConnection.fromPassword(
                    baseUrl = serverUrl,
                    username = username,
                    password = password,
                    displayName = resolvedConnectionDisplayName(),
                    tlsSettings = tlsSettings,
                )
                var smartPlaylistAuthWarning: String? = null
                val connection = if (password.isNotBlank()) {
                    runCatching {
                        connectionWithoutNativeRefresh.withNativeTokenFromPassword(password, required = true)
                    }.getOrElse { nativeAuthError ->
                        smartPlaylistAuthWarning = nativeAuthError.message
                            ?: "Could not authenticate with Navidrome's native API."
                        connectionWithoutNativeRefresh
                    }
                } else {
                    connectionWithoutNativeRefresh
                }
                NavidromeTls.applyJvmDefaults(connection.tlsSettings)
                val provider = NavidromeProvider(connection)
                val validation = provider.validateConnection()
                if (!restoreSavedSession) {
                    sessionCache.clearProviderData()
                }
                connectedProvider = provider
                connectedSourceId = sessionCache.upsertNavidromeSource(connection, provider).id
                mediaSourcesRevision++
                if (restoreSavedSession && savedPlaybackSession != null) {
                    val internetRadioStation = savedPlaybackSession.internetRadioStation?.toStation()
                    val restoredSession = savedPlaybackSession.restoredTrackSession()
                    val currentTrack = restoredSession?.currentTrack ?: savedPlaybackSession.currentTrack()
                    if (internetRadioStation != null && currentTrack != null) {
                        nowPlayingInternetRadioStation = internetRadioStation
                        nowPlayingStreamMetadata = PlaybackStreamMetadata()
                        nowPlayingTrack = currentTrack
                        nowPlayingCoverArtUrl = null
                        nowPlayingWaveform = null
                        nowPlayingWaveformStatus = "Internet radio"
                        nowPlayingAudioTags = null
                        nowPlayingLyrics = null
                        nowPlayingLyricsStatus = null
                        playbackProgress = PlaybackProgress.Unknown
                        playbackQueue = PlaybackQueue()
                        playbackState = PlaybackState.Idle
                    } else if (restoredSession != null) {
                        playlistEngine.restore(
                            provider = provider,
                            tracks = restoredSession.tracks,
                            index = restoredSession.currentIndex,
                            quality = playbackSettings.streamQuality(playbackEngine),
                            replayGainMode = playbackSettings.replayGainMode,
                            callbacks = playlistCallbacks,
                        )
                        nowPlayingInternetRadioStation = null
                        nowPlayingStreamMetadata = PlaybackStreamMetadata()
                        nowPlayingTrack = restoredSession.currentTrack
                        nowPlayingCoverArtUrl = restoredSession.currentTrack.coverArtId?.let { provider.coverArtUrl(it) }
                        playbackState = PlaybackState.Idle
                    }
                }
                settingsStore.clearConnection()
                savedConnectionForLogin = connection
                if (connection.nativeToken?.isNotBlank() == true) {
                    password = ""
                }
                isConnectionFormOpen = false
                if (appRoute == AppRoute.Settings) {
                    appRoute = AppRoute.Home
                }
                connectionStatus = buildString {
                    append("Connected")
                    validation.serverVersion?.let { append(" to Navidrome $it") }
                    append(".")
                    when {
                        connection.nativeToken?.isNotBlank() == true -> append(" Smart playlist saves are enabled.")
                        smartPlaylistAuthWarning != null -> {
                            append(" Smart playlist saves are not enabled: ")
                            append(smartPlaylistAuthWarning)
                        }
                        password.isBlank() -> append(" Smart playlist saves require editing this connection and entering your password.")
                    }
                }
                refreshLibrarySnapshot()
                loadHomeContent(provider)
                refreshPlaylists()
                refreshInternetRadioStations()
                startLibrarySync(force = !restoreSavedSession)
                checkLibraryFreshness()
            } catch (exception: Exception) {
                connectedProvider = null
                appRoute = AppRoute.Settings
                connectionStatus = exception.message ?: "Could not connect to Navidrome."
            } finally {
                isConnecting = false
            }
        }
    }

    fun openAlbumDetails(album: Album, backRouteOverride: AppRoute? = null) {
        val provider = connectedProvider ?: return
        albumDetailBackRoute = backRouteOverride ?: when (appRoute) {
            AppRoute.AlbumDetail -> albumDetailBackRoute
            AppRoute.ArtistDetail -> AppRoute.ArtistDetail
            AppRoute.Player -> lastContentRoute
            else -> appRoute
        }
        selectedAlbum = album
        selectedAlbumDetails = null
        selectedAlbumStatus = "Loading..."
        appRoute = AppRoute.AlbumDetail
        coroutineScope.launch {
            try {
                selectedAlbumDetails = sessionCache.album(provider, album.id)
                selectedAlbumStatus = null
            } catch (exception: Exception) {
                selectedAlbumStatus = exception.message ?: "Could not load album."
            }
        }
    }

    fun playAlbumDetails(shuffle: Boolean = false, index: Int = 0) {
        val provider = connectedProvider ?: return
        val tracks = selectedAlbumDetails?.tracks.orEmpty()
        if (tracks.isEmpty()) return
        stopRadioContinuation()
        clearShuffleSnapshot()
        openPlayerOnTrackStart = true
        playlistEngine.playFrom(
            scope = coroutineScope,
            provider = provider,
            tracks = if (shuffle) tracks.shuffled() else tracks,
            index = if (shuffle) 0 else index,
            quality = playbackSettings.streamQuality(playbackEngine),
            replayGainMode = playbackSettings.replayGainMode,
            callbacks = playlistCallbacks,
        )
    }

    fun playSearchTrack(index: Int) {
        val provider = connectedProvider ?: return
        val tracks = searchResults.tracks
        if (tracks.isEmpty() || index !in tracks.indices) return
        stopRadioContinuation()
        clearShuffleSnapshot()
        openPlayerOnTrackStart = true
        playlistEngine.playFrom(
            scope = coroutineScope,
            provider = provider,
            tracks = tracks,
            index = index,
            quality = playbackSettings.streamQuality(playbackEngine),
            replayGainMode = playbackSettings.replayGainMode,
            callbacks = playlistCallbacks,
        )
    }

    fun playRelatedTrack(index: Int) {
        val provider = connectedProvider ?: return
        val tracks = relatedTracks
        if (tracks.isEmpty() || index !in tracks.indices) return
        stopRadioContinuation()
        clearShuffleSnapshot()
        openPlayerOnTrackStart = true
        playlistEngine.playFrom(
            scope = coroutineScope,
            provider = provider,
            tracks = tracks,
            index = index,
            quality = playbackSettings.streamQuality(playbackEngine),
            replayGainMode = playbackSettings.replayGainMode,
            callbacks = playlistCallbacks,
        )
    }

    fun playPopularTracks(tracks: List<Track>, index: Int = 0) {
        val provider = connectedProvider ?: return
        if (tracks.isEmpty() || index !in tracks.indices) return
        stopRadioContinuation()
        clearShuffleSnapshot()
        openPlayerOnTrackStart = true
        playlistEngine.playFrom(
            scope = coroutineScope,
            provider = provider,
            tracks = tracks,
            index = index,
            quality = playbackSettings.streamQuality(playbackEngine),
            replayGainMode = playbackSettings.replayGainMode,
            callbacks = playlistCallbacks,
        )
    }

    fun addPopularTracksToQueue(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        val existingIds = playlistEngine.queue.tracks.map { it.id }.toSet()
        val newTracks = tracks.filterNot { it.id in existingIds }
        if (newTracks.isEmpty()) {
            connectionStatus = "Popular tracks are already in the queue."
        } else {
            playlistEngine.appendTracks(newTracks)
            connectionStatus = "Added ${newTracks.size} popular tracks to queue."
        }
    }

    fun downloadTracks(label: String, tracks: List<Track>) {
        val provider = connectedProvider ?: run {
            downloadStatus = "Connect to Navidrome before downloading."
            return
        }
        val sourceId = connectedSourceId ?: run {
            downloadStatus = "Connect to Navidrome before downloading."
            return
        }
        if (tracks.isEmpty()) {
            downloadStatus = "$label did not return any tracks."
            return
        }
        downloadStatus = "Downloading $label..."
        coroutineScope.launch {
            var completed = 0
            val uiContext = coroutineContext
            try {
                withContext(Dispatchers.IO) {
                    tracks.forEachIndexed { index, track ->
                        withContext(uiContext) {
                            downloadStatus = "Downloading $label (${index + 1}/${tracks.size})..."
                        }
                        sessionCache.downloadAudioTrack(
                            sourceId = sourceId,
                            provider = provider,
                            track = track,
                            quality = playbackSettings.streamQuality(playbackEngine),
                            maxDownloadBytes = cacheSettings.maxDownloadBytes,
                        )
                        completed += 1
                    }
                }
                downloadRefreshToken += 1
                downloadStatus = "Downloaded $label ($completed tracks)."
            } catch (exception: Exception) {
                downloadRefreshToken += 1
                downloadStatus = exception.message ?: "Could not download $label."
            }
        }
    }

    fun downloadTrack(track: Track) {
        downloadTracks(track.title, listOf(track))
    }

    fun downloadAlbum(album: Album) {
        val provider = connectedProvider ?: run {
            downloadStatus = "Connect to Navidrome before downloading."
            return
        }
        downloadStatus = "Loading ${album.title}..."
        coroutineScope.launch {
            try {
                val tracks = withContext(Dispatchers.IO) {
                    sessionCache.album(provider, album.id).tracks
                }
                downloadTracks(album.title, tracks)
            } catch (exception: Exception) {
                downloadStatus = exception.message ?: "Could not load ${album.title}."
            }
        }
    }

    fun downloadPlaylist(playlist: Playlist) {
        val provider = connectedProvider ?: run {
            downloadStatus = "Connect to Navidrome before downloading."
            return
        }
        downloadStatus = "Loading ${playlist.name}..."
        coroutineScope.launch {
            try {
                val tracks = withContext(Dispatchers.IO) {
                    provider.playlistTracks(playlist.id)
                }
                downloadTracks(playlist.name, tracks)
            } catch (exception: Exception) {
                downloadStatus = exception.message ?: "Could not load ${playlist.name}."
            }
        }
    }

    fun removeDownloadedTrack(download: DownloadedTrack) {
        val sourceId = connectedSourceId ?: return
        sessionCache.removeDownloadedAudio(sourceId, download.track.id, playbackSettings.streamQuality(playbackEngine))
        downloadRefreshToken += 1
        downloadStatus = "Removed ${download.track.title}."
    }

    fun playDownloadedTrack(downloads: List<DownloadedTrack>, index: Int) {
        val provider = connectedProvider ?: return
        if (downloads.isEmpty() || index !in downloads.indices) return
        stopRadioContinuation()
        clearShuffleSnapshot()
        openPlayerOnTrackStart = true
        playlistEngine.playFrom(
            scope = coroutineScope,
            provider = provider,
            tracks = downloads.map { it.track },
            index = index,
            quality = playbackSettings.streamQuality(playbackEngine),
            replayGainMode = playbackSettings.replayGainMode,
            callbacks = playlistCallbacks,
        )
    }

    fun playRadio(
        label: String,
        recentRadioStream: RecentRadioStream,
        loadTracks: suspend (RadioService) -> List<Track>,
    ) {
        val provider = connectedProvider ?: return
        val radioService = RadioService(provider)
        rememberRadioStream(recentRadioStream)
        connectionStatus = "Loading $label..."
        coroutineScope.launch {
            try {
                val tracks = withContext(Dispatchers.IO) {
                    loadTracks(radioService)
                }
                if (tracks.isEmpty()) {
                    connectionStatus = "$label did not return any tracks."
                    return@launch
                }
                connectionStatus = null
                radioSessionId += 1
                radioQueueActive = true
                clearShuffleSnapshot()
                isRadioRefilling = false
                lastRadioRefillSeedId = null
                openPlayerOnTrackStart = true
                playlistEngine.playFrom(
                    scope = coroutineScope,
                    provider = provider,
                    tracks = tracks,
                    index = 0,
                    quality = playbackSettings.streamQuality(playbackEngine),
                    replayGainMode = playbackSettings.replayGainMode,
                    callbacks = playlistCallbacks,
                )
            } catch (exception: Exception) {
                connectionStatus = exception.message ?: "Could not start $label."
            }
        }
    }

    fun appendGeneratedRadioTracks(
        radioSession: Int,
        seedTrack: Track,
        fetchedTracks: List<Track>,
    ) {
        val newTracks = generatedRadioTracksToAppend(seedTrack, fetchedTracks, playlistEngine.queue.tracks)
        if (radioQueueActive && radioSession == radioSessionId && newTracks.isNotEmpty()) {
            playlistEngine.appendTracks(
                tracks = newTracks,
                maxHistory = RadioQueueHistoryLimit,
            )
        }
    }

    fun replaceGeneratedRadioUpcomingTracks(
        radioSession: Int,
        currentTrack: Track,
        fetchedTracks: List<Track>,
    ) {
        if (radioQueueActive && radioSession == radioSessionId) {
            playlistEngine.replaceUpcomingTracks(
                currentTrack = currentTrack,
                upcomingTracks = generatedRadioUpcomingTracks(currentTrack, fetchedTracks),
                maxHistory = RadioQueueHistoryLimit,
            )
        }
    }

    fun appendGeneratedRadioUpcomingTracks(
        radioSession: Int,
        currentTrack: Track,
        fetchedTracks: List<Track>,
    ) {
        val newTracks = generatedRadioUpcomingTracksToAppend(currentTrack, fetchedTracks, playlistEngine.queue.tracks)
        if (radioQueueActive && radioSession == radioSessionId && newTracks.isNotEmpty()) {
            playlistEngine.appendTracks(
                tracks = newTracks,
                maxHistory = RadioQueueHistoryLimit,
            )
        }
    }

    fun finishRadioRefillIfCurrent(radioSession: Int) {
        if (radioSession == radioSessionId) {
            isRadioRefilling = false
        }
    }

    fun startSeededRadio(
        label: String,
        provider: NavidromeProvider,
        seedTrack: Track,
        recentRadioStream: RecentRadioStream,
        loadRest: suspend (RadioService) -> List<Track>,
    ) {
        rememberRadioStream(recentRadioStream)
        connectionStatus = null
        radioSessionId += 1
        val activeRadioSessionId = radioSessionId
        radioQueueActive = true
        clearShuffleSnapshot()
        isRadioRefilling = true
        lastRadioRefillSeedId = seedTrack.id
        openPlayerOnTrackStart = true
        nowPlayingWaveform = null
        nowPlayingWaveformStatus = "Waiting"
        nowPlayingWaveformReloadToken += 1
        playlistEngine.playFrom(
            scope = coroutineScope,
            provider = provider,
            tracks = listOf(seedTrack),
            index = 0,
            quality = playbackSettings.streamQuality(playbackEngine),
            replayGainMode = playbackSettings.replayGainMode,
            callbacks = playlistCallbacks,
        )

        coroutineScope.launch {
            try {
                val fetchedTracks = withContext(Dispatchers.IO) {
                    loadRest(RadioService(provider, count = InitialSimilarRadioCount))
                }
                appendGeneratedRadioTracks(
                    radioSession = activeRadioSessionId,
                    seedTrack = seedTrack,
                    fetchedTracks = fetchedTracks,
                )
                if (activeRadioSessionId == radioSessionId) {
                    connectionStatus = "Building $label queue..."
                }
            } catch (exception: Exception) {
                if (activeRadioSessionId == radioSessionId) {
                    connectionStatus = exception.message ?: "Could not build $label."
                }
            }
            if (activeRadioSessionId == radioSessionId) {
                isRadioRefilling = false
            }

            SimilarRadioExpansionCounts.forEach { count ->
                if (!radioQueueActive || activeRadioSessionId != radioSessionId) return@launch
                val fetchedTracks = runCatching {
                    withContext(Dispatchers.IO) {
                        loadRest(RadioService(provider, count = count))
                    }
                }.getOrElse {
                    return@forEach
                }
                appendGeneratedRadioTracks(
                    radioSession = activeRadioSessionId,
                    seedTrack = seedTrack,
                    fetchedTracks = fetchedTracks,
                )
                if (activeRadioSessionId == radioSessionId) {
                    connectionStatus = "Building $label queue (${playlistEngine.queue.tracks.size} tracks)..."
                }
            }

            if (activeRadioSessionId == radioSessionId) {
                connectionStatus = null
            }
        }
    }

    fun playPopularTracksRadio(tracks: List<Track>) {
        val provider = connectedProvider ?: return
        val seedTrack = tracks.shuffled().firstOrNull() ?: return
        startSeededRadio(
            label = "${seedTrack.artistName} popular tracks radio",
            provider = provider,
            seedTrack = seedTrack,
            recentRadioStream = RecentRadioStream(
                id = "popular:${seedTrack.artistId?.value ?: seedTrack.artistName}",
                label = "${seedTrack.artistName} popular tracks radio",
                kind = RecentRadioKind.Track,
                track = SavedTrack.fromTrack(seedTrack),
            ),
        ) { radioService ->
            coroutineScope {
                tracks.take(PopularRadioSeedLimit)
                    .map { track -> async(Dispatchers.IO) { radioService.trackRadio(track.id) } }
                    .awaitAll()
                    .flatten()
            }
        }
    }

    fun playPlaylist(playlist: Playlist, shuffle: Boolean = false) {
        val provider = connectedProvider ?: return
        connectionStatus = "Loading ${playlist.name}..."
        coroutineScope.launch {
            try {
                val loadedTracks = withContext(Dispatchers.IO) {
                    provider.playlistTracks(playlist.id)
                }
                playlistTracksById = playlistTracksById + (playlist.id to loadedTracks)
                val tracks = if (shuffle) loadedTracks.shuffled() else loadedTracks
                if (tracks.isEmpty()) {
                    connectionStatus = "${playlist.name} did not return any tracks."
                    return@launch
                }
                connectionStatus = null
                markPlaylistPlayed(playlist)
                stopRadioContinuation()
                clearShuffleSnapshot()
                openPlayerOnTrackStart = true
                playlistEngine.playFrom(
                    scope = coroutineScope,
                    provider = provider,
                    tracks = tracks,
                    index = 0,
                    quality = playbackSettings.streamQuality(playbackEngine),
                    replayGainMode = playbackSettings.replayGainMode,
                    callbacks = playlistCallbacks,
                )
            } catch (exception: Exception) {
                connectionStatus = exception.message ?: "Could not play ${playlist.name}."
            }
        }
    }

    fun openPlaylistDetails(playlist: Playlist) {
        val provider = connectedProvider ?: return
        selectedPlaylist = playlist
        selectedPlaylistTracks = emptyList()
        selectedPlaylistStatus = "Loading ${playlist.name}..."
        appRoute = AppRoute.PlaylistDetail
        coroutineScope.launch {
            try {
                refreshPlaylistDetailsFromServer(provider, playlist, showLoadingStatus = false)
            } catch (exception: Exception) {
                selectedPlaylistStatus = exception.message ?: "Could not load ${playlist.name}."
            }
        }
    }

    fun playPlaylistDetails(index: Int = 0, shuffle: Boolean = false) {
        val provider = connectedProvider ?: return
        val playlist = selectedPlaylist ?: return
        val tracks = if (shuffle) selectedPlaylistTracks.shuffled() else selectedPlaylistTracks
        if (tracks.isEmpty()) return
        markPlaylistPlayed(playlist)
        stopRadioContinuation()
        clearShuffleSnapshot()
        openPlayerOnTrackStart = true
        playlistEngine.playFrom(
            scope = coroutineScope,
            provider = provider,
            tracks = tracks,
            index = index.coerceIn(tracks.indices),
            quality = playbackSettings.streamQuality(playbackEngine),
            replayGainMode = playbackSettings.replayGainMode,
            callbacks = playlistCallbacks,
        )
    }

    fun renamePlaylist(playlist: Playlist, name: String) {
        val provider = connectedProvider ?: return
        playlistStatus = "Renaming ${playlist.name}..."
        coroutineScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    provider.renamePlaylist(playlist.id, name)
                }
                playlistPendingRename = null
                playlistStatus = null
                selectedPlaylist = selectedPlaylist?.takeIf { it.id == playlist.id }?.copy(name = name) ?: selectedPlaylist
                refreshPlaylists()
            } catch (exception: Exception) {
                playlistStatus = exception.message ?: "Could not rename playlist."
            }
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        val provider = connectedProvider ?: return
        playlistStatus = "Deleting ${playlist.name}..."
        coroutineScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    provider.deletePlaylist(playlist.id)
                }
                playlistPendingDelete = null
                if (selectedPlaylist?.id == playlist.id) {
                    selectedPlaylist = null
                    selectedPlaylistTracks = emptyList()
                    appRoute = AppRoute.Playlists
                }
                playlistTracksById = playlistTracksById - playlist.id
                playlistStatus = null
                refreshPlaylists()
            } catch (exception: Exception) {
                playlistStatus = exception.message ?: "Could not delete playlist."
            }
        }
    }

    fun playLibraryRadio() {
        playRadio(
            label = "Library radio",
            recentRadioStream = RecentRadioStream(
                id = "library",
                label = "Library radio",
                kind = RecentRadioKind.Library,
            ),
        ) { radioService ->
            radioService.libraryRadio()
        }
    }

    fun playGenreRadio(genre: Genre) {
        playRadio(
            label = "${genre.name} radio",
            recentRadioStream = RecentRadioStream(
                id = "genre:${genre.name}",
                label = "${genre.name} radio",
                kind = RecentRadioKind.Genre,
                genre = genre.name,
            ),
        ) { radioService ->
            radioService.genreRadio(genre.name)
        }
    }

    fun playDecadeRadio(fromYear: Int, toYear: Int) {
        playRadio(
            label = "$fromYear-$toYear radio",
            recentRadioStream = RecentRadioStream(
                id = "decade:$fromYear:$toYear",
                label = "$fromYear-$toYear radio",
                kind = RecentRadioKind.Decade,
                fromYear = fromYear,
                toYear = toYear,
            ),
        ) { radioService ->
            radioService.decadeRadio(fromYear, toYear)
        }
    }

    suspend fun artistRadioSeedTrack(
        provider: NavidromeProvider,
        artist: Artist,
        sourceId: String?,
    ): Track? =
        runCatching {
            sourceId?.let { localSourceId ->
                sessionCache.randomLibraryTrackForArtist(localSourceId, artist.id)?.let { return@runCatching it }
            }
            val details = sessionCache.artist(provider, artist.id)
            details.albums.shuffled().forEach { album ->
                val tracks = sessionCache.album(provider, album.id).tracks
                tracks.filter { it.artistId == artist.id }
                    .randomOrNull()
                    ?.let { return@runCatching it }
                tracks.filter { it.artistName.equals(artist.name, ignoreCase = true) }
                    .randomOrNull()
                    ?.let { return@runCatching it }
            }
            null
        }.getOrNull()

    suspend fun albumRadioSeedTrack(
        provider: NavidromeProvider,
        album: Album,
        sourceId: String?,
        loadedAlbumTracks: List<Track> = emptyList(),
    ): Track? =
        runCatching {
            loadedAlbumTracks.randomOrNull()?.let { return@runCatching it }
            sourceId?.let { localSourceId ->
                sessionCache.randomLibraryTrackForAlbum(localSourceId, album.id)?.let { return@runCatching it }
            }
            sessionCache.album(provider, album.id).tracks.randomOrNull()
        }.getOrNull()

    fun playRandomAlbumRadio() {
        val provider = connectedProvider ?: return
        connectionStatus = "Starting random album radio..."
        coroutineScope.launch {
            try {
                val album = withContext(Dispatchers.IO) {
                    provider.albumList(AlbumListType.Random, limit = 1).firstOrNull()
                } ?: run {
                    connectionStatus = "Random album radio did not find an album."
                    return@launch
                }
                val sourceId = connectedSourceId
                val seedTrack = withContext(Dispatchers.IO) {
                    albumRadioSeedTrack(provider, album, sourceId)
                } ?: run {
                    connectionStatus = "${album.title} did not return any tracks."
                    return@launch
                }
                startSeededRadio(
                    label = "${album.title} radio",
                    provider = provider,
                    seedTrack = seedTrack,
                    recentRadioStream = RecentRadioStream(
                        id = "random-album:${album.id.value}",
                        label = "${album.title} radio",
                        kind = RecentRadioKind.RandomAlbum,
                        album = SavedAlbum.fromAlbum(album),
                    ),
                ) { radioService ->
                    radioService.albumRadio(album.id)
                }
            } catch (exception: Exception) {
                connectionStatus = exception.message ?: "Could not start random album radio."
            }
        }
    }

    fun playArtistRadio(artist: Artist) {
        val provider = connectedProvider ?: return
        val sourceId = connectedSourceId
        connectionStatus = "Starting ${artist.name} radio..."
        coroutineScope.launch {
            try {
                val seedTrack = withContext(Dispatchers.IO) {
                    artistRadioSeedTrack(provider, artist, sourceId)
                } ?: run {
                    connectionStatus = "${artist.name} radio did not find a seed track."
                    return@launch
                }
                startSeededRadio(
                    label = "${artist.name} radio",
                    provider = provider,
                    seedTrack = seedTrack,
                    recentRadioStream = RecentRadioStream(
                        id = "artist:${artist.id.value}",
                        label = "${artist.name} radio",
                        kind = RecentRadioKind.Artist,
                        artist = SavedArtist.fromArtist(artist),
                    ),
                ) { radioService ->
                    radioService.artistRadio(artist.id)
                }
            } catch (exception: Exception) {
                connectionStatus = exception.message ?: "Could not start ${artist.name} radio."
            }
        }
    }

    fun playAlbumRadio(album: Album) {
        val provider = connectedProvider ?: return
        val sourceId = connectedSourceId
        val loadedAlbumTracks = if (selectedAlbum?.id == album.id || selectedAlbumDetails?.album?.id == album.id) {
            selectedAlbumDetails?.tracks.orEmpty()
        } else {
            emptyList()
        }
        connectionStatus = "Starting ${album.title} radio..."
        coroutineScope.launch {
            try {
                val seedTrack = withContext(Dispatchers.IO) {
                    albumRadioSeedTrack(provider, album, sourceId, loadedAlbumTracks)
                } ?: run {
                    connectionStatus = "${album.title} did not return any tracks."
                    return@launch
                }
                startSeededRadio(
                    label = "${album.title} radio",
                    provider = provider,
                    seedTrack = seedTrack,
                    recentRadioStream = RecentRadioStream(
                        id = "album:${album.id.value}",
                        label = "${album.title} radio",
                        kind = RecentRadioKind.Album,
                        album = SavedAlbum.fromAlbum(album),
                    ),
                ) { radioService ->
                    radioService.albumRadio(album.id, loadedAlbumTracks)
                }
            } catch (exception: Exception) {
                connectionStatus = exception.message ?: "Could not start ${album.title} radio."
            }
        }
    }

    fun playTrackRadio(track: Track) {
        val provider = connectedProvider ?: return
        startSeededRadio(
            label = "${track.title} radio",
            provider = provider,
            seedTrack = track,
            recentRadioStream = RecentRadioStream(
                id = "track:${track.id.value}",
                label = "${track.title} radio",
                kind = RecentRadioKind.Track,
                track = SavedTrack.fromTrack(track),
            ),
        ) { radioService ->
            radioService.trackRadio(track.id)
        }
    }

    fun convertCurrentTrackToRadio(track: Track) {
        val provider = connectedProvider ?: return
        val currentTrack = playlistEngine.queue.tracks.getOrNull(playlistEngine.queue.currentIndex)
        if (currentTrack?.id != track.id) {
            playTrackRadio(track)
            return
        }

        connectionStatus = "Building ${track.title} radio..."
        rememberRadioStream(
            RecentRadioStream(
                id = "track:${track.id.value}",
                label = "${track.title} radio",
                kind = RecentRadioKind.Track,
                track = SavedTrack.fromTrack(track),
            ),
        )
        radioSessionId += 1
        val activeRadioSessionId = radioSessionId
        radioQueueActive = true
        isRadioRefilling = true
        lastRadioRefillSeedId = track.id
        coroutineScope.launch {
            try {
                val fetchedTracks = withContext(Dispatchers.IO) {
                    RadioService(provider, count = InitialSimilarRadioCount).trackRadio(track.id)
                }
                replaceGeneratedRadioUpcomingTracks(activeRadioSessionId, track, fetchedTracks)
                if (activeRadioSessionId == radioSessionId) {
                    connectionStatus = "Building ${track.title} radio queue..."
                }
            } catch (exception: Exception) {
                if (activeRadioSessionId == radioSessionId) {
                    connectionStatus = exception.message ?: "Could not build ${track.title} radio."
                }
            }
            finishRadioRefillIfCurrent(activeRadioSessionId)
            SimilarRadioExpansionCounts.forEach { count ->
                if (!radioQueueActive || activeRadioSessionId != radioSessionId) return@launch
                val fetchedTracks = runCatching {
                    withContext(Dispatchers.IO) {
                        RadioService(provider, count = count).trackRadio(track.id)
                    }
                }.getOrElse {
                    return@forEach
                }
                appendGeneratedRadioUpcomingTracks(activeRadioSessionId, track, fetchedTracks)
                if (activeRadioSessionId == radioSessionId) {
                    connectionStatus = "Building ${track.title} radio queue (${playlistEngine.queue.tracks.size} tracks)..."
                }
            }
            if (activeRadioSessionId == radioSessionId) {
                connectionStatus = null
            }
        }
    }

    fun playRecentRadio(stream: RecentRadioStream) {
        when (stream.kind) {
            RecentRadioKind.Library -> playLibraryRadio()
            RecentRadioKind.RandomAlbum -> stream.album?.toAlbum()?.let { playAlbumRadio(it) } ?: playRandomAlbumRadio()
            RecentRadioKind.Genre -> stream.genre?.let { playGenreRadio(Genre(it)) }
            RecentRadioKind.Decade -> {
                val fromYear = stream.fromYear ?: return
                val toYear = stream.toYear ?: return
                playDecadeRadio(fromYear, toYear)
            }
            RecentRadioKind.Artist -> stream.artist?.toArtist()?.let { playArtistRadio(it) }
            RecentRadioKind.Album -> stream.album?.toAlbum()?.let { playAlbumRadio(it) }
            RecentRadioKind.Track -> stream.track?.toTrack()?.let { playTrackRadio(it) }
        }
    }

    fun applyTrackMetadataUpdate(updatedTrack: Track) {
        nowPlayingTrack = nowPlayingTrack?.let { track ->
            if (track.id == updatedTrack.id) updatedTrack else track
        }
        searchResults = searchResults.copy(
            tracks = searchResults.tracks.map { track ->
                if (track.id == updatedTrack.id) updatedTrack else track
            },
        )
        selectedAlbumDetails = selectedAlbumDetails?.let { details ->
            details.copy(
                tracks = details.tracks.map { track ->
                    if (track.id == updatedTrack.id) updatedTrack else track
                },
            )
        }
        playlistEngine.updateTrack(updatedTrack)
        sessionCache.updateTrack(updatedTrack)
    }

    fun toggleTrackFavorite(track: Track) {
        val provider = connectedProvider ?: return
        if (!provider.capabilities.supportsTrackFavorites) return

        coroutineScope.launch {
            try {
                val favorite = track.favoritedAtIso8601 == null
                provider.setTrackFavorite(track.id, favorite)
                applyTrackMetadataUpdate(
                    track.copy(
                        favoritedAtIso8601 = if (favorite) Instant.now().toString() else null,
                    ),
                )
            } catch (exception: Exception) {
                connectionStatus = exception.message ?: "Could not update favorite."
            }
        }
    }

    fun clearCacheData() {
        sessionCache.clearCacheData()
        connectionStatus = "Image, provider response, audio, and waveform cache cleared."
    }

    fun clearLibraryData() {
        connectedSourceId?.let { sourceId ->
            sessionCache.clearLibraryData(sourceId)
        } ?: sessionCache.clearLibraryData(null)
        librarySnapshot = LibrarySnapshot()
        connectionStatus = "Local artist, album, and track index cleared."
    }

    fun resetDatabase() {
        sessionCache.clearAll()
        settingsStore.clearConnection()
        savedConnectionForLogin = null
        mediaSourcesRevision++
        connectedProvider = null
        connectedSourceId = null
        stopRadioContinuation()
        librarySnapshot = LibrarySnapshot()
        libraryStatus = null
        homeContent = HomeContent()
        homeStatus = null
        playlistEngine.clear()
        playbackEngine.stop()
        nowPlayingTrack = null
        nowPlayingCoverArtUrl = null
        nowPlayingLyricsStatus = null
        playbackState = PlaybackState.Idle
        playbackProgress = PlaybackProgress.Unknown
        playbackQueue = PlaybackQueue()
        settingsStore.savePlaybackSession(null)
        connectionStatus = "Database reset. Saved servers were removed."
        appRoute = AppRoute.Settings
    }

    fun deleteConnection(source: SavedMediaSource) {
        sessionCache.deleteMediaSource(source.id)
        mediaSourcesRevision++

        if (connectedSourceId == source.id) {
            connectedProvider = null
            connectedSourceId = null
            stopRadioContinuation()
            librarySnapshot = LibrarySnapshot()
            libraryStatus = null
            homeContent = HomeContent()
            homeStatus = null
            playlistEngine.clear()
            playbackEngine.stop()
            nowPlayingTrack = null
            nowPlayingCoverArtUrl = null
            nowPlayingLyricsStatus = null
            playbackState = PlaybackState.Idle
            playbackProgress = PlaybackProgress.Unknown
            playbackQueue = PlaybackQueue()
            settingsStore.savePlaybackSession(null)
        }

        if (savedConnectionForLogin?.baseUrl == source.baseUrl && savedConnectionForLogin?.username == source.username) {
            savedConnectionForLogin = null
        }
        isConnectionFormOpen = false
        connectionStatus = "Deleted ${source.displayName}."
    }

    fun openExternalArtistUrl(url: String) {
        runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url))
            }
        }.onFailure { error ->
            selectedArtistSimilarArtistsStatus = "Could not open external artist page: ${error.message ?: "unknown error"}"
        }
    }

    fun findSimilarArtists(artist: Artist) {
        selectedArtistSimilarArtistsStatus = "Finding similar artists..."
        selectedArtistSimilarArtists = emptyList()
        coroutineScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    similarArtistsService.similarArtists(
                        artistName = artist.name,
                        limit = SimilarArtistsFetchLimit,
                    )
                }
            }.onSuccess { artists ->
                if (selectedArtist?.id == artist.id) {
                    selectedArtistSimilarArtists = artists.take(SimilarArtistsDisplayLimit)
                    selectedArtistSimilarArtistsStatus = if (artists.isEmpty()) {
                        "No similar artists found."
                    } else {
                        null
                    }
                }
            }.onFailure { error ->
                if (selectedArtist?.id == artist.id) {
                    selectedArtistSimilarArtistsStatus = "Similar artists unavailable: ${error.message ?: "unknown error"}"
                }
            }
        }
    }

    fun openArtistDetails(
        artist: Artist,
        backRouteOverride: AppRoute? = null,
        pushCurrentArtist: Boolean = true,
    ) {
        val provider = connectedProvider ?: return
        if (pushCurrentArtist && appRoute == AppRoute.ArtistDetail) {
            selectedArtist
                ?.takeIf { currentArtist -> currentArtist.id != artist.id }
                ?.let { currentArtist -> artistDetailBackStack = artistDetailBackStack + currentArtist }
        } else if (appRoute != AppRoute.ArtistDetail) {
            artistDetailBackStack = emptyList()
        }
        artistDetailBackRoute = backRouteOverride ?: when (appRoute) {
            AppRoute.ArtistDetail -> artistDetailBackRoute
            AppRoute.Player -> lastContentRoute
            else -> appRoute
        }
        selectedArtist = artist
        selectedArtistDetails = null
        selectedArtistPopularTracks = emptyList()
        selectedArtistPopularTracksStatus = null
        selectedArtistSimilarArtists = emptyList()
        selectedArtistSimilarArtistsStatus = null
        selectedArtistStatus = "Loading..."
        appRoute = AppRoute.ArtistDetail
        coroutineScope.launch {
            try {
                val details = sessionCache.artist(provider, artist.id)
                selectedArtistDetails = details
                selectedArtistStatus = null
                val sourceId = connectedSourceId
                if (sourceId == null) {
                    selectedArtistPopularTracksStatus = "Popular tracks unavailable: no connected media source."
                } else {
                    selectedArtistPopularTracksStatus = "Loading popular tracks..."
                    runCatching {
                        popularTracksService.popularTracks(
                            sourceId = sourceId,
                            artist = details.artist,
                            limit = PopularTracksFetchLimit,
                        )
                    }.onSuccess { matches ->
                        if (selectedArtist?.id == artist.id) {
                            selectedArtistPopularTracks = matches
                                .map { it.matchedTrack }
                                .take(PopularTracksDisplayLimit)
                            selectedArtistPopularTracksStatus = if (matches.isEmpty()) {
                                "No popular tracks matched songs in your library."
                            } else {
                                null
                            }
                        }
                    }.onFailure { error ->
                        if (selectedArtist?.id == artist.id) {
                            selectedArtistPopularTracksStatus =
                                "Popular tracks unavailable: ${error.message ?: "unknown error"}"
                        }
                    }
                }
            } catch (exception: Exception) {
                selectedArtistStatus = exception.message ?: "Could not load artist."
            }
        }
    }

    fun openTrackArtistDetails(track: Track, backRouteOverride: AppRoute = AppRoute.Player) {
        val artistId = track.artistId ?: return
        openArtistDetails(
            artist = Artist(
                id = artistId,
                name = track.artistName,
            ),
            backRouteOverride = backRouteOverride,
        )
    }

    fun closeArtistDetails() {
        val previousArtist = artistDetailBackStack.lastOrNull()
        if (previousArtist != null) {
            artistDetailBackStack = artistDetailBackStack.dropLast(1)
            openArtistDetails(
                artist = previousArtist,
                backRouteOverride = artistDetailBackRoute,
                pushCurrentArtist = false,
            )
        } else {
            appRoute = artistDetailBackRoute
        }
    }

    fun openTrackAlbumDetails(track: Track) {
        val albumId = track.albumId ?: return
        openAlbumDetails(
            album = Album(
                id = albumId,
                title = track.albumTitle ?: "Album",
                artistName = track.artistName,
                coverArtId = track.coverArtId,
                recentlyAddedAtIso8601 = null,
                releaseYear = track.albumReleaseYear,
            ),
            backRouteOverride = AppRoute.Player,
        )
    }

    fun setTrackRating(track: Track, rating: Int?) {
        val provider = connectedProvider ?: return
        if (!provider.capabilities.supportsTrackRatings) return

        coroutineScope.launch {
            try {
                provider.setTrackRating(track.id, rating)
                applyTrackMetadataUpdate(track.copy(userRating = rating))
            } catch (exception: Exception) {
                connectionStatus = exception.message ?: "Could not update rating."
            }
        }
    }

    LaunchedEffect(Unit) {
        if (savedConnection != null) {
            connectToServer(restoreSavedSession = true)
        }
    }

    LaunchedEffect(searchQuery, connectedProvider) {
        val provider = connectedProvider
        val query = searchQuery.trim()
        if (query.isEmpty()) {
            searchResults = MediaSearchResults()
            searchStatus = if (provider == null) "Connect to Navidrome to search." else null
            isSearching = false
            return@LaunchedEffect
        }
        if (provider == null) {
            searchResults = MediaSearchResults()
            searchStatus = "Connect to Navidrome to search."
            isSearching = false
            return@LaunchedEffect
        }

        delay(250)
        isSearching = true
        searchStatus = null
        try {
            searchResults = sessionCache.search(provider, query, limit = 12)
        } catch (exception: Exception) {
            searchResults = MediaSearchResults()
            searchStatus = exception.message ?: "Search failed."
        } finally {
            isSearching = false
        }
    }

    LaunchedEffect(libraryQuery, connectedSourceId) {
        libraryLimit = LibraryPageSize
        refreshLibrarySnapshot()
        libraryListState.scrollToItem(0)
    }

    LaunchedEffect(showStatsForNerds) {
        while (showStatsForNerds) {
            delay(1_000)
            statsForNerdsRefreshTick++
        }
    }

    LaunchedEffect(showStatsForNerds, appRoute, statsForNerdsRefreshTick, downloadRefreshToken, mediaSourcesRevision) {
        if (showStatsForNerds || appRoute == AppRoute.Settings || appRoute == AppRoute.Downloads) {
            cacheStats = withContext(Dispatchers.IO) { sessionCache.stats() }
        }
    }

    val statsMediaSource = connectedSourceId?.let { sessionCache.mediaSource(it) } ?: sessionCache.latestMediaSource()
    val savedMediaSources = mediaSourcesRevision.let { sessionCache.mediaSources() }
    val streamQuality = playbackSettings.streamQuality(playbackEngine)
    val currentAudioCacheMetadata = connectedSourceId
        ?.let { sourceId ->
            nowPlayingTrack?.let { track ->
                sessionCache.cachedAudioMetadata(sourceId, track.id, streamQuality)
            }
        }
    val statsForNerdsInfo = if (showStatsForNerds) StatsForNerdsInfo(
        route = appRoute.name,
        os = "${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")})",
        javaVersion = System.getProperty("java.version"),
        workingDirectory = System.getProperty("user.dir"),
        serverUrl = serverUrl,
        username = username,
        providerName = connectedProvider?.displayName ?: "Not connected",
        providerCacheNamespace = connectedProvider?.cacheNamespace ?: "Not connected",
        mediaSource = statsMediaSource?.toStats(),
        connectionStatus = connectionStatus,
        librarySync = LibrarySyncStats(
            isSyncing = isLibrarySyncing,
            status = libraryStatus ?: "Idle",
            selectedTab = libraryTab.label,
            query = libraryQuery,
            visibleArtists = librarySnapshot.artists.size,
            visibleAlbums = librarySnapshot.albums.size,
            visibleTracks = librarySnapshot.tracks.size,
        ),
        playbackEngineName = playbackEngine.name,
        playbackCapabilities = playbackEngine.capabilitiesLabel(),
        playbackEngineStats = (playbackEngine as? PlaybackEngineDiagnostics)?.statsRows().orEmpty(),
        queueSize = playbackQueue.tracks.size,
        currentQueueIndex = playbackQueue.currentIndex,
        cacheRuntime = playlistEngine.cacheRuntimeStats(),
        stream = nowPlayingTrack?.toStreamStats(
            playbackState = playbackState,
            playbackProgress = playbackProgress,
            playbackSettings = playbackSettings,
            streamQuality = streamQuality,
            waveform = nowPlayingWaveform,
            waveformStatus = nowPlayingWaveformStatus,
            cachedAudio = currentAudioCacheMetadata,
            internetRadioStation = nowPlayingInternetRadioStation,
            streamMetadata = nowPlayingStreamMetadata,
        ),
        cacheStats = cacheStats,
        providerCapabilities = connectedProvider?.capabilities?.asStatsMap().orEmpty(),
        apiCalls = (
            NavidromeApiCallHistory.recent(50).map { call ->
                ApiCallStats(
                    source = "Navidrome",
                    endpoint = "${call.method} ${call.endpoint}",
                    sanitizedUrl = call.sanitizedUrl,
                    startedAtEpochMillis = call.startedAtEpochMillis,
                    durationMillis = call.durationMillis,
                    success = call.success,
                    errorMessage = call.errorMessage,
                )
            } + DesktopPopularTracksApiCallHistory.recent(50).map { call ->
                ApiCallStats(
                    source = "Deezer",
                    endpoint = call.endpoint,
                    sanitizedUrl = call.sanitizedUrl,
                    startedAtEpochMillis = call.startedAtEpochMillis,
                    durationMillis = call.durationMillis,
                    success = call.success,
                    errorMessage = call.errorMessage,
                )
            } + DesktopLrclibApiCallHistory.recent(50).map { call ->
                ApiCallStats(
                    source = "LRCLIB",
                    endpoint = call.endpoint,
                    sanitizedUrl = call.sanitizedUrl,
                    startedAtEpochMillis = call.startedAtEpochMillis,
                    durationMillis = call.durationMillis,
                    success = call.success,
                    errorMessage = call.errorMessage,
                )
            }
        ).sortedByDescending { it.startedAtEpochMillis }.take(50),
    ) else null

    MaterialTheme(colorScheme = colorScheme) {
        statsForNerdsInfo?.let { info ->
            StatsForNerdsWindow(
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
                    if (appRoute == AppRoute.Player && nowPlayingTrack != null) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        ) {
                            NowPlayingPanel(
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
                                    playCurrentSelection()
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
                                    playbackSettings = playbackSettings
                                        .copy(volumePercent = volumePercent)
                                        .forEngine(playbackEngine)
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
                                    toggleTrackFavorite(track)
                                },
                                onTrackRatingSelected = { track, rating ->
                                    setTrackRating(track, rating)
                                },
                                onArtistSelected = { track ->
                                    openTrackArtistDetails(track)
                                },
                                onAlbumSelected = { track ->
                                    openTrackAlbumDetails(track)
                                },
                                onTrackRadioSelected = { track ->
                                    convertCurrentTrackToRadio(track)
                                },
                                onDownloadTrackSelected = { track ->
                                    downloadTrack(track)
                                },
                                onAddTrackToPlaylist = { track ->
                                    openAddToPlaylist(AddToPlaylistTarget.TrackTarget(track))
                                },
                                onInternetRadioStationSelected = { station ->
                                    playInternetRadioStation(station)
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
                                    playTrackRadio(track)
                                },
                                onUpNextTrackDownloadSelected = { track ->
                                    downloadTrack(track)
                                },
                                onUpNextTrackAddToPlaylist = { track ->
                                    openAddToPlaylist(AddToPlaylistTarget.TrackTarget(track))
                                },
                                onRelatedTrackSelected = { index ->
                                    playRelatedTrack(index)
                                },
                                onRelatedTrackRadioSelected = { track ->
                                    playTrackRadio(track)
                                },
                                onRelatedTrackDownloadSelected = { track ->
                                    downloadTrack(track)
                                },
                                onRelatedTrackAddToPlaylist = { track ->
                                    openAddToPlaylist(AddToPlaylistTarget.TrackTarget(track))
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
                                        appRoute == AppRoute.Library ||
                                            appRoute == AppRoute.Settings ||
                                            appRoute == AppRoute.AlbumDetail ||
                                            appRoute == AppRoute.ArtistDetail
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
                                    AppRoute.Player -> Unit
                                AppRoute.Home -> HomePanel(
                                    appColors = appColors,
                                    connectionStatus = homeStatus ?: connectionStatus,
                                    homeContent = homeContent,
                                    coverArtUrl = { coverArtId ->
                                        coverArtId?.let { connectedProvider?.coverArtUrl(it) }
                                    },
                                    onAlbumSelected = { album ->
                                        openAlbumDetails(album)
                                    },
                                    onAlbumRadioSelected = { album ->
                                        playAlbumRadio(album)
                                    },
                                    onAlbumDownloadSelected = { album ->
                                        downloadAlbum(album)
                                    },
                                    onAlbumAddToQueue = { album ->
                                        addTargetToQueue(AddToPlaylistTarget.AlbumTarget(album))
                                    },
                                    onAlbumAddToPlaylist = { album ->
                                        openAddToPlaylist(AddToPlaylistTarget.AlbumTarget(album))
                                    },
                                    onPlaylistSelected = { playlist ->
                                        openPlaylistDetails(playlist)
                                    },
                                    onPlaylistDownloadSelected = { playlist ->
                                        downloadPlaylist(playlist)
                                    },
                                    onPlaylistAddToQueue = { playlist ->
                                        addTargetToQueue(AddToPlaylistTarget.PlaylistTarget(playlist))
                                    },
                                    onPlaylistAddToPlaylist = { playlist ->
                                        openAddToPlaylist(AddToPlaylistTarget.PlaylistTarget(playlist))
                                    },
                                    onRecentRadioSelected = { stream ->
                                        playRecentRadio(stream)
                                    },
                                    onInternetRadioStationSelected = { station ->
                                        playInternetRadioStation(station)
                                    },
                                    onLibraryRadioSelected = {
                                        playLibraryRadio()
                                    },
                                    onRandomAlbumRadioSelected = {
                                        playRandomAlbumRadio()
                                    },
                                    onGenreRadioSelected = { genre ->
                                        playGenreRadio(genre)
                                    },
                                    onDecadeRadioSelected = { fromYear, toYear ->
                                        playDecadeRadio(fromYear, toYear)
                                    },
                                    onOpenArtistMixBuilder = {
                                        libraryTab = LibraryTab.Artists
                                        appRoute = AppRoute.Library
                                    },
                                    onOpenAlbumMixBuilder = {
                                        libraryTab = LibraryTab.Albums
                                        appRoute = AppRoute.Library
                                    },
                                )
                                AppRoute.AlbumDetail -> AlbumDetailPanel(
                                    appColors = appColors,
                                    album = selectedAlbum,
                                    albumDetails = selectedAlbumDetails,
                                    status = selectedAlbumStatus,
                                    coverArtUrl = (
                                        selectedAlbumDetails?.album?.coverArtId ?: selectedAlbum?.coverArtId
                                        )?.let { connectedProvider?.coverArtUrl(it) },
                                    popularTrackIds = selectedArtistPopularTracks.map { it.id.value }.toSet(),
                                    onBack = { appRoute = albumDetailBackRoute },
                                    onPlayAlbum = { playAlbumDetails() },
                                    onShuffleAlbum = { playAlbumDetails(shuffle = true) },
                                    onAlbumRadio = {
                                        selectedAlbumDetails?.album?.let { playAlbumRadio(it) }
                                            ?: selectedAlbum?.let { playAlbumRadio(it) }
                                    },
                                    onDownloadAlbum = {
                                        selectedAlbumDetails?.let { downloadTracks(it.album.title, it.tracks) }
                                            ?: selectedAlbum?.let { downloadAlbum(it) }
                                    },
                                    onAddAlbumToQueue = {
                                        selectedAlbumDetails?.album?.let {
                                            addTargetToQueue(AddToPlaylistTarget.AlbumTarget(it))
                                        } ?: selectedAlbum?.let {
                                            addTargetToQueue(AddToPlaylistTarget.AlbumTarget(it))
                                        }
                                    },
                                    onPlayTrack = { index -> playAlbumDetails(index = index) },
                                    onTrackRadio = { track -> playTrackRadio(track) },
                                    onDownloadTrack = { track -> downloadTrack(track) },
                                    onAddTrackToQueue = { track ->
                                        addTargetToQueue(AddToPlaylistTarget.TrackTarget(track))
                                    },
                                    onAddAlbumToPlaylist = {
                                        selectedAlbumDetails?.album?.let {
                                            openAddToPlaylist(AddToPlaylistTarget.AlbumTarget(it))
                                        } ?: selectedAlbum?.let {
                                            openAddToPlaylist(AddToPlaylistTarget.AlbumTarget(it))
                                        }
                                    },
                                    onAddTrackToPlaylist = { track ->
                                        openAddToPlaylist(AddToPlaylistTarget.TrackTarget(track))
                                    },
                                    onArtistSelected = { track ->
                                        openTrackArtistDetails(track, backRouteOverride = AppRoute.AlbumDetail)
                                    },
                                )
                                AppRoute.ArtistDetail -> ArtistDetailPanel(
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
                                    onBack = { closeArtistDetails() },
                                    onArtistRadio = { artist -> playArtistRadio(artist) },
                                    onFindSimilarArtists = { artist -> findSimilarArtists(artist) },
                                    onSimilarArtistSelected = { artist ->
                                        openArtistDetails(artist)
                                    },
                                    onSimilarArtistExternalSelected = { url -> openExternalArtistUrl(url) },
                                    onPopularTracksPlay = { tracks -> playPopularTracks(tracks) },
                                    onPopularTracksRadio = { tracks -> playPopularTracksRadio(tracks) },
                                    onPopularTracksAddToQueue = { tracks -> addPopularTracksToQueue(tracks) },
                                    onPopularTrackSelected = { track ->
                                        val index = selectedArtistPopularTracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
                                        playPopularTracks(selectedArtistPopularTracks, index)
                                    },
                                    onPopularTrackAddToQueue = { track ->
                                        addTargetToQueue(AddToPlaylistTarget.TrackTarget(track))
                                    },
                                    onAddArtistToQueue = { artist ->
                                        addTargetToQueue(AddToPlaylistTarget.ArtistTarget(artist))
                                    },
                                    onAddArtistToPlaylist = { artist ->
                                        openAddToPlaylist(AddToPlaylistTarget.ArtistTarget(artist))
                                    },
                                    onAlbumSelected = { album -> openAlbumDetails(album) },
                                    onAlbumRadioSelected = { album -> playAlbumRadio(album) },
                                    onAlbumDownloadSelected = { album -> downloadAlbum(album) },
                                    onAlbumAddToQueue = { album ->
                                        addTargetToQueue(AddToPlaylistTarget.AlbumTarget(album))
                                    },
                                    onAlbumAddToPlaylist = { album ->
                                        openAddToPlaylist(AddToPlaylistTarget.AlbumTarget(album))
                                    },
                                )
                                AppRoute.Playlists -> PlaylistsPanel(
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
                                    onPlaylistSelected = { playlist -> openPlaylistDetails(playlist) },
                                    onPlayPlaylist = { playlist, shuffle -> playPlaylist(playlist, shuffle) },
                                    onRenamePlaylist = { playlist -> playlistPendingRename = playlist },
                                    onDeletePlaylist = { playlist -> playlistPendingDelete = playlist },
                                    onDownloadPlaylist = { playlist -> downloadPlaylist(playlist) },
                                    onAddPlaylistToQueue = { playlist ->
                                        addTargetToQueue(AddToPlaylistTarget.PlaylistTarget(playlist))
                                    },
                                    onAddPlaylistToPlaylist = { playlist ->
                                        openAddToPlaylist(AddToPlaylistTarget.PlaylistTarget(playlist))
                                    },
                                    onSmartPlaylistSave = { definition -> saveSmartPlaylist(definition) },
                                    onSmartPlaylistUpdate = { playlist, definition -> updateSmartPlaylist(playlist, definition) },
                                    onSmartPlaylistLoad = { playlist -> loadSmartPlaylistDefinition(playlist) },
                                )
                                AppRoute.PlaylistDetail -> PlaylistDetailPanel(
                                    appColors = appColors,
                                    playlist = selectedPlaylist,
                                    tracks = selectedPlaylistTracks,
                                    status = selectedPlaylistStatus ?: playlistStatus,
                                    playlistCoverArtUrl = selectedPlaylist?.coverArtId?.let { connectedProvider?.coverArtUrl(it) },
                                    coverArtUrl = { coverArtId ->
                                        coverArtId?.let { connectedProvider?.coverArtUrl(it) }
                                    },
                                    onBack = { appRoute = AppRoute.Playlists },
                                    onPlayPlaylist = { playPlaylistDetails() },
                                    onShufflePlaylist = { playPlaylistDetails(shuffle = true) },
                                    onRenamePlaylist = { selectedPlaylist?.let { playlistPendingRename = it } },
                                    onDeletePlaylist = { selectedPlaylist?.let { playlistPendingDelete = it } },
                                    onDownloadPlaylist = {
                                        selectedPlaylist?.let { downloadTracks(it.name, selectedPlaylistTracks) }
                                    },
                                    onAddPlaylistToQueue = {
                                        selectedPlaylist?.let {
                                            addTargetToQueue(AddToPlaylistTarget.PlaylistTarget(it))
                                        }
                                    },
                                    onAddPlaylistToPlaylist = {
                                        selectedPlaylist?.let {
                                            openAddToPlaylist(AddToPlaylistTarget.PlaylistTarget(it))
                                        }
                                    },
                                    onPlayTrack = { index -> playPlaylistDetails(index = index) },
                                    onTrackRadio = { track -> playTrackRadio(track) },
                                    onDownloadTrack = { track -> downloadTrack(track) },
                                    onAddTrackToQueue = { track ->
                                        addTargetToQueue(AddToPlaylistTarget.TrackTarget(track))
                                    },
                                    onAddTrackToPlaylist = { track ->
                                        openAddToPlaylist(AddToPlaylistTarget.TrackTarget(track))
                                    },
                                )
                                    AppRoute.Library -> {
                                        LibraryListLoadMoreEffect(
                                            selectedTab = libraryTab,
                                            snapshot = librarySnapshot,
                                            listState = libraryListState,
                                            onLoadMore = { loadMoreLibraryRows() },
                                        )
                                        LibraryPanel(
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
                                                refreshLibrarySnapshot()
                                                coroutineScope.launch {
                                                    libraryListState.scrollToItem(0)
                                                }
                                            },
                                            onLoadMore = { loadMoreLibraryRows() },
                                            onJumpToLetter = { letter -> jumpLibraryToLetter(letter) },
                                            onArtistSelected = { artist -> openArtistDetails(artist) },
                                            onArtistRadioSelected = { artist -> playArtistRadio(artist) },
                                            onArtistAddToQueue = { artist ->
                                                addTargetToQueue(AddToPlaylistTarget.ArtistTarget(artist))
                                            },
                                            onArtistAddToPlaylist = { artist ->
                                                openAddToPlaylist(AddToPlaylistTarget.ArtistTarget(artist))
                                            },
                                            onAlbumSelected = { album -> openAlbumDetails(album) },
                                            onAlbumRadioSelected = { album -> playAlbumRadio(album) },
                                            onAlbumDownloadSelected = { album -> downloadAlbum(album) },
                                            onAlbumAddToQueue = { album ->
                                                addTargetToQueue(AddToPlaylistTarget.AlbumTarget(album))
                                            },
                                            onAlbumAddToPlaylist = { album ->
                                                openAddToPlaylist(AddToPlaylistTarget.AlbumTarget(album))
                                            },
                                            onRefreshLibrary = { startLibrarySync(force = true) },
                                        )
                                    }
                                    AppRoute.Search -> SearchPanel(
                                        appColors = appColors,
                                        query = searchQuery,
                                        results = searchResults,
                                        status = searchStatus,
                                        isSearching = isSearching,
                                        coverArtUrl = { coverArtId ->
                                            coverArtId?.let { connectedProvider?.coverArtUrl(it) }
                                        },
                                        onQueryChanged = {
                                            searchQuery = it
                                            settingsStore.saveSearchSettings(SearchSettings(query = it))
                                        },
                                        onArtistSelected = { artist -> openArtistDetails(artist) },
                                        onArtistRadioSelected = { artist -> playArtistRadio(artist) },
                                        onArtistAddToQueue = { artist ->
                                            addTargetToQueue(AddToPlaylistTarget.ArtistTarget(artist))
                                        },
                                        onArtistAddToPlaylist = { artist ->
                                            openAddToPlaylist(AddToPlaylistTarget.ArtistTarget(artist))
                                        },
                                        onAlbumSelected = { album -> openAlbumDetails(album) },
                                        onAlbumRadioSelected = { album -> playAlbumRadio(album) },
                                        onAlbumDownloadSelected = { album -> downloadAlbum(album) },
                                        onAlbumAddToQueue = { album ->
                                            addTargetToQueue(AddToPlaylistTarget.AlbumTarget(album))
                                        },
                                        onAlbumAddToPlaylist = { album ->
                                            openAddToPlaylist(AddToPlaylistTarget.AlbumTarget(album))
                                        },
                                        onTrackSelected = { index -> playSearchTrack(index) },
                                        onTrackRadioSelected = { index ->
                                            searchResults.tracks.getOrNull(index)?.let { playTrackRadio(it) }
                                        },
                                        onTrackDownloadSelected = { index ->
                                            searchResults.tracks.getOrNull(index)?.let { downloadTrack(it) }
                                        },
                                        onTrackAddToQueue = { index ->
                                            searchResults.tracks.getOrNull(index)?.let {
                                                addTargetToQueue(AddToPlaylistTarget.TrackTarget(it))
                                            }
                                        },
                                        onTrackAddToPlaylist = { index ->
                                            searchResults.tracks.getOrNull(index)?.let {
                                                openAddToPlaylist(AddToPlaylistTarget.TrackTarget(it))
                                            }
                                        },
                                    )
                                    AppRoute.InternetRadio -> InternetRadioPanel(
                                        appColors = appColors,
                                        stations = internetRadioStations,
                                        status = internetRadioStatus ?: connectionStatus,
                                        onPlayStation = { station -> playInternetRadioStation(station) },
                                        onNewStation = { isNewInternetRadioStationDialogOpen = true },
                                        onEditStation = { station -> internetRadioStationPendingEdit = station },
                                        onDeleteStation = { station -> internetRadioStationPendingDelete = station },
                                    )
                                    AppRoute.Downloads -> {
                                        val downloads = remember(
                                            connectedSourceId,
                                            downloadRefreshToken,
                                            cacheStats.downloadCount,
                                        ) {
                                            connectedSourceId
                                                ?.let { sessionCache.downloadedTracks(it) }
                                                .orEmpty()
                                        }
                                        DownloadsPanel(
                                            appColors = appColors,
                                            downloads = downloads,
                                            status = downloadStatus ?: connectionStatus,
                                            downloadBytes = cacheStats.downloadBytes,
                                            maxDownloadBytes = cacheSettings.maxDownloadBytes,
                                            coverArtUrl = { coverArtId ->
                                                coverArtId?.let { connectedProvider?.coverArtUrl(it) }
                                            },
                                            onTrackSelected = { index -> playDownloadedTrack(downloads, index) },
                                            onRemoveDownload = { download -> removeDownloadedTrack(download) },
                                            onTrackAddToPlaylist = { download ->
                                                openAddToPlaylist(AddToPlaylistTarget.TrackTarget(download.track))
                                            },
                                        )
                                    }
                                    AppRoute.Settings -> SettingsPanel(
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
                                        onConnect = { connectToServer() },
                                        onNewConnection = {
                                            savedConnectionForLogin = null
                                            isConnectionFormOpen = true
                                            serverUrl = ""
                                            connectionName = ""
                                            username = ""
                                            password = ""
                                            insecureSkipTlsVerification = false
                                            customCertificatePath = ""
                                            clientCertificateKeyStorePath = ""
                                            clientCertificateKeyStorePassword = ""
                                            connectionStatus = null
                                        },
                                        onEditConnection = { source ->
                                            val connection = source.toNavidromeConnection()
                                            savedConnectionForLogin = connection
                                            isConnectionFormOpen = true
                                            serverUrl = connection.baseUrl
                                            connectionName = connection.displayName
                                                ?.takeUnless { it == connection.normalizedBaseUrl }
                                                .orEmpty()
                                            username = connection.username
                                            password = ""
                                            insecureSkipTlsVerification = connection.tlsSettings.insecureSkipTlsVerification
                                            customCertificatePath = connection.tlsSettings.customCertificatePath.orEmpty()
                                            clientCertificateKeyStorePath =
                                                connection.tlsSettings.clientCertificateKeyStorePath.orEmpty()
                                            clientCertificateKeyStorePassword =
                                                connection.tlsSettings.clientCertificateKeyStorePassword.orEmpty()
                                            connectionStatus = "Editing saved connection. Leave password blank to reuse it."
                                        },
                                        onConnectSavedConnection = { source ->
                                            val connection = source.toNavidromeConnection()
                                            savedConnectionForLogin = connection
                                            isConnectionFormOpen = false
                                            serverUrl = connection.baseUrl
                                            connectionName = connection.displayName
                                                ?.takeUnless { it == connection.normalizedBaseUrl }
                                                .orEmpty()
                                            username = connection.username
                                            password = ""
                                            insecureSkipTlsVerification = connection.tlsSettings.insecureSkipTlsVerification
                                            customCertificatePath = connection.tlsSettings.customCertificatePath.orEmpty()
                                            clientCertificateKeyStorePath =
                                                connection.tlsSettings.clientCertificateKeyStorePath.orEmpty()
                                            clientCertificateKeyStorePassword =
                                                connection.tlsSettings.clientCertificateKeyStorePassword.orEmpty()
                                            connectToServer()
                                        },
                                        onDeleteConnection = { source -> deleteConnection(source) },
                                        onCancelConnectionForm = { isConnectionFormOpen = false },
                                        onPlaybackSettingsChanged = { settings ->
                                            playbackSettings = settings.forEngine(playbackEngine)
                                            settingsStore.savePlaybackSettings(playbackSettings)
                                        },
                                        onCacheSettingsChanged = { settings ->
                                            cacheSettings = settings.normalized()
                                            settingsStore.saveCacheSettings(cacheSettings)
                                        },
                                        onOpenStatsForNerds = { showStatsForNerds = true },
                                        onClearCache = { clearCacheData() },
                                        onClearLibrary = { clearLibraryData() },
                                        onRefreshLibrary = { startLibrarySync(force = true) },
                                        onResetDatabase = { resetDatabase() },
                                    )
                                }
                            }
                        }
                        addToPlaylistTarget?.let { target ->
                            AddToPlaylistDialog(
                                appColors = appColors,
                                target = target,
                                playlists = playlists,
                                status = addToPlaylistStatus,
                                onDismiss = {
                                    addToPlaylistTarget = null
                                    addToPlaylistStatus = null
                                },
                                onAddToExisting = { playlist ->
                                    addTargetToPlaylist(target, playlist = playlist)
                                },
                                onCreateAndAdd = { name ->
                                    addTargetToPlaylist(target, playlist = null, newPlaylistName = name)
                                },
                            )
                        }
                        playlistPendingRename?.let { playlist ->
                            RenamePlaylistDialog(
                                playlist = playlist,
                                onDismiss = { playlistPendingRename = null },
                                onConfirm = { name -> renamePlaylist(playlist, name) },
                            )
                        }
                        playlistPendingDelete?.let { playlist ->
                            DeletePlaylistDialog(
                                playlist = playlist,
                                onDismiss = { playlistPendingDelete = null },
                                onConfirm = { deletePlaylist(playlist) },
                            )
                        }
                        if (isNewInternetRadioStationDialogOpen) {
                            InternetRadioStationDialog(
                                initialStation = null,
                                onDismiss = { isNewInternetRadioStationDialogOpen = false },
                                onConfirm = { station -> saveInternetRadioStation(station) },
                            )
                        }
                        internetRadioStationPendingEdit?.let { station ->
                            InternetRadioStationDialog(
                                initialStation = station,
                                onDismiss = { internetRadioStationPendingEdit = null },
                                onConfirm = { updatedStation -> saveInternetRadioStation(updatedStation) },
                            )
                        }
                        internetRadioStationPendingDelete?.let { station ->
                            DeleteInternetRadioStationDialog(
                                station = station,
                                onDismiss = { internetRadioStationPendingDelete = null },
                                onConfirm = { deleteInternetRadioStation(station) },
                            )
                        }
                        if (nowPlayingTrack != null) {
                            MiniPlayerPanel(
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
                                    playCurrentSelection()
                                },
                                onPrevious = {
                                    handlePreviousButton()
                                },
                                onNext = {
                                    openPlayerOnTrackStart = false
                                    playlistEngine.next(coroutineScope)
                                },
                                onOpenPlayer = {
                                    appRoute = AppRoute.Player
                                },
                            )
                        }
                        BottomNavigationBar(
                            appColors = appColors,
                            selectedRoute = when (appRoute) {
                                AppRoute.AlbumDetail -> if (albumDetailBackRoute == AppRoute.ArtistDetail) {
                                    artistDetailBackRoute
                                } else {
                                    albumDetailBackRoute
                                }
                                AppRoute.ArtistDetail -> artistDetailBackRoute
                                AppRoute.PlaylistDetail -> AppRoute.Playlists
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

private data class NowPlayingAnalysis(
    val waveform: AudioWaveform?,
    val waveformStatus: String,
    val audioTags: List<AudioTag>,
    val lyrics: Lyrics?,
)
