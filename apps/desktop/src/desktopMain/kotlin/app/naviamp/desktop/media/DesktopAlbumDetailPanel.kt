package app.naviamp.desktop

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.Track
import app.naviamp.ui.SharedMediaItemAction
import app.naviamp.ui.ExpandedMediaImageDialog
import app.naviamp.ui.SharedMediaItemActionRequest
import app.naviamp.ui.SharedMediaItemKind
import app.naviamp.ui.SharedTrackRowActionRequest
import app.naviamp.ui.actionRequest
import app.naviamp.ui.toSharedMediaItemUi

@Composable
fun DesktopAlbumDetailPanel(
    appColors: DesktopAppColors,
    album: Album?,
    albumDetails: AlbumDetails?,
    status: String?,
    coverArtUrl: String?,
    popularTrackIds: Set<String> = emptySet(),
    onBack: () -> Unit,
    onAlbumAction: (SharedMediaItemActionRequest) -> Unit,
    onTrackAction: (SharedTrackRowActionRequest) -> Unit,
    onArtistSelected: (Track) -> Unit,
) {
    val effectiveAlbumId = albumDetails?.album?.id ?: album?.id
    var albumImageOpen by remember(effectiveAlbumId) { mutableStateOf(false) }
    var actionMenuExpanded by remember(effectiveAlbumId) { mutableStateOf(false) }
    Column(
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = DesktopNavigationIcons.Back,
                    contentDescription = "Back",
                    tint = appColors.primaryText,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                albumDetails?.album?.title ?: album?.title ?: "Album",
                color = appColors.primaryText,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier.clickable(
                    enabled = coverArtUrl != null,
                    onClick = { albumImageOpen = true },
                ),
            ) {
                DesktopCoverArtThumb(
                    appColors = appColors,
                    coverArtUrl = coverArtUrl,
                    size = 96.dp,
                    cornerRadius = 4.dp,
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f),
            ) {
                val releaseYear = albumDetails?.album?.releaseYear ?: album?.releaseYear
                val artistName = albumDetails?.album?.artistName ?: album?.artistName ?: ""
                val artistTrack = albumDetails?.tracks
                    ?.firstOrNull { it.artistId != null && it.artistName == artistName }
                    ?: albumDetails?.tracks?.firstOrNull { it.artistId != null }
                if (artistTrack != null) {
                    TextButton(
                        onClick = { onArtistSelected(artistTrack) },
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                        modifier = Modifier.height(24.dp),
                    ) {
                        Text(
                            artistName,
                            color = appColors.primaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    Text(
                        artistName,
                        color = appColors.primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                releaseYear?.let {
                    Text(it.toString(), color = appColors.secondaryText, fontSize = 12.sp)
                }
                status?.let {
                    Text(it, color = appColors.secondaryText, fontSize = 11.sp)
                }
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val effectiveAlbum = albumDetails?.album ?: album
                    val albumItem = effectiveAlbum?.toSharedMediaItemUi(
                        coverArtUrl = { coverArtUrl },
                        canFavorite = true,
                    )
                    fun request(action: SharedMediaItemAction, shuffle: Boolean = false) {
                        albumItem?.let { item ->
                            onAlbumAction(item.actionRequest(action, kind = SharedMediaItemKind.Album, shuffle = shuffle))
                        }
                    }
                    val tracksAvailable = albumDetails?.tracks?.isNotEmpty() == true
                    val showAllActions = maxWidth >= 288.dp
                    val actionButtonSize = if (showAllActions) 36.dp else 32.dp
                    val actionIconSize = if (showAllActions) 22.dp else 20.dp
                    Row(horizontalArrangement = Arrangement.spacedBy(if (showAllActions) 6.dp else 2.dp)) {
                        DetailActionIconButton(
                            appColors = appColors,
                            icon = TransportIcons.Play,
                            contentDescription = "Play album",
                            enabled = tracksAvailable,
                            buttonSize = actionButtonSize,
                            iconSize = actionIconSize,
                            onClick = { request(SharedMediaItemAction.Play) },
                        )
                        DetailActionIconButton(
                            appColors = appColors,
                            icon = TransportIcons.Shuffle,
                            contentDescription = "Shuffle album",
                            enabled = (albumDetails?.tracks?.size ?: 0) > 1,
                            buttonSize = actionButtonSize,
                            iconSize = actionIconSize,
                            onClick = { request(SharedMediaItemAction.Shuffle, shuffle = true) },
                        )
                        DetailActionIconButton(
                            appColors = appColors,
                            icon = DesktopNavigationIcons.Downloads,
                            contentDescription = "Download album",
                            enabled = tracksAvailable,
                            buttonSize = actionButtonSize,
                            iconSize = actionIconSize,
                            onClick = { request(SharedMediaItemAction.Download) },
                        )
                        DetailActionIconButton(
                            appColors = appColors,
                            icon = TransportIcons.Radio,
                            contentDescription = "Start album radio",
                            enabled = tracksAvailable,
                            buttonSize = actionButtonSize,
                            iconSize = actionIconSize,
                            onClick = { request(SharedMediaItemAction.StartRadio) },
                        )
                        DetailActionIconButton(
                            appColors = appColors,
                            icon = DesktopNavigationIcons.Queue,
                            contentDescription = "Add album to queue",
                            enabled = tracksAvailable,
                            buttonSize = actionButtonSize,
                            iconSize = actionIconSize,
                            onClick = { request(SharedMediaItemAction.AddToQueue) },
                        )
                        if (showAllActions) {
                            DetailActionIconButton(
                                appColors = appColors,
                                icon = DesktopNavigationIcons.Playlist,
                                contentDescription = "Add album to playlist",
                                enabled = tracksAvailable,
                                onClick = { request(SharedMediaItemAction.AddToPlaylist) },
                            )
                            DetailActionIconButton(
                                appColors = appColors,
                                icon = TransportIcons.Heart,
                                contentDescription = if (effectiveAlbum?.favoritedAtIso8601 != null) {
                                    "Remove album favorite"
                                } else {
                                    "Favorite album"
                                },
                                enabled = effectiveAlbum != null,
                                onClick = { request(SharedMediaItemAction.ToggleFavorite) },
                            )
                        } else {
                            Box {
                                IconButton(
                                    onClick = { actionMenuExpanded = true },
                                    modifier = Modifier.size(actionButtonSize),
                                ) {
                                    Text("⋮", color = appColors.primaryText, fontSize = 17.sp)
                                }
                                DesktopNaviampDropdownMenu(
                                    expanded = actionMenuExpanded,
                                    onDismissRequest = { actionMenuExpanded = false },
                                ) {
                                    DesktopNaviampDropdownMenuItem(
                                        label = "Add album to playlist",
                                        icon = DesktopNavigationIcons.Playlist,
                                        enabled = tracksAvailable,
                                        onClick = {
                                            actionMenuExpanded = false
                                            request(SharedMediaItemAction.AddToPlaylist)
                                        },
                                    )
                                    DesktopNaviampDropdownMenuItem(
                                        label = if (effectiveAlbum?.favoritedAtIso8601 != null) {
                                            "Remove album favorite"
                                        } else {
                                            "Favorite album"
                                        },
                                        icon = TransportIcons.Heart,
                                        enabled = effectiveAlbum != null,
                                        onClick = {
                                            actionMenuExpanded = false
                                            request(SharedMediaItemAction.ToggleFavorite)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        albumDetails?.let { details ->
            Text(
                listOfNotNull(
                    "${details.tracks.size} tracks",
                    details.album.releaseYear?.toString(),
                    "Total ${details.tracks.totalDurationLabel()}",
                ).joinToString(" - "),
                color = appColors.secondaryText,
                modifier = Modifier.fillMaxWidth(),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                val reservePopularIndicatorSpace = details.tracks.any { it.id.value in popularTrackIds }
                details.tracks.forEachIndexed { index, track ->
                    DesktopTrackRow(
                        appColors = appColors,
                        track = track,
                        canGoToAlbum = false,
                        index = index + 1,
                        subtitle = track.artistName,
                        background = false,
                        horizontalPadding = 0.dp,
                        verticalPadding = 0.dp,
                        verticalAlignment = Alignment.Top,
                        showMenu = true,
                        popular = track.id.value in popularTrackIds,
                        reservePopularIndicatorSpace = reservePopularIndicatorSpace,
                        canStartRadio = true,
                        canDownload = true,
                        canAddToQueue = true,
                        canAddToPlaylist = true,
                        onTrackAction = onTrackAction,
                    )
                }
            }
        }
    }

    if (albumImageOpen) {
        ExpandedMediaImageDialog(
            imageUrl = coverArtUrl,
            colors = appColors,
            onDismissRequest = { albumImageOpen = false },
        )
    }
}

private fun List<app.naviamp.domain.Track>.totalDurationLabel(): String {
    val totalSeconds = mapNotNull { it.durationSeconds }.sum()
    if (totalSeconds <= 0) return "--"
    val hours = totalSeconds / 3600
    val minutes = totalSeconds / 60
    val remainingMinutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours}h ${remainingMinutes}m" else "$minutes minutes"
}
