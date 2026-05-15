package app.naviamp.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.domain.Album
import app.naviamp.domain.Track
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.desktop.playback.PlaylistCallbacks
import app.naviamp.desktop.playback.PlaylistEngine
import app.naviamp.desktop.settings.PlaybackSettings
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.ui.durationLabel
import kotlinx.coroutines.launch

@Composable
fun ConnectionPanel(
    appColors: AppColors,
    connectedProvider: NavidromeProvider?,
    connectionStatus: String?,
    recentlyAddedAlbums: List<Album>,
    playbackEngine: PlaybackEngine,
    playlistEngine: PlaylistEngine,
    playbackSettings: PlaybackSettings,
    playlistCallbacks: PlaylistCallbacks,
    sessionCache: DesktopCache = DesktopCaches.session,
    onPlaybackShouldOpenPlayer: () -> Unit,
) {
    var isLoadingAlbum by remember { mutableStateOf(false) }
    var selectedAlbumTitle by remember { mutableStateOf<String?>(null) }
    var selectedTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var albumStatus by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("Naviamp", color = appColors.primaryText, style = MaterialTheme.typography.titleMedium)

        if (connectedProvider == null) {
            Text(
                connectionStatus ?: "Open Settings to connect to Navidrome.",
                color = appColors.secondaryText,
            )
            return@Column
        }

        connectionStatus?.let {
            Text(it, color = appColors.secondaryText)
        }

        if (recentlyAddedAlbums.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("Recently Added", color = appColors.primaryText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                recentlyAddedAlbums.forEach { album ->
                    TextButton(
                        enabled = !isLoadingAlbum,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp),
                        onClick = {
                            val provider = connectedProvider
                            isLoadingAlbum = true
                            selectedAlbumTitle = album.title
                            selectedTracks = emptyList()
                            albumStatus = "Loading ${album.title}..."

                            coroutineScope.launch {
                                try {
                                    val details = sessionCache.album(provider, album.id)
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
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    selectedAlbumTitle ?: "Album Tracks",
                    color = appColors.primaryText,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp),
                        onClick = {
                            onPlaybackShouldOpenPlayer()
                            playlistEngine.playFrom(
                                scope = coroutineScope,
                                provider = connectedProvider,
                                tracks = selectedTracks,
                                index = 0,
                                quality = playbackEngine.streamQuality(),
                                replayGainMode = playbackSettings.replayGainMode,
                                callbacks = playlistCallbacks,
                            )
                        },
                    ) {
                        Text("Play Album")
                    }

                    Button(
                        enabled = selectedTracks.size > 1,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp),
                        onClick = {
                            onPlaybackShouldOpenPlayer()
                            playlistEngine.playFrom(
                                scope = coroutineScope,
                                provider = connectedProvider,
                                tracks = selectedTracks.shuffled(),
                                index = 0,
                                quality = playbackEngine.streamQuality(),
                                replayGainMode = playbackSettings.replayGainMode,
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
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                            onClick = {
                                onPlaybackShouldOpenPlayer()
                                playlistEngine.playFrom(
                                    scope = coroutineScope,
                                    provider = connectedProvider,
                                    tracks = selectedTracks,
                                    index = index,
                                    quality = playbackEngine.streamQuality(),
                                    replayGainMode = playbackSettings.replayGainMode,
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
