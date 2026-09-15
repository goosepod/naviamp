package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.domain.library.libraryLetterJumpIndex
import app.naviamp.domain.settings.LibraryAlbumSortOrder
import app.naviamp.ui.generated.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Retained above detail routes; each catalog owns its viewport and stable focused identity. */
@Stable
internal class NaviampTelevisionLibraryState(
    val lists: NaviampLibraryViewportState,
    private val grids: Map<NaviampLibraryView, LazyGridState>,
) {
    private val focusedIds = mutableStateMapOf<NaviampLibraryView, String>()
    var restoreContent by mutableStateOf(false)
    private var consumedJump: Long? = null

    fun grid(view: NaviampLibraryView): LazyGridState = grids.getValue(view)
    fun focusedId(view: NaviampLibraryView): String? = focusedIds[view]
    fun record(view: NaviampLibraryView, id: String) {
        focusedIds[view] = id
        restoreContent = true
    }
    fun consumeJump(jump: NaviampLibraryJumpUi, view: NaviampLibraryView): Boolean {
        if (jump.view != view || jump.generation == consumedJump) return false
        consumedJump = jump.generation
        return true
    }
}

@Composable
internal fun rememberNaviampTelevisionLibraryState(): NaviampTelevisionLibraryState {
    val lists = rememberNaviampLibraryViewportState()
    val artists = rememberLazyGridState()
    val albums = rememberLazyGridState()
    return remember(lists, artists, albums) {
        NaviampTelevisionLibraryState(lists, mapOf(
            NaviampLibraryView.Artists to artists,
            NaviampLibraryView.Albums to albums,
        ))
    }
}

internal fun televisionLibraryRestoreIndex(ids: List<String>, focusedId: String?): Int? =
    if (ids.isEmpty()) null else ids.indexOf(focusedId).takeIf { it >= 0 } ?: 0

internal fun televisionLibraryShortcutRequiresScroll(
    targetIndex: Int,
    visibleItemIndices: Iterable<Int>,
): Boolean = targetIndex !in visibleItemIndices

@Composable
internal fun TelevisionLibrary(
    screen: NaviampLibraryScreenUi,
    colors: NaviampColors,
    actions: NaviampLibraryActions,
    mediaActions: NaviampMediaActions,
    viewport: NaviampTelevisionLibraryState,
    topNavigationFocusRequester: FocusRequester,
    entryFocusGeneration: Int? = null,
    onEntryFocusHandled: (Int) -> Unit = {},
) {
    NaviampLibrarySourcePicker(colors, screen.sourcePicker, actions)
    val view = screen.selectedView
    val catalog = screen.selectedCatalog
    val items = catalog.items
    val tracks = catalog.tracks
    val ids = if (view == NaviampLibraryView.Songs) tracks.map { it.id } else items.map { it.id }
    val titles = if (view == NaviampLibraryView.Songs) tracks.map { it.title } else items.map { it.title }
    val selectors = remember { NaviampLibraryView.entries.associateWith { FocusRequester() } }
    val refreshFocus = remember { FocusRequester() }
    val sourcesFocus = remember { FocusRequester() }
    val sortFocus = remember { FocusRequester() }
    val showShortcuts = view != NaviampLibraryView.Albums || catalog.albumSortOrder == LibraryAlbumSortOrder.Title
    val shortcuts = if (showShortcuts) televisionLibraryShortcuts() else emptyList()
    val letterFocus = remember { shortcuts.associateWith { FocusRequester() } }
    val letterListState = rememberLazyListState()
    val songFocus = remember(ids, view) { ids.associateWith { FocusRequester() } }
    val scope = rememberCoroutineScope()
    var songFocusJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var shortcutFocusJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val currentSongFocus by rememberUpdatedState(songFocus)
    val currentIds by rememberUpdatedState(ids)
    DisposableEffect(view, viewport) {
        onDispose {
            songFocusJob?.cancel()
            shortcutFocusJob?.cancel()
        }
    }
    var gridRequest by remember(view) { mutableStateOf<TelevisionGridFocusRequest?>(null) }
    var focusGeneration by remember { mutableIntStateOf(0) }
    var contentFocused by remember { mutableStateOf(false) }
    var letterFocused by remember { mutableStateOf(false) }
    var backKeyInProgress by remember { mutableStateOf(false) }

    fun focusSelector() {
        viewport.restoreContent = false
        selectors.getValue(view).requestFocus()
    }
    fun focusContent(index: Int? = televisionLibraryRestoreIndex(ids, viewport.focusedId(view))) {
        if (index == null || index !in ids.indices) return
        songFocusJob?.cancel()
        if (view == NaviampLibraryView.Songs) {
            val id = ids[index]
            if (currentSongFocus[id]?.requestFocus() == true) return
            songFocusJob = scope.launch {
                val listState = viewport.lists.listState(view)
                val focusedIndex = currentIds.indexOf(viewport.focusedId(view))
                if (focusedIndex >= 0 && kotlin.math.abs(index - focusedIndex) == 1) {
                    val visibleItems = listState.layoutInfo.visibleItemsInfo
                    val focusedItemSize = visibleItems.firstOrNull { it.index == focusedIndex }?.size
                        ?: visibleItems.firstOrNull()?.size
                        ?: 0
                    if (focusedItemSize > 0) {
                        val direction = if (index > focusedIndex) 1f else -1f
                        listState.animateScrollBy(focusedItemSize * 1.1f * direction)
                    }
                } else {
                    listState.scrollToItem(index)
                }
                repeat(5) {
                    withFrameNanos { }
                    if (id !in currentIds) return@launch
                    if (currentSongFocus[id]?.requestFocus() == true) return@launch
                }
            }
        } else gridRequest = TelevisionGridFocusRequest(index, ++focusGeneration)
    }
    fun focusShortcut(index: Int) {
        if (shortcutFocusJob?.isActive == true) return
        val target = index.coerceIn(shortcuts.indices)
        val requester = letterFocus.getValue(shortcuts[target])
        val visibleIndices = letterListState.layoutInfo.visibleItemsInfo.map { it.index }
        if (!televisionLibraryShortcutRequiresScroll(target, visibleIndices)) {
            requester.requestFocus()
        } else {
            shortcutFocusJob = scope.launch {
                letterListState.scrollToItem(target)
                withFrameNanos { }
                requester.requestFocus()
            }
        }
    }
    fun focusLetter() {
        if (!showShortcuts) return
        val index = televisionLibraryRestoreIndex(ids, viewport.focusedId(view)) ?: return
        val letter = app.naviamp.domain.library.libraryTitleLetter(titles[index])
        shortcuts.indexOf(letter).takeIf { it >= 0 }?.let(::focusShortcut)
    }
    val backToSelector = contentFocused || letterFocused
    NaviampSystemBackHandler(enabled = backToSelector) {
        backKeyInProgress = false
        focusSelector()
    }

    LaunchedEffect(view, ids.isNotEmpty()) {
        if (viewport.restoreContent) {
            if (ids.isNotEmpty()) focusContent()
            else if (!catalog.syncStatus.isSyncing) focusSelector()
        }
    }
    LaunchedEffect(entryFocusGeneration) {
        entryFocusGeneration?.let {
            withFrameNanos { }
            focusSelector()
            onEntryFocusHandled(it)
        }
    }
    LaunchedEffect(screen.jumpRequest) {
        val jump = screen.jumpRequest ?: return@LaunchedEffect
        if (viewport.consumeJump(jump, view)) {
            libraryLetterJumpIndex(titles, jump.letter).takeIf { it >= 0 }?.let { focusContent(it) }
        }
    }
    LaunchedEffect(view, ids.size, viewport.focusedId(view)) {
        val index = ids.indexOf(viewport.focusedId(view))
        if (index >= 0 && index >= ids.size - 8 && !catalog.syncStatus.isSyncing) actions.onLoadMore()
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize().testTag(TelevisionLibraryTestTag).onPreviewKeyEvent { event ->
            if (event.key != Key.Back && event.key != Key.Escape) return@onPreviewKeyEvent false
            when {
                event.type == KeyEventType.KeyDown && backToSelector -> {
                    backKeyInProgress = true
                    true
                }
                event.type == KeyEventType.KeyUp && backKeyInProgress -> {
                    backKeyInProgress = false
                    focusSelector()
                    true
                }
                else -> false
            }
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            NaviampLibraryView.entries.forEachIndexed { index, option ->
                val label = stringResource(when (option) {
                    NaviampLibraryView.Artists -> Res.string.library_view_artists
                    NaviampLibraryView.Albums -> Res.string.library_view_albums
                    NaviampLibraryView.Songs -> Res.string.library_view_songs
                })
                val selectionDescription = stringResource(if (option == view) Res.string.library_view_state_selected else Res.string.library_view_state_not_selected)
                TelevisionTextButton(label, colors, calmFocus = true, fontSize = 18.sp, onClick = {
                    if (option != view) {
                        viewport.restoreContent = viewport.focusedId(option) != null
                        actions.onViewChanged(option)
                    }
                }, modifier = Modifier
                    .focusRequester(selectors.getValue(option))
                    .testTag(TelevisionLibraryViewTagPrefix + option.name)
                    .semantics { selected = option == view; role = Role.Tab; stateDescription = selectionDescription }
                    .onFocusChanged { if (it.isFocused) viewport.restoreContent = false }
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                            Key.DirectionDown -> { focusContent(); true }
                            Key.DirectionUp -> { topNavigationFocusRequester.requestFocus(); true }
                            Key.DirectionRight -> {
                                if (index < NaviampLibraryView.entries.lastIndex) selectors.getValue(NaviampLibraryView.entries[index + 1]).requestFocus()
                                else sourcesFocus.requestFocus()
                                true
                            }
                            Key.DirectionLeft -> { selectors.getValue(NaviampLibraryView.entries[(index - 1).coerceAtLeast(0)]).requestFocus(); true }
                            else -> false
                        }
                    })
            }
            Spacer(Modifier.weight(1f))
            TelevisionTextButton(
                stringResource(Res.string.library_sources_choose),
                colors,
                calmFocus = true,
                onClick = actions.onOpenSourcePicker,
                modifier = Modifier.focusRequester(sourcesFocus).onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                        Key.DirectionLeft -> { selectors.getValue(NaviampLibraryView.Songs).requestFocus(); true }
                        Key.DirectionRight -> {
                            if (view == NaviampLibraryView.Albums) sortFocus.requestFocus() else refreshFocus.requestFocus()
                            true
                        }
                        Key.DirectionUp -> { topNavigationFocusRequester.requestFocus(); true }
                        Key.DirectionDown -> { focusContent(); true }
                        else -> false
                    }
                },
            )
            if (view == NaviampLibraryView.Albums) {
                val sortLabel = stringResource(
                    if (catalog.albumSortOrder == LibraryAlbumSortOrder.Title) {
                        Res.string.library_sort_recently_added
                    } else {
                        Res.string.library_sort_title
                    },
                )
                TelevisionTextButton(sortLabel, colors, calmFocus = true, onClick = {
                    actions.onAlbumSortOrderChanged(
                        if (catalog.albumSortOrder == LibraryAlbumSortOrder.Title) {
                            LibraryAlbumSortOrder.RecentlyAdded
                        } else {
                            LibraryAlbumSortOrder.Title
                        },
                    )
                }, modifier = Modifier.focusRequester(sortFocus).onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                        Key.DirectionLeft -> { sourcesFocus.requestFocus(); true }
                        Key.DirectionRight -> { refreshFocus.requestFocus(); true }
                        Key.DirectionUp -> { topNavigationFocusRequester.requestFocus(); true }
                        Key.DirectionDown -> { focusContent(); true }
                        else -> false
                    }
                })
            }
            val refreshModifier = Modifier.focusRequester(refreshFocus).onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                    Key.DirectionLeft -> {
                        if (view == NaviampLibraryView.Albums) sortFocus.requestFocus()
                        else sourcesFocus.requestFocus()
                        true
                    }
                    Key.DirectionRight -> true
                    Key.DirectionUp -> { topNavigationFocusRequester.requestFocus(); true }
                    Key.DirectionDown -> { focusContent(); true }
                    else -> false
                }
            }
            TelevisionTextButton(stringResource(Res.string.library_refresh), colors,
                enabled = !catalog.syncStatus.isSyncing, onClick = actions.onRefresh,
                modifier = refreshModifier)
        }
        NaviampLibraryLoadingStatus(colors, view, catalog)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (showShortcuts) LazyColumn(state = letterListState, modifier = Modifier.fillMaxHeight().width(54.dp).testTag(TelevisionLibraryShortcutRailTestTag).onFocusChanged {
                letterFocused = it.hasFocus
                if (it.hasFocus) viewport.restoreContent = false
            }.focusGroup()) {
                itemsIndexed(shortcuts, key = { _, letter -> letter }) { index, letter ->
                    val description = stringResource(Res.string.tv_library_jump_to_letter, letter.toString())
                    var focused by remember { mutableStateOf(false) }
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.height(36.dp).fillMaxWidth()
                        .background(if (focused) colors.primaryText else colors.controlSurface, RoundedCornerShape(4.dp))
                        .focusRequester(letterFocus.getValue(letter))
                        .testTag(TelevisionLibraryLetterTagPrefix + letter)
                        .semantics { contentDescription = description }
                        .onFocusChanged { focused = it.isFocused }
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                                Key.DirectionRight -> { focusContent(); true }
                                Key.DirectionLeft -> true
                                Key.DirectionUp -> { if (index == 0) focusSelector() else focusShortcut(index - 1); true }
                                Key.DirectionDown -> { focusShortcut(index + 1); true }
                                Key.Enter, Key.DirectionCenter, Key.Spacebar -> { actions.onJumpToLetter(letter); true }
                                else -> false
                            }
                        }.clickable { actions.onJumpToLetter(letter) }) {
                        Text(
                            letter.toString(),
                            color = if (focused) colors.background else colors.primaryText,
                            fontSize = 16.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        )
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight().onFocusChanged { contentFocused = it.hasFocus }.focusGroup()) {
                if (ids.isEmpty()) {
                    if (!catalog.syncStatus.isSyncing && catalog.pendingJump == null) Text(
                        stringResource(when (view) {
                            NaviampLibraryView.Artists -> Res.string.library_no_artists
                            NaviampLibraryView.Albums -> Res.string.library_no_albums
                            NaviampLibraryView.Songs -> Res.string.library_no_songs
                        }), color = colors.secondaryText, fontSize = 20.sp,
                    )
                } else key(view) {
                    if (view == NaviampLibraryView.Songs) {
                        LazyColumn(
                            state = viewport.lists.listState(view),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 32.dp),
                        ) {
                            itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                                TelevisionTrackRow(track, (index + 1).toString(), true, colors,
                                    onAction = { actions.onTrackAction(SharedTrackRowActionRequest(track, it)) },
                                    modifier = Modifier.focusRequester(songFocus.getValue(track.id))
                                        .testTag(TelevisionLibraryItemTagPrefix + track.id)
                                        .onFocusChanged { if (it.isFocused) viewport.record(view, track.id) }
                                        .onPreviewKeyEvent { event ->
                                            if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                                                Key.DirectionUp -> { if (index == 0) focusSelector() else focusContent(index - 1); true }
                                                Key.DirectionDown -> { if (index < tracks.lastIndex) focusContent(index + 1) else actions.onLoadMore(); true }
                                                Key.DirectionLeft -> { focusLetter(); true }
                                                else -> false
                                            }
                                        },
                                )
                            }
                        }
                    } else TelevisionMediaGrid(
                        items = items.map { item -> TelevisionMediaGridItem(
                            key = item.id, title = item.title, subtitle = item.subtitle, coverArtUrl = item.coverArtUrl,
                            artworkShape = if (view == NaviampLibraryView.Artists) TelevisionArtworkShape.Circle else TelevisionArtworkShape.RoundedSquare,
                            action = {
                                viewport.record(view, item.id)
                                mediaActions.onMediaItemAction(if (view == NaviampLibraryView.Artists) {
                                    item.artistActionRequest(NaviampArtistMediaCommand.Select)
                                } else item.albumActionRequest(NaviampArtistAlbumCommand.Select))
                            },
                        ) },
                        colors = colors,
                        focusRequest = gridRequest,
                        gridState = viewport.grid(view),
                        onItemFocused = { viewport.record(view, items[it].id) },
                        onLeftFromFirstColumn = ::focusLetter,
                        onUpFromFirstRow = ::focusSelector,
                        onEndReached = { if (!catalog.syncStatus.isSyncing) actions.onLoadMore() },
                        itemTagPrefix = TelevisionLibraryItemTagPrefix,
                    )
                }
            }
        }
    }
}

internal const val TelevisionLibraryTestTag = "television-library"
internal const val TelevisionLibraryViewTagPrefix = "television-library-view:"
internal const val TelevisionLibraryItemTagPrefix = "television-library-item:"
internal const val TelevisionLibraryShortcutRailTestTag = "television-library-shortcut-rail"
internal const val TelevisionLibraryLetterTagPrefix = "television-library-letter:"
