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
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
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

fun playlistDragTargetIndex(
    fromIndex: Int,
    dragOffsetY: Float,
    rowStepPx: Float,
    lastIndex: Int,
): Int = if (rowStepPx <= 0f || lastIndex < 0) {
    fromIndex
} else {
    (fromIndex + (dragOffsetY / rowStepPx).roundToInt()).coerceIn(0, lastIndex)
}

fun playlistDragGapOffset(
    rowIndex: Int,
    fromIndex: Int,
    targetIndex: Int,
    rowStepPx: Float,
): Float = when {
    targetIndex > fromIndex && rowIndex in (fromIndex + 1)..targetIndex -> -rowStepPx
    targetIndex < fromIndex && rowIndex in targetIndex until fromIndex -> rowStepPx
    else -> 0f
}

private data class PlaylistManagementEntry(
    val key: String,
    val track: SharedTrackRowUi,
)

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
    var saving by remember(initialTracks) { mutableStateOf(false) }
    var errorMessage by remember(initialTracks) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val trackNumberWidth = trackNumberColumnWidth(tracks.size)

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
                                    TrackNumberColumn(
                                        number = index + 1,
                                        width = trackNumberWidth,
                                        color = colors.mutedText,
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
                        try {
                            onSave(tracks)
                            onDismissRequest()
                        } catch (error: Throwable) {
                            errorMessage = error.message ?: "Could not update playlist."
                        } finally {
                            saving = false
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
    externallyDisplayedStatus: String? = null,
    scrollState: ScrollState? = null,
    dragViewportTop: Float = 0f,
    dragViewportBottom: Float = Float.POSITIVE_INFINITY,
) {
    var entries by remember(initialTracks) {
        mutableStateOf(initialTracks.mapIndexed { index, track ->
            PlaylistManagementEntry(key = "$index:${track.id}", track = track)
        })
    }
    var savedTracks by remember(initialTracks) { mutableStateOf(initialTracks) }
    var undoEntries by remember(initialTracks) { mutableStateOf<List<PlaylistManagementEntry>?>(null) }
    var saving by remember(initialTracks) { mutableStateOf(false) }
    var errorMessage by remember(initialTracks) { mutableStateOf<String?>(null) }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    var dragPointerY by remember { mutableStateOf(Float.NaN) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val autoScrollEdgePx = with(density) { PlaylistDragAutoScrollEdge.toPx() }
    val minimumAutoScrollPx = with(density) { PlaylistDragMinimumAutoScroll.toPx() }
    val maximumAutoScrollPx = with(density) { PlaylistDragMaximumAutoScroll.toPx() }
    val rowStepPx = with(density) { PlaylistManagementRowStep.toPx() }
    val trackNumberWidth = trackNumberColumnWidth(entries.size)
    val dragTargetIndex = draggingIndex?.let { fromIndex ->
        playlistDragTargetIndex(fromIndex, dragOffsetY, rowStepPx, entries.lastIndex)
    }
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

    fun updateDrag(deltaY: Float, pointerY: Float = dragPointerY) {
        if (draggingIndex == null) return
        dragOffsetY += deltaY
        dragPointerY = pointerY
    }

    LaunchedEffect(draggingIndex, autoScrollDelta, scrollState) {
        val activeScrollState = scrollState ?: return@LaunchedEffect
        if (draggingIndex == null || autoScrollDelta == 0f) return@LaunchedEffect
        while (true) {
            val consumed = activeScrollState.scrollBy(autoScrollDelta)
            if (consumed == 0f) break
            updateDrag(consumed)
            withFrameNanos { }
        }
    }

    fun apply(index: Int, action: TrackSwipeAction) {
        val updated = applyPlaylistEditTrackAction(entries, index, action)
        if (updated != entries) {
            undoEntries = entries
            entries = updated
            errorMessage = null
        }
    }

    fun finishDrag(rowStepPx: Float) {
        val fromIndex = draggingIndex
        if (fromIndex != null && rowStepPx > 0f) {
            val toIndex = playlistDragTargetIndex(fromIndex, dragOffsetY, rowStepPx, entries.lastIndex)
            if (toIndex != fromIndex) {
                undoEntries = entries
                entries = entries.moveItem(fromIndex, toIndex)
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
                    enabled = undoEntries != null && !saving,
                    onClick = {
                        undoEntries?.let { previous ->
                            val current = entries
                            entries = previous
                            undoEntries = current
                        }
                    },
                )
                PlaylistManagementActionButton(
                    colors = colors,
                    label = if (saving) "Saving..." else "Save changes",
                    enabled = entries.map { it.track } != savedTracks && !saving,
                    onClick = {
                        saving = true
                        errorMessage = null
                        scope.launch {
                            val requestedTracks = entries.map { it.track }
                            try {
                                onSave(requestedTracks)
                                savedTracks = requestedTracks
                                undoEntries = null
                            } catch (error: Throwable) {
                                errorMessage = error.message ?: "Could not update playlist."
                            } finally {
                                saving = false
                            }
                        }
                    },
                )
            }
        }
        errorMessage?.let { message ->
            if (message != externallyDisplayedStatus) {
                Text(message, color = colors.secondaryText, fontSize = 12.sp)
            }
        }
        Layout(
            modifier = Modifier.fillMaxWidth(),
            content = {
                entries.forEachIndexed { index, entry ->
                    key(entry.key) {
                    val swipeSettings = LocalTrackSwipeSettings.current
                    val isDragging = draggingIndex == index
                    SwipeActionContainer(
                        modifier = Modifier.zIndex(if (isDragging) 2f else 0f),
                        swipeRight = playlistEditSwipeVisual(swipeSettings.playlistEditRight) { action -> apply(index, action) },
                        swipeLeft = playlistEditSwipeVisual(swipeSettings.playlistEditLeft) { action -> apply(index, action) },
                    ) { swipeModifier ->
                        PlaylistManagementTrackRow(
                            colors = colors,
                            track = entry.track,
                            index = index,
                            trackNumberWidth = trackNumberWidth,
                            modifier = swipeModifier,
                            isDragging = isDragging,
                            dragEnabled = !saving,
                            onTrackSelected = { onTrackSelected(entry.track) },
                            onDragStart = {
                                draggingIndex = index
                                dragOffsetY = 0f
                            },
                            onDrag = { deltaY, pointerY ->
                                updateDrag(deltaY, pointerY)
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
            },
        ) { measurables, constraints ->
            val rowHeight = rowStepPx.roundToInt().coerceAtLeast(1)
            val rowConstraints = constraints.copy(minHeight = 0, maxHeight = rowHeight)
            val placeables = measurables.map { measurable -> measurable.measure(rowConstraints) }
            layout(constraints.maxWidth, rowHeight * placeables.size) {
                placeables.forEachIndexed { index, placeable ->
                    val y = if (index == draggingIndex) {
                        index * rowHeight + dragOffsetY.roundToInt()
                    } else {
                        index * rowHeight + playlistDragGapOffset(
                            rowIndex = index,
                            fromIndex = draggingIndex ?: index,
                            targetIndex = dragTargetIndex ?: index,
                            rowStepPx = rowHeight.toFloat(),
                        ).roundToInt()
                    }
                    placeable.placeRelative(0, y)
                }
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
    val trackNumberWidth = trackNumberColumnWidth(tracks.size)
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
                TrackNumberColumn(
                    number = index + 1,
                    width = trackNumberWidth,
                    color = colors.mutedText,
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
    trackNumberWidth: Dp,
    modifier: Modifier,
    isDragging: Boolean,
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
            .background(if (isDragging) colors.accent.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(onClick = onTrackSelected)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TrackNumberColumn(
            number = index + 1,
            width = trackNumberWidth,
            color = colors.mutedText,
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

private val PlaylistManagementRowStep = 52.dp
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
