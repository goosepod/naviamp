package app.naviamp.desktop

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import app.naviamp.domain.Album
import app.naviamp.domain.Track
import app.naviamp.desktop.playback.PlaybackEngine
import app.naviamp.desktop.playback.PlaybackEngineFactory
import app.naviamp.desktop.playback.PlaybackProgress
import app.naviamp.desktop.playback.PlaybackQueue
import app.naviamp.desktop.playback.PlaybackState
import app.naviamp.desktop.playback.PlaylistEngine
import app.naviamp.desktop.playback.ReplayGainMode
import app.naviamp.desktop.playback.mergeWith
import app.naviamp.desktop.settings.DesktopSettingsStore
import app.naviamp.desktop.settings.PlaybackSettings
import app.naviamp.desktop.settings.SavedConnection
import app.naviamp.provider.navidrome.NavidromeConnection
import app.naviamp.provider.navidrome.NavidromeProvider
import kotlinx.coroutines.launch

fun main() {
    configureDesktopAppearance()

    application {
        val playbackEngine = remember { PlaybackEngineFactory.createDefault() }
        Window(
            onCloseRequest = {
                playbackEngine.stop()
                exitApplication()
            },
            title = "Naviamp",
        ) {
            NaviampApp(playbackEngine)
        }
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
) {
    val isDark = isSystemInDarkTheme()
    val appColors = if (isDark) AppColors.Dark else AppColors.Light
    val colorScheme = if (isDark) darkColorScheme() else lightColorScheme()
    val settingsStore = remember { DesktopSettingsStore() }
    val savedConnection = remember { settingsStore.loadConnection() }
    val playlistEngine = remember(playbackEngine) { PlaylistEngine(playbackEngine) }
    val coroutineScope = rememberCoroutineScope()
    var serverUrl by remember { mutableStateOf(savedConnection?.baseUrl.orEmpty()) }
    var username by remember { mutableStateOf(savedConnection?.username.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var savedConnectionForLogin by remember { mutableStateOf(savedConnection) }
    var isConnecting by remember { mutableStateOf(false) }
    var connectionStatus by remember { mutableStateOf<String?>(null) }
    var connectedProvider by remember { mutableStateOf<NavidromeProvider?>(null) }
    var recentlyAddedAlbums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var showSettings by remember { mutableStateOf(savedConnection == null) }
    var nowPlayingTrack by remember { mutableStateOf<Track?>(null) }
    var nowPlayingCoverArtUrl by remember { mutableStateOf<String?>(null) }
    var playbackState by remember { mutableStateOf<PlaybackState>(PlaybackState.Idle) }
    var playbackProgress by remember { mutableStateOf(PlaybackProgress.Unknown) }
    var playbackQueue by remember { mutableStateOf(PlaybackQueue()) }
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

    fun connectToServer() {
        if (isConnecting) return
        if (serverUrl.isBlank() || username.isBlank()) {
            connectionStatus = "Enter a server URL and username."
            showSettings = true
            return
        }
        if (password.isBlank() && savedConnectionForLogin == null) {
            connectionStatus = "Enter a password for first-time setup."
            showSettings = true
            return
        }

        isConnecting = true
        connectionStatus = "Connecting to Navidrome..."
        recentlyAddedAlbums = emptyList()
        playlistEngine.clear()
        playbackEngine.stop()
        nowPlayingTrack = null
        nowPlayingCoverArtUrl = null
        playbackState = PlaybackState.Idle
        playbackProgress = PlaybackProgress.Unknown
        playbackQueue = PlaybackQueue()

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
                settingsStore.saveConnection(connection)
                savedConnectionForLogin = SavedConnection(
                    baseUrl = connection.baseUrl,
                    username = connection.username,
                    token = connection.token,
                    salt = connection.salt,
                )
                password = ""
                showSettings = false
                connectionStatus = buildString {
                    append("Connected")
                    validation.serverVersion?.let { append(" to Navidrome $it") }
                    append(".")
                }
            } catch (exception: Exception) {
                connectedProvider = null
                showSettings = true
                connectionStatus = exception.message ?: "Could not connect to Navidrome."
            } finally {
                isConnecting = false
            }
        }
    }

    LaunchedEffect(Unit) {
        if (savedConnection != null) {
            connectToServer()
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
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (showSettings) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Button(
                                        enabled = connectedProvider != null,
                                        onClick = { showSettings = false },
                                    ) {
                                        Text("Back")
                                    }
                                }
                                SettingsPanel(
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
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Button(onClick = { showSettings = true }) {
                                        Text("Settings")
                                    }
                                }
                                ConnectionPanel(
                                    appColors = appColors,
                                    connectedProvider = connectedProvider,
                                    connectionStatus = connectionStatus,
                                    recentlyAddedAlbums = recentlyAddedAlbums,
                                    playbackEngine = playbackEngine,
                                    playlistEngine = playlistEngine,
                                    playbackSettings = playbackSettings,
                                    onPlaybackStarted = { track, coverArtUrl ->
                                        nowPlayingTrack = track
                                        nowPlayingCoverArtUrl = coverArtUrl
                                    },
                                    onQueueChanged = { queue ->
                                        playbackQueue = queue
                                    },
                                    onPlaybackStateChanged = { state ->
                                        playbackState = state
                                    },
                                    onPlaybackProgressChanged = { progress ->
                                        playbackProgress = progress.mergeWith(playbackProgress)
                                    },
                                )
                            }
                        }
                    }
                    if (nowPlayingTrack != null) {
                        if (showSettings) {
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
                                onPrevious = {
                                    playlistEngine.previous(coroutineScope)
                                },
                                onNext = {
                                    playlistEngine.next(coroutineScope)
                                },
                            )
                        } else {
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
                                onSeek = { positionSeconds ->
                                    playbackEngine.seek(positionSeconds)
                                },
                                onPrevious = {
                                    playlistEngine.previous(coroutineScope)
                                },
                                onNext = {
                                    playlistEngine.next(coroutineScope)
                                },
                            )
                        }
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
