package app.naviamp.desktop

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
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
            DesktopCoverArtThumb(
                appColors = appColors,
                coverArtUrl = coverArtUrl,
                size = 96.dp,
                cornerRadius = 4.dp,
            )
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    DetailActionIconButton(
                        appColors = appColors,
                        icon = TransportIcons.Play,
                        contentDescription = "Play album",
                        enabled = albumDetails?.tracks?.isNotEmpty() == true,
                        onClick = { request(SharedMediaItemAction.Play) },
                    )
                    DetailActionIconButton(
                        appColors = appColors,
                        icon = TransportIcons.Shuffle,
                        contentDescription = "Shuffle album",
                        enabled = (albumDetails?.tracks?.size ?: 0) > 1,
                        onClick = { request(SharedMediaItemAction.Shuffle, shuffle = true) },
                    )
                    DetailActionIconButton(
                        appColors = appColors,
                        icon = TransportIcons.Radio,
                        contentDescription = "Start album radio",
                        enabled = albumDetails?.tracks?.isNotEmpty() == true,
                        onClick = { request(SharedMediaItemAction.StartRadio) },
                    )
                    DetailActionIconButton(
                        appColors = appColors,
                        icon = DesktopNavigationIcons.Downloads,
                        contentDescription = "Download album",
                        enabled = albumDetails?.tracks?.isNotEmpty() == true,
                        onClick = { request(SharedMediaItemAction.Download) },
                    )
                    DetailActionIconButton(
                        appColors = appColors,
                        icon = DesktopNavigationIcons.Queue,
                        contentDescription = "Add album to queue",
                        enabled = albumDetails?.tracks?.isNotEmpty() == true,
                        onClick = { request(SharedMediaItemAction.AddToQueue) },
                    )
                    DetailActionIconButton(
                        appColors = appColors,
                        icon = DesktopNavigationIcons.Playlist,
                        contentDescription = "Add album to playlist",
                        enabled = albumDetails?.tracks?.isNotEmpty() == true,
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
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                val reservePopularIndicatorSpace = details.tracks.any { it.id.value in popularTrackIds }
                details.tracks.forEachIndexed { index, track ->
                    DesktopTrackRow(
                        appColors = appColors,
                        track = track,
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
}

private fun List<app.naviamp.domain.Track>.totalDurationLabel(): String {
    val totalSeconds = mapNotNull { it.durationSeconds }.sum()
    if (totalSeconds <= 0) return "--"
    val hours = totalSeconds / 3600
    val minutes = totalSeconds / 60
    val remainingMinutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours}h ${remainingMinutes}m" else "$minutes minutes"
}
