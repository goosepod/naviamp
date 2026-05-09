package app.naviamp.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.Track
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
                    ArtistResultRow(appColors = appColors, artist = artist)
                }
            }
        }

        if (results.albums.isNotEmpty()) {
            SearchSection(title = "Albums", appColors = appColors) {
                results.albums.forEach { album ->
                    AlbumResultRow(
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
                    TrackResultRow(
                        appColors = appColors,
                        track = track,
                        coverArtUrl = coverArtUrl(track.coverArtId),
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

@Composable
private fun ArtistResultRow(
    appColors: AppColors,
    artist: Artist,
) {
    SearchRow(appColors = appColors) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                artist.name,
                color = appColors.primaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
            Text("Artist", color = appColors.secondaryText, fontSize = 11.sp)
        }
    }
}

@Composable
private fun AlbumResultRow(
    appColors: AppColors,
    album: Album,
    coverArtUrl: String?,
    onClick: () -> Unit,
) {
    SearchRow(appColors = appColors, onClick = onClick) {
        CoverArtThumb(appColors = appColors, coverArtUrl = coverArtUrl, size = 34.dp, cornerRadius = 4.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                album.title,
                color = appColors.primaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
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

@Composable
private fun TrackResultRow(
    appColors: AppColors,
    track: Track,
    coverArtUrl: String?,
    onClick: () -> Unit,
) {
    SearchRow(appColors = appColors, onClick = onClick) {
        CoverArtThumb(appColors = appColors, coverArtUrl = coverArtUrl, size = 34.dp, cornerRadius = 4.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.title,
                color = appColors.primaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
            Text(
                listOfNotNull(track.artistName, track.albumTitle).joinToString(" - "),
                color = appColors.secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 11.sp,
            )
        }
        Text(track.durationLabel(), color = appColors.mutedText, fontSize = 11.sp)
    }
}

@Composable
private fun SearchRow(
    appColors: AppColors,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val modifier = Modifier
        .fillMaxWidth()
        .background(ColorOverlay, RoundedCornerShape(5.dp))
        .padding(horizontal = 6.dp, vertical = 3.dp)
        .let { base ->
            if (onClick != null) {
                base.clickable(onClick = onClick)
            } else {
                base
            }
        }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier,
        content = content,
    )
}

private val ColorOverlay = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.12f)
