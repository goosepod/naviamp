package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.domain.smartplaylist.SmartPlaylistDefinition

@Composable
internal fun PlaylistsContent(
    colors: NaviampColors,
    playlists: List<SharedMediaItemUi>,
    recentPlaylistIds: List<String>,
    sortMode: SharedPlaylistSortMode,
    status: String?,
    onSortModeChanged: (SharedPlaylistSortMode) -> Unit,
    onPlaylistSelected: (SharedMediaItemUi) -> Unit,
    onPlaylistPlay: (SharedMediaItemUi, Boolean) -> Unit,
    onPlaylistDownload: (SharedMediaItemUi) -> Unit,
    onPlaylistRename: (SharedMediaItemUi, String) -> Unit,
    onPlaylistDelete: (SharedMediaItemUi) -> Unit,
    onSmartPlaylistSave: suspend (SmartPlaylistDefinition) -> Unit,
) {
    var playlistToRename by remember { mutableStateOf<SharedMediaItemUi?>(null) }
    var playlistToDelete by remember { mutableStateOf<SharedMediaItemUi?>(null) }
    var smartPlaylistBuilderOpen by remember { mutableStateOf(false) }
    val sortedPlaylists = when (sortMode) {
        SharedPlaylistSortMode.Alphabetical -> playlists.sortedBy { it.title.lowercase() }
        SharedPlaylistSortMode.RecentlyPlayed -> playlists.sortedWith(
            compareBy<SharedMediaItemUi> {
                val index = recentPlaylistIds.indexOf(it.id)
                if (index == -1) Int.MAX_VALUE else index
            }.thenBy { it.title.lowercase() },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Playlists", color = colors.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = { smartPlaylistBuilderOpen = true },
                    modifier = Modifier.size(38.dp),
                ) {
                    Icon(
                        NaviampIcons.Playlist,
                        contentDescription = "Create smart playlist",
                        tint = colors.primaryText,
                        modifier = Modifier.size(20.dp),
                    )
                }
                SharedPlaylistSortMode.entries.forEach { mode ->
                    PlaylistSortIconButton(
                        mode = mode,
                        selected = sortMode == mode,
                        colors = colors,
                        onClick = { onSortModeChanged(mode) },
                    )
                }
            }
        }
        if (sortedPlaylists.isEmpty()) {
            Text("No playlists yet.", color = colors.secondaryText, fontSize = 12.sp)
        }
        status?.let {
            Text(it, color = colors.secondaryText, fontSize = 12.sp)
        }
        sortedPlaylists.forEach { playlist ->
            PlaylistListRow(
                playlist = playlist,
                colors = colors,
                onClick = { onPlaylistSelected(playlist) },
                onPlay = { onPlaylistPlay(playlist, false) },
                onShuffle = { onPlaylistPlay(playlist, true) },
                onDownload = { onPlaylistDownload(playlist) },
                onRename = { playlistToRename = playlist },
                onDelete = { playlistToDelete = playlist },
            )
        }
    }

    playlistToRename?.let { playlist ->
        RenamePlaylistDialog(
            playlist = playlist,
            colors = colors,
            onDismiss = { playlistToRename = null },
            onConfirm = { name ->
                playlistToRename = null
                onPlaylistRename(playlist, name)
            },
        )
    }
    playlistToDelete?.let { playlist ->
        DeletePlaylistDialog(
            playlist = playlist,
            colors = colors,
            onDismiss = { playlistToDelete = null },
            onConfirm = {
                playlistToDelete = null
                onPlaylistDelete(playlist)
            },
        )
    }
    if (smartPlaylistBuilderOpen) {
        SmartPlaylistBuilderDialog(
            colors = colors,
            onDismissRequest = { smartPlaylistBuilderOpen = false },
            onSave = { definition ->
                onSmartPlaylistSave(definition)
                smartPlaylistBuilderOpen = false
            },
        )
    }
}

@Composable
private fun PlaylistSortIconButton(
    mode: SharedPlaylistSortMode,
    selected: Boolean,
    colors: NaviampColors,
    onClick: () -> Unit,
) {
    val icon = when (mode) {
        SharedPlaylistSortMode.Alphabetical -> NaviampIcons.Alphabetical
        SharedPlaylistSortMode.RecentlyPlayed -> NaviampIcons.Clock
    }
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(if (selected) colors.accent else colors.controlSurface.copy(alpha = 0.72f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = mode.label,
            tint = if (selected) colors.onAccent else colors.secondaryText,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun PlaylistListRow(
    playlist: SharedMediaItemUi,
    colors: NaviampColors,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onDownload: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(5.dp))
            .background(Color.Black.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PlaylistCoverFromUrls(
            colors = colors,
            covers = listOfNotNull(playlist.coverArtUrl).ifEmpty { playlist.coverArtUrls },
            size = 38.dp,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(playlist.title, color = colors.primaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(playlist.subtitle, color = colors.secondaryText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        MiniPlayerIconButton(colors, true, NaviampTransportIcons.Play, "Play playlist", onPlay)
        MiniPlayerIconButton(colors, playlist.meta != "1 track", NaviampTransportIcons.Shuffle, "Play playlist in random order", onShuffle)
        NaviampRowOverflowMenu(
            colors = colors,
            items = playlistRowActions(canDownload = true, canRename = true, canDelete = true).mapNotNull { action ->
                when (action.action) {
                    NaviampAction.DownloadPlaylist -> NaviampRowMenuItem(action.label, action.icon, onDownload, action.enabled)
                    NaviampAction.RenamePlaylist -> NaviampRowMenuItem(action.label, action.icon, onRename, action.enabled)
                    NaviampAction.DeletePlaylist -> NaviampRowMenuItem(action.label, action.icon, onDelete, action.enabled)
                    else -> null
                }
            },
        )
    }
}

@Composable
internal fun PlaylistDetailContent(
    colors: NaviampColors,
    detail: SharedPlaylistDetailUi,
    onBack: () -> Unit,
    onPlayPlaylist: () -> Unit,
    onShufflePlaylist: () -> Unit,
    onAddPlaylistToQueue: () -> Unit,
    onDownloadPlaylist: () -> Unit,
    onRenamePlaylist: (SharedMediaItemUi, String) -> Unit,
    onDeletePlaylist: (SharedMediaItemUi) -> Unit,
    onTrackSelected: (AndroidTrackRowUi) -> Unit,
    onTrackAddToQueue: (AndroidTrackRowUi) -> Unit,
) {
    var renameOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                Icon(NaviampIcons.Back, contentDescription = "Back", tint = colors.primaryText, modifier = Modifier.size(18.dp))
            }
            Text(detail.playlist.title, color = colors.primaryText, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PlaylistCoverFromUrls(
                colors = colors,
                covers = listOfNotNull(detail.playlist.coverArtUrl).ifEmpty {
                    detail.tracks.mapNotNull { it.coverArtUrl }.distinct().take(4)
                },
                size = 96.dp,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Text(detail.playlist.subtitle, color = colors.secondaryText, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    MiniPlayerIconButton(colors, detail.tracks.isNotEmpty(), NaviampTransportIcons.Play, "Play playlist", onPlayPlaylist)
                    MiniPlayerIconButton(colors, detail.tracks.size > 1, NaviampTransportIcons.Shuffle, "Play playlist in random order", onShufflePlaylist)
                    MiniPlayerIconButton(colors, detail.tracks.isNotEmpty(), NaviampIcons.Queue, "Add playlist to queue", onAddPlaylistToQueue)
                    MiniPlayerIconButton(colors, detail.tracks.isNotEmpty(), NaviampIcons.Downloads, "Download playlist", onDownloadPlaylist)
                    MiniPlayerIconButton(colors, true, NaviampIcons.Edit, "Rename playlist", { renameOpen = true })
                    MiniPlayerIconButton(colors, true, NaviampIcons.Trash, "Delete playlist", { deleteOpen = true })
                }
            }
        }
        detail.tracks.forEach { track ->
            TrackRow(track, colors, onTrackSelected, onAddToQueue = onTrackAddToQueue)
        }
    }

    if (renameOpen) {
        RenamePlaylistDialog(
            playlist = detail.playlist,
            colors = colors,
            onDismiss = { renameOpen = false },
            onConfirm = { name ->
                renameOpen = false
                onRenamePlaylist(detail.playlist, name)
            },
        )
    }
    if (deleteOpen) {
        DeletePlaylistDialog(
            playlist = detail.playlist,
            colors = colors,
            onDismiss = { deleteOpen = false },
            onConfirm = {
                deleteOpen = false
                onDeletePlaylist(detail.playlist)
            },
        )
    }
}

@Composable
private fun PlaylistCoverFromUrls(colors: NaviampColors, covers: List<String>, size: Dp) {
    val visibleCovers = covers.distinct().take(4)
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(4.dp)),
    ) {
        when (visibleCovers.size) {
            0 -> PlatformCoverArt(null, colors, size, 4.dp)
            1 -> PlatformCoverArt(visibleCovers[0], colors, size, 4.dp)
            else -> {
                val cell = size / 2
                Column {
                    Row {
                        PlatformCoverArt(visibleCovers[0], colors, cell, 0.dp)
                        PlatformCoverArt(visibleCovers[1], colors, cell, 0.dp)
                    }
                    Row {
                        PlatformCoverArt(visibleCovers.getOrElse(2) { visibleCovers[0] }, colors, cell, 0.dp)
                        PlatformCoverArt(
                            visibleCovers.getOrElse(3) { visibleCovers.getOrElse(2) { visibleCovers[1] } },
                            colors,
                            cell,
                            0.dp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RenamePlaylistDialog(
    playlist: SharedMediaItemUi,
    colors: NaviampColors,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(playlist.id) { mutableStateOf(playlist.title) }
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
        containerColor = colors.controlSurface,
        titleContentColor = colors.primaryText,
        textContentColor = colors.secondaryText,
    )
}

@Composable
private fun DeletePlaylistDialog(
    playlist: SharedMediaItemUi,
    colors: NaviampColors,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete playlist") },
        text = { Text("Delete ${playlist.title}? This removes the server playlist.") },
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
        containerColor = colors.controlSurface,
        titleContentColor = colors.primaryText,
        textContentColor = colors.secondaryText,
    )
}
