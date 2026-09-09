package app.naviamp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NaviampLibraryFocusUiTest {
    @Test
    fun loadingPanelStaysVisibleAboveAScrolledCatalogAndClearsWhenFinished() = runComposeUiTest {
        lateinit var screen: MutableState<NaviampLibraryScreenUi>
        lateinit var viewport: NaviampLibraryViewportState
        setContent {
            screen = remember { mutableStateOf(NaviampLibraryScreenUi(
                selectedView = NaviampLibraryView.Albums,
                albums = NaviampLibraryCatalogUi(items = List(100) {
                    SharedMediaItemUi("album-$it", "Album $it", "Artist")
                }),
            )) }
            viewport = rememberNaviampLibraryViewportState()
            Box(Modifier.height(400.dp)) {
                NaviampLibraryContent(NaviampColors(), screen.value, libraryActions {}, emptyMediaActions(), viewport)
            }
        }
        runOnIdle { viewport.listState(NaviampLibraryView.Albums).requestScrollToItem(25) }
        waitForIdle()
        runOnIdle { screen.value = screen.value.copy(albums = screen.value.albums.copy(pendingJump = 'M')) }
        onNodeWithText("Loading M…").assertIsDisplayed()
        runOnIdle { assertEquals(25, viewport.listState(NaviampLibraryView.Albums).firstVisibleItemIndex) }
        runOnIdle { screen.value = screen.value.copy(albums = screen.value.albums.copy(pendingJump = null)) }
        onNodeWithText("Loading M…").assertDoesNotExist()
        runOnIdle { assertEquals(25, viewport.listState(NaviampLibraryView.Albums).firstVisibleItemIndex) }
    }

    @Test
    fun albumJumpSkipsCurlyQuotedAndNumberedTitles() = runComposeUiTest {
        lateinit var screen: MutableState<NaviampLibraryScreenUi>
        lateinit var viewport: NaviampLibraryViewportState
        setContent {
            screen = remember { mutableStateOf(NaviampLibraryScreenUi(
                selectedView = NaviampLibraryView.Albums,
                albums = NaviampLibraryCatalogUi(items =
                    (listOf("’90s Rock Essentials", "10 000 Hz Legend", "25") +
                        List(20) { "G Album $it" }).mapIndexed { index, title ->
                        SharedMediaItemUi("album-$index", title, "Artist")
                    }),
            )) }
            viewport = rememberNaviampLibraryViewportState()
            Box(Modifier.height(400.dp)) {
                NaviampLibraryContent(NaviampColors(), screen.value, libraryActions {}, emptyMediaActions(), viewport)
            }
        }
        runOnIdle { screen.value = screen.value.copy(
            jumpRequest = NaviampLibraryJumpUi(NaviampLibraryView.Albums, 'G', 1)) }
        waitForIdle()
        onNodeWithText("G Album 0").assertIsDisplayed()
        runOnIdle { assertEquals(3, viewport.listState(NaviampLibraryView.Albums).firstVisibleItemIndex) }
    }

    @Test
    fun alphabetTargetsAreCenteredAndClickableAcrossTheirColumnInEveryView() = runComposeUiTest {
        val screen = mutableStateOf(libraryScreen())
        var requested: Char? = null
        setContent {
            Box(Modifier.height(600.dp)) {
                NaviampLibraryContent(NaviampColors(), screen.value,
                    libraryActions(onViewChanged = {}, onJumpToLetter = { requested = it }),
                    emptyMediaActions(), rememberNaviampLibraryViewportState())
            }
        }
        NaviampLibraryView.entries.forEach { view ->
            runOnIdle { screen.value = screen.value.copy(selectedView = view); requested = null }
            val letter = onNodeWithText("G").performScrollTo()
            val bounds = letter.fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.width >= 36f)
            letter.performTouchInput { click(androidx.compose.ui.geometry.Offset(2f, center.y)) }
            runOnIdle { assertEquals('G', requested); requested = null }
            letter.performTouchInput { click(androidx.compose.ui.geometry.Offset(width - 2f, center.y)) }
            runOnIdle { assertEquals('G', requested) }
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            letter.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            val glyph = layout.getBoundingBox(0)
            assertTrue(kotlin.math.abs(glyph.center.x - layout.size.width / 2f) <= 1f)
        }
    }

    @Test
    fun initialLoadingDoesNotClaimTheCatalogIsEmpty() = runComposeUiTest {
        lateinit var screen: MutableState<NaviampLibraryScreenUi>
        setContent {
            screen = remember { mutableStateOf(NaviampLibraryScreenUi(
                selectedView = NaviampLibraryView.Songs,
                songs = NaviampLibraryCatalogUi(syncStatus = NaviampLibrarySyncStatusUi(isSyncing = true)),
            )) }
            NaviampLibraryContent(NaviampColors(), screen.value, libraryActions {}, emptyMediaActions(), rememberNaviampLibraryViewportState())
        }
        onNodeWithText("Loading songs…").assertIsDisplayed()
        onNodeWithText("No library songs found.").assertDoesNotExist()
        runOnIdle { screen.value = screen.value.copy(songs = NaviampLibraryCatalogUi()) }
        onNodeWithText("Loading songs…").assertDoesNotExist()
        onNodeWithText("No library songs found.").assertIsDisplayed()
    }

    @Test
    fun aConsumedJumpDoesNotReplayAfterPagingOrDetailBack() = runComposeUiTest {
        lateinit var screen: MutableState<NaviampLibraryScreenUi>
        lateinit var visible: MutableState<Boolean>
        lateinit var viewport: NaviampLibraryViewportState
        fun rows(count: Int) = List(count) { SharedMediaItemUi("m-$it", "M Album $it", "Artist") }
        setContent {
            screen = remember { mutableStateOf(NaviampLibraryScreenUi(selectedView = NaviampLibraryView.Albums,
                albums = NaviampLibraryCatalogUi(items = rows(100)),
                jumpRequest = NaviampLibraryJumpUi(NaviampLibraryView.Albums, 'M', 1))) }
            visible = remember { mutableStateOf(true) }
            viewport = rememberNaviampLibraryViewportState()
            if (visible.value) Box(Modifier.height(300.dp)) {
                NaviampLibraryContent(NaviampColors(), screen.value, libraryActions {}, emptyMediaActions(), viewport)
            }
        }
        waitForIdle()
        runOnIdle { viewport.listState(NaviampLibraryView.Albums).requestScrollToItem(25) }
        waitForIdle()
        runOnIdle { screen.value = screen.value.copy(albums = screen.value.albums.copy(items = rows(150))) }
        waitForIdle()
        runOnIdle { assertEquals(25, viewport.listState(NaviampLibraryView.Albums).firstVisibleItemIndex) }
        runOnIdle { visible.value = false }
        runOnIdle { visible.value = true }
        waitForIdle()
        runOnIdle { assertEquals(25, viewport.listState(NaviampLibraryView.Albums).firstVisibleItemIndex) }
    }

    @Test
    fun replacingTheFocusedRowFallsBackToTheSelector() = runComposeUiTest {
        lateinit var screen: MutableState<NaviampLibraryScreenUi>
        setContent {
            screen = remember { mutableStateOf(libraryScreen()) }
            NaviampLibraryContent(NaviampColors(), screen.value, libraryActions {}, emptyMediaActions(), rememberNaviampLibraryViewportState())
        }
        onNodeWithText("Artist A").performSemanticsAction(SemanticsActions.RequestFocus)
        waitForIdle()
        runOnIdle { screen.value = screen.value.copy(artists = NaviampLibraryCatalogUi(
            items = listOf(SharedMediaItemUi("other", "Other artist", "Artist")))) }
        waitForIdle()
        onNodeWithText("Artists").assertIsFocused()
    }

    @Test
    fun quickIndexWaitsForTheServerBackedJumpWindowBeforeScrolling() = runComposeUiTest {
        lateinit var screenState: MutableState<NaviampLibraryScreenUi>
        lateinit var viewportState: NaviampLibraryViewportState
        var requestedLetter: Char? = null
        setContent {
            viewportState = rememberNaviampLibraryViewportState()
            screenState = remember {
                mutableStateOf(
                    NaviampLibraryScreenUi(
                        selectedView = NaviampLibraryView.Albums,
                        albums = NaviampLibraryCatalogUi(
                            items = List(100) { index ->
                                SharedMediaItemUi("z-$index", "Z Album $index", "Artist")
                            },
                        ),
                    ),
                )
            }
            Box(Modifier.height(300.dp)) {
                NaviampLibraryContent(
                    colors = NaviampColors(),
                    screen = screenState.value,
                    actions = libraryActions(
                        onViewChanged = {},
                        onJumpToLetter = { requestedLetter = it },
                    ),
                    mediaActions = emptyMediaActions(),
                    viewportState = viewportState,
                )
            }
        }

        onNodeWithText("M").performScrollTo().performClick()
        runOnIdle {
            assertEquals('M', requestedLetter)
            assertEquals(0, viewportState.listState(NaviampLibraryView.Albums).firstVisibleItemIndex)
            screenState.value = screenState.value.copy(
                albums = NaviampLibraryCatalogUi(
                    items = List(100) { index ->
                        SharedMediaItemUi("m-$index", "M Album $index", "Artist")
                    },
                ),
                jumpRequest = NaviampLibraryJumpUi(NaviampLibraryView.Albums, 'M', 1),
            )
        }
        waitForIdle()

        val searchBounds = onNodeWithText("Search library albums").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val targetBounds = onNodeWithText("M Album 0").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(targetBounds.top >= searchBounds.bottom, "The jump target must remain below the pinned search")
    }

    @Test
    fun eachViewRestoresItsFocusedRowAndBackRestoresTheOriginatingRow() = runComposeUiTest {
        lateinit var libraryVisible: MutableState<Boolean>
        lateinit var viewportState: NaviampLibraryViewportState
        setContent {
            val viewport = rememberNaviampLibraryViewportState()
            viewportState = viewport
            var screen by remember { mutableStateOf(libraryScreen()) }
            libraryVisible = remember { mutableStateOf(true) }
            if (libraryVisible.value) {
                NaviampLibraryContent(
                    colors = NaviampColors(),
                    screen = screen,
                    actions = libraryActions { screen = screen.copy(selectedView = it) },
                    mediaActions = emptyMediaActions(),
                    viewportState = viewport,
                )
            } else {
                Box(Modifier)
            }
        }

        onNodeWithText("Artist A").performSemanticsAction(SemanticsActions.RequestFocus)
        onNodeWithText("Artist A").assertIsFocused()
        runOnIdle {
            assertEquals(libraryItemFocusTarget("artist-a"), viewportState.focusedTarget(NaviampLibraryView.Artists))
        }

        onNodeWithText("Albums").performClick()
        onNodeWithText("Album A").performSemanticsAction(SemanticsActions.RequestFocus)
        onNodeWithText("Album A").assertIsFocused()
        runOnIdle {
            assertEquals(libraryItemFocusTarget("album-a"), viewportState.focusedTarget(NaviampLibraryView.Albums))
        }

        onNodeWithText("Artists").performClick()
        waitForIdle()
        onNodeWithText("Artist A").assertIsFocused()

        onNodeWithText("Albums").performClick()
        waitForIdle()
        onNodeWithText("Album A").assertIsFocused()

        runOnIdle { libraryVisible.value = false }
        runOnIdle { libraryVisible.value = true }
        waitForIdle()
        onNodeWithText("Album A").assertIsFocused()
    }

    @Test
    fun selectorExposesTranslatedSelectionState() = runComposeUiTest {
        setContent {
            val viewport = rememberNaviampLibraryViewportState()
            var screen by remember { mutableStateOf(libraryScreen()) }
            NaviampLibraryContent(
                colors = NaviampColors(),
                screen = screen,
                actions = libraryActions { screen = screen.copy(selectedView = it) },
                mediaActions = emptyMediaActions(),
                viewportState = viewport,
            )
        }

        onNodeWithText("Artists").assertIsSelected().assert(hasStateDescription("Selected"))
        onNodeWithText("Songs").performClick().assertIsSelected()
    }

    @Test
    fun keyboardAndDpadTraverseSelectorInViewOrderThenEnterSearch() = runComposeUiTest {
        setContent {
            val viewport = rememberNaviampLibraryViewportState()
            var screen by remember { mutableStateOf(libraryScreen()) }
            NaviampLibraryContent(
                colors = NaviampColors(),
                screen = screen,
                actions = libraryActions { screen = screen.copy(selectedView = it) },
                mediaActions = emptyMediaActions(),
                viewportState = viewport,
            )
        }

        onNodeWithText("Artists")
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        onNodeWithText("Albums").assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        onNodeWithText("Albums").assertIsSelected().performKeyInput { pressKey(Key.DirectionDown) }
        onNodeWithText("Search library albums").assertIsFocused()
    }
}

private fun libraryScreen() = NaviampLibraryScreenUi(
    artists = NaviampLibraryCatalogUi(
        items = listOf(SharedMediaItemUi(id = "artist-a", title = "Artist A", subtitle = "Artist")),
    ),
    albums = NaviampLibraryCatalogUi(
        items = listOf(SharedMediaItemUi(id = "album-a", title = "Album A", subtitle = "Artist A")),
    ),
    songs = NaviampLibraryCatalogUi(
        tracks = listOf(SharedTrackRowUi(id = "song-a", title = "Song A", subtitle = "Artist A")),
    ),
)

private fun libraryActions(
    onJumpToLetter: (Char) -> Unit = {},
    onViewChanged: (NaviampLibraryView) -> Unit,
) = NaviampLibraryActions(
    onViewChanged = onViewChanged,
    onQueryChanged = {},
    onRefresh = {},
    onLoadMore = {},
    onJumpToLetter = onJumpToLetter,
    onTrackAction = {},
)

private fun emptyMediaActions() = NaviampMediaActions(
    onTrackAction = {},
    onMediaItemAction = {},
)
