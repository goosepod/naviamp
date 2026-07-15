package app.naviamp.ui

import app.naviamp.ui.generated.resources.Res
import app.naviamp.ui.generated.resources.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.domain.smartplaylist.SmartPlaylistDefinition
import app.naviamp.domain.smartplaylist.SmartPlaylistDraft
import app.naviamp.domain.settings.ConnectionFormMusicFolder
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PlaylistsContent(
    colors: NaviampColors,
    playlists: List<SharedMediaItemUi>,
    recentPlaylistIds: List<String>,
    sortMode: SharedPlaylistSortMode,
    status: String?,
    onSortModeChanged: (SharedPlaylistSortMode) -> Unit,
    onPlaylistAction: (SharedMediaItemActionRequest) -> Unit,
    onSmartPlaylistSave: suspend (SmartPlaylistDefinition) -> Unit,
    onSmartPlaylistUpdate: suspend (SharedMediaItemUi, SmartPlaylistDefinition) -> Unit,
    onSmartPlaylistSaveWithPassword: suspend (SmartPlaylistDefinition, String) -> Unit = { definition, _ ->
        onSmartPlaylistSave(definition)
    },
    onSmartPlaylistUpdateWithPassword: suspend (SharedMediaItemUi, SmartPlaylistDefinition, String) -> Unit = { playlist, definition, _ ->
        onSmartPlaylistUpdate(playlist, definition)
    },
    onSmartPlaylistLoad: suspend (SharedMediaItemUi) -> SmartPlaylistDefinition,
    playlistChoices: List<NaviampPlaylistChoiceUi>,
    availableLibraries: List<ConnectionFormMusicFolder> = emptyList(),
    selectedConnectionLibraryIds: List<String> = emptyList(),
) {
    var playlistToRename by remember { mutableStateOf<SharedMediaItemUi?>(null) }
    var playlistToDelete by remember { mutableStateOf<SharedMediaItemUi?>(null) }
    var playlistToAddToPlaylist by remember { mutableStateOf<SharedMediaItemUi?>(null) }
    var smartPlaylistBuilderOpen by remember { mutableStateOf(false) }
    var smartPlaylistEditTarget by remember { mutableStateOf<SharedMediaItemUi?>(null) }
    var smartPlaylistInitialDraft by remember { mutableStateOf(SmartPlaylistDraft()) }
    var smartPlaylistLoadMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val handlePlaylistAction: (SharedMediaItemActionRequest) -> Unit = { request ->
        handleSharedMediaItemAction(
            request,
            SharedMediaItemActionHandlers(
                onSelect = { onPlaylistAction(request) },
                onPlay = { _, _ -> onPlaylistAction(request) },
                onAddToQueue = { onPlaylistAction(request) },
                onDownload = { onPlaylistAction(request) },
                onAddToPlaylist = { playlist, choice ->
                    if (choice == null) playlistToAddToPlaylist = playlist else onPlaylistAction(request)
                },
                onCreatePlaylistAndAdd = { _, _ -> onPlaylistAction(request) },
                onRename = { _, _ -> onPlaylistAction(request) },
                onDelete = { onPlaylistAction(request) },
                onEditSmartPlaylist = { playlist ->
                    coroutineScope.launch {
                        runCatching { onSmartPlaylistLoad(playlist) }
                            .onSuccess { definition ->
                                smartPlaylistInitialDraft = SmartPlaylistDraft.fromDefinition(definition)
                                smartPlaylistEditTarget = playlist
                                smartPlaylistBuilderOpen = true
                                smartPlaylistLoadMessage = null
                            }
                            .onFailure { error ->
                                smartPlaylistLoadMessage = error.message.orEmpty()
                            }
                    }
                },
            ),
        )
    }
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
            Text(
                stringResource(Res.string.playlists_title),
                color = colors.primaryText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = { smartPlaylistBuilderOpen = true },
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        NaviampIcons.Brain,
                        contentDescription = stringResource(Res.string.playlists_create_smart),
                        tint = colors.primaryText,
                        modifier = Modifier.size(24.dp),
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
            Text(stringResource(Res.string.playlists_empty), color = colors.secondaryText, fontSize = 12.sp)
        }
        status?.let {
            Text(it, color = colors.secondaryText, fontSize = 12.sp)
        }
        smartPlaylistLoadMessage?.let { message ->
            Text(
                message.ifBlank { stringResource(Res.string.playlists_load_smart_failed) },
                color = colors.secondaryText,
                fontSize = 12.sp,
            )
        }
        sortedPlaylists.forEach { playlist ->
            PlaylistListRow(
                playlist = playlist,
                colors = colors,
                onAction = handlePlaylistAction,
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
                handlePlaylistAction(
                    playlist.actionRequest(
                        SharedMediaItemAction.Rename,
                        kind = SharedMediaItemKind.Playlist,
                        textValue = name,
                    ),
                )
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
                handlePlaylistAction(
                    playlist.actionRequest(SharedMediaItemAction.Delete, kind = SharedMediaItemKind.Playlist),
                )
            },
        )
    }
    playlistToAddToPlaylist?.let { playlist ->
        AddToPlaylistDialog(
            title = playlist.title,
            colors = colors,
            playlists = playlistChoices,
            status = status,
            onDismissRequest = { playlistToAddToPlaylist = null },
            onAddToExisting = { choice ->
                playlistToAddToPlaylist = null
                handlePlaylistAction(
                    playlist.actionRequest(
                        SharedMediaItemAction.AddToPlaylist,
                        kind = SharedMediaItemKind.Playlist,
                        playlistChoice = choice,
                    ),
                )
            },
            onCreateAndAdd = { name ->
                playlistToAddToPlaylist = null
                handlePlaylistAction(
                    playlist.actionRequest(
                        SharedMediaItemAction.CreatePlaylistAndAdd,
                        kind = SharedMediaItemKind.Playlist,
                        playlistName = name,
                    ),
                )
            },
        )
    }
    if (smartPlaylistBuilderOpen) {
        val editTarget = smartPlaylistEditTarget
        SmartPlaylistBuilderDialog(
            colors = colors,
            initialDraft = smartPlaylistInitialDraft,
            title = if (editTarget == null) {
                stringResource(Res.string.playlists_smart)
            } else {
                stringResource(Res.string.playlists_edit_smart)
            },
            saveLabel = if (editTarget == null) {
                stringResource(Res.string.common_save)
            } else {
                stringResource(Res.string.playlists_update)
            },
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
            .size(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (selected) colors.accent else colors.controlSurface.copy(alpha = 0.72f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = mode.label,
            tint = if (selected) colors.onAccent else colors.secondaryText,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun PlaylistListRow(
    playlist: SharedMediaItemUi,
    colors: NaviampColors,
    onAction: (SharedMediaItemActionRequest) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(5.dp))
            .background(Color.Black.copy(alpha = 0.12f))
            .clickable {
                onAction(
                    playlist.actionRequest(SharedMediaItemAction.Select, kind = SharedMediaItemKind.Playlist),
                )
            }
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MultiCoverArt(
            colors = colors,
            covers = listOfNotNull(playlist.coverArtUrl).ifEmpty { playlist.coverArtUrls },
            size = 44.dp,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (playlist.isSmartPlaylist) {
                    Icon(
                        NaviampIcons.Brain,
                        contentDescription = stringResource(Res.string.playlists_smart),
                        tint = colors.secondaryText,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Text(
                    playlist.title,
                    color = colors.primaryText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(playlist.subtitle, color = colors.secondaryText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        MiniPlayerIconButton(
            colors,
            true,
            NaviampTransportIcons.Play,
            stringResource(Res.string.playlists_play),
            onClick = {
                onAction(playlist.actionRequest(SharedMediaItemAction.Play, kind = SharedMediaItemKind.Playlist))
            },
        )
        MiniPlayerIconButton(
            colors,
            playlist.meta != "1 track",
            NaviampTransportIcons.Shuffle,
            stringResource(Res.string.playlists_shuffle),
            onClick = {
                onAction(playlist.actionRequest(SharedMediaItemAction.Shuffle, kind = SharedMediaItemKind.Playlist))
            },
        )
        NaviampRowOverflowMenu(
            colors = colors,
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
                    NaviampAction.DownloadPlaylist -> NaviampRowMenuItem(
                        action.label,
                        action.icon,
                        { onAction(playlist.actionRequest(SharedMediaItemAction.Download, kind = SharedMediaItemKind.Playlist)) },
                        action.enabled,
                    )
                    NaviampAction.KeepPlaylistDownloaded -> NaviampRowMenuItem(
                        action.label,
                        action.icon,
                        {
                            onAction(
                                playlist.actionRequest(
                                    SharedMediaItemAction.Download,
                                    kind = SharedMediaItemKind.Playlist,
                                    textValue = KeepDownloadedActionValue,
                                ),
                            )
                        },
                        action.enabled,
                    )
                    NaviampAction.AddToQueue -> NaviampRowMenuItem(
                        action.label,
                        action.icon,
                        { onAction(playlist.actionRequest(SharedMediaItemAction.AddToQueue, kind = SharedMediaItemKind.Playlist)) },
                        action.enabled,
                    )
                    NaviampAction.AddPlaylistToPlaylist -> NaviampRowMenuItem(
                        action.label,
                        action.icon,
                        { onAction(playlist.actionRequest(SharedMediaItemAction.AddToPlaylist, kind = SharedMediaItemKind.Playlist)) },
                        action.enabled,
                    )
                    NaviampAction.RenamePlaylist -> NaviampRowMenuItem(action.label, action.icon, onRename, action.enabled)
                    NaviampAction.EditSmartPlaylist -> NaviampRowMenuItem(
                        action.label,
                        action.icon,
                        {
                            onAction(
                                playlist.actionRequest(
                                    SharedMediaItemAction.EditSmartPlaylist,
                                    kind = SharedMediaItemKind.Playlist,
                                ),
                            )
                        },
                        action.enabled,
                    )
                    NaviampAction.DeletePlaylist -> NaviampRowMenuItem(action.label, action.icon, onDelete, action.enabled)
                    else -> null
                }
            },
        )
    }
}

const val KeepDownloadedActionValue = "keep-downloaded"

@Composable
internal fun PlaylistDetailContent(
    colors: NaviampColors,
    detail: SharedPlaylistDetailUi,
    onBack: () -> Unit,
    onPlayPlaylist: () -> Unit,
    onShufflePlaylist: () -> Unit,
    onAddPlaylistToQueue: () -> Unit,
    onDownloadPlaylist: () -> Unit,
    onAddPlaylistToPlaylist: (NaviampPlaylistChoiceUi?) -> Unit,
    onCreatePlaylistAndAddPlaylist: (String) -> Unit,
    onCopyPlaylist: (String, Boolean) -> Unit,
    onRenamePlaylist: (SharedMediaItemUi, String) -> Unit,
    onDeletePlaylist: (SharedMediaItemUi) -> Unit,
    onUpdateStandardPlaylist: suspend (SharedMediaItemUi, List<SharedTrackRowUi>) -> Unit,
    onSmartPlaylistUpdate: suspend (SharedMediaItemUi, SmartPlaylistDefinition) -> Unit,
    onSmartPlaylistUpdateWithPassword: suspend (SharedMediaItemUi, SmartPlaylistDefinition, String) -> Unit,
    onSmartPlaylistLoad: suspend (SharedMediaItemUi) -> SmartPlaylistDefinition,
    onTrackSelected: (SharedTrackRowUi) -> Unit,
    onTrackAddToQueue: (SharedTrackRowUi) -> Unit,
    playlistChoices: List<NaviampPlaylistChoiceUi>,
    availableLibraries: List<ConnectionFormMusicFolder> = emptyList(),
    selectedConnectionLibraryIds: List<String> = emptyList(),
) {
    var renameOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    var addToPlaylistOpen by remember { mutableStateOf(false) }
    var bulkToolsOpen by remember { mutableStateOf(false) }
    var smartPlaylistEditorOpen by remember { mutableStateOf(false) }
    var smartPlaylistInitialDraft by remember { mutableStateOf(SmartPlaylistDraft()) }
    var smartPlaylistLoadMessage by remember { mutableStateOf<String?>(null) }
    val detailScrollState = rememberScrollState()
    var detailViewportBounds by remember { mutableStateOf(Rect.Zero) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                detailViewportBounds = coordinates.boundsInWindow()
            }
            .verticalScroll(detailScrollState),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                Icon(
                    NaviampIcons.Back,
                    contentDescription = stringResource(Res.string.common_back),
                    tint = colors.primaryText,
                    modifier = Modifier.size(18.dp),
                )
            }
            Icon(
                if (detail.playlist.isSmartPlaylist) NaviampIcons.Brain else NaviampIcons.Playlist,
                contentDescription = if (detail.playlist.isSmartPlaylist) stringResource(Res.string.playlists_smart) else stringResource(Res.string.playlists_title),
                tint = colors.secondaryText,
                modifier = Modifier.size(18.dp),
            )
            Text(
                detail.playlist.title,
                color = colors.primaryText,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            MultiCoverArt(
                colors = colors,
                covers = listOfNotNull(detail.playlist.coverArtUrl).ifEmpty {
                    detail.tracks.mapNotNull { it.coverArtUrl }.distinct().take(4)
                },
                size = 96.dp,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Text(detail.playlist.subtitle, color = colors.secondaryText, fontSize = 12.sp)
                NaviampResponsiveActionRow(
                    colors = colors,
                    actions = buildList {
                        add(NaviampDetailAction(stringResource(Res.string.playlists_play), NaviampTransportIcons.Play, onPlayPlaylist, detail.tracks.isNotEmpty()))
                        add(NaviampDetailAction(stringResource(Res.string.playlists_shuffle), NaviampTransportIcons.Shuffle, onShufflePlaylist, detail.tracks.size > 1))
                        if (detail.playlist.isSmartPlaylist) {
                            add(NaviampDetailAction(stringResource(Res.string.playlists_edit_smart), NaviampIcons.Brain, {
                            coroutineScope.launch {
                                runCatching { onSmartPlaylistLoad(detail.playlist) }
                                    .onSuccess { definition ->
                                        smartPlaylistInitialDraft = SmartPlaylistDraft.fromDefinition(definition)
                                        smartPlaylistEditorOpen = true
                                        smartPlaylistLoadMessage = null
                                    }
                                    .onFailure { error ->
                                        smartPlaylistLoadMessage = error.message.orEmpty()
                                }
                            }
                            }))
                        } else {
                            add(NaviampDetailAction(stringResource(Res.string.playlists_rename_title), NaviampIcons.Edit, { renameOpen = true }))
                        }
                        add(NaviampDetailAction(stringResource(Res.string.playlists_add_to_queue), NaviampIcons.Queue, onAddPlaylistToQueue, detail.tracks.isNotEmpty()))
                        add(NaviampDetailAction(stringResource(Res.string.playlists_download), NaviampIcons.Downloads, onDownloadPlaylist, detail.tracks.isNotEmpty()))
                        if (!detail.playlist.isSmartPlaylist) {
                            add(NaviampDetailAction(stringResource(Res.string.playlists_add_to_playlist), NaviampIcons.Playlist, { addToPlaylistOpen = true }, detail.tracks.isNotEmpty()))
                            add(NaviampDetailAction(stringResource(Res.string.playlists_bulk_tools_title), NaviampIcons.Settings, { bulkToolsOpen = true }, detail.tracks.isNotEmpty()))
                        }
                        add(NaviampDetailAction(stringResource(Res.string.playlists_delete_title), NaviampIcons.Trash, { deleteOpen = true }))
                    },
                )
            }
        }
        if (detail.playlist.isSmartPlaylist) {
            SmartPlaylistTrackList(
                colors = colors,
                tracks = detail.tracks,
                onTrackSelected = onTrackSelected,
            )
        } else {
            StandardPlaylistManagementList(
                colors = colors,
                initialTracks = detail.tracks,
                onTrackSelected = onTrackSelected,
                onSave = { tracks -> onUpdateStandardPlaylist(detail.playlist, tracks) },
                scrollState = detailScrollState,
                dragViewportTop = detailViewportBounds.top,
                dragViewportBottom = detailViewportBounds.bottom,
            )
        }
        smartPlaylistLoadMessage?.let { message ->
            Text(
                message.ifBlank { stringResource(Res.string.playlists_load_smart_failed) },
                color = colors.secondaryText,
                fontSize = 12.sp,
            )
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
    if (addToPlaylistOpen) {
        AddToPlaylistDialog(
            title = detail.playlist.title,
            colors = colors,
            playlists = playlistChoices,
            status = null,
            onDismissRequest = { addToPlaylistOpen = false },
            onAddToExisting = { choice ->
                addToPlaylistOpen = false
                onAddPlaylistToPlaylist(choice)
            },
            onCreateAndAdd = { name ->
                addToPlaylistOpen = false
                onCreatePlaylistAndAddPlaylist(name)
            },
        )
    }
    if (bulkToolsOpen) {
        PlaylistBulkToolsDialog(
            detail = detail,
            colors = colors,
            playlists = playlistChoices,
            onDismissRequest = { bulkToolsOpen = false },
            onCopyPlaylist = { name, deduplicate ->
                bulkToolsOpen = false
                onCopyPlaylist(name, deduplicate)
            },
            onAddToExisting = { choice ->
                bulkToolsOpen = false
                onAddPlaylistToPlaylist(choice)
            },
            onCreateAndAdd = { name ->
                bulkToolsOpen = false
                onCreatePlaylistAndAddPlaylist(name)
            },
        )
    }
    if (smartPlaylistEditorOpen) {
        SmartPlaylistBuilderDialog(
            colors = colors,
            initialDraft = smartPlaylistInitialDraft,
            title = stringResource(Res.string.playlists_edit_smart),
            saveLabel = stringResource(Res.string.playlists_update),
            availableLibraries = availableLibraries,
            selectedConnectionLibraryIds = selectedConnectionLibraryIds,
            onDismissRequest = {
                smartPlaylistEditorOpen = false
                smartPlaylistInitialDraft = SmartPlaylistDraft()
            },
            onSave = { definition ->
                onSmartPlaylistUpdate(detail.playlist, definition)
                smartPlaylistEditorOpen = false
                smartPlaylistInitialDraft = SmartPlaylistDraft()
            },
            onSaveWithPassword = { definition, password ->
                onSmartPlaylistUpdateWithPassword(detail.playlist, definition, password)
                smartPlaylistEditorOpen = false
                smartPlaylistInitialDraft = SmartPlaylistDraft()
            },
        )
    }
}

@Composable
private fun PlaylistBulkToolsDialog(
    detail: SharedPlaylistDetailUi,
    colors: NaviampColors,
    playlists: List<NaviampPlaylistChoiceUi>,
    onDismissRequest: () -> Unit,
    onCopyPlaylist: (String, Boolean) -> Unit,
    onAddToExisting: (NaviampPlaylistChoiceUi) -> Unit,
    onCreateAndAdd: (String) -> Unit,
) {
    val defaultCopyName = stringResource(Res.string.playlists_copy_suffix, detail.playlist.title)
    var copyName by remember(detail.playlist.title) {
        mutableStateOf(defaultCopyName)
    }
    val deduplicatedCount = remember(detail.tracks) { detail.tracks.distinctBy { it.id }.size }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(Res.string.playlists_bulk_tools_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(Res.string.playlists_bulk_count, detail.tracks.size, deduplicatedCount),
                    color = colors.secondaryText,
                    fontSize = 12.sp,
                )
                OutlinedTextField(
                    value = copyName,
                    onValueChange = { copyName = it },
                    label = { Text(stringResource(Res.string.playlists_new_name)) },
                    singleLine = true,
                )
                TextButton(
                    enabled = detail.tracks.isNotEmpty() && copyName.isNotBlank(),
                    onClick = { onCopyPlaylist(copyName.trim(), false) },
                ) {
                    Text(stringResource(Res.string.playlists_copy))
                }
                TextButton(
                    enabled = detail.tracks.isNotEmpty() && copyName.isNotBlank(),
                    onClick = { onCopyPlaylist(copyName.trim(), true) },
                ) {
                    Text(stringResource(Res.string.playlists_copy_deduplicated))
                }
                Text(stringResource(Res.string.playlists_merge_copy), color = colors.secondaryText, fontSize = 12.sp)
                playlists.take(6).forEach { playlist ->
                    TextButton(onClick = { onAddToExisting(playlist) }) {
                        Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                TextButton(
                    enabled = copyName.isNotBlank(),
                    onClick = { onCreateAndAdd(copyName.trim()) },
                ) {
                    Text(stringResource(Res.string.playlists_create_and_add))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(Res.string.common_close))
            }
        },
    )
}

@Composable
fun MultiCoverArt(colors: NaviampColors, covers: List<String>, size: Dp, cornerRadius: Dp = 4.dp) {
    val visibleCovers = covers.distinct().take(4)
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius)),
    ) {
        when (visibleCovers.size) {
            0 -> PlatformCoverArt(null, colors, size, cornerRadius)
            1 -> PlatformCoverArt(visibleCovers[0], colors, size, cornerRadius)
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
fun RenamePlaylistDialog(
    playlist: SharedMediaItemUi,
    colors: NaviampColors,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(playlist.id) { mutableStateOf(playlist.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.playlists_rename_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(Res.string.playlists_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name) }) {
                Text(stringResource(Res.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.common_cancel))
            }
        },
        containerColor = colors.controlSurface,
        titleContentColor = colors.primaryText,
        textContentColor = colors.secondaryText,
    )
}

@Composable
fun DeletePlaylistDialog(
    playlist: SharedMediaItemUi,
    colors: NaviampColors,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.playlists_delete_title)) },
        text = { Text(stringResource(Res.string.playlists_delete_message, playlist.title)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(Res.string.common_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.common_cancel))
            }
        },
        containerColor = colors.controlSurface,
        titleContentColor = colors.primaryText,
        textContentColor = colors.secondaryText,
    )
}
