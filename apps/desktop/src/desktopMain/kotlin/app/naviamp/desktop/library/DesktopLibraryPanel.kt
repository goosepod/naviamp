package app.naviamp.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.domain.cache.LibrarySnapshot
import app.naviamp.ui.NaviampPageTitle
import app.naviamp.ui.SharedMediaItemActionRequest

@Composable
fun DesktopLibraryPanel(
    appColors: DesktopAppColors,
    snapshot: LibrarySnapshot,
    query: String,
    status: String?,
    isSyncing: Boolean,
    listState: LazyListState,
    coverArtUrl: (String?) -> String?,
    onQueryChanged: (String) -> Unit,
    onJumpToLetter: (Char) -> Unit,
    onMediaItemAction: (SharedMediaItemActionRequest) -> Unit,
    onRefreshLibrary: () -> Unit,
) {
    val searchFocusRequester = remember { FocusRequester() }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            NaviampPageTitle("Library", appColors)
            DesktopPageOverflowMenu(
                appColors = appColors,
                onRefresh = onRefreshLibrary,
                refreshEnabled = !isSyncing,
            )
        }

        status?.let { message ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    message,
                    color = appColors.secondaryText,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                if (message.startsWith("Library changed on server")) {
                    TextButton(
                        enabled = !isSyncing,
                        onClick = onRefreshLibrary,
                    ) {
                        Text(if (isSyncing) "Refreshing..." else "Refresh", fontSize = 12.sp)
                    }
                }
            }
        }

        DesktopCompactSearchField(
            value = query,
            onValueChange = onQueryChanged,
            placeholder = "Search artists",
            appColors = appColors,
            onClear = {
                onQueryChanged("")
                searchFocusRequester.requestFocus()
            },
            modifier = Modifier.padding(horizontal = 8.dp).focusRequester(searchFocusRequester),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f),
        ) {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f),
            ) {
                item {
                    Text("Artists", color = appColors.primaryText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                if (snapshot.artists.isEmpty()) {
                    item {
                        Text("Nothing here yet.", color = appColors.secondaryText, fontSize = 12.sp)
                    }
                }
                items(snapshot.artists, key = { it.id.value }) { artist ->
                    DesktopArtistRow(
                        appColors = appColors,
                        artist = artist,
                        coverArtUrl = coverArtUrl(artist.id.value),
                        showCoverArt = true,
                        canStartRadio = true,
                        canAddToQueue = true,
                        canAddToPlaylist = true,
                        canFavorite = true,
                        onItemAction = onMediaItemAction,
                    )
                }
                item {
                    Box(Modifier.height(24.dp))
                }
            }
            LetterRail(
                appColors = appColors,
                enabled = query.isBlank(),
                onJumpToLetter = onJumpToLetter,
            )
        }
    }
}

@Composable
private fun LetterRail(
    appColors: DesktopAppColors,
    enabled: Boolean,
    onJumpToLetter: (Char) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .width(18.dp)
            .padding(top = 2.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        LibraryJumpLetters.forEach { letter ->
            Text(
                letter.toString(),
                color = if (enabled) appColors.secondaryText else appColors.mutedText.copy(alpha = 0.45f),
                fontSize = 10.sp,
                modifier = Modifier.clickable(enabled = enabled) { onJumpToLetter(letter) },
            )
        }
    }
}

enum class DesktopLibraryTab(val label: String) {
    Artists("Artists"),
    Albums("Albums"),
}

val LibraryJumpLetters: List<Char> = listOf('#') + ('A'..'Z')
