package app.naviamp.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.provider.MediaSearchResults

@Composable
fun SearchPanel(
    appColors: AppColors,
    query: String,
    results: MediaSearchResults,
    status: String?,
    isSearching: Boolean,
    coverArtUrl: (String?) -> String?,
    onQueryChanged: (String) -> Unit,
    onArtistSelected: (Artist) -> Unit,
    onAlbumSelected: (Album) -> Unit,
    onTrackSelected: (Int) -> Unit,
) {
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = appColors.primaryText,
        unfocusedTextColor = appColors.primaryText,
        focusedLabelColor = appColors.primaryText,
        unfocusedLabelColor = appColors.secondaryText,
        cursorColor = appColors.primaryText,
        focusedBorderColor = appColors.accent,
        unfocusedBorderColor = appColors.border,
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChanged,
            label = { Text("Search music") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            colors = textFieldColors,
            modifier = Modifier.fillMaxWidth(),
        )

        status?.let {
            Text(it, color = appColors.secondaryText, fontSize = 12.sp)
        }

        if (isSearching) {
            Text("Searching...", color = appColors.secondaryText, fontSize = 12.sp)
        } else if (query.isNotBlank() && results.isEmpty && status == null) {
            Text("No matches found.", color = appColors.secondaryText, fontSize = 12.sp)
        }

        if (results.artists.isNotEmpty()) {
            SearchSection(title = "Artists", appColors = appColors) {
                results.artists.forEach { artist ->
                    ArtistRow(
                        appColors = appColors,
                        artist = artist,
                        onClick = { onArtistSelected(artist) },
                    )
                }
            }
        }

        if (results.albums.isNotEmpty()) {
            SearchSection(title = "Albums", appColors = appColors) {
                results.albums.forEach { album ->
                    AlbumRow(
                        appColors = appColors,
                        album = album,
                        coverArtUrl = coverArtUrl(album.coverArtId),
                        onClick = { onAlbumSelected(album) },
                    )
                }
            }
        }

        if (results.tracks.isNotEmpty()) {
            SearchSection(title = "Tracks", appColors = appColors) {
                results.tracks.forEachIndexed { index, track ->
                    TrackRow(
                        appColors = appColors,
                        track = track,
                        coverArtUrl = coverArtUrl(track.coverArtId),
                        showCoverArt = true,
                        onClick = { onTrackSelected(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchSection(
    title: String,
    appColors: AppColors,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
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
