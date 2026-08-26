package app.naviamp.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
) {
    val sections = televisionHomeSections(home.content.collectionSections)
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val contextInsetPx = with(density) { TelevisionHomeFocusedSectionTopInset.roundToPx() }
    val sectionItemKeys = sections.map { section ->
        section.items.map { item -> "${item.mediaKind}:${item.mediaItem.id}" }
    }
    val itemFocusRequesters = remember(sectionItemKeys) {
        sectionItemKeys.map { itemKeys -> List(itemKeys.size) { FocusRequester() } }
    }
    var rememberedItemIndices by remember(sectionItemKeys) { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var focusedSectionIndex by remember { mutableStateOf<Int?>(null) }
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
                                itemCount = sections[targetSectionIndex].items.size,
                            ),
                        )
                    },
                    nextSectionFocusRequester = nextSectionIndex?.let { targetSectionIndex ->
                        itemFocusRequesters[targetSectionIndex].getOrNull(
                            televisionHomeRememberedItemIndex(
                                rememberedIndex = rememberedItemIndices[sections[targetSectionIndex].id],
                                itemCount = sections[targetSectionIndex].items.size,
                            ),
                        )
                    },
                    onSectionFocused = { focusedSectionIndex = sectionIndex },
                    onItemFocused = { itemIndex ->
                        rememberedItemIndices = rememberedItemIndices + (section.id to itemIndex)
                    },
                    onSelected = { item -> dispatchHomeCollectionItem(item, actions, mediaActions) },
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
                            .focusRequester(itemFocusRequesters[index])
                            .then(
                                if (index == section.items.lastIndex) {
                                    Modifier.onPreviewKeyEvent { event ->
                                        televisionHomeConsumesEndOfRailKey(event.key)
                                    }
                                } else {
                                    Modifier
                                },
                            ),
                        onFocused = {
                            focusedIndex = index
                            onItemFocused(index)
                            onSectionFocused()
                        },
                        onClick = { onSelected(item) },
                    )
                }
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
        HomeCollectionArtwork(
            item,
            colors,
            TelevisionHomeCardWidth,
            Modifier.televisionFocusEffect(focused, colors, RoundedCornerShape(7.dp)),
        )
        TelevisionCardLabels(item.title, item.subtitle, colors)
    }
}

@Composable
internal fun TelevisionLibrary(
    screen: NaviampLibraryScreenUi,
    colors: NaviampColors,
    actions: NaviampLibraryActions,
    mediaActions: NaviampMediaActions,
    onOpenPlaylists: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            TelevisionTextButton("Playlists", colors, onClick = onOpenPlaylists)
            Box(modifier = Modifier.padding(start = 10.dp)) {
                TelevisionTextButton("Refresh", colors, onClick = actions.onRefresh)
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
                        action = {
                            mediaActions.onMediaItemAction(artist.artistActionRequest(NaviampArtistMediaCommand.Select))
                        },
                    )
                },
                colors = colors,
            )
        }
    }
}

private data class TelevisionMediaGridItem(
    val key: String,
    val title: String,
    val subtitle: String,
    val coverArtUrl: String?,
    val action: () -> Unit,
)

@Composable
internal fun TelevisionSearch(
    screen: NaviampSearchScreenUi,
    colors: NaviampColors,
    actions: NaviampSearchActions,
    mediaActions: NaviampMediaActions,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFieldFocusRequester = remember { FocusRequester() }
    var searchFieldFocused by remember { mutableStateOf(false) }
    var keyboardActive by remember { mutableStateOf(false) }
    var resultsActive by remember { mutableStateOf(false) }
    var searchHeaderVisible by remember { mutableStateOf(true) }
    var returnFocusToSearch by remember { mutableStateOf(false) }
    val submitSearch = {
        actions.onSearch()
        if (searchFieldFocused) {
            keyboardActive = true
            keyboardController?.show()
        }
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
                TelevisionMediaGridItem("artist:${artist.id}", artist.title, artist.subtitle, artist.coverArtUrl) {
                    mediaActions.onMediaItemAction(artist.artistActionRequest(NaviampArtistMediaCommand.Select))
                },
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
                                !keyboardActive &&
                                items.isNotEmpty()
                            ) {
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
                    onClick = submitSearch,
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
private fun TelevisionMediaGrid(
    items: List<TelevisionMediaGridItem>,
    colors: NaviampColors,
    focusFirstItem: Boolean = false,
) {
    val itemKeys = items.map { it.key }
    val focusRequesters = remember(itemKeys) { List(items.size) { FocusRequester() } }
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
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
        LaunchedEffect(focusedRowStart) {
            focusedRowStart?.let { gridState.animateScrollToItem(it) }
        }
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
                    colors = colors,
                    onFocused = {
                        focusedRowStart = televisionGridRowStart(index, columnCount)
                    },
                    onClick = item.action,
                    modifier = Modifier
                        .focusRequester(focusRequesters[index])
                        .onPreviewKeyEvent { event ->
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
    colors: NaviampColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
) {
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
            12.dp,
            Modifier.televisionFocusEffect(focused, colors, RoundedCornerShape(12.dp)),
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

@Composable
internal fun TelevisionNowPlaying(
    nowPlaying: NowPlayingUi,
    playbackProgress: StateFlow<PlaybackProgress>?,
    colors: NaviampColors,
    actions: NaviampNowPlayingActions,
    onClose: () -> Unit,
    onSearch: () -> Unit,
) {
    NaviampSystemBackHandler(enabled = true, onBack = onClose)
    val progress = playbackProgress?.collectAsState()?.value
        ?: PlaybackProgress(nowPlaying.positionSeconds, nowPlaying.durationSeconds)
    val duration = progress.durationSeconds ?: nowPlaying.durationSeconds
    val progressFraction = playbackFraction(progress.positionSeconds, duration).toFloat()
    val listeningModeFocusRequester = remember(nowPlaying.id) { FocusRequester() }
    var controlsVisible by remember(nowPlaying.id) { mutableStateOf(true) }
    var interactionSequence by remember(nowPlaying.id) { mutableIntStateOf(0) }
    var consumedWakeKey by remember(nowPlaying.id) { mutableStateOf<Key?>(null) }
    LaunchedEffect(nowPlaying.id, controlsVisible, interactionSequence) {
        if (controlsVisible) {
            delay(TelevisionNowPlayingControlsTimeoutMillis)
            controlsVisible = false
        }
    }
    LaunchedEffect(controlsVisible) {
        if (!controlsVisible) {
            repeat(TelevisionFocusRequestAttempts) {
                withFrameNanos { }
                if (listeningModeFocusRequester.requestFocus()) return@LaunchedEffect
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 24.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(34.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                NaviampCoverArt(nowPlaying.coverArtUrl, colors, 270.dp, 18.dp)
                if (nowPlaying.lyricsVisible) {
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
                        val album = nowPlaying.albumTitle.ifBlank { nowPlaying.albumLine }
                        if (album.isNotBlank()) {
                            Text(album, color = colors.mutedText, fontSize = 18.sp, maxLines = 1)
                        }
                        if (nowPlaying.audioInfo.isNotBlank()) {
                            Text(nowPlaying.audioInfo, color = colors.mutedText, fontSize = 15.sp)
                        }
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TelevisionNowPlayingScrubberTestTag)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
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
                    colors = colors,
                    onValueChange = {},
                    onValueChangeFinished = {},
                    modifier = Modifier.weight(1f).height(46.dp),
                )
                Text(televisionSecondsLabel(duration), color = colors.secondaryText, fontSize = 15.sp)
            }
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(tween(TelevisionFocusAnimationMillis)),
                exit = fadeOut(tween(TelevisionFocusAnimationMillis)),
            ) {
                Box(modifier = Modifier.fillMaxWidth().height(70.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.Center),
                    ) {
                        TelevisionIconButton(nowPlaying.hasPrevious, NaviampTransportIcons.Previous, "Previous", colors) {
                            actions.playback(NowPlayingPlaybackAction.Previous)
                        }
                        TelevisionIconButton(
                            enabled = nowPlaying.canPlayPause,
                            icon = if (nowPlaying.isPlaying) NaviampTransportIcons.Pause else NaviampTransportIcons.Play,
                            description = if (nowPlaying.isPlaying) "Pause" else "Play",
                            colors = colors,
                            size = 64.dp,
                            prominent = true,
                            initiallyFocused = true,
                        ) {
                            actions.playback(
                                when {
                                    nowPlaying.isPlaying -> NowPlayingPlaybackAction.Pause
                                    nowPlaying.isPaused -> NowPlayingPlaybackAction.Resume
                                    else -> NowPlayingPlaybackAction.PlayCurrent
                                },
                            )
                        }
                        TelevisionIconButton(nowPlaying.hasNext, NaviampTransportIcons.Next, "Next", colors) {
                            actions.playback(NowPlayingPlaybackAction.Next)
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    ) {
                        TelevisionIconButton(nowPlaying.lyricsAvailable, NaviampTransportIcons.Lyrics, "Lyrics", colors, selected = nowPlaying.lyricsVisible) {
                            actions.display(NowPlayingDisplayAction.ToggleLyrics)
                        }
                        TelevisionIconButton(nowPlaying.canFavorite, if (nowPlaying.favoriteActive) NaviampTransportIcons.HeartFilled else NaviampTransportIcons.Heart, "Favorite", colors, selected = nowPlaying.favoriteActive) {
                            actions.currentTrack(NowPlayingCurrentTrackAction.ToggleFavorite)
                        }
                        TelevisionIconButton(nowPlaying.canRepeat, NaviampTransportIcons.Repeat, "Repeat", colors, selected = nowPlaying.repeatMode != NaviampRepeatMode.Off) {
                            actions.playback(NowPlayingPlaybackAction.CycleRepeatMode)
                        }
                        TelevisionIconButton(nowPlaying.shuffleEnabled, NaviampTransportIcons.Shuffle, "Shuffle", colors, selected = nowPlaying.shuffleActive) {
                            actions.playback(NowPlayingPlaybackAction.ToggleShuffle)
                        }
                        TelevisionIconButton(true, NaviampIcons.Search, "Search", colors, onClick = onSearch)
                    }
                }
            }
        }
        if (!controlsVisible) {
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
    val positionMillis = positionSeconds?.times(1000)?.toLong()
    val activeIndex = nowPlaying.lyricsLines.indexOfLast { line ->
        line.startMillis?.plus(nowPlaying.lyricsOffsetMillis)?.let { start ->
            positionMillis != null && start <= positionMillis
        } == true
    }
    val first = if (activeIndex < 0) 0 else (activeIndex - 2).coerceAtLeast(0)
    val visible = nowPlaying.lyricsLines.drop(first).take(6)
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier,
    ) {
        when {
            nowPlaying.lyricsStatus != null -> Text(nowPlaying.lyricsStatus, color = colors.secondaryText, fontSize = 22.sp)
            visible.isEmpty() -> Text("Lyrics are not available.", color = colors.secondaryText, fontSize = 22.sp)
            else -> visible.forEachIndexed { offset, line ->
                val index = first + offset
                val active = index == activeIndex || (activeIndex < 0 && index == 0)
                Text(
                    line.text,
                    color = if (active) colors.primaryText else colors.secondaryText.copy(alpha = 0.58f),
                    fontSize = if (active) 29.sp else 21.sp,
                    lineHeight = if (active) 34.sp else 26.sp,
                    fontWeight = if (active) FontWeight.Black else FontWeight.Medium,
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
    initiallyFocused: Boolean = false,
    upFocusRequester: FocusRequester? = null,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val localFocusRequester = remember { FocusRequester() }
    val effectiveFocusRequester = focusRequester ?: localFocusRequester
    val shape = RoundedCornerShape(999.dp)
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
                    prominent -> colors.primaryText
                    selected -> colors.accent.copy(alpha = 0.32f)
                    else -> colors.controlSurface.copy(alpha = 0.9f)
                },
            ),
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (prominent) colors.background else if (enabled) colors.primaryText else colors.mutedText,
            modifier = Modifier.size(if (prominent) 34.dp else 25.dp),
        )
    }
}

@Composable
internal fun TelevisionTextButton(
    label: String,
    colors: NaviampColors,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .televisionFocusEffect(focused, colors, shape),
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
    val sparkle = remember { Animatable(0f) }
    LaunchedEffect(focused) {
        if (!focused) {
            sparkle.snapTo(0f)
            return@LaunchedEffect
        }
        sparkle.snapTo(0.32f)
        while (true) {
            sparkle.animateTo(1f, tween(220))
            sparkle.animateTo(0.38f, tween(520))
            sparkle.animateTo(0.76f, tween(180))
            sparkle.animateTo(0.32f, tween(680))
        }
    }
    val scale by animateFloatAsState(
        targetValue = if (focused) TelevisionFocusedScale else 1f,
        animationSpec = tween(TelevisionFocusAnimationMillis),
        label = "Television focus scale",
    )
    val elevation by animateFloatAsState(
        targetValue = if (focused) TelevisionFocusedElevation.value else 0f,
        animationSpec = tween(TelevisionFocusAnimationMillis),
        label = "Television focus elevation",
    )
    val sparkleValue = if (focused) sparkle.value else 0f
    return this
        .zIndex(if (focused) 1f else 0f)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            shadowElevation =
                (elevation + sparkleValue * TelevisionFocusedSparkleElevation.value).dp.toPx()
            this.shape = shape
            clip = false
            ambientShadowColor = colors.accent.copy(alpha = 0.70f + sparkleValue * 0.20f)
            spotShadowColor = colors.accent.copy(alpha = 0.78f + sparkleValue * 0.22f)
        }
        .border(
            width = if (focused) TelevisionFocusedOuterBorderWidth else 0.dp,
            color = colors.accent.copy(alpha = 0.18f),
            shape = shape,
        )
        .border(
            width = if (focused) TelevisionFocusedMiddleBorderWidth else 0.dp,
            color = colors.accent.copy(alpha = 0.58f),
            shape = shape,
        )
        .border(
            width = if (focused) TelevisionFocusedCoreBorderWidth else 0.dp,
            color = TelevisionFocusedCoreColor.copy(alpha = 0.72f + sparkleValue * 0.28f),
            shape = shape,
        )
}

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
private const val TelevisionFocusRequestAttempts = 5
private const val TelevisionFocusAnimationMillis = 140
private const val TelevisionFocusedScale = 1.06f
private val TelevisionFocusedElevation = 32.dp
private val TelevisionFocusedSparkleElevation = 10.dp
private val TelevisionFocusedOuterBorderWidth = 7.dp
private val TelevisionFocusedMiddleBorderWidth = 4.dp
private val TelevisionFocusedCoreBorderWidth = 1.5.dp
private val TelevisionFocusedCoreColor = Color(0xFFBDEBFF)
private val TelevisionFocusedItemOverflow = 10.dp
private val TelevisionHomeRailSpacing = 8.dp
private val TelevisionHomeFocusedSectionTopInset = 40.dp
private val TelevisionHomeBottomFocusClearance = 360.dp
internal const val TelevisionNowPlayingControlsTimeoutMillis = 5_000L
internal const val TelevisionNowPlayingScrubberTestTag = "television-now-playing-scrubber"
internal const val TelevisionNowPlayingListeningModeTestTag = "television-now-playing-listening-mode"
