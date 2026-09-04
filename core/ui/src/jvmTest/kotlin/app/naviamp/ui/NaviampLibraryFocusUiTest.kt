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
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NaviampLibraryFocusUiTest {
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

        onNodeWithText("M").performClick()
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

        runOnIdle {
            assertEquals(2, viewportState.listState(NaviampLibraryView.Albums).firstVisibleItemIndex)
        }
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
