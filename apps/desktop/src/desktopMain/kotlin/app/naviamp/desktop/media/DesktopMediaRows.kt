package app.naviamp.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.ui.NaviampAction
import app.naviamp.ui.NaviampActionSpec
import app.naviamp.ui.NaviampIcons
import app.naviamp.ui.NaviampRowMenuItem
import app.naviamp.ui.NaviampRowOverflowMenu
import app.naviamp.ui.SharedMediaItemKind
import app.naviamp.ui.SharedMediaItemAction
import app.naviamp.ui.SharedMediaItemActionRequest
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.SharedMediaRow
import app.naviamp.ui.SharedTrackRowActionRequest
import app.naviamp.ui.SharedTrackRowUi
import app.naviamp.ui.TrackRow
import app.naviamp.ui.actionRequest
import app.naviamp.ui.albumRowActions
import app.naviamp.ui.artistRowActions

@Composable
fun DesktopMediaRow(
    appColors: DesktopAppColors,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    background: Boolean = true,
    horizontalPadding: Dp = 6.dp,
    verticalPadding: Dp = 3.dp,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(6.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val rowModifier = modifier
        .fillMaxWidth()
        .then(
            if (background) {
                Modifier.background(MediaRowOverlay, RoundedCornerShape(5.dp))
            } else {
                Modifier
            },
        )
        .padding(horizontal = horizontalPadding, vertical = verticalPadding)
        .let { base ->
            if (onClick != null) base.clickable(onClick = onClick) else base
        }

    Row(
        verticalAlignment = verticalAlignment,
        horizontalArrangement = horizontalArrangement,
        modifier = rowModifier,
        content = content,
    )
}

@Composable
fun DesktopSharedArtistRow(
    appColors: DesktopAppColors,
    item: SharedMediaItemUi,
    modifier: Modifier = Modifier,
    coverArtSize: Dp = 44.dp,
    canStartRadio: Boolean = false,
    canAddToQueue: Boolean = false,
    canAddToPlaylist: Boolean = false,
    canSelect: Boolean = true,
    onItemAction: (SharedMediaItemActionRequest) -> Unit,
) {
    DesktopSharedMediaItemRow(
        appColors = appColors,
        item = item,
        kind = SharedMediaItemKind.Artist,
        actions = artistRowActions(
            canStartRadio = canStartRadio,
            canAddToQueue = canAddToQueue,
            canAddToPlaylist = canAddToPlaylist,
        ).mapNotNull { action ->
            when (action.action) {
                NaviampAction.StartArtistRadio -> action to SharedMediaItemAction.StartRadio
                NaviampAction.AddToQueue -> action to SharedMediaItemAction.AddToQueue
                NaviampAction.AddToPlaylist -> action to SharedMediaItemAction.AddToPlaylist
                else -> null
            }
        },
        coverArtSize = coverArtSize,
        coverArtCornerRadius = coverArtSize / 2,
        canSelect = canSelect,
        onItemAction = onItemAction,
        modifier = modifier,
    )
}

@Composable
fun DesktopSharedAlbumRow(
    appColors: DesktopAppColors,
    item: SharedMediaItemUi,
    modifier: Modifier = Modifier,
    coverArtSize: Dp = 44.dp,
    verticalPadding: Dp = 7.dp,
    canStartRadio: Boolean = false,
    canDownload: Boolean = false,
    canAddToQueue: Boolean = false,
    canAddToPlaylist: Boolean = false,
    canSelect: Boolean = true,
    onItemAction: (SharedMediaItemActionRequest) -> Unit,
) {
    DesktopSharedMediaItemRow(
        appColors = appColors,
        item = item,
        kind = SharedMediaItemKind.Album,
        actions = albumRowActions(
            canStartRadio = canStartRadio,
            canDownload = canDownload,
            canAddToQueue = canAddToQueue,
            canAddToPlaylist = canAddToPlaylist,
        ).mapNotNull { action ->
            when (action.action) {
                NaviampAction.StartAlbumRadio -> action to SharedMediaItemAction.StartRadio
                NaviampAction.DownloadAlbum -> action to SharedMediaItemAction.Download
                NaviampAction.AddToQueue -> action to SharedMediaItemAction.AddToQueue
                NaviampAction.AddToPlaylist -> action to SharedMediaItemAction.AddToPlaylist
                else -> null
            }
        },
        coverArtSize = coverArtSize,
        coverArtCornerRadius = 4.dp,
        verticalPadding = verticalPadding,
        canSelect = canSelect,
        onItemAction = onItemAction,
        modifier = modifier,
    )
}

@Composable
private fun DesktopSharedMediaItemRow(
    appColors: DesktopAppColors,
    item: SharedMediaItemUi,
    kind: SharedMediaItemKind,
    actions: List<Pair<NaviampActionSpec, SharedMediaItemAction>>,
    coverArtSize: Dp,
    coverArtCornerRadius: Dp,
    verticalPadding: Dp = 7.dp,
    canSelect: Boolean,
    onItemAction: (SharedMediaItemActionRequest) -> Unit,
    modifier: Modifier,
) {
    SharedMediaRow(
        item = item,
        colors = appColors,
        itemKind = kind,
        onItemAction = onItemAction,
        menuItems = actions.map { (spec, action) ->
            spec.toRowMenuItem {
                onItemAction(item.actionRequest(action, kind = kind))
            }.toSharedMenuItem()
        },
        onFavoriteToggled = if (item.canFavorite) {
            { selected ->
                onItemAction(selected.actionRequest(SharedMediaItemAction.ToggleFavorite, kind = kind))
            }
        } else {
            null
        },
        canSelect = canSelect,
        canToggleFavorite = item.canFavorite,
        coverArtSize = coverArtSize,
        coverArtCornerRadius = coverArtCornerRadius,
        verticalPadding = verticalPadding,
        modifier = modifier,
    )
}

@Composable
fun DesktopSharedTrackRow(
    appColors: DesktopAppColors,
    track: SharedTrackRowUi,
    modifier: Modifier = Modifier,
    coverArtSize: Dp = 44.dp,
    canStartRadio: Boolean = false,
    canDownload: Boolean = false,
    canAddToQueue: Boolean = false,
    canAddToPlaylist: Boolean = false,
    showMenu: Boolean = false,
    showCoverArt: Boolean = true,
    background: Boolean = true,
    horizontalPadding: Dp = 6.dp,
    verticalPadding: Dp = 3.dp,
    reservePopularIndicatorSpace: Boolean = false,
    onTrackAction: (SharedTrackRowActionRequest) -> Unit,
) {
    TrackRow(
        track = track.copy(meta = ""),
        colors = appColors,
        onTrackSelected = null,
        canSelect = true,
        canStartRadio = canStartRadio,
        canDownload = canDownload,
        canAddToQueue = canAddToQueue,
        canAddToPlaylist = canAddToPlaylist,
        onTrackAction = onTrackAction,
        reservePopularIndicatorSpace = reservePopularIndicatorSpace,
        modifier = modifier,
        background = background,
        horizontalPadding = horizontalPadding,
        verticalPadding = verticalPadding,
        showCoverArt = showCoverArt,
        coverArtSize = coverArtSize,
        coverArtCornerRadius = 4.dp,
        titleStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
        subtitleStyle = TextStyle(fontSize = 11.sp),
        metaStyle = TextStyle(fontSize = 13.sp, lineHeight = 16.sp),
        titleSubtitleSpacing = 0.dp,
        showMenu = showMenu,
        trailingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                track.ratingLabel?.let { rating ->
                    Text(rating, color = appColors.primaryText, fontSize = 11.sp)
                }
                if (track.meta.isNotBlank()) {
                    Text(track.meta, color = appColors.mutedText, fontSize = 11.sp)
                }
            }
        },
    )
}

@Composable
fun DesktopRowOverflowMenu(
    appColors: DesktopAppColors,
    items: List<DesktopRowMenuItem>,
) {
    NaviampRowOverflowMenu(
        colors = appColors,
        items = items.map { NaviampRowMenuItem(it.label, it.icon, it.onClick, it.enabled) },
    )
}

data class DesktopRowMenuItem(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
)

private fun NaviampActionSpec.toRowMenuItem(onClick: () -> Unit): DesktopRowMenuItem =
    DesktopRowMenuItem(label = label, icon = icon, onClick = onClick, enabled = enabled)

private fun DesktopRowMenuItem.toSharedMenuItem(): NaviampRowMenuItem =
    NaviampRowMenuItem(label = label, icon = icon, onClick = onClick, enabled = enabled)

private val MediaRowOverlay = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.12f)
