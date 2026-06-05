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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import app.naviamp.domain.smartplaylist.SmartPlaylistDefinition
import app.naviamp.domain.smartplaylist.SmartPlaylistDraft
import app.naviamp.ui.NaviampAction
import app.naviamp.ui.NaviampActionSpec
import app.naviamp.ui.SmartPlaylistBuilderDialog
import app.naviamp.ui.playlistRowActions
import app.naviamp.ui.toSpec
import kotlinx.coroutines.launch

enum class DesktopPlaylistSortMode(val label: String) {
    Alphabetical("A-Z"),
    RecentlyPlayed("Recent"),
}

@Composable
fun DesktopPlaylistsPanel(
    appColors: DesktopAppColors,
    playlists: List<Playlist>,
    playlistTracks: (Playlist) -> List<Track>,
    recentPlaylistIds: List<String>,
    sortMode: DesktopPlaylistSortMode,
    status: String?,
    coverArtUrl: (String?) -> String?,
    onSortModeChanged: (DesktopPlaylistSortMode) -> Unit,
    onPlaylistSelected: (Playlist) -> Unit,
    onPlayPlaylist: (Playlist, Boolean) -> Unit,
    onRenamePlaylist: (Playlist) -> Unit,
    onDeletePlaylist: (Playlist) -> Unit,
    onDownloadPlaylist: (Playlist) -> Unit,
    onAddPlaylistToQueue: (Playlist) -> Unit,
    onAddPlaylistToPlaylist: (Playlist) -> Unit,
    onSmartPlaylistSave: suspend (SmartPlaylistDefinition) -> Unit,
    onSmartPlaylistUpdate: suspend (Playlist, SmartPlaylistDefinition) -> Unit,
    onSmartPlaylistLoad: suspend (Playlist) -> SmartPlaylistDefinition,
) {
    var smartPlaylistBuilderOpen by remember { mutableStateOf(false) }
    var smartPlaylistEditTarget by remember { mutableStateOf<Playlist?>(null) }
    var smartPlaylistInitialDraft by remember { mutableStateOf(SmartPlaylistDraft()) }
    var smartPlaylistLoadMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val sortedPlaylists = when (sortMode) {
        DesktopPlaylistSortMode.Alphabetical -> playlists.sortedBy { it.name.lowercase() }
        DesktopPlaylistSortMode.RecentlyPlayed -> playlists.sortedWith(
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
                IconButton(
                    onClick = {
                        smartPlaylistEditTarget = null
                        smartPlaylistInitialDraft = SmartPlaylistDraft()
                        smartPlaylistBuilderOpen = true
                    },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        DesktopNavigationIcons.Brain,
                        contentDescription = "Create smart playlist",
                        tint = appColors.primaryText,
                        modifier = Modifier.size(18.dp),
                    )
                }
                DesktopPlaylistSortMode.entries.forEach { mode ->
                    FilterChip(
                        selected = sortMode == mode,
                        onClick = { onSortModeChanged(mode) },
                        label = {
                            Icon(
                                imageVector = when (mode) {
                                    DesktopPlaylistSortMode.Alphabetical -> DesktopNavigationIcons.Alphabetical
                                    DesktopPlaylistSortMode.RecentlyPlayed -> DesktopNavigationIcons.Clock
                                },
                                contentDescription = mode.label,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        modifier = Modifier.height(28.dp),
                    )
                }
            }
        }
        status?.let {
            Text(it, color = appColors.secondaryText, fontSize = 12.sp)
        }
        smartPlaylistLoadMessage?.let {
            Text(it, color = appColors.secondaryText, fontSize = 12.sp)
        }
        if (sortedPlaylists.isEmpty()) {
            Text("No playlists yet.", color = appColors.secondaryText, fontSize = 12.sp)
        }
        sortedPlaylists.forEach { playlist ->
            PlaylistListRow(
                appColors = appColors,
                playlist = playlist,
                coverArtUrl = coverArtUrl,
                playlistCoverArtUrl = coverArtUrl(playlist.coverArtId),
                tracks = playlistTracks(playlist),
                onClick = { onPlaylistSelected(playlist) },
                onPlay = { onPlayPlaylist(playlist, false) },
                onShuffle = { onPlayPlaylist(playlist, true) },
                onRename = { onRenamePlaylist(playlist) },
                onDelete = { onDeletePlaylist(playlist) },
                onDownload = { onDownloadPlaylist(playlist) },
                onAddToQueue = { onAddPlaylistToQueue(playlist) },
                onAddToPlaylist = { onAddPlaylistToPlaylist(playlist) },
                onEditSmartPlaylist = {
                    coroutineScope.launch {
                        runCatching {
                            onSmartPlaylistLoad(playlist)
                        }.onSuccess { definition ->
                            smartPlaylistInitialDraft = SmartPlaylistDraft.fromDefinition(definition)
                            smartPlaylistEditTarget = playlist
                            smartPlaylistBuilderOpen = true
                            smartPlaylistLoadMessage = null
                        }.onFailure { error ->
                            smartPlaylistLoadMessage = error.message ?: "Could not load smart playlist rules."
                        }
                    }
                },
            )
        }
    }
    if (smartPlaylistBuilderOpen) {
        val editTarget = smartPlaylistEditTarget
        SmartPlaylistBuilderDialog(
            colors = appColors,
            initialDraft = smartPlaylistInitialDraft,
            title = if (editTarget == null) "Smart playlist" else "Edit smart playlist",
            saveLabel = if (editTarget == null) "Save" else "Update",
            onDismissRequest = {
                smartPlaylistBuilderOpen = false
                smartPlaylistEditTarget = null
                smartPlaylistInitialDraft = SmartPlaylistDraft()
            },
            onSave = { definition ->
                if (editTarget == null) {
                    onSmartPlaylistSave(definition)
                } else {
                    onSmartPlaylistUpdate(editTarget, definition)
                }
                smartPlaylistBuilderOpen = false
                smartPlaylistEditTarget = null
                smartPlaylistInitialDraft = SmartPlaylistDraft()
            },
        )
    }
}

@Composable
private fun PlaylistListRow(
    appColors: DesktopAppColors,
    playlist: Playlist,
    coverArtUrl: (String?) -> String?,
    playlistCoverArtUrl: String?,
    tracks: List<Track>,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDownload: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onEditSmartPlaylist: () -> Unit,
) {
    DesktopMediaRow(appColors = appColors, onClick = onClick) {
        if (playlistCoverArtUrl != null) {
            DesktopCoverArtThumb(appColors = appColors, coverArtUrl = playlistCoverArtUrl, size = 38.dp, cornerRadius = 4.dp)
        } else {
            DesktopPlaylistCover(appColors = appColors, tracks = tracks, coverArtUrl = coverArtUrl, size = 38.dp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (playlist.isSmart) {
                    Icon(
                        DesktopNavigationIcons.Brain,
                        contentDescription = "Smart playlist",
                        tint = appColors.secondaryText,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Text(
                    playlist.name,
                    color = appColors.primaryText,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 13.sp,
                )
            }
            Text(playlist.summaryLabel(), color = appColors.secondaryText, fontSize = 11.sp)
        }
        DetailActionIconButton(appColors, TransportIcons.Play, "Play playlist", true, onPlay)
        DetailActionIconButton(appColors, TransportIcons.Shuffle, "Play playlist in random order", playlist.trackCount > 1, onShuffle)
        DesktopRowOverflowMenu(
            appColors = appColors,
            items = playlistRowActions(
                canDownload = true,
                canAddToQueue = true,
                canAddToPlaylist = true,
                canRename = true,
                canEditSmartPlaylist = playlist.isSmart,
                canDelete = true,
            ).mapNotNull { action ->
                when (action.action) {
                    NaviampAction.RenamePlaylist -> action.toPlaylistRowMenuItem(onRename)
                    NaviampAction.EditSmartPlaylist -> action.toPlaylistRowMenuItem(onEditSmartPlaylist)
                    NaviampAction.DeletePlaylist -> action.toPlaylistRowMenuItem(onDelete)
                    NaviampAction.DownloadPlaylist -> action.toPlaylistRowMenuItem(onDownload)
                    NaviampAction.AddToQueue -> action.toPlaylistRowMenuItem(onAddToQueue)
                    NaviampAction.AddPlaylistToPlaylist -> action.toPlaylistRowMenuItem(onAddToPlaylist)
                    else -> null
                }
            },
        )
    }
}

@Composable
fun DesktopPlaylistDetailPanel(
    appColors: DesktopAppColors,
    playlist: Playlist?,
    tracks: List<Track>,
    status: String?,
    playlistCoverArtUrl: String?,
    coverArtUrl: (String?) -> String?,
    onBack: () -> Unit,
    onPlayPlaylist: () -> Unit,
    onShufflePlaylist: () -> Unit,
    onRenamePlaylist: () -> Unit,
    onDeletePlaylist: () -> Unit,
    onDownloadPlaylist: () -> Unit,
    onAddPlaylistToQueue: () -> Unit,
    onAddPlaylistToPlaylist: () -> Unit,
    onPlayTrack: (Int) -> Unit,
    onTrackRadio: (Track) -> Unit,
    onDownloadTrack: (Track) -> Unit,
    onAddTrackToQueue: (Track) -> Unit,
    onAddTrackToPlaylist: (Track) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = DesktopNavigationIcons.Back,
                    contentDescription = "Back",
                    tint = appColors.primaryText,
                    modifier = Modifier.size(18.dp),
                )
            }
            if (playlist?.isSmart == true) {
                Icon(
                    imageVector = DesktopNavigationIcons.Brain,
                    contentDescription = "Smart playlist",
                    tint = appColors.secondaryText,
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
            if (playlistCoverArtUrl != null) {
                DesktopCoverArtThumb(appColors = appColors, coverArtUrl = playlistCoverArtUrl, size = 96.dp, cornerRadius = 4.dp)
            } else {
                DesktopPlaylistCover(appColors = appColors, tracks = tracks, coverArtUrl = coverArtUrl, size = 96.dp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
                Text(playlist?.summaryLabel() ?: "${tracks.size} tracks", color = appColors.secondaryText, fontSize = 12.sp)
                status?.let { Text(it, color = appColors.secondaryText, fontSize = 11.sp) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val playlistActions = playlistRowActions(
                        canDownload = tracks.isNotEmpty(),
                        canAddToQueue = tracks.isNotEmpty(),
                        canAddToPlaylist = tracks.isNotEmpty(),
                        canRename = playlist != null,
                        canDelete = playlist != null,
                    )
                    val renameAction = playlistActions.playlistAction(NaviampAction.RenamePlaylist)
                    val deleteAction = playlistActions.playlistAction(NaviampAction.DeletePlaylist)
                    val downloadAction = playlistActions.playlistAction(NaviampAction.DownloadPlaylist)
                    val addToQueueAction = playlistActions.playlistAction(NaviampAction.AddToQueue)
                    val addToPlaylistAction = playlistActions.playlistAction(NaviampAction.AddPlaylistToPlaylist)
                    DetailActionIconButton(appColors, TransportIcons.Play, "Play playlist", tracks.isNotEmpty(), onPlayPlaylist)
                    DetailActionIconButton(appColors, TransportIcons.Shuffle, "Play playlist in random order", tracks.size > 1, onShufflePlaylist)
                    DetailActionIconButton(appColors, renameAction.icon, renameAction.label, renameAction.enabled, onRenamePlaylist)
                    DetailActionIconButton(appColors, deleteAction.icon, deleteAction.label, deleteAction.enabled, onDeletePlaylist)
                    DetailActionIconButton(appColors, downloadAction.icon, downloadAction.label, downloadAction.enabled, onDownloadPlaylist)
                    DetailActionIconButton(appColors, addToQueueAction.icon, addToQueueAction.label, addToQueueAction.enabled, onAddPlaylistToQueue)
                    DetailActionIconButton(appColors, addToPlaylistAction.icon, addToPlaylistAction.label, addToPlaylistAction.enabled, onAddPlaylistToPlaylist)
                }
            }
        }
        tracks.forEachIndexed { index, track ->
            DesktopTrackRow(
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
                onAddToQueue = { onAddTrackToQueue(track) },
                onAddToPlaylist = { onAddTrackToPlaylist(track) },
            )
        }
    }
}

private fun NaviampActionSpec.toPlaylistRowMenuItem(onClick: () -> Unit): DesktopRowMenuItem =
    DesktopRowMenuItem(label = label, icon = icon, onClick = onClick, enabled = enabled)

private fun List<NaviampActionSpec>.playlistAction(action: NaviampAction): NaviampActionSpec =
    firstOrNull { it.action == action } ?: action.toSpec(enabled = false)

@Composable
fun DesktopPlaylistCover(
    appColors: DesktopAppColors,
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
            0 -> DesktopCoverArtThumb(appColors, null, size, 4.dp)
            1 -> DesktopCoverArtThumb(appColors, coverArtUrl(covers[0]), size, 4.dp)
            else -> {
                val cell = size / 2
                Column {
                    Row {
                        DesktopCoverArtThumb(appColors, coverArtUrl(covers[0]), cell, 0.dp)
                        DesktopCoverArtThumb(appColors, coverArtUrl(covers[1]), cell, 0.dp)
                    }
                    if (covers.size > 2) {
                        Row {
                            DesktopCoverArtThumb(appColors, coverArtUrl(covers[2]), cell, 0.dp)
                            DesktopCoverArtThumb(appColors, coverArtUrl(covers.getOrElse(3) { covers[2] }), cell, 0.dp)
                        }
                    }
                }
            }
        }
    }
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
