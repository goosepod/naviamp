package app.naviamp.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.android.playback.AndroidMedia3PlaybackEngine
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackRequest
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.label
import app.naviamp.provider.navidrome.NavidromeConnection
import app.naviamp.provider.navidrome.NavidromeProvider
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NaviampAndroidApp()
        }
    }
}

@Composable
private fun NaviampAndroidApp() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val playbackEngine = remember { AndroidMedia3PlaybackEngine(context) }
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf<NavidromeProvider?>(null) }
    var validation by remember { mutableStateOf<ConnectionValidation?>(null) }
    var status by remember { mutableStateOf("Connect to Navidrome to start Android playback.") }
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var nowPlaying by remember { mutableStateOf<Track?>(null) }
    var playbackState by remember { mutableStateOf<PlaybackState>(PlaybackState.Idle) }
    var playbackProgress by remember { mutableStateOf(PlaybackProgress.Unknown) }

    DisposableEffect(playbackEngine) {
        onDispose {
            playbackEngine.release()
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(0xFF120F13),
            surface = Color(0xFF1C171C),
            primary = Color(0xFFD8B9FF),
            onPrimary = Color(0xFF28103C),
            onBackground = Color.White,
            onSurface = Color.White,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Naviamp Android", fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text(status, color = Color(0xFFD7CADF), fontSize = 13.sp)
            validation?.let {
                Text("Connected to Navidrome ${it.serverVersion}.", color = Color(0xFFD7CADF), fontSize = 13.sp)
            }

            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text("Server URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    scope.launch {
                        status = "Connecting..."
                        runCatching {
                            val connection = NavidromeConnection.fromPassword(serverUrl, username, password)
                            val nextProvider = NavidromeProvider(connection)
                            validation = nextProvider.validateConnection()
                            provider = nextProvider
                        }.onSuccess {
                            status = "Connected."
                        }.onFailure { error ->
                            status = error.message ?: "Connection failed."
                            provider = null
                            validation = null
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Connect")
            }

            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search tracks") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    val activeProvider = provider ?: return@Button
                    scope.launch {
                        status = "Searching..."
                        runCatching {
                            activeProvider.search(query, limit = 20).tracks
                        }.onSuccess { results ->
                            tracks = results
                            status = if (results.isEmpty()) "No tracks found." else "Found ${results.size} tracks."
                        }.onFailure { error ->
                            status = error.message ?: "Search failed."
                        }
                    }
                },
                enabled = provider != null && query.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Search")
            }

            nowPlaying?.let { track ->
                Text("Now Playing", fontWeight = FontWeight.Bold)
                Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artistName, color = Color(0xFFD7CADF), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${playbackState.label()} ${playbackProgress.positionSeconds?.toInt() ?: 0}s",
                    color = Color(0xFFD7CADF),
                    fontSize = 12.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = playbackEngine::pause) { Text("Pause") }
                    Button(onClick = playbackEngine::resume) { Text("Resume") }
                    Button(onClick = playbackEngine::stop) { Text("Stop") }
                }
            }

            tracks.forEach { track ->
                TrackRow(
                    track = track,
                    onClick = {
                        val activeProvider = provider
                        if (activeProvider == null) {
                            status = "Connect before playing a track."
                            return@TrackRow
                        }
                        scope.launch {
                            status = "Loading ${track.title}..."
                            runCatching {
                                activeProvider.streamUrl(StreamRequest(track.id, StreamQuality.Original))
                            }.onSuccess { streamUrl ->
                                nowPlaying = track
                                playbackEngine.play(
                                    scope = scope,
                                    request = PlaybackRequest(streamUrl),
                                    onStateChanged = { playbackState = it },
                                    onProgressChanged = { playbackProgress = it },
                                )
                                status = "Playing."
                            }.onFailure { error ->
                                status = error.message ?: "Playback failed."
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun TrackRow(
    track: Track,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            listOfNotNull(track.artistName, track.albumTitle).joinToString(" - "),
            color = Color(0xFFD7CADF),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 12.sp,
        )
    }
}
