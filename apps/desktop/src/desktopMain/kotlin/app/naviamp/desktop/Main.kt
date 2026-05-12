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
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.Genre
import app.naviamp.domain.Lyrics
import app.naviamp.domain.Playlist
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.desktop.playback.CrossfadeSettings
import app.naviamp.desktop.playback.PlaybackEngine
import app.naviamp.desktop.playback.PlaybackEngineFactory
import app.naviamp.desktop.playback.PlaybackProgress
import app.naviamp.desktop.playback.PlaybackQueue
import app.naviamp.desktop.playback.PlaybackState
import app.naviamp.desktop.playback.PlaybackTrace
import app.naviamp.desktop.playback.PlaylistCallbacks
import app.naviamp.desktop.playback.PlaylistEngine
import app.naviamp.desktop.playback.ReplayGainMode
import app.naviamp.desktop.playback.label
import app.naviamp.desktop.playback.mergeWith
import app.naviamp.desktop.settings.DesktopSettingsStore
import app.naviamp.desktop.settings.NavigationSettings
import app.naviamp.desktop.settings.PlaybackSettings
import app.naviamp.desktop.settings.PlaybackSessionSettings
import app.naviamp.desktop.settings.SearchSettings
import app.naviamp.desktop.settings.WindowSettings
import app.naviamp.domain.provider.AlbumListType
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.provider.navidrome.NavidromeApiCallHistory
import app.naviamp.provider.navidrome.NavidromeConnection
import app.naviamp.provider.navidrome.NavidromeProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Taskbar
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
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
            NaviampApp(
                playbackEngine = playbackEngine,
                settingsStore = settingsStore,
            )
        }
    }
}

private fun configureDesktopApplicationName() {
    System.setProperty("compose.application.name", "Naviamp")
    System.setProperty("apple.awt.application.name", "Naviamp")
    System.setProperty("sun.awt.application.name", "Naviamp")
}

private fun configureDesktopIcon() {
    runCatching {
        if (!Taskbar.isTaskbarSupported()) return
        val taskbar = Taskbar.getTaskbar()
        if (!taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) return
        val iconUrl = Thread.currentThread().contextClassLoader.getResource("icons/naviamp.png") ?: return
        taskbar.iconImage = ImageIO.read(iconUrl)
    }
}

private fun configureDesktopAppearance() {
    if (System.getProperty("os.name").contains("Mac", ignoreCase = true)) {
        System.setProperty("apple.awt.application.appearance", "system")
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
    val savedNavigation = remember { settingsStore.loadNavigationSettings() }
    val savedSearch = remember { settingsStore.loadSearchSettings() }
    var connectedSourceId by remember { mutableStateOf(savedMediaSource?.id) }
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
    val restoredTracks = remember(savedPlaybackSession) { savedPlaybackSession?.toTracks().orEmpty() }
    val restoredTrack = remember(savedPlaybackSession) { savedPlaybackSession?.currentTrack() }
    var serverUrl by remember { mutableStateOf(savedConnection?.baseUrl.orEmpty()) }
    var username by remember { mutableStateOf(savedConnection?.username.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var savedConnectionForLogin by remember { mutableStateOf(savedConnection) }
    var isConnecting by remember { mutableStateOf(false) }
    var connectionStatus by remember { mutableStateOf<String?>(null) }
    var connectedProvider by remember { mutableStateOf<NavidromeProvider?>(null) }
    var homeContent by remember { mutableStateOf(HomeContent()) }
    var homeStatus by remember { mutableStateOf<String?>(null) }
    var selectedAlbum by remember { mutableStateOf<Album?>(null) }
    var selectedAlbumDetails by remember { mutableStateOf<AlbumDetails?>(null) }
    var selectedAlbumStatus by remember { mutableStateOf<String?>(null) }
    var albumDetailBackRoute by remember { mutableStateOf(AppRoute.Home) }
    var selectedArtist by remember { mutableStateOf<Artist?>(null) }
    var selectedArtistDetails by remember { mutableStateOf<ArtistDetails?>(null) }
    var selectedArtistStatus by remember { mutableStateOf<String?>(null) }
    var artistDetailBackRoute by remember { mutableStateOf(AppRoute.Search) }
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
    var nowPlayingAudioTags by remember { mutableStateOf<List<AudioTag>?>(null) }
    var nowPlayingLyrics by remember { mutableStateOf<Lyrics?>(null) }
    var relatedTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var playbackState by remember { mutableStateOf<PlaybackState>(PlaybackState.Idle) }
    var playbackProgress by remember { mutableStateOf(PlaybackProgress.Unknown) }
    var playbackQueue by remember {
        mutableStateOf(
            if (savedPlaybackSession != null && savedPlaybackSession.currentIndex in restoredTracks.indices) {
                PlaybackQueue(
                    tracks = restoredTracks,
                    currentIndex = savedPlaybackSession.currentIndex,
                )
            } else {
                PlaybackQueue()
            },
        )
    }
    var playbackSettings by remember {
        mutableStateOf(settingsStore.loadPlaybackSettings().forEngine(playbackEngine))
    }
    var showStatsForNerds by remember { mutableStateOf(false) }
    var openPlayerOnTrackStart by remember { mutableStateOf(false) }
    var radioQueueActive by remember { mutableStateOf(false) }
    var isRadioRefilling by remember { mutableStateOf(false) }
    var lastRadioRefillSeedId by remember { mutableStateOf<String?>(null) }
    var radioSessionId by remember { mutableStateOf(0) }
    var restoredPlaybackPositionSeconds by remember {
        mutableStateOf(savedPlaybackSession?.positionSeconds?.takeIf { it > 0.0 })
    }
    var lastSavedPlaybackPositionSeconds by remember {
        mutableStateOf(savedPlaybackSession?.positionSeconds?.takeIf { it > 0.0 })
    }
    var pendingSeekPositionSeconds by remember { mutableStateOf<Double?>(null) }
    var pendingSeekIssuedAtMillis by remember { mutableStateOf<Long?>(null) }
    var playReportSessionId by remember { mutableStateOf(0) }
    var submittedPlayReportSessionId by remember { mutableStateOf<Int?>(null) }
    var targetPlayerColors by remember {
        mutableStateOf(PlayerColors.from(AlbumPalette.fallback(appColors.albumArtPlaceholder), appColors))
    }
    val targetBackgroundColors = if (nowPlayingTrack != null) {
        targetPlayerColors
    } else {
        PlayerColors.solid(appColors.background)
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

    LaunchedEffect(playbackEngine, playbackSettings.crossfadeDurationSeconds) {
        val crossfadeDurationSeconds = playbackSettings.crossfadeDurationSeconds.coerceIn(0, 12)
        playlistEngine.setCrossfadeSettings(
            CrossfadeSettings(
                enabled = crossfadeDurationSeconds > 0,
                durationSeconds = crossfadeDurationSeconds,
            ),
        )
    }

    LaunchedEffect(playbackEngine, playbackSettings.volumePercent) {
        playbackEngine.setVolume(playbackSettings.volumePercent.coerceIn(0, 100))
    }

    LaunchedEffect(playbackSettings.debugLoggingEnabled) {
        PlaybackTrace.setDefaultEnabled(playbackSettings.debugLoggingEnabled)
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
        if (appRoute == AppRoute.Player || appRoute == AppRoute.AlbumDetail || appRoute == AppRoute.ArtistDetail) {
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
            PlaybackSessionSettings.fromTracks(
                tracks = queue.tracks,
                currentIndex = queue.currentIndex,
                positionSeconds = positionSeconds,
            ),
        )
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
        val durationSeconds = playbackProgress.durationSeconds ?: nowPlayingTrack?.durationSeconds?.toDouble()
        val seekProgress = PlaybackProgress(
            positionSeconds = positionSeconds,
            durationSeconds = durationSeconds,
        )
        pendingSeekPositionSeconds = positionSeconds
        pendingSeekIssuedAtMillis = System.currentTimeMillis()
        playbackProgress = seekProgress
        maybeSavePlaybackPosition(seekProgress)
        playbackEngine.seek(positionSeconds)
    }

    fun reportNowPlaying(track: Track) {
        val provider = connectedProvider ?: return
        if (!provider.capabilities.supportsPlayReporting) return
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
        if (!radioQueueActive || isRadioRefilling) return
        val remaining = queue.tracks.size - queue.currentIndex - 1
        if (remaining > RadioRefillThreshold) return
        val seedTrack = queue.tracks.getOrNull(queue.currentIndex) ?: return
        if (lastRadioRefillSeedId == seedTrack.id.value) return
        val provider = connectedProvider ?: return

        isRadioRefilling = true
        lastRadioRefillSeedId = seedTrack.id.value
        val activeRadioSessionId = radioSessionId
        coroutineScope.launch {
            try {
                val fetchedTracks = withContext(Dispatchers.IO) {
                    provider.trackRadio(seedTrack.id, count = RadioRefillCount)
                }
                val existingTrackIds = playlistEngine.queue.tracks.map { it.id }.toSet()
                val newTracks = fetchedTracks.filterNot { track ->
                    track.id in existingTrackIds
                }
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
            if (
                pendingSeek != null &&
                pendingSeekIssuedAt != null &&
                progress.positionSeconds != null &&
                abs(progress.positionSeconds - pendingSeek) > PendingSeekToleranceSeconds &&
                System.currentTimeMillis() - pendingSeekIssuedAt < PendingSeekStaleProgressWindowMillis
            ) {
                return@progressChanged
            }
            if (
                pendingSeek != null &&
                (progress.positionSeconds == null ||
                    pendingSeekIssuedAt == null ||
                    abs(progress.positionSeconds - pendingSeek) <= PendingSeekToleranceSeconds ||
                    System.currentTimeMillis() - pendingSeekIssuedAt >= PendingSeekStaleProgressWindowMillis)
            ) {
                pendingSeekPositionSeconds = null
                pendingSeekIssuedAtMillis = null
            }
            val mergedProgress = progress.mergeWith(playbackProgress)
            playbackProgress = mergedProgress
            maybeSavePlaybackPosition(mergedProgress)
            maybeReportPlayed(mergedProgress)
        },
    )

    LaunchedEffect(
        nowPlayingTrack?.id,
        connectedSourceId,
        connectedProvider,
        playbackEngine,
        nowPlayingWaveformReloadToken,
        cacheSettings.audioCachingEnabled,
    ) {
        val track = nowPlayingTrack ?: run {
            nowPlayingWaveform = null
            nowPlayingWaveformStatus = "No track"
            return@LaunchedEffect
        }
        val sourceId = connectedSourceId ?: run {
            nowPlayingWaveform = null
            nowPlayingWaveformStatus = "No source"
            return@LaunchedEffect
        }
        val provider = connectedProvider ?: run {
            nowPlayingWaveform = null
            nowPlayingWaveformStatus = "No provider"
            return@LaunchedEffect
        }
        val quality = playbackEngine.streamQuality()
        nowPlayingWaveformStatus = "Loading"

        val waveformTagsAndLyrics = withContext(Dispatchers.IO) {
            runCatching {
                val downloadedFile = sessionCache.downloadedAudioFile(sourceId, track.id, quality)
                val cachedFile = if (downloadedFile == null && cacheSettings.audioCachingEnabled) {
                    sessionCache.cacheAudioTrack(
                        sourceId = sourceId,
                        provider = provider,
                        track = track,
                        quality = quality,
                    )
                } else {
                    sessionCache.cachedAudioFile(sourceId, track.id, quality)
                }
                val audioPath = downloadedFile?.path ?: cachedFile?.path
                val cachedWaveform = if (audioPath != null) sessionCache.cachedAudioWaveform(
                    sourceId = sourceId,
                    trackId = track.id,
                    quality = quality,
                ) else null
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
                    else -> "Unavailable"
                }
                val tags = audioPath?.let { AudioTagReader().read(it) }.orEmpty()
                val lyrics = sessionCache.providerLyrics(sourceId, provider, track.id)
                    ?: lyricsFromAudioTags(tags)
                NowPlayingAnalysis(waveform, waveformStatus, tags, lyrics)
            }.getOrNull()
        }
        nowPlayingWaveform = waveformTagsAndLyrics?.waveform
        nowPlayingWaveformStatus = waveformTagsAndLyrics?.waveformStatus ?: "Unavailable"
        nowPlayingAudioTags = waveformTagsAndLyrics?.audioTags
        nowPlayingLyrics = waveformTagsAndLyrics?.lyrics
    }

    LaunchedEffect(nowPlayingTrack?.id, connectedSourceId) {
        val track = nowPlayingTrack
        val sourceId = connectedSourceId
        if (track == null || sourceId == null) {
            relatedTracks = emptyList()
            return@LaunchedEffect
        }
        relatedTracks = withContext(Dispatchers.IO) {
            sessionCache.relatedLibraryTracks(sourceId, track)
        }
    }

    fun refreshLibrarySnapshot() {
        val sourceId = connectedSourceId
        if (sourceId == null) {
            librarySnapshot = LibrarySnapshot()
            libraryStatus = "Connect to Navidrome to import your library."
            return
        }
        librarySnapshot = if (libraryQuery.isBlank()) {
            sessionCache.librarySnapshot(sourceId, limit = libraryLimit.toLong())
        } else {
            sessionCache.searchLibrary(sourceId, libraryQuery, limit = libraryLimit.toLong())
        }
    }

    fun loadMoreLibraryRows() {
        val visibleCount = when (libraryTab) {
            LibraryTab.Artists -> librarySnapshot.artists.size
            LibraryTab.Albums -> librarySnapshot.albums.size
        }
        if (visibleCount < libraryLimit) return
        libraryLimit += LibraryPageSize
        refreshLibrarySnapshot()
    }

    fun jumpLibraryToLetter(letter: Char) {
        val sourceId = connectedSourceId ?: return
        if (libraryQuery.isNotBlank()) return
        val offset = sessionCache.libraryOffsetForLetter(sourceId, libraryTab, letter).toInt()
        libraryLimit = ((offset / LibraryPageSize) + 1) * LibraryPageSize
        refreshLibrarySnapshot()
        coroutineScope.launch {
            libraryListState.scrollToItem((offset + 1).coerceAtLeast(0))
        }
    }

    fun loadHomeContent(provider: NavidromeProvider) {
        homeStatus = "Loading home..."
        coroutineScope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    val genres = runCatching { provider.genres(limit = 12) }.getOrDefault(emptyList())
                    val genreSpotlight = genres.firstOrNull()
                    val decadeFromYear = HomeDecadeFromYear
                    val decadeToYear = HomeDecadeToYear
                    HomeContent(
                        recentlyAddedAlbums = runCatching {
                            provider.albumList(AlbumListType.Newest, limit = 8)
                        }.getOrDefault(emptyList()),
                        mixAlbums = runCatching {
                            provider.albumList(AlbumListType.Random, limit = 8)
                        }.getOrDefault(emptyList()),
                        recentAlbums = runCatching {
                            provider.albumList(AlbumListType.Recent, limit = 6)
                        }.getOrDefault(emptyList()),
                        frequentAlbums = runCatching {
                            provider.albumList(AlbumListType.Frequent, limit = 6)
                        }.getOrDefault(emptyList()),
                        randomAlbums = runCatching {
                            provider.albumList(AlbumListType.Random, limit = 6)
                        }.getOrDefault(emptyList()),
                        playlists = runCatching {
                            provider.playlists(limit = 6)
                        }.getOrDefault(emptyList()),
                        genres = genres,
                        genreSpotlight = genreSpotlight,
                        genreSpotlightAlbums = genreSpotlight?.let { genre ->
                            runCatching {
                                provider.albumsByGenre(genre.name, limit = 6)
                            }.getOrDefault(emptyList())
                        }.orEmpty(),
                        decadeLabel = HomeDecadeLabel,
                        decadeFromYear = decadeFromYear,
                        decadeToYear = decadeToYear,
                        decadeAlbums = runCatching {
                            provider.albumsByYear(decadeFromYear, decadeToYear, limit = 6)
                        }.getOrDefault(emptyList()),
                    )
                }
                homeContent = content
                homeStatus = null
            } catch (exception: Exception) {
                homeStatus = exception.message ?: "Could not load home."
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
            playlistEngine.clear()
            playbackEngine.stop()
            nowPlayingTrack = null
            nowPlayingCoverArtUrl = null
            playbackState = PlaybackState.Idle
            playbackProgress = PlaybackProgress.Unknown
            playbackQueue = PlaybackQueue()
            settingsStore.savePlaybackSession(null)
        }

        coroutineScope.launch {
            try {
                val connection = savedConnectionForLogin
                    ?.takeIf { it.baseUrl == serverUrl && it.username == username && password.isBlank() }
                    ?: NavidromeConnection.fromPassword(
                        baseUrl = serverUrl,
                        username = username,
                        password = password,
                    )
                val provider = NavidromeProvider(connection)
                val validation = provider.validateConnection()
                if (!restoreSavedSession) {
                    sessionCache.clearProviderData()
                }
                connectedProvider = provider
                connectedSourceId = sessionCache.upsertNavidromeSource(connection, provider).id
                if (restoreSavedSession && savedPlaybackSession != null) {
                    val tracks = savedPlaybackSession.toTracks()
                    val currentTrack = savedPlaybackSession.currentTrack()
                    if (tracks.isNotEmpty() && currentTrack != null) {
                        playlistEngine.restore(
                            provider = provider,
                            tracks = tracks,
                            index = savedPlaybackSession.currentIndex,
                            quality = playbackEngine.streamQuality(),
                            replayGainMode = playbackSettings.replayGainMode,
                            callbacks = playlistCallbacks,
                        )
                        nowPlayingTrack = currentTrack
                        nowPlayingCoverArtUrl = currentTrack.coverArtId?.let { provider.coverArtUrl(it) }
                        playbackState = PlaybackState.Idle
                    }
                }
                settingsStore.clearConnection()
                savedConnectionForLogin = connection
                password = ""
                if (appRoute == AppRoute.Settings) {
                    appRoute = AppRoute.Home
                }
                connectionStatus = buildString {
                    append("Connected")
                    validation.serverVersion?.let { append(" to Navidrome $it") }
                    append(".")
                }
                refreshLibrarySnapshot()
                loadHomeContent(provider)
                startLibrarySync(force = !restoreSavedSession)
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
        openPlayerOnTrackStart = true
        playlistEngine.playFrom(
            scope = coroutineScope,
            provider = provider,
            tracks = if (shuffle) tracks.shuffled() else tracks,
            index = if (shuffle) 0 else index,
            quality = playbackEngine.streamQuality(),
            replayGainMode = playbackSettings.replayGainMode,
            callbacks = playlistCallbacks,
        )
    }

    fun playSearchTrack(index: Int) {
        val provider = connectedProvider ?: return
        val tracks = searchResults.tracks
        if (tracks.isEmpty() || index !in tracks.indices) return
        stopRadioContinuation()
        openPlayerOnTrackStart = true
        playlistEngine.playFrom(
            scope = coroutineScope,
            provider = provider,
            tracks = tracks,
            index = index,
            quality = playbackEngine.streamQuality(),
            replayGainMode = playbackSettings.replayGainMode,
            callbacks = playlistCallbacks,
        )
    }

    fun playRelatedTrack(index: Int) {
        val provider = connectedProvider ?: return
        val tracks = relatedTracks
        if (tracks.isEmpty() || index !in tracks.indices) return
        stopRadioContinuation()
        openPlayerOnTrackStart = true
        playlistEngine.playFrom(
            scope = coroutineScope,
            provider = provider,
            tracks = tracks,
            index = index,
            quality = playbackEngine.streamQuality(),
            replayGainMode = playbackSettings.replayGainMode,
            callbacks = playlistCallbacks,
        )
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
                            quality = playbackEngine.streamQuality(),
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
        sessionCache.removeDownloadedAudio(sourceId, download.track.id, playbackEngine.streamQuality())
        downloadRefreshToken += 1
        downloadStatus = "Removed ${download.track.title}."
    }

    fun playDownloadedTrack(downloads: List<DownloadedTrack>, index: Int) {
        val provider = connectedProvider ?: return
        if (downloads.isEmpty() || index !in downloads.indices) return
        stopRadioContinuation()
        openPlayerOnTrackStart = true
        playlistEngine.playFrom(
            scope = coroutineScope,
            provider = provider,
            tracks = downloads.map { it.track },
            index = index,
            quality = playbackEngine.streamQuality(),
            replayGainMode = playbackSettings.replayGainMode,
            callbacks = playlistCallbacks,
        )
    }

    fun playRadio(
        label: String,
        loadTracks: suspend (NavidromeProvider) -> List<Track>,
    ) {
        val provider = connectedProvider ?: return
        connectionStatus = "Loading $label..."
        coroutineScope.launch {
            try {
                val tracks = withContext(Dispatchers.IO) {
                    loadTracks(provider)
                }
                if (tracks.isEmpty()) {
                    connectionStatus = "$label did not return any tracks."
                    return@launch
                }
                connectionStatus = null
                radioSessionId += 1
                radioQueueActive = true
                isRadioRefilling = false
                lastRadioRefillSeedId = null
                openPlayerOnTrackStart = true
                playlistEngine.playFrom(
                    scope = coroutineScope,
                    provider = provider,
                    tracks = tracks,
                    index = 0,
                    quality = playbackEngine.streamQuality(),
                    replayGainMode = playbackSettings.replayGainMode,
                    callbacks = playlistCallbacks,
                )
            } catch (exception: Exception) {
                connectionStatus = exception.message ?: "Could not start $label."
            }
        }
    }

    fun startSeededRadio(
        label: String,
        provider: NavidromeProvider,
        seedTrack: Track,
        loadRest: suspend (NavidromeProvider) -> List<Track>,
    ) {
        connectionStatus = null
        radioSessionId += 1
        val activeRadioSessionId = radioSessionId
        radioQueueActive = true
        isRadioRefilling = true
        lastRadioRefillSeedId = seedTrack.id.value
        openPlayerOnTrackStart = true
        playlistEngine.playFrom(
            scope = coroutineScope,
            provider = provider,
            tracks = listOf(seedTrack),
            index = 0,
            quality = playbackEngine.streamQuality(),
            replayGainMode = playbackSettings.replayGainMode,
            callbacks = playlistCallbacks,
        )

        coroutineScope.launch {
            try {
                val fetchedTracks = withContext(Dispatchers.IO) {
                    loadRest(provider)
                }
                val existingTrackIds = playlistEngine.queue.tracks.map { it.id }.toSet()
                val newTracks = fetchedTracks.filterNot { track ->
                    track.id in existingTrackIds
                }
                if (radioQueueActive && activeRadioSessionId == radioSessionId && newTracks.isNotEmpty()) {
                    playlistEngine.appendTracks(
                        tracks = newTracks,
                        maxHistory = RadioQueueHistoryLimit,
                    )
                }
            } catch (exception: Exception) {
                if (activeRadioSessionId == radioSessionId) {
                    connectionStatus = exception.message ?: "Could not build $label."
                }
            } finally {
                if (activeRadioSessionId == radioSessionId) {
                    isRadioRefilling = false
                }
            }
        }
    }

    fun playPlaylist(playlist: Playlist) {
        val provider = connectedProvider ?: return
        connectionStatus = "Loading ${playlist.name}..."
        coroutineScope.launch {
            try {
                val tracks = withContext(Dispatchers.IO) {
                    provider.playlistTracks(playlist.id)
                }
                if (tracks.isEmpty()) {
                    connectionStatus = "${playlist.name} did not return any tracks."
                    return@launch
                }
                connectionStatus = null
                stopRadioContinuation()
                openPlayerOnTrackStart = true
                playlistEngine.playFrom(
                    scope = coroutineScope,
                    provider = provider,
                    tracks = tracks,
                    index = 0,
                    quality = playbackEngine.streamQuality(),
                    replayGainMode = playbackSettings.replayGainMode,
                    callbacks = playlistCallbacks,
                )
            } catch (exception: Exception) {
                connectionStatus = exception.message ?: "Could not play ${playlist.name}."
            }
        }
    }

    fun playLibraryRadio() {
        playRadio("Library radio") { provider ->
            provider.randomSongs(limit = 50)
        }
    }

    fun playGenreRadio(genre: Genre) {
        playRadio("${genre.name} radio") { provider ->
            provider.randomSongs(limit = 50, genre = genre.name)
        }
    }

    fun playDecadeRadio(fromYear: Int, toYear: Int) {
        playRadio("$fromYear-$toYear radio") { provider ->
            provider.randomSongs(limit = 50, fromYear = fromYear, toYear = toYear)
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
                startSeededRadio("${album.title} radio", provider, seedTrack) { radioProvider ->
                    radioProvider.albumRadio(album.id).ifEmpty {
                        radioProvider.album(album.id).tracks.shuffled()
                    }
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
                startSeededRadio("${artist.name} radio", provider, seedTrack) { radioProvider ->
                    radioProvider.artistRadio(artist.id)
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
                startSeededRadio("${album.title} radio", provider, seedTrack) { radioProvider ->
                    radioProvider.albumRadio(album.id).ifEmpty {
                        radioProvider.album(album.id).tracks.shuffled()
                    }
                }
            } catch (exception: Exception) {
                connectionStatus = exception.message ?: "Could not start ${album.title} radio."
            }
        }
    }

    fun playTrackRadio(track: Track) {
        val provider = connectedProvider ?: return
        startSeededRadio("${track.title} radio", provider, track) { radioProvider ->
            radioProvider.trackRadio(track.id, count = RadioRefillCount)
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
        radioSessionId += 1
        val activeRadioSessionId = radioSessionId
        radioQueueActive = true
        isRadioRefilling = true
        lastRadioRefillSeedId = track.id.value
        coroutineScope.launch {
            try {
                val fetchedTracks = withContext(Dispatchers.IO) {
                    provider.trackRadio(track.id, count = RadioRefillCount)
                }
                if (radioQueueActive && activeRadioSessionId == radioSessionId) {
                    playlistEngine.replaceUpcomingTracks(
                        currentTrack = track,
                        upcomingTracks = fetchedTracks,
                        maxHistory = RadioQueueHistoryLimit,
                    )
                    connectionStatus = null
                }
            } catch (exception: Exception) {
                if (activeRadioSessionId == radioSessionId) {
                    connectionStatus = exception.message ?: "Could not build ${track.title} radio."
                }
            } finally {
                if (activeRadioSessionId == radioSessionId) {
                    isRadioRefilling = false
                }
            }
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
        } ?: sessionCache.clearLibraryData()
        librarySnapshot = LibrarySnapshot()
        connectionStatus = "Local artist, album, and track index cleared."
    }

    fun resetDatabase() {
        sessionCache.clearAll()
        settingsStore.clearConnection()
        savedConnectionForLogin = null
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
        playbackState = PlaybackState.Idle
        playbackProgress = PlaybackProgress.Unknown
        playbackQueue = PlaybackQueue()
        settingsStore.savePlaybackSession(null)
        connectionStatus = "Database reset. Saved servers were removed."
        appRoute = AppRoute.Settings
    }

    fun openArtistDetails(artist: Artist, backRouteOverride: AppRoute? = null) {
        val provider = connectedProvider ?: return
        artistDetailBackRoute = backRouteOverride ?: when (appRoute) {
            AppRoute.ArtistDetail -> artistDetailBackRoute
            AppRoute.Player -> lastContentRoute
            else -> appRoute
        }
        selectedArtist = artist
        selectedArtistDetails = null
        selectedArtistStatus = "Loading..."
        appRoute = AppRoute.ArtistDetail
        coroutineScope.launch {
            try {
                selectedArtistDetails = sessionCache.artist(provider, artist.id)
                selectedArtistStatus = null
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

    val statsMediaSource = connectedSourceId?.let { sessionCache.mediaSource(it) } ?: sessionCache.latestMediaSource()
    val savedMediaSources = sessionCache.mediaSources()
    val streamQuality = playbackEngine.streamQuality()
    val currentAudioCacheMetadata = connectedSourceId
        ?.let { sourceId ->
            nowPlayingTrack?.let { track ->
                sessionCache.cachedAudioMetadata(sourceId, track.id, streamQuality)
            }
        }
    val statsForNerdsInfo = StatsForNerdsInfo(
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
        ),
        cacheStats = sessionCache.stats(),
        providerCapabilities = connectedProvider?.capabilities?.asStatsMap().orEmpty(),
        apiCalls = NavidromeApiCallHistory.recent(50).map { call ->
            ApiCallStats(
                endpoint = call.endpoint,
                sanitizedUrl = call.sanitizedUrl,
                durationMillis = call.durationMillis,
                success = call.success,
                errorMessage = call.errorMessage,
            )
        },
    )

    MaterialTheme(colorScheme = colorScheme) {
        if (showStatsForNerds) {
            StatsForNerdsWindow(
                appColors = appColors,
                info = statsForNerdsInfo,
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
                            PlayerColors(
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
                                supportsSeek = playbackEngine.supportsSeek,
                                supportsSoftwareVolume = playbackEngine.supportsSoftwareVolume,
                                supportsTrackFavorites = connectedProvider?.capabilities?.supportsTrackFavorites == true,
                                supportsTrackRatings = connectedProvider?.capabilities?.supportsTrackRatings == true,
                                nowPlayingTrack = nowPlayingTrack,
                                nowPlayingWaveform = nowPlayingWaveform,
                                nowPlayingAudioTags = nowPlayingAudioTags,
                                nowPlayingLyrics = nowPlayingLyrics,
                                coverArtUrl = nowPlayingCoverArtUrl,
                                upNext = playbackQueue.upNext(),
                                firstUpNextQueueIndex = playbackQueue.currentIndex + 1,
                                upNextCoverArtUrl = { track ->
                                    track.coverArtId?.let { connectedProvider?.coverArtUrl(it) }
                                },
                                relatedTracks = relatedTracks,
                                relatedCoverArtUrl = { track ->
                                    track.coverArtId?.let { connectedProvider?.coverArtUrl(it) }
                                },
                                hasPrevious = playbackQueue.hasPrevious(),
                                hasNext = playbackQueue.hasNext(),
                                playbackState = playbackState,
                                playbackProgress = playbackProgress,
                                volumePercent = playbackSettings.volumePercent,
                                onPlayerColorsChanged = { colors ->
                                    targetPlayerColors = colors
                                },
                                onPause = {
                                    playbackEngine.pause()
                                },
                                onResume = {
                                    playbackEngine.resume()
                                },
                                onPlayCurrent = {
                                    val restoredPosition = restoredPlaybackPositionSeconds
                                    restoredPlaybackPositionSeconds = null
                                    playlistEngine.playCurrent(coroutineScope, restoredPosition)
                                },
                                onSeek = { positionSeconds ->
                                    performSeek(positionSeconds)
                                },
                                onPrevious = {
                                    openPlayerOnTrackStart = false
                                    playlistEngine.previous(coroutineScope)
                                },
                                onNext = {
                                    openPlayerOnTrackStart = false
                                    playlistEngine.next(coroutineScope)
                                },
                                onVolumeChanged = { volumePercent ->
                                    playbackSettings = playbackSettings
                                        .copy(volumePercent = volumePercent)
                                        .forEngine(playbackEngine)
                                    settingsStore.savePlaybackSettings(playbackSettings)
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
                                onQueueIndexSelected = { queueIndex ->
                                    openPlayerOnTrackStart = false
                                    playlistEngine.jumpTo(
                                        scope = coroutineScope,
                                        index = queueIndex,
                                    )
                                },
                                onUpNextTrackRadioSelected = { track ->
                                    playTrackRadio(track)
                                },
                                onUpNextTrackDownloadSelected = { track ->
                                    downloadTrack(track)
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
                                    if (appRoute == AppRoute.Library) {
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
                                    onPlaylistSelected = { playlist ->
                                        playPlaylist(playlist)
                                    },
                                    onPlaylistDownloadSelected = { playlist ->
                                        downloadPlaylist(playlist)
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
                                    onPlayTrack = { index -> playAlbumDetails(index = index) },
                                    onTrackRadio = { track -> playTrackRadio(track) },
                                    onDownloadTrack = { track -> downloadTrack(track) },
                                    onArtistSelected = { track ->
                                        openTrackArtistDetails(track, backRouteOverride = AppRoute.AlbumDetail)
                                    },
                                )
                                AppRoute.ArtistDetail -> ArtistDetailPanel(
                                    appColors = appColors,
                                    artist = selectedArtist,
                                    artistDetails = selectedArtistDetails,
                                    status = selectedArtistStatus,
                                    coverArtUrl = { coverArtId ->
                                        coverArtId?.let { connectedProvider?.coverArtUrl(it) }
                                    },
                                    onBack = { appRoute = artistDetailBackRoute },
                                    onArtistRadio = { artist -> playArtistRadio(artist) },
                                    onAlbumSelected = { album -> openAlbumDetails(album) },
                                    onAlbumRadioSelected = { album -> playAlbumRadio(album) },
                                    onAlbumDownloadSelected = { album -> downloadAlbum(album) },
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
                                            onAlbumSelected = { album -> openAlbumDetails(album) },
                                            onAlbumRadioSelected = { album -> playAlbumRadio(album) },
                                            onAlbumDownloadSelected = { album -> downloadAlbum(album) },
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
                                        onAlbumSelected = { album -> openAlbumDetails(album) },
                                        onAlbumRadioSelected = { album -> playAlbumRadio(album) },
                                        onAlbumDownloadSelected = { album -> downloadAlbum(album) },
                                        onTrackSelected = { index -> playSearchTrack(index) },
                                        onTrackRadioSelected = { index ->
                                            searchResults.tracks.getOrNull(index)?.let { playTrackRadio(it) }
                                        },
                                        onTrackDownloadSelected = { index ->
                                            searchResults.tracks.getOrNull(index)?.let { downloadTrack(it) }
                                        },
                                    )
                                    AppRoute.Downloads -> {
                                        val downloads = remember(
                                            connectedSourceId,
                                            downloadRefreshToken,
                                            statsForNerdsInfo.cacheStats.downloadCount,
                                        ) {
                                            connectedSourceId
                                                ?.let { sessionCache.downloadedTracks(it) }
                                                .orEmpty()
                                        }
                                        DownloadsPanel(
                                            appColors = appColors,
                                            downloads = downloads,
                                            status = downloadStatus ?: connectionStatus,
                                            downloadBytes = statsForNerdsInfo.cacheStats.downloadBytes,
                                            maxDownloadBytes = cacheSettings.maxDownloadBytes,
                                            coverArtUrl = { coverArtId ->
                                                coverArtId?.let { connectedProvider?.coverArtUrl(it) }
                                            },
                                            onTrackSelected = { index -> playDownloadedTrack(downloads, index) },
                                            onRemoveDownload = { download -> removeDownloadedTrack(download) },
                                        )
                                    }
                                    AppRoute.Settings -> SettingsPanel(
                                        appColors = appColors,
                                        serverUrl = serverUrl,
                                        username = username,
                                        password = password,
                                        savedConnections = savedMediaSources,
                                        currentSourceId = connectedSourceId,
                                        hasSavedConnection = savedConnectionForLogin != null,
                                        isConnecting = isConnecting,
                                        connectionStatus = connectionStatus,
                                        playbackSettings = playbackSettings,
                                        cacheSettings = cacheSettings,
                                        cacheStats = statsForNerdsInfo.cacheStats,
                                        supportsReplayGain = playbackEngine.supportsReplayGain,
                                        supportsCrossfade = playbackEngine.supportsCrossfade,
                                        onServerUrlChanged = {
                                            serverUrl = it
                                            if (savedConnectionForLogin?.baseUrl != it) {
                                                savedConnectionForLogin = null
                                            }
                                        },
                                        onUsernameChanged = {
                                            username = it
                                            if (savedConnectionForLogin?.username != it) {
                                                savedConnectionForLogin = null
                                            }
                                        },
                                        onPasswordChanged = { password = it },
                                        onConnect = { connectToServer() },
                                        onNewConnection = {
                                            savedConnectionForLogin = null
                                            serverUrl = ""
                                            username = ""
                                            password = ""
                                            connectionStatus = null
                                        },
                                        onEditConnection = { source ->
                                            savedConnectionForLogin = source.toNavidromeConnection()
                                            serverUrl = source.baseUrl
                                            username = source.username
                                            password = ""
                                            connectionStatus = "Editing saved connection. Leave password blank to reuse it."
                                        },
                                        onConnectSavedConnection = { source ->
                                            savedConnectionForLogin = source.toNavidromeConnection()
                                            serverUrl = source.baseUrl
                                            username = source.username
                                            password = ""
                                            connectToServer()
                                        },
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
                        if (nowPlayingTrack != null) {
                            MiniPlayerPanel(
                                appColors = appColors,
                                nowPlayingTrack = nowPlayingTrack,
                                coverArtUrl = nowPlayingCoverArtUrl,
                                hasPrevious = playbackQueue.hasPrevious(),
                                hasNext = playbackQueue.hasNext(),
                                playbackState = playbackState,
                                onPlayerColorsChanged = { colors ->
                                    targetPlayerColors = colors
                                },
                                onPause = {
                                    playbackEngine.pause()
                                },
                                onResume = {
                                    playbackEngine.resume()
                                },
                                onPlayCurrent = {
                                    openPlayerOnTrackStart = false
                                    val restoredPosition = restoredPlaybackPositionSeconds
                                    restoredPlaybackPositionSeconds = null
                                    playlistEngine.playCurrent(coroutineScope, restoredPosition)
                                },
                                onPrevious = {
                                    openPlayerOnTrackStart = false
                                    playlistEngine.previous(coroutineScope)
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

private fun PlaybackSettings.forEngine(playbackEngine: PlaybackEngine): PlaybackSettings =
    copy(
        replayGainMode = if (playbackEngine.supportsReplayGain) {
            replayGainMode
        } else {
            ReplayGainMode.Off
        },
        crossfadeDurationSeconds = if (playbackEngine.supportsCrossfade) {
            crossfadeDurationSeconds.coerceIn(0, 12)
        } else {
            0
        },
        volumePercent = if (playbackEngine.supportsSoftwareVolume) {
            volumePercent.coerceIn(0, 100)
        } else {
            100
        },
        debugLoggingEnabled = debugLoggingEnabled,
    )

private fun shouldAutoSyncLibrary(
    sourceId: String,
    cache: DesktopCache,
    nowEpochMillis: Long = System.currentTimeMillis(),
): Boolean {
    val indexStats = cache.libraryIndexStats(sourceId)
    if (!indexStats.hasUsableIndex) return true

    val source = cache.mediaSource(sourceId) ?: return false
    val lastCompleted = source.lastSyncCompletedAtEpochMillis ?: return true
    return nowEpochMillis - lastCompleted > LibraryAutoSyncIntervalMillis
}

private val LibraryAutoSyncIntervalMillis = TimeUnit.HOURS.toMillis(24)
private const val LibraryPageSize = 50
private const val PlaybackPositionSaveThresholdSeconds = 5.0
private const val PendingSeekToleranceSeconds = 2.0
private const val PendingSeekStaleProgressWindowMillis = 1_500L
private const val RadioRefillThreshold = 10
private const val RadioRefillCount = 50
private const val RadioQueueHistoryLimit = 25
private const val HomeDecadeFromYear = 2000
private const val HomeDecadeToYear = 2009
private const val HomeDecadeLabel = "The 2000s"

private fun PlaybackEngine.capabilitiesLabel(): String =
    listOf(
        "pause" to supportsPause,
        "seek" to supportsSeek,
        "gapless" to supportsGapless,
        "crossfade" to supportsCrossfade,
        "ReplayGain" to supportsReplayGain,
        "volume" to supportsSoftwareVolume,
    ).joinToString(", ") { (label, supported) ->
        if (supported) label else "no $label"
    }

private fun Track.toStreamStats(
    playbackState: PlaybackState,
    playbackProgress: PlaybackProgress,
    playbackSettings: PlaybackSettings,
    streamQuality: StreamQuality,
    waveform: AudioWaveform?,
    waveformStatus: String,
    cachedAudio: CachedAudioMetadata?,
): StreamStats {
    val effectiveDurationSeconds = durationSeconds?.toDouble() ?: playbackProgress.durationSeconds
    val audio = audioInfo
    return StreamStats(
        state = playbackState.label(),
        trackId = id.value,
        title = title,
        artist = artistName,
        album = albumTitleWithYear() ?: "Unknown album",
        duration = durationLabel(),
        progress = playbackProgress.label(effectiveDurationSeconds),
        streamQuality = streamQuality.statsLabel(),
        replayGainMode = playbackSettings.replayGainMode.displayName,
        codec = audio?.codec ?: "Unknown",
        bitrate = audio?.bitrateKbps?.let { "$it kbps" } ?: "Unknown",
        contentType = audio?.contentType ?: "Unknown",
        coverArtId = coverArtId ?: "None",
        waveformStatus = waveformStatus,
        waveformBuckets = waveform?.amplitudes?.size?.toString() ?: "None",
        audioCacheStatus = cachedAudio?.let { if (it.exists) "Cached" else "Missing file" } ?: "Not cached",
        audioCacheSize = cachedAudio?.sizeBytes?.statsBytesLabel() ?: "None",
        audioCachePath = cachedAudio?.path?.toAbsolutePath()?.toString() ?: "None",
    )
}

private fun Long.statsBytesLabel(): String {
    val kib = 1024.0
    val mib = kib * 1024.0
    val gib = mib * 1024.0
    return when {
        this >= gib -> "%.1f GB".format(this / gib)
        this >= mib -> "%.1f MB".format(this / mib)
        this >= kib -> "%.1f KB".format(this / kib)
        else -> "$this B"
    }
}

private data class NowPlayingAnalysis(
    val waveform: AudioWaveform?,
    val waveformStatus: String,
    val audioTags: List<AudioTag>,
    val lyrics: Lyrics?,
)

private fun StreamQuality.statsLabel(): String =
    when (this) {
        StreamQuality.Original -> "Original"
        is StreamQuality.Transcoded -> "${codec.name.uppercase()} transcode at $bitrateKbps kbps"
    }

private fun app.naviamp.domain.provider.ProviderCapabilities.asStatsMap(): Map<String, Boolean> =
    mapOf(
        "Streaming transcode" to supportsStreamingTranscode,
        "Download transcode" to supportsDownloadTranscode,
        "Artist radio" to supportsArtistRadio,
        "Album radio" to supportsAlbumRadio,
        "Track radio" to supportsTrackRadio,
        "Track favorites" to supportsTrackFavorites,
        "Track ratings" to supportsTrackRatings,
        "Play reporting" to supportsPlayReporting,
    )

private fun playReportThresholdSeconds(durationSeconds: Double?): Double =
    durationSeconds
        ?.takeIf { it > 0.0 }
        ?.let { minOf(it * PlayReportDurationFraction, PlayReportMaxThresholdSeconds) }
        ?: PlayReportMaxThresholdSeconds

private fun restoredRoute(
    savedRouteName: String?,
    hasConnection: Boolean,
    hasRestoredTrack: Boolean,
): AppRoute {
    if (!hasConnection) return AppRoute.Settings
    return when (val route = AppRoute.fromStoredName(savedRouteName)) {
        AppRoute.Player -> if (hasRestoredTrack) AppRoute.Player else AppRoute.Home
        AppRoute.AlbumDetail -> AppRoute.Home
        AppRoute.ArtistDetail -> AppRoute.Search
        else -> route
    }
}

private fun restoredLastContentRoute(savedRouteName: String?): AppRoute =
    when (val route = AppRoute.fromStoredName(savedRouteName)) {
        AppRoute.Player,
        AppRoute.AlbumDetail,
        AppRoute.ArtistDetail
        -> AppRoute.Home
        else -> route
    }

private fun WindowState.toWindowSettings(): WindowSettings =
    WindowSettings(
        widthDp = size.width.value.coerceAtLeast(320f),
        heightDp = size.height.value.coerceAtLeast(420f),
    )

private const val PlayReportDurationFraction = 0.5
private const val PlayReportMaxThresholdSeconds = 240.0
