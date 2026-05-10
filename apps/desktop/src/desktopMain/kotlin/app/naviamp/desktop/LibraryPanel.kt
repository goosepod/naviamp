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
    listState: LazyListState,
    coverArtUrl: (String?) -> String?,
    onQueryChanged: (String) -> Unit,
    onTabSelected: (LibraryTab) -> Unit,
    onLoadMore: () -> Unit,
    onJumpToLetter: (Char) -> Unit,
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

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
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

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f),
        ) {
            LibraryTab.entries.forEach { tab ->
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
                        LibraryTab.Artists -> "Artists"
                        LibraryTab.Albums -> "Albums"
                    }
                    Text(title, color = appColors.primaryText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                if (selectedTab == LibraryTab.Artists && snapshot.artists.isEmpty()) {
                    item {
                        Text("Nothing here yet.", color = appColors.secondaryText, fontSize = 12.sp)
                    }
                }
                if (selectedTab == LibraryTab.Albums && snapshot.albums.isEmpty()) {
                    item {
                        Text("Nothing here yet.", color = appColors.secondaryText, fontSize = 12.sp)
                    }
                }
                when (selectedTab) {
                    LibraryTab.Artists -> items(snapshot.artists, key = { it.id.value }) { artist ->
                        ArtistRow(
                            appColors = appColors,
                            artist = artist,
                            onClick = { onArtistSelected(artist) },
                        )
                    }
                    LibraryTab.Albums -> items(snapshot.albums, key = { it.id.value }) { album ->
                        AlbumRow(
                            appColors = appColors,
                            album = album,
                            coverArtUrl = coverArtUrl(album.coverArtId),
                            onClick = { onAlbumSelected(album) },
                        )
                    }
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
fun LibraryListLoadMoreEffect(
    selectedTab: LibraryTab,
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
            LibraryTab.Artists -> snapshot.artists.size
            LibraryTab.Albums -> snapshot.albums.size
        }
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@LaunchedEffect
        if (visibleCount > 0 && lastVisible >= visibleCount - 8) {
            onLoadMore()
        }
    }
}

@Composable
private fun LetterRail(
    appColors: AppColors,
    enabled: Boolean,
    onJumpToLetter: (Char) -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .width(18.dp)
            .padding(top = 2.dp)
            .verticalScroll(scrollState),
    ) {
        LibraryJumpLetters.forEach { letter ->
            Text(
                letter.toString(),
                color = if (enabled) appColors.secondaryText else appColors.mutedText.copy(alpha = 0.45f),
                fontSize = 10.sp,
                modifier = Modifier.clickable(enabled = enabled) {
                    onJumpToLetter(letter)
                },
            )
        }
    }
}

enum class LibraryTab(val label: String) {
    Artists("Artists"),
    Albums("Albums"),
}

val LibraryJumpLetters: List<Char> = listOf('#') + ('A'..'Z')
