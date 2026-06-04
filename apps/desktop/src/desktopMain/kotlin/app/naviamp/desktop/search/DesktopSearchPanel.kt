package app.naviamp.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
fun DesktopSearchPanel(
    appColors: AppColors,
    query: String,
    results: MediaSearchResults,
    status: String?,
    isSearching: Boolean,
    coverArtUrl: (String?) -> String?,
    onQueryChanged: (String) -> Unit,
    onClearSearch: () -> Unit,
    onArtistSelected: (Artist) -> Unit,
    onArtistRadioSelected: (Artist) -> Unit,
    onArtistAddToQueue: (Artist) -> Unit,
    onArtistAddToPlaylist: (Artist) -> Unit,
    onAlbumSelected: (Album) -> Unit,
    onAlbumRadioSelected: (Album) -> Unit,
    onAlbumDownloadSelected: (Album) -> Unit,
    onAlbumAddToQueue: (Album) -> Unit,
    onAlbumAddToPlaylist: (Album) -> Unit,
    onTrackSelected: (Int) -> Unit,
    onTrackRadioSelected: (Int) -> Unit,
    onTrackDownloadSelected: (Int) -> Unit,
    onTrackAddToQueue: (Int) -> Unit,
    onTrackAddToPlaylist: (Int) -> Unit,
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
            trailingIcon = {
                if (query.isNotBlank() || !results.isEmpty || status != null || isSearching) {
                    IconButton(onClick = onClearSearch) {
                        Icon(
                            imageVector = NavigationIcons.Close,
                            contentDescription = "Clear search",
                            tint = appColors.secondaryText,
                        )
                    }
                }
            },
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
                    DesktopArtistRow(
                        appColors = appColors,
                        artist = artist,
                        coverArtUrl = coverArtUrl(artist.id.value),
                        showCoverArt = true,
                        onClick = { onArtistSelected(artist) },
                        onStartRadio = { onArtistRadioSelected(artist) },
                        onAddToQueue = { onArtistAddToQueue(artist) },
                        onAddToPlaylist = { onArtistAddToPlaylist(artist) },
                    )
                }
            }
        }

        if (results.albums.isNotEmpty()) {
            SearchSection(title = "Albums", appColors = appColors) {
                results.albums.forEach { album ->
                    DesktopAlbumRow(
                        appColors = appColors,
                        album = album,
                        coverArtUrl = coverArtUrl(album.coverArtId),
                        onClick = { onAlbumSelected(album) },
                        onStartRadio = { onAlbumRadioSelected(album) },
                        onDownload = { onAlbumDownloadSelected(album) },
                        onAddToQueue = { onAlbumAddToQueue(album) },
                        onAddToPlaylist = { onAlbumAddToPlaylist(album) },
                    )
                }
            }
        }

        if (results.tracks.isNotEmpty()) {
            SearchSection(title = "Tracks", appColors = appColors) {
                results.tracks.forEachIndexed { index, track ->
                    DesktopTrackRow(
                        appColors = appColors,
                        track = track,
                        coverArtUrl = coverArtUrl(track.coverArtId),
                        showCoverArt = true,
                        onClick = { onTrackSelected(index) },
                        onStartRadio = { onTrackRadioSelected(index) },
                        onDownload = { onTrackDownloadSelected(index) },
                        onAddToQueue = { onTrackAddToQueue(index) },
                        onAddToPlaylist = { onTrackAddToPlaylist(index) },
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
