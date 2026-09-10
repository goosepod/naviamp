package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NaviampTelevisionLibraryUiTest {
    @Test fun viewSwitchingDetailReturnAndSongActionsPreserveFocus() = runDesktopComposeUiTest(1280, 720) {
        val screen = mutableStateOf(screen())
        val detailOpen = mutableStateOf(false)
        val selections = mutableListOf<NaviampMediaItemActionRequest>()
        val trackActions = mutableListOf<SharedTrackRowActionRequest>()
        setContent {
            val viewport = rememberNaviampTelevisionLibraryState()
            if (detailOpen.value) Text("Details") else LibraryFixture(screen, viewport,
                onMedia = { selections += it; detailOpen.value = true }, onTrack = trackActions::add)
        }
        onNodeWithTag(viewTag(NaviampLibraryView.Artists)).performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput { pressKey(Key.DirectionRight); pressKey(Key.Enter) }
        onNodeWithTag(viewTag(NaviampLibraryView.Albums)).assertIsSelected()
            .performKeyInput { pressKey(Key.DirectionDown) }
        onNodeWithTag(itemTag("album-0")).assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        onNodeWithTag(itemTag("album-1")).assertIsFocused().performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput { pressKey(Key.Enter) }
        runOnIdle {
            assertEquals("album-1", selections.single().item.id)
            screen.value = screen.value.copy(albums = screen.value.albums.copy(
                items = listOf(SharedMediaItemUi("inserted", "Inserted", "Artist")) + screen.value.albums.items,
            ))
            detailOpen.value = false
        }
        onNodeWithTag(itemTag("album-1")).assertIsFocused().performKeyInput { pressKey(Key.Escape) }
        onNodeWithTag(viewTag(NaviampLibraryView.Albums)).assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight); pressKey(Key.Enter) }
        onNodeWithTag(viewTag(NaviampLibraryView.Songs)).assertIsSelected().performKeyInput { pressKey(Key.DirectionDown) }
        onNodeWithTag(itemTag("song-0")).assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        runOnIdle { assertEquals(SharedTrackRowAction.Select, trackActions.last().action) }
        onNodeWithTag(itemTag("song-0")).performKeyInput { pressKey(Key.DirectionRight) }
        onNodeWithText("Track actions").assertIsDisplayed()
        onNodeWithText("Play After Current Group").performClick()
        onNodeWithTag(itemTag("song-0")).assertIsFocused()
        runOnIdle { assertEquals(SharedTrackRowAction.PlayNext, trackActions.last().action) }
        onNodeWithTag(itemTag("song-0")).performKeyInput { pressKey(Key.Escape) }
        onNodeWithTag(viewTag(NaviampLibraryView.Albums)).performClick()
        onNodeWithTag(itemTag("album-1")).assertIsFocused()
    }

    @Test fun queriesEmptyStatesAndPendingJumpsStayScopedToTheSelectedView() = runDesktopComposeUiTest(1280, 720) {
        val screen = mutableStateOf(screen().copy(selectedView = NaviampLibraryView.Songs))
        var requestedLetter: Char? = null
        var loads = 0
        setContent { LibraryFixture(screen, rememberNaviampTelevisionLibraryState(),
            onLoadMore = { loads++ }, onJump = { requestedLetter = it }) }
        onNodeWithTag(TelevisionLibrarySearchTag).performTextInput("missing")
        onNodeWithText("No library songs match.").assertIsDisplayed()
        onNodeWithTag(viewTag(NaviampLibraryView.Albums)).performClick()
        onNodeWithTag(TelevisionLibrarySearchTag).assertTextContains("")
        onNodeWithTag(viewTag(NaviampLibraryView.Songs)).performClick()
        onNodeWithTag(TelevisionLibrarySearchTag).assertTextContains("missing").performTextClearance()
        onNodeWithTag(TelevisionLibraryLetterTagPrefix + 'A')
            .performSemanticsAction(SemanticsActions.RequestFocus)
        repeat(25) {
            onRoot().performKeyInput { pressKey(Key.DirectionDown) }
            waitForIdle()
        }
        onNodeWithTag(TelevisionLibraryLetterTagPrefix + 'Z').assertIsDisplayed().assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }
        runOnIdle {
            assertEquals('Z', requestedLetter)
            screen.value = screen.value.copy(songs = screen.value.songs.copy(pendingJump = 'Z'))
        }
        onNodeWithText("Loading Z…").assertIsDisplayed()
        onNodeWithTag(TelevisionLibraryLetterTagPrefix + 'Z').assertIsFocused()
        runOnIdle {
            screen.value = screen.value.copy(
                songs = screen.value.songs.copy(pendingJump = null,
                    tracks = screen.value.songs.tracks + SharedTrackRowUi("z", "Zebra", "Artist")),
                jumpRequest = NaviampLibraryJumpUi(NaviampLibraryView.Songs, 'Z', 1),
            )
        }
        onNodeWithTag(itemTag("z")).assertIsFocused()
        runOnIdle { assertTrue(loads > 0) }
        onNodeWithTag(itemTag("z")).performKeyInput { pressKey(Key.Escape) }
        onNodeWithTag(viewTag(NaviampLibraryView.Albums)).performClick()
        runOnIdle { screen.value = screen.value.copy(jumpRequest = NaviampLibraryJumpUi(NaviampLibraryView.Songs, 'A', 2)) }
        onNodeWithTag(viewTag(NaviampLibraryView.Albums)).assertIsFocused()
    }

    @Test fun appendingAlbumsKeepsTheCurrentFocusInsteadOfReplayingEntry() = runDesktopComposeUiTest(1280, 720) {
        val screen = mutableStateOf(screen().copy(selectedView = NaviampLibraryView.Albums))
        setContent { LibraryFixture(screen, rememberNaviampTelevisionLibraryState()) }
        onNodeWithTag(viewTag(NaviampLibraryView.Albums)).performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput { pressKey(Key.DirectionDown) }
        onNodeWithTag(itemTag("album-0")).assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        onNodeWithTag(itemTag("album-1")).assertIsFocused()
        runOnIdle {
            screen.value = screen.value.copy(albums = screen.value.albums.copy(
                items = screen.value.albums.items + SharedMediaItemUi("next-page", "Next page", "Artist"),
            ))
        }
        onNodeWithTag(itemTag("album-1")).assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        onNodeWithTag(itemTag("album-2")).assertIsFocused()
    }

    @Test fun anEmptySearchAfterBrowsingKeepsKeyboardFocusInTheSearchField() = runDesktopComposeUiTest(1280, 720) {
        val screen = mutableStateOf(screen().copy(selectedView = NaviampLibraryView.Songs))
        setContent { LibraryFixture(screen, rememberNaviampTelevisionLibraryState()) }
        onNodeWithTag(viewTag(NaviampLibraryView.Songs)).performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput { pressKey(Key.DirectionDown) }
        onNodeWithTag(itemTag("song-0")).assertIsFocused().performKeyInput { pressKey(Key.DirectionUp) }
        onNodeWithTag(TelevisionLibrarySearchTag).assertIsFocused().performTextInput("missing")
        onNodeWithText("No library songs match.").assertIsDisplayed()
        onNodeWithTag(TelevisionLibrarySearchTag).assertIsFocused().performTextClearance()
        onNodeWithTag(TelevisionLibrarySearchTag).assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        onNodeWithTag(itemTag("song-0")).assertIsFocused()
    }

    @Test fun libraryAt720p() = checkSize(1280, 720)
    @Test fun libraryAt1080p() = checkSize(1920, 1080)
    @Test fun libraryAtNative4k() = checkSize(3840, 2160)
    @Test fun libraryAt4kDoubleDensity() = checkSize(3840, 2160, 2f)

    private fun checkSize(width: Int, height: Int, density: Float = 1f) = runDesktopComposeUiTest(width, height) {
        val screen = mutableStateOf(screen().copy(selectedView = NaviampLibraryView.Albums))
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(density)) {
                Box(Modifier.fillMaxSize().background(NaviampColors.Dark.background)) {
                    LibraryFixture(screen, rememberNaviampTelevisionLibraryState())
                }
            }
        }
        onNodeWithTag(viewTag(NaviampLibraryView.Albums)).performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput { pressKey(Key.DirectionDown) }
        onNodeWithTag(itemTag("album-0")).assertIsFocused().assertIsDisplayed()
        NaviampLibraryView.entries.forEach { onNodeWithTag(viewTag(it)).assertIsDisplayed() }
        onNodeWithTag(TelevisionLibraryShortcutRailTestTag).assertIsDisplayed()
        onNodeWithTag(TelevisionLibraryLetterTagPrefix + 'A').assertIsDisplayed()
        val pixels = onRoot().captureToImage().toPixelMap()
        val snapshot = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until height) for (x in 0 until width) snapshot.setRGB(x, y, pixels[x, y].toArgb())
        val output = File("build/reports/television-library/library-${width}x$height-${density}x.png")
        output.parentFile.mkdirs()
        ImageIO.write(snapshot, "png", output)
    }

    @Composable
    private fun LibraryFixture(
        screen: MutableState<NaviampLibraryScreenUi>,
        viewport: NaviampTelevisionLibraryState,
        onMedia: (NaviampMediaItemActionRequest) -> Unit = {},
        onTrack: (SharedTrackRowActionRequest) -> Unit = {},
        onLoadMore: () -> Unit = {},
        onJump: (Char) -> Unit = {},
    ) {
        val colors = NaviampColors.Dark
        MaterialTheme(colorScheme = darkColorScheme(background = colors.background, surface = colors.controlSurface,
            primary = colors.accent, onPrimary = colors.onAccent, onBackground = colors.primaryText, onSurface = colors.primaryText),
            typography = rememberNaviampTypography()) {
        TelevisionLibrary(screen.value, NaviampColors.Dark, NaviampLibraryActions(
            onViewChanged = { screen.value = screen.value.copy(selectedView = it) },
            onQueryChanged = { query ->
                val value = screen.value
                screen.value = when (value.selectedView) {
                    NaviampLibraryView.Artists -> value.copy(artists = value.artists.copy(query = query))
                    NaviampLibraryView.Albums -> value.copy(albums = value.albums.copy(query = query))
                    NaviampLibraryView.Songs -> value.copy(songs = value.songs.copy(query = query))
                }
            }, onRefresh = {}, onLoadMore = onLoadMore, onJumpToLetter = onJump, onTrackAction = onTrack,
        ), NaviampMediaActions({}, onMedia), viewport, {}, {}, FocusRequester())
        }
    }

    private fun screen() = NaviampLibraryScreenUi(
        artists = NaviampLibraryCatalogUi(items = List(30) { SharedMediaItemUi("artist-$it", "Artist $it", "Artist") }),
        albums = NaviampLibraryCatalogUi(items = List(30) { SharedMediaItemUi("album-$it", "Album $it", "Artist") }),
        songs = NaviampLibraryCatalogUi(tracks = List(30) { SharedTrackRowUi("song-$it", "Song $it", "Artist") }),
    )
    private fun viewTag(view: NaviampLibraryView) = TelevisionLibraryViewTagPrefix + view.name
    private fun itemTag(id: String) = TelevisionLibraryItemTagPrefix + id
}
