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
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Music", color = appColors.primaryText, style = MaterialTheme.typography.titleMedium)
            connectionStatus?.let {
                Text(it, color = appColors.secondaryText, fontSize = 11.sp)
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
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            title.uppercase(),
            color = appColors.primaryText,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(ColorOverlay, RoundedCornerShape(5.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        CoverArtThumb(
            appColors = appColors,
            coverArtUrl = coverArtUrl,
            size = 40.dp,
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
                fontSize = 11.sp,
            )
        }
        Text("Album", color = appColors.mutedText, fontSize = 11.sp)
    }
}

private val ColorOverlay = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.12f)
