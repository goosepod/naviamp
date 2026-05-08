package app.naviamp.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.naviamp.domain.Album
import app.naviamp.domain.Track
import app.naviamp.desktop.playback.PlaybackEngine
import app.naviamp.desktop.playback.PlaybackProgress
import app.naviamp.desktop.playback.PlaybackQueue
import app.naviamp.desktop.playback.PlaybackState
import app.naviamp.desktop.playback.PlaylistCallbacks
import app.naviamp.desktop.playback.PlaylistEngine
import app.naviamp.desktop.settings.DesktopSettingsStore
import app.naviamp.desktop.settings.SavedConnection
import app.naviamp.provider.navidrome.NavidromeConnection
import app.naviamp.provider.navidrome.NavidromeProvider
import kotlinx.coroutines.launch

@Composable
fun ConnectionPanel(
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
    val playlistCallbacks = PlaylistCallbacks(
        onTrackStarted = onPlaybackStarted,
        onQueueChanged = onQueueChanged,
        onPlaybackStateChanged = onPlaybackStateChanged,
        onPlaybackProgressChanged = onPlaybackProgressChanged,
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
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            val provider = connectedProvider ?: return@Button
                            playlistEngine.playFrom(
                                scope = coroutineScope,
                                provider = provider,
                                tracks = selectedTracks,
                                index = 0,
                                quality = playbackEngine.streamQuality(),
                                callbacks = playlistCallbacks,
                            )
                        },
                    ) {
                        Text("Play Album")
                    }

                    Button(
                        enabled = selectedTracks.size > 1,
                        onClick = {
                            val provider = connectedProvider ?: return@Button
                            playlistEngine.playFrom(
                                scope = coroutineScope,
                                provider = provider,
                                tracks = selectedTracks.shuffled(),
                                index = 0,
                                quality = playbackEngine.streamQuality(),
                                callbacks = playlistCallbacks,
                            )
                        },
                    ) {
                        Text("Shuffle Album")
                    }
                }

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
                                    callbacks = playlistCallbacks,
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
