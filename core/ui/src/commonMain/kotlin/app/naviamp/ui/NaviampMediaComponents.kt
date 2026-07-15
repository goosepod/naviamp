package app.naviamp.ui

import app.naviamp.ui.generated.resources.Res
import app.naviamp.ui.generated.resources.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.settings.TrackSwipeAction
import app.naviamp.domain.settings.TrackSwipeSettings
import kotlin.math.roundToInt

val LocalTrackSwipeSettings = compositionLocalOf { TrackSwipeSettings() }

enum class TrackSwipeContext { Library, Related }

@Composable
fun TrackRow(
    track: SharedTrackRowUi,
    colors: NaviampColors,
    onTrackSelected: ((SharedTrackRowUi) -> Unit)?,
    onStartRadio: ((SharedTrackRowUi) -> Unit)? = null,
    onAddToQueue: ((SharedTrackRowUi) -> Unit)? = null,
    onDownload: ((SharedTrackRowUi) -> Unit)? = null,
    onAddToPlaylist: ((SharedTrackRowUi) -> Unit)? = null,
    canSelect: Boolean = onTrackSelected != null,
    canStartRadio: Boolean = onStartRadio != null,
    canAddToQueue: Boolean = onAddToQueue != null,
    canDownload: Boolean = onDownload != null,
    canAddToPlaylist: Boolean = onAddToPlaylist != null,
    onTrackAction: (SharedTrackRowActionRequest) -> Unit = { request ->
        when (request.action) {
            SharedTrackRowAction.Select -> onTrackSelected?.invoke(request.track)
            SharedTrackRowAction.PlayNext -> Unit
            SharedTrackRowAction.StartRadio -> onStartRadio?.invoke(request.track)
            SharedTrackRowAction.PlayTrackRadioNext,
            SharedTrackRowAction.AddTrackRadioToQueue,
            -> Unit
            SharedTrackRowAction.AddToQueue -> onAddToQueue?.invoke(request.track)
            SharedTrackRowAction.Download -> onDownload?.invoke(request.track)
            SharedTrackRowAction.AddToPlaylist -> onAddToPlaylist?.invoke(request.track)
            SharedTrackRowAction.CreatePlaylistAndAdd -> Unit
            SharedTrackRowAction.ToggleFavorite,
            SharedTrackRowAction.GoToAlbum,
            SharedTrackRowAction.GoToArtist,
            -> Unit
        }
    },
    reservePopularIndicatorSpace: Boolean = false,
    modifier: Modifier = Modifier,
    background: Boolean = false,
    horizontalPadding: Dp = 0.dp,
    verticalPadding: Dp = 6.dp,
    showCoverArt: Boolean = true,
    coverArtSize: Dp = 44.dp,
    coverArtCornerRadius: Dp = 5.dp,
    titleStyle: TextStyle = TextStyle(fontSize = 16.sp),
    subtitleStyle: TextStyle = TextStyle(fontSize = 12.sp),
    metaStyle: TextStyle = TextStyle(fontSize = 11.sp, lineHeight = 14.sp),
    titleSubtitleSpacing: Dp = 3.dp,
    showMenu: Boolean = false,
    leadingContent: (@Composable RowScope.() -> Unit)? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
    swipeContext: TrackSwipeContext = TrackSwipeContext.Library,
) {
    var detailsOpen by remember(track.id) { mutableStateOf(false) }
    val swipeSettings = LocalTrackSwipeSettings.current
    SwipeActionContainer(
        modifier = modifier,
        swipeRight = trackSwipeActionVisual(
            action = if (swipeContext == TrackSwipeContext.Related) swipeSettings.relatedRight else swipeSettings.libraryRight,
            track = track,
            canStartRadio = canStartRadio,
            canAddToQueue = canAddToQueue,
            canDownload = canDownload,
            canAddToPlaylist = canAddToPlaylist,
            onTrackAction = onTrackAction,
        ),
        swipeLeft = trackSwipeActionVisual(
            action = if (swipeContext == TrackSwipeContext.Related) swipeSettings.relatedLeft else swipeSettings.libraryLeft,
            track = track,
            canStartRadio = canStartRadio,
            canAddToQueue = canAddToQueue,
            canDownload = canDownload,
            canAddToPlaylist = canAddToPlaylist,
            onTrackAction = onTrackAction,
        ),
    ) { swipeModifier ->
    Row(
        modifier = swipeModifier
            .fillMaxWidth()
            .then(
                if (background) {
                    Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color.Black.copy(alpha = 0.12f))
                } else {
                    Modifier
                },
            )
            .let { rowModifier ->
                if (canSelect) {
                    rowModifier.clickable {
                        onTrackAction(SharedTrackRowActionRequest(track, SharedTrackRowAction.Select))
                    }
                } else {
                    rowModifier
                }
            }
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        leadingContent?.invoke(this)
        if (reservePopularIndicatorSpace || track.meta.isNotBlank()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.width(11.dp),
                ) {
                    if (track.popular) {
                        Icon(
                            imageVector = NaviampIcons.Fire,
                            contentDescription = "Popular track",
                            tint = colors.primaryText,
                            modifier = Modifier.size(10.dp),
                        )
                    }
                }
                Text(track.meta, color = colors.mutedText, style = metaStyle)
            }
        }
        if (showCoverArt) {
            PlatformCoverArt(track.coverArtUrl, colors, coverArtSize, coverArtCornerRadius)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(titleSubtitleSpacing)) {
            Text(track.title, color = colors.primaryText, style = titleStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.subtitle, color = colors.secondaryText, style = subtitleStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailingContent?.invoke(this)
        val rowActions = trackRowActions(
            canStartRadio = canStartRadio,
            canDownload = canDownload,
            canAddToQueue = canAddToQueue,
            canAddToPlaylist = canAddToPlaylist,
            canToggleFavorite = track.canToggleFavorite,
            favoriteActive = track.favoriteActive,
            hasAlbum = track.hasAlbum,
            hasArtist = track.hasArtist,
            canShowDetails = track.detailSections.isNotEmpty(),
        ).mapNotNull { action ->
            when (action.action) {
                NaviampAction.PlayNext -> if (canAddToQueue) {
                    NaviampRowMenuItem(
                        action.label,
                        action.icon,
                        { onTrackAction(SharedTrackRowActionRequest(track, SharedTrackRowAction.PlayNext)) },
                        action.enabled,
                    )
                } else {
                    null
                }
                NaviampAction.StartTrackRadio -> if (canStartRadio) {
                    NaviampRowMenuItem(
                        action.label,
                        action.icon,
                        { onTrackAction(SharedTrackRowActionRequest(track, SharedTrackRowAction.StartRadio)) },
                        action.enabled,
                    )
                } else {
                    null
                }
                NaviampAction.PlayTrackRadioNext -> if (canStartRadio) {
                    NaviampRowMenuItem(
                        action.label,
                        action.icon,
                        { onTrackAction(SharedTrackRowActionRequest(track, SharedTrackRowAction.PlayTrackRadioNext)) },
                        action.enabled,
                    )
                } else {
                    null
                }
                NaviampAction.AddTrackRadioToQueue -> if (canStartRadio) {
                    NaviampRowMenuItem(
                        action.label,
                        action.icon,
                        { onTrackAction(SharedTrackRowActionRequest(track, SharedTrackRowAction.AddTrackRadioToQueue)) },
                        action.enabled,
                    )
                } else {
                    null
                }
                NaviampAction.DownloadTrack -> if (canDownload) {
                    NaviampRowMenuItem(
                        action.label,
                        action.icon,
                        { onTrackAction(SharedTrackRowActionRequest(track, SharedTrackRowAction.Download)) },
                        action.enabled,
                    )
                } else {
                    null
                }
                NaviampAction.AddToQueue -> if (canAddToQueue) {
                    NaviampRowMenuItem(
                        action.label,
                        action.icon,
                        { onTrackAction(SharedTrackRowActionRequest(track, SharedTrackRowAction.AddToQueue)) },
                        action.enabled,
                    )
                } else {
                    null
                }
                NaviampAction.AddToPlaylist -> if (canAddToPlaylist) {
                    NaviampRowMenuItem(
                        action.label,
                        action.icon,
                        { onTrackAction(SharedTrackRowActionRequest(track, SharedTrackRowAction.AddToPlaylist)) },
                        action.enabled,
                    )
                } else {
                    null
                }
                NaviampAction.ToggleFavorite -> if (track.canToggleFavorite) {
                    NaviampRowMenuItem(
                        action.label,
                        action.icon,
                        { onTrackAction(SharedTrackRowActionRequest(track, SharedTrackRowAction.ToggleFavorite)) },
                        action.enabled,
                    )
                } else {
                    null
                }
                NaviampAction.GoToAlbum -> if (track.hasAlbum) {
                    NaviampRowMenuItem(
                        action.label,
                        action.icon,
                        { onTrackAction(SharedTrackRowActionRequest(track, SharedTrackRowAction.GoToAlbum)) },
                        action.enabled,
                    )
                } else {
                    null
                }
                NaviampAction.GoToArtist -> if (track.hasArtist) {
                    NaviampRowMenuItem(
                        action.label,
                        action.icon,
                        { onTrackAction(SharedTrackRowActionRequest(track, SharedTrackRowAction.GoToArtist)) },
                        action.enabled,
                    )
                } else {
                    null
                }
                NaviampAction.TrackDetails -> NaviampRowMenuItem(
                    action.label,
                    action.icon,
                    { detailsOpen = true },
                    action.enabled,
                )
                else -> null
            }
        }
        if (showMenu || rowActions.isNotEmpty()) {
            NaviampRowOverflowMenu(
                colors = colors,
                items = rowActions,
            )
        }
    }
    }
    if (detailsOpen) {
        TrackDetailsDialog(
            sections = track.detailSections,
            colors = colors,
            onDismissRequest = { detailsOpen = false },
        )
    }
}

private fun trackSwipeActionVisual(
    action: TrackSwipeAction,
    track: SharedTrackRowUi,
    canStartRadio: Boolean,
    canAddToQueue: Boolean,
    canDownload: Boolean,
    canAddToPlaylist: Boolean,
    onTrackAction: (SharedTrackRowActionRequest) -> Unit,
): TrackSwipeActionVisual? {
    val rowAction = when (action) {
        TrackSwipeAction.None,
        TrackSwipeAction.Play,
        TrackSwipeAction.Remove,
        TrackSwipeAction.MoveUp,
        TrackSwipeAction.MoveDown,
        TrackSwipeAction.MoveToTop,
        TrackSwipeAction.MoveToBottom,
        -> return null
        TrackSwipeAction.PlayNext -> SharedTrackRowAction.PlayNext.takeIf { canAddToQueue }
        TrackSwipeAction.AddToQueue -> SharedTrackRowAction.AddToQueue.takeIf { canAddToQueue }
        TrackSwipeAction.AddToPlaylist -> SharedTrackRowAction.AddToPlaylist.takeIf { canAddToPlaylist }
        TrackSwipeAction.Download -> SharedTrackRowAction.Download.takeIf { canDownload }
        TrackSwipeAction.StartRadio -> SharedTrackRowAction.StartRadio.takeIf { canStartRadio }
        TrackSwipeAction.ToggleFavorite -> SharedTrackRowAction.ToggleFavorite.takeIf { track.canToggleFavorite }
        TrackSwipeAction.GoToAlbum -> SharedTrackRowAction.GoToAlbum.takeIf { track.hasAlbum }
        TrackSwipeAction.GoToArtist -> SharedTrackRowAction.GoToArtist.takeIf { track.hasArtist }
    } ?: return null
    val (label, icon) = when (action) {
        TrackSwipeAction.PlayNext -> "Play next" to NaviampIcons.Queue
        TrackSwipeAction.AddToQueue -> "Add to queue" to NaviampIcons.Queue
        TrackSwipeAction.AddToPlaylist -> "Add to playlist" to NaviampIcons.Playlist
        TrackSwipeAction.Download -> "Download" to NaviampIcons.Downloads
        TrackSwipeAction.StartRadio -> "Start radio" to NaviampTransportIcons.Radio
        TrackSwipeAction.ToggleFavorite ->
            (if (track.favoriteActive) "Unfavorite" else "Favorite") to
                (if (track.favoriteActive) NaviampTransportIcons.HeartFilled else NaviampTransportIcons.Heart)
        TrackSwipeAction.GoToAlbum -> "Go to album" to NaviampIcons.Album
        TrackSwipeAction.GoToArtist -> "Go to artist" to NaviampIcons.Artist
        TrackSwipeAction.None,
        TrackSwipeAction.Play,
        TrackSwipeAction.Remove,
        TrackSwipeAction.MoveUp,
        TrackSwipeAction.MoveDown,
        TrackSwipeAction.MoveToTop,
        TrackSwipeAction.MoveToBottom,
        -> return null
    }
    return TrackSwipeActionVisual(
        label = label,
        icon = icon,
        background = Color(0xFF2E7D32),
        onTriggered = { onTrackAction(SharedTrackRowActionRequest(track, rowAction)) },
    )
}

data class TrackSwipeActionVisual(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val background: Color,
    val onTriggered: () -> Unit,
)

@Composable
fun SwipeActionContainer(
    modifier: Modifier = Modifier,
    swipeRight: TrackSwipeActionVisual? = null,
    swipeLeft: TrackSwipeActionVisual? = null,
    clipContent: Boolean = true,
    content: @Composable (Modifier) -> Unit,
) {
    if (swipeRight == null && swipeLeft == null) {
        content(modifier)
        return
    }

    val density = LocalDensity.current
    val thresholdPx = with(density) { 72.dp.toPx() }
    val maximumOffsetPx = with(density) { 132.dp.toPx() }
    val currentSwipeRight by rememberUpdatedState(swipeRight)
    val currentSwipeLeft by rememberUpdatedState(swipeLeft)
    var offsetX by remember { mutableStateOf(0f) }
    val visibleAction = when {
        offsetX > 0f -> swipeRight
        offsetX < 0f -> swipeLeft
        else -> null
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (clipContent) {
                    Modifier.clip(RoundedCornerShape(5.dp))
                } else {
                    Modifier
                },
            ),
    ) {
        visibleAction?.let { action ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier
                    .matchParentSize()
                    .background(action.background)
                    .padding(horizontal = 14.dp),
            ) {
                if (offsetX < 0f) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                Icon(action.icon, contentDescription = action.label, tint = Color.White, modifier = Modifier.size(20.dp))
                Text(action.label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (offsetX > 0f) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            }
        }
        content(
            Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .pointerInput(swipeRight != null, swipeLeft != null, thresholdPx) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            offsetX = (offsetX + dragAmount).coerceIn(
                                minimumValue = if (currentSwipeLeft == null) 0f else -maximumOffsetPx,
                                maximumValue = if (currentSwipeRight == null) 0f else maximumOffsetPx,
                            )
                        },
                        onDragEnd = {
                            val action = when {
                                offsetX >= thresholdPx -> currentSwipeRight
                                offsetX <= -thresholdPx -> currentSwipeLeft
                                else -> null
                            }
                            offsetX = 0f
                            action?.onTriggered?.invoke()
                        },
                        onDragCancel = { offsetX = 0f },
                    )
                },
        )
    }
}

fun downloadedTrackSwipeActionVisual(
    action: TrackSwipeAction,
    download: NaviampDownloadedTrackUi,
    onAction: (DownloadedTrackActionRequest) -> Unit,
): TrackSwipeActionVisual? = when (action) {
    TrackSwipeAction.Play -> TrackSwipeActionVisual(
        label = "Play",
        icon = NaviampTransportIcons.Play,
        background = Color(0xFF2E7D32),
        onTriggered = { onAction(DownloadedTrackActionRequest(download, DownloadedTrackAction.Select)) },
    )
    TrackSwipeAction.AddToPlaylist -> TrackSwipeActionVisual(
        label = "Add to playlist",
        icon = NaviampIcons.Playlist,
        background = Color(0xFF315D9E),
        onTriggered = { onAction(DownloadedTrackActionRequest(download, DownloadedTrackAction.AddToPlaylist)) },
    )
    TrackSwipeAction.Remove -> TrackSwipeActionVisual(
        label = "Remove download",
        icon = NaviampIcons.Trash,
        background = Color(0xFF9B2C2C),
        onTriggered = { onAction(DownloadedTrackActionRequest(download, DownloadedTrackAction.Remove)) },
    )
    else -> null
}

@Composable
internal fun MixCard(
    album: SharedMediaItemUi,
    colors: NaviampColors,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(154.dp)
            .height(120.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
    ) {
        PlatformCoverArt(album.coverArtUrl, colors, 154.dp, 6.dp)
        Column(
            verticalArrangement = Arrangement.Bottom,
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.68f),
                    ),
                )
                .padding(8.dp),
        ) {
            Text(
                "${album.subtitle} Mix",
                color = colors.primaryText,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                album.title,
                color = colors.secondaryText,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun HomeStationSection(
    stations: List<SharedHomeStationUi>,
    colors: NaviampColors,
    onStationSelected: (SharedHomeStationUi) -> Unit,
) {
    if (stations.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionHeader("STATIONS", colors)
        stations.forEach { station ->
            StationRow(
                title = station.title,
                subtitle = station.subtitle,
                colors = colors,
                onClick = { onStationSelected(station) },
            )
        }
    }
}

@Composable
private fun StationRow(
    title: String,
    subtitle: String,
    colors: NaviampColors,
    onClick: () -> Unit,
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
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(colors.controlSurface.copy(alpha = 0.5f)),
        ) {
            Icon(NaviampIcons.InternetRadio, contentDescription = null, tint = colors.primaryText, modifier = Modifier.size(19.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = colors.primaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = colors.secondaryText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(">", color = colors.mutedText, fontSize = 16.sp)
    }
}

@Composable
internal fun HomeSection(
    title: String,
    items: List<SharedMediaItemUi>,
    colors: NaviampColors,
    onItemSelected: ((SharedMediaItemUi) -> Unit)? = null,
    onFavoriteToggled: ((SharedMediaItemUi) -> Unit)? = null,
    itemKind: SharedMediaItemKind = SharedMediaItemKind.Unknown,
    stationStyle: Boolean = false,
    emptyText: String? = null,
) {
    if (items.isEmpty() && emptyText == null) return

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionHeader(title.uppercase(), colors)
        if (items.isEmpty()) {
            Text(emptyText.orEmpty(), color = colors.secondaryText, fontSize = 13.sp)
        } else {
            items.take(6).forEach { item ->
                if (stationStyle) {
                    StationRow(
                        title = item.title,
                        subtitle = item.subtitle,
                        colors = colors,
                        onClick = { onItemSelected?.invoke(item) },
                    )
                } else {
                    SharedMediaRow(
                        item = item,
                        colors = colors,
                        itemKind = itemKind,
                        onClick = onItemSelected?.let { { it(item) } },
                        onFavoriteToggled = onFavoriteToggled,
                    )
                }
            }
        }
    }
}

@Composable
internal fun MediaSection(
    title: String,
    items: List<SharedMediaItemUi>,
    colors: NaviampColors,
    onItemSelected: ((SharedMediaItemUi) -> Unit)? = null,
    onFavoriteToggled: ((SharedMediaItemUi) -> Unit)? = null,
    itemKind: SharedMediaItemKind = SharedMediaItemKind.Unknown,
) {
    if (items.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        SectionHeader(title.uppercase(), colors)
        items.forEach { item ->
            SharedMediaRow(
                item = item,
                colors = colors,
                itemKind = itemKind,
                onClick = onItemSelected?.let { { it(item) } },
                onFavoriteToggled = onFavoriteToggled,
            )
        }
    }
}

@Composable
fun SharedMediaRow(
    item: SharedMediaItemUi,
    colors: NaviampColors,
    onClick: (() -> Unit)? = null,
    menuItems: List<NaviampRowMenuItem> = emptyList(),
    onFavoriteToggled: ((SharedMediaItemUi) -> Unit)? = null,
    canSelect: Boolean = onClick != null,
    canToggleFavorite: Boolean = item.canFavorite && onFavoriteToggled != null,
    itemKind: SharedMediaItemKind = SharedMediaItemKind.Unknown,
    onItemAction: (SharedMediaItemActionRequest) -> Unit = { request ->
        handleSharedMediaItemAction(
            request,
            SharedMediaItemActionHandlers(
                onSelect = { onClick?.invoke() },
                onToggleFavorite = { selectedItem -> onFavoriteToggled?.invoke(selectedItem) },
            ),
        )
    },
    coverArtSize: Dp = 44.dp,
    coverArtCornerRadius: Dp = 5.dp,
    verticalPadding: Dp = 7.dp,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(5.dp))
            .background(Color.Black.copy(alpha = 0.12f))
            .let { rowModifier ->
                if (canSelect) {
                    rowModifier.clickable {
                        onItemAction(
                            item.actionRequest(SharedMediaItemAction.Select, kind = itemKind),
                        )
                    }
                } else {
                    rowModifier
                }
            }
            .padding(horizontal = 8.dp, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val coverUrls = listOfNotNull(item.coverArtUrl).ifEmpty { item.coverArtUrls }
        MultiCoverArt(colors = colors, covers = coverUrls, size = coverArtSize, cornerRadius = coverArtCornerRadius)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.title, color = colors.primaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.subtitle, color = colors.secondaryText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (item.meta.isNotBlank()) {
            Text(item.meta, color = colors.mutedText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (item.favoriteActive) {
            Icon(
                imageVector = NaviampTransportIcons.Heart,
                contentDescription = "Favorite",
                tint = colors.accent,
                modifier = Modifier.size(15.dp),
            )
        }
        val favoriteMenuItem = if (canToggleFavorite) {
            NaviampRowMenuItem(
                label = if (item.favoriteActive) "Remove favorite" else "Favorite",
                icon = NaviampTransportIcons.Heart,
                onClick = {
                    onItemAction(
                        item.actionRequest(SharedMediaItemAction.ToggleFavorite, kind = itemKind),
                    )
                },
            )
        } else {
            null
        }
        val allMenuItems = listOfNotNull(favoriteMenuItem) + menuItems
        if (allMenuItems.isNotEmpty()) {
            NaviampRowOverflowMenu(colors = colors, items = allMenuItems)
        }
    }
}

@Composable
fun SharedAlbumGridTile(
    item: SharedMediaItemUi,
    colors: NaviampColors,
    onClick: () -> Unit,
    menuItems: List<NaviampRowMenuItem> = emptyList(),
    onFavoriteToggled: ((SharedMediaItemUi) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val favoriteMenuItem = if (item.canFavorite && onFavoriteToggled != null) {
        NaviampRowMenuItem(
            label = if (item.favoriteActive) "Remove favorite" else "Favorite",
            icon = NaviampTransportIcons.Heart,
            onClick = { onFavoriteToggled(item) },
        )
    } else {
        null
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.width(144.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick),
        ) {
            PlatformCoverArt(item.coverArtUrl, colors, 144.dp, 8.dp)
            if (item.favoriteActive) {
                Icon(
                    imageVector = NaviampTransportIcons.Heart,
                    contentDescription = "Favorite",
                    tint = colors.accent,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(7.dp)
                        .size(17.dp),
                )
            }
        }
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.title,
                    color = colors.primaryText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    item.subtitle,
                    color = colors.secondaryText,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.meta.takeIf { it.isNotBlank() }?.let { meta ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        meta,
                        color = colors.mutedText,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            val allMenuItems = listOfNotNull(favoriteMenuItem) + menuItems
            if (allMenuItems.isNotEmpty()) {
                NaviampRowOverflowMenu(colors = colors, items = allMenuItems)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ArtistMixBuilderContent(
    colors: NaviampColors,
    builder: SharedArtistMixBuilderUi,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onArtistSelected: (SharedMediaItemUi) -> Unit,
    onArtistRemoved: (SharedMediaItemUi) -> Unit,
    onReset: () -> Unit,
    onPlayMix: () -> Unit,
    showPlayMixButton: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            NaviampPageTitle(stringResource(Res.string.mix_artist_builder_title), colors)
            if (builder.query.isNotBlank() || builder.selectedArtists.isNotEmpty()) {
                TextButton(onClick = onReset) {
                    Text(stringResource(Res.string.common_reset), fontSize = 12.sp)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NaviampTextField(
                value = builder.query,
                onValueChange = onQueryChanged,
                label = stringResource(Res.string.mix_search_artists),
                colors = colors,
                modifier = Modifier.weight(1f),
                onSubmit = onSearch,
            )
            Button(onClick = onSearch, modifier = Modifier.height(48.dp)) {
                Icon(
                    NaviampIcons.Search,
                    contentDescription = stringResource(Res.string.mix_search_artists),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (builder.selectedArtists.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                builder.selectedArtists.forEach { artist ->
                    ArtistMixSelectedArtist(
                        artist = artist,
                        colors = colors,
                        onRemove = { onArtistRemoved(artist) },
                        modifier = Modifier.widthIn(min = 128.dp, max = 220.dp),
                    )
                }
            }
        }
        (builder.status ?: if (builder.loading) stringResource(Res.string.mix_loading_artists) else null)?.let {
            Text(it, color = colors.secondaryText, fontSize = 12.sp)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            builder.suggestedArtists.forEach { artist ->
                ArtistMixArtistTile(
                    artist = artist,
                    colors = colors,
                    onClick = { onArtistSelected(artist) },
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(min = 148.dp, max = 220.dp),
                )
            }
        }
        if (showPlayMixButton && builder.selectedArtists.isNotEmpty()) {
            PrimaryButton(stringResource(Res.string.mix_play_mix), colors, onClick = onPlayMix)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlbumMixBuilderContent(
    colors: NaviampColors,
    builder: SharedAlbumMixBuilderUi,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onAlbumSelected: (SharedMediaItemUi) -> Unit,
    onAlbumRemoved: (SharedMediaItemUi) -> Unit,
    onReset: () -> Unit,
    onPlayMix: () -> Unit,
    showPlayMixButton: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            NaviampPageTitle(stringResource(Res.string.mix_album_builder_title), colors)
            if (builder.query.isNotBlank() || builder.selectedAlbums.isNotEmpty()) {
                TextButton(onClick = onReset) {
                    Text(stringResource(Res.string.common_reset), fontSize = 12.sp)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NaviampTextField(
                value = builder.query,
                onValueChange = onQueryChanged,
                label = stringResource(Res.string.mix_search_albums),
                colors = colors,
                modifier = Modifier.weight(1f),
                onSubmit = onSearch,
            )
            Button(onClick = onSearch, modifier = Modifier.height(48.dp)) {
                Icon(
                    NaviampIcons.Search,
                    contentDescription = stringResource(Res.string.mix_search_albums),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (builder.selectedAlbums.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                builder.selectedAlbums.forEach { album ->
                    AlbumMixSelectedAlbum(
                        album = album,
                        colors = colors,
                        onRemove = { onAlbumRemoved(album) },
                        modifier = Modifier.widthIn(min = 128.dp, max = 220.dp),
                    )
                }
            }
        }
        (builder.status ?: if (builder.loading) stringResource(Res.string.mix_loading_albums) else null)?.let {
            Text(it, color = colors.secondaryText, fontSize = 12.sp)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            builder.suggestedAlbums.forEach { album ->
                AlbumMixAlbumTile(
                    album = album,
                    colors = colors,
                    onClick = { onAlbumSelected(album) },
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(min = 148.dp, max = 220.dp),
                )
            }
        }
        if (showPlayMixButton && builder.selectedAlbums.isNotEmpty()) {
            PrimaryButton(stringResource(Res.string.mix_play_mix), colors, onClick = onPlayMix)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenreMixBuilderContent(
    colors: NaviampColors,
    builder: SharedGenreMixBuilderUi,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onGenreSelected: (SharedGenreMixItemUi) -> Unit,
    onGenreRemoved: (SharedGenreMixItemUi) -> Unit,
    onReset: () -> Unit,
    onPlayMix: () -> Unit,
    showPlayMixButton: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            NaviampPageTitle(stringResource(Res.string.mix_genre_builder_title), colors)
            if (builder.query.isNotBlank() || builder.selectedGenres.isNotEmpty()) {
                TextButton(onClick = onReset) {
                    Text(stringResource(Res.string.common_reset), fontSize = 12.sp)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NaviampTextField(
                value = builder.query,
                onValueChange = onQueryChanged,
                label = stringResource(Res.string.mix_search_genres),
                colors = colors,
                modifier = Modifier.weight(1f),
                onSubmit = onSearch,
            )
            Button(onClick = onSearch, modifier = Modifier.height(48.dp)) {
                Icon(
                    NaviampIcons.Search,
                    contentDescription = stringResource(Res.string.mix_search_genres),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (builder.selectedGenres.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                builder.selectedGenres.forEach { genre ->
                    GenreMixSelectedGenre(
                        genre = genre,
                        colors = colors,
                        onRemove = { onGenreRemoved(genre) },
                    )
                }
            }
        }
        (builder.status ?: if (builder.loading) stringResource(Res.string.mix_loading_genres) else null)?.let {
            Text(it, color = colors.secondaryText, fontSize = 12.sp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            builder.suggestedGenres.forEach { genre ->
                GenreMixGenreRow(
                    genre = genre,
                    colors = colors,
                    onClick = { onGenreSelected(genre) },
                )
            }
        }
        if (showPlayMixButton && builder.selectedGenres.isNotEmpty()) {
            PrimaryButton(stringResource(Res.string.mix_play_mix), colors, onClick = onPlayMix)
        }
    }
}

@Composable
fun SonicPathBuilderContent(
    colors: NaviampColors,
    builder: SharedSonicPathBuilderUi,
    onStartQueryChanged: (String) -> Unit,
    onEndQueryChanged: (String) -> Unit,
    onStartSearch: () -> Unit,
    onEndSearch: () -> Unit,
    onStartTrackSelected: (SharedTrackRowUi) -> Unit,
    onEndTrackSelected: (SharedTrackRowUi) -> Unit,
    onStartTrackCleared: () -> Unit,
    onEndTrackCleared: () -> Unit,
    onCountChanged: (Int) -> Unit,
    onBuildPath: () -> Unit,
    onReset: () -> Unit,
    onPlayPath: () -> Unit,
    onAddPathToQueue: () -> Unit,
    showPathActions: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            NaviampPageTitle(stringResource(Res.string.sonic_path_title), colors)
            if (
                builder.startQuery.isNotBlank() ||
                builder.endQuery.isNotBlank() ||
                builder.startTrack != null ||
                builder.endTrack != null ||
                builder.hasPath
            ) {
                TextButton(onClick = onReset) {
                    Text(stringResource(Res.string.common_reset), fontSize = 12.sp)
                }
            }
        }
        SonicPathTrackPicker(
            title = stringResource(Res.string.sonic_path_start_track),
            query = builder.startQuery,
            selectedTrack = builder.startTrack,
            suggestions = builder.startSuggestions,
            colors = colors,
            onQueryChanged = onStartQueryChanged,
            onSearch = onStartSearch,
            onTrackSelected = onStartTrackSelected,
            onTrackCleared = onStartTrackCleared,
        )
        SonicPathTrackPicker(
            title = stringResource(Res.string.sonic_path_destination_track),
            query = builder.endQuery,
            selectedTrack = builder.endTrack,
            suggestions = builder.endSuggestions,
            colors = colors,
            onQueryChanged = onEndQueryChanged,
            onSearch = onEndSearch,
            onTrackSelected = onEndTrackSelected,
            onTrackCleared = onEndTrackCleared,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(Res.string.sonic_path_max_tracks),
                color = colors.secondaryText,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            Button(
                enabled = builder.count > 2,
                onClick = { onCountChanged(builder.count - 1) },
            ) {
                Text("-")
            }
            Text(builder.count.toString(), color = colors.primaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Button(
                enabled = builder.count < 100,
                onClick = { onCountChanged(builder.count + 1) },
            ) {
                Text("+")
            }
        }
        (builder.status ?: if (builder.loading) stringResource(Res.string.sonic_path_finding) else null)?.let {
            Text(it, color = colors.secondaryText, fontSize = 12.sp)
        }
        PrimaryButton(
            label = stringResource(Res.string.sonic_path_find),
            colors = colors,
            enabled = builder.canBuild && !builder.loading,
            onClick = onBuildPath,
        )
        if (builder.pathTracks.isNotEmpty()) {
            Text(
                stringResource(Res.string.sonic_path_result_title),
                color = colors.primaryText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                builder.pathTracks.forEachIndexed { index, track ->
                    TrackRow(
                        track = track,
                        colors = colors,
                        onTrackSelected = null,
                        trailingContent = {
                            Text((index + 1).toString(), color = colors.mutedText, fontSize = 11.sp)
                        },
                    )
                }
            }
            if (showPathActions) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onPlayPath, modifier = Modifier.weight(1f)) {
                        Text(stringResource(Res.string.sonic_path_play))
                    }
                    Button(
                        onClick = onAddPathToQueue,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(Res.string.mix_add_to_queue))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SonicMixBuilderContent(
    colors: NaviampColors,
    builder: SharedSonicMixBuilderUi,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onTrackSelected: (SharedTrackRowUi) -> Unit,
    onTrackRemoved: (SharedTrackRowUi) -> Unit,
    onTargetLengthChanged: (Int) -> Unit,
    onBiasChanged: (SharedSonicMixBiasUi) -> Unit,
    onBuildMix: () -> Unit,
    onReset: () -> Unit,
    onPlayMix: () -> Unit,
    onAddMixToQueue: () -> Unit,
    showMixActions: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            NaviampPageTitle(stringResource(Res.string.nav_sonic_mix), colors)
            if (
                builder.query.isNotBlank() ||
                builder.selectedTracks.isNotEmpty() ||
                builder.suggestedTracks.isNotEmpty() ||
                builder.hasMix
            ) {
                TextButton(onClick = onReset) {
                    Text(stringResource(Res.string.common_reset), fontSize = 12.sp)
                }
            }
        }
        Text(
            stringResource(Res.string.mix_seed_tracks),
            color = colors.primaryText,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NaviampTextField(
                value = builder.query,
                onValueChange = onQueryChanged,
                label = stringResource(Res.string.mix_search_tracks),
                colors = colors,
                modifier = Modifier.weight(1f),
                onSubmit = onSearch,
            )
            Button(onClick = onSearch, modifier = Modifier.height(48.dp)) {
                Icon(
                    NaviampIcons.Search,
                    contentDescription = stringResource(Res.string.mix_search_tracks),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (builder.selectedTracks.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                builder.selectedTracks.forEach { track ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        TrackRow(
                            track = track,
                            colors = colors,
                            onTrackSelected = null,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onTrackRemoved(track) }) {
                            Text(stringResource(Res.string.mix_remove), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        if (builder.suggestedTracks.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                builder.suggestedTracks.forEach { track ->
                    TrackRow(
                        track = track,
                        colors = colors,
                        onTrackSelected = onTrackSelected,
                    )
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(Res.string.mix_target_tracks),
                color = colors.secondaryText,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            Button(
                enabled = builder.targetLength > 5,
                onClick = { onTargetLengthChanged(builder.targetLength - 5) },
            ) {
                Text("-")
            }
            Text(builder.targetLength.toString(), color = colors.primaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Button(
                enabled = builder.targetLength < 100,
                onClick = { onTargetLengthChanged(builder.targetLength + 5) },
            ) {
                Text("+")
            }
        }
        Text(stringResource(Res.string.mix_bias), color = colors.secondaryText, fontSize = 13.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SharedSonicMixBiasUi.entries.forEach { bias ->
                Button(
                    onClick = { onBiasChanged(bias) },
                    enabled = builder.bias != bias,
                ) {
                    Text(bias.labelText())
                }
            }
        }
        (builder.status ?: if (builder.loading) stringResource(Res.string.mix_building) else null)?.let {
            Text(it, color = colors.secondaryText, fontSize = 12.sp)
        }
        PrimaryButton(
            label = stringResource(Res.string.mix_build_mix),
            colors = colors,
            enabled = builder.canBuild && !builder.loading,
            onClick = onBuildMix,
        )
        if (builder.mixTracks.isNotEmpty()) {
            Text(
                stringResource(Res.string.mix_result_title),
                color = colors.primaryText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                builder.mixTracks.forEachIndexed { index, track ->
                    TrackRow(
                        track = track,
                        colors = colors,
                        onTrackSelected = null,
                        trailingContent = {
                            Text((index + 1).toString(), color = colors.mutedText, fontSize = 11.sp)
                        },
                    )
                }
            }
            if (showMixActions) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onPlayMix, modifier = Modifier.weight(1f)) {
                        Text(stringResource(Res.string.mix_play_mix))
                    }
                    Button(onClick = onAddMixToQueue, modifier = Modifier.weight(1f)) {
                        Text(stringResource(Res.string.mix_add_to_queue))
                    }
                }
            }
        }
    }
}

@Composable
private fun SonicPathTrackPicker(
    title: String,
    query: String,
    selectedTrack: SharedTrackRowUi?,
    suggestions: List<SharedTrackRowUi>,
    colors: NaviampColors,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onTrackSelected: (SharedTrackRowUi) -> Unit,
    onTrackCleared: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = colors.primaryText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NaviampTextField(
                value = query,
                onValueChange = onQueryChanged,
                label = stringResource(Res.string.mix_search_tracks),
                colors = colors,
                modifier = Modifier.weight(1f),
                onSubmit = onSearch,
            )
            Button(onClick = onSearch, modifier = Modifier.height(48.dp)) {
                Icon(
                    NaviampIcons.Search,
                    contentDescription = stringResource(Res.string.mix_search_tracks),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        selectedTrack?.let { track ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TrackRow(
                    track = track,
                    colors = colors,
                    onTrackSelected = null,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onTrackCleared) {
                    Text(stringResource(Res.string.common_clear), fontSize = 12.sp)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            suggestions.forEach { track ->
                TrackRow(
                    track = track,
                    colors = colors,
                    onTrackSelected = onTrackSelected,
                )
            }
        }
    }
}

@Composable
private fun SharedSonicMixBiasUi.labelText(): String = when (this) {
    SharedSonicMixBiasUi.Balanced -> stringResource(Res.string.mix_bias_balanced)
    SharedSonicMixBiasUi.Favorites -> stringResource(Res.string.mix_bias_favorites)
    SharedSonicMixBiasUi.Unplayed -> stringResource(Res.string.mix_bias_unplayed)
    SharedSonicMixBiasUi.Recent -> stringResource(Res.string.mix_bias_recent)
}

@Composable
private fun ArtistMixSelectedArtist(
    artist: SharedMediaItemUi,
    colors: NaviampColors,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.18f))
            .clickable(onClick = onRemove)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PlatformCoverArt(artist.coverArtUrl, colors, 30.dp, 15.dp)
        Text(artist.title, color = colors.primaryText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun GenreMixSelectedGenre(
    genre: SharedGenreMixItemUi,
    colors: NaviampColors,
    onRemove: () -> Unit,
) {
    Text(
        genre.title,
        color = colors.primaryText,
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.18f))
            .clickable(onClick = onRemove)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    )
}

@Composable
private fun GenreMixGenreRow(
    genre: SharedGenreMixItemUi,
    colors: NaviampColors,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            genre.title,
            color = colors.primaryText,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (genre.subtitle.isNotBlank()) {
            Text(genre.subtitle, color = colors.secondaryText, fontSize = 12.sp)
        }
    }
}

@Composable
private fun AlbumMixSelectedAlbum(
    album: SharedMediaItemUi,
    colors: NaviampColors,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.18f))
            .clickable(onClick = onRemove)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PlatformCoverArt(album.coverArtUrl, colors, 30.dp, 4.dp)
        Text(album.title, color = colors.primaryText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AlbumMixAlbumTile(
    album: SharedMediaItemUi,
    colors: NaviampColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .height(142.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        PlatformCoverArt(album.coverArtUrl, colors, 72.dp, 6.dp)
        Text(
            album.title,
            color = colors.primaryText,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            album.subtitle,
            color = colors.secondaryText,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ArtistMixArtistTile(
    artist: SharedMediaItemUi,
    colors: NaviampColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .height(116.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        PlatformCoverArt(artist.coverArtUrl, colors, 64.dp, 32.dp)
        Text(
            artist.title,
            color = colors.primaryText,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun InternetRadioContent(
    colors: NaviampColors,
    stations: List<InternetRadioStation>,
    status: String?,
    onStationAction: (StationRowActionRequest) -> Unit,
    onSaveStation: ((InternetRadioStation) -> Unit)? = null,
    headerActions: @Composable RowScope.() -> Unit = {},
) {
    var stationBeingEdited by remember { mutableStateOf<InternetRadioStation?>(null) }
    var stationBeingDeleted by remember { mutableStateOf<InternetRadioStation?>(null) }
    var creatingStation by remember { mutableStateOf(false) }
    val stationById = remember(stations) { stations.associateBy { station -> station.id } }
    val handleStationAction: (StationRowActionRequest) -> Unit = { request ->
        handleStationRowAction(
            request,
            StationRowActionHandlers(
                onSelect = { onStationAction(request) },
                onEdit = { item -> stationBeingEdited = stationById[item.id] },
                onDelete = { item -> stationBeingDeleted = stationById[item.id] },
            ),
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            NaviampPageTitle("Internet Radio", colors)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onSaveStation != null) {
                    Button(
                        onClick = { creatingStation = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primaryText.copy(alpha = 0.12f),
                            contentColor = colors.primaryText,
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp),
                    ) {
                        Text("New station", fontSize = 12.sp)
                    }
                }
                headerActions()
            }
        }
        status?.let { Text(it, color = colors.secondaryText, fontSize = 12.sp) }
        if (stations.isEmpty()) {
            Text("Saved internet radio stations will appear here.", color = colors.secondaryText, fontSize = 12.sp)
        }
        stations.sortedBy { it.name.lowercase() }.forEach { station ->
            val stationItem = station.toSharedMediaItemUi()
            SharedMediaRow(
                item = stationItem,
                colors = colors,
                itemKind = SharedMediaItemKind.RadioStation,
                onClick = {
                    handleStationAction(StationRowActionRequest(stationItem, StationRowAction.Select))
                },
                onItemAction = { request ->
                    when (request.action) {
                        SharedMediaItemAction.Select ->
                            handleStationAction(StationRowActionRequest(stationItem, StationRowAction.Select))
                        else ->
                            handleSharedMediaItemAction(
                                request,
                                SharedMediaItemActionHandlers(
                                    onSelect = {
                                        handleStationAction(StationRowActionRequest(stationItem, StationRowAction.Select))
                                    },
                                ),
                            )
                    }
                },
                menuItems = stationRowActions(
                    canEdit = onSaveStation != null,
                    canDelete = true,
                ).mapNotNull { action ->
                    when (action.action) {
                        NaviampAction.EditStation -> NaviampRowMenuItem(
                            label = action.label,
                            icon = action.icon,
                            onClick = {
                                handleStationAction(StationRowActionRequest(stationItem, StationRowAction.Edit))
                            },
                            enabled = action.enabled,
                        )
                        NaviampAction.DeleteStation -> NaviampRowMenuItem(
                            label = action.label,
                            icon = action.icon,
                            onClick = {
                                handleStationAction(StationRowActionRequest(stationItem, StationRowAction.Delete))
                            },
                            enabled = action.enabled,
                        )
                        else -> null
                    }
                },
            )
        }
    }

    if (creatingStation) {
        InternetRadioStationDialog(
            initialStation = null,
            onDismiss = { creatingStation = false },
            onConfirm = { station ->
                creatingStation = false
                onSaveStation?.invoke(station)
            },
        )
    }

    stationBeingEdited?.let { station ->
        InternetRadioStationDialog(
            initialStation = station,
            onDismiss = { stationBeingEdited = null },
            onConfirm = { updated ->
                stationBeingEdited = null
                onSaveStation?.invoke(updated)
            },
        )
    }

    stationBeingDeleted?.let { station ->
        AlertDialog(
            onDismissRequest = { stationBeingDeleted = null },
            title = { Text("Delete station") },
            text = { Text("Delete ${station.name}? This removes the server internet radio station.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        stationBeingDeleted = null
                        onStationAction(
                            StationRowActionRequest(
                                station = station.toSharedMediaItemUi(),
                                action = StationRowAction.Delete,
                            ),
                        )
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { stationBeingDeleted = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun InternetRadioStationDialog(
    initialStation: InternetRadioStation?,
    onDismiss: () -> Unit,
    onConfirm: (InternetRadioStation) -> Unit,
) {
    var name by remember(initialStation?.id) { mutableStateOf(initialStation?.name.orEmpty()) }
    var streamUrl by remember(initialStation?.id) { mutableStateOf(initialStation?.streamUrl.orEmpty()) }
    var homePageUrl by remember(initialStation?.id) { mutableStateOf(initialStation?.homePageUrl.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialStation == null) "New station" else "Edit station") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(value = streamUrl, onValueChange = { streamUrl = it }, label = { Text("Stream URL") }, singleLine = true)
                OutlinedTextField(value = homePageUrl, onValueChange = { homePageUrl = it }, label = { Text("Home page URL") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && streamUrl.isNotBlank(),
                onClick = {
                    onConfirm(
                        InternetRadioStation(
                            id = initialStation?.id ?: streamUrl.trim(),
                            name = name.trim(),
                            streamUrl = streamUrl.trim(),
                            homePageUrl = homePageUrl.trim().takeIf { it.isNotBlank() },
                        ),
                    )
                },
            ) {
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
