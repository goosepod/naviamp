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
import app.naviamp.ui.NaviampAlbumDetailScreenUi
import app.naviamp.ui.SharedMediaItemAction
import app.naviamp.ui.ExpandedMediaImageDialog
import app.naviamp.ui.NaviampDetailAction
import app.naviamp.ui.NaviampResponsiveActionRow
import app.naviamp.ui.SharedMediaItemActionRequest
import app.naviamp.ui.SharedMediaItemKind
import app.naviamp.ui.SharedTrackRowActionRequest
import app.naviamp.ui.actionRequest

@Composable
fun DesktopAlbumDetailPanel(
    appColors: DesktopAppColors,
    screen: NaviampAlbumDetailScreenUi,
    onBack: () -> Unit,
    onAlbumAction: (SharedMediaItemActionRequest) -> Unit,
    onTrackAction: (SharedTrackRowActionRequest) -> Unit,
    onArtistSelected: (SharedTrackRowActionRequest) -> Unit,
) {
    val detail = screen.detail
    val album = detail?.album ?: screen.selectedAlbum
    val coverArtUrl = album?.coverArtUrl
    val effectiveAlbumId = album?.id
    var albumImageOpen by remember(effectiveAlbumId) { mutableStateOf(false) }
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
                album?.title ?: "Album",
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
                val releaseYear = album?.releaseYear
                val artistName = album?.subtitle.orEmpty()
                val artistTrack = detail?.tracks
                    ?.firstOrNull { track -> track.artistCredits.any { credit -> credit.id != null && credit.name == artistName } }
                    ?: detail?.tracks?.firstOrNull { track -> track.artistCredits.any { credit -> credit.id != null } }
                if (artistTrack != null) {
                    TextButton(
                        onClick = {
                            val artistCredit = artistTrack.artistCredits
                                .firstOrNull { credit -> credit.id != null && credit.name == artistName }
                                ?: artistTrack.artistCredits.firstOrNull { credit -> credit.id != null }
                            onArtistSelected(
                                SharedTrackRowActionRequest(
                                    track = artistTrack,
                                    action = app.naviamp.ui.SharedTrackRowAction.GoToArtist,
                                    artistId = artistCredit?.id,
                                    artistName = artistCredit?.name ?: artistName,
                                ),
                            )
                        },
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
                screen.status?.let {
                    Text(it, color = appColors.secondaryText, fontSize = 11.sp)
                }
                Box(modifier = Modifier.fillMaxWidth()) {
                    fun request(action: SharedMediaItemAction, shuffle: Boolean = false) {
                        album?.let { item ->
                            onAlbumAction(item.actionRequest(action, kind = SharedMediaItemKind.Album, shuffle = shuffle))
                        }
                    }
                    val tracksAvailable = detail?.tracks?.isNotEmpty() == true
                    NaviampResponsiveActionRow(
                        colors = appColors,
                        actions = listOf(
                            NaviampDetailAction("Play album", TransportIcons.Play, { request(SharedMediaItemAction.Play) }, tracksAvailable),
                            NaviampDetailAction("Shuffle album", TransportIcons.Shuffle, { request(SharedMediaItemAction.Shuffle, shuffle = true) }, (detail?.tracks?.size ?: 0) > 1),
                            NaviampDetailAction("Download album", DesktopNavigationIcons.Downloads, { request(SharedMediaItemAction.Download) }, tracksAvailable),
                            NaviampDetailAction("Start album radio", TransportIcons.Radio, { request(SharedMediaItemAction.StartRadio) }, tracksAvailable),
                            NaviampDetailAction("Add album to queue", DesktopNavigationIcons.Queue, { request(SharedMediaItemAction.AddToQueue) }, tracksAvailable),
                            NaviampDetailAction("Add album to playlist", DesktopNavigationIcons.Playlist, { request(SharedMediaItemAction.AddToPlaylist) }, tracksAvailable),
                            NaviampDetailAction(
                                if (album?.favoriteActive == true) "Remove album favorite" else "Favorite album",
                                TransportIcons.Heart,
                                { request(SharedMediaItemAction.ToggleFavorite) },
                                album?.canFavorite == true,
                            ),
                        ),
                    )
                }
            }
        }

        detail?.let { details ->
            Text(
                listOfNotNull(
                    "${details.tracks.size} tracks",
                    details.album.releaseYear?.toString(),
                    "Total ${details.totalDurationLabel}",
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
                val reservePopularIndicatorSpace = details.tracks.any { it.popular }
                details.tracks.forEachIndexed { index, track ->
                    DesktopSharedTrackRow(
                        appColors = appColors,
                        track = track.copy(meta = (index + 1).toString()),
                        showCoverArt = false,
                        background = false,
                        horizontalPadding = 0.dp,
                        verticalPadding = 0.dp,
                        showMenu = true,
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
