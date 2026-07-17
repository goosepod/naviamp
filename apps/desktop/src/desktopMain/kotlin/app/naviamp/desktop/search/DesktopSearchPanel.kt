package app.naviamp.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.ui.NaviampSearchScreenUi
import app.naviamp.ui.NaviampPageTitle
import app.naviamp.ui.SharedMediaItemActionRequest
import app.naviamp.ui.SharedTrackRowActionRequest

@Composable
fun DesktopSearchPanel(
    appColors: DesktopAppColors,
    search: NaviampSearchScreenUi,
    onQueryChanged: (String) -> Unit,
    onClearSearch: () -> Unit,
    onMediaItemAction: (SharedMediaItemActionRequest) -> Unit,
    onTrackAction: (SharedTrackRowActionRequest) -> Unit,
) {
    val query = search.query
    val results = search.results
    val status = search.status
    val isSearching = search.searching
    val searchFocusRequester = remember { FocusRequester() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NaviampPageTitle("Search", appColors)
        DesktopCompactSearchField(
            value = query,
            onValueChange = onQueryChanged,
            placeholder = "Search music",
            appColors = appColors,
            onClear = {
                onClearSearch()
                searchFocusRequester.requestFocus()
            },
            showClear = query.isNotBlank() || !results.isEmpty || status != null || isSearching,
            modifier = Modifier.padding(horizontal = 8.dp).focusRequester(searchFocusRequester),
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
                    DesktopSharedArtistRow(
                        appColors = appColors,
                        item = artist,
                        canStartRadio = true,
                        canAddToQueue = true,
                        canAddToPlaylist = true,
                        onItemAction = onMediaItemAction,
                    )
                }
            }
        }

        if (results.albums.isNotEmpty()) {
            SearchSection(title = "Albums", appColors = appColors) {
                results.albums.forEach { album ->
                    DesktopSharedAlbumRow(
                        appColors = appColors,
                        item = album,
                        canStartRadio = true,
                        canDownload = true,
                        canAddToQueue = true,
                        canAddToPlaylist = true,
                        onItemAction = onMediaItemAction,
                    )
                }
            }
        }

        if (results.tracks.isNotEmpty()) {
            SearchSection(title = "Tracks", appColors = appColors) {
                results.tracks.forEach { track ->
                    DesktopSharedTrackRow(
                        appColors = appColors,
                        track = track,
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
}

@Composable
private fun SearchSection(
    title: String,
    appColors: DesktopAppColors,
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
