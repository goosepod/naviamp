package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.naviamp.domain.settings.TrackSwipeAction
import app.naviamp.domain.settings.PlaylistEditSwipeActions
import kotlinx.coroutines.launch
import androidx.compose.runtime.withFrameNanos
import kotlin.math.roundToInt

fun <T> applyPlaylistEditTrackAction(
    tracks: List<T>,
    index: Int,
    action: TrackSwipeAction,
): List<T> {
    if (index !in tracks.indices || action == TrackSwipeAction.None) return tracks
    return when (action) {
        TrackSwipeAction.Remove -> tracks.filterIndexed { itemIndex, _ -> itemIndex != index }
        TrackSwipeAction.MoveUp -> tracks.moveItem(index, (index - 1).coerceAtLeast(0))
        TrackSwipeAction.MoveDown -> tracks.moveItem(index, (index + 1).coerceAtMost(tracks.lastIndex))
        TrackSwipeAction.MoveToTop -> tracks.moveItem(index, 0)
        TrackSwipeAction.MoveToBottom -> tracks.moveItem(index, tracks.lastIndex)
        else -> tracks
    }
}

private fun <T> List<T>.moveItem(fromIndex: Int, toIndex: Int): List<T> {
    if (fromIndex == toIndex) return this
    return toMutableList().apply {
        val item = removeAt(fromIndex)
        add(toIndex, item)
    }
}

@Composable
fun StandardPlaylistEditorDialog(
    colors: NaviampColors,
    playlistName: String,
    initialTracks: List<SharedTrackRowUi>,
    onDismissRequest: () -> Unit,
    onSave: suspend (List<SharedTrackRowUi>) -> Unit,
) {
    var tracks by remember(initialTracks) { mutableStateOf(initialTracks) }
    var undoTracks by remember(initialTracks) { mutableStateOf<List<SharedTrackRowUi>?>(null) }
    var saving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun apply(index: Int, action: TrackSwipeAction) {
        val updated = applyPlaylistEditTrackAction(tracks, index, action)
        if (updated != tracks) {
            undoTracks = tracks
            tracks = updated
            errorMessage = null
        }
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismissRequest() },
        title = { Text("Edit $playlistName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${tracks.size} tracks", color = colors.secondaryText, fontSize = 12.sp)
                    TextButton(
                        enabled = undoTracks != null && !saving,
                        onClick = {
                            undoTracks?.let { previous ->
                                val current = tracks
                                tracks = previous
                                undoTracks = current
                            }
                        },
                    ) {
                        Text("Undo")
                    }
                }
                errorMessage?.let { message ->
                    Text(message, color = colors.secondaryText, fontSize = 12.sp)
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(tracks, key = { index, track -> "${track.id}:$index" }) { index, track ->
                        val swipeSettings = LocalTrackSwipeSettings.current
                        SwipeActionContainer(
                            swipeRight = playlistEditSwipeVisual(swipeSettings.playlistEditRight) { action ->
                                apply(index, action)
                            },
                            swipeLeft = playlistEditSwipeVisual(swipeSettings.playlistEditLeft) { action ->
                                apply(index, action)
                            },
                        ) { swipeModifier ->
                            Column(
                                modifier = swipeModifier
                                    .fillMaxWidth()
                                    .background(colors.controlSurface)
                                    .padding(vertical = 2.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        "${index + 1}",
                                        color = colors.mutedText,
                                        fontSize = 11.sp,
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            track.title,
                                            color = colors.primaryText,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            track.subtitle,
                                            color = colors.secondaryText,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    PlaylistEditIconButton(NaviampIcons.MoveToTop, "Move to top", index > 0) {
                                        apply(index, TrackSwipeAction.MoveToTop)
                                    }
                                    PlaylistEditIconButton(NaviampIcons.ChevronUp, "Move up", index > 0) {
                                        apply(index, TrackSwipeAction.MoveUp)
                                    }
                                    PlaylistEditIconButton(NaviampIcons.ChevronDown, "Move down", index < tracks.lastIndex) {
                                        apply(index, TrackSwipeAction.MoveDown)
                                    }
                                    PlaylistEditIconButton(NaviampIcons.MoveToBottom, "Move to bottom", index < tracks.lastIndex) {
                                        apply(index, TrackSwipeAction.MoveToBottom)
                                    }
                                    PlaylistEditIconButton(NaviampIcons.Trash, "Remove", true) {
                                        apply(index, TrackSwipeAction.Remove)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                modifier = Modifier.testTag(StandardPlaylistSaveTestTag),
                enabled = !saving && tracks != initialTracks,
                onClick = {
                    saving = true
                    errorMessage = null
                    scope.launch {
                        runCatching { onSave(tracks) }
                            .onSuccess { onDismissRequest() }
                            .onFailure { error ->
                                saving = false
                                errorMessage = error.message ?: "Could not update playlist."
                            }
                    }
                },
            ) {
                Text(if (saving) "Saving..." else "Save")
            }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismissRequest) {
                Text("Cancel")
            }
        },
        containerColor = colors.controlSurface,
        titleContentColor = colors.primaryText,
        textContentColor = colors.secondaryText,
    )
}

@Composable
fun StandardPlaylistManagementList(
    colors: NaviampColors,
    initialTracks: List<SharedTrackRowUi>,
    onTrackSelected: (SharedTrackRowUi) -> Unit,
    onSave: suspend (List<SharedTrackRowUi>) -> Unit,
    scrollState: ScrollState? = null,
    dragViewportTop: Float = 0f,
    dragViewportBottom: Float = Float.POSITIVE_INFINITY,
) {
    var tracks by remember(initialTracks) { mutableStateOf(initialTracks) }
    var undoTracks by remember(initialTracks) { mutableStateOf<List<SharedTrackRowUi>?>(null) }
    var saving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    var dragPointerY by remember { mutableStateOf(Float.NaN) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val autoScrollEdgePx = with(density) { PlaylistDragAutoScrollEdge.toPx() }
    val minimumAutoScrollPx = with(density) { PlaylistDragMinimumAutoScroll.toPx() }
    val maximumAutoScrollPx = with(density) { PlaylistDragMaximumAutoScroll.toPx() }
    val autoScrollDelta = when {
        draggingIndex == null ||
            dragPointerY.isNaN() ||
            dragViewportBottom <= dragViewportTop ||
            scrollState == null -> 0f
        dragPointerY < dragViewportTop + autoScrollEdgePx -> {
            val proximity = ((dragViewportTop + autoScrollEdgePx - dragPointerY) / autoScrollEdgePx)
                .coerceIn(0f, 1f)
            -(minimumAutoScrollPx + (maximumAutoScrollPx - minimumAutoScrollPx) * proximity)
        }
        dragPointerY > dragViewportBottom - autoScrollEdgePx -> {
            val proximity = ((dragPointerY - (dragViewportBottom - autoScrollEdgePx)) / autoScrollEdgePx)
                .coerceIn(0f, 1f)
            minimumAutoScrollPx + (maximumAutoScrollPx - minimumAutoScrollPx) * proximity
        }
        else -> 0f
    }

    LaunchedEffect(draggingIndex, autoScrollDelta, scrollState) {
        val activeScrollState = scrollState ?: return@LaunchedEffect
        if (draggingIndex == null || autoScrollDelta == 0f) return@LaunchedEffect
        while (true) {
            val consumed = activeScrollState.scrollBy(autoScrollDelta)
            if (consumed == 0f) break
            dragOffsetY += consumed
            withFrameNanos { }
        }
    }

    fun apply(index: Int, action: TrackSwipeAction) {
        val updated = applyPlaylistEditTrackAction(tracks, index, action)
        if (updated != tracks) {
            undoTracks = tracks
            tracks = updated
            errorMessage = null
        }
    }

    fun finishDrag(rowStepPx: Float) {
        val fromIndex = draggingIndex
        if (fromIndex != null && rowStepPx > 0f) {
            val toIndex = (fromIndex + (dragOffsetY / rowStepPx).roundToInt())
                .coerceIn(tracks.indices)
            if (toIndex != fromIndex) {
                undoTracks = tracks
                tracks = tracks.moveItem(fromIndex, toIndex)
                errorMessage = null
            }
        }
        draggingIndex = null
        dragOffsetY = 0f
        dragPointerY = Float.NaN
    }

    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Playlist tracks", color = colors.primaryText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlaylistManagementActionButton(
                    colors = colors,
                    label = "Undo",
                    enabled = undoTracks != null && !saving,
                    onClick = {
                        undoTracks?.let { previous ->
                            val current = tracks
                            tracks = previous
                            undoTracks = current
                        }
                    },
                )
                PlaylistManagementActionButton(
                    colors = colors,
                    label = if (saving) "Saving..." else "Save changes",
                    enabled = tracks != initialTracks && !saving,
                    onClick = {
                        saving = true
                        errorMessage = null
                        scope.launch {
                            runCatching { onSave(tracks) }
                                .onSuccess {
                                    saving = false
                                    undoTracks = null
                                }
                                .onFailure { error ->
                                    saving = false
                                    errorMessage = error.message ?: "Could not update playlist."
                            }
                        }
                    },
                )
            }
        }
        errorMessage?.let { message ->
            Text(message, color = colors.secondaryText, fontSize = 12.sp)
        }
        tracks.forEachIndexed { index, track ->
            val swipeSettings = LocalTrackSwipeSettings.current
            val isDragging = draggingIndex == index
            SwipeActionContainer(
                modifier = Modifier.zIndex(if (isDragging) 2f else 0f),
                swipeRight = playlistEditSwipeVisual(swipeSettings.playlistEditRight) { action -> apply(index, action) },
                swipeLeft = playlistEditSwipeVisual(swipeSettings.playlistEditLeft) { action -> apply(index, action) },
                clipContent = !isDragging,
            ) { swipeModifier ->
                PlaylistManagementTrackRow(
                    colors = colors,
                    track = track,
                    index = index,
                    modifier = swipeModifier,
                    isDragging = isDragging,
                    dragOffsetY = if (isDragging) dragOffsetY else 0f,
                    dragEnabled = !saving,
                    onTrackSelected = { onTrackSelected(track) },
                    onDragStart = {
                        draggingIndex = index
                        dragOffsetY = 0f
                    },
                    onDrag = { deltaY, pointerY ->
                        dragOffsetY += deltaY
                        dragPointerY = pointerY
                    },
                    onDragEnd = ::finishDrag,
                    onDragCancel = {
                        draggingIndex = null
                        dragOffsetY = 0f
                        dragPointerY = Float.NaN
                    },
                )
            }
        }
    }
}

@Composable
fun SmartPlaylistTrackList(
    colors: NaviampColors,
    tracks: List<SharedTrackRowUi>,
    onTrackSelected: (SharedTrackRowUi) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "Generated tracks - edit the smart playlist rules to change this list",
            color = colors.secondaryText,
            fontSize = 12.sp,
        )
        tracks.forEachIndexed { index, track ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onTrackSelected(track) }
                    .padding(horizontal = 6.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("${index + 1}", color = colors.mutedText, fontSize = 11.sp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        track.title,
                        color = colors.primaryText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        track.subtitle,
                        color = colors.secondaryText,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(track.meta, color = colors.mutedText, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun PlaylistManagementTrackRow(
    colors: NaviampColors,
    track: SharedTrackRowUi,
    index: Int,
    modifier: Modifier,
    isDragging: Boolean,
    dragOffsetY: Float,
    dragEnabled: Boolean,
    onTrackSelected: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: (Float) -> Unit,
    onDragCancel: () -> Unit,
) {
    var dragHandleCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer { translationY = dragOffsetY }
            .background(if (isDragging) colors.accent.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(onClick = onTrackSelected)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("${index + 1}", color = colors.mutedText, fontSize = 11.sp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.title,
                color = colors.primaryText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.subtitle,
                color = colors.secondaryText,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(track.meta, color = colors.mutedText, fontSize = 11.sp)
        Icon(
            NaviampTransportIcons.Menu,
            contentDescription = "Drag to reorder",
            tint = if (isDragging) colors.primaryText else colors.secondaryText,
            modifier = Modifier
                .size(28.dp)
                .onGloballyPositioned { coordinates -> dragHandleCoordinates = coordinates }
                .then(
                    if (dragEnabled) {
                        Modifier.pointerInput(index) {
                            detectDragGestures(
                                onDragStart = { onDragStart() },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val pointerY = dragHandleCoordinates
                                        ?.localToWindow(change.position)
                                        ?.y
                                        ?: Float.NaN
                                    onDrag(dragAmount.y, pointerY)
                                },
                                onDragEnd = { onDragEnd(PlaylistManagementRowStep.toPx()) },
                                onDragCancel = onDragCancel,
                            )
                        }
                    } else {
                        Modifier
                    },
                )
                .padding(5.dp),
        )
    }
}

private val PlaylistManagementRowStep = 44.dp
private val PlaylistDragAutoScrollEdge = 72.dp
private val PlaylistDragMinimumAutoScroll = 3.dp
private val PlaylistDragMaximumAutoScroll = 16.dp

@Composable
private fun PlaylistManagementActionButton(
    colors: NaviampColors,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    TextButton(
        enabled = enabled,
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(
            contentColor = colors.primaryText,
            containerColor = colors.controlSurface.copy(alpha = 0.42f),
            disabledContentColor = colors.secondaryText.copy(alpha = 0.78f),
            disabledContainerColor = colors.controlSurface.copy(alpha = 0.18f),
        ),
        contentPadding = PaddingValues(horizontal = 11.dp, vertical = 4.dp),
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

internal const val StandardPlaylistSaveTestTag = "standard-playlist-save"

private fun playlistEditSwipeVisual(
    action: TrackSwipeAction,
    onTriggered: (TrackSwipeAction) -> Unit,
): TrackSwipeActionVisual? {
    if (action !in PlaylistEditSwipeActions) return null
    val (label, icon) = when (action) {
        TrackSwipeAction.Remove -> "Remove" to NaviampIcons.Trash
        TrackSwipeAction.MoveUp -> "Move up" to NaviampIcons.ChevronUp
        TrackSwipeAction.MoveDown -> "Move down" to NaviampIcons.ChevronDown
        TrackSwipeAction.MoveToTop -> "Move to top" to NaviampIcons.MoveToTop
        TrackSwipeAction.MoveToBottom -> "Move to bottom" to NaviampIcons.MoveToBottom
        else -> return null
    }
    return TrackSwipeActionVisual(
        label = label,
        icon = icon,
        background = if (action == TrackSwipeAction.Remove) {
            androidx.compose.ui.graphics.Color(0xFF9B2C2C)
        } else {
            androidx.compose.ui.graphics.Color(0xFF315D9E)
        },
        onTriggered = { onTriggered(action) },
    )
}

@Composable
private fun PlaylistEditIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(enabled = enabled, onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(18.dp))
    }
}
