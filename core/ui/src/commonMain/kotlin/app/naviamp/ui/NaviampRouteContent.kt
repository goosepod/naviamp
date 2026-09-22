package app.naviamp.ui
import app.naviamp.ui.generated.resources.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
    val visibleSections = home.collectionSections.filter { it.visible }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (home.isEmpty || visibleSections.isEmpty()) {
            PlaceholderTile(stringResource(Res.string.home_empty), colors)
        }
        visibleSections.forEach { section ->
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
    val sectionTitle = section.localizedTitle()
    val homeSection = section.copy(items = section.items.take(section.homeItemLimit ?: section.items.size))
    if (homeSection.favoriteArtistSort != null) {
        FavoriteArtistStatus(homeSection, colors)
        if (homeSection.items.isEmpty()) return
    } else if (homeSection.items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (homeSection.homeLayout) {
            HomeSectionLayout.Carousel -> HomeCollectionCarousel(
                section = homeSection,
                colors = colors,
                actions = actions,
                mediaActions = mediaActions,
                onTitleSelected = onTitleSelected,
                onItemSelected = onItemSelected,
            )
            HomeSectionLayout.List -> {
                HomeCollectionSectionTitle(sectionTitle, colors, onTitleSelected)
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    homeSection.items.forEach { item ->
                        HomeCollectionItemListRow(item, colors, actions, mediaActions)
                    }
                }
            }
            HomeSectionLayout.Grid -> {
                HomeCollectionSectionTitle(sectionTitle, colors, onTitleSelected)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(HomeCollectionGridSpacing),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    homeSection.items.forEach { item ->
                        HomeCollectionGridCard(
                            item = item,
                            colors = colors,
                            width = HomeCollectionHomeGridCardWidth,
                            onClick = { onItemSelected(item) },
                            menuItems = homeCollectionMenuItems(item, actions, mediaActions, includeFavorite = true),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteArtistStatus(section: SharedHomeCollectionSectionUi, colors: NaviampColors) {
    if (section.favoriteArtistSort == null) return
    if (section.items.isEmpty()) Text(section.localizedTitle(), color = colors.primaryText)
    val status = when {
        section.favoriteArtistsStatus == app.naviamp.domain.home.FavoriteArtistsStatus.Failed -> Res.string.favorite_artists_failed
        section.favoriteArtistsStatus == app.naviamp.domain.home.FavoriteArtistsStatus.Cached -> Res.string.favorite_artists_cached
        section.items.isEmpty() -> Res.string.favorite_artists_empty
        else -> null
    }
    status?.let { Text(stringResource(it), color = colors.secondaryText) }
}

@Composable
internal fun SharedHomeCollectionSectionUi.localizedTitle(): String = when (titleResource) {
    SharedHomeCollectionTitleResource.FavoriteArtists -> stringResource(Res.string.home_favorite_artists)
    null -> when {
        id == app.naviamp.domain.settings.HomeSectionIds.GenreSpotlight && titleArgument != null ->
            stringResource(Res.string.home_more_in, titleArgument).uppercase()
        id == app.naviamp.domain.settings.HomeSectionIds.Decade && titleArgument != null ->
            stringResource(Res.string.home_from_decade, titleArgument).uppercase()
        else -> HomeScreenSectionOptions.firstOrNull { it.id == id }?.titleResource
            ?.let { stringResource(it).uppercase() } ?: title
    }
}

@Composable
private fun HomeCollectionCarousel(
    section: SharedHomeCollectionSectionUi,
    colors: NaviampColors,
    actions: NaviampHomeActions,
    mediaActions: NaviampMediaActions,
    onTitleSelected: () -> Unit,
    onItemSelected: (SharedHomeCollectionItemUi) -> Unit,
) {
    val sectionTitle = section.localizedTitle()
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val itemStride = with(density) { HomeCollectionCarouselItemStride.roundToPx() }
    val itemWidth = with(density) { HomeCollectionCarouselCardWidth.roundToPx() }
    val television = LocalNaviampApplicationSurface.current == NaviampApplicationSurface.Television
    var focusedItemIndex by remember(section.id) { mutableStateOf<Int?>(null) }
    LaunchedEffect(television, focusedItemIndex) {
        val index = focusedItemIndex ?: return@LaunchedEffect
        if (!television) return@LaunchedEffect
        scrollState.animateScrollTo(
            homeCarouselFocusedItemScrollTarget(
                current = scrollState.value,
                viewport = scrollState.viewportSize,
                itemIndex = index,
                itemWidth = itemWidth,
                itemStride = itemStride,
                maximum = scrollState.maxValue,
            ),
        )
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        HomeCollectionSectionTitle(
            title = sectionTitle,
            colors = colors,
            onTitleSelected = onTitleSelected,
            modifier = Modifier.weight(1f),
        )
        HomeRailArrow(
            pointsRight = false,
            enabled = scrollState.canScrollBackward,
            colors = colors,
            onClick = {
                scope.launch {
                    scrollState.animateScrollTo(
                        homeCarouselScrollTarget(
                            current = scrollState.value,
                            viewport = scrollState.viewportSize,
                            itemStride = itemStride,
                            maximum = scrollState.maxValue,
                            forward = false,
                        ),
                    )
                }
            },
        )
        HomeRailArrow(
            pointsRight = true,
            enabled = scrollState.canScrollForward,
            colors = colors,
            onClick = {
                scope.launch {
                    scrollState.animateScrollTo(
                        homeCarouselScrollTarget(
                            current = scrollState.value,
                            viewport = scrollState.viewportSize,
                            itemStride = itemStride,
                            maximum = scrollState.maxValue,
                            forward = true,
                        ),
                    )
                }
            },
        )
    }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val trailingSpace = (maxWidth - HomeCollectionCarouselCardWidth).coerceAtLeast(0.dp)
        Row(
            horizontalArrangement = Arrangement.spacedBy(HomeCollectionCarouselSpacing),
            modifier = Modifier
                .horizontalScroll(scrollState)
                .padding(end = trailingSpace),
        ) {
            section.items.forEachIndexed { index, item ->
                HomeCollectionGridCard(
                    item = item,
                    colors = colors,
                    width = HomeCollectionCarouselCardWidth,
                    televisionFocus = television,
                    onFocused = { focusedItemIndex = index },
                    onClick = { onItemSelected(item) },
                    menuItems = homeCollectionMenuItems(item, actions, mediaActions, includeFavorite = true),
                )
            }
        }
    }
}

internal fun homeCarouselScrollTarget(
    current: Int,
    viewport: Int,
    itemStride: Int,
    maximum: Int,
    forward: Boolean,
): Int {
    if (itemStride <= 0 || maximum <= 0) return 0
    val target = if (forward) {
        val firstItemAtRightEdge = (current + viewport) / itemStride
        maxOf(current + itemStride, firstItemAtRightEdge * itemStride)
    } else {
        val precedingPartialItem = current % itemStride
        if (precedingPartialItem != 0) {
            current - precedingPartialItem
        } else {
            val previousViewportStart = (current - viewport).coerceAtLeast(0)
            val previousItem = (previousViewportStart + itemStride - 1) / itemStride
            previousItem * itemStride
        }
    }
    return target.coerceIn(0, maximum)
}

internal fun homeCarouselFocusedItemScrollTarget(
    current: Int,
    viewport: Int,
    itemIndex: Int,
    itemWidth: Int,
    itemStride: Int,
    maximum: Int,
): Int {
    if (itemWidth <= 0 || itemStride <= 0 || viewport <= 0 || maximum <= 0) return 0
    val target = (itemIndex.coerceAtLeast(0) - TelevisionCarouselFocusAnchorIndex)
        .coerceAtLeast(0) * itemStride
    return target.coerceIn(0, maximum)
}

private const val TelevisionCarouselFocusAnchorIndex = 2

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
    menuItems: List<NaviampRowMenuItem> = emptyList(),
    televisionFocus: Boolean = false,
    onFocused: () -> Unit = {},
) {
    var focused by remember(item.mediaItem.id) { mutableStateOf(false) }
    val shape = RoundedCornerShape(7.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .width(width)
            .onFocusChanged { focusState ->
                focused = focusState.isFocused
                if (focusState.isFocused) onFocused()
            }
            .clip(shape)
            .then(
                if (televisionFocus && focused) {
                    Modifier
                        .background(colors.accent.copy(alpha = 0.18f))
                        .border(3.dp, colors.accent, shape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(bottom = 4.dp),
    ) {
        Box {
            HomeCollectionArtwork(item, colors, width)
            NaviampRowOverflowMenu(
                colors = colors,
                items = menuItems,
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
            )
        }
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
internal fun HomeCollectionArtwork(
    item: SharedHomeCollectionItemUi,
    colors: NaviampColors,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val artwork = when (item.artwork) {
        SharedHomeCollectionArtwork.CoverArt -> {
            val cornerRadius = mediaArtworkCornerRadius(item.mediaKind, size, 7.dp)
            NaviampCoverArt(item.mediaItem.coverArtUrl, colors, size, cornerRadius, modifier)
            return
        }
        SharedHomeCollectionArtwork.NavibeatGenerated -> navibeatMixArtwork(item.artworkKey.orEmpty())
        SharedHomeCollectionArtwork.StationGenerated -> stationArtwork(item.artworkKey.orEmpty())
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(7.dp))
            .background(Brush.linearGradient(artwork.colors)),
    ) {
        Icon(
            imageVector = artwork.icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.94f),
            modifier = Modifier
                .offset(
                    y = if (item.artwork == SharedHomeCollectionArtwork.NavibeatGenerated) {
                        NavibeatMixIconVerticalOffset
                    } else {
                        0.dp
                    },
                )
                .size(
                    if (item.artwork == SharedHomeCollectionArtwork.NavibeatGenerated) {
                        navibeatMixIconSize(size)
                    } else {
                        size * 0.46f
                    },
                ),
        )
        if (item.artwork == SharedHomeCollectionArtwork.NavibeatGenerated) {
            Text(
                text = "NAVIBEAT MIX",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
            )
        }
    }
}

private data class NavibeatMixArtwork(
    val icon: ImageVector,
    val colors: List<Color>,
)

internal fun navibeatMixIconSize(artworkSize: androidx.compose.ui.unit.Dp) = artworkSize * 0.54f

private fun navibeatMixArtwork(kind: String): NavibeatMixArtwork = when (kind) {
    "timeofday" -> NavibeatMixArtwork(NaviampIcons.Clock, listOf(Color(0xFFFF8A3D), Color(0xFF9B3CE8)))
    "rediscover", "discovery" -> NavibeatMixArtwork(NaviampIcons.Refresh, listOf(Color(0xFF1FB7A6), Color(0xFF315B9E)))
    "loved", "essentials", "onrepeat" -> NavibeatMixArtwork(NaviampIcons.Fire, listOf(Color(0xFFFF5F6D), Color(0xFFFFA726)))
    "artistradio", "dailymix" -> NavibeatMixArtwork(NaviampIcons.Brain, listOf(Color(0xFFB45CFF), Color(0xFF315BFF)))
    "genreradio", "decade" -> NavibeatMixArtwork(NaviampTransportIcons.Radio, listOf(Color(0xFF1BC779), Color(0xFF167BC2)))
    else -> NavibeatMixArtwork(NaviampIcons.Playlist, listOf(Color(0xFF607D8B), Color(0xFF37474F)))
}

private fun stationArtwork(id: String): NavibeatMixArtwork = when {
    id == "library" -> NavibeatMixArtwork(NaviampIcons.Library, listOf(Color(0xFF526DFF), Color(0xFF6E3BB8)))
    id == "random-album" -> NavibeatMixArtwork(NaviampIcons.Album, listOf(Color(0xFFFF9A3D), Color(0xFFCF3F72)))
    id.startsWith("decade:") -> NavibeatMixArtwork(NaviampIcons.Clock, listOf(Color(0xFFFFB13B), Color(0xFF8B46C7)))
    else -> NavibeatMixArtwork(NaviampTransportIcons.Radio, listOf(Color(0xFF20BFA9), Color(0xFF2374C6)))
}

internal fun dispatchHomeCollectionItem(
    item: SharedHomeCollectionItemUi,
    actions: NaviampHomeActions,
    mediaActions: NaviampMediaActions,
) {
    when (item.action) {
        SharedHomeCollectionItemAction.PlayAlbum -> mediaActions.onMediaItemAction(item.mediaItem.playAlbumRequest())
        SharedHomeCollectionItemAction.OpenAlbum ->
            mediaActions.onMediaItemAction(item.mediaItem.albumActionRequest(NaviampArtistAlbumCommand.Select))
        SharedHomeCollectionItemAction.OpenArtist ->
            mediaActions.onMediaItemAction(
                NaviampMediaItemActionRequest(
                    item.mediaItem,
                    NaviampMediaItemCommand.Artist(NaviampArtistMediaCommand.Select),
                ),
            )
        SharedHomeCollectionItemAction.PlayPlaylist ->
            mediaActions.onMediaItemAction(
                item.mediaItem.playlistActionRequest(
                    NaviampPlaylistMediaCommand.Detail(NaviampPlaylistDetailCommand.Play(shuffle = false)),
                ),
            )
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

private fun homeCollectionMenuItems(
    item: SharedHomeCollectionItemUi,
    actions: NaviampHomeActions,
    mediaActions: NaviampMediaActions,
    includeFavorite: Boolean = false,
): List<NaviampRowMenuItem> = buildList {
    if (item.artwork == SharedHomeCollectionArtwork.NavibeatGenerated) {
        add(NaviampRowMenuItem("Shuffle mix", NaviampTransportIcons.Shuffle, {
            mediaActions.onMediaItemAction(
                item.mediaItem.playlistActionRequest(
                    NaviampPlaylistMediaCommand.Detail(NaviampPlaylistDetailCommand.Play(shuffle = true)),
                ),
            )
        }))
        add(NaviampRowMenuItem("View", NaviampIcons.Playlist, {
            mediaActions.onMediaItemAction(
                item.mediaItem.playlistActionRequest(NaviampPlaylistMediaCommand.Select),
            )
        }))
        return@buildList
    }
    when (item.mediaKind) {
        SharedMediaItemKind.Album -> {
            add(NaviampRowMenuItem("Start Radio", NaviampTransportIcons.Radio, {
                mediaActions.onMediaItemAction(
                    item.mediaItem.albumActionRequest(NaviampArtistAlbumCommand.StartRadio),
                )
            }))
            if (includeFavorite && item.mediaItem.canFavorite) {
                add(NaviampRowMenuItem(
                    if (item.mediaItem.favoriteActive) "Remove favorite" else "Favorite",
                    if (item.mediaItem.favoriteActive) NaviampTransportIcons.HeartFilled else NaviampTransportIcons.Heart,
                    {
                        mediaActions.onMediaItemAction(
                            item.mediaItem.albumActionRequest(NaviampArtistAlbumCommand.ToggleFavorite),
                        )
                    },
                ))
            }
        }
        SharedMediaItemKind.Track -> item.track?.let { track ->
            add(NaviampRowMenuItem("Start Radio", NaviampTransportIcons.Radio, {
                dispatchHomeTrackAction(item, track, SharedTrackRowAction.StartRadio, actions)
            }))
            if (includeFavorite && track.canToggleFavorite) {
                add(NaviampRowMenuItem(
                    if (track.favoriteActive) "Unfavorite" else "Favorite",
                    if (track.favoriteActive) NaviampTransportIcons.HeartFilled else NaviampTransportIcons.Heart,
                    { dispatchHomeTrackAction(item, track, SharedTrackRowAction.ToggleFavorite, actions) },
                ))
            }
        }
        else -> Unit
    }
}

private fun dispatchHomeTrackAction(
    item: SharedHomeCollectionItemUi,
    track: SharedTrackRowUi,
    action: SharedTrackRowAction,
    actions: NaviampHomeActions,
) {
    when (item.action) {
        SharedHomeCollectionItemAction.SelectRecentTrack ->
            actions.onRecentlyPlayedTrackAction(SharedTrackRowActionRequest(track, action))
        SharedHomeCollectionItemAction.SelectSonicTrack ->
            actions.onSonicDiscoveryTrackAction(
                SharedHomeDiscoveryTrackActionRequest(
                    rowId = item.discoveryRowId.orEmpty(),
                    track = track,
                    action = action,
                ),
            )
        else -> Unit
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
                            menuItems = homeCollectionMenuItems(item, actions, mediaActions, includeFavorite = true),
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
    FavoriteArtistStatus(page.section, colors)
    val sectionTitle = page.section.localizedTitle()
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth < HomeCollectionSingleRowHeaderMinWidth) {
            Column(
                verticalArrangement = Arrangement.spacedBy(1.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                HomeCollectionTitleRow(sectionTitle, colors, actions.onCollectionBack)
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
                    title = sectionTitle,
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
    page.section.favoriteArtistSort?.let { sort ->
        FavoriteArtistSortMenu(sort, colors, actions.onFavoriteArtistSortChanged)
    }
    if (HomeSectionPageLayout.List in page.section.supportedPageLayouts) {
        HomeCollectionLayoutButton(
            label = HomeSectionPageLayout.List.label,
            selected = page.layout == HomeSectionPageLayout.List,
            colors = colors,
            onClick = { actions.onCollectionPageLayoutChanged(page.section.id, HomeSectionPageLayout.List) },
        )
    }
    if (HomeSectionPageLayout.Grid in page.section.supportedPageLayouts) {
        HomeCollectionLayoutButton(
            label = HomeSectionPageLayout.Grid.label,
            selected = page.layout == HomeSectionPageLayout.Grid,
            colors = colors,
            onClick = { actions.onCollectionPageLayoutChanged(page.section.id, HomeSectionPageLayout.Grid) },
        )
    }
}

@Composable
internal fun FavoriteArtistSortMenu(
    sort: app.naviamp.domain.settings.FavoriteArtistSort,
    colors: NaviampColors,
    onSortChanged: (app.naviamp.domain.settings.FavoriteArtistSort) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = colors.secondaryText),
        ) {
            Text(favoriteArtistSortLabel(sort), fontSize = 11.sp)
            Icon(NaviampIcons.ChevronDown, contentDescription = stringResource(Res.string.favorite_artists_sort_title),
                modifier = Modifier.padding(start = 4.dp).size(14.dp))
        }
        NaviampDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            app.naviamp.domain.settings.FavoriteArtistSort.entries.forEach { option ->
                NaviampDropdownMenuItem(label = favoriteArtistSortLabel(option), selected = sort == option) {
                    expanded = false
                    onSortChanged(option)
                }
            }
        }
    }
}

@Composable
internal fun favoriteArtistSortLabel(sort: app.naviamp.domain.settings.FavoriteArtistSort): String =
    stringResource(when (sort) {
        app.naviamp.domain.settings.FavoriteArtistSort.Name -> Res.string.favorite_artists_sort_name
        app.naviamp.domain.settings.FavoriteArtistSort.DateFavorited -> Res.string.favorite_artists_sort_favorited
        app.naviamp.domain.settings.FavoriteArtistSort.LastRadioPlayed -> Res.string.favorite_artists_sort_played
    })

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
                background = true,
                horizontalPadding = 8.dp,
                verticalPadding = 7.dp,
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
                canStartRadio = true,
                canAddToQueue = true,
                canDownload = false,
                canAddToPlaylist = false,
                background = true,
                horizontalPadding = 8.dp,
                verticalPadding = 7.dp,
                swipeContext = TrackSwipeContext.Related,
            )
        }
        SharedHomeCollectionItemAction.SelectMixBuilder -> item.mixBuilder?.let { builder ->
            MixBuilderRow(builder, colors) { actions.onMixBuilderSelected(builder) }
        }
        SharedHomeCollectionItemAction.SelectStation -> HomeCollectionListRow(
            item = item,
            colors = colors,
            onClick = { dispatchHomeCollectionItem(item, actions, mediaActions) },
        )
        else -> SharedMediaRow(
            item = item.mediaItem,
            mediaKind = item.mediaKind,
            colors = colors,
            onClick = { dispatchHomeCollectionItem(item, actions, mediaActions) },
            menuItems = homeCollectionMenuItems(item, actions, mediaActions),
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
        Column(modifier = Modifier.fillMaxSize()) {
            HomeCollectionPageHeader(page, colors, actions)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(collectionScrollState),
            ) {
                SharedHomeCollectionPage(
                    page = page,
                    colors = colors,
                    actions = actions,
                    mediaActions = mediaActions,
                )
            }
        }
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
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
        PullToRefreshBox(
            isRefreshing = home.refreshing,
            onRefresh = actions.onRefresh,
            modifier = Modifier.weight(1f).fillMaxWidth(),
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
}

private val HomeCollectionCarouselCardWidth = 128.dp
private val HomeCollectionCarouselSpacing = 10.dp
private val HomeCollectionCarouselItemStride = HomeCollectionCarouselCardWidth + HomeCollectionCarouselSpacing
private val NavibeatMixIconVerticalOffset = (-5).dp
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
                background = true,
                horizontalPadding = 8.dp,
                verticalPadding = 7.dp,
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
                        canStartRadio = true,
                        canAddToQueue = true,
                        canDownload = false,
                        canAddToPlaylist = false,
                        background = true,
                        horizontalPadding = 8.dp,
                        verticalPadding = 7.dp,
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
            mediaKind = kind,
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
            placeholder = stringResource(Res.string.search_music_label),
            colors = colors,
            onClear = {
                actions.onClear()
                searchFocusRequester.requestFocus()
            },
            showClear = query.isNotBlank() || !results.isEmpty || screen.status != null || screen.searching,
            modifier = Modifier.padding(horizontal = 8.dp).focusRequester(searchFocusRequester),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
        ) {
            screen.status?.let { status ->
                Text(status, color = colors.secondaryText, fontSize = 12.sp)
            }
            if (screen.searching) {
                Text(stringResource(Res.string.search_searching), color = colors.secondaryText, fontSize = 12.sp)
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
    viewportState: NaviampLibraryViewportState,
) {
    NaviampLibrarySourcePicker(colors, screen.sourcePicker, actions)
    val catalog = screen.selectedCatalog
    val items = catalog.items
    val tracks = catalog.tracks
    val query = catalog.query
    val syncStatus = catalog.syncStatus
    val activeListState = viewportState.listState(screen.selectedView)
    val libraryScope = rememberCoroutineScope()
    val searchFocusRequester = remember { FocusRequester() }
    val selectorFocusRequesters = remember {
        NaviampLibraryView.entries.associateWith { FocusRequester() }
    }
    val restoredTarget = viewportState.focusedTarget(screen.selectedView)
    val restoredItemFocusRequester = remember(screen.selectedView, restoredTarget) { FocusRequester() }
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
    val filteredTracks = remember(tracks, query) {
        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.isBlank()) tracks else tracks.filter { track ->
            track.title.lowercase().contains(normalizedQuery) || track.subtitle.lowercase().contains(normalizedQuery)
        }
    }
    val focusIds = if (screen.selectedView == NaviampLibraryView.Songs) filteredTracks.map { it.id } else filteredItems.map { it.id }
    val restoredIndex = focusIds.indexOfFirst { libraryItemFocusTarget(it) == restoredTarget }
    val targetExists = restoredIndex >= 0 || restoredTarget == LibrarySearchFocusTarget
    androidx.compose.runtime.LaunchedEffect(screen.selectedView, targetExists) {
        if (restoredTarget == null && (activeListState.firstVisibleItemIndex > 0 || activeListState.firstVisibleItemScrollOffset > 0)) return@LaunchedEffect
        if (restoredIndex >= 0) {
            if (activeListState.layoutInfo.visibleItemsInfo.none { it.key == focusIds[restoredIndex] }) {
                activeListState.scrollToItem(restoredIndex)
            }
        } else if (restoredTarget != LibrarySearchFocusTarget) {
            activeListState.scrollToItem(0)
        }
        withFrameNanos { }
        val requester = when {
            restoredIndex >= 0 -> restoredItemFocusRequester
            restoredTarget == LibrarySearchFocusTarget -> searchFocusRequester
            else -> selectorFocusRequesters.getValue(screen.selectedView)
        }
        requester.requestFocus()
    }
    androidx.compose.runtime.LaunchedEffect(screen.jumpRequest) {
        val jump = screen.jumpRequest?.takeIf { it.view == screen.selectedView } ?: return@LaunchedEffect
        if (!viewportState.consumeJump(jump.generation)) return@LaunchedEffect
        val titles = if (screen.selectedView == NaviampLibraryView.Songs) {
            filteredTracks.map { it.title }
        } else {
            filteredItems.map { it.title }
        }
        val index = app.naviamp.domain.library.libraryLetterJumpIndex(titles, jump.letter)
        if (index >= 0) {
            activeListState.scrollToItem(index)
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(Modifier.weight(1f)) { NaviampPageTitle(stringResource(Res.string.library_title), colors) }
            IconButton(onClick = {
                libraryScope.launch {
                    activeListState.scrollToItem(0)
                    withFrameNanos { }
                    searchFocusRequester.requestFocus()
                }
            }) {
                Icon(NaviampIcons.Search, contentDescription = stringResource(Res.string.library_return_to_search),
                    tint = colors.primaryText)
            }
            NaviampRowOverflowMenu(
                colors = colors,
                items = buildList {
                    add(
                        NaviampRowMenuItem(
                            label = stringResource(Res.string.library_sources_choose),
                            icon = NaviampIcons.Library,
                            onClick = actions.onOpenSourcePicker,
                        ),
                    )
                    add(
                        NaviampRowMenuItem(
                            label = stringResource(Res.string.library_refresh),
                            icon = NaviampIcons.Refresh,
                            onClick = actions.onRefresh,
                            enabled = !syncStatus.isSyncing,
                        ),
                    )
                    if (screen.selectedView == NaviampLibraryView.Albums) {
                        add(
                            NaviampRowMenuItem(
                                label = stringResource(Res.string.library_sort_title),
                                icon = NaviampIcons.Alphabetical,
                                onClick = {
                                    actions.onAlbumSortOrderChanged(
                                        app.naviamp.domain.settings.LibraryAlbumSortOrder.Title,
                                    )
                                },
                                enabled = catalog.albumSortOrder !=
                                    app.naviamp.domain.settings.LibraryAlbumSortOrder.Title,
                            ),
                        )
                        add(
                            NaviampRowMenuItem(
                                label = stringResource(Res.string.library_sort_recently_added),
                                icon = NaviampIcons.Clock,
                                onClick = {
                                    actions.onAlbumSortOrderChanged(
                                        app.naviamp.domain.settings.LibraryAlbumSortOrder.RecentlyAdded,
                                    )
                                },
                                enabled = catalog.albumSortOrder !=
                                    app.naviamp.domain.settings.LibraryAlbumSortOrder.RecentlyAdded,
                            ),
                        )
                    }
                },
            )
        }
        NaviampLibraryLoadingStatus(colors, screen.selectedView, catalog)
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            ) {
                NaviampLibraryView.entries.forEachIndexed { index, view ->
                    val label = when (view) {
                        NaviampLibraryView.Artists -> stringResource(Res.string.library_view_artists)
                        NaviampLibraryView.Albums -> stringResource(Res.string.library_view_albums)
                        NaviampLibraryView.Songs -> stringResource(Res.string.library_view_songs)
                    }
                    val selectionState = if (screen.selectedView == view) {
                        stringResource(Res.string.library_view_state_selected)
                    } else {
                        stringResource(Res.string.library_view_state_not_selected)
                    }
                    TextButton(
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = if (screen.selectedView == view) colors.accent else colors.controlSurface,
                            contentColor = if (screen.selectedView == view) colors.onAccent else colors.primaryText,
                        ),
                        onClick = { actions.onViewChanged(view) },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(selectorFocusRequesters.getValue(view))
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) {
                                    false
                                } else {
                                    when (event.key) {
                                        Key.DirectionLeft -> {
                                            selectorFocusRequesters.getValue(
                                                NaviampLibraryView.entries[(index - 1).coerceAtLeast(0)],
                                            ).requestFocus()
                                            true
                                        }
                                        Key.DirectionRight -> {
                                            selectorFocusRequesters.getValue(
                                                NaviampLibraryView.entries[
                                                    (index + 1).coerceAtMost(NaviampLibraryView.entries.lastIndex)
                                                ],
                                            ).requestFocus()
                                            true
                                        }
                                        Key.DirectionDown -> {
                                            searchFocusRequester.requestFocus()
                                            true
                                        }
                                        else -> false
                                    }
                                }
                            }
                            .focusProperties {
                                left = selectorFocusRequesters.getValue(
                                    NaviampLibraryView.entries[(index - 1).coerceAtLeast(0)],
                                )
                                right = selectorFocusRequesters.getValue(
                                    NaviampLibraryView.entries[(index + 1).coerceAtMost(NaviampLibraryView.entries.lastIndex)],
                                )
                                down = searchFocusRequester
                            }
                            .semantics {
                                selected = screen.selectedView == view
                                stateDescription = selectionState
                            },
                    ) { Text(label, maxLines = 1) }
                }
            }
            val searchPlaceholder = when (screen.selectedView) {
                NaviampLibraryView.Artists -> stringResource(Res.string.library_search_artists)
                NaviampLibraryView.Albums -> stringResource(Res.string.library_search_albums)
                NaviampLibraryView.Songs -> stringResource(Res.string.library_search_songs)
            }
            NaviampCompactSearchField(
                value = query,
                onValueChange = actions.onQueryChanged,
                placeholder = searchPlaceholder,
                colors = colors,
                onClear = {
                    actions.onQueryChanged("")
                    searchFocusRequester.requestFocus()
                },
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .focusRequester(searchFocusRequester)
                    .focusProperties {
                        up = selectorFocusRequesters.getValue(screen.selectedView)
                    }
                    .onFocusChanged { focus ->
                        if (focus.isFocused) {
                            viewportState.recordFocusedTarget(screen.selectedView, LibrarySearchFocusTarget)
                        }
                    },
            )
        }
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            LazyColumn(
                state = activeListState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
            if (filteredItems.isEmpty() && filteredTracks.isEmpty() &&
                !syncStatus.isSyncing && catalog.pendingJump == null) {
                item {
                val emptyMessage = when (screen.selectedView) {
                    NaviampLibraryView.Artists -> if (query.isBlank()) {
                        stringResource(Res.string.library_no_artists)
                    } else {
                        stringResource(Res.string.library_no_artist_matches)
                    }
                    NaviampLibraryView.Albums -> if (query.isBlank()) {
                        stringResource(Res.string.library_no_albums)
                    } else {
                        stringResource(Res.string.library_no_album_matches)
                    }
                    NaviampLibraryView.Songs -> if (query.isBlank()) {
                        stringResource(Res.string.library_no_songs)
                    } else {
                        stringResource(Res.string.library_no_song_matches)
                    }
                }
                Text(
                    emptyMessage,
                    color = colors.secondaryText,
                    fontSize = 13.sp,
                )
                }
            }
            if (screen.selectedView == NaviampLibraryView.Songs) {
                items(items = filteredTracks, key = { track -> track.id }) { track ->
                    val focusTarget = libraryItemFocusTarget(track.id)
                    TrackRow(
                        track = track,
                        colors = colors,
                        onTrackAction = actions.onTrackAction,
                        canSelect = true,
                        canStartRadio = true,
                        canAddToQueue = true,
                        canDownload = true,
                        canAddToPlaylist = true,
                        background = true,
                        horizontalPadding = 6.dp,
                        modifier = Modifier
                            .then(
                                if (restoredTarget == focusTarget) {
                                    Modifier.focusRequester(restoredItemFocusRequester)
                                } else {
                                    Modifier
                                },
                            )
                            .onFocusChanged { focus ->
                                if (focus.isFocused) {
                                    viewportState.recordFocusedTarget(screen.selectedView, focusTarget)
                                }
                            },
                    )
                }
            } else {
                items(items = filteredItems, key = { item -> item.id }) { item ->
                    val focusTarget = libraryItemFocusTarget(item.id)
                    val kind = if (screen.selectedView == NaviampLibraryView.Artists) {
                        SharedMediaItemKind.Artist
                    } else {
                        SharedMediaItemKind.Album
                    }
                    val specs = if (kind == SharedMediaItemKind.Artist) {
                        artistRowActions(
                            canStartRadio = NaviampSharedMediaCapabilities.artist.canStartRadio,
                            canAddToQueue = NaviampSharedMediaCapabilities.artist.canAddToQueue,
                            canAddToPlaylist = NaviampSharedMediaCapabilities.artist.canAddToPlaylist,
                            canFavorite = NaviampSharedMediaCapabilities.artist.canToggleFavorite && item.canFavorite,
                            favoriteActive = item.favoriteActive,
                        )
                    } else {
                        albumRowActions(
                            canStartRadio = NaviampSharedMediaCapabilities.album.canStartRadio,
                            canDownload = NaviampSharedMediaCapabilities.album.canDownload,
                            canAddToQueue = NaviampSharedMediaCapabilities.album.canAddToQueue,
                            canAddToPlaylist = NaviampSharedMediaCapabilities.album.canAddToPlaylist,
                            canFavorite = NaviampSharedMediaCapabilities.album.canToggleFavorite && item.canFavorite,
                            favoriteActive = item.favoriteActive,
                        )
                    }
                    val menuItems = specs.mapNotNull { spec ->
                        val command = if (kind == SharedMediaItemKind.Artist) {
                            spec.action.artistMediaCommandOrNull()?.let(NaviampMediaItemCommand::Artist)
                        } else {
                            spec.action.albumMediaCommandOrNull()?.let(NaviampMediaItemCommand::Album)
                        }
                        command?.let {
                        NaviampRowMenuItem(
                            label = spec.label,
                            icon = spec.icon,
                            onClick = {
                                mediaActions.onMediaItemAction(NaviampMediaItemActionRequest(item, it))
                            },
                            enabled = spec.enabled,
                        )
                    }
                    }
                    SharedMediaRow(
                        item = item,
                        mediaKind = kind,
                        colors = colors,
                        menuItems = menuItems,
                        onClick = {
                            val command = if (kind == SharedMediaItemKind.Artist) {
                                NaviampMediaItemCommand.Artist(NaviampArtistMediaCommand.Select)
                            } else {
                                NaviampMediaItemCommand.Album(NaviampArtistAlbumCommand.Select)
                            }
                            mediaActions.onMediaItemAction(NaviampMediaItemActionRequest(item, command))
                        },
                        modifier = Modifier
                            .then(
                                if (restoredTarget == focusTarget) {
                                    Modifier.focusRequester(restoredItemFocusRequester)
                                } else {
                                    Modifier
                                },
                            )
                            .onFocusChanged { focus ->
                                if (focus.isFocused) {
                                    viewportState.recordFocusedTarget(screen.selectedView, focusTarget)
                                }
                            },
                    )
                }
            }
            if (filteredItems.isNotEmpty() || filteredTracks.isNotEmpty()) {
                item(key = "library-load-more-${screen.selectedView.name}") {
                    androidx.compose.runtime.LaunchedEffect(screen.selectedView, items.size, tracks.size, query) {
                        if (!syncStatus.isSyncing) actions.onLoadMore()
                    }
                }
            }
        }
            if (query.isBlank() && (
                screen.selectedView != NaviampLibraryView.Albums ||
                    catalog.albumSortOrder == app.naviamp.domain.settings.LibraryAlbumSortOrder.Title
            )) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(36.dp).verticalScroll(rememberScrollState()),
                ) {
                    (listOf('#') + ('A'..'Z')).forEach { letter ->
                        Text(
                            text = letter.toString(),
                            color = colors.secondaryText,
                            fontSize = 10.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                                .clickable { actions.onJumpToLetter(letter) }
                                .padding(vertical = 2.dp),
                        )
                    }
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
    Column(modifier = Modifier.fillMaxSize()) {
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
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
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
