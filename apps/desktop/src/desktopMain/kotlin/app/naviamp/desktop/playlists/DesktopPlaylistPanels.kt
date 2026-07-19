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
import app.naviamp.domain.smartplaylist.SmartPlaylistDefinition
import app.naviamp.domain.smartplaylist.SmartPlaylistDraft
import app.naviamp.ui.NaviampAction
import app.naviamp.ui.NaviampActionSpec
import app.naviamp.ui.NaviampDetailAction
import app.naviamp.ui.NaviampMediaActions
import app.naviamp.ui.NaviampPageTitle
import app.naviamp.ui.NaviampPlaylistDetailActions
import app.naviamp.ui.NaviampPlaylistDetailScreenUi
import app.naviamp.ui.NaviampPlaylistsActions
import app.naviamp.ui.NaviampPlaylistsScreenUi
import app.naviamp.ui.NaviampResponsiveActionRow
import app.naviamp.ui.SharedMediaItemAction
import app.naviamp.ui.SharedMediaItemActionRequest
import app.naviamp.ui.SharedMediaItemKind
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.SharedPlaylistSortMode
import app.naviamp.ui.SharedTrackRowUi
import app.naviamp.ui.SharedTrackRowAction
import app.naviamp.ui.SharedTrackRowActionRequest
import app.naviamp.ui.SmartPlaylistBuilderDialog
import app.naviamp.ui.SmartPlaylistTrackList
import app.naviamp.ui.StandardPlaylistManagementList
import app.naviamp.ui.actionRequest
import app.naviamp.ui.playlistRowActions
import app.naviamp.ui.toSpec
import kotlinx.coroutines.launch

@Composable
fun DesktopPlaylistsPanel(
    appColors: DesktopAppColors,
    screen: NaviampPlaylistsScreenUi,
    actions: NaviampPlaylistsActions,
    mediaActions: NaviampMediaActions,
) {
    var smartPlaylistBuilderOpen by remember { mutableStateOf(false) }
    var smartPlaylistEditTarget by remember { mutableStateOf<SharedMediaItemUi?>(null) }
    var smartPlaylistInitialDraft by remember { mutableStateOf(SmartPlaylistDraft()) }
    var smartPlaylistLoadMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val sortedPlaylists = when (screen.sortMode) {
        SharedPlaylistSortMode.Alphabetical -> screen.playlists.sortedBy { it.title.lowercase() }
        SharedPlaylistSortMode.RecentlyPlayed -> screen.playlists.sortedWith(
            compareBy<SharedMediaItemUi> {
                val index = screen.recentPlaylistIds.indexOf(it.id)
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
                SharedPlaylistSortMode.entries.forEach { mode ->
                    FilterChip(
                        selected = screen.sortMode == mode,
                        onClick = { actions.onSortModeChanged(mode) },
                        label = {
                            Icon(
                                imageVector = when (mode) {
                                    SharedPlaylistSortMode.Alphabetical -> DesktopNavigationIcons.Alphabetical
                                    SharedPlaylistSortMode.RecentlyPlayed -> DesktopNavigationIcons.Clock
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
                    onRefresh = actions.onRefresh,
                )
            }
        }
        screen.status?.let {
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
                onPlaylistAction = mediaActions.onMediaItemAction ?: {},
                onEditSmartPlaylist = {
                    coroutineScope.launch {
                        runCatching {
                            actions.onSmartPlaylistLoad(playlist)
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
            availableLibraries = screen.availableLibraries,
            selectedConnectionLibraryIds = screen.selectedConnectionLibraryIds,
            onDismissRequest = {
                smartPlaylistBuilderOpen = false
                smartPlaylistEditTarget = null
                smartPlaylistInitialDraft = SmartPlaylistDraft()
            },
            onSave = { definition ->
                if (editTarget == null) {
                    actions.onSmartPlaylistSave(definition)
                } else {
                    actions.onSmartPlaylistUpdate(editTarget, definition)
                }
                smartPlaylistBuilderOpen = false
                smartPlaylistEditTarget = null
                smartPlaylistInitialDraft = SmartPlaylistDraft()
            },
            onSaveWithPassword = { definition, password ->
                if (editTarget == null) {
                    actions.onSmartPlaylistSaveWithPassword(definition, password)
                } else {
                    actions.onSmartPlaylistUpdateWithPassword(editTarget, definition, password)
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
    playlist: SharedMediaItemUi,
    onPlaylistAction: (SharedMediaItemActionRequest) -> Unit,
    onEditSmartPlaylist: () -> Unit,
) {
    fun request(action: SharedMediaItemAction, shuffle: Boolean = false) {
        onPlaylistAction(playlist.actionRequest(action, kind = SharedMediaItemKind.Playlist, shuffle = shuffle))
    }
    DesktopMediaRow(appColors = appColors, onClick = { request(SharedMediaItemAction.Select) }) {
        if (playlist.coverArtUrl != null) {
            DesktopCoverArtThumb(appColors = appColors, coverArtUrl = playlist.coverArtUrl, size = 38.dp, cornerRadius = 4.dp)
        } else {
            DesktopPlaylistCover(appColors = appColors, coverArtUrls = playlist.coverArtUrls, size = 38.dp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (playlist.isSmartPlaylist) {
                    Icon(
                        DesktopNavigationIcons.Brain,
                        contentDescription = "Smart playlist",
                        tint = appColors.secondaryText,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Text(
                    playlist.title,
                    color = appColors.primaryText,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 13.sp,
                )
            }
            Text(listOf(playlist.subtitle, playlist.meta).filter { it.isNotBlank() }.joinToString(" - "), color = appColors.secondaryText, fontSize = 11.sp)
        }
        DetailActionIconButton(appColors, TransportIcons.Play, "Play playlist", true) {
            request(SharedMediaItemAction.Play)
        }
        DetailActionIconButton(appColors, TransportIcons.Shuffle, "Play playlist in random order", (playlist.trackCount ?: 0) > 1) {
            request(SharedMediaItemAction.Shuffle, shuffle = true)
        }
        DesktopRowOverflowMenu(
            appColors = appColors,
            items = playlistRowActions(
                canDownload = true,
                canKeepDownloaded = true,
                keepDownloadedActive = playlist.keepDownloadedActive,
                canAddToQueue = true,
                canAddToPlaylist = true,
                canRename = true,
                canEditSmartPlaylist = playlist.isSmartPlaylist,
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
                    NaviampAction.KeepPlaylistDownloaded -> action.toPlaylistRowMenuItem {
                        onPlaylistAction(
                            playlist.actionRequest(
                                SharedMediaItemAction.Download,
                                kind = SharedMediaItemKind.Playlist,
                                textValue = app.naviamp.ui.KeepDownloadedActionValue,
                            ),
                        )
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
    screen: NaviampPlaylistDetailScreenUi,
    actions: NaviampPlaylistDetailActions,
    playlistsActions: NaviampPlaylistsActions,
) {
    val detail = screen.detail
    val playlist = detail?.playlist ?: screen.selectedPlaylist
    val tracks = detail?.tracks.orEmpty()
    var bulkToolsOpen by remember { mutableStateOf(false) }
    var smartPlaylistEditorOpen by remember { mutableStateOf(false) }
    var smartPlaylistInitialDraft by remember { mutableStateOf(SmartPlaylistDraft()) }
    var smartPlaylistLoadMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IconButton(onClick = actions.onBack, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = DesktopNavigationIcons.Back,
                    contentDescription = "Back",
                    tint = appColors.primaryText,
                    modifier = Modifier.size(18.dp),
                )
            }
            Icon(
                imageVector = if (playlist?.isSmartPlaylist == true) DesktopNavigationIcons.Brain else DesktopNavigationIcons.Playlist,
                contentDescription = if (playlist?.isSmartPlaylist == true) "Smart playlist" else "Playlist",
                tint = appColors.secondaryText,
                modifier = Modifier.size(18.dp),
            )
            Text(
                playlist?.title ?: "Playlist",
                color = appColors.primaryText,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (playlist?.coverArtUrl != null) {
                DesktopCoverArtThumb(appColors = appColors, coverArtUrl = playlist.coverArtUrl, size = 96.dp, cornerRadius = 4.dp)
            } else {
                DesktopPlaylistCover(appColors = appColors, coverArtUrls = playlist?.coverArtUrls.orEmpty(), size = 96.dp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
                Text(playlist?.let { listOf(it.subtitle, it.meta).filter(String::isNotBlank).joinToString(" - ") } ?: "${tracks.size} tracks", color = appColors.secondaryText, fontSize = 12.sp)
                screen.status?.let { Text(it, color = appColors.secondaryText, fontSize = 11.sp) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    fun request(action: SharedMediaItemAction, shuffle: Boolean = false) {
                        playlist?.let { item ->
                            actions.onMediaItemAction(item.actionRequest(action, kind = SharedMediaItemKind.Playlist, shuffle = shuffle))
                        }
                    }
                    val playlistActions = playlistRowActions(
                        canDownload = tracks.isNotEmpty(),
                        canKeepDownloaded = playlist != null,
                        keepDownloadedActive = playlist?.keepDownloadedActive == true,
                        canAddToQueue = tracks.isNotEmpty(),
                        canAddToPlaylist = tracks.isNotEmpty() && playlist?.isSmartPlaylist == false,
                        canRename = playlist?.isSmartPlaylist == false,
                        canDelete = playlist != null,
                    )
                    val renameAction = playlistActions.playlistAction(NaviampAction.RenamePlaylist)
                    val deleteAction = playlistActions.playlistAction(NaviampAction.DeletePlaylist)
                    val downloadAction = playlistActions.playlistAction(NaviampAction.DownloadPlaylist)
                    val keepDownloadedAction = playlistActions.playlistAction(NaviampAction.KeepPlaylistDownloaded)
                    val addToQueueAction = playlistActions.playlistAction(NaviampAction.AddToQueue)
                    val addToPlaylistAction = playlistActions.playlistAction(NaviampAction.AddPlaylistToPlaylist)
                    NaviampResponsiveActionRow(
                        colors = appColors,
                        actions = buildList {
                            add(NaviampDetailAction("Play playlist", TransportIcons.Play, { request(SharedMediaItemAction.Play) }, tracks.isNotEmpty()))
                            add(NaviampDetailAction("Play playlist in random order", TransportIcons.Shuffle, { request(SharedMediaItemAction.Shuffle, shuffle = true) }, tracks.size > 1))
                            if (playlist?.isSmartPlaylist == true) {
                                add(NaviampDetailAction("Edit smart playlist", DesktopNavigationIcons.Brain, {
                                    coroutineScope.launch {
                                        runCatching { playlistsActions.onSmartPlaylistLoad(playlist) }
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
                            add(
                                NaviampDetailAction(
                                    keepDownloadedAction.label,
                                    keepDownloadedAction.icon,
                                    {
                                        playlist?.let { item ->
                                            actions.onMediaItemAction(
                                                item.actionRequest(
                                                    SharedMediaItemAction.Download,
                                                    kind = SharedMediaItemKind.Playlist,
                                                    textValue = app.naviamp.ui.KeepDownloadedActionValue,
                                                ),
                                            )
                                        }
                                    },
                                    keepDownloadedAction.enabled,
                                ),
                            )
                            add(NaviampDetailAction(addToQueueAction.label, addToQueueAction.icon, { request(SharedMediaItemAction.AddToQueue) }, addToQueueAction.enabled))
                            if (playlist?.isSmartPlaylist == false) {
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
        if (playlist?.isSmartPlaylist == true) {
            SmartPlaylistTrackList(
                colors = appColors,
                tracks = tracks,
                onTrackSelected = { row ->
                    actions.onTrackAction(SharedTrackRowActionRequest(row, SharedTrackRowAction.Select))
                },
            )
        } else if (playlist != null) {
            StandardPlaylistManagementList(
                colors = appColors,
                initialTracks = tracks,
                onTrackSelected = { row ->
                    actions.onTrackAction(SharedTrackRowActionRequest(row, SharedTrackRowAction.Select))
                },
                onSave = { editedRows ->
                    actions.onUpdateStandardPlaylist(playlist, editedRows)
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
                actions.onMediaItemAction(
                    playlist.actionRequest(
                        action = action,
                        kind = SharedMediaItemKind.Playlist,
                        playlistName = name,
                    ),
                )
            },
            onCreateAndAdd = { name ->
                bulkToolsOpen = false
                actions.onMediaItemAction(
                    playlist.actionRequest(
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
            availableLibraries = screen.availableLibraries,
            selectedConnectionLibraryIds = screen.selectedConnectionLibraryIds,
            onDismissRequest = {
                smartPlaylistEditorOpen = false
                smartPlaylistInitialDraft = SmartPlaylistDraft()
            },
            onSave = { definition ->
                playlistsActions.onSmartPlaylistUpdate(playlist, definition)
                smartPlaylistEditorOpen = false
                smartPlaylistInitialDraft = SmartPlaylistDraft()
            },
            onSaveWithPassword = { definition, password ->
                playlistsActions.onSmartPlaylistUpdateWithPassword(playlist, definition, password)
                smartPlaylistEditorOpen = false
                smartPlaylistInitialDraft = SmartPlaylistDraft()
            },
        )
    }
}

@Composable
private fun DesktopPlaylistBulkToolsDialog(
    appColors: DesktopAppColors,
    playlist: SharedMediaItemUi,
    tracks: List<SharedTrackRowUi>,
    onDismissRequest: () -> Unit,
    onCopyPlaylist: (String, Boolean) -> Unit,
    onCreateAndAdd: (String) -> Unit,
) {
    var copyName by remember { mutableStateOf("${playlist.title} Copy") }
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
    coverArtUrls: List<String>,
    size: Dp,
) {
    val covers = coverArtUrls.distinct().take(4)
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(4.dp)),
    ) {
        when (covers.size) {
            0 -> DesktopCoverArtThumb(appColors, null, size, 4.dp)
            1 -> DesktopCoverArtThumb(appColors, covers[0], size, 4.dp)
            else -> {
                val cell = size / 2
                Column {
                    Row {
                        DesktopCoverArtThumb(appColors, covers[0], cell, 0.dp)
                        DesktopCoverArtThumb(appColors, covers[1], cell, 0.dp)
                    }
                    if (covers.size > 2) {
                        Row {
                            DesktopCoverArtThumb(appColors, covers[2], cell, 0.dp)
                            DesktopCoverArtThumb(appColors, covers.getOrElse(3) { covers[2] }, cell, 0.dp)
                        }
                    }
                }
            }
        }
    }
}
