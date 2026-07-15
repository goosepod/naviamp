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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.domain.cache.LibrarySnapshot
import app.naviamp.ui.SharedMediaItemActionRequest

@Composable
fun DesktopLibraryPanel(
    appColors: DesktopAppColors,
    snapshot: LibrarySnapshot,
    query: String,
    selectedTab: DesktopLibraryTab,
    status: String?,
    isSyncing: Boolean,
    listState: LazyListState,
    coverArtUrl: (String?) -> String?,
    onQueryChanged: (String) -> Unit,
    onTabSelected: (DesktopLibraryTab) -> Unit,
    onLoadMore: () -> Unit,
    onMediaItemAction: (SharedMediaItemActionRequest) -> Unit,
    onRefreshLibrary: () -> Unit,
) {
    val searchFocusRequester = remember { FocusRequester() }
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = appColors.primaryText,
        unfocusedTextColor = appColors.primaryText,
        focusedLabelColor = appColors.primaryText,
        unfocusedLabelColor = appColors.secondaryText,
        cursorColor = appColors.primaryText,
        focusedBorderColor = appColors.accent,
        unfocusedBorderColor = appColors.border,
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Library", color = appColors.primaryText, style = MaterialTheme.typography.titleMedium)
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

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChanged,
            label = { Text("Search") },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(
                        onClick = {
                            onQueryChanged("")
                            searchFocusRequester.requestFocus()
                        },
                    ) {
                        Icon(
                            imageVector = DesktopNavigationIcons.Close,
                            contentDescription = "Clear library search",
                            tint = appColors.secondaryText,
                        )
                    }
                }
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().focusRequester(searchFocusRequester),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions.Default,
            colors = textFieldColors,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f),
        ) {
            DesktopLibraryTab.entries.forEach { tab ->
                FilterChip(
                    selected = selectedTab == tab,
                    onClick = { onTabSelected(tab) },
                    label = { Text(tab.label, fontSize = 12.sp) },
                    modifier = Modifier.height(28.dp),
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f),
            ) {
                item {
                    val title = when (selectedTab) {
                        DesktopLibraryTab.Artists -> "Artists"
                        DesktopLibraryTab.Albums -> "Albums"
                    }
                    Text(title, color = appColors.primaryText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                if (selectedTab == DesktopLibraryTab.Artists && snapshot.artists.isEmpty()) {
                    item {
                        Text("Nothing here yet.", color = appColors.secondaryText, fontSize = 12.sp)
                    }
                }
                if (selectedTab == DesktopLibraryTab.Albums && snapshot.albums.isEmpty()) {
                    item {
                        Text("Nothing here yet.", color = appColors.secondaryText, fontSize = 12.sp)
                    }
                }
                when (selectedTab) {
                    DesktopLibraryTab.Artists -> items(snapshot.artists, key = { it.id.value }) { artist ->
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
                    DesktopLibraryTab.Albums -> items(snapshot.albums, key = { it.id.value }) { album ->
                        DesktopAlbumRow(
                            appColors = appColors,
                            album = album,
                            coverArtUrl = coverArtUrl(album.coverArtId),
                            canStartRadio = true,
                            canDownload = true,
                            canAddToQueue = true,
                            canAddToPlaylist = true,
                            canFavorite = true,
                            onItemAction = onMediaItemAction,
                        )
                    }
                }
                item {
                    Box(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
fun DesktopLibraryListLoadMoreEffect(
    selectedTab: DesktopLibraryTab,
    snapshot: LibrarySnapshot,
    listState: LazyListState,
    onLoadMore: () -> Unit,
) {
    androidx.compose.runtime.LaunchedEffect(
        selectedTab,
        snapshot.artists.size,
        snapshot.albums.size,
        listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index,
    ) {
        val visibleCount = when (selectedTab) {
            DesktopLibraryTab.Artists -> snapshot.artists.size
            DesktopLibraryTab.Albums -> snapshot.albums.size
        }
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@LaunchedEffect
        if (visibleCount > 0 && lastVisible >= visibleCount - 8) {
            onLoadMore()
        }
    }
}

enum class DesktopLibraryTab(val label: String) {
    Artists("Artists"),
    Albums("Albums"),
}

val LibraryJumpLetters: List<Char> = listOf('#') + ('A'..'Z')
