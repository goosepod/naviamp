package app.naviamp.desktop

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import app.naviamp.domain.Album
import app.naviamp.domain.AudioCodec
import app.naviamp.domain.AudioInfo
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.desktop.playback.PlaybackEngine
import app.naviamp.desktop.playback.PlaybackEngineFactory
import app.naviamp.desktop.playback.PlaybackProgress
import app.naviamp.desktop.playback.PlaybackQueue
import app.naviamp.desktop.playback.PlaybackState
import app.naviamp.desktop.playback.PlaylistCallbacks
import app.naviamp.desktop.playback.PlaylistEngine
import app.naviamp.desktop.playback.label
import app.naviamp.desktop.playback.mergeWith
import app.naviamp.desktop.settings.DesktopSettingsStore
import app.naviamp.desktop.settings.SavedConnection
import app.naviamp.provider.navidrome.NavidromeConnection
import app.naviamp.provider.navidrome.NavidromeProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage
import java.net.URI

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
    val playlistEngine = remember(playbackEngine) { PlaylistEngine(playbackEngine) }
    var nowPlayingTrack by remember { mutableStateOf<Track?>(null) }
    var nowPlayingCoverArtUrl by remember { mutableStateOf<String?>(null) }
    var playbackState by remember { mutableStateOf<PlaybackState>(PlaybackState.Idle) }
    var playbackProgress by remember { mutableStateOf(PlaybackProgress.Unknown) }
    var playbackQueue by remember { mutableStateOf(PlaybackQueue()) }

    DisposableEffect(playbackEngine) {
        onDispose {
            playbackEngine.stop()
        }
    }

    MaterialTheme(colorScheme = colorScheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = appColors.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    ConnectionPanel(
                        appColors = appColors,
                        settingsStore = settingsStore,
                        playbackEngine = playbackEngine,
                        playlistEngine = playlistEngine,
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
                NowPlayingPanel(
                    appColors = appColors,
                    playbackEngineName = playbackEngine.name,
                    supportsPause = playbackEngine.supportsPause,
                    supportsSeek = playbackEngine.supportsSeek,
                    nowPlayingTrack = nowPlayingTrack,
                    coverArtUrl = nowPlayingCoverArtUrl,
                    upNext = playbackQueue.upNext(),
                    playbackState = playbackState,
                    playbackProgress = playbackProgress,
                    onPause = {
                        playbackEngine.pause()
                    },
                    onResume = {
                        playbackEngine.resume()
                    },
                    onSeek = { positionSeconds ->
                        playbackEngine.seek(positionSeconds)
                    },
                    onStop = {
                        playbackEngine.stop()
                        playbackState = PlaybackState.Stopped
                        playbackProgress = PlaybackProgress.Unknown
                    },
                )
            }
        }
    }
}

@Composable
private fun ConnectionPanel(
    appColors: AppColors,
    settingsStore: DesktopSettingsStore,
    playbackEngine: PlaybackEngine,
    playlistEngine: PlaylistEngine,
    onPlaybackStarted: (Track, String?) -> Unit,
    onQueueChanged: (PlaybackQueue) -> Unit,
    onPlaybackStateChanged: (PlaybackState) -> Unit,
    onPlaybackProgressChanged: (PlaybackProgress) -> Unit,
) {
    val savedConnection = remember { settingsStore.loadConnection() }
    var serverUrl by remember { mutableStateOf(savedConnection?.baseUrl.orEmpty()) }
    var username by remember { mutableStateOf(savedConnection?.username.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var connectionStatus by remember { mutableStateOf<String?>(null) }
    var savedConnectionForLogin by remember { mutableStateOf(savedConnection) }
    var isConnecting by remember { mutableStateOf(false) }
    var isLoadingAlbum by remember { mutableStateOf(false) }
    var connectedProvider by remember { mutableStateOf<NavidromeProvider?>(null) }
    var recentlyAddedAlbums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var selectedAlbumTitle by remember { mutableStateOf<String?>(null) }
    var selectedTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var albumStatus by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = appColors.primaryText,
        unfocusedTextColor = appColors.primaryText,
        focusedLabelColor = appColors.primaryText,
        unfocusedLabelColor = appColors.secondaryText,
        cursorColor = appColors.primaryText,
        focusedBorderColor = appColors.accent,
        unfocusedBorderColor = appColors.border,
    )
    val connect = {
        if (!isConnecting) {
            if (serverUrl.isBlank() || username.isBlank()) {
                connectionStatus = "Enter a server URL and username."
            } else if (password.isBlank() && savedConnectionForLogin == null) {
                connectionStatus = "Enter a password for first-time setup."
            } else {
                isConnecting = true
                connectionStatus = "Connecting to Navidrome..."
                recentlyAddedAlbums = emptyList()
                selectedAlbumTitle = null
                selectedTracks = emptyList()
                playlistEngine.clear()
                albumStatus = null

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
                        connectionStatus = buildString {
                            append("Connected")
                            validation.serverVersion?.let { append(" to Navidrome $it") }
                            append(".")
                        }
                    } catch (exception: Exception) {
                        connectedProvider = null
                        connectionStatus = exception.message ?: "Could not connect to Navidrome."
                    } finally {
                        isConnecting = false
                    }
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Naviamp", color = appColors.primaryText, style = MaterialTheme.typography.headlineMedium)
        if (connectedProvider == null) {
            Text("Connect to Navidrome", color = appColors.secondaryText)
            if (savedConnectionForLogin != null) {
                Text("Saved connection loaded. Leave password blank to reuse it.", color = appColors.mutedText)
            }

            OutlinedTextField(
                value = serverUrl,
                onValueChange = {
                    serverUrl = it
                    if (savedConnectionForLogin?.baseUrl != it) {
                        savedConnectionForLogin = null
                    }
                },
                label = { Text("Server URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Next) },
                    onDone = { connect() },
                ),
                colors = textFieldColors,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        if (savedConnectionForLogin?.username != it) {
                            savedConnectionForLogin = null
                        }
                    },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Next) },
                        onDone = { connect() },
                    ),
                    colors = textFieldColors,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(if (savedConnectionForLogin == null) "Password" else "Password (optional)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { connect() }),
                    colors = textFieldColors,
                )
            }

            Button(
                enabled = !isConnecting,
                onClick = { connect() },
            ) {
                Text(if (isConnecting) "Connecting" else "Connect")
            }

            connectionStatus?.let {
                Text(it, color = appColors.secondaryText)
            }
        } else {
            Text("Connected as $username", color = appColors.secondaryText)
        }

        if (recentlyAddedAlbums.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Recently Added", color = appColors.primaryText, fontWeight = FontWeight.SemiBold)
                recentlyAddedAlbums.forEach { album ->
                    TextButton(
                        enabled = !isLoadingAlbum,
                        onClick = {
                            val provider = connectedProvider ?: return@TextButton
                            isLoadingAlbum = true
                            selectedAlbumTitle = album.title
                            selectedTracks = emptyList()
                            albumStatus = "Loading ${album.title}..."

                            coroutineScope.launch {
                                try {
                                    val details = provider.album(album.id)
                                    selectedAlbumTitle = "${details.album.title} - ${details.album.artistName}"
                                    selectedTracks = details.tracks
                                    albumStatus = if (details.tracks.isEmpty()) {
                                        "No tracks found for ${details.album.title}."
                                    } else {
                                        null
                                    }
                                } catch (exception: Exception) {
                                    albumStatus = exception.message ?: "Could not load album."
                                } finally {
                                    isLoadingAlbum = false
                                }
                            }
                        },
                    ) {
                        Text("${album.title} - ${album.artistName}")
                    }
                }
            }
        }

        albumStatus?.let {
            Text(it, color = appColors.secondaryText)
        }

        if (selectedTracks.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(selectedAlbumTitle ?: "Album Tracks", color = appColors.primaryText, fontWeight = FontWeight.SemiBold)
                selectedTracks.forEachIndexed { index, track ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = {
                                val provider = connectedProvider ?: return@TextButton
                                playlistEngine.playFrom(
                                    scope = coroutineScope,
                                    provider = provider,
                                    tracks = selectedTracks,
                                    index = index,
                                    quality = playbackEngine.streamQuality(),
                                    callbacks = PlaylistCallbacks(
                                        onTrackStarted = onPlaybackStarted,
                                        onQueueChanged = onQueueChanged,
                                        onPlaybackStateChanged = onPlaybackStateChanged,
                                        onPlaybackProgressChanged = onPlaybackProgressChanged,
                                    ),
                                )
                            },
                        ) {
                            Text("Play")
                        }
                        Text(
                            "${index + 1}. ${track.title} (${track.durationLabel()})",
                            color = appColors.secondaryText,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NowPlayingPanel(
    appColors: AppColors,
    playbackEngineName: String,
    supportsPause: Boolean,
    supportsSeek: Boolean,
    nowPlayingTrack: Track?,
    coverArtUrl: String?,
    upNext: List<Track>,
    playbackState: PlaybackState,
    playbackProgress: PlaybackProgress,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSeek: (Double) -> Unit,
    onStop: () -> Unit,
) {
    var scrubberValue by remember { mutableFloatStateOf(0f) }
    var isScrubbing by remember { mutableStateOf(false) }
    val effectiveDurationSeconds = nowPlayingTrack?.durationSeconds?.toDouble()
        ?: playbackProgress.durationSeconds
    val effectiveProgressFraction = playbackProgress.fraction(effectiveDurationSeconds)
    val canSeek = supportsSeek && effectiveDurationSeconds != null && nowPlayingTrack != null

    LaunchedEffect(effectiveProgressFraction) {
        if (!isScrubbing) {
            scrubberValue = effectiveProgressFraction.toFloat()
        }
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoverArt(
                coverArtUrl = coverArtUrl,
                appColors = appColors,
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    nowPlayingTrack?.title ?: "Nothing Playing",
                    color = appColors.primaryText,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(nowPlayingTrack?.artistName ?: "Queue will appear here after connection", color = appColors.secondaryText)
                Spacer(modifier = Modifier.height(8.dp))
                nowPlayingTrack?.playbackAudioLabel(playbackEngineName)?.let {
                    Text(it, color = appColors.mutedText)
                }
                Text("${playbackState.label()} via $playbackEngineName", color = appColors.mutedText)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Slider(
            value = scrubberValue,
            onValueChange = {
                isScrubbing = true
                scrubberValue = it
            },
            onValueChangeFinished = {
                effectiveDurationSeconds?.let { duration ->
                    onSeek(scrubberValue * duration)
                }
                isScrubbing = false
            },
            enabled = canSeek,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(playbackProgress.label(effectiveDurationSeconds), color = appColors.mutedText)

        if (nowPlayingTrack != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (supportsPause && playbackState == PlaybackState.Playing) {
                    Button(onClick = onPause) {
                        Text("Pause")
                    }
                }

                if (supportsPause && playbackState == PlaybackState.Paused) {
                    Button(onClick = onResume) {
                        Text("Resume")
                    }
                }

                Button(onClick = onStop) {
                    Text("Stop")
                }
            }
        }

        if (upNext.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Up Next", color = appColors.primaryText, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            upNext.take(5).forEachIndexed { index, track ->
                Text(
                    "${index + 1}. ${track.title} - ${track.artistName}",
                    color = appColors.secondaryText,
                )
            }
        }
    }
}

@Composable
private fun CoverArt(
    coverArtUrl: String?,
    appColors: AppColors,
) {
    var image by remember(coverArtUrl) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(coverArtUrl) {
        image = if (coverArtUrl == null) {
            null
        } else {
            runCatching {
                withContext(Dispatchers.IO) {
                    val bytes = URI.create(coverArtUrl).toURL().openStream().use { it.readBytes() }
                    SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
                }
            }.getOrNull()
        }
    }

    Box(
        modifier = Modifier
            .size(96.dp)
            .background(appColors.albumArtPlaceholder),
    ) {
        image?.let {
            Image(
                bitmap = it,
                contentDescription = "Album art",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private data class AppColors(
    val background: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val mutedText: Color,
    val border: Color,
    val accent: Color,
    val albumArtPlaceholder: Color,
) {
    companion object {
        val Dark = AppColors(
            background = Color(0xFF101114),
            primaryText = Color.White,
            secondaryText = Color(0xFFB9BDC7),
            mutedText = Color(0xFF8F96A3),
            border = Color(0xFF59606D),
            accent = Color(0xFF8EA7D8),
            albumArtPlaceholder = Color(0xFF43536B),
        )

        val Light = AppColors(
            background = Color(0xFFF8F9FB),
            primaryText = Color(0xFF171A21),
            secondaryText = Color(0xFF4F5663),
            mutedText = Color(0xFF727A86),
            border = Color(0xFFBAC1CC),
            accent = Color(0xFF315D9E),
            albumArtPlaceholder = Color(0xFFD3DBE8),
        )
    }
}

private fun Track.durationLabel(): String {
    val duration = durationSeconds ?: return "--:--"
    val minutes = duration / 60
    val seconds = duration % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

private fun PlaybackProgress.label(effectiveDurationSeconds: Double?): String {
    val position = positionSeconds?.toTimeLabel() ?: "--:--"
    val duration = effectiveDurationSeconds?.toTimeLabel() ?: "--:--"
    return "$position / $duration"
}

private fun PlaybackProgress.fraction(effectiveDurationSeconds: Double?): Double {
    val position = positionSeconds ?: return 0.0
    val duration = effectiveDurationSeconds ?: return 0.0
    if (duration <= 0.0) return 0.0
    return (position / duration).coerceIn(0.0, 1.0)
}

private fun PlaybackEngine.streamQuality(): StreamQuality =
    if (prefersOriginalStream) {
        StreamQuality.Original
    } else {
        StreamQuality.Transcoded(
            codec = AudioCodec.Mp3,
            bitrateKbps = 192,
        )
    }

private fun Track.playbackAudioLabel(playbackEngineName: String): String? =
    if (playbackEngineName == "JLayer") {
        "MP3 transcode • 192 kbps"
    } else {
        audioInfo?.label()
    }

private fun AudioInfo.label(): String? {
    val parts = listOfNotNull(
        codec,
        bitrateKbps?.let { "$it kbps" },
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" • ")
}

private fun Double.toTimeLabel(): String {
    val totalSeconds = toInt().coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
