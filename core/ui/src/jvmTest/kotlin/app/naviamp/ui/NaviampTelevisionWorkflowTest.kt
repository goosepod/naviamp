package app.naviamp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import app.naviamp.domain.media.AlbumReleaseSection
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class NaviampTelevisionWorkflowTest {
    @Test fun remoteDirectionsNeverAppendTheirDeviceHubHidCharacters() {
        for (key in listOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight, Key.DirectionCenter, Key.Enter)) {
            assertNull(televisionSearchKeyboardCharacter(key, 'U'.code))
        }
        assertEquals('u', televisionSearchKeyboardCharacter(Key.U, 'u'.code))
        assertEquals(' ', televisionSearchKeyboardCharacter(Key.Spacebar, ' '.code))
    }

    @Test fun tvSearchEditorAcceptsRemoteKeysAndOptionalSystemInputAndRestoresQueryFocus() = runComposeUiTest {
        val screen = mutableStateOf(NaviampSearchScreenUi(query = "Existing"))
        val requests = mutableListOf<NaviampTelevisionTextEntryRequest>()
        val submitted = mutableListOf<String>()
        val editor = NaviampTelevisionTextEntry { request ->
            requests += request
            "New query"
        }
        setContent {
            CompositionLocalProvider(LocalNaviampTelevisionTextEntry provides editor) {
                TelevisionSearch(
                    screen.value, NaviampColors.Dark,
                    NaviampSearchActions(
                        { screen.value = screen.value.copy(query = it) },
                        { submitted += screen.value.query },
                        {},
                    ),
                    NaviampMediaActions({}, {}),
                    entryFocusGeneration = 1,
                )
            }
        }
        val field = onNodeWithTag("television-search-query")
        field.assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        onNodeWithText("a").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        onNodeWithText("Existinga").assertIsDisplayed()
        onNodeWithText("Cancel").performClick()
        assertTrue(submitted.isEmpty())
        field.assertIsFocused().assert(hasText("Existing")).performKeyInput { pressKey(Key.DirectionCenter) }
        onNodeWithText("System keyboard").performClick()
        onNodeWithText("New query").assertIsDisplayed()
        onNode(hasText("Search") and hasAnyAncestor(hasTestTag(TelevisionSearchEditorTestTag))).performClick()
        assertEquals(listOf("Existing"), requests.map { it.value })
        assertEquals(listOf("New query"), submitted)
        field.assertIsFocused().assert(hasText("New query"))
    }

    @Test fun hardwareReturnActivatesTheFocusedEditorActionInsteadOfSubmitting() = runComposeUiTest {
        val screen = mutableStateOf(NaviampSearchScreenUi(query = "ab"))
        val submitted = mutableListOf<String>()
        setContent {
            CompositionLocalProvider(LocalNaviampTelevisionTextEntry provides NaviampTelevisionTextEntry { null }) {
                TelevisionSearch(
                    screen.value, NaviampColors.Dark,
                    NaviampSearchActions(
                        { screen.value = screen.value.copy(query = it) },
                        { submitted += screen.value.query },
                        {},
                    ),
                    NaviampMediaActions({}, {}),
                    entryFocusGeneration = 1,
                )
            }
        }
        val field = onNodeWithTag("television-search-query")
        field.assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        val editor = hasAnyAncestor(hasTestTag(TelevisionSearchEditorTestTag))
        onNode(hasText("Backspace") and editor).requestFocus().performKeyInput { pressKey(Key.Enter) }
        onNode(hasText("a") and !hasClickAction() and editor).assertIsDisplayed()
        onNode(hasText("Space") and editor).requestFocus().performKeyInput { pressKey(Key.NumPadEnter) }
        onNode(hasText("a ") and !hasClickAction() and editor, useUnmergedTree = true).assertExists()
        onNode(hasText("Cancel") and editor).requestFocus().performKeyInput { pressKey(Key.Enter) }
        onNodeWithTag(TelevisionSearchEditorTestTag).assertDoesNotExist()
        assertTrue(submitted.isEmpty())
        field.assertIsFocused().assert(hasText("ab"))
        field.performKeyInput { pressKey(Key.DirectionCenter) }
        onNode(hasText("Search") and editor).requestFocus().performKeyInput { pressKey(Key.Enter) }
        assertEquals(listOf("ab"), submitted)
    }

    @Test fun hardwareReturnSubmitsSearchAndSelectsTheFocusedResult() = runComposeUiTest {
        val album = SharedMediaItemUi("album", "Fixture album", "Artist")
        val screen = mutableStateOf(NaviampSearchScreenUi())
        val submitted = mutableListOf<String>()
        val selected = mutableListOf<String>()
        setContent {
            CompositionLocalProvider(LocalNaviampTelevisionTextEntry provides NaviampTelevisionTextEntry { null }) {
                TelevisionSearch(
                    screen.value, NaviampColors.Dark,
                    NaviampSearchActions(
                        { screen.value = screen.value.copy(query = it) },
                        {
                            submitted += screen.value.query
                            screen.value = screen.value.copy(results = SharedSearchResultsUi(albums = listOf(album)))
                        },
                        {},
                    ),
                    NaviampMediaActions({}, { selected += it.item.id }),
                    entryFocusGeneration = 1,
                )
            }
        }
        val field = onNodeWithTag("television-search-query")
        field.assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        onNodeWithText("a").assertIsFocused().performKeyInput { pressKey(Key.NumPadEnter) }
        assertTrue(submitted.isEmpty())
        onNode(hasText("a") and !hasClickAction()).assertDoesNotExist()
        val remoteLetter = onNode(hasText("a") and hasClickAction())
        remoteLetter.assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        onNode(hasText("a") and !hasClickAction()).assertIsDisplayed()
        remoteLetter.performKeyInput { pressKey(Key.Enter) }
        assertEquals(listOf("a"), submitted)
        onNodeWithTag(TelevisionSearchEditorTestTag).assertDoesNotExist()
        field.assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        onNode(hasClickAction() and hasAnyDescendant(hasText("Fixture album")))
            .assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        assertEquals(listOf("album"), selected)
    }

    @Test fun artistDetailRestoresTheAlbumCardWhenReturningFromItsDetail() = runDesktopComposeUiTest(1280, 720) {
        val artist = SharedMediaItemUi("artist", "Artist", "")
        val album = SharedMediaItemUi("album", "Album", "Artist")
        val single = SharedMediaItemUi("single", "Single", "Artist")
        val screen = NaviampArtistDetailScreenUi(
            selectedArtist = artist,
            detail = SharedArtistDetailUi(artist, listOf(album, single), albumSections = listOf(
                SharedAlbumSectionUi(AlbumReleaseSection.Albums, listOf(album)),
                SharedAlbumSectionUi(AlbumReleaseSection.Singles, listOf(single)),
            )),
        )
        val opened = mutableStateOf<String?>(null)
        setContent {
            val viewport = rememberSaveableStateHolder()
            if (opened.value == null) viewport.SaveableStateProvider(artist.id) {
                TelevisionArtistDetail(screen, NaviampColors.Dark,
                    NaviampArtistDetailActions({}, {}, { opened.value = it.album.id }, {}),
                    topNavigationFocusRequester = remember { FocusRequester() },
                )
            }
        }
        val card = onNode(hasClickAction() and hasAnyDescendant(hasText("Single")))
        card.assertIsDisplayed().performClick()
        assertEquals(single.id, opened.value)
        runOnIdle { opened.value = null }
        card.assertIsDisplayed().assertIsFocused()
    }

    @Test fun searchSubmissionResultsBackAndReentry() = runDesktopComposeUiTest(1280, 720) {
        mainClock.autoAdvance = false
        val screen = mutableStateOf(NaviampSearchScreenUi())
        val entry = mutableStateOf(1)
        val mounted = mutableStateOf(true)
        val back = NaviampSystemBackDispatcher()
        var submissions = 0
        var selected: String? = null
        setContent {
            CompositionLocalProvider(LocalNaviampSystemBackDispatcher provides back) {
                if (mounted.value) TelevisionSearch(screen.value, NaviampColors.Dark,
                    NaviampSearchActions({ screen.value = screen.value.copy(query = it) }, {
                        submissions++
                        screen.value = screen.value.copy(searching = true)
                    }, { screen.value = NaviampSearchScreenUi() }),
                    NaviampMediaActions({ selected = it.track.id }, { selected = it.item.id }),
                    entryFocusGeneration = entry.value)
            }
        }
        mainClock.advanceTimeBy(200)
        onNode(hasSetTextAction()).assertIsFocused().performTextInput("Fixture")
        onNode(hasSetTextAction()).performImeAction()
        assertEquals(1, submissions)
        assertEquals("Fixture", screen.value.query)
        runOnIdle { screen.value = screen.value.copy(searching = false, results = SharedSearchResultsUi(
            albums = listOf(SharedMediaItemUi("album", "Fixture album", "Artist")))) }
        mainClock.advanceTimeBy(200)
        onNode(hasSetTextAction()).performKeyInput { pressKey(Key.DirectionDown) }
        mainClock.advanceTimeBy(400)
        onNodeWithText("Fixture album").assertIsDisplayed().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals("album", selected)
        runOnIdle { requireNotNull(back.currentHandler).invoke() }
        mainClock.advanceTimeBy(300)
        onNode(hasSetTextAction()).assertIsFocused()
        assertEquals("Fixture", screen.value.query)
        runOnIdle { mounted.value = false }
        mainClock.advanceTimeBy(200)
        onNode(hasSetTextAction()).assertDoesNotExist()
        runOnIdle { mounted.value = true; entry.value++ }
        mainClock.advanceTimeBy(200)
        onNode(hasSetTextAction()).assertIsFocused()
        assertEquals(1, submissions, "Route re-entry must not submit again")
        runOnIdle { screen.value = screen.value.copy(results = SharedSearchResultsUi(), status = "No matches") }
        mainClock.advanceTimeBy(200)
        onNodeWithText("Fixture album").assertDoesNotExist()
        onNodeWithText("No matches").assertIsDisplayed()
    }

    @Test fun radioCreateValidationEditCancelAndDelete() = runDesktopComposeUiTest(1280, 720) {
        val station = NaviampInternetRadioStationUi(SharedMediaItemUi("radio", "Station", "Radio"), "https://fixture.invalid/live")
        val saved = mutableListOf<NaviampInternetRadioStationEditUi>()
        val actions = mutableListOf<StationRowActionRequest>()
        setContent { TelevisionInternetRadio(NaviampInternetRadioScreenUi(stations = listOf(station)), NaviampColors.Dark,
            NaviampInternetRadioActions({}, actions::add, saved::add), remember { FocusRequester() }) }
        onNodeWithText("New station").performClick()
        onNodeWithText("Save").assertIsNotEnabled()
        onNodeWithText("Name").performTextInput("  New station  ")
        onNodeWithText("Stream URL").performTextInput(" https://fixture.invalid/new ")
        onNodeWithText("Save").assertIsEnabled().performClick()
        assertEquals("New station", saved.single().name)
        assertEquals("https://fixture.invalid/new", saved.single().streamUrl)
        assertNull(saved.single().id)
        onNode(hasAnyDescendant(hasText("Station")) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Focused)).performSemanticsAction(SemanticsActions.RequestFocus).performKeyInput { pressKey(Key.DirectionRight) }
        onNodeWithText("Edit").assertIsFocused().performClick()
        onNodeWithText("Name").performTextReplacement("Changed")
        onNodeWithText("Cancel").performClick()
        assertEquals(1, saved.size)
        onNode(hasAnyDescendant(hasText("Station")) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Focused)).performSemanticsAction(SemanticsActions.RequestFocus).performKeyInput { pressKey(Key.DirectionRight) }
        onNodeWithText("Edit").performClick()
        onNodeWithText("Name").performTextReplacement("Updated")
        onNodeWithText("Save").performClick()
        assertEquals("radio", saved.last().id)
        assertEquals("Updated", saved.last().name)
        onNode(hasAnyDescendant(hasText("Station")) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Focused)).performSemanticsAction(SemanticsActions.RequestFocus).performKeyInput { pressKey(Key.DirectionRight) }
        onNodeWithText("Delete").performClick()
        onNodeWithText("Cancel").assertIsFocused().performClick()
        assertTrue(actions.isEmpty())
        onNode(hasAnyDescendant(hasText("Station")) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Focused)).performSemanticsAction(SemanticsActions.RequestFocus).performKeyInput { pressKey(Key.DirectionRight) }
        onNodeWithText("Delete").performClick()
        onNodeWithText("Delete").performClick()
        assertEquals(StationRowAction.Delete, actions.single().action)
    }

    @Test fun radioEmptyLoadingAndFailedRefreshStayUsable() = runDesktopComposeUiTest(1280, 720) {
        val screen = mutableStateOf(NaviampInternetRadioScreenUi())
        var refreshes = 0
        val saves = mutableListOf<NaviampInternetRadioStationEditUi>()
        setContent { TelevisionInternetRadio(screen.value, NaviampColors.Dark,
            NaviampInternetRadioActions({ refreshes++; screen.value = screen.value.copy(refreshing = true) }, {}, saves::add),
            remember { FocusRequester() }, entryFocusGeneration = 1) }
        onNodeWithText("New station").assertIsFocused()
        onNodeWithText("Refresh").performClick()
        assertEquals(1, refreshes)
        onNodeWithText("Refresh").assertIsNotEnabled()
        runOnIdle { screen.value = screen.value.copy(refreshing = false, status = "Fixture unavailable") }
        onNodeWithText("Fixture unavailable").assertIsDisplayed()
        onNodeWithText("Refresh").assertIsEnabled()
        onNodeWithText("New station").performClick()
        onNodeWithText("Cancel").performClick()
        assertTrue(saves.isEmpty())
    }

    @Test fun lyricsTimingOffsetTrackChangeAndUnavailableState() = runDesktopComposeUiTest(1280, 720) {
        mainClock.autoAdvance = false
        val now = mutableStateOf(NowPlayingUi(id = "one", title = "Track", subtitle = "Artist", stateLabel = "Paused",
            lyricsVisible = true, lyricsAvailable = true, positionSeconds = 1.0,
            lyricsLines = listOf(NaviampLyricLineUi(0, "First line"), NaviampLyricLineUi(2000, "Second line"))))
        setContent { Box(Modifier.fillMaxSize()) { TelevisionNowPlaying(now.value, null, NaviampColors.Dark,
            actions = NaviampNowPlayingActions({}, {}, {}, {}, {}, {}, {}), interactive = false, onClose = {}, onOpenSettings = {}) } }
        mainClock.advanceTimeBy(400)
        onNodeWithText("First line").assertIsSelected()
        runOnIdle { now.value = now.value.copy(positionSeconds = 3.0) }
        mainClock.advanceTimeBy(500)
        onNodeWithText("Second line").assertIsSelected().assertIsDisplayed()
        runOnIdle { now.value = now.value.copy(lyricsOffsetMillis = 2000) }
        mainClock.advanceTimeBy(500)
        onNodeWithText("First line").assertIsSelected()
        runOnIdle { now.value = now.value.copy(id = "two", lyricsLines = emptyList(), lyricsStatus = "Loading lyrics") }
        mainClock.advanceTimeBy(500)
        onNodeWithText("Loading lyrics").assertIsDisplayed()
        onNodeWithText("First line").assertDoesNotExist()
        runOnIdle { now.value = now.value.copy(lyricsStatus = null) }
        mainClock.advanceTimeBy(200)
        onNodeWithText("Lyrics are not available.").assertDoesNotExist()
        onNodeWithContentDescription("Pause").assertDoesNotExist()
    }
    @Test fun lyricsScrollToPlaybackAndResetForAnotherTrack() = runDesktopComposeUiTest(1280, 720) {
        mainClock.autoAdvance = false
        val now = mutableStateOf(NowPlayingUi(id = "long", title = "Track", subtitle = "Artist", stateLabel = "Paused",
            lyricsVisible = true, lyricsAvailable = true, positionSeconds = 1.0,
            lyricsLines = (0..30).map { NaviampLyricLineUi(it * 5000L, "Verse $it") }))
        setContent { TelevisionNowPlaying(now.value, null, NaviampColors.Dark,
            actions = NaviampNowPlayingActions({}, {}, {}, {}, {}, {}, {}), interactive = false, onClose = {}, onOpenSettings = {}) }
        mainClock.advanceTimeBy(500)
        onNodeWithText("Verse 0").assertIsDisplayed().assertIsSelected()
        runOnIdle { now.value = now.value.copy(positionSeconds = 100.0) }
        mainClock.advanceTimeBy(2000)
        onNodeWithText("Verse 20").assertIsDisplayed().assertIsSelected()
        runOnIdle { now.value = now.value.copy(id = "new", positionSeconds = 0.0,
            lyricsLines = listOf(NaviampLyricLineUi(null, "New plain lyric"))) }
        mainClock.advanceTimeBy(500)
        onNodeWithText("New plain lyric").assertIsDisplayed()
        onNodeWithText("Verse 20").assertDoesNotExist()
    }

    @Test fun remoteLyricsToggleKeepsButtonFocus() = runDesktopComposeUiTest(1280, 720) {
        mainClock.autoAdvance = false
        val now = mutableStateOf(NowPlayingUi(id = "track", title = "Track", subtitle = "Artist", stateLabel = "Paused",
            lyricsAvailable = true, lyricsLines = listOf(NaviampLyricLineUi(null, "Plain lyric"))))
        var toggles = 0
        setContent { TelevisionNowPlaying(now.value, null, NaviampColors.Dark,
            actions = NaviampNowPlayingActions({}, {
                if (it.action == NowPlayingDisplayAction.ToggleLyrics) { toggles++; now.value = now.value.copy(lyricsVisible = !now.value.lyricsVisible) }
            }, {}, {}, {}, {}, {}), onClose = {}, onOpenSettings = {}) }
        mainClock.advanceTimeBy(200)
        onNodeWithContentDescription("Lyrics").performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput { pressKey(Key.DirectionCenter) }
        mainClock.advanceTimeBy(300)
        onNodeWithText("Plain lyric").assertIsDisplayed()
        onNodeWithContentDescription("Lyrics").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        mainClock.advanceTimeBy(300)
        onNodeWithText("Plain lyric").assertDoesNotExist()
        onNodeWithContentDescription("Lyrics").assertIsFocused()
        assertEquals(2, toggles)
    }

}
