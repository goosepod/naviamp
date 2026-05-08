package app.naviamp.desktop

import androidx.compose.desktop.ui.tooling.preview.Preview
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
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.desktop.playback.JLayerPlaybackEngine
import app.naviamp.desktop.playback.PlaybackEngine
import app.naviamp.desktop.playback.PlaybackRequest
import app.naviamp.desktop.playback.PlaybackState
import app.naviamp.desktop.playback.label
import app.naviamp.provider.navidrome.NavidromeConnection
import app.naviamp.provider.navidrome.NavidromeProvider
import kotlinx.coroutines.launch

fun main() {
    configureDesktopAppearance()

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Naviamp",
        ) {
            NaviampApp()
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
fun NaviampApp() {
    val isDark = isSystemInDarkTheme()
    val appColors = if (isDark) AppColors.Dark else AppColors.Light
    val colorScheme = if (isDark) darkColorScheme() else lightColorScheme()
    val playbackEngine = remember { JLayerPlaybackEngine() }
    var nowPlayingTrack by remember { mutableStateOf<Track?>(null) }
    var playbackState by remember { mutableStateOf<PlaybackState>(PlaybackState.Idle) }

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
                        playbackEngine = playbackEngine,
                        onPlaybackStarted = { track ->
                            nowPlayingTrack = track
                        },
                        onPlaybackStateChanged = { state ->
                            playbackState = state
                        },
                    )
                }
                NowPlayingPanel(
                    appColors = appColors,
                    nowPlayingTrack = nowPlayingTrack,
                    playbackState = playbackState,
                    onStop = {
                        playbackEngine.stop()
                        playbackState = PlaybackState.Stopped
                    },
                )
            }
        }
    }
}

@Composable
private fun ConnectionPanel(
    appColors: AppColors,
    playbackEngine: PlaybackEngine,
    onPlaybackStarted: (Track) -> Unit,
    onPlaybackStateChanged: (PlaybackState) -> Unit,
) {
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var connectionStatus by remember { mutableStateOf<String?>(null) }
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
            if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) {
                connectionStatus = "Enter a server URL, username, and password."
            } else {
                isConnecting = true
                connectionStatus = "Connecting to Navidrome..."
                recentlyAddedAlbums = emptyList()
                selectedAlbumTitle = null
                selectedTracks = emptyList()
                albumStatus = null

                coroutineScope.launch {
                    try {
                        val provider = NavidromeProvider(
                            NavidromeConnection.fromPassword(
                                baseUrl = serverUrl,
                                username = username,
                                password = password,
                            ),
                        )
                        val validation = provider.validateConnection()
                        recentlyAddedAlbums = provider.recentlyAddedAlbums(limit = 5)
                        connectedProvider = provider
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
        Text("Connect to Navidrome", color = appColors.secondaryText)

        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
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
                onValueChange = { username = it },
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
                label = { Text("Password") },
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

                                coroutineScope.launch {
                                    try {
                                        val streamUrl = provider.streamUrl(
                                            StreamRequest(
                                                trackId = track.id,
                                                quality = StreamQuality.Transcoded(
                                                    codec = AudioCodec.Mp3,
                                                    bitrateKbps = 192,
                                                ),
                                            ),
                                        )
                                        onPlaybackStarted(track)
                                        playbackEngine.play(
                                            scope = coroutineScope,
                                            request = PlaybackRequest(streamUrl),
                                            onStateChanged = { state ->
                                                coroutineScope.launch {
                                                    onPlaybackStateChanged(state)
                                                }
                                            },
                                        )
                                    } catch (exception: Exception) {
                                        onPlaybackStateChanged(
                                            PlaybackState.Error(exception.message ?: "Playback failed."),
                                        )
                                    }
                                }
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
    nowPlayingTrack: Track?,
    playbackState: PlaybackState,
    onStop: () -> Unit,
) {
    var progress by remember { mutableFloatStateOf(0.35f) }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(appColors.albumArtPlaceholder),
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
                Text(playbackState.label(), color = appColors.mutedText)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Slider(
            value = progress,
            onValueChange = { progress = it },
            modifier = Modifier.fillMaxWidth(),
        )

        if (nowPlayingTrack != null) {
            Button(onClick = onStop) {
                Text("Stop")
            }
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
