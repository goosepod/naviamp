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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.ui.generated.resources.Res
import app.naviamp.ui.generated.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import app.naviamp.domain.cache.DownloadJob
import app.naviamp.domain.cache.DownloadJobItemStatus
import app.naviamp.domain.cache.DownloadJobStatus

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
    onRefresh: () -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            NaviampPageTitle(
                title = stringResource(Res.string.home_music_title),
                colors = colors,
                modifier = Modifier.weight(1f),
            )
            NaviampRowOverflowMenu(
                colors = colors,
                items = listOf(NaviampRowMenuItem("Refresh", NaviampIcons.Refresh, onRefresh)),
            )
        }
        if (home.isEmpty) {
            PlaceholderTile(stringResource(Res.string.home_empty), colors)
        }
        if (home.mixAlbums.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                SectionHeader(stringResource(Res.string.home_mixes_for_you), colors)
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
            title = stringResource(Res.string.home_recently_played_radio),
            items = home.recentRadioStreams,
            colors = colors,
            onItemSelected = onRecentRadioSelected,
            emptyText = stringResource(Res.string.home_recent_radio_empty),
        )
        RecentPlayedSection(home.recentlyPlayedTracks, colors, onRecentlyPlayedTrackAction)
        MixBuilderSection(home.mixBuilders, colors, onMixBuilderSelected)
        SonicDiscoverySection(home.sonicDiscoveryRows, colors, onSonicDiscoveryTrackAction)
        HomeSection(
            stringResource(Res.string.home_recently_added_music),
            home.recentlyAddedAlbums,
            colors,
            onAlbumSelected,
            onAlbumFavoriteToggled,
            SharedMediaItemKind.Album,
        )
        HomeSection(
            stringResource(Res.string.home_recent_playlists),
            home.playlists,
            colors,
            onPlaylistSelected,
            itemKind = SharedMediaItemKind.Playlist,
        )
        HomeSection(
            stringResource(Res.string.home_recent_internet_radio),
            home.radioStations,
            colors,
            onInternetRadioStationSelected,
            itemKind = SharedMediaItemKind.RadioStation,
        )
        HomeStationSection(home.stations, colors, onHomeStationSelected)
        HomeSection(stringResource(Res.string.home_recent_albums), home.recentAlbums, colors, onAlbumSelected, onAlbumFavoriteToggled, SharedMediaItemKind.Album)
        HomeSection(stringResource(Res.string.home_frequently_played_albums), home.frequentAlbums, colors, onAlbumSelected, onAlbumFavoriteToggled, SharedMediaItemKind.Album)
        HomeSection(stringResource(Res.string.home_random_albums), home.randomAlbums, colors, onAlbumSelected, onAlbumFavoriteToggled, SharedMediaItemKind.Album)
        home.genreSpotlightTitle?.let { title ->
            HomeSection(stringResource(Res.string.home_more_in, title), home.genreSpotlightAlbums, colors, onAlbumSelected, onAlbumFavoriteToggled, SharedMediaItemKind.Album)
        }
        HomeSection(stringResource(Res.string.home_from_decade, home.decadeLabel), home.decadeAlbums, colors, onAlbumSelected, onAlbumFavoriteToggled, SharedMediaItemKind.Album)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedHomeRoute(
    colors: NaviampColors,
    home: SharedHomeUi,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
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
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            SharedHome(
                colors = colors,
                home = home,
                onAlbumSelected = onAlbumSelected,
                onMixAlbumSelected = onMixAlbumSelected,
                onPlaylistSelected = onPlaylistSelected,
                onRecentRadioSelected = onRecentRadioSelected,
                onInternetRadioStationSelected = onInternetRadioStationSelected,
                onMixBuilderSelected = onMixBuilderSelected,
                onHomeStationSelected = onHomeStationSelected,
                onSonicDiscoveryTrackAction = onSonicDiscoveryTrackAction,
                onRecentlyPlayedTrackAction = onRecentlyPlayedTrackAction,
                onAlbumFavoriteToggled = onAlbumFavoriteToggled,
                onRefresh = onRefresh,
            )
        }
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
        SectionHeader(stringResource(Res.string.home_recently_played), colors)
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
                        swipeContext = TrackSwipeContext.Related,
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
    val searchFocusRequester = remember { FocusRequester() }
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        NaviampPageTitle(stringResource(Res.string.search_title), colors)
        NaviampCompactSearchField(
            value = query,
            onValueChange = onQueryChanged,
            placeholder = stringResource(Res.string.search_tracks_label),
            colors = colors,
            onClear = {
                onClearSearch()
                searchFocusRequester.requestFocus()
            },
            showClear = query.isNotBlank() || !results.isEmpty,
            modifier = Modifier.padding(horizontal = 8.dp).focusRequester(searchFocusRequester),
        )
        if (query.isNotBlank() && results.isEmpty) {
            Text(stringResource(Res.string.search_no_matches), color = colors.secondaryText, fontSize = 12.sp)
        }
        MediaSection(stringResource(Res.string.search_artists), results.artists, colors, onArtistSelected, onArtistFavoriteToggled, SharedMediaItemKind.Artist)
        MediaSection(stringResource(Res.string.search_albums), results.albums, colors, onAlbumSelected, onAlbumFavoriteToggled, SharedMediaItemKind.Album)
        if (results.tracks.isNotEmpty()) {
            SectionHeader(stringResource(Res.string.search_tracks_section), colors)
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
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
    onLoadMore: () -> Unit,
    onArtistSelected: (SharedMediaItemUi) -> Unit,
    onArtistFavoriteToggled: (SharedMediaItemUi) -> Unit = {},
) {
    val searchFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
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
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
            NaviampPageTitle(stringResource(Res.string.library_title), colors)
            }
            item {
            NaviampCompactSearchField(
                value = query,
                onValueChange = onQueryChanged,
                placeholder = stringResource(Res.string.library_search_artists),
                colors = colors,
                onClear = {
                    onQueryChanged("")
                    searchFocusRequester.requestFocus()
                },
                modifier = Modifier.padding(horizontal = 8.dp).focusRequester(searchFocusRequester),
            )
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
                            Text(
                                if (syncStatus.isSyncing) stringResource(Res.string.library_refreshing) else stringResource(Res.string.library_refresh),
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
                }
            }
            if (filteredItems.isEmpty()) {
                item {
                Text(
                    if (query.isBlank()) stringResource(Res.string.library_no_artists) else stringResource(Res.string.library_no_artist_matches),
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
        if (query.isBlank()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.width(18.dp).verticalScroll(rememberScrollState()),
            ) {
                (listOf('#') + ('A'..'Z')).forEach { letter ->
                    Text(
                        text = letter.toString(),
                        color = colors.secondaryText,
                        fontSize = 10.sp,
                        modifier = Modifier.clickable {
                            val boundary = if (letter == '#') "" else letter.lowercaseChar().toString()
                            val index = filteredItems.indexOfFirst { item -> item.title.lowercase() >= boundary }
                            val headerCount = 2 + if (syncStatus.message != null) 1 else 0
                            if (index >= 0) scope.launch { listState.scrollToItem(index + headerCount) }
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun DownloadsContent(
    colors: NaviampColors,
    downloads: List<NaviampDownloadedTrackUi>,
    status: String?,
    downloadJobs: List<DownloadJob>,
    maxDownloadBytes: Long,
    offlineDashboard: NaviampOfflineDashboardUi,
    playlistChoices: List<NaviampPlaylistChoiceUi>,
    playlistActionStatus: String?,
    onDownloadAction: (DownloadedTrackActionRequest) -> Unit,
    onCancelDownloadJob: (String) -> Unit,
    onRetryDownloadJob: (String) -> Unit,
    onRefreshDownloads: () -> Unit,
    keepFavoritesDownloaded: Boolean,
    onToggleKeepFavoritesDownloaded: () -> Unit,
    onDeleteAllDownloads: () -> Unit,
) {
    var downloadForPlaylist by remember { mutableStateOf<NaviampDownloadedTrackUi?>(null) }
    var offlineDashboardExpanded by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    val swipeSettings = LocalTrackSwipeSettings.current
    val visibleDownloadBytes = downloads.totalDownloadBytes()
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
    val remainingBytes = (maxDownloadBytes - visibleDownloadBytes).coerceAtLeast(0L)
    val usedPercent = if (maxDownloadBytes > 0L) {
        ((visibleDownloadBytes.toDouble() / maxDownloadBytes.toDouble()) * 100.0).coerceIn(0.0, 100.0)
    } else {
        0.0
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                    NaviampPageTitle(stringResource(Res.string.downloads_offline_title), colors)
                    Text(
                        stringResource(
                            Res.string.downloads_summary,
                            downloads.size,
                            visibleDownloadBytes.storageBytesLabel(),
                            maxDownloadBytes.storageBytesLabel(),
                        ),
                        color = colors.secondaryText,
                        fontSize = 12.sp,
                    )
                    Text(
                        stringResource(
                            Res.string.downloads_remaining,
                            remainingBytes.storageBytesLabel(),
                            usedPercent.oneDecimalLabel() + "%",
                        ),
                        color = colors.mutedText,
                        fontSize = 11.sp,
                    )
                }
                NaviampRowOverflowMenu(
                    colors = colors,
                    items = listOf(
                        NaviampRowMenuItem("Refresh", NaviampIcons.Refresh, onRefreshDownloads),
                        NaviampRowMenuItem(
                            if (keepFavoritesDownloaded) "Stop keeping favorites downloaded" else "Keep favorites downloaded",
                            NaviampTransportIcons.Heart,
                            onToggleKeepFavoritesDownloaded,
                        ),
                        NaviampRowMenuItem("Delete All", NaviampIcons.Trash, { confirmDeleteAll = true }, downloads.isNotEmpty()),
                    ),
                )
            }
        }
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color.Black.copy(alpha = 0.12f))
                    .clickable { offlineDashboardExpanded = !offlineDashboardExpanded }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text("Offline dashboard", color = colors.primaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(
                    if (offlineDashboardExpanded) NaviampIcons.ChevronUp else NaviampIcons.ChevronDown,
                    contentDescription = if (offlineDashboardExpanded) "Hide offline dashboard" else "Show offline dashboard",
                    tint = colors.secondaryText,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (offlineDashboardExpanded) {
            item {
                OfflineDashboardSummary(
                    colors = colors,
                    downloads = downloads,
                    downloadBytes = visibleDownloadBytes,
                    maxDownloadBytes = maxDownloadBytes,
                    offlineDashboard = offlineDashboard,
                )
            }
        }
        if (downloadJobs.isNotEmpty()) {
            item {
                Text("DOWNLOAD ACTIVITY", color = colors.primaryText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            items(downloadJobs, key = { job -> job.id }) { job ->
                DownloadJobCard(
                    colors = colors,
                    job = job,
                    onCancel = { onCancelDownloadJob(job.id) },
                    onRetry = { onRetryDownloadJob(job.id) },
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
                Text(stringResource(Res.string.downloads_empty), color = colors.secondaryText, fontSize = 13.sp)
            }
        }
        items(
            items = downloads,
            key = { item -> item.id },
        ) { download ->
            SwipeActionContainer(
                swipeRight = downloadedTrackSwipeActionVisual(swipeSettings.downloadsRight, download, handleDownloadAction),
                swipeLeft = downloadedTrackSwipeActionVisual(swipeSettings.downloadsLeft, download, handleDownloadAction),
            ) { swipeModifier ->
                Row(
                    modifier = swipeModifier
                        .background(Color.Black.copy(alpha = 0.12f), RoundedCornerShape(5.dp))
                        .clickable {
                            handleDownloadAction(DownloadedTrackActionRequest(download, DownloadedTrackAction.Select))
                        }
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PlatformCoverArt(download.track.coverArtUrl, colors, 42.dp, 4.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(download.track.title, color = colors.primaryText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(download.track.subtitle, color = colors.secondaryText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOf(download.track.meta, download.qualityLabel, download.sizeBytes.storageBytesLabel()).filter { it.isNotBlank() }.joinToString(" · "),
                            color = colors.mutedText,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
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
    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Delete all downloads?") },
            text = { Text("This removes every downloaded file shown for the active source. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteAll = false
                    onDeleteAllDownloads()
                }) { Text("Delete All") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun DownloadJobCard(
    colors: NaviampColors,
    job: DownloadJob,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    val activeItem = job.items.firstOrNull { it.status == DownloadJobItemStatus.Downloading }
    val failedItem = job.items.firstOrNull { it.status == DownloadJobItemStatus.Failed }
    val statusLabel = when (job.status) {
        DownloadJobStatus.Queued -> "Queued"
        DownloadJobStatus.Running -> "${job.completedCount} of ${job.totalCount}"
        DownloadJobStatus.Completed -> "Completed · ${job.totalCount} tracks"
        DownloadJobStatus.Failed -> "Failed · ${job.completedCount} of ${job.totalCount} saved"
        DownloadJobStatus.Cancelled -> "Cancelled · ${job.completedCount} of ${job.totalCount} saved"
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(job.label, color = colors.primaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(statusLabel, color = colors.secondaryText, fontSize = 11.sp)
            }
            if (job.canCancel) TextButton(onClick = onCancel) { Text("Cancel") }
            if (job.canRetry) TextButton(onClick = onRetry) { Text("Retry") }
        }
        LinearProgressIndicator(
            progress = { job.progress },
            modifier = Modifier.fillMaxWidth(),
            color = colors.primaryText,
            trackColor = colors.mutedText.copy(alpha = 0.25f),
        )
        activeItem?.let { item ->
            Text("Downloading ${item.track.title}", color = colors.secondaryText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        failedItem?.let { item ->
            Text(
                "${item.track.title}: ${item.failureMessage ?: "Download failed"}",
                color = colors.secondaryText,
                fontSize = 11.sp,
            )
        }
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
        stringResource(Res.string.offline_ready)
    } else {
        stringResource(Res.string.offline_not_ready)
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
        Text(stringResource(Res.string.offline_dashboard_title), color = colors.primaryText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(readyMessage, color = if (ready) colors.primaryText else colors.secondaryText, fontSize = 13.sp)
        OfflineDashboardMetric(
            colors = colors,
            label = stringResource(Res.string.offline_downloaded_tracks),
            value = downloads.size.toString(),
            detail = stringResource(Res.string.offline_download_budget_detail, downloadBytes.storageBytesLabel(), downloadPercent),
        )
        OfflineDashboardMetric(
            colors = colors,
            label = stringResource(Res.string.offline_playback_cache),
            value = offlineDashboard.audioCacheCount.toString(),
            detail = stringResource(Res.string.offline_streaming_cache_detail, offlineDashboard.audioCacheBytes.storageBytesLabel(), audioCachePercent),
        )
        OfflineDashboardMetric(
            colors = colors,
            label = stringResource(Res.string.offline_pending_actions),
            value = "0",
            detail = stringResource(Res.string.offline_pending_detail),
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
