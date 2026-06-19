package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
    onSonicDiscoveryTrackAction: (SharedHomeDiscoveryTrackActionRequest) -> Unit = {},
    onRecentlyPlayedTrackAction: (SharedTrackRowActionRequest) -> Unit = {},
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
        RecentPlayedSection(home.recentlyPlayedTracks, colors, onRecentlyPlayedTrackAction)
        MixBuilderSection(home.mixBuilders, colors, onMixBuilderSelected)
        SonicDiscoverySection(home.sonicDiscoveryRows, colors, onSonicDiscoveryTrackAction)
        HomeSection(
            "Recently Added In Music",
            home.recentlyAddedAlbums,
            colors,
            onAlbumSelected,
            onAlbumFavoriteToggled,
            SharedMediaItemKind.Album,
        )
        HomeSection(
            "Recent Playlists",
            home.playlists,
            colors,
            onPlaylistSelected,
            itemKind = SharedMediaItemKind.Playlist,
        )
        HomeSection(
            "Recent Internet Radio",
            home.radioStations,
            colors,
            onInternetRadioStationSelected,
            itemKind = SharedMediaItemKind.RadioStation,
        )
        HomeStationSection(home.stations, colors, onHomeStationSelected)
        HomeSection("Recent Albums", home.recentAlbums, colors, onAlbumSelected, onAlbumFavoriteToggled, SharedMediaItemKind.Album)
        HomeSection("Frequently Played Albums", home.frequentAlbums, colors, onAlbumSelected, onAlbumFavoriteToggled, SharedMediaItemKind.Album)
        HomeSection("Random Albums", home.randomAlbums, colors, onAlbumSelected, onAlbumFavoriteToggled, SharedMediaItemKind.Album)
        home.genreSpotlightTitle?.let { title ->
            HomeSection("More In $title", home.genreSpotlightAlbums, colors, onAlbumSelected, onAlbumFavoriteToggled, SharedMediaItemKind.Album)
        }
        HomeSection("From ${home.decadeLabel}", home.decadeAlbums, colors, onAlbumSelected, onAlbumFavoriteToggled, SharedMediaItemKind.Album)
    }
}

@Composable
private fun RecentPlayedSection(
    tracks: List<SharedTrackRowUi>,
    colors: NaviampColors,
    onTrackAction: (SharedTrackRowActionRequest) -> Unit,
) {
    if (tracks.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionHeader("RECENTLY PLAYED", colors)
        tracks.take(8).forEach { track ->
            TrackRow(
                track = track,
                colors = colors,
                onTrackSelected = {
                    onTrackAction(SharedTrackRowActionRequest(track, SharedTrackRowAction.Select))
                },
                canStartRadio = true,
                canDownload = true,
                canAddToQueue = true,
                onTrackAction = onTrackAction,
            )
        }
    }
}

@Composable
private fun SonicDiscoverySection(
    rows: List<SharedHomeDiscoveryTrackRowUi>,
    colors: NaviampColors,
    onTrackAction: (SharedHomeDiscoveryTrackActionRequest) -> Unit,
) {
    rows.forEach { row ->
        if (row.tracks.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionHeader(row.title.uppercase(), colors)
                row.tracks.forEach { track ->
                    TrackRow(
                        track = track,
                        colors = colors,
                        onTrackSelected = {
                            onTrackAction(
                                SharedHomeDiscoveryTrackActionRequest(
                                    rowId = row.id,
                                    track = track,
                                    action = SharedTrackRowAction.Select,
                                ),
                            )
                        },
                        onAddToQueue = {
                            onTrackAction(
                                SharedHomeDiscoveryTrackActionRequest(
                                    rowId = row.id,
                                    track = track,
                                    action = SharedTrackRowAction.AddToQueue,
                                ),
                            )
                        },
                        onTrackAction = { request ->
                            onTrackAction(
                                SharedHomeDiscoveryTrackActionRequest(
                                    rowId = row.id,
                                    track = request.track,
                                    action = request.action,
                                ),
                            )
                        },
                    )
                }
            }
        }
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
            MixBuilderRow(
                builder = builder,
                colors = colors,
                onClick = { onBuilderSelected(builder) },
            )
        }
    }
}

@Composable
private fun MixBuilderRow(
    builder: SharedMixBuilderUi,
    colors: NaviampColors,
    onClick: () -> Unit,
) {
    val artwork = mixBuilderArtwork(builder.id)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(5.dp))
            .background(Color.Black.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Brush.linearGradient(artwork.colors)),
        ) {
            Icon(
                imageVector = artwork.icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                builder.title,
                color = colors.primaryText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                builder.subtitle,
                color = colors.secondaryText,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(">", color = colors.mutedText, fontSize = 16.sp)
    }
}

private data class MixBuilderArtwork(
    val icon: ImageVector,
    val colors: List<Color>,
)

private fun mixBuilderArtwork(id: String): MixBuilderArtwork =
    when (id) {
        "artist" -> MixBuilderArtwork(
            icon = NaviampIcons.Brain,
            colors = listOf(Color(0xFFB45CFF), Color(0xFF315BFF)),
        )
        "album" -> MixBuilderArtwork(
            icon = NaviampIcons.Library,
            colors = listOf(Color(0xFFFFA726), Color(0xFFDE3B79)),
        )
        "genre" -> MixBuilderArtwork(
            icon = NaviampTransportIcons.Radio,
            colors = listOf(Color(0xFF1BC779), Color(0xFF167BC2)),
        )
        "sonic-path" -> MixBuilderArtwork(
            icon = NaviampIcons.Brain,
            colors = listOf(Color(0xFF00C2FF), Color(0xFF7655FF)),
        )
        "sonic-mix" -> MixBuilderArtwork(
            icon = NaviampIcons.Turntable,
            colors = listOf(Color(0xFFFF5F6D), Color(0xFFFFC371)),
        )
        else -> MixBuilderArtwork(
            icon = NaviampTransportIcons.Radio,
            colors = listOf(Color(0xFF607D8B), Color(0xFF37474F)),
        )
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
        MediaSection("Artists", results.artists, colors, onArtistSelected, onArtistFavoriteToggled, SharedMediaItemKind.Artist)
        MediaSection("Albums", results.albums, colors, onAlbumSelected, onAlbumFavoriteToggled, SharedMediaItemKind.Album)
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
    itemKind: SharedMediaItemKind = SharedMediaItemKind.Unknown,
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
                itemKind = itemKind,
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
                itemKind = SharedMediaItemKind.Artist,
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
    offlineDashboard: NaviampOfflineDashboardUi,
    playlistChoices: List<NaviampPlaylistChoiceUi>,
    playlistActionStatus: String?,
    onDownloadAction: (DownloadedTrackActionRequest) -> Unit,
) {
    var downloadForPlaylist by remember { mutableStateOf<NaviampDownloadedTrackUi?>(null) }
    val handleDownloadAction: (DownloadedTrackActionRequest) -> Unit = { request ->
        handleDownloadedTrackAction(
            request,
            DownloadedTrackActionHandlers(
                onSelect = { onDownloadAction(request) },
                onAddToPlaylist = { download, playlist ->
                    if (playlist == null) downloadForPlaylist = download else onDownloadAction(request)
                },
                onCreatePlaylistAndAdd = { _, _ -> onDownloadAction(request) },
                onRemove = { onDownloadAction(request) },
            ),
        )
    }
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
                Text("Offline Mode", color = colors.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
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
        item {
            OfflineDashboardSummary(
                colors = colors,
                downloads = downloads,
                downloadBytes = downloadBytes,
                maxDownloadBytes = maxDownloadBytes,
                offlineDashboard = offlineDashboard,
            )
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
                    .clickable {
                        handleDownloadAction(DownloadedTrackActionRequest(download, DownloadedTrackAction.Select))
                    }
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
                                onClick = {
                                    handleDownloadAction(
                                        DownloadedTrackActionRequest(download, DownloadedTrackAction.AddToPlaylist),
                                    )
                                },
                                enabled = action.enabled,
                            )
                            NaviampAction.RemoveDownload -> NaviampRowMenuItem(
                                label = action.label,
                                icon = action.icon,
                                onClick = {
                                    handleDownloadAction(
                                        DownloadedTrackActionRequest(download, DownloadedTrackAction.Remove),
                                    )
                                },
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
                handleDownloadAction(
                    DownloadedTrackActionRequest(
                        download = download,
                        action = DownloadedTrackAction.AddToPlaylist,
                        playlistChoice = playlist,
                    ),
                )
            },
            onCreateAndAdd = { name ->
                downloadForPlaylist = null
                handleDownloadAction(
                    DownloadedTrackActionRequest(
                        download = download,
                        action = DownloadedTrackAction.CreatePlaylistAndAdd,
                        playlistName = name,
                    ),
                )
            },
        )
    }
}

@Composable
private fun OfflineDashboardSummary(
    colors: NaviampColors,
    downloads: List<NaviampDownloadedTrackUi>,
    downloadBytes: Long,
    maxDownloadBytes: Long,
    offlineDashboard: NaviampOfflineDashboardUi,
) {
    val ready = downloads.isNotEmpty()
    val readyMessage = if (ready) {
        "Ready for offline playback and Android Auto Downloads browsing."
    } else {
        "Download albums, playlists, or tracks before using offline mode."
    }
    val downloadPercent = storagePercentLabel(downloadBytes, maxDownloadBytes)
    val audioCachePercent = storagePercentLabel(
        offlineDashboard.audioCacheBytes,
        offlineDashboard.maxAudioCacheBytes,
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.2f))
            .padding(12.dp),
    ) {
        Text("OFFLINE DASHBOARD", color = colors.primaryText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(readyMessage, color = if (ready) colors.primaryText else colors.secondaryText, fontSize = 13.sp)
        OfflineDashboardMetric(
            colors = colors,
            label = "Downloaded tracks",
            value = downloads.size.toString(),
            detail = "${downloadBytes.storageBytesLabel()} used - $downloadPercent of download budget",
        )
        OfflineDashboardMetric(
            colors = colors,
            label = "Playback cache",
            value = offlineDashboard.audioCacheCount.toString(),
            detail = "${offlineDashboard.audioCacheBytes.storageBytesLabel()} used - $audioCachePercent of streaming cache",
        )
        OfflineDashboardMetric(
            colors = colors,
            label = "Pending actions",
            value = "0",
            detail = "Downloads are applied immediately; failed sync tracking is not stored yet.",
        )
    }
}

@Composable
private fun OfflineDashboardMetric(
    colors: NaviampColors,
    label: String,
    value: String,
    detail: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(value, color = colors.primaryText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(1.dp), modifier = Modifier.weight(1f)) {
            Text(label, color = colors.secondaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = colors.mutedText, fontSize = 11.sp)
        }
    }
}

private fun storagePercentLabel(
    usedBytes: Long,
    maxBytes: Long,
): String =
    if (maxBytes > 0L) {
        ((usedBytes.toDouble() / maxBytes.toDouble()) * 100.0)
            .coerceIn(0.0, 100.0)
            .oneDecimalLabel() + "%"
    } else {
        "0.0%"
    }
