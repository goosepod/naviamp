package app.naviamp.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.Track
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
import app.naviamp.ui.SharedTrackRowAction
import app.naviamp.ui.SharedTrackRowActionRequest
import app.naviamp.ui.SharedTrackRowUi
import app.naviamp.ui.TrackRow
import app.naviamp.ui.albumRowActions
import app.naviamp.ui.artistRowActions
import app.naviamp.ui.compactFavoriteRatingLabel
import app.naviamp.ui.durationLabel
import app.naviamp.ui.toNowPlayingDetailSections
import app.naviamp.ui.trackRowActions

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
fun DesktopArtistRow(
    appColors: DesktopAppColors,
    artist: Artist,
    modifier: Modifier = Modifier,
    coverArtUrl: String? = null,
    showCoverArt: Boolean = false,
    coverArtSize: Dp = 44.dp,
    onClick: (() -> Unit)? = null,
    onStartRadio: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onFavoriteToggle: (() -> Unit)? = null,
    canStartRadio: Boolean = onStartRadio != null,
    canAddToQueue: Boolean = onAddToQueue != null,
    canAddToPlaylist: Boolean = onAddToPlaylist != null,
    canFavorite: Boolean = onFavoriteToggle != null,
    onItemAction: ((SharedMediaItemActionRequest) -> Unit)? = null,
) {
    val item = SharedMediaItemUi(
        id = artist.id.value,
        title = artist.name,
        subtitle = "Artist",
        coverArtUrl = coverArtUrl.takeIf { showCoverArt },
        favoriteActive = artist.favoritedAtIso8601 != null,
        canFavorite = canFavorite,
    )
    val handleItemAction = onItemAction ?: { request: SharedMediaItemActionRequest ->
        when (request.action) {
            SharedMediaItemAction.Select -> {
                onClick?.invoke()
                Unit
            }
            SharedMediaItemAction.StartRadio -> {
                onStartRadio?.invoke()
                Unit
            }
            SharedMediaItemAction.AddToQueue -> {
                onAddToQueue?.invoke()
                Unit
            }
            SharedMediaItemAction.AddToPlaylist -> {
                onAddToPlaylist?.invoke()
                Unit
            }
            SharedMediaItemAction.ToggleFavorite -> {
                onFavoriteToggle?.invoke()
                Unit
            }
            SharedMediaItemAction.Play,
            SharedMediaItemAction.Shuffle,
            SharedMediaItemAction.Download,
            SharedMediaItemAction.CreatePlaylistAndAdd,
            SharedMediaItemAction.Rename,
            SharedMediaItemAction.EditSmartPlaylist,
            SharedMediaItemAction.Delete,
            SharedMediaItemAction.EditStation,
            SharedMediaItemAction.DeleteStation,
            -> Unit
        }
    }
    SharedMediaRow(
        item = item,
        colors = appColors,
        onClick = onClick,
        itemKind = SharedMediaItemKind.Artist,
        onItemAction = handleItemAction,
        menuItems = artistRowActions(
            canStartRadio = canStartRadio,
            canAddToQueue = canAddToQueue,
            canAddToPlaylist = canAddToPlaylist,
        ).mapNotNull { action ->
            when (action.action) {
                NaviampAction.StartArtistRadio -> if (canStartRadio) {
                    action.toRowMenuItem {
                        handleItemAction(
                            SharedMediaItemActionRequest(item, SharedMediaItemAction.StartRadio, kind = SharedMediaItemKind.Artist),
                        )
                    }.toSharedMenuItem()
                } else {
                    null
                }
                NaviampAction.AddToQueue -> if (canAddToQueue) {
                    action.toRowMenuItem {
                        handleItemAction(
                            SharedMediaItemActionRequest(item, SharedMediaItemAction.AddToQueue, kind = SharedMediaItemKind.Artist),
                        )
                    }.toSharedMenuItem()
                } else {
                    null
                }
                NaviampAction.AddToPlaylist -> if (canAddToPlaylist) {
                    action.toRowMenuItem {
                        handleItemAction(
                            SharedMediaItemActionRequest(item, SharedMediaItemAction.AddToPlaylist, kind = SharedMediaItemKind.Artist),
                        )
                    }.toSharedMenuItem()
                } else {
                    null
                }
                else -> null
            }
        },
        onFavoriteToggled = onFavoriteToggle?.let { toggle -> { toggle() } },
        canSelect = onClick != null || onItemAction != null,
        canToggleFavorite = canFavorite,
        coverArtSize = coverArtSize,
        coverArtCornerRadius = coverArtSize / 2,
        modifier = modifier,
    )
}

@Composable
fun DesktopAlbumRow(
    appColors: DesktopAppColors,
    album: Album,
    coverArtUrl: String?,
    modifier: Modifier = Modifier,
    coverArtSize: Dp = 44.dp,
    verticalPadding: Dp = 7.dp,
    onClick: (() -> Unit)? = null,
    onStartRadio: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onFavoriteToggle: (() -> Unit)? = null,
    canStartRadio: Boolean = onStartRadio != null,
    canDownload: Boolean = onDownload != null,
    canAddToQueue: Boolean = onAddToQueue != null,
    canAddToPlaylist: Boolean = onAddToPlaylist != null,
    canFavorite: Boolean = onFavoriteToggle != null,
    onItemAction: ((SharedMediaItemActionRequest) -> Unit)? = null,
) {
    val item = SharedMediaItemUi(
        id = album.id.value,
        title = album.title,
        subtitle = album.artistName,
        meta = listOfNotNull(
            "Album",
            album.releaseYear?.toString(),
        ).joinToString(" "),
        coverArtUrl = coverArtUrl,
        favoriteActive = album.favoritedAtIso8601 != null,
        canFavorite = canFavorite,
    )
    val handleItemAction = onItemAction ?: { request: SharedMediaItemActionRequest ->
        when (request.action) {
            SharedMediaItemAction.Select -> {
                onClick?.invoke()
                Unit
            }
            SharedMediaItemAction.StartRadio -> {
                onStartRadio?.invoke()
                Unit
            }
            SharedMediaItemAction.Download -> {
                onDownload?.invoke()
                Unit
            }
            SharedMediaItemAction.AddToQueue -> {
                onAddToQueue?.invoke()
                Unit
            }
            SharedMediaItemAction.AddToPlaylist -> {
                onAddToPlaylist?.invoke()
                Unit
            }
            SharedMediaItemAction.ToggleFavorite -> {
                onFavoriteToggle?.invoke()
                Unit
            }
            SharedMediaItemAction.Play,
            SharedMediaItemAction.Shuffle,
            SharedMediaItemAction.CreatePlaylistAndAdd,
            SharedMediaItemAction.Rename,
            SharedMediaItemAction.EditSmartPlaylist,
            SharedMediaItemAction.Delete,
            SharedMediaItemAction.EditStation,
            SharedMediaItemAction.DeleteStation,
            -> Unit
        }
    }
    SharedMediaRow(
        item = item,
        colors = appColors,
        onClick = onClick,
        itemKind = SharedMediaItemKind.Album,
        onItemAction = handleItemAction,
        menuItems = albumRowActions(
            canStartRadio = canStartRadio,
            canDownload = canDownload,
            canAddToQueue = canAddToQueue,
            canAddToPlaylist = canAddToPlaylist,
        ).mapNotNull { action ->
            when (action.action) {
                NaviampAction.StartAlbumRadio -> if (canStartRadio) {
                    action.toRowMenuItem {
                        handleItemAction(
                            SharedMediaItemActionRequest(item, SharedMediaItemAction.StartRadio, kind = SharedMediaItemKind.Album),
                        )
                    }.toSharedMenuItem()
                } else {
                    null
                }
                NaviampAction.DownloadAlbum -> if (canDownload) {
                    action.toRowMenuItem {
                        handleItemAction(
                            SharedMediaItemActionRequest(item, SharedMediaItemAction.Download, kind = SharedMediaItemKind.Album),
                        )
                    }.toSharedMenuItem()
                } else {
                    null
                }
                NaviampAction.AddToQueue -> if (canAddToQueue) {
                    action.toRowMenuItem {
                        handleItemAction(
                            SharedMediaItemActionRequest(item, SharedMediaItemAction.AddToQueue, kind = SharedMediaItemKind.Album),
                        )
                    }.toSharedMenuItem()
                } else {
                    null
                }
                NaviampAction.AddToPlaylist -> if (canAddToPlaylist) {
                    action.toRowMenuItem {
                        handleItemAction(
                            SharedMediaItemActionRequest(item, SharedMediaItemAction.AddToPlaylist, kind = SharedMediaItemKind.Album),
                        )
                    }.toSharedMenuItem()
                } else {
                    null
                }
                else -> null
            }
        },
        onFavoriteToggled = onFavoriteToggle?.let { toggle -> { toggle() } },
        canSelect = onClick != null || onItemAction != null,
        canToggleFavorite = canFavorite,
        coverArtSize = coverArtSize,
        coverArtCornerRadius = 4.dp,
        verticalPadding = verticalPadding,
        modifier = modifier,
    )
}

@Composable
fun DesktopTrackRow(
    appColors: DesktopAppColors,
    track: Track,
    modifier: Modifier = Modifier,
    coverArtUrl: String? = null,
    showCoverArt: Boolean = false,
    coverArtSize: Dp = 44.dp,
    index: Int? = null,
    titleStyle: TextStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
    subtitleStyle: TextStyle = TextStyle(fontSize = 11.sp),
    subtitle: String = listOfNotNull(track.artistName, track.albumTitle).joinToString(" - "),
    background: Boolean = true,
    horizontalPadding: Dp = 6.dp,
    verticalPadding: Dp = 3.dp,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    leadingContent: (@Composable RowScope.() -> Unit)? = null,
    showDuration: Boolean = true,
    showMenu: Boolean = false,
    popular: Boolean = false,
    reservePopularIndicatorSpace: Boolean = false,
    onStartRadio: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    canStartRadio: Boolean = onStartRadio != null,
    canDownload: Boolean = onDownload != null,
    canAddToQueue: Boolean = onAddToQueue != null,
    canAddToPlaylist: Boolean = onAddToPlaylist != null,
    onTrackAction: ((SharedTrackRowActionRequest) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val sharedTrack = SharedTrackRowUi(
        id = track.id.value,
        title = track.title,
        subtitle = subtitle,
        coverArtUrl = coverArtUrl,
        meta = index?.toString().orEmpty(),
        popular = popular,
        detailSections = track.toNowPlayingDetailSections(),
    )
    TrackRow(
        track = sharedTrack,
        colors = appColors,
        onTrackSelected = onClick?.let { click -> { _: SharedTrackRowUi -> click() } },
        onStartRadio = onStartRadio?.let { startRadio -> { _: SharedTrackRowUi -> startRadio() } },
        onDownload = onDownload?.let { download -> { _: SharedTrackRowUi -> download() } },
        onAddToQueue = onAddToQueue?.let { addToQueue -> { _: SharedTrackRowUi -> addToQueue() } },
        onAddToPlaylist = onAddToPlaylist?.let { addToPlaylist -> { _: SharedTrackRowUi -> addToPlaylist() } },
        canSelect = onClick != null || onTrackAction != null,
        canStartRadio = canStartRadio,
        canDownload = canDownload,
        canAddToQueue = canAddToQueue,
        canAddToPlaylist = canAddToPlaylist,
        onTrackAction = onTrackAction ?: { request ->
            when (request.action) {
                SharedTrackRowAction.Select -> {
                    onClick?.invoke()
                    Unit
                }
                SharedTrackRowAction.StartRadio -> {
                    onStartRadio?.invoke()
                    Unit
                }
                SharedTrackRowAction.AddToQueue -> {
                    onAddToQueue?.invoke()
                    Unit
                }
                SharedTrackRowAction.Download -> {
                    onDownload?.invoke()
                    Unit
                }
                SharedTrackRowAction.AddToPlaylist -> {
                    onAddToPlaylist?.invoke()
                    Unit
                }
                SharedTrackRowAction.CreatePlaylistAndAdd -> Unit
            }
        },
        modifier = modifier,
        background = background,
        horizontalPadding = horizontalPadding,
        verticalPadding = verticalPadding,
        showCoverArt = showCoverArt,
        coverArtSize = coverArtSize,
        coverArtCornerRadius = 4.dp,
        titleStyle = titleStyle,
        subtitleStyle = subtitleStyle,
        metaStyle = TextStyle(fontSize = 13.sp, lineHeight = 16.sp),
        titleSubtitleSpacing = 0.dp,
        showMenu = showMenu,
        reservePopularIndicatorSpace = reservePopularIndicatorSpace || index != null,
        leadingContent = leadingContent,
        trailingContent = {
            DesktopTrackMetadataTrailing(
                appColors = appColors,
                track = track,
                showDuration = showDuration,
            )
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

@Composable
fun DesktopTrackMetadataTrailing(
    appColors: DesktopAppColors,
    track: Track,
    showDuration: Boolean,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        track.compactFavoriteRatingLabel()?.let {
            Text(it, color = appColors.primaryText, fontSize = 11.sp)
        }
        if (showDuration) {
            Text(track.durationLabel(), color = appColors.mutedText, fontSize = 11.sp)
        }
    }
}

@Composable
private fun MediaTextBlock(
    appColors: DesktopAppColors,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle = TextStyle(fontWeight = FontWeight.SemiBold),
    subtitleStyle: TextStyle = TextStyle(fontSize = 11.sp),
) {
    Column(modifier = modifier) {
        Text(
            title,
            color = appColors.primaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = titleStyle,
        )
        Text(
            subtitle,
            color = appColors.secondaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = subtitleStyle,
        )
    }
}

private val MediaRowOverlay = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.12f)
