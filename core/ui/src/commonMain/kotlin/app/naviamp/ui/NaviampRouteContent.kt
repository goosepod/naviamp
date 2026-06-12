package app.naviamp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SharedHome(
    colors: NaviampColors,
    home: SharedHomeUi,
    onAlbumSelected: (SharedMediaItemUi) -> Unit,
    onMixAlbumSelected: (SharedMediaItemUi) -> Unit,
    onPlaylistSelected: (SharedMediaItemUi) -> Unit,
    onRecentRadioSelected: (SharedMediaItemUi) -> Unit,
    onInternetRadioStationSelected: (SharedMediaItemUi) -> Unit,
    onMixBuilderSelected: (SharedMixBuilderUi) -> Unit,
    onHomeStationSelected: (SharedHomeStationUi) -> Unit,
    onAlbumFavoriteToggled: (SharedMediaItemUi) -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Music", color = colors.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        if (home.isEmpty) {
            PlaceholderTile("Home sections will appear after connection.", colors)
        }
        if (home.mixAlbums.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                SectionHeader("MIXES FOR YOU", colors)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    home.mixAlbums.take(6).forEach { album ->
                        MixCard(album, colors, onClick = { onMixAlbumSelected(album) })
                    }
                }
            }
        }
        HomeSection(
            title = "Recently Played Radio",
            items = home.recentRadioStreams,
            colors = colors,
            onItemSelected = onRecentRadioSelected,
            emptyText = "Start a radio station to build this list.",
        )
        MixBuilderSection(home.mixBuilders, colors, onMixBuilderSelected)
        HomeSection("Recently Added In Music", home.recentlyAddedAlbums, colors, onAlbumSelected, onAlbumFavoriteToggled)
        HomeSection("Recent Playlists", home.playlists, colors, onPlaylistSelected)
        HomeSection("Recent Internet Radio", home.radioStations, colors, onInternetRadioStationSelected)
        HomeStationSection(home.stations, colors, onHomeStationSelected)
        HomeSection("Recent Albums", home.recentAlbums, colors, onAlbumSelected, onAlbumFavoriteToggled)
        HomeSection("Frequently Played Albums", home.frequentAlbums, colors, onAlbumSelected, onAlbumFavoriteToggled)
        HomeSection("Random Albums", home.randomAlbums, colors, onAlbumSelected, onAlbumFavoriteToggled)
        home.genreSpotlightTitle?.let { title ->
            HomeSection("More In $title", home.genreSpotlightAlbums, colors, onAlbumSelected, onAlbumFavoriteToggled)
        }
        HomeSection("From ${home.decadeLabel}", home.decadeAlbums, colors, onAlbumSelected, onAlbumFavoriteToggled)
    }
}

@Composable
private fun MixBuilderSection(
    builders: List<SharedMixBuilderUi>,
    colors: NaviampColors,
    onBuilderSelected: (SharedMixBuilderUi) -> Unit,
) {
    if (builders.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionHeader("MIX BUILDERS", colors)
        builders.forEach { builder ->
            SharedMediaRow(
                item = SharedMediaItemUi(
                    id = builder.id,
                    title = builder.title,
                    subtitle = builder.subtitle,
                ),
                colors = colors,
                onClick = { onBuilderSelected(builder) },
            )
        }
    }
}

@Composable
internal fun SearchContent(
    colors: NaviampColors,
    query: String,
    results: SharedSearchResultsUi,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onClearSearch: () -> Unit,
    onTrackSelected: (SharedTrackRowUi) -> Unit,
    onTrackAddToQueue: (SharedTrackRowUi) -> Unit,
    onAlbumSelected: (SharedMediaItemUi) -> Unit,
    onArtistSelected: (SharedMediaItemUi) -> Unit,
    onArtistFavoriteToggled: (SharedMediaItemUi) -> Unit = {},
    onAlbumFavoriteToggled: (SharedMediaItemUi) -> Unit = {},
) {
    val handleTrackAction: (SharedTrackRowActionRequest) -> Unit = { request ->
        handleSharedTrackRowAction(
            request,
            SharedTrackRowActionHandlers(
                onSelect = onTrackSelected,
                onAddToQueue = onTrackAddToQueue,
            ),
        )
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        Text("Search", color = colors.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            NaviampTextField(
                value = query,
                onValueChange = onQueryChanged,
                label = "Search tracks",
                colors = colors,
                modifier = Modifier.weight(1f),
            )
            if (query.isNotBlank() || !results.isEmpty) {
                IconButton(onClick = onClearSearch) {
                    Icon(NaviampIcons.Close, contentDescription = "Clear search", tint = colors.secondaryText)
                }
            }
        }
        if (query.isNotBlank() && results.isEmpty) {
            Text("No matches found.", color = colors.secondaryText, fontSize = 12.sp)
        }
        MediaSection("Artists", results.artists, colors, onArtistSelected, onArtistFavoriteToggled)
        MediaSection("Albums", results.albums, colors, onAlbumSelected, onAlbumFavoriteToggled)
        if (results.tracks.isNotEmpty()) {
            SectionHeader("TRACKS", colors)
            results.tracks.forEach { track ->
                TrackRow(
                    track,
                    colors,
                    onTrackSelected,
                    onAddToQueue = onTrackAddToQueue,
                    onTrackAction = handleTrackAction,
                )
            }
        }
    }
}

@Composable
internal fun MediaListContent(
    colors: NaviampColors,
    title: String,
    items: List<SharedMediaItemUi>,
    emptyText: String,
    onItemSelected: ((SharedMediaItemUi) -> Unit)? = null,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(title, color = colors.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        if (items.isEmpty()) {
            item {
                Text(emptyText, color = colors.secondaryText, fontSize = 13.sp)
            }
        }
        items(
            items = items,
            key = { item -> item.id },
        ) { item ->
            SharedMediaRow(
                item = item,
                colors = colors,
                onClick = onItemSelected?.let { { it(item) } },
            )
        }
    }
}

@Composable
internal fun LibraryContent(
    colors: NaviampColors,
    items: List<SharedMediaItemUi>,
    query: String,
    syncStatus: NaviampLibrarySyncStatusUi,
    onQueryChanged: (String) -> Unit,
    onRefreshLibrary: () -> Unit,
    onArtistSelected: (SharedMediaItemUi) -> Unit,
    onArtistFavoriteToggled: (SharedMediaItemUi) -> Unit = {},
) {
    val filteredItems = remember(items, query) {
        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.isBlank()) {
            items
        } else {
            items.filter { item ->
                item.title.lowercase().contains(normalizedQuery) ||
                    item.subtitle.lowercase().contains(normalizedQuery) ||
                    item.meta.lowercase().contains(normalizedQuery)
            }
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Library", color = colors.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                NaviampTextField(
                    value = query,
                    onValueChange = onQueryChanged,
                    label = "Search library artists",
                    colors = colors,
                    modifier = Modifier.weight(1f),
                )
                if (query.isNotBlank()) {
                    IconButton(onClick = { onQueryChanged("") }) {
                        Icon(NaviampIcons.Close, contentDescription = "Clear library search", tint = colors.secondaryText)
                    }
                }
            }
        }
        syncStatus.message?.let { message ->
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        message,
                        color = colors.secondaryText,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                    if (syncStatus.showRefresh) {
                        TextButton(
                            enabled = !syncStatus.isSyncing,
                            onClick = onRefreshLibrary,
                        ) {
                            Text(if (syncStatus.isSyncing) "Refreshing..." else "Refresh", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        if (filteredItems.isEmpty()) {
            item {
                Text(
                    if (query.isBlank()) "No library artists found." else "No library artists match.",
                    color = colors.secondaryText,
                    fontSize = 13.sp,
                )
            }
        }
        items(
            items = filteredItems,
            key = { item -> item.id },
        ) { item ->
            SharedMediaRow(
                item = item,
                colors = colors,
                onClick = { onArtistSelected(item) },
                onFavoriteToggled = onArtistFavoriteToggled,
            )
        }
    }
}

@Composable
internal fun DownloadsContent(
    colors: NaviampColors,
    downloads: List<NaviampDownloadedTrackUi>,
    status: String?,
    downloadBytes: Long,
    maxDownloadBytes: Long,
    onTrackSelected: (NaviampDownloadedTrackUi) -> Unit,
    playlistChoices: List<NaviampPlaylistChoiceUi>,
    playlistActionStatus: String?,
    onAddToPlaylist: (NaviampDownloadedTrackUi, NaviampPlaylistChoiceUi?) -> Unit,
    onCreatePlaylistAndAdd: (NaviampDownloadedTrackUi, String) -> Unit,
    onRemoveDownload: (NaviampDownloadedTrackUi) -> Unit,
) {
    var downloadForPlaylist by remember { mutableStateOf<NaviampDownloadedTrackUi?>(null) }
    val remainingBytes = (maxDownloadBytes - downloadBytes).coerceAtLeast(0L)
    val usedPercent = if (maxDownloadBytes > 0L) {
        ((downloadBytes.toDouble() / maxDownloadBytes.toDouble()) * 100.0).coerceIn(0.0, 100.0)
    } else {
        0.0
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Downloads", color = colors.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${downloads.size} files - ${downloadBytes.storageBytesLabel()} of ${maxDownloadBytes.storageBytesLabel()}",
                    color = colors.secondaryText,
                    fontSize = 12.sp,
                )
                Text(
                    "${remainingBytes.storageBytesLabel()} remaining - ${usedPercent.oneDecimalLabel()}% used",
                    color = colors.mutedText,
                    fontSize = 11.sp,
                )
            }
        }
        status?.takeIf { it.isNotBlank() }?.let { message ->
            item {
                Text(message, color = colors.secondaryText, fontSize = 12.sp)
            }
        }
        if (downloads.isEmpty()) {
            item {
                Text("Downloaded tracks will appear here.", color = colors.secondaryText, fontSize = 13.sp)
            }
        }
        items(
            items = downloads,
            key = { item -> item.id },
        ) { download ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onTrackSelected(download) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlatformCoverArt(download.track.coverArtUrl, colors, 38.dp, 4.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(download.track.title, color = colors.primaryText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(download.track.subtitle, color = colors.secondaryText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(download.sizeBytes.storageBytesLabel(), color = colors.mutedText, fontSize = 11.sp)
                NaviampRowOverflowMenu(
                    colors = colors,
                    items = downloadRowActions(canRemove = true, canAddToPlaylist = true).mapNotNull { action ->
                        when (action.action) {
                            NaviampAction.AddToPlaylist -> NaviampRowMenuItem(
                                label = action.label,
                                icon = action.icon,
                                onClick = { downloadForPlaylist = download },
                                enabled = action.enabled,
                            )
                            NaviampAction.RemoveDownload -> NaviampRowMenuItem(
                                label = action.label,
                                icon = action.icon,
                                onClick = { onRemoveDownload(download) },
                                enabled = action.enabled,
                            )
                            else -> null
                        }
                    },
                )
            }
        }
    }

    downloadForPlaylist?.let { download ->
        AddToPlaylistDialog(
            title = download.track.title,
            colors = colors,
            playlists = playlistChoices,
            status = playlistActionStatus,
            onDismissRequest = { downloadForPlaylist = null },
            onAddToExisting = { playlist ->
                downloadForPlaylist = null
                onAddToPlaylist(download, playlist)
            },
            onCreateAndAdd = { name ->
                downloadForPlaylist = null
                onCreatePlaylistAndAdd(download, name)
            },
        )
    }
}
