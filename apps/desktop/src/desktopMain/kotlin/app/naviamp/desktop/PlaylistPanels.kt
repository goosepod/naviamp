package app.naviamp.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.domain.Playlist
import app.naviamp.domain.Track

enum class PlaylistSortMode(val label: String) {
    Alphabetical("A-Z"),
    RecentlyPlayed("Recent"),
}

@Composable
fun PlaylistsPanel(
    appColors: AppColors,
    playlists: List<Playlist>,
    recentPlaylistIds: List<String>,
    sortMode: PlaylistSortMode,
    status: String?,
    coverArtUrl: (String?) -> String?,
    onSortModeChanged: (PlaylistSortMode) -> Unit,
    onPlaylistSelected: (Playlist) -> Unit,
    onPlayPlaylist: (Playlist, Boolean) -> Unit,
    onRenamePlaylist: (Playlist) -> Unit,
    onDeletePlaylist: (Playlist) -> Unit,
    onDownloadPlaylist: (Playlist) -> Unit,
    onAddPlaylistToPlaylist: (Playlist) -> Unit,
) {
    val sortedPlaylists = when (sortMode) {
        PlaylistSortMode.Alphabetical -> playlists.sortedBy { it.name.lowercase() }
        PlaylistSortMode.RecentlyPlayed -> playlists.sortedWith(
            compareBy<Playlist> {
                val index = recentPlaylistIds.indexOf(it.id)
                if (index == -1) Int.MAX_VALUE else index
            }.thenBy { it.name.lowercase() },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Playlists", color = appColors.primaryText, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                PlaylistSortMode.entries.forEach { mode ->
                    FilterChip(
                        selected = sortMode == mode,
                        onClick = { onSortModeChanged(mode) },
                        label = { Text(mode.label, fontSize = 12.sp) },
                        modifier = Modifier.height(28.dp),
                    )
                }
            }
        }
        status?.let {
            Text(it, color = appColors.secondaryText, fontSize = 12.sp)
        }
        if (sortedPlaylists.isEmpty()) {
            Text("No playlists yet.", color = appColors.secondaryText, fontSize = 12.sp)
        }
        sortedPlaylists.forEach { playlist ->
            PlaylistListRow(
                appColors = appColors,
                playlist = playlist,
                coverArtUrl = coverArtUrl(playlist.coverArtId),
                onClick = { onPlaylistSelected(playlist) },
                onPlay = { onPlayPlaylist(playlist, false) },
                onShuffle = { onPlayPlaylist(playlist, true) },
                onRename = { onRenamePlaylist(playlist) },
                onDelete = { onDeletePlaylist(playlist) },
                onDownload = { onDownloadPlaylist(playlist) },
                onAddToPlaylist = { onAddPlaylistToPlaylist(playlist) },
            )
        }
    }
}

@Composable
private fun PlaylistListRow(
    appColors: AppColors,
    playlist: Playlist,
    coverArtUrl: String?,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDownload: () -> Unit,
    onAddToPlaylist: () -> Unit,
) {
    MediaRow(appColors = appColors, onClick = onClick) {
        CoverArtThumb(appColors = appColors, coverArtUrl = coverArtUrl, size = 38.dp, cornerRadius = 4.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                playlist.name,
                color = appColors.primaryText,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp,
            )
            Text(playlist.summaryLabel(), color = appColors.secondaryText, fontSize = 11.sp)
        }
        DetailActionIconButton(appColors, TransportIcons.Play, "Play playlist", true, onPlay)
        DetailActionIconButton(appColors, TransportIcons.Shuffle, "Shuffle playlist", playlist.trackCount > 1, onShuffle)
        RowOverflowMenu(
            appColors = appColors,
            items = listOf(
                RowMenuItem("Rename playlist", NavigationIcons.Edit, onRename),
                RowMenuItem("Delete playlist", NavigationIcons.Trash, onDelete),
                RowMenuItem("Download playlist", NavigationIcons.Downloads, onDownload),
                RowMenuItem("Add to playlist", NavigationIcons.Playlist, onAddToPlaylist),
            ),
        )
    }
}

@Composable
fun PlaylistDetailPanel(
    appColors: AppColors,
    playlist: Playlist?,
    tracks: List<Track>,
    status: String?,
    coverArtUrl: (String?) -> String?,
    onBack: () -> Unit,
    onPlayPlaylist: () -> Unit,
    onShufflePlaylist: () -> Unit,
    onRenamePlaylist: () -> Unit,
    onDeletePlaylist: () -> Unit,
    onDownloadPlaylist: () -> Unit,
    onAddPlaylistToPlaylist: () -> Unit,
    onPlayTrack: (Int) -> Unit,
    onTrackRadio: (Track) -> Unit,
    onDownloadTrack: (Track) -> Unit,
    onAddTrackToPlaylist: (Track) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = NavigationIcons.Back,
                    contentDescription = "Back",
                    tint = appColors.primaryText,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                playlist?.name ?: "Playlist",
                color = appColors.primaryText,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PlaylistCover(appColors = appColors, tracks = tracks, coverArtUrl = coverArtUrl, size = 96.dp)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
                Text(playlist?.summaryLabel() ?: "${tracks.size} tracks", color = appColors.secondaryText, fontSize = 12.sp)
                status?.let { Text(it, color = appColors.secondaryText, fontSize = 11.sp) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DetailActionIconButton(appColors, TransportIcons.Play, "Play playlist", tracks.isNotEmpty(), onPlayPlaylist)
                    DetailActionIconButton(appColors, TransportIcons.Shuffle, "Shuffle playlist", tracks.size > 1, onShufflePlaylist)
                    DetailActionIconButton(appColors, NavigationIcons.Edit, "Rename playlist", playlist != null, onRenamePlaylist)
                    DetailActionIconButton(appColors, NavigationIcons.Trash, "Delete playlist", playlist != null, onDeletePlaylist)
                    DetailActionIconButton(appColors, NavigationIcons.Downloads, "Download playlist", tracks.isNotEmpty(), onDownloadPlaylist)
                    DetailActionIconButton(appColors, NavigationIcons.Playlist, "Add playlist to playlist", tracks.isNotEmpty(), onAddPlaylistToPlaylist)
                }
            }
        }
        tracks.forEachIndexed { index, track ->
            TrackRow(
                appColors = appColors,
                track = track,
                index = index + 1,
                subtitle = track.artistName,
                background = false,
                horizontalPadding = 0.dp,
                verticalPadding = 0.dp,
                onClick = { onPlayTrack(index) },
                onStartRadio = { onTrackRadio(track) },
                onDownload = { onDownloadTrack(track) },
                onAddToPlaylist = { onAddTrackToPlaylist(track) },
            )
        }
    }
}

@Composable
fun PlaylistCover(
    appColors: AppColors,
    tracks: List<Track>,
    coverArtUrl: (String?) -> String?,
    size: Dp,
) {
    val covers = tracks.mapNotNull { it.coverArtId }.distinct().take(4)
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(4.dp)),
    ) {
        when (covers.size) {
            0 -> CoverArtThumb(appColors, null, size, 4.dp)
            1 -> CoverArtThumb(appColors, coverArtUrl(covers[0]), size, 4.dp)
            else -> {
                val cell = size / 2
                Column {
                    Row {
                        CoverArtThumb(appColors, coverArtUrl(covers[0]), cell, 0.dp)
                        CoverArtThumb(appColors, coverArtUrl(covers[1]), cell, 0.dp)
                    }
                    if (covers.size > 2) {
                        Row {
                            CoverArtThumb(appColors, coverArtUrl(covers[2]), cell, 0.dp)
                            CoverArtThumb(appColors, coverArtUrl(covers.getOrElse(3) { covers[2] }), cell, 0.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RenamePlaylistDialog(
    playlist: Playlist,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(playlist.id) { mutableStateOf(playlist.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Playlist name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun DeletePlaylistDialog(
    playlist: Playlist,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete playlist") },
        text = { Text("Delete ${playlist.name}? This removes the server playlist.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

fun Playlist.summaryLabel(): String =
    listOfNotNull(
        "$trackCount tracks",
        durationSeconds?.durationSummary(),
    ).joinToString(" - ")

private fun Int.durationSummary(): String {
    val hours = this / 3600
    val minutes = (this % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
