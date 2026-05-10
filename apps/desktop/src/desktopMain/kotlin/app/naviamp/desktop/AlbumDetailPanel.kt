package app.naviamp.desktop

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails

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
    onPlayTrack: (Int) -> Unit,
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
                Text(
                    albumDetails?.album?.artistName ?: album?.artistName ?: "",
                    color = appColors.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
                }
            }
        }

        albumDetails?.let { details ->
            Text(
                "${details.tracks.size} tracks - ${details.tracks.totalDurationLabel()}",
                color = appColors.secondaryText,
                modifier = Modifier.fillMaxWidth(),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                details.tracks.forEachIndexed { index, track ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlayTrack(index) }
                            .padding(vertical = 0.dp),
                    ) {
                        Text("${index + 1}", color = appColors.mutedText, modifier = Modifier.padding(top = 1.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                track.title,
                                color = appColors.primaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${track.artistName}\n${track.durationLabel()}",
                                color = appColors.secondaryText,
                                fontSize = 11.sp,
                            )
                        }
                        track.compactFavoriteRatingLabel()?.let {
                            Text(it, color = appColors.primaryText, fontSize = 11.sp)
                        }
                        Text("⋮", color = appColors.mutedText)
                    }
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
