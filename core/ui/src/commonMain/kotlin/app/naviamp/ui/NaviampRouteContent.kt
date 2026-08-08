package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.ui.generated.resources.Res
import app.naviamp.ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.launch
import app.naviamp.domain.settings.HomeSectionLayout
import app.naviamp.domain.settings.HomeSectionPageLayout

@Composable
fun SharedHome(
    colors: NaviampColors,
    home: SharedHomeUi,
    actions: NaviampHomeActions,
    mediaActions: NaviampMediaActions,
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
                items = listOf(NaviampRowMenuItem("Refresh", NaviampIcons.Refresh, actions.onRefresh)),
            )
        }
        if (home.isEmpty) {
            PlaceholderTile(stringResource(Res.string.home_empty), colors)
        }
        home.collectionSections.forEach { section ->
            HomeCollectionSection(
                section = section,
                colors = colors,
                actions = actions,
                mediaActions = mediaActions,
                onTitleSelected = { actions.onCollectionSelected(section.id) },
                onItemSelected = { item -> dispatchHomeCollectionItem(item, actions, mediaActions) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeCollectionSection(
    section: SharedHomeCollectionSectionUi,
    colors: NaviampColors,
    actions: NaviampHomeActions,
    mediaActions: NaviampMediaActions,
    onTitleSelected: () -> Unit,
    onItemSelected: (SharedHomeCollectionItemUi) -> Unit,
) {
    if (section.items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (section.homeLayout) {
            HomeSectionLayout.Carousel -> HomeCollectionCarousel(
                section = section,
                colors = colors,
                onTitleSelected = onTitleSelected,
                onItemSelected = onItemSelected,
            )
            HomeSectionLayout.List -> {
                HomeCollectionSectionTitle(section.title, colors, onTitleSelected)
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    section.items.forEach { item ->
                        HomeCollectionItemListRow(item, colors, actions, mediaActions)
                    }
                }
            }
            HomeSectionLayout.Grid -> {
                HomeCollectionSectionTitle(section.title, colors, onTitleSelected)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(HomeCollectionGridSpacing),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    section.items.forEach { item ->
                        HomeCollectionGridCard(
                            item = item,
                            colors = colors,
                            width = HomeCollectionHomeGridCardWidth,
                            onClick = { onItemSelected(item) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeCollectionCarousel(
    section: SharedHomeCollectionSectionUi,
    colors: NaviampColors,
    onTitleSelected: () -> Unit,
    onItemSelected: (SharedHomeCollectionItemUi) -> Unit,
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val scrollStep = with(LocalDensity.current) { HomeRailScrollStep.toPx() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        HomeCollectionSectionTitle(
            title = section.title,
            colors = colors,
            onTitleSelected = onTitleSelected,
            modifier = Modifier.weight(1f),
        )
        HomeRailArrow(
            pointsRight = false,
            enabled = scrollState.canScrollBackward,
            colors = colors,
            onClick = { scope.launch { scrollState.animateScrollBy(-scrollStep) } },
        )
        HomeRailArrow(
            pointsRight = true,
            enabled = scrollState.canScrollForward,
            colors = colors,
            onClick = { scope.launch { scrollState.animateScrollBy(scrollStep) } },
        )
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.horizontalScroll(scrollState),
    ) {
        section.items.forEach { item ->
            HomeCollectionGridCard(
                item = item,
                colors = colors,
                width = 128.dp,
                onClick = { onItemSelected(item) },
            )
        }
    }
}

@Composable
private fun HomeCollectionSectionTitle(
    title: String,
    colors: NaviampColors,
    onTitleSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .clickable(onClick = onTitleSelected)
            .padding(vertical = 3.dp),
    ) {
        SectionHeader(title, colors)
        Icon(
            imageVector = NaviampIcons.ChevronRight,
            contentDescription = null,
            tint = colors.secondaryText,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun HomeRailArrow(
    pointsRight: Boolean,
    enabled: Boolean,
    colors: NaviampColors,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(32.dp),
    ) {
        Icon(
            imageVector = NaviampIcons.ChevronRight,
            contentDescription = if (pointsRight) "Scroll right" else "Scroll left",
            tint = if (enabled) colors.primaryText else colors.mutedText.copy(alpha = 0.45f),
            modifier = Modifier
                .size(19.dp)
                .graphicsLayer { rotationZ = if (pointsRight) 0f else 180f },
        )
    }
}

@Composable
private fun HomeCollectionGridCard(
    item: SharedHomeCollectionItemUi,
    colors: NaviampColors,
    width: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .width(width)
            .clip(RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .padding(bottom = 4.dp),
    ) {
        HomeCollectionArtwork(item, colors, width)
        Text(
            text = item.title,
            color = colors.primaryText,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        Text(
            text = item.subtitle,
            color = colors.secondaryText,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

@Composable
private fun HomeCollectionArtwork(
    item: SharedHomeCollectionItemUi,
    colors: NaviampColors,
    size: androidx.compose.ui.unit.Dp,
) {
    if (item.artwork == SharedHomeCollectionArtwork.CoverArt) {
        NaviampCoverArt(item.mediaItem.coverArtUrl, colors, size, 7.dp)
        return
    }
    val artwork = navibeatMixArtwork(item.artworkKey.orEmpty())
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(7.dp))
            .background(Brush.linearGradient(artwork.colors)),
    ) {
        Icon(
            imageVector = artwork.icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.94f),
            modifier = Modifier.size(size * 0.36f),
        )
        Text(
            text = "NAVIBEAT MIX",
            color = Color.White.copy(alpha = 0.72f),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
        )
    }
}

private data class NavibeatMixArtwork(
    val icon: ImageVector,
    val colors: List<Color>,
)

private fun navibeatMixArtwork(kind: String): NavibeatMixArtwork = when (kind) {
    "timeofday" -> NavibeatMixArtwork(NaviampIcons.Clock, listOf(Color(0xFFFF8A3D), Color(0xFF9B3CE8)))
    "rediscover", "discovery" -> NavibeatMixArtwork(NaviampIcons.Refresh, listOf(Color(0xFF1FB7A6), Color(0xFF315B9E)))
    "loved", "essentials", "onrepeat" -> NavibeatMixArtwork(NaviampIcons.Fire, listOf(Color(0xFFFF5F6D), Color(0xFFFFA726)))
    "artistradio", "dailymix" -> NavibeatMixArtwork(NaviampIcons.Brain, listOf(Color(0xFFB45CFF), Color(0xFF315BFF)))
    "genreradio", "decade" -> NavibeatMixArtwork(NaviampTransportIcons.Radio, listOf(Color(0xFF1BC779), Color(0xFF167BC2)))
    else -> NavibeatMixArtwork(NaviampIcons.Playlist, listOf(Color(0xFF607D8B), Color(0xFF37474F)))
}

private fun dispatchHomeCollectionItem(
    item: SharedHomeCollectionItemUi,
    actions: NaviampHomeActions,
    mediaActions: NaviampMediaActions,
) {
    when (item.action) {
        SharedHomeCollectionItemAction.PlayAlbum -> mediaActions.onMediaItemAction(item.mediaItem.playAlbumRequest())
        SharedHomeCollectionItemAction.OpenAlbum ->
            mediaActions.onMediaItemAction(item.mediaItem.albumActionRequest(NaviampArtistAlbumCommand.Select))
        SharedHomeCollectionItemAction.OpenPlaylist ->
            mediaActions.onMediaItemAction(item.mediaItem.playlistActionRequest(NaviampPlaylistMediaCommand.Select))
        SharedHomeCollectionItemAction.SelectRecentRadio -> actions.onRecentRadioSelected(item.mediaItem)
        SharedHomeCollectionItemAction.SelectInternetRadio -> actions.onInternetRadioStationSelected(item.mediaItem)
        SharedHomeCollectionItemAction.SelectStation -> item.station?.let(actions.onStationSelected)
        SharedHomeCollectionItemAction.SelectMixBuilder -> item.mixBuilder?.let(actions.onMixBuilderSelected)
        SharedHomeCollectionItemAction.SelectRecentTrack -> item.track?.let { track ->
            actions.onRecentlyPlayedTrackAction(SharedTrackRowActionRequest(track, SharedTrackRowAction.Select))
        }
        SharedHomeCollectionItemAction.SelectSonicTrack -> item.track?.let { track ->
            actions.onSonicDiscoveryTrackAction(
                SharedHomeDiscoveryTrackActionRequest(
                    rowId = item.discoveryRowId.orEmpty(),
                    track = track,
                    action = SharedTrackRowAction.Select,
                ),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SharedHomeCollectionPage(
    page: SharedHomeCollectionPageUi,
    colors: NaviampColors,
    actions: NaviampHomeActions,
    mediaActions: NaviampMediaActions,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        HomeCollectionPageHeader(page, colors, actions)
        when (page.layout) {
            HomeSectionPageLayout.List -> Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                page.section.items.forEach { item ->
                    HomeCollectionItemListRow(item, colors, actions, mediaActions)
                }
            }
            HomeSectionPageLayout.Grid -> BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val tileWidth = minOf(
                    160.dp,
                    ((maxWidth - HomeCollectionGridSpacing) / 2f).coerceAtLeast(80.dp),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(HomeCollectionGridSpacing),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    page.section.items.forEach { item ->
                        HomeCollectionGridCard(
                            item = item,
                            colors = colors,
                            width = tileWidth,
                            onClick = { dispatchHomeCollectionItem(item, actions, mediaActions) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeCollectionPageHeader(
    page: SharedHomeCollectionPageUi,
    colors: NaviampColors,
    actions: NaviampHomeActions,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth < HomeCollectionSingleRowHeaderMinWidth) {
            Column(
                verticalArrangement = Arrangement.spacedBy(1.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                HomeCollectionTitleRow(page.section.title, colors, actions.onCollectionBack)
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    HomeCollectionLayoutButtons(page, colors, actions)
                }
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                HomeCollectionTitleRow(
                    title = page.section.title,
                    colors = colors,
                    onBack = actions.onCollectionBack,
                    modifier = Modifier.weight(1f),
                )
                HomeCollectionLayoutButtons(page, colors, actions)
            }
        }
    }
}

@Composable
private fun HomeCollectionTitleRow(
    title: String,
    colors: NaviampColors,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = NaviampIcons.ChevronRight,
                contentDescription = "Back to Home",
                tint = colors.primaryText,
                modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = 180f },
            )
        }
        Text(
            text = title,
            color = colors.primaryText,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HomeCollectionLayoutButtons(
    page: SharedHomeCollectionPageUi,
    colors: NaviampColors,
    actions: NaviampHomeActions,
) {
    if (HomeSectionPageLayout.List in page.section.supportedPageLayouts) {
        HomeCollectionLayoutButton(
            label = "List",
            selected = page.layout == HomeSectionPageLayout.List,
            colors = colors,
            onClick = { actions.onCollectionPageLayoutChanged(page.section.id, HomeSectionPageLayout.List) },
        )
    }
    if (HomeSectionPageLayout.Grid in page.section.supportedPageLayouts) {
        HomeCollectionLayoutButton(
            label = "Thumbnails",
            selected = page.layout == HomeSectionPageLayout.Grid,
            colors = colors,
            onClick = { actions.onCollectionPageLayoutChanged(page.section.id, HomeSectionPageLayout.Grid) },
        )
    }
}

@Composable
private fun HomeCollectionLayoutButton(
    label: String,
    selected: Boolean,
    colors: NaviampColors,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (selected) colors.primaryText else colors.secondaryText,
        ),
    ) {
        Text(label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun HomeCollectionListRow(
    item: SharedHomeCollectionItemUi,
    colors: NaviampColors,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(6.dp),
    ) {
        HomeCollectionArtwork(item, colors, 48.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = item.title,
                color = colors.primaryText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.subtitle,
                color = colors.secondaryText,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = NaviampIcons.ChevronRight,
            contentDescription = null,
            tint = colors.mutedText,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun HomeCollectionItemListRow(
    item: SharedHomeCollectionItemUi,
    colors: NaviampColors,
    actions: NaviampHomeActions,
    mediaActions: NaviampMediaActions,
) {
    when (item.action) {
        SharedHomeCollectionItemAction.SelectRecentTrack -> item.track?.let { track ->
            TrackRow(
                track = track,
                colors = colors,
                onTrackAction = actions.onRecentlyPlayedTrackAction,
                canSelect = true,
                canStartRadio = true,
                canAddToQueue = true,
                canDownload = true,
                canAddToPlaylist = false,
            )
        }
        SharedHomeCollectionItemAction.SelectSonicTrack -> item.track?.let { track ->
            TrackRow(
                track = track,
                colors = colors,
                onTrackAction = { request ->
                    actions.onSonicDiscoveryTrackAction(
                        SharedHomeDiscoveryTrackActionRequest(
                            rowId = item.discoveryRowId.orEmpty(),
                            track = request.track,
                            action = request.action,
                            artistId = request.artistId,
                            artistName = request.artistName,
                        ),
                    )
                },
                canSelect = true,
                canStartRadio = false,
                canAddToQueue = true,
                canDownload = false,
                canAddToPlaylist = false,
                swipeContext = TrackSwipeContext.Related,
            )
        }
        SharedHomeCollectionItemAction.SelectMixBuilder -> item.mixBuilder?.let { builder ->
            MixBuilderRow(builder, colors) { actions.onMixBuilderSelected(builder) }
        }
        else -> SharedMediaRow(
            item = item.mediaItem,
            colors = colors,
            onClick = { dispatchHomeCollectionItem(item, actions, mediaActions) },
            onFavoriteToggled = if (item.mediaKind == SharedMediaItemKind.Album) {
                { album ->
                    mediaActions.onMediaItemAction(
                        album.albumActionRequest(NaviampArtistAlbumCommand.ToggleFavorite),
                    )
                }
            } else {
                null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedHomeRoute(
    colors: NaviampColors,
    home: NaviampHomeScreenUi,
    actions: NaviampHomeActions,
    mediaActions: NaviampMediaActions,
    scrollState: ScrollState = rememberScrollState(),
) {
    val collectionScrollState = remember(home.collectionPage?.section?.id) { ScrollState(0) }
    home.collectionPage?.let { page ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(collectionScrollState),
        ) {
            SharedHomeCollectionPage(
                page = page,
                colors = colors,
                actions = actions,
                mediaActions = mediaActions,
            )
        }
        return
    }
    PullToRefreshBox(
        isRefreshing = home.refreshing,
        onRefresh = actions.onRefresh,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            if (home.refreshing) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    color = colors.primaryText,
                    trackColor = colors.mutedText.copy(alpha = 0.25f),
                )
            }
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
        ) {
            SharedHome(
                colors = colors,
                home = home.content,
                actions = actions,
                mediaActions = mediaActions,
            )
        }
    }
}

private val HomeRailScrollStep = 310.dp
private val HomeCollectionGridSpacing = 8.dp
private val HomeCollectionHomeGridCardWidth = 128.dp
private val HomeCollectionSingleRowHeaderMinWidth = 430.dp

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
                onTrackAction = onTrackAction,
                canSelect = true,
                canStartRadio = true,
                canAddToQueue = true,
                canDownload = true,
                canAddToPlaylist = false,
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
                        onTrackAction = { request ->
                            onTrackAction(
                                SharedHomeDiscoveryTrackActionRequest(
                                    rowId = row.id,
                                    track = request.track,
                                    action = request.action,
                                    artistId = request.artistId,
                                    artistName = request.artistName,
                                ),
                            )
                        },
                        canSelect = true,
                        canStartRadio = false,
                        canAddToQueue = true,
                        canDownload = false,
                        canAddToPlaylist = false,
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
fun NaviampSearchContent(
    colors: NaviampColors,
    screen: NaviampSearchScreenUi,
    actions: NaviampSearchActions,
    mediaActions: NaviampMediaActions,
) {
    val query = screen.query
    val results = screen.results
    val searchFocusRequester = remember { FocusRequester() }
    val mediaMenuItems: (SharedMediaItemUi, SharedMediaItemKind, List<NaviampActionSpec>) -> List<NaviampRowMenuItem> =
        { item, kind, specs ->
            specs.mapNotNull { spec ->
                val command = when (kind) {
                    SharedMediaItemKind.Artist -> spec.action.artistMediaCommandOrNull()?.let(NaviampMediaItemCommand::Artist)
                    SharedMediaItemKind.Album -> spec.action.albumMediaCommandOrNull()?.let(NaviampMediaItemCommand::Album)
                    else -> null
                }
                command?.let {
                    NaviampRowMenuItem(
                        label = spec.label,
                        icon = spec.icon,
                        onClick = { mediaActions.onMediaItemAction(NaviampMediaItemActionRequest(item, it)) },
                        enabled = spec.enabled,
                    )
                }
            }
        }
    val sharedMediaRow: @Composable (SharedMediaItemUi, SharedMediaItemKind) -> Unit = { item, kind ->
        val specs = when (kind) {
            SharedMediaItemKind.Artist -> artistRowActions(
                canStartRadio = NaviampSharedMediaCapabilities.artist.canStartRadio,
                canAddToQueue = NaviampSharedMediaCapabilities.artist.canAddToQueue,
                canAddToPlaylist = NaviampSharedMediaCapabilities.artist.canAddToPlaylist,
                canFavorite = NaviampSharedMediaCapabilities.artist.canToggleFavorite && item.canFavorite,
                favoriteActive = item.favoriteActive,
            )
            SharedMediaItemKind.Album -> albumRowActions(
                canStartRadio = NaviampSharedMediaCapabilities.album.canStartRadio,
                canDownload = NaviampSharedMediaCapabilities.album.canDownload,
                canAddToQueue = NaviampSharedMediaCapabilities.album.canAddToQueue,
                canAddToPlaylist = NaviampSharedMediaCapabilities.album.canAddToPlaylist,
                canFavorite = NaviampSharedMediaCapabilities.album.canToggleFavorite && item.canFavorite,
                favoriteActive = item.favoriteActive,
            )
            else -> emptyList()
        }
        SharedMediaRow(
            item = item,
            colors = colors,
            menuItems = mediaMenuItems(item, kind, specs),
            onClick = {
                val command = when (kind) {
                    SharedMediaItemKind.Artist -> NaviampMediaItemCommand.Artist(NaviampArtistMediaCommand.Select)
                    SharedMediaItemKind.Album -> NaviampMediaItemCommand.Album(NaviampArtistAlbumCommand.Select)
                    else -> null
                }
                command?.let { mediaActions.onMediaItemAction(NaviampMediaItemActionRequest(item, it)) }
            },
        )
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        NaviampPageTitle(stringResource(Res.string.search_title), colors)
        NaviampCompactSearchField(
            value = query,
            onValueChange = actions.onQueryChanged,
            placeholder = stringResource(Res.string.search_tracks_label),
            colors = colors,
            onClear = {
                actions.onClear()
                searchFocusRequester.requestFocus()
            },
            showClear = query.isNotBlank() || !results.isEmpty || screen.status != null || screen.searching,
            modifier = Modifier.padding(horizontal = 8.dp).focusRequester(searchFocusRequester),
        )
        screen.status?.let { status ->
            Text(status, color = colors.secondaryText, fontSize = 12.sp)
        }
        if (screen.searching) {
            Text("Searching...", color = colors.secondaryText, fontSize = 12.sp)
        } else if (query.isNotBlank() && results.isEmpty && screen.status == null) {
            Text(stringResource(Res.string.search_no_matches), color = colors.secondaryText, fontSize = 12.sp)
        }
        if (results.artists.isNotEmpty()) {
            SectionHeader(stringResource(Res.string.search_artists), colors)
            results.artists.forEach { artist -> sharedMediaRow(artist, SharedMediaItemKind.Artist) }
        }
        if (results.albums.isNotEmpty()) {
            SectionHeader(stringResource(Res.string.search_albums), colors)
            results.albums.forEach { album -> sharedMediaRow(album, SharedMediaItemKind.Album) }
        }
        if (results.tracks.isNotEmpty()) {
            SectionHeader(stringResource(Res.string.search_tracks_section), colors)
            results.tracks.forEach { track ->
                TrackRow(
                    track = track,
                    colors = colors,
                    onTrackAction = mediaActions.onTrackAction,
                    canSelect = true,
                    canStartRadio = true,
                    canAddToQueue = true,
                    canDownload = true,
                    canAddToPlaylist = true,
                    background = true,
                    horizontalPadding = 6.dp,
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
            )
        }
    }
}

@Composable
fun NaviampLibraryContent(
    colors: NaviampColors,
    screen: NaviampLibraryScreenUi,
    actions: NaviampLibraryActions,
    mediaActions: NaviampMediaActions,
    listState: LazyListState,
) {
    val items = screen.artists
    val query = screen.query
    val syncStatus = screen.syncStatus
    val searchFocusRequester = remember { FocusRequester() }
    var pendingJump by remember { mutableStateOf<Char?>(null) }
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
    androidx.compose.runtime.LaunchedEffect(items, pendingJump) {
        val letter = pendingJump ?: return@LaunchedEffect
        val boundary = if (letter == '#') "" else letter.lowercaseChar().toString()
        val index = filteredItems.indexOfFirst { item -> item.title.lowercase() >= boundary }
        if (index >= 0) {
            val headerCount = 2 + if (syncStatus.message != null) 1 else 0
            listState.scrollToItem(index + headerCount)
            pendingJump = null
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
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    NaviampPageTitle(stringResource(Res.string.library_title), colors)
                    NaviampRowOverflowMenu(
                        colors = colors,
                        items = listOf(
                            NaviampRowMenuItem(
                                label = stringResource(Res.string.library_refresh),
                                icon = NaviampIcons.Refresh,
                                onClick = actions.onRefresh,
                                enabled = !syncStatus.isSyncing,
                            ),
                        ),
                    )
                }
            }
            item {
            NaviampCompactSearchField(
                value = query,
                onValueChange = actions.onQueryChanged,
                placeholder = stringResource(Res.string.library_search_artists),
                colors = colors,
                onClear = {
                    actions.onQueryChanged("")
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
                            onClick = actions.onRefresh,
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
                val menuItems = artistRowActions(
                    canStartRadio = NaviampSharedMediaCapabilities.artist.canStartRadio,
                    canAddToQueue = NaviampSharedMediaCapabilities.artist.canAddToQueue,
                    canAddToPlaylist = NaviampSharedMediaCapabilities.artist.canAddToPlaylist,
                    canFavorite = NaviampSharedMediaCapabilities.artist.canToggleFavorite && item.canFavorite,
                    favoriteActive = item.favoriteActive,
                ).mapNotNull { spec ->
                    spec.action.artistMediaCommandOrNull()?.let { command ->
                        NaviampRowMenuItem(
                            label = spec.label,
                            icon = spec.icon,
                            onClick = {
                                mediaActions.onMediaItemAction(
                                    NaviampMediaItemActionRequest(item, NaviampMediaItemCommand.Artist(command)),
                                )
                            },
                            enabled = spec.enabled,
                        )
                    }
                }
                SharedMediaRow(
                    item = item,
                    colors = colors,
                    menuItems = menuItems,
                    onClick = {
                        mediaActions.onMediaItemAction(
                            NaviampMediaItemActionRequest(
                                item,
                                NaviampMediaItemCommand.Artist(NaviampArtistMediaCommand.Select),
                            ),
                        )
                    },
                )
            }
            if (query.isBlank() && items.isNotEmpty()) {
                item(key = "library-load-more") {
                    androidx.compose.runtime.LaunchedEffect(items.size) {
                        if (!syncStatus.isSyncing) actions.onLoadMore()
                    }
                }
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
                            pendingJump = letter
                            actions.onJumpToLetter(letter)
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun NaviampDownloadsContent(
    colors: NaviampColors,
    screen: NaviampDownloadsScreenUi,
    actions: NaviampDownloadsActions,
    playlistChoices: List<NaviampPlaylistChoiceUi>,
    playlistActionStatus: String?,
) {
    val downloads = screen.downloads
    var downloadForPlaylist by remember { mutableStateOf<NaviampDownloadedTrackUi?>(null) }
    var offlineDashboardExpanded by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    val swipeSettings = LocalTrackSwipeSettings.current
    val visibleDownloadBytes = downloads.totalDownloadBytes()
    val handleDownloadAction: (DownloadedTrackActionRequest) -> Unit = { request ->
        handleDownloadedTrackAction(
            request,
            DownloadedTrackActionHandlers(
                onSelect = { actions.onTrackAction(request) },
                onAddToPlaylist = { download, playlist ->
                    if (playlist == null) downloadForPlaylist = download else actions.onTrackAction(request)
                },
                onCreatePlaylistAndAdd = { _, _ -> actions.onTrackAction(request) },
                onRemove = { actions.onTrackAction(request) },
            ),
        )
    }
    val remainingBytes = (screen.maxDownloadBytes - visibleDownloadBytes).coerceAtLeast(0L)
    val usedPercent = if (screen.maxDownloadBytes > 0L) {
        ((visibleDownloadBytes.toDouble() / screen.maxDownloadBytes.toDouble()) * 100.0).coerceIn(0.0, 100.0)
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
                            screen.maxDownloadBytes.storageBytesLabel(),
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
                        NaviampRowMenuItem("Refresh", NaviampIcons.Refresh, actions.onRefresh),
                        NaviampRowMenuItem(
                            if (screen.keepFavoritesDownloaded) "Stop keeping favorites downloaded" else "Keep favorites downloaded",
                            NaviampTransportIcons.Heart,
                            actions.onToggleKeepFavoritesDownloaded,
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
                    maxDownloadBytes = screen.maxDownloadBytes,
                    offlineDashboard = screen.offlineDashboard,
                )
            }
        }
        if (screen.jobs.isNotEmpty()) {
            item {
                Text("DOWNLOAD ACTIVITY", color = colors.primaryText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            items(screen.jobs, key = { job -> job.id }) { job ->
                DownloadJobCard(
                    colors = colors,
                    job = job,
                    onCancel = { actions.onCancelJob(job.id) },
                    onRetry = { actions.onRetryJob(job.id) },
                )
            }
        }
        screen.status?.takeIf { it.isNotBlank() }?.let { message ->
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
                    NaviampCoverArt(download.track.coverArtUrl, colors, 42.dp, 4.dp)
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
                actions.onDeleteAll()
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
    job: NaviampDownloadJobUi,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
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
                Text(job.statusLabel, color = colors.secondaryText, fontSize = 11.sp)
            }
            val jobActionColors = ButtonDefaults.textButtonColors(
                containerColor = colors.primaryText.copy(alpha = 0.14f),
                contentColor = colors.primaryText,
            )
            if (job.canCancel) {
                TextButton(onClick = onCancel, colors = jobActionColors) { Text("Cancel") }
            }
            if (job.canRetry) {
                TextButton(onClick = onRetry, colors = jobActionColors) { Text("Retry") }
            }
        }
        LinearProgressIndicator(
            progress = { job.progress },
            modifier = Modifier.fillMaxWidth(),
            color = colors.primaryText,
            trackColor = colors.mutedText.copy(alpha = 0.25f),
        )
        job.activeItemLabel?.let { label ->
            Text(label, color = colors.secondaryText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        job.failedItemLabel?.let { label ->
            Text(
                label,
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
            value = offlineDashboard.pendingProviderActionCount.toString(),
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
