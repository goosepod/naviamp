package app.naviamp.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.domain.Album

@Composable
fun HomePanel(
    appColors: AppColors,
    connectionStatus: String?,
    recentlyAddedAlbums: List<Album>,
    coverArtUrl: (Album) -> String?,
    onAlbumSelected: (Album) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Music", color = appColors.primaryText, style = MaterialTheme.typography.titleLarge)
            connectionStatus?.let {
                Text(it, color = appColors.secondaryText, fontSize = 12.sp)
            }
        }

        HomeSection(
            title = "Recently Added",
            appColors = appColors,
        ) {
            if (recentlyAddedAlbums.isEmpty()) {
                Text("Recent albums will appear here after connection.", color = appColors.secondaryText)
            } else {
                recentlyAddedAlbums.take(5).forEach { album ->
                    AlbumSummaryRow(
                        album = album,
                        appColors = appColors,
                        coverArtUrl = coverArtUrl(album),
                        onClick = { onAlbumSelected(album) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeSection(
    title: String,
    appColors: AppColors,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title.uppercase(),
            color = appColors.primaryText,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

@Composable
private fun AlbumSummaryRow(
    album: Album,
    appColors: AppColors,
    coverArtUrl: String?,
    onClick: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(ColorOverlay, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        CoverArtThumb(
            appColors = appColors,
            coverArtUrl = coverArtUrl,
            size = 48.dp,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                album.title,
                color = appColors.primaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                album.artistName,
                color = appColors.secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp,
            )
        }
        Text("Album", color = appColors.mutedText, fontSize = 12.sp)
    }
}

private val ColorOverlay = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.12f)
