package app.naviamp.desktop

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Brush
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
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.desktop.playback.PlaybackEngine
import app.naviamp.desktop.playback.PlaybackEngineFactory
import app.naviamp.desktop.playback.PlaybackProgress
import app.naviamp.desktop.playback.PlaybackQueue
import app.naviamp.desktop.playback.PlaybackState
import app.naviamp.desktop.playback.PlaylistCallbacks
import app.naviamp.desktop.playback.PlaylistEngine
import app.naviamp.desktop.playback.ReplayGainMode
import app.naviamp.desktop.playback.label
import app.naviamp.desktop.playback.mergeWith
import app.naviamp.desktop.settings.DesktopSettingsStore
import app.naviamp.desktop.settings.NavigationSettings
import app.naviamp.desktop.settings.PlaybackSettings
import app.naviamp.desktop.settings.PlaybackSessionSettings
import app.naviamp.desktop.settings.SavedConnection
import app.naviamp.desktop.settings.SearchSettings
import app.naviamp.desktop.settings.WindowSettings
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.provider.navidrome.NavidromeApiCallHistory
import app.naviamp.provider.navidrome.NavidromeConnection
import app.naviamp.provider.navidrome.NavidromeProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.awt.Taskbar
import java.time.Instant
import javax.imageio.ImageIO

fun main() {
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
                playbackEngine.stop()
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
    val savedConnection = remember { settingsStore.loadConnection() }
    val savedPlaybackSession = remember { settingsStore.loadPlaybackSession() }
    val savedNavigation = remember { settingsStore.loadNavigationSettings() }
    val savedSearch = remember { settingsStore.loadSearchSettings() }
    val playlistEngine = remember(playbackEngine) { PlaylistEngine(playbackEngine) }
    val sessionCache = remember { DesktopCaches.session }
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
    var recentlyAddedAlbums by remember { mutableStateOf<List<Album>>(emptyList()) }
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
    var playbackSettings by remember { mutableStateOf(settingsStore.loadPlaybackSettings()) }
    var showStatsForNerds by remember { mutableStateOf(false) }
    var openPlayerOnTrackStart by remember { mutableStateOf(false) }
    var targetPlayerColors by remember {
        mutableStateOf(PlayerColors.from(AlbumPalette.fallback(appColors.albumArtPlaceholder), appColors))
    }
    val backgroundStart by animateColorAsState(
        targetValue = if (nowPlayingTrack != null) targetPlayerColors.backgroundStart else appColors.background,
        label = "backgroundStart",
    )
    val backgroundMid by animateColorAsState(
        targetValue = if (nowPlayingTrack != null) targetPlayerColors.backgroundMid else appColors.background,
        label = "backgroundMid",
    )
    val backgroundEnd by animateColorAsState(
        targetValue = if (nowPlayingTrack != null) targetPlayerColors.backgroundEnd else appColors.background,
        label = "backgroundEnd",
    )

    DisposableEffect(playbackEngine) {
        onDispose {
            playbackEngine.stop()
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

    fun savePlaybackSession(queue: PlaybackQueue) {
        settingsStore.savePlaybackSession(
            PlaybackSessionSettings.fromTracks(
                tracks = queue.tracks,
                currentIndex = queue.currentIndex,
            ),
        )
    }

    val playlistCallbacks = PlaylistCallbacks(
        onTrackStarted = { track, coverArtUrl ->
            nowPlayingTrack = track
            nowPlayingCoverArtUrl = coverArtUrl
            playbackProgress = PlaybackProgress.Unknown
            if (openPlayerOnTrackStart) {
                appRoute = AppRoute.Player
            }
        },
        onQueueChanged = { queue ->
            playbackQueue = queue
            savePlaybackSession(queue)
        },
        onPlaybackStateChanged = { state ->
            playbackState = state
        },
        onPlaybackProgressChanged = { progress ->
            playbackProgress = progress.mergeWith(playbackProgress)
        },
    )

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
            recentlyAddedAlbums = emptyList()
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
                    ?.toConnection()
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
                recentlyAddedAlbums = sessionCache.recentlyAddedAlbums(provider, limit = 5)
                connectedProvider = provider
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
                settingsStore.saveConnection(connection)
                savedConnectionForLogin = SavedConnection(
                    baseUrl = connection.baseUrl,
                    username = connection.username,
                    token = connection.token,
                    salt = connection.salt,
                )
                password = ""
                if (appRoute == AppRoute.Settings) {
                    appRoute = AppRoute.Home
                }
                connectionStatus = buildString {
                    append("Connected")
                    validation.serverVersion?.let { append(" to Navidrome $it") }
                    append(".")
                }
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

    val statsForNerdsInfo = StatsForNerdsInfo(
        route = appRoute.name,
        os = "${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")})",
        javaVersion = System.getProperty("java.version"),
        workingDirectory = System.getProperty("user.dir"),
        serverUrl = serverUrl,
        username = username,
        providerName = connectedProvider?.displayName ?: "Not connected",
        providerCacheNamespace = connectedProvider?.cacheNamespace ?: "Not connected",
        connectionStatus = connectionStatus,
        playbackEngineName = playbackEngine.name,
        playbackCapabilities = playbackEngine.capabilitiesLabel(),
        queueSize = playbackQueue.tracks.size,
        currentQueueIndex = playbackQueue.currentIndex,
        stream = nowPlayingTrack?.toStreamStats(
            playbackState = playbackState,
            playbackProgress = playbackProgress,
            playbackSettings = playbackSettings,
            streamQuality = playbackEngine.streamQuality(),
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
                            Brush.linearGradient(
                                colors = listOf(backgroundStart, backgroundMid, backgroundEnd),
                                start = Offset.Zero,
                                end = gradientEnd,
                            ),
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
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                        ) {
                            NowPlayingPanel(
                                appColors = appColors,
                                playbackEngineName = playbackEngine.name,
                                supportsPause = playbackEngine.supportsPause,
                                supportsSeek = playbackEngine.supportsSeek,
                                supportsGapless = playbackEngine.supportsGapless,
                                supportsCrossfade = playbackEngine.supportsCrossfade,
                                supportsTrackFavorites = connectedProvider?.capabilities?.supportsTrackFavorites == true,
                                supportsTrackRatings = connectedProvider?.capabilities?.supportsTrackRatings == true,
                                nowPlayingTrack = nowPlayingTrack,
                                coverArtUrl = nowPlayingCoverArtUrl,
                                upNext = playbackQueue.upNext(),
                                hasPrevious = playbackQueue.hasPrevious(),
                                hasNext = playbackQueue.hasNext(),
                                playbackState = playbackState,
                                playbackProgress = playbackProgress,
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
                                    playlistEngine.playCurrent(coroutineScope)
                                },
                                onSeek = { positionSeconds ->
                                    playbackEngine.seek(positionSeconds)
                                },
                                onPrevious = {
                                    openPlayerOnTrackStart = false
                                    playlistEngine.previous(coroutineScope)
                                },
                                onNext = {
                                    openPlayerOnTrackStart = false
                                    playlistEngine.next(coroutineScope)
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
                                onCollapseToHome = {
                                    appRoute = lastContentRoute
                                },
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                when (appRoute) {
                                    AppRoute.Player -> Unit
                                AppRoute.Home -> HomePanel(
                                    appColors = appColors,
                                    connectionStatus = connectionStatus,
                                    recentlyAddedAlbums = recentlyAddedAlbums,
                                    coverArtUrl = { album ->
                                        album.coverArtId?.let { connectedProvider?.coverArtUrl(it) }
                                    },
                                    onAlbumSelected = { album ->
                                        openAlbumDetails(album)
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
                                    onPlayTrack = { index -> playAlbumDetails(index = index) },
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
                                    onAlbumSelected = { album -> openAlbumDetails(album) },
                                )
                                    AppRoute.Library -> ConnectionPanel(
                                        appColors = appColors,
                                        connectedProvider = connectedProvider,
                                        connectionStatus = connectionStatus,
                                        recentlyAddedAlbums = recentlyAddedAlbums,
                                        playbackEngine = playbackEngine,
                                        playlistEngine = playlistEngine,
                                        playbackSettings = playbackSettings,
                                        playlistCallbacks = playlistCallbacks,
                                        sessionCache = sessionCache,
                                        onPlaybackShouldOpenPlayer = {
                                            openPlayerOnTrackStart = true
                                        },
                                    )
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
                                        onAlbumSelected = { album -> openAlbumDetails(album) },
                                        onTrackSelected = { index -> playSearchTrack(index) },
                                    )
                                    AppRoute.Downloads -> PlaceholderRoutePanel(
                                        appColors = appColors,
                                        title = "Downloads",
                                        message = "Offline music will live here next.",
                                    )
                                    AppRoute.Settings -> SettingsPanel(
                                        appColors = appColors,
                                        serverUrl = serverUrl,
                                        username = username,
                                        password = password,
                                        hasSavedConnection = savedConnectionForLogin != null,
                                        isConnecting = isConnecting,
                                        connectionStatus = connectionStatus,
                                        playbackSettings = playbackSettings,
                                        supportsReplayGain = playbackEngine.supportsReplayGain,
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
                                        onPlaybackSettingsChanged = { settings ->
                                            playbackSettings = settings.forEngine(playbackEngine)
                                            settingsStore.savePlaybackSettings(playbackSettings)
                                        },
                                        onOpenStatsForNerds = { showStatsForNerds = true },
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
                                    playlistEngine.playCurrent(coroutineScope)
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
    if (playbackEngine.supportsReplayGain) {
        this
    } else {
        copy(replayGainMode = ReplayGainMode.Off)
    }

private fun PlaybackEngine.capabilitiesLabel(): String =
    listOf(
        "pause" to supportsPause,
        "seek" to supportsSeek,
        "gapless" to supportsGapless,
        "crossfade" to supportsCrossfade,
        "ReplayGain" to supportsReplayGain,
    ).joinToString(", ") { (label, supported) ->
        if (supported) label else "no $label"
    }

private fun Track.toStreamStats(
    playbackState: PlaybackState,
    playbackProgress: PlaybackProgress,
    playbackSettings: PlaybackSettings,
    streamQuality: StreamQuality,
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
    )
}

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
        "Track radio" to supportsTrackRadio,
        "Track favorites" to supportsTrackFavorites,
        "Track ratings" to supportsTrackRatings,
    )

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
