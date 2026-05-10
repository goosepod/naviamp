package app.naviamp.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.domain.Album
import app.naviamp.domain.Artist

@Composable
fun LibraryPanel(
    appColors: AppColors,
    snapshot: LibrarySnapshot,
    query: String,
    selectedTab: LibraryTab,
    status: String?,
    isSyncing: Boolean,
    coverArtUrl: (String?) -> String?,
    onQueryChanged: (String) -> Unit,
    onTabSelected: (LibraryTab) -> Unit,
    onArtistSelected: (Artist) -> Unit,
    onAlbumSelected: (Album) -> Unit,
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

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Library", color = appColors.primaryText, style = MaterialTheme.typography.titleMedium)

        status?.let {
            Text(it, color = appColors.secondaryText, fontSize = 12.sp)
        }

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChanged,
            label = { Text("Search") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions.Default,
            colors = textFieldColors,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            LibraryTab.entries.forEach { tab ->
                FilterChip(
                    selected = selectedTab == tab,
                    onClick = { onTabSelected(tab) },
                    label = { Text(tab.label, fontSize = 12.sp) },
                    modifier = Modifier.height(28.dp),
                )
            }
        }

        when (selectedTab) {
            LibraryTab.Artists -> LibrarySection(appColors = appColors, title = "Artists", empty = snapshot.artists.isEmpty()) {
                snapshot.artists.forEach { artist ->
                    ArtistRow(
                        appColors = appColors,
                        artist = artist,
                        onClick = { onArtistSelected(artist) },
                    )
                }
            }
            LibraryTab.Albums -> LibrarySection(appColors = appColors, title = "Albums", empty = snapshot.albums.isEmpty()) {
                snapshot.albums.forEach { album ->
                    AlbumRow(
                        appColors = appColors,
                        album = album,
                        coverArtUrl = coverArtUrl(album.coverArtId),
                        onClick = { onAlbumSelected(album) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibrarySection(
    appColors: AppColors,
    title: String,
    empty: Boolean,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = appColors.primaryText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        if (empty) {
            Text("Nothing here yet.", color = appColors.secondaryText, fontSize = 12.sp)
        } else {
            content()
        }
    }
}

enum class LibraryTab(val label: String) {
    Artists("Artists"),
    Albums("Albums"),
}
