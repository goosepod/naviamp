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
import app.naviamp.domain.Track
import app.naviamp.desktop.playback.PlaybackEngine
import app.naviamp.desktop.playback.PlaybackEngineFactory
import app.naviamp.desktop.playback.PlaybackProgress
import app.naviamp.desktop.playback.PlaybackQueue
import app.naviamp.desktop.playback.PlaybackState
import app.naviamp.desktop.playback.PlaylistCallbacks
import app.naviamp.desktop.playback.PlaylistEngine
import app.naviamp.desktop.playback.ReplayGainMode
import app.naviamp.desktop.playback.mergeWith
import app.naviamp.desktop.settings.DesktopSettingsStore
import app.naviamp.desktop.settings.NavigationSettings
import app.naviamp.desktop.settings.PlaybackSettings
import app.naviamp.desktop.settings.PlaybackSessionSettings
import app.naviamp.desktop.settings.SavedConnection
import app.naviamp.desktop.settings.SearchSettings
import app.naviamp.desktop.settings.WindowSettings
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.provider.navidrome.NavidromeConnection
import app.naviamp.provider.navidrome.NavidromeProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.awt.Taskbar
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

    LaunchedEffect(appRoute) {
        if (appRoute == AppRoute.Player) {
            settingsStore.saveNavigationSettings(
                NavigationSettings(
                    route = appRoute.name,
                    lastContentRoute = lastContentRoute.name,
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
            appRoute = AppRoute.Player
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
                recentlyAddedAlbums = provider.recentlyAddedAlbums(limit = 5)
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

    fun openAlbumDetails(album: Album) {
        val provider = connectedProvider ?: return
        selectedAlbum = album
        selectedAlbumDetails = null
        selectedAlbumStatus = "Loading..."
        appRoute = AppRoute.AlbumDetail
        coroutineScope.launch {
            try {
                selectedAlbumDetails = provider.album(album.id)
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
            searchResults = provider.search(query, limit = 12)
        } catch (exception: Exception) {
            searchResults = MediaSearchResults()
            searchStatus = exception.message ?: "Search failed."
        } finally {
            isSearching = false
        }
    }

    MaterialTheme(colorScheme = colorScheme) {
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
                                .fillMaxWidth(),
                        ) {
                            NowPlayingPanel(
                                appColors = appColors,
                                playbackEngineName = playbackEngine.name,
                                supportsPause = playbackEngine.supportsPause,
                                supportsSeek = playbackEngine.supportsSeek,
                                supportsGapless = playbackEngine.supportsGapless,
                                supportsCrossfade = playbackEngine.supportsCrossfade,
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
                                    playlistEngine.previous(coroutineScope)
                                },
                                onNext = {
                                    playlistEngine.next(coroutineScope)
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
                                    coverArtUrl = selectedAlbum?.coverArtId?.let { connectedProvider?.coverArtUrl(it) },
                                    onBack = { appRoute = AppRoute.Home },
                                    onPlayAlbum = { playAlbumDetails() },
                                    onShuffleAlbum = { playAlbumDetails(shuffle = true) },
                                    onPlayTrack = { index -> playAlbumDetails(index = index) },
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
                                    appRoute = AppRoute.Player
                                    playlistEngine.playCurrent(coroutineScope)
                                },
                                onPrevious = {
                                    playlistEngine.previous(coroutineScope)
                                },
                                onNext = {
                                    playlistEngine.next(coroutineScope)
                                },
                                onOpenPlayer = {
                                    appRoute = AppRoute.Player
                                },
                            )
                        }
                        BottomNavigationBar(
                            appColors = appColors,
                            selectedRoute = if (appRoute == AppRoute.AlbumDetail) AppRoute.Home else appRoute,
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

private fun restoredRoute(
    savedRouteName: String?,
    hasConnection: Boolean,
    hasRestoredTrack: Boolean,
): AppRoute {
    if (!hasConnection) return AppRoute.Settings
    return when (val route = AppRoute.fromStoredName(savedRouteName)) {
        AppRoute.Player -> if (hasRestoredTrack) AppRoute.Player else AppRoute.Home
        AppRoute.AlbumDetail -> AppRoute.Home
        else -> route
    }
}

private fun restoredLastContentRoute(savedRouteName: String?): AppRoute =
    when (val route = AppRoute.fromStoredName(savedRouteName)) {
        AppRoute.Player,
        AppRoute.AlbumDetail
        -> AppRoute.Home
        else -> route
    }

private fun WindowState.toWindowSettings(): WindowSettings =
    WindowSettings(
        widthDp = size.width.value.coerceAtLeast(320f),
        heightDp = size.height.value.coerceAtLeast(420f),
    )
