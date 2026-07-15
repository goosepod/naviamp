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
import androidx.compose.material3.OutlinedTextField
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
import app.naviamp.domain.settings.ConnectionFormMusicFolder
import app.naviamp.ui.NaviampAction
import app.naviamp.ui.NaviampActionSpec
import app.naviamp.ui.NaviampDetailAction
import app.naviamp.ui.NaviampPageTitle
import app.naviamp.ui.NaviampResponsiveActionRow
import app.naviamp.ui.SharedMediaItemAction
import app.naviamp.ui.SharedMediaItemActionRequest
import app.naviamp.ui.SharedMediaItemKind
import app.naviamp.ui.SharedTrackRowAction
import app.naviamp.ui.SharedTrackRowActionRequest
import app.naviamp.ui.SmartPlaylistBuilderDialog
import app.naviamp.ui.SmartPlaylistTrackList
import app.naviamp.ui.StandardPlaylistManagementList
import app.naviamp.ui.actionRequest
import app.naviamp.ui.playlistRowActions
import app.naviamp.ui.toSpec
import app.naviamp.ui.toSharedMediaItemUi
import app.naviamp.ui.toSharedTrackRowUi
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
    onPlaylistAction: (SharedMediaItemActionRequest) -> Unit,
    onRefreshPlaylists: () -> Unit,
    onSmartPlaylistSave: suspend (SmartPlaylistDefinition) -> Unit,
    onSmartPlaylistUpdate: suspend (Playlist, SmartPlaylistDefinition) -> Unit,
    onSmartPlaylistSaveWithPassword: suspend (SmartPlaylistDefinition, String) -> Unit,
    onSmartPlaylistUpdateWithPassword: suspend (Playlist, SmartPlaylistDefinition, String) -> Unit,
    onSmartPlaylistLoad: suspend (Playlist) -> SmartPlaylistDefinition,
    availableLibraries: List<ConnectionFormMusicFolder> = emptyList(),
    selectedConnectionLibraryIds: List<String> = emptyList(),
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
            NaviampPageTitle("Playlists", appColors)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = {
                        smartPlaylistEditTarget = null
                        smartPlaylistInitialDraft = SmartPlaylistDraft()
                        smartPlaylistBuilderOpen = true
                    },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        DesktopNavigationIcons.Brain,
                        contentDescription = "Create smart playlist",
                        tint = appColors.primaryText,
                        modifier = Modifier.size(24.dp),
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
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        modifier = Modifier.height(34.dp),
                    )
                }
                DesktopPageOverflowMenu(
                    appColors = appColors,
                    onRefresh = onRefreshPlaylists,
                )
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
                onPlaylistAction = onPlaylistAction,
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
            availableLibraries = availableLibraries,
            selectedConnectionLibraryIds = selectedConnectionLibraryIds,
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
            onSaveWithPassword = { definition, password ->
                if (editTarget == null) {
                    onSmartPlaylistSaveWithPassword(definition, password)
                } else {
                    onSmartPlaylistUpdateWithPassword(editTarget, definition, password)
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
    onPlaylistAction: (SharedMediaItemActionRequest) -> Unit,
    onEditSmartPlaylist: () -> Unit,
) {
    val playlistItem = playlist.toSharedMediaItemUi(coverArtUrl, tracks)
    fun request(action: SharedMediaItemAction, shuffle: Boolean = false) {
        onPlaylistAction(playlistItem.actionRequest(action, kind = SharedMediaItemKind.Playlist, shuffle = shuffle))
    }
    DesktopMediaRow(appColors = appColors, onClick = { request(SharedMediaItemAction.Select) }) {
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
        DetailActionIconButton(appColors, TransportIcons.Play, "Play playlist", true) {
            request(SharedMediaItemAction.Play)
        }
        DetailActionIconButton(appColors, TransportIcons.Shuffle, "Play playlist in random order", playlist.trackCount > 1) {
            request(SharedMediaItemAction.Shuffle, shuffle = true)
        }
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
                    NaviampAction.RenamePlaylist -> action.toPlaylistRowMenuItem {
                        request(SharedMediaItemAction.Rename)
                    }
                    NaviampAction.EditSmartPlaylist -> action.toPlaylistRowMenuItem(onEditSmartPlaylist)
                    NaviampAction.DeletePlaylist -> action.toPlaylistRowMenuItem {
                        request(SharedMediaItemAction.Delete)
                    }
                    NaviampAction.DownloadPlaylist -> action.toPlaylistRowMenuItem {
                        request(SharedMediaItemAction.Download)
                    }
                    NaviampAction.AddToQueue -> action.toPlaylistRowMenuItem {
                        request(SharedMediaItemAction.AddToQueue)
                    }
                    NaviampAction.AddPlaylistToPlaylist -> action.toPlaylistRowMenuItem {
                        request(SharedMediaItemAction.AddToPlaylist)
                    }
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
    onPlaylistAction: (SharedMediaItemActionRequest) -> Unit,
    onTrackAction: (SharedTrackRowActionRequest) -> Unit,
    onUpdateStandardPlaylist: suspend (Playlist, List<Track>) -> Unit,
    onSmartPlaylistUpdate: suspend (Playlist, SmartPlaylistDefinition) -> Unit,
    onSmartPlaylistUpdateWithPassword: suspend (Playlist, SmartPlaylistDefinition, String) -> Unit,
    onSmartPlaylistLoad: suspend (Playlist) -> SmartPlaylistDefinition,
    availableLibraries: List<ConnectionFormMusicFolder> = emptyList(),
    selectedConnectionLibraryIds: List<String> = emptyList(),
) {
    var bulkToolsOpen by remember { mutableStateOf(false) }
    var smartPlaylistEditorOpen by remember { mutableStateOf(false) }
    var smartPlaylistInitialDraft by remember { mutableStateOf(SmartPlaylistDraft()) }
    var smartPlaylistLoadMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
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
            Icon(
                imageVector = if (playlist?.isSmart == true) DesktopNavigationIcons.Brain else DesktopNavigationIcons.Playlist,
                contentDescription = if (playlist?.isSmart == true) "Smart playlist" else "Playlist",
                tint = appColors.secondaryText,
                modifier = Modifier.size(18.dp),
            )
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
                    val playlistItem = playlist?.toSharedMediaItemUi(coverArtUrl, tracks)
                    fun request(action: SharedMediaItemAction, shuffle: Boolean = false) {
                        playlistItem?.let { item ->
                            onPlaylistAction(item.actionRequest(action, kind = SharedMediaItemKind.Playlist, shuffle = shuffle))
                        }
                    }
                    val playlistActions = playlistRowActions(
                        canDownload = tracks.isNotEmpty(),
                        canAddToQueue = tracks.isNotEmpty(),
                        canAddToPlaylist = tracks.isNotEmpty() && playlist?.isSmart == false,
                        canRename = playlist?.isSmart == false,
                        canDelete = playlist != null,
                    )
                    val renameAction = playlistActions.playlistAction(NaviampAction.RenamePlaylist)
                    val deleteAction = playlistActions.playlistAction(NaviampAction.DeletePlaylist)
                    val downloadAction = playlistActions.playlistAction(NaviampAction.DownloadPlaylist)
                    val addToQueueAction = playlistActions.playlistAction(NaviampAction.AddToQueue)
                    val addToPlaylistAction = playlistActions.playlistAction(NaviampAction.AddPlaylistToPlaylist)
                    NaviampResponsiveActionRow(
                        colors = appColors,
                        actions = buildList {
                            add(NaviampDetailAction("Play playlist", TransportIcons.Play, { request(SharedMediaItemAction.Play) }, tracks.isNotEmpty()))
                            add(NaviampDetailAction("Play playlist in random order", TransportIcons.Shuffle, { request(SharedMediaItemAction.Shuffle, shuffle = true) }, tracks.size > 1))
                            if (playlist?.isSmart == true) {
                                add(NaviampDetailAction("Edit smart playlist", DesktopNavigationIcons.Brain, {
                                    coroutineScope.launch {
                                        runCatching { onSmartPlaylistLoad(playlist) }
                                            .onSuccess { definition ->
                                                smartPlaylistInitialDraft = SmartPlaylistDraft.fromDefinition(definition)
                                                smartPlaylistEditorOpen = true
                                                smartPlaylistLoadMessage = null
                                            }
                                            .onFailure { error ->
                                                smartPlaylistLoadMessage = error.message ?: "Could not load smart playlist rules."
                                            }
                                    }
                                }))
                            } else if (playlist != null) {
                                add(NaviampDetailAction(renameAction.label, renameAction.icon, { request(SharedMediaItemAction.Rename) }, renameAction.enabled))
                            }
                            add(NaviampDetailAction(downloadAction.label, downloadAction.icon, { request(SharedMediaItemAction.Download) }, downloadAction.enabled))
                            add(NaviampDetailAction(addToQueueAction.label, addToQueueAction.icon, { request(SharedMediaItemAction.AddToQueue) }, addToQueueAction.enabled))
                            if (playlist?.isSmart == false) {
                                add(NaviampDetailAction(addToPlaylistAction.label, addToPlaylistAction.icon, { request(SharedMediaItemAction.AddToPlaylist) }, addToPlaylistAction.enabled))
                                add(NaviampDetailAction("Playlist tools", DesktopNavigationIcons.Settings, { bulkToolsOpen = true }, tracks.isNotEmpty()))
                            }
                            add(NaviampDetailAction(deleteAction.label, deleteAction.icon, { request(SharedMediaItemAction.Delete) }, deleteAction.enabled))
                        },
                    )
                }
                smartPlaylistLoadMessage?.let {
                    Text(it, color = appColors.secondaryText, fontSize = 11.sp)
                }
            }
        }
        if (playlist?.isSmart == true) {
            SmartPlaylistTrackList(
                colors = appColors,
                tracks = tracks.map { it.toSharedTrackRowUi(coverArtUrl) },
                onTrackSelected = { row ->
                    tracks.firstOrNull { it.id.value == row.id }?.let { track ->
                        onTrackAction(SharedTrackRowActionRequest(track.toSharedTrackRowUi(coverArtUrl), SharedTrackRowAction.Select))
                    }
                },
            )
        } else if (playlist != null) {
            StandardPlaylistManagementList(
                colors = appColors,
                initialTracks = tracks.map { it.toSharedTrackRowUi(coverArtUrl) },
                onTrackSelected = { row ->
                    onTrackAction(SharedTrackRowActionRequest(row, SharedTrackRowAction.Select))
                },
                onSave = { editedRows ->
                    val editedTracks = editedRows.map { row ->
                        tracks.firstOrNull { track -> track.id.value == row.id }
                            ?: throw IllegalArgumentException("Track ${row.title} is no longer in the playlist.")
                    }
                    onUpdateStandardPlaylist(playlist, editedTracks)
                },
            )
        }
    }
    if (bulkToolsOpen && playlist != null) {
        DesktopPlaylistBulkToolsDialog(
            appColors = appColors,
            playlist = playlist,
            tracks = tracks,
            onDismissRequest = { bulkToolsOpen = false },
            onCopyPlaylist = { name, deduplicate ->
                bulkToolsOpen = false
                val action = if (deduplicate) {
                    SharedMediaItemAction.CopyPlaylistDeduplicated
                } else {
                    SharedMediaItemAction.CopyPlaylist
                }
                onPlaylistAction(
                    playlist.toSharedMediaItemUi(coverArtUrl, tracks).actionRequest(
                        action = action,
                        kind = SharedMediaItemKind.Playlist,
                        playlistName = name,
                    ),
                )
            },
            onCreateAndAdd = { name ->
                bulkToolsOpen = false
                onPlaylistAction(
                    playlist.toSharedMediaItemUi(coverArtUrl, tracks).actionRequest(
                        action = SharedMediaItemAction.CreatePlaylistAndAdd,
                        kind = SharedMediaItemKind.Playlist,
                        playlistName = name,
                    ),
                )
            },
        )
    }
    if (smartPlaylistEditorOpen && playlist != null) {
        SmartPlaylistBuilderDialog(
            colors = appColors,
            initialDraft = smartPlaylistInitialDraft,
            title = "Edit smart playlist",
            saveLabel = "Update",
            availableLibraries = availableLibraries,
            selectedConnectionLibraryIds = selectedConnectionLibraryIds,
            onDismissRequest = {
                smartPlaylistEditorOpen = false
                smartPlaylistInitialDraft = SmartPlaylistDraft()
            },
            onSave = { definition ->
                onSmartPlaylistUpdate(playlist, definition)
                smartPlaylistEditorOpen = false
                smartPlaylistInitialDraft = SmartPlaylistDraft()
            },
            onSaveWithPassword = { definition, password ->
                onSmartPlaylistUpdateWithPassword(playlist, definition, password)
                smartPlaylistEditorOpen = false
                smartPlaylistInitialDraft = SmartPlaylistDraft()
            },
        )
    }
}

@Composable
private fun DesktopPlaylistBulkToolsDialog(
    appColors: DesktopAppColors,
    playlist: Playlist,
    tracks: List<Track>,
    onDismissRequest: () -> Unit,
    onCopyPlaylist: (String, Boolean) -> Unit,
    onCreateAndAdd: (String) -> Unit,
) {
    var copyName by remember { mutableStateOf("${playlist.name} Copy") }
    val deduplicatedCount = remember(tracks) { tracks.distinctBy { it.id }.size }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Playlist bulk tools") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${tracks.size} tracks - $deduplicatedCount unique", color = appColors.secondaryText, fontSize = 12.sp)
                OutlinedTextField(
                    value = copyName,
                    onValueChange = { copyName = it },
                    label = { Text("New playlist name") },
                    singleLine = true,
                )
                TextButton(
                    enabled = tracks.isNotEmpty() && copyName.isNotBlank(),
                    onClick = { onCopyPlaylist(copyName.trim(), false) },
                ) {
                    Text("Copy playlist")
                }
                TextButton(
                    enabled = tracks.isNotEmpty() && copyName.isNotBlank(),
                    onClick = { onCopyPlaylist(copyName.trim(), true) },
                ) {
                    Text("Copy deduplicated playlist")
                }
                TextButton(
                    enabled = tracks.isNotEmpty() && copyName.isNotBlank(),
                    onClick = { onCreateAndAdd(copyName.trim()) },
                ) {
                    Text("Create playlist and add these tracks")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Close")
            }
        },
    )
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
