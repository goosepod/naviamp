package app.naviamp.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.Playlist
import app.naviamp.domain.Track

sealed interface AddToPlaylistTarget {
    val title: String

    data class ArtistTarget(val artist: Artist) : AddToPlaylistTarget {
        override val title: String = artist.name
    }

    data class AlbumTarget(val album: Album) : AddToPlaylistTarget {
        override val title: String = album.title
    }

    data class TrackTarget(val track: Track) : AddToPlaylistTarget {
        override val title: String = track.title
    }

    data class PlaylistTarget(val playlist: Playlist) : AddToPlaylistTarget {
        override val title: String = playlist.name
    }
}

@Composable
fun AddToPlaylistDialog(
    appColors: AppColors,
    target: AddToPlaylistTarget,
    playlists: List<Playlist>,
    status: String?,
    onDismiss: () -> Unit,
    onAddToExisting: (Playlist) -> Unit,
    onCreateAndAdd: (String) -> Unit,
) {
    var createNew by remember { mutableStateOf(playlists.isEmpty()) }
    var playlistName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Add ${target.title} to a server playlist.",
                    color = appColors.secondaryText,
                    fontSize = 12.sp,
                )
                status?.let {
                    Text(it, color = appColors.secondaryText, fontSize = 12.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = !createNew,
                        enabled = playlists.isNotEmpty(),
                        onClick = { createNew = false },
                        label = { Text("Existing", fontSize = 12.sp) },
                        modifier = Modifier.height(28.dp),
                    )
                    FilterChip(
                        selected = createNew,
                        onClick = { createNew = true },
                        label = { Text("New", fontSize = 12.sp) },
                        modifier = Modifier.height(28.dp),
                    )
                }
                if (createNew) {
                    OutlinedTextField(
                        value = playlistName,
                        onValueChange = { playlistName = it },
                        label = { Text("Playlist name") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        playlists.sortedBy { it.name.lowercase() }.forEach { playlist ->
                            TextButton(onClick = { onAddToExisting(playlist) }) {
                                Text(playlist.name)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (createNew) {
                TextButton(
                    enabled = playlistName.isNotBlank(),
                    onClick = { onCreateAndAdd(playlistName) },
                ) {
                    Text("Create and add")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
