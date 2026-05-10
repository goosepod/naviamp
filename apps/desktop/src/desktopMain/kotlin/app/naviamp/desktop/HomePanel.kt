package app.naviamp.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
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
                    AlbumRow(
                        appColors = appColors,
                        album = album,
                        coverArtUrl = coverArtUrl(album),
                        coverArtSize = 40.dp,
                        verticalPadding = 2.dp,
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
