package app.naviamp.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
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

@Composable
fun AlbumDetailPanel(
    appColors: AppColors,
    album: Album?,
    albumDetails: AlbumDetails?,
    status: String?,
    coverArtUrl: String?,
    onBack: () -> Unit,
    onPlayAlbum: () -> Unit,
    onShuffleAlbum: () -> Unit,
    onAlbumRadio: () -> Unit,
    onDownloadAlbum: () -> Unit,
    onPlayTrack: (Int) -> Unit,
    onTrackRadio: (Track) -> Unit,
    onDownloadTrack: (Track) -> Unit,
    onArtistSelected: (Track) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = NavigationIcons.Back,
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
            CoverArtThumb(
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
                    Button(
                        enabled = albumDetails?.tracks?.isNotEmpty() == true,
                        onClick = onPlayAlbum,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp),
                    ) {
                        Text("Play")
                    }
                    Button(
                        enabled = (albumDetails?.tracks?.size ?: 0) > 1,
                        onClick = onShuffleAlbum,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp),
                    ) {
                        Text("Shuffle")
                    }
                    Button(
                        enabled = albumDetails?.tracks?.isNotEmpty() == true,
                        onClick = onAlbumRadio,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp),
                    ) {
                        Text("Radio")
                    }
                    Button(
                        enabled = albumDetails?.tracks?.isNotEmpty() == true,
                        onClick = onDownloadAlbum,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp),
                    ) {
                        Text("Download")
                    }
                }
            }
        }

        albumDetails?.let { details ->
            Text(
                listOfNotNull(
                    "${details.tracks.size} tracks",
                    details.album.releaseYear?.toString(),
                    details.tracks.totalDurationLabel(),
                ).joinToString(" - "),
                color = appColors.secondaryText,
                modifier = Modifier.fillMaxWidth(),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                details.tracks.forEachIndexed { index, track ->
                    TrackRow(
                        appColors = appColors,
                        track = track,
                        index = index + 1,
                        subtitle = track.artistName,
                        background = false,
                        horizontalPadding = 0.dp,
                        verticalPadding = 0.dp,
                        verticalAlignment = Alignment.Top,
                        showMenu = true,
                        onClick = { onPlayTrack(index) },
                        onStartRadio = { onTrackRadio(track) },
                        onDownload = { onDownloadTrack(track) },
                    )
                }
            }
        }
    }
}

private fun List<app.naviamp.domain.Track>.totalDurationLabel(): String {
    val totalSeconds = mapNotNull { it.durationSeconds }.sum()
    if (totalSeconds <= 0) return "-- minutes"
    val minutes = totalSeconds / 60
    return "$minutes minutes"
}
