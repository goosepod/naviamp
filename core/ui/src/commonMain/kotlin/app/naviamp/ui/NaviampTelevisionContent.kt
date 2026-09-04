package app.naviamp.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onClick as semanticsOnClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.waveform.playbackFraction
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun TelevisionHome(
    home: NaviampHomeScreenUi,
    colors: NaviampColors,
    actions: NaviampHomeActions,
    mediaActions: NaviampMediaActions,
    entryFocusGeneration: Int? = null,
    onEntryFocusHandled: (Int) -> Unit = {},
) {
    val sections = televisionHomeSections(home.content.collectionSections)
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val contextInsetPx = with(density) { TelevisionHomeFocusedSectionTopInset.roundToPx() }
    val sectionItemKeys = sections.map { section ->
        section.items.map { item -> "${item.mediaKind}:${item.mediaItem.id}" } + "view-all:${section.id}"
    }
    val itemFocusRequesters = remember(sectionItemKeys) {
        sectionItemKeys.map { itemKeys -> List(itemKeys.size) { FocusRequester() } }
    }
    var rememberedItemIndices by remember(sectionItemKeys) { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var focusedSectionIndex by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(entryFocusGeneration, sectionItemKeys) {
        entryFocusGeneration?.let { generation ->
            if (itemFocusRequesters.firstOrNull()?.isNotEmpty() == true) {
                listState.scrollToItem(0)
                withFrameNanos { }
                itemFocusRequesters.first().first().requestFocus()
            }
            onEntryFocusHandled(generation)
        }
    }
    LaunchedEffect(focusedSectionIndex) {
        focusedSectionIndex?.let { sectionIndex ->
            listState.scrollToItem(
                sectionIndex,
                televisionHomeSectionScrollOffset(sectionIndex, contextInsetPx),
            )
        }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides TelevisionHomeBringIntoViewSpec) {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(TelevisionHomeRailSpacing),
            contentPadding = PaddingValues(bottom = TelevisionHomeBottomFocusClearance),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (sections.isEmpty()) {
                item(key = "television-home-empty") {
                    Text(
                        if (home.refreshing) "Loading your music…" else "Your Home sections are empty.",
                        color = colors.secondaryText,
                        fontSize = 20.sp,
                    )
                }
            }
            itemsIndexed(sections, key = { _, section -> section.id }) { sectionIndex, section ->
                val nextSectionIndex = televisionHomeVerticalSectionTarget(
                    currentSectionIndex = sectionIndex,
                    sectionCount = sections.size,
                    key = Key.DirectionDown,
                )
                val previousSectionIndex = televisionHomeVerticalSectionTarget(
                    currentSectionIndex = sectionIndex,
                    sectionCount = sections.size,
                    key = Key.DirectionUp,
                )
                TelevisionHomeCarousel(
                    section = section,
                    colors = colors,
                    itemFocusRequesters = itemFocusRequesters[sectionIndex],
                    previousSectionFocusRequester = previousSectionIndex?.let { targetSectionIndex ->
                        itemFocusRequesters[targetSectionIndex].getOrNull(
                            televisionHomeRememberedItemIndex(
                                rememberedIndex = rememberedItemIndices[sections[targetSectionIndex].id],
                                itemCount = itemFocusRequesters[targetSectionIndex].size,
                            ),
                        )
                    },
                    nextSectionFocusRequester = nextSectionIndex?.let { targetSectionIndex ->
                        itemFocusRequesters[targetSectionIndex].getOrNull(
                            televisionHomeRememberedItemIndex(
                                rememberedIndex = rememberedItemIndices[sections[targetSectionIndex].id],
                                itemCount = itemFocusRequesters[targetSectionIndex].size,
                            ),
                        )
                    },
                    onSectionFocused = { focusedSectionIndex = sectionIndex },
                    onItemFocused = { itemIndex ->
                        rememberedItemIndices = rememberedItemIndices + (section.id to itemIndex)
                    },
                    onSelected = { item -> dispatchHomeCollectionItem(item, actions, mediaActions) },
                    onViewAll = { actions.onCollectionSelected(section.id) },
                )
            }
        }
    }
}

private object TelevisionHomeBringIntoViewSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

@Composable
private fun TelevisionHomeCarousel(
    section: SharedHomeCollectionSectionUi,
    colors: NaviampColors,
    itemFocusRequesters: List<FocusRequester>,
    previousSectionFocusRequester: FocusRequester?,
    nextSectionFocusRequester: FocusRequester?,
    onSectionFocused: () -> Unit,
    onItemFocused: (Int) -> Unit,
    onSelected: (SharedHomeCollectionItemUi) -> Unit,
    onViewAll: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val itemStride = with(density) { TelevisionHomeCardStride.roundToPx() }
    val itemWidth = with(density) { TelevisionHomeCardWidth.roundToPx() }
    var focusedIndex by remember(section.id) { mutableStateOf<Int?>(null) }
    LaunchedEffect(focusedIndex) {
        val index = focusedIndex ?: return@LaunchedEffect
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
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            section.title,
            color = colors.primaryText,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val anchoredRailWidth =
                TelevisionHomeCardWidth * TelevisionHomeVisibleAnchorItems +
                    TelevisionHomeCardSpacing * (TelevisionHomeVisibleAnchorItems - 1)
            val trailingSpace = (maxWidth - anchoredRailWidth).coerceAtLeast(0.dp)
            Row(
                horizontalArrangement = Arrangement.spacedBy(TelevisionHomeCardSpacing),
                modifier = Modifier
                    .onPreviewKeyEvent { event ->
                        val target = when (event.key) {
                            Key.DirectionUp -> previousSectionFocusRequester
                            Key.DirectionDown -> nextSectionFocusRequester
                            else -> null
                        }
                        if (target == null) {
                            false
                        } else {
                            if (event.type == KeyEventType.KeyDown) {
                                target.requestFocus()
                            }
                            true
                        }
                    }
                    .horizontalScroll(scrollState)
                    .padding(
                        start = TelevisionFocusedItemOverflow,
                        top = TelevisionFocusedItemOverflow,
                        end = trailingSpace + TelevisionFocusedItemOverflow,
                        bottom = TelevisionFocusedItemOverflow,
                    ),
            ) {
                section.items.forEachIndexed { index, item ->
                    TelevisionHomeCard(
                        item = item,
                        colors = colors,
                        modifier = Modifier
                            .focusRequester(itemFocusRequesters[index]),
                        onFocused = {
                            focusedIndex = index
                            onItemFocused(index)
                            onSectionFocused()
                        },
                        onClick = { onSelected(item) },
                    )
                }
                TelevisionHomeViewAllCard(
                    section = section,
                    colors = colors,
                    onFocused = {
                        focusedIndex = section.items.size
                        onItemFocused(section.items.size)
                        onSectionFocused()
                    },
                    onClick = onViewAll,
                    modifier = Modifier
                        .focusRequester(itemFocusRequesters[section.items.size])
                        .onPreviewKeyEvent { event -> televisionHomeConsumesEndOfRailKey(event.key) },
                )
            }
        }
    }
}

@Composable
private fun TelevisionHomeCard(
    item: SharedHomeCollectionItemUi,
    colors: NaviampColors,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TelevisionFocusableCard(
        colors = colors,
        width = TelevisionHomeCardWidth,
        onFocused = onFocused,
        onClick = onClick,
        modifier = modifier,
    ) { focused ->
        val artworkShape = if (item.mediaKind == SharedMediaItemKind.Artist) {
            RoundedCornerShape(TelevisionHomeCardWidth / 2)
        } else {
            RoundedCornerShape(7.dp)
        }
        HomeCollectionArtwork(
            item,
            colors,
            TelevisionHomeCardWidth,
            Modifier.televisionMediaArtworkFocusEffect(focused, colors, artworkShape),
        )
        TelevisionCardLabels(item.title, item.subtitle, colors)
    }
}

@Composable
private fun TelevisionHomeViewAllCard(
    section: SharedHomeCollectionSectionUi,
    colors: NaviampColors,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TelevisionFocusableCard(
        colors = colors,
        width = TelevisionHomeCardWidth,
        onFocused = onFocused,
        onClick = onClick,
        modifier = modifier.testTag("$TelevisionHomeViewAllTestTagPrefix${section.id}"),
    ) { focused ->
        val shape = RoundedCornerShape(12.dp)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .requiredSize(TelevisionHomeCardWidth)
                .televisionMediaArtworkFocusEffect(focused, colors, shape)
                .clip(shape)
                .background(colors.controlSurface.copy(alpha = 0.92f)),
        ) {
            Icon(
                imageVector = NaviampIcons.ChevronRight,
                contentDescription = null,
                tint = colors.primaryText,
                modifier = Modifier.size(42.dp),
            )
        }
        TelevisionCardLabels("View all", section.title, colors)
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun TelevisionHomeCollection(
    page: SharedHomeCollectionPageUi,
    colors: NaviampColors,
    actions: NaviampHomeActions,
    mediaActions: NaviampMediaActions,
    topNavigationFocusRequester: FocusRequester,
) {
    val items = page.section.items
    val itemKeys = items.map { item -> "${item.mediaKind}:${item.mediaItem.id}" }
    val focusRequesters = remember(itemKeys) { List(items.size) { FocusRequester() } }
    val backFocusRequester = remember(page.section.id) { FocusRequester() }
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val focusedRowInsetPx = with(density) { TelevisionGridFocusedRowTopInset.roundToPx() }
    var focusedRowStart by remember { mutableStateOf<Int?>(null) }

    suspend fun focusItem(index: Int) {
        gridState.scrollToItem(index)
        repeat(TelevisionFocusRequestAttempts) {
            withFrameNanos { }
            if (focusRequesters[index].requestFocus()) return
        }
    }

    LaunchedEffect(page.section.id, itemKeys) {
        if (items.isNotEmpty()) focusItem(0) else backFocusRequester.requestFocus()
    }
    LaunchedEffect(focusedRowStart) {
        focusedRowStart?.let { gridState.scrollToItem(it, -focusedRowInsetPx) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TelevisionTextButton(
                label = "Back",
                colors = colors,
                calmFocus = true,
                onClick = actions.onCollectionBack,
                modifier = Modifier
                    .focusRequester(backFocusRequester)
                    .testTag(TelevisionHomeCollectionBackTestTag)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                            topNavigationFocusRequester.requestFocus()
                            true
                        } else {
                            false
                        }
                    },
            )
            Text(
                page.section.title,
                color = colors.primaryText,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 14.dp),
            )
            Spacer(Modifier.weight(1f))
            Text("${items.size} items", color = colors.secondaryText, fontSize = 15.sp)
        }
        if (items.isEmpty()) {
            Text("This collection is empty.", color = colors.secondaryText, fontSize = 20.sp)
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val columnCount = televisionHomeCollectionColumnCount(maxWidth)
                CompositionLocalProvider(LocalBringIntoViewSpec provides TelevisionGridBringIntoViewSpec) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columnCount),
                        state = gridState,
                        horizontalArrangement = Arrangement.spacedBy(TelevisionGridSpacing),
                        verticalArrangement = Arrangement.spacedBy(22.dp),
                        contentPadding = PaddingValues(TelevisionFocusedItemOverflow),
                        modifier = Modifier.fillMaxSize().testTag(TelevisionHomeCollectionTestTag),
                    ) {
                        gridItemsIndexed(
                            items,
                            key = { _, item -> "${item.mediaKind}:${item.mediaItem.id}" },
                        ) { index, item ->
                            TelevisionHomeCard(
                                item = item,
                                colors = colors,
                                onFocused = { focusedRowStart = televisionGridRowStart(index, columnCount) },
                                onClick = { dispatchHomeCollectionItem(item, actions, mediaActions) },
                                modifier = Modifier
                                    .focusRequester(focusRequesters[index])
                                    .testTag("$TelevisionHomeCollectionItemTestTagPrefix$index")
                                    .onPreviewKeyEvent { event ->
                                        if (
                                            event.type == KeyEventType.KeyDown &&
                                            event.key == Key.DirectionUp &&
                                            index < columnCount
                                        ) {
                                            backFocusRequester.requestFocus()
                                            true
                                        } else {
                                            val nextIndex = televisionGridRightTarget(index, items.size)
                                            if (
                                                event.type == KeyEventType.KeyDown &&
                                                event.key == Key.DirectionRight &&
                                                nextIndex != null
                                            ) {
                                                if (!focusRequesters[nextIndex].requestFocus()) {
                                                    coroutineScope.launch { focusItem(nextIndex) }
                                                }
                                                true
                                            } else {
                                                false
                                            }
                                        }
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun televisionHomeCollectionColumnCount(availableWidth: Dp): Int =
    ((availableWidth + TelevisionGridSpacing) / (TelevisionHomeCardWidth + TelevisionGridSpacing))
        .toInt()
        .coerceAtLeast(1)

@Composable
internal fun TelevisionLibrary(
    screen: NaviampLibraryScreenUi,
    colors: NaviampColors,
    actions: NaviampLibraryActions,
    mediaActions: NaviampMediaActions,
    initialFocusedArtistIndex: Int = 0,
    restoreArtistFocus: Boolean = false,
    onArtistFocused: (Int) -> Unit = {},
    onOpenArtist: () -> Unit = {},
    onOpenPlaylists: () -> Unit,
    onOpenInternetRadio: () -> Unit,
    topNavigationFocusRequester: FocusRequester,
    entryFocusGeneration: Int? = null,
    onEntryFocusHandled: (Int) -> Unit = {},
) {
    val shortcuts = televisionLibraryShortcuts()
    val shortcutFocusRequesters = remember { shortcuts.associateWith { FocusRequester() } }
    val playlistsFocusRequester = remember { FocusRequester() }
    val radioFocusRequester = remember { FocusRequester() }
    val refreshFocusRequester = remember { FocusRequester() }
    var focusedArtistIndex by remember { mutableIntStateOf(initialFocusedArtistIndex) }
    var pendingShortcut by remember { mutableStateOf<Char?>(null) }
    var gridFocusRequest by remember { mutableStateOf<TelevisionGridFocusRequest?>(null) }
    var gridFocusGeneration by remember { mutableIntStateOf(0) }
    val focusArtist = { index: Int ->
        gridFocusRequest = TelevisionGridFocusRequest(
            index.coerceIn(screen.artists.indices),
            ++gridFocusGeneration,
        )
    }
    LaunchedEffect(entryFocusGeneration, screen.artists) {
        entryFocusGeneration?.let { generation ->
            if (screen.artists.isNotEmpty()) focusArtist(0)
            onEntryFocusHandled(generation)
        }
    }
    LaunchedEffect(screen.artists, pendingShortcut) {
        val shortcut = pendingShortcut ?: return@LaunchedEffect
        val target = televisionLibraryShortcutTarget(screen.artists.map { it.title }, shortcut) ?: return@LaunchedEffect
        gridFocusRequest = TelevisionGridFocusRequest(target, ++gridFocusGeneration)
        pendingShortcut = null
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(start = TelevisionLibraryContentStartInset),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                TelevisionTextButton(
                    "Internet Radio",
                    colors,
                    onClick = onOpenInternetRadio,
                    modifier = Modifier
                        .focusRequester(radioFocusRequester)
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.DirectionUp -> {
                                    topNavigationFocusRequester.requestFocus()
                                    true
                                }
                                Key.DirectionRight -> {
                                    playlistsFocusRequester.requestFocus()
                                    true
                                }
                                Key.DirectionDown -> if (screen.artists.isNotEmpty()) {
                                    focusArtist(0)
                                    true
                                } else false
                                else -> false
                            }
                        },
                )
                Box(modifier = Modifier.padding(start = 10.dp)) {
                    TelevisionTextButton(
                    "Playlists",
                    colors,
                    onClick = onOpenPlaylists,
                    modifier = Modifier
                        .focusRequester(playlistsFocusRequester)
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.DirectionUp -> {
                                    topNavigationFocusRequester.requestFocus()
                                    true
                                }
                                Key.DirectionRight -> {
                                    refreshFocusRequester.requestFocus()
                                    true
                                }
                                Key.DirectionLeft -> {
                                    radioFocusRequester.requestFocus()
                                    true
                                }
                                Key.DirectionDown -> if (screen.artists.isNotEmpty()) {
                                    focusArtist(0)
                                    true
                                } else {
                                    false
                                }
                                else -> false
                            }
                        },
                    )
                }
                Box(modifier = Modifier.padding(start = 10.dp)) {
                    TelevisionTextButton(
                        "Refresh",
                        colors,
                        onClick = actions.onRefresh,
                        modifier = Modifier
                            .focusRequester(refreshFocusRequester)
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (event.key) {
                                    Key.DirectionUp -> {
                                        topNavigationFocusRequester.requestFocus()
                                        true
                                    }
                                    Key.DirectionLeft -> {
                                        playlistsFocusRequester.requestFocus()
                                        true
                                    }
                                    Key.DirectionDown -> if (screen.artists.isNotEmpty()) {
                                        focusArtist(0)
                                        true
                                    } else {
                                        false
                                    }
                                    else -> false
                                }
                            },
                    )
                }
            }
            screen.syncStatus.message?.let {
                Text(it, color = colors.secondaryText, fontSize = 15.sp)
            }
            if (screen.artists.isEmpty()) {
                Text("No artists are available.", color = colors.secondaryText, fontSize = 20.sp)
            } else {
                TelevisionMediaGrid(
                    items = screen.artists.map { artist ->
                        TelevisionMediaGridItem(
                            key = "artist:${artist.id}",
                            title = artist.title,
                            subtitle = artist.subtitle,
                            coverArtUrl = artist.coverArtUrl,
                            artworkShape = TelevisionArtworkShape.Circle,
                            action = {
                                onOpenArtist()
                                mediaActions.onMediaItemAction(
                                    artist.artistActionRequest(NaviampArtistMediaCommand.Select),
                                )
                            },
                        )
                    },
                    colors = colors,
                    focusRequest = gridFocusRequest ?: if (
                        restoreArtistFocus && initialFocusedArtistIndex in screen.artists.indices
                    ) {
                        TelevisionGridFocusRequest(initialFocusedArtistIndex, -1)
                    } else {
                        null
                    },
                    onItemFocused = {
                        focusedArtistIndex = it
                        onArtistFocused(it)
                    },
                    onLeftFromFirstColumn = {
                        val section = televisionLibrarySection(screen.artists[focusedArtistIndex].title)
                        shortcutFocusRequesters[section]?.requestFocus()
                        Unit
                    },
                    onUpFromFirstRow = {
                        playlistsFocusRequester.requestFocus()
                        Unit
                    },
                )
            }
        }
        if (screen.artists.isNotEmpty()) {
            LazyColumn(
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .requiredWidth(TelevisionLibraryShortcutRailWidth)
                    .fillMaxHeight(),
            ) {
                itemsIndexed(shortcuts, key = { _, shortcut -> shortcut }) { _, shortcut ->
                    TelevisionLibraryShortcut(
                        shortcut = shortcut,
                        colors = colors,
                        focusRequester = shortcutFocusRequesters.getValue(shortcut),
                        onClick = {
                            pendingShortcut = shortcut
                            actions.onJumpToLetter(shortcut)
                        },
                        onRight = { focusArtist(0) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TelevisionLibraryShortcut(
    shortcut: Char,
    colors: NaviampColors,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    onRight: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(999.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .requiredSize(TelevisionLibraryShortcutSize)
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                    onRight()
                    true
                } else if (
                    event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.Spacebar)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .focusable()
            .televisionFocusEffect(focused, colors, shape)
            .clip(shape)
            .background(if (focused) colors.accent else colors.controlSurface.copy(alpha = 0.88f)),
    ) {
        Text(
            text = shortcut.toString(),
            color = colors.primaryText,
            fontSize = 8.sp,
            lineHeight = 8.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.offset(y = (-0.5).dp),
        )
    }
}

internal fun televisionLibraryShortcuts(): List<Char> = listOf('#') + ('A'..'Z')

internal fun televisionLibrarySection(title: String): Char =
    title.trim().firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() } ?: '#'

internal fun televisionLibraryShortcutTarget(titles: List<String>, shortcut: Char): Int? {
    val normalized = shortcut.uppercaseChar()
    return titles.indexOfFirst { title ->
        val section = televisionLibrarySection(title)
        if (normalized == '#') section == '#' else section != '#' && section >= normalized
    }.takeIf { it >= 0 }
}

@Composable
internal fun TelevisionInternetRadio(
    screen: NaviampInternetRadioScreenUi,
    colors: NaviampColors,
    actions: NaviampInternetRadioActions,
    topNavigationFocusRequester: FocusRequester,
) {
    val stations = screen.stations.sortedBy { it.item.title.lowercase() }
    val newFocusRequester = remember { FocusRequester() }
    val refreshFocusRequester = remember { FocusRequester() }
    val editActionFocusRequester = remember { FocusRequester() }
    val cancelDeleteFocusRequester = remember { FocusRequester() }
    val stationFocusRequesters = remember(stations.map { it.item.id }) {
        List(stations.size) { FocusRequester() }
    }
    var initialFocusAssigned by remember { mutableStateOf(false) }
    var actionStation by remember { mutableStateOf<NaviampInternetRadioStationUi?>(null) }
    var editingStation by remember { mutableStateOf<NaviampInternetRadioStationUi?>(null) }
    var deletingStation by remember { mutableStateOf<NaviampInternetRadioStationUi?>(null) }
    var creatingStation by remember { mutableStateOf(false) }

    LaunchedEffect(stations, initialFocusAssigned) {
        if (!initialFocusAssigned) {
            withFrameNanos { }
            (stationFocusRequesters.firstOrNull() ?: newFocusRequester).requestFocus()
            initialFocusAssigned = true
        }
    }
    LaunchedEffect(actionStation) {
        if (actionStation != null) {
            withFrameNanos { }
            editActionFocusRequester.requestFocus()
        }
    }
    LaunchedEffect(deletingStation) {
        if (deletingStation != null) {
            withFrameNanos { }
            cancelDeleteFocusRequester.requestFocus()
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize().padding(horizontal = 30.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Internet Radio", color = colors.primaryText, fontSize = 30.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
            TelevisionTextButton(
                "New station",
                colors,
                calmFocus = true,
                onClick = { creatingStation = true },
                modifier = Modifier
                    .focusRequester(newFocusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionUp -> {
                                topNavigationFocusRequester.requestFocus()
                                true
                            }
                            Key.DirectionRight -> {
                                refreshFocusRequester.requestFocus()
                                true
                            }
                            Key.DirectionDown -> stationFocusRequesters.firstOrNull()?.requestFocus() == true
                            else -> false
                        }
                    },
            )
            Box(Modifier.padding(start = 10.dp)) {
                TelevisionTextButton(
                    "Refresh",
                    colors,
                    enabled = !screen.refreshing,
                    calmFocus = true,
                    onClick = actions.onRefresh,
                    modifier = Modifier
                        .focusRequester(refreshFocusRequester)
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.DirectionUp -> {
                                    topNavigationFocusRequester.requestFocus()
                                    true
                                }
                                Key.DirectionLeft -> {
                                    newFocusRequester.requestFocus()
                                    true
                                }
                                Key.DirectionDown -> stationFocusRequesters.firstOrNull()?.requestFocus() == true
                                else -> false
                            }
                        },
                )
            }
        }
        screen.status?.let { Text(it, color = colors.secondaryText, fontSize = 15.sp) }
        if (stations.isEmpty()) {
            Text(
                if (screen.refreshing) "Loading internet radio…" else "No internet radio stations are saved.",
                color = colors.secondaryText,
                fontSize = 20.sp,
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                itemsIndexed(stations, key = { _, station -> station.item.id }) { index, station ->
                    TelevisionInternetRadioRow(
                        station = station,
                        colors = colors,
                        focusRequester = stationFocusRequesters[index],
                        onClick = {
                            actions.onStationAction(
                                StationRowActionRequest(station.item, StationRowAction.Select),
                            )
                        },
                        onOpenActions = { actionStation = station },
                        modifier = Modifier.onPreviewKeyEvent { event ->
                            if (index == 0 && event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                                newFocusRequester.requestFocus()
                                true
                            } else false
                        },
                    )
                }
            }
        }
    }

    actionStation?.let { station ->
        AlertDialog(
            onDismissRequest = { actionStation = null },
            title = { Text(station.item.title) },
            text = { Text(station.streamUrl) },
            confirmButton = {
                TextButton(
                    onClick = {
                        actionStation = null
                        editingStation = station
                    },
                    modifier = Modifier.focusRequester(editActionFocusRequester),
                ) { Text("Edit") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        actionStation = null
                        deletingStation = station
                    }) { Text("Delete") }
                    TextButton(onClick = { actionStation = null }) { Text("Cancel") }
                }
            },
        )
    }
    if (creatingStation) {
        InternetRadioStationDialog(
            initialStation = null,
            onDismiss = { creatingStation = false },
            onConfirm = { station ->
                creatingStation = false
                actions.onSaveStation(station)
            },
        )
    }
    editingStation?.let { station ->
        InternetRadioStationDialog(
            initialStation = station,
            onDismiss = { editingStation = null },
            onConfirm = { edit ->
                editingStation = null
                actions.onSaveStation(edit)
            },
        )
    }
    deletingStation?.let { station ->
        AlertDialog(
            onDismissRequest = { deletingStation = null },
            title = { Text("Delete station") },
            text = { Text("Delete ${station.item.title}? This removes it from the server.") },
            confirmButton = {
                TextButton(onClick = {
                    deletingStation = null
                    actions.onStationAction(StationRowActionRequest(station.item, StationRowAction.Delete))
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(
                    onClick = { deletingStation = null },
                    modifier = Modifier.focusRequester(cancelDeleteFocusRequester),
                ) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun TelevisionInternetRadioRow(
    station: NaviampInternetRadioStationUi,
    colors: NaviampColors,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    onOpenActions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                    onOpenActions()
                    true
                } else false
            }
            .onKeyEvent { event ->
                if (
                    event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.Spacebar)
                ) {
                    onClick()
                    true
                } else false
            }
            .focusable()
            .televisionFocusEffect(focused, colors, shape)
            .clip(shape)
            .background(if (focused) colors.accent.copy(alpha = 0.34f) else Color.Black.copy(alpha = 0.22f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        NaviampCoverArt(station.item.coverArtUrl, colors, 58.dp, 10.dp)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
            Text(
                station.item.title,
                color = colors.primaryText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(station.streamUrl, color = colors.secondaryText, fontSize = 13.sp, maxLines = 1)
        }
        Text("Right: actions", color = colors.mutedText, fontSize = 12.sp)
    }
}

private data class TelevisionGridFocusRequest(val index: Int, val generation: Int)

@Composable
internal fun TelevisionPlaylists(
    screen: NaviampPlaylistsScreenUi,
    colors: NaviampColors,
    actions: NaviampPlaylistsActions,
    mediaActions: NaviampMediaActions,
    topNavigationFocusRequester: FocusRequester,
    entryFocusGeneration: Int? = null,
    onEntryFocusHandled: (Int) -> Unit = {},
) {
    val playlists = screen.playlists.sortedForPlaylistScreen(screen.sortMode, screen.recentPlaylistIds)
    var gridFocusGeneration by remember { mutableIntStateOf(0) }
    var gridFocusRequest by remember { mutableStateOf<TelevisionGridFocusRequest?>(null) }
    val enterGrid: () -> Unit = {
        if (playlists.isNotEmpty()) {
            gridFocusRequest = TelevisionGridFocusRequest(0, ++gridFocusGeneration)
        }
    }
    LaunchedEffect(entryFocusGeneration, playlists) {
        entryFocusGeneration?.let { generation ->
            enterGrid()
            onEntryFocusHandled(generation)
        }
    }
    val headerModifier = Modifier.onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown && playlists.isNotEmpty()) {
            enterGrid()
            true
        } else {
            false
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxSize()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Playlists", color = colors.primaryText, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
            SharedPlaylistSortMode.entries.forEach { mode ->
                TelevisionTextButton(
                    label = mode.label,
                    colors = colors,
                    selected = screen.sortMode == mode,
                    onClick = { actions.onSortModeChanged(mode) },
                    modifier = headerModifier,
                )
            }
            TelevisionTextButton(
                label = if (screen.refreshing) "Refreshing…" else "Refresh",
                colors = colors,
                onClick = actions.onRefresh,
                modifier = headerModifier,
            )
        }
        screen.status?.let { Text(it, color = colors.secondaryText, fontSize = 15.sp) }
        if (playlists.isEmpty()) {
            Text("No playlists are available.", color = colors.secondaryText, fontSize = 20.sp)
        } else {
            TelevisionMediaGrid(
                items = playlists.map { playlist ->
                    TelevisionMediaGridItem(
                        key = "playlist:${playlist.id}",
                        title = playlist.title,
                        subtitle = playlist.subtitle,
                        coverArtUrl = playlist.coverArtUrl ?: playlist.coverArtUrls.firstOrNull(),
                        action = {
                            mediaActions.onMediaItemAction(
                                playlist.playlistActionRequest(NaviampPlaylistMediaCommand.Select),
                            )
                        },
                    )
                },
                colors = colors,
                focusFirstItem = true,
                focusRequest = gridFocusRequest,
                onUpFromFirstRow = {
                    topNavigationFocusRequester.requestFocus()
                    Unit
                },
            )
        }
    }
}

private data class TelevisionMediaGridItem(
    val key: String,
    val title: String,
    val subtitle: String,
    val coverArtUrl: String?,
    val artworkShape: TelevisionArtworkShape = TelevisionArtworkShape.RoundedSquare,
    val action: () -> Unit,
)

private enum class TelevisionArtworkShape {
    RoundedSquare,
    Circle,
}

@Composable
internal fun TelevisionSearch(
    screen: NaviampSearchScreenUi,
    colors: NaviampColors,
    actions: NaviampSearchActions,
    mediaActions: NaviampMediaActions,
    entryFocusGeneration: Int? = null,
    onEntryFocusHandled: (Int) -> Unit = {},
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFieldFocusRequester = remember { FocusRequester() }
    var searchFieldFocused by remember { mutableStateOf(false) }
    var keyboardActive by remember { mutableStateOf(false) }
    var resultsActive by remember { mutableStateOf(false) }
    var searchHeaderVisible by remember { mutableStateOf(true) }
    var returnFocusToSearch by remember { mutableStateOf(false) }
    LaunchedEffect(entryFocusGeneration) {
        entryFocusGeneration?.let { generation ->
            searchHeaderVisible = true
            withFrameNanos { }
            searchFieldFocusRequester.requestFocus()
            keyboardController?.show()
            keyboardActive = true
            onEntryFocusHandled(generation)
        }
    }
    val submitSearch = {
        actions.onSearch()
        keyboardController?.hide()
        keyboardActive = false
    }
    val backTarget = televisionSearchBackTarget(resultsActive, searchFieldFocused, keyboardActive)
    NaviampSystemBackHandler(enabled = backTarget != TelevisionSearchBackTarget.Navigation) {
        when (backTarget) {
            TelevisionSearchBackTarget.Query -> {
                resultsActive = false
                searchHeaderVisible = true
                returnFocusToSearch = true
            }
            TelevisionSearchBackTarget.HideKeyboard -> {
                keyboardController?.hide()
                keyboardActive = false
            }
            TelevisionSearchBackTarget.Navigation -> Unit
        }
    }
    LaunchedEffect(returnFocusToSearch) {
        if (returnFocusToSearch) {
            withFrameNanos { }
            searchFieldFocusRequester.requestFocus()
            keyboardController?.hide()
            keyboardActive = false
            returnFocusToSearch = false
        }
    }
    val results = screen.results
    val items = buildList {
        results.artists.forEach { artist ->
            add(
                TelevisionMediaGridItem(
                    key = "artist:${artist.id}",
                    title = artist.title,
                    subtitle = artist.subtitle,
                    coverArtUrl = artist.coverArtUrl,
                    artworkShape = TelevisionArtworkShape.Circle,
                    action = {
                        mediaActions.onMediaItemAction(artist.artistActionRequest(NaviampArtistMediaCommand.Select))
                    },
                ),
            )
        }
        results.albums.forEach { album ->
            add(
                TelevisionMediaGridItem("album:${album.id}", album.title, album.subtitle, album.coverArtUrl) {
                    mediaActions.onMediaItemAction(album.albumActionRequest(NaviampArtistAlbumCommand.Select))
                },
            )
        }
        results.tracks.forEach { track ->
            add(
                TelevisionMediaGridItem("track:${track.id}", track.title, track.subtitle, track.coverArtUrl) {
                    mediaActions.onTrackAction(SharedTrackRowActionRequest(track, SharedTrackRowAction.Select))
                },
            )
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxSize()) {
        if (searchHeaderVisible) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = screen.query,
                    onValueChange = actions.onQueryChanged,
                    label = { Text("Artists, albums, or tracks") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(searchFieldFocusRequester)
                        .onFocusChanged {
                            searchFieldFocused = it.isFocused
                            if (it.isFocused && !returnFocusToSearch) keyboardActive = true
                        }
                        .onPreviewKeyEvent { event ->
                            if (
                                event.type == KeyEventType.KeyDown &&
                                event.key == Key.DirectionDown &&
                                items.isNotEmpty()
                            ) {
                                keyboardController?.hide()
                                keyboardActive = false
                                resultsActive = true
                                searchHeaderVisible = false
                                true
                            } else {
                                false
                            }
                        },
                )
                TelevisionTextButton(
                    label = if (screen.searching) "Searching…" else "Search",
                    colors = colors,
                    calmFocus = true,
                    onClick = submitSearch,
                    modifier = Modifier.onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown && items.isNotEmpty()) {
                            keyboardController?.hide()
                            keyboardActive = false
                            resultsActive = true
                            searchHeaderVisible = false
                            true
                        } else {
                            false
                        }
                    },
                )
            }
        }
        screen.status?.let { Text(it, color = colors.secondaryText, fontSize = 15.sp) }
        if (items.isNotEmpty()) {
            TelevisionMediaGrid(
                items = items,
                colors = colors,
                focusFirstItem = resultsActive && !screen.searching,
            )
        }
    }
}

internal enum class TelevisionSearchBackTarget {
    Query,
    HideKeyboard,
    Navigation,
}

internal fun televisionSearchBackTarget(
    resultsActive: Boolean,
    searchFieldFocused: Boolean,
    keyboardActive: Boolean,
): TelevisionSearchBackTarget = when {
    resultsActive -> TelevisionSearchBackTarget.Query
    searchFieldFocused && keyboardActive -> TelevisionSearchBackTarget.HideKeyboard
    else -> TelevisionSearchBackTarget.Navigation
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun TelevisionMediaGrid(
    items: List<TelevisionMediaGridItem>,
    colors: NaviampColors,
    focusFirstItem: Boolean = false,
    focusRequest: TelevisionGridFocusRequest? = null,
    onItemFocused: (Int) -> Unit = {},
    onLeftFromFirstColumn: (() -> Unit)? = null,
    onUpFromFirstRow: (() -> Unit)? = null,
) {
    val itemKeys = items.map { it.key }
    val focusRequesters = remember(itemKeys) { List(items.size) { FocusRequester() } }
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val focusedRowInsetPx = with(density) { TelevisionGridFocusedRowTopInset.roundToPx() }
    var focusedRowStart by remember { mutableStateOf<Int?>(null) }

    suspend fun focusItem(index: Int) {
        gridState.scrollToItem(index)
        repeat(TelevisionFocusRequestAttempts) {
            withFrameNanos { }
            if (focusRequesters[index].requestFocus()) return
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val columnCount = televisionGridColumnCount(maxWidth)
        LaunchedEffect(focusRequest?.generation, itemKeys) {
            focusRequest?.index?.takeIf { it in items.indices }?.let { focusItem(it) }
        }
        LaunchedEffect(focusedRowStart) {
            focusedRowStart?.let { gridState.scrollToItem(it, -focusedRowInsetPx) }
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides TelevisionGridBringIntoViewSpec) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columnCount),
                state = gridState,
                horizontalArrangement = Arrangement.spacedBy(TelevisionGridSpacing),
                verticalArrangement = Arrangement.spacedBy(22.dp),
                contentPadding = PaddingValues(TelevisionFocusedItemOverflow),
                modifier = Modifier.fillMaxSize(),
            ) {
                gridItemsIndexed(items, key = { _, item -> item.key }) { index, item ->
                if (index == 0) {
                    LaunchedEffect(focusFirstItem, item.key) {
                        if (focusFirstItem) focusItem(0)
                    }
                }
                    TelevisionMediaGridCard(
                    title = item.title,
                    subtitle = item.subtitle,
                    coverArtUrl = item.coverArtUrl,
                    artworkShape = item.artworkShape,
                    colors = colors,
                    onFocused = {
                        focusedRowStart = televisionGridRowStart(index, columnCount)
                        onItemFocused(index)
                    },
                    onClick = item.action,
                    modifier = Modifier
                        .focusRequester(focusRequesters[index])
                        .onPreviewKeyEvent { event ->
                            if (
                                event.type == KeyEventType.KeyDown &&
                                event.key == Key.DirectionUp &&
                                index < columnCount &&
                                onUpFromFirstRow != null
                            ) {
                                onUpFromFirstRow()
                                return@onPreviewKeyEvent true
                            }
                            if (
                                event.type == KeyEventType.KeyDown &&
                                event.key == Key.DirectionLeft &&
                                index % columnCount == 0 &&
                                onLeftFromFirstColumn != null
                            ) {
                                onLeftFromFirstColumn()
                                return@onPreviewKeyEvent true
                            }
                            val nextIndex = televisionGridRightTarget(index, items.size)
                            if (
                                event.type == KeyEventType.KeyDown &&
                                event.key == Key.DirectionRight &&
                                nextIndex != null
                            ) {
                                if (!focusRequesters[nextIndex].requestFocus()) {
                                    coroutineScope.launch { focusItem(nextIndex) }
                                }
                                true
                            } else {
                                false
                            }
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private object TelevisionGridBringIntoViewSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

internal fun televisionGridRightTarget(currentIndex: Int, itemCount: Int): Int? =
    (currentIndex + 1).takeIf { currentIndex >= 0 && it < itemCount }

internal fun televisionGridRowStart(itemIndex: Int, columnCount: Int): Int? =
    if (itemIndex >= 0 && columnCount > 0) itemIndex - (itemIndex % columnCount) else null

internal fun televisionGridColumnCount(availableWidth: Dp): Int =
    ((availableWidth + TelevisionGridSpacing) / (TelevisionGridCardWidth + TelevisionGridSpacing))
        .toInt()
        .coerceAtLeast(1)

@Composable
private fun TelevisionMediaGridCard(
    title: String,
    subtitle: String,
    coverArtUrl: String?,
    artworkShape: TelevisionArtworkShape,
    colors: NaviampColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
) {
    val cornerRadius = when (artworkShape) {
        TelevisionArtworkShape.RoundedSquare -> 12.dp
        TelevisionArtworkShape.Circle -> TelevisionGridArtworkSize / 2
    }
    val shape = RoundedCornerShape(cornerRadius)
    TelevisionFocusableCard(
        colors = colors,
        width = TelevisionGridCardWidth,
        onFocused = onFocused,
        onClick = onClick,
        modifier = modifier,
    ) { focused ->
        NaviampCoverArt(
            coverArtUrl,
            colors,
            TelevisionGridArtworkSize,
            cornerRadius,
            Modifier.televisionMediaArtworkFocusEffect(focused, colors, shape),
        )
        TelevisionCardLabels(title, subtitle, colors)
    }
}

@Composable
internal fun TelevisionFocusableCard(
    colors: NaviampColors,
    width: Dp,
    onFocused: () -> Unit = {},
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .requiredWidth(width)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .onKeyEvent { event ->
                if (
                    event.type == KeyEventType.KeyUp &&
                    (
                        event.key == Key.DirectionCenter ||
                            event.key == Key.Enter ||
                            event.key == Key.Spacebar
                    )
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .focusable()
            .pointerInput(onClick) { detectTapGestures { onClick() } }
            .semantics { semanticsOnClick { onClick(); true } }
            .padding(bottom = 8.dp),
    ) {
        content(focused)
    }
}

@Composable
internal fun TelevisionCardLabels(title: String, subtitle: String, colors: NaviampColors) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Text(
            title,
            color = colors.primaryText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        if (subtitle.isNotBlank()) {
            Text(
                subtitle,
                color = colors.secondaryText,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

internal fun televisionHomeSectionScrollOffset(sectionIndex: Int, contextInsetPx: Int): Int =
    if (sectionIndex > 0) -contextInsetPx.coerceAtLeast(0) else 0

internal fun televisionHomeConsumesEndOfRailKey(key: Key): Boolean = key == Key.DirectionRight

internal fun televisionHomeVerticalSectionTarget(
    currentSectionIndex: Int,
    sectionCount: Int,
    key: Key,
): Int? = if (
    currentSectionIndex < 0 || currentSectionIndex >= sectionCount
) null else when (key) {
    Key.DirectionUp -> (currentSectionIndex - 1).takeIf { it >= 0 }
    Key.DirectionDown -> (currentSectionIndex + 1).takeIf { it < sectionCount }
    else -> null
}

internal fun televisionHomeRememberedItemIndex(rememberedIndex: Int?, itemCount: Int): Int =
    if (itemCount <= 0) 0 else (rememberedIndex ?: 0).coerceIn(0, itemCount - 1)

internal data class TelevisionQueueReorderState(
    val sourceQueueIndex: Int,
    val destinationQueueIndex: Int,
    val firstUpcomingQueueIndex: Int,
    val items: List<NaviampNowPlayingItemUi>,
    val sourceItem: NaviampNowPlayingItemUi,
)

internal fun televisionQueueBeginReorder(
    items: List<NaviampNowPlayingItemUi>,
    itemIndex: Int,
): TelevisionQueueReorderState? {
    val item = items.getOrNull(itemIndex) ?: return null
    val sourceQueueIndex = nowPlayingQueueIndex(item) ?: return null
    val firstUpcomingQueueIndex = items.firstOrNull()?.let(::nowPlayingQueueIndex) ?: return null
    return TelevisionQueueReorderState(
        sourceQueueIndex = sourceQueueIndex,
        destinationQueueIndex = sourceQueueIndex,
        firstUpcomingQueueIndex = firstUpcomingQueueIndex,
        items = items,
        sourceItem = item,
    )
}

internal fun televisionQueueMoveReorder(
    state: TelevisionQueueReorderState,
    direction: Int,
): TelevisionQueueReorderState {
    val currentIndex = state.destinationQueueIndex - state.firstUpcomingQueueIndex
    val destinationIndex = (currentIndex + direction).coerceIn(state.items.indices)
    if (destinationIndex == currentIndex) return state
    val reordered = state.items.toMutableList().apply {
        add(destinationIndex, removeAt(currentIndex))
    }
    return state.copy(
        destinationQueueIndex = state.firstUpcomingQueueIndex + destinationIndex,
        items = reordered,
    )
}

internal fun televisionNowPlayingQueueItems(nowPlaying: NowPlayingUi): List<NaviampNowPlayingItemUi> =
    if (nowPlaying.isLive) {
        nowPlaying.radioStations.filterNot { it.id == nowPlaying.id }
    } else {
        nowPlaying.upNext
    }

internal fun televisionNowPlayingQueueAvailable(nowPlaying: NowPlayingUi): Boolean =
    if (nowPlaying.isLive) nowPlaying.radioStations.isNotEmpty() else nowPlaying.queueCurrentIndex != null

@Composable
internal fun TelevisionNowPlaying(
    nowPlaying: NowPlayingUi,
    playbackProgress: StateFlow<PlaybackProgress>?,
    colors: NaviampColors,
    playerColors: NaviampPlayerColors = NaviampPlayerColors.fallback(colors),
    actions: NaviampNowPlayingActions,
    interactive: Boolean = true,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val queueButtonFocusRequester = remember { FocusRequester() }
    var queueOpen by remember { mutableStateOf(false) }
    var queueActionItem by remember { mutableStateOf<NaviampNowPlayingItemUi?>(null) }
    var queueReorder by remember { mutableStateOf<TelevisionQueueReorderState?>(null) }
    var restoreQueueButtonFocus by remember { mutableStateOf(false) }
    NaviampSystemBackHandler(enabled = interactive) {
        when {
            queueReorder != null -> queueReorder = null
            queueOpen -> {
                queueOpen = false
                queueActionItem = null
                restoreQueueButtonFocus = true
            }
            else -> onClose()
        }
    }
    LaunchedEffect(nowPlaying.id) {
        queueReorder = null
        queueActionItem = null
    }
    LaunchedEffect(queueOpen, restoreQueueButtonFocus) {
        if (!queueOpen && restoreQueueButtonFocus) {
            withFrameNanos { }
            queueButtonFocusRequester.requestFocus()
            restoreQueueButtonFocus = false
        }
    }
    val progress = playbackProgress?.collectAsState()?.value
        ?: PlaybackProgress(nowPlaying.positionSeconds, nowPlaying.durationSeconds)
    val duration = progress.durationSeconds ?: nowPlaying.durationSeconds
    val progressFraction = playbackFraction(progress.positionSeconds, duration).toFloat()
    val listeningModeFocusRequester = remember { FocusRequester() }
    var controlsVisible by remember(interactive) { mutableStateOf(interactive) }
    var interactionSequence by remember { mutableIntStateOf(0) }
    var consumedWakeKey by remember { mutableStateOf<Key?>(null) }
    LaunchedEffect(controlsVisible, interactionSequence, interactive, queueOpen) {
        if (interactive && controlsVisible && !queueOpen) {
            delay(TelevisionNowPlayingControlsTimeoutMillis)
            controlsVisible = false
        }
    }
    LaunchedEffect(controlsVisible, interactive) {
        if (interactive && !controlsVisible) {
            delay(TelevisionListeningModeTransitionMillis.toLong())
            repeat(TelevisionFocusRequestAttempts) {
                withFrameNanos { }
                if (listeningModeFocusRequester.requestFocus()) return@LaunchedEffect
            }
        }
    }
    val listeningMode = !controlsVisible
    val listeningCoverArtSize by animateDpAsState(
        targetValue = if (listeningMode) 310.dp else 270.dp,
        animationSpec = tween(TelevisionListeningModeTransitionMillis, easing = FastOutSlowInEasing),
        label = "TV listening-mode cover art size",
    )
    val listeningCoverArtRadius by animateDpAsState(
        targetValue = if (listeningMode) 22.dp else 18.dp,
        animationSpec = tween(TelevisionListeningModeTransitionMillis, easing = FastOutSlowInEasing),
        label = "TV listening-mode cover art radius",
    )
    val listeningScrubberHeight by animateDpAsState(
        targetValue = if (listeningMode) 60.dp else 46.dp,
        animationSpec = tween(TelevisionListeningModeTransitionMillis, easing = FastOutSlowInEasing),
        label = "TV listening-mode scrubber height",
    )
    val listeningScrubberPadding by animateDpAsState(
        targetValue = if (listeningMode) 9.dp else 5.dp,
        animationSpec = tween(TelevisionListeningModeTransitionMillis, easing = FastOutSlowInEasing),
        label = "TV listening-mode scrubber padding",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (!interactive) return@onPreviewKeyEvent false
                when {
                    consumedWakeKey == event.key && event.type == KeyEventType.KeyUp -> {
                        consumedWakeKey = null
                        true
                    }
                    event.type != KeyEventType.KeyDown -> false
                    !controlsVisible && televisionWakesNowPlayingControls(event.key) -> {
                        controlsVisible = true
                        interactionSequence += 1
                        consumedWakeKey = event.key
                        true
                    }
                    controlsVisible -> {
                        interactionSequence += 1
                        false
                    }
                    else -> false
                }
            },
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 24.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(34.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                NaviampCoverArt(
                    nowPlaying.coverArtUrl,
                    colors,
                    listeningCoverArtSize,
                    listeningCoverArtRadius,
                    decodeSize = 310.dp,
                )
                if (queueOpen) {
                    TelevisionNowPlayingQueue(
                        nowPlaying = nowPlaying,
                        colors = colors,
                        reorder = queueReorder,
                        onReorderChanged = { queueReorder = it },
                        onPlay = { item ->
                            if (nowPlaying.isLive) {
                                actions.selectItem(item, NowPlayingSelectionAction.SelectRadioStation)
                            } else if (
                                nowPlaying.isPaused &&
                                nowPlayingQueueIndex(item) == nowPlaying.queueCurrentIndex
                            ) {
                                actions.playback(NowPlayingPlaybackAction.Resume)
                            } else {
                                actions.selectItem(item, NowPlayingSelectionAction.SelectQueueItem)
                            }
                        },
                        onOpenActions = { queueActionItem = it },
                        onCommitReorder = { state ->
                            actions.moveQueueItem(
                                state.sourceQueueIndex,
                                state.destinationQueueIndex,
                                state.sourceItem,
                            )
                            queueReorder = null
                        },
                        modifier = Modifier.weight(1f),
                    )
                } else if (nowPlaying.lyricsVisible) {
                    TelevisionLyrics(nowPlaying, progress.positionSeconds, colors, Modifier.weight(1f))
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            nowPlaying.title,
                            color = colors.primaryText,
                            fontSize = 38.sp,
                            lineHeight = 43.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            nowPlaying.subtitle,
                            color = colors.secondaryText,
                            fontSize = 25.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val album = nowPlaying.albumLine.ifBlank { nowPlaying.albumTitle }
                        if (album.isNotBlank()) {
                            Text(album, color = colors.mutedText, fontSize = 18.sp, maxLines = 1)
                        }
                        if (nowPlaying.audioInfo.isNotBlank()) {
                            Text(nowPlaying.audioInfo, color = colors.mutedText, fontSize = 15.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TelevisionNowPlayingScrubberTestTag)
                    .padding(horizontal = 12.dp, vertical = listeningScrubberPadding),
            ) {
                Text(
                    televisionSecondsLabel(progress.positionSeconds),
                    color = colors.secondaryText,
                    fontSize = 15.sp,
                )
                WaveformScrubber(
                    amplitudes = nowPlaying.waveform?.amplitudes.orEmpty(),
                    value = progressFraction.coerceIn(0f, 1f),
                    enabled = false,
                    smoothProgress = nowPlaying.isPlaying,
                    durationSeconds = duration,
                    continuousWaveform = true,
                    colors = colors.copy(accent = playerColors.accent),
                    onValueChange = {},
                    onValueChangeFinished = {},
                    modifier = Modifier.weight(1f).height(listeningScrubberHeight),
                )
                Text(televisionSecondsLabel(duration), color = colors.secondaryText, fontSize = 15.sp)
            }
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(
                    tween(TelevisionListeningModeTransitionMillis, easing = FastOutSlowInEasing),
                ) + expandVertically(
                    animationSpec = tween(
                        TelevisionListeningModeTransitionMillis,
                        easing = FastOutSlowInEasing,
                    ),
                    expandFrom = Alignment.CenterVertically,
                ),
                exit = fadeOut(
                    tween(TelevisionListeningModeTransitionMillis, easing = FastOutSlowInEasing),
                ) + shrinkVertically(
                    animationSpec = tween(
                        TelevisionListeningModeTransitionMillis,
                        easing = FastOutSlowInEasing,
                    ),
                    shrinkTowards = Alignment.CenterVertically,
                ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(86.dp)
                        .padding(top = 16.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.Center),
                    ) {
                        TelevisionIconButton(
                            nowPlaying.hasPrevious,
                            NaviampTransportIcons.Previous,
                            "Previous",
                            colors,
                            whiteHighlight = true,
                        ) {
                            actions.playback(NowPlayingPlaybackAction.Previous)
                        }
                        TelevisionIconButton(
                            enabled = nowPlaying.canPlayPause,
                            icon = if (nowPlaying.isPlaying) NaviampTransportIcons.Pause else NaviampTransportIcons.Play,
                            description = if (nowPlaying.isPlaying) "Pause" else "Play",
                            colors = colors,
                            size = 64.dp,
                            prominent = true,
                            whiteHighlight = true,
                            initiallyFocused = interactive,
                        ) {
                            actions.playback(
                                when {
                                    nowPlaying.isPlaying -> NowPlayingPlaybackAction.Pause
                                    nowPlaying.isPaused -> NowPlayingPlaybackAction.Resume
                                    else -> NowPlayingPlaybackAction.PlayCurrent
                                },
                            )
                        }
                        TelevisionIconButton(
                            nowPlaying.hasNext,
                            NaviampTransportIcons.Next,
                            "Next",
                            colors,
                            whiteHighlight = true,
                        ) {
                            actions.playback(NowPlayingPlaybackAction.Next)
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    ) {
                        TelevisionIconButton(
                            nowPlaying.lyricsAvailable,
                            NaviampTransportIcons.Lyrics,
                            "Lyrics",
                            colors,
                            size = TelevisionNowPlayingSecondaryButtonSize,
                            iconSize = TelevisionNowPlayingSecondaryIconSize,
                            selected = nowPlaying.lyricsVisible && !queueOpen,
                            whiteHighlight = true,
                        ) {
                            if (queueOpen) {
                                queueOpen = false
                                if (!nowPlaying.lyricsVisible) actions.display(NowPlayingDisplayAction.ToggleLyrics)
                            } else {
                                actions.display(NowPlayingDisplayAction.ToggleLyrics)
                            }
                        }
                        TelevisionIconButton(
                            enabled = televisionNowPlayingQueueAvailable(nowPlaying),
                            icon = NaviampIcons.Queue,
                            description = "Queue",
                            colors = colors,
                            size = TelevisionNowPlayingSecondaryButtonSize,
                            iconSize = TelevisionNowPlayingSecondaryIconSize,
                            selected = queueOpen,
                            whiteHighlight = true,
                            focusRequester = queueButtonFocusRequester,
                        ) {
                            queueOpen = !queueOpen
                            queueActionItem = null
                            queueReorder = null
                        }
                        TelevisionIconButton(
                            nowPlaying.canFavorite,
                            if (nowPlaying.favoriteActive) NaviampTransportIcons.HeartFilled else NaviampTransportIcons.Heart,
                            "Favorite",
                            colors,
                            size = TelevisionNowPlayingSecondaryButtonSize,
                            iconSize = TelevisionNowPlayingSecondaryIconSize,
                            selected = nowPlaying.favoriteActive,
                            whiteHighlight = true,
                            selectedKeepsDarkBackground = true,
                            iconTint = Color(0xFFFF4D5A).takeIf { nowPlaying.favoriteActive },
                        ) {
                            actions.currentTrack(NowPlayingCurrentTrackAction.ToggleFavorite)
                        }
                        TelevisionIconButton(
                            enabled = nowPlaying.canRepeat,
                            icon = NaviampTransportIcons.Repeat,
                            description = televisionRepeatModeDescription(nowPlaying.repeatMode),
                            colors = colors,
                            size = TelevisionNowPlayingSecondaryButtonSize,
                            iconSize = TelevisionNowPlayingSecondaryIconSize,
                            selected = nowPlaying.repeatMode != NaviampRepeatMode.Off,
                            centerText = naviampRepeatIconCenterText(nowPlaying.repeatMode),
                            whiteHighlight = true,
                        ) {
                            actions.playback(NowPlayingPlaybackAction.CycleRepeatMode)
                        }
                        TelevisionIconButton(
                            nowPlaying.shuffleEnabled,
                            NaviampTransportIcons.Shuffle,
                            "Shuffle",
                            colors,
                            size = TelevisionNowPlayingSecondaryButtonSize,
                            iconSize = TelevisionNowPlayingSecondaryIconSize,
                            selected = nowPlaying.shuffleActive,
                            whiteHighlight = true,
                        ) {
                            actions.playback(NowPlayingPlaybackAction.ToggleShuffle)
                        }
                        TelevisionIconButton(
                            true,
                            NaviampIcons.Settings,
                            "Settings",
                            colors,
                            size = TelevisionNowPlayingSecondaryButtonSize,
                            iconSize = TelevisionNowPlayingSecondaryIconSize,
                            whiteHighlight = true,
                            onClick = onOpenSettings,
                        )
                    }
                }
            }
        }
        if (interactive && !controlsVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .size(1.dp)
                    .testTag(TelevisionNowPlayingListeningModeTestTag)
                    .focusRequester(listeningModeFocusRequester)
                    .focusable(),
            )
        }
    }
    queueActionItem?.let { item ->
        TelevisionQueueActionsDialog(
            item = item,
            colors = colors,
            onDismiss = { queueActionItem = null },
            onAction = { action ->
                actions.onQueueItemAction(nowPlayingItemActionRequest(item, action))
                queueActionItem = null
            },
        )
    }
}

@Composable
private fun TelevisionNowPlayingQueue(
    nowPlaying: NowPlayingUi,
    colors: NaviampColors,
    reorder: TelevisionQueueReorderState?,
    onReorderChanged: (TelevisionQueueReorderState?) -> Unit,
    onPlay: (NaviampNowPlayingItemUi) -> Unit,
    onOpenActions: (NaviampNowPlayingItemUi) -> Unit,
    onCommitReorder: (TelevisionQueueReorderState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stationMode = nowPlaying.isLive
    val upcoming = reorder?.items ?: televisionNowPlayingQueueItems(nowPlaying)
    val listState = rememberLazyListState()
    val currentFocusRequester = remember { FocusRequester() }
    val upcomingFocusRequesters = remember(upcoming.size) { List(upcoming.size) { FocusRequester() } }
    val currentItem = remember(nowPlaying.id, nowPlaying.isLive, nowPlaying.queueCurrentIndex) {
        NaviampNowPlayingItemUi(
            id = if (nowPlaying.isLive) {
                nowPlaying.id
            } else {
                nowPlaying.queueCurrentIndex?.let(::nowPlayingQueueItemId) ?: "current:${nowPlaying.id}"
            },
            title = nowPlaying.title,
            subtitle = nowPlaying.subtitle,
            meta = nowPlaying.albumLine.ifBlank { nowPlaying.albumTitle },
            coverArtUrl = nowPlaying.coverArtUrl,
            artistCredits = nowPlaying.artistCredits,
        )
    }
    LaunchedEffect(Unit) {
        repeat(TelevisionFocusRequestAttempts) {
            withFrameNanos { }
            val requester = upcomingFocusRequesters.firstOrNull() ?: currentFocusRequester
            if (requester.requestFocus()) return@LaunchedEffect
        }
    }
    LaunchedEffect(reorder?.destinationQueueIndex) {
        val state = reorder ?: return@LaunchedEffect
        val index = state.destinationQueueIndex - state.firstUpcomingQueueIndex
        if (index in upcomingFocusRequesters.indices) {
            withFrameNanos { }
            upcomingFocusRequesters[index].requestFocus()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier.fillMaxHeight()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (stationMode) "INTERNET RADIO" else "QUEUE",
                color = colors.primaryText,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.weight(1f))
            Text(
                when {
                    stationMode -> "Select: play station"
                    reorder == null -> "Left: move  •  Right: actions"
                    else -> "Up/Down: move  •  Select/Right: place  •  Back: cancel"
                },
                color = colors.mutedText,
                fontSize = 13.sp,
            )
        }
        TelevisionQueueRow(
            item = currentItem,
            colors = colors,
            label = "NOW PLAYING",
            focusRequester = currentFocusRequester,
            onClick = { onPlay(currentItem) },
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .testTag(TelevisionNowPlayingQueueCurrentTestTag)
                .onPreviewKeyEvent { event ->
                    if (
                        !stationMode &&
                        event.type == KeyEventType.KeyDown &&
                        event.key == Key.DirectionRight
                    ) {
                        onOpenActions(currentItem)
                        true
                    } else if (
                        event.type == KeyEventType.KeyDown &&
                        event.key == Key.DirectionDown &&
                        upcomingFocusRequesters.isNotEmpty()
                    ) {
                        upcomingFocusRequesters.first().requestFocus()
                        true
                    } else false
                },
        )
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(7.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag(TelevisionNowPlayingQueueTestTag),
        ) {
            if (upcoming.isEmpty()) item(key = "empty") {
                Text(
                    if (stationMode) "No other Internet Radio stations are saved." else "Nothing else is queued.",
                    color = colors.secondaryText,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 18.dp),
                )
            }
            itemsIndexed(
                items = upcoming,
                key = { index, _ -> "upcoming-position:$index" },
            ) { index, item ->
                val moving = reorder != null &&
                    reorder.destinationQueueIndex == reorder.firstUpcomingQueueIndex + index
                TelevisionQueueRow(
                    item = item,
                    colors = colors,
                    label = when {
                        moving -> "MOVING"
                        item.playNextPriority -> "PLAY NEXT"
                        else -> null
                    },
                    moving = moving,
                    focusRequester = upcomingFocusRequesters[index],
                    onClick = { onPlay(item) },
                    modifier = Modifier
                        .testTag("$TelevisionNowPlayingQueueUpcomingTestTagPrefix$index")
                        .onPreviewKeyEvent { event ->
                        when {
                            !stationMode && reorder != null &&
                                event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp -> {
                                onReorderChanged(televisionQueueMoveReorder(reorder, -1))
                                true
                            }
                            !stationMode && reorder != null &&
                                event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown -> {
                                onReorderChanged(televisionQueueMoveReorder(reorder, 1))
                                true
                            }
                            !stationMode && reorder != null &&
                                event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight -> {
                                onCommitReorder(reorder)
                                true
                            }
                            !stationMode && reorder != null && event.type == KeyEventType.KeyUp &&
                                (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.Spacebar) -> {
                                onCommitReorder(reorder)
                                true
                            }
                            !stationMode && reorder == null &&
                                event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft -> {
                                onReorderChanged(televisionQueueBeginReorder(nowPlaying.upNext, index))
                                true
                            }
                            !stationMode && reorder == null &&
                                event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight -> {
                                onOpenActions(item)
                                true
                            }
                            index == 0 && event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp -> {
                                currentFocusRequester.requestFocus()
                                true
                            }
                            else -> false
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun TelevisionQueueRow(
    item: NaviampNowPlayingItemUi,
    colors: NaviampColors,
    label: String?,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    moving: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                if (
                    event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.Spacebar)
                ) {
                    onClick()
                    true
                } else false
            }
            .focusable()
            .televisionFocusEffect(focused, colors, shape)
            .clip(shape)
            .background(
                when {
                    moving -> colors.accent.copy(alpha = 0.52f)
                    focused -> colors.accent.copy(alpha = 0.34f)
                    label == "NOW PLAYING" -> colors.controlSurface.copy(alpha = 0.82f)
                    else -> Color.Black.copy(alpha = 0.22f)
                },
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        NaviampCoverArt(item.coverArtUrl, colors, 48.dp, 7.dp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(
                item.title,
                color = colors.primaryText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(item.subtitle, color = colors.secondaryText, fontSize = 13.sp, maxLines = 1)
        }
        if (item.meta.isNotBlank()) Text(item.meta, color = colors.mutedText, fontSize = 12.sp)
        label?.let {
            Text(it, color = colors.primaryText, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun TelevisionQueueActionsDialog(
    item: NaviampNowPlayingItemUi,
    colors: NaviampColors,
    onDismiss: () -> Unit,
    onAction: (NowPlayingItemAction) -> Unit,
) {
    val firstActionFocusRequester = remember { FocusRequester() }
    val actions = listOf(
        NowPlayingItemAction.PlayNext to "Play Next",
        NowPlayingItemAction.RemoveFromQueue to "Remove",
        NowPlayingItemAction.StartRadio to "Start Radio",
    )
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.68f))) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .background(colors.controlSurface, RoundedCornerShape(18.dp))
                    .padding(28.dp),
            ) {
                Text("Queue actions", color = colors.primaryText, fontSize = 27.sp, fontWeight = FontWeight.Black)
                Text(item.title, color = colors.primaryText, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(item.subtitle, color = colors.secondaryText, fontSize = 16.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                    actions.forEachIndexed { index, (action, label) ->
                        TelevisionTextButton(
                            label = label,
                            colors = colors,
                            onClick = { onAction(action) },
                            modifier = Modifier
                                .weight(1f)
                                .then(if (index == 0) Modifier.focusRequester(firstActionFocusRequester) else Modifier),
                        )
                    }
                }
                Text("Back closes this panel", color = colors.mutedText, fontSize = 14.sp)
            }
        }
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        firstActionFocusRequester.requestFocus()
    }
}

@Composable
internal fun TelevisionMiniPlayer(
    nowPlaying: NowPlayingUi,
    colors: NaviampColors,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.34f), shape)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        NaviampCoverArt(nowPlaying.coverArtUrl, colors, 36.dp, 6.dp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                nowPlaying.title,
                color = colors.primaryText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                nowPlaying.subtitle,
                color = colors.secondaryText,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TelevisionLyrics(
    nowPlaying: NowPlayingUi,
    positionSeconds: Double?,
    colors: NaviampColors,
    modifier: Modifier = Modifier,
) {
    val listState = remember(nowPlaying.id) { LazyListState() }
    val positionMillis = positionSeconds?.times(1000)?.toLong()
    val activeIndex = nowPlaying.lyricsLines.indexOfLast { line ->
        line.startMillis?.plus(nowPlaying.lyricsOffsetMillis)?.let { start ->
            positionMillis != null && start <= positionMillis
        } == true
    }
    LaunchedEffect(activeIndex, nowPlaying.lyricsLines.size) {
        if (activeIndex >= 0) listState.animateToActiveLyricLine(activeIndex)
    }
    when {
        nowPlaying.lyricsStatus != null -> Text(
            nowPlaying.lyricsStatus,
            color = colors.secondaryText,
            fontSize = 22.sp,
            modifier = modifier,
        )
        nowPlaying.lyricsLines.isEmpty() -> Text(
            "Lyrics are not available.",
            color = colors.secondaryText,
            fontSize = 22.sp,
            modifier = modifier,
        )
        else -> LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = modifier,
        ) {
            itemsIndexed(
                nowPlaying.lyricsLines,
                key = { index, line -> "lyric:$index:${line.startMillis}:${line.text}" },
            ) { index, line ->
                val active = index == activeIndex || (activeIndex < 0 && index == 0)
                val emphasis by animateFloatAsState(
                    targetValue = if (active) 1f else 0f,
                    animationSpec = tween(
                        durationMillis = LyricsLineTransitionMillis,
                        easing = FastOutSlowInEasing,
                    ),
                    label = "TV lyric line emphasis",
                )
                val inactiveColor = colors.secondaryText.copy(alpha = 0.58f)
                Text(
                    line.text,
                    color = androidx.compose.ui.graphics.lerp(inactiveColor, colors.primaryText, emphasis),
                    fontSize = (21f + 8f * emphasis).sp,
                    lineHeight = (26f + 8f * emphasis).sp,
                    fontWeight = if (emphasis >= 0.5f) FontWeight.Black else FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun TelevisionIconButton(
    enabled: Boolean,
    icon: ImageVector,
    description: String,
    colors: NaviampColors,
    selected: Boolean = false,
    size: Dp = 48.dp,
    prominent: Boolean = false,
    iconSize: Dp = if (prominent) 34.dp else 25.dp,
    centerText: String? = null,
    whiteHighlight: Boolean = false,
    selectedKeepsDarkBackground: Boolean = false,
    iconTint: Color? = null,
    initiallyFocused: Boolean = false,
    upFocusRequester: FocusRequester? = null,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val localFocusRequester = remember { FocusRequester() }
    val effectiveFocusRequester = focusRequester ?: localFocusRequester
    val shape = RoundedCornerShape(999.dp)
    val usesLightSurface = televisionIconButtonUsesLightSurface(
        focused = focused,
        selected = selected,
        whiteHighlight = whiteHighlight,
        selectedKeepsDarkBackground = selectedKeepsDarkBackground,
    )
    LaunchedEffect(Unit) {
        if (initiallyFocused && enabled) {
            repeat(TelevisionFocusRequestAttempts) {
                withFrameNanos { }
                if (effectiveFocusRequester.requestFocus()) return@LaunchedEffect
            }
        }
    }
    IconButton(
        onClick = {
            onClick()
            effectiveFocusRequester.requestFocus()
        },
        enabled = enabled,
        modifier = Modifier
            .requiredSize(size)
            .focusRequester(effectiveFocusRequester)
            .onPreviewKeyEvent { event ->
                if (upFocusRequester == null || event.key != Key.DirectionUp) {
                    false
                } else {
                    if (event.type == KeyEventType.KeyDown) upFocusRequester.requestFocus()
                    true
                }
            }
            .onFocusChanged { focused = it.isFocused }
            .televisionFocusEffect(focused, colors, shape)
            .clip(shape)
            .background(
                when {
                    usesLightSurface ->
                        colors.primaryText
                    selected && selectedKeepsDarkBackground -> colors.controlSurface.copy(alpha = 0.9f)
                    focused -> colors.accent.copy(alpha = if (prominent) 0.96f else 0.82f)
                    prominent -> colors.controlSurface.copy(alpha = 0.9f)
                    selected -> colors.accent.copy(alpha = 0.32f)
                    else -> colors.controlSurface.copy(alpha = 0.9f)
                },
            ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = description,
                tint = iconTint
                    ?: if (usesLightSurface) colors.background
                    else if (enabled) colors.primaryText
                    else colors.mutedText,
                modifier = Modifier.size(iconSize),
            )
            centerText?.let {
                Text(
                    it,
                    color = if (usesLightSurface) colors.background else colors.primaryText,
                    fontSize = if (it.length > 1) 7.sp else 10.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

internal fun televisionIconButtonUsesLightSurface(
    focused: Boolean,
    selected: Boolean,
    whiteHighlight: Boolean,
    selectedKeepsDarkBackground: Boolean,
): Boolean =
    whiteHighlight && (focused || (selected && !selectedKeepsDarkBackground))

internal fun televisionRepeatModeDescription(mode: NaviampRepeatMode): String = when (mode) {
    NaviampRepeatMode.Off -> "Repeat off"
    NaviampRepeatMode.Queue -> "Repeat all"
    NaviampRepeatMode.Track -> "Repeat one"
}

@Composable
internal fun TelevisionTextButton(
    label: String,
    colors: NaviampColors,
    enabled: Boolean = true,
    selected: Boolean = false,
    calmFocus: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = when {
            calmFocus -> ButtonDefaults.buttonColors(
                containerColor = if (focused) colors.primaryText else colors.controlSurface,
                contentColor = if (focused) colors.background else colors.primaryText,
            )
            selected -> ButtonDefaults.buttonColors(
                containerColor = colors.accent.copy(alpha = 0.72f),
                contentColor = colors.primaryText,
            )
            else -> ButtonDefaults.buttonColors()
        },
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .then(
                if (calmFocus) Modifier else Modifier.televisionFocusEffect(focused, colors, shape),
            ),
        shape = shape,
    ) {
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun Modifier.televisionFocusEffect(
    focused: Boolean,
    colors: NaviampColors,
    shape: RoundedCornerShape,
): Modifier {
    val scale by animateFloatAsState(
        targetValue = if (focused) TelevisionFocusedScale else 1f,
        animationSpec = tween(TelevisionFocusAnimationMillis),
        label = "Television focus scale",
    )
    return this
        .zIndex(if (focused) 1f else 0f)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.shape = shape
            clip = false
        }
}

@Composable
internal fun Modifier.televisionMediaArtworkFocusEffect(
    focused: Boolean,
    colors: NaviampColors,
    shape: RoundedCornerShape,
): Modifier = televisionFocusEffect(focused, colors, shape)
    .then(
        if (focused) {
            Modifier.border(3.dp, colors.accent.copy(alpha = 0.95f), shape)
        } else {
            Modifier
        },
    )

private fun televisionSecondsLabel(seconds: Double?): String {
    val total = seconds?.takeIf { it.isFinite() && it >= 0.0 }?.roundToInt() ?: return "--:--"
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}

internal fun televisionWakesNowPlayingControls(key: Key): Boolean =
    key == Key.DirectionLeft ||
        key == Key.DirectionRight ||
        key == Key.DirectionUp ||
        key == Key.DirectionDown ||
        key == Key.DirectionCenter ||
        key == Key.Enter ||
        key == Key.Spacebar

private val TelevisionHomeCardWidth = 150.dp
private val TelevisionHomeCardSpacing = 14.dp
private val TelevisionHomeCardStride = TelevisionHomeCardWidth + TelevisionHomeCardSpacing
private const val TelevisionHomeVisibleAnchorItems = 3
private val TelevisionGridCardWidth = 136.dp
private val TelevisionGridSpacing = 14.dp
private val TelevisionGridArtworkSize = 136.dp
private val TelevisionGridFocusedRowTopInset = 26.dp
private val TelevisionLibraryShortcutRailWidth = 20.dp
private val TelevisionLibraryShortcutSize = 12.dp
private val TelevisionLibraryContentStartInset = 30.dp
private const val TelevisionFocusRequestAttempts = 5
private const val TelevisionFocusAnimationMillis = 140
private const val TelevisionFocusedScale = 1.06f
private val TelevisionFocusedItemOverflow = 10.dp
private val TelevisionHomeRailSpacing = 8.dp
private val TelevisionHomeFocusedSectionTopInset = 40.dp
private val TelevisionHomeBottomFocusClearance = 360.dp
internal const val TelevisionNowPlayingControlsTimeoutMillis = 5_000L
internal const val TelevisionListeningModeTransitionMillis = 800
internal val TelevisionNowPlayingSecondaryButtonSize = 38.dp
internal val TelevisionNowPlayingSecondaryIconSize = 20.dp
internal const val TelevisionNowPlayingScrubberTestTag = "television-now-playing-scrubber"
internal const val TelevisionNowPlayingListeningModeTestTag = "television-now-playing-listening-mode"
internal const val TelevisionNowPlayingQueueTestTag = "television-now-playing-queue"
internal const val TelevisionNowPlayingQueueCurrentTestTag = "television-now-playing-queue-current"
internal const val TelevisionNowPlayingQueueUpcomingTestTagPrefix = "television-now-playing-queue-upcoming-"
internal const val TelevisionHomeViewAllTestTagPrefix = "television-home-view-all-"
internal const val TelevisionHomeCollectionTestTag = "television-home-collection"
internal const val TelevisionHomeCollectionBackTestTag = "television-home-collection-back"
internal const val TelevisionHomeCollectionItemTestTagPrefix = "television-home-collection-item-"
