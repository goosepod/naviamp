package app.naviamp.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NaviampTelevisionNowPlayingFocusTest {
    @Test
    fun prominentPlayButtonUsesWhiteSurfaceOnlyWhileFocused() {
        assertFalse(
            televisionIconButtonUsesLightSurface(
                focused = false,
                selected = false,
                whiteHighlight = true,
                selectedKeepsDarkBackground = false,
            ),
        )
        assertTrue(
            televisionIconButtonUsesLightSurface(
                focused = true,
                selected = false,
                whiteHighlight = true,
                selectedKeepsDarkBackground = false,
            ),
        )
    }

    @Test
    fun playPauseReceivesFocusWhenNowPlayingOpens() = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent {
            TelevisionNowPlaying(
                nowPlaying = NowPlayingUi(
                    id = "track",
                    title = "Track",
                    subtitle = "Artist",
                    stateLabel = "Playing",
                    isPlaying = true,
                    canPlayPause = true,
                ),
                playbackProgress = null,
                colors = NaviampColors.Dark,
                actions = NaviampNowPlayingActions(
                    onPlaybackAction = { _ -> },
                    onDisplayAction = { _ -> },
                    onCurrentTrackAction = { _ -> },
                    onQueueAction = { _ -> },
                    onSleepTimerAction = { _ -> },
                    onSelectionAction = { _ -> },
                    onQueueItemAction = { _ -> },
                ),
                onClose = {},
                onOpenSettings = {},
            )
        }
        mainClock.advanceTimeBy(200)

        onNodeWithContentDescription("Pause").assertIsFocused()
        onNodeWithContentDescription("Settings").assertExists()
        onNodeWithContentDescription("Search").assertDoesNotExist()
    }

    @Test
    fun scrubberCannotReceiveFocusOrSeekFromTheRemote() = runComposeUiTest {
        mainClock.autoAdvance = false
        val playbackActions = mutableListOf<NowPlayingPlaybackActionRequest>()
        setContent {
            TelevisionNowPlaying(
                nowPlaying = NowPlayingUi(
                    id = "track",
                    title = "Track",
                    subtitle = "Artist",
                    stateLabel = "Paused",
                    positionSeconds = 30.0,
                    durationSeconds = 120.0,
                    isPaused = true,
                    canPlayPause = true,
                    canSeek = true,
                ),
                playbackProgress = null,
                colors = NaviampColors.Dark,
                actions = NaviampNowPlayingActions(
                    onPlaybackAction = playbackActions::add,
                    onDisplayAction = { _ -> },
                    onCurrentTrackAction = { _ -> },
                    onQueueAction = { _ -> },
                    onSleepTimerAction = { _ -> },
                    onSelectionAction = { _ -> },
                    onQueueItemAction = { _ -> },
                ),
                onClose = {},
                onOpenSettings = {},
            )
        }
        mainClock.advanceTimeBy(200)

        onNodeWithContentDescription("Play").performKeyInput { pressKey(Key.DirectionUp) }
        onNodeWithTag(TelevisionNowPlayingScrubberTestTag).assert(
            SemanticsMatcher.keyNotDefined(SemanticsProperties.Focused),
        )

        runOnIdle {
            assertEquals(emptyList(), playbackActions)
        }
    }

    @Test
    fun inactivityHidesControlsAndFirstDirectionPressOnlyRestoresThem() = runComposeUiTest {
        mainClock.autoAdvance = false
        val playbackActions = mutableListOf<NowPlayingPlaybackActionRequest>()
        setContent {
            TelevisionNowPlaying(
                nowPlaying = NowPlayingUi(
                    id = "track",
                    title = "Track",
                    subtitle = "Artist",
                    stateLabel = "Playing",
                    isPlaying = true,
                    hasPrevious = true,
                    canPlayPause = true,
                ),
                playbackProgress = null,
                colors = NaviampColors.Dark,
                actions = NaviampNowPlayingActions(
                    onPlaybackAction = playbackActions::add,
                    onDisplayAction = { _ -> },
                    onCurrentTrackAction = { _ -> },
                    onQueueAction = { _ -> },
                    onSleepTimerAction = { _ -> },
                    onSelectionAction = { _ -> },
                    onQueueItemAction = { _ -> },
                ),
                onClose = {},
                onOpenSettings = {},
            )
        }
        mainClock.advanceTimeBy(200)

        onNodeWithContentDescription("Pause").assertIsFocused()
        mainClock.advanceTimeBy(
            TelevisionNowPlayingControlsTimeoutMillis + TelevisionListeningModeTransitionMillis + 400,
        )
        onNodeWithContentDescription("Pause").assertDoesNotExist()
        onNodeWithTag(TelevisionNowPlayingListeningModeTestTag).assertIsFocused()

        onNodeWithTag(TelevisionNowPlayingListeningModeTestTag).performKeyInput {
            pressKey(Key.DirectionLeft)
        }
        mainClock.advanceTimeBy(TelevisionListeningModeTransitionMillis + 200L)

        onNodeWithContentDescription("Pause").assertIsFocused()
        runOnIdle { assertEquals(emptyList(), playbackActions) }
    }

    @Test
    fun trackChangeKeepsListeningModeWhenControlsWereAlreadyHidden() = runComposeUiTest {
        mainClock.autoAdvance = false
        val trackId = mutableStateOf("track-one")
        setContent {
            TelevisionNowPlaying(
                nowPlaying = NowPlayingUi(
                    id = trackId.value,
                    title = trackId.value,
                    subtitle = "Artist",
                    stateLabel = "Playing",
                    isPlaying = true,
                    canPlayPause = true,
                ),
                playbackProgress = null,
                colors = NaviampColors.Dark,
                actions = NaviampNowPlayingActions(
                    onPlaybackAction = { _ -> },
                    onDisplayAction = { _ -> },
                    onCurrentTrackAction = { _ -> },
                    onQueueAction = { _ -> },
                    onSleepTimerAction = { _ -> },
                    onSelectionAction = { _ -> },
                    onQueueItemAction = { _ -> },
                ),
                onClose = {},
                onOpenSettings = {},
            )
        }
        mainClock.advanceTimeBy(
            TelevisionNowPlayingControlsTimeoutMillis + TelevisionListeningModeTransitionMillis + 400,
        )
        onNodeWithContentDescription("Pause").assertDoesNotExist()

        trackId.value = "track-two"
        mainClock.advanceTimeBy(400)

        onNodeWithContentDescription("Pause").assertDoesNotExist()
        onNodeWithTag(TelevisionNowPlayingListeningModeTestTag).assertIsFocused()
    }

    @Test
    fun nonInteractiveLyricsPreviewDoesNotShowTransportControls() = runComposeUiTest {
        setContent {
            TelevisionNowPlaying(
                nowPlaying = NowPlayingUi(
                    id = "track",
                    title = "Track",
                    subtitle = "Artist",
                    stateLabel = "Playing",
                    isPlaying = true,
                    canPlayPause = true,
                    lyricsAvailable = true,
                    lyricsVisible = true,
                    lyricsLines = listOf(NaviampLyricLineUi(startMillis = 0L, text = "Visible lyric")),
                ),
                playbackProgress = null,
                colors = NaviampColors.Dark,
                actions = NaviampNowPlayingActions(
                    onPlaybackAction = { _ -> },
                    onDisplayAction = { _ -> },
                    onCurrentTrackAction = { _ -> },
                    onQueueAction = { _ -> },
                    onSleepTimerAction = { _ -> },
                    onSelectionAction = { _ -> },
                    onQueueItemAction = { _ -> },
                ),
                interactive = false,
                onClose = {},
                onOpenSettings = {},
            )
        }

        onNodeWithText("Visible lyric").assertExists()
        onNodeWithContentDescription("Pause").assertDoesNotExist()
        onNodeWithContentDescription("Lyrics").assertDoesNotExist()
    }

    @Test
    fun repeatAllHasAnExplicitVisualModeMarker() = runComposeUiTest {
        setContent {
            TelevisionNowPlaying(
                nowPlaying = NowPlayingUi(
                    id = "track",
                    title = "Track",
                    subtitle = "Artist",
                    stateLabel = "Playing",
                    isPlaying = true,
                    canPlayPause = true,
                    canRepeat = true,
                    repeatMode = NaviampRepeatMode.Queue,
                ),
                playbackProgress = null,
                colors = NaviampColors.Dark,
                actions = NaviampNowPlayingActions(
                    onPlaybackAction = { _ -> },
                    onDisplayAction = { _ -> },
                    onCurrentTrackAction = { _ -> },
                    onQueueAction = { _ -> },
                    onSleepTimerAction = { _ -> },
                    onSelectionAction = { _ -> },
                    onQueueItemAction = { _ -> },
                ),
                onClose = {},
                onOpenSettings = {},
            )
        }

        onNodeWithContentDescription("Repeat all").assertExists()
        onNodeWithText("A").assertExists()
    }

    @Test
    fun queueOpensWithPinnedCurrentSupportsReorderAndBackRestoresItsButton() = runComposeUiTest {
        mainClock.autoAdvance = false
        val dispatcher = NaviampSystemBackDispatcher()
        val queueActions = mutableListOf<NowPlayingQueueActionRequest>()
        setContent {
            CompositionLocalProvider(LocalNaviampSystemBackDispatcher provides dispatcher) {
                TelevisionNowPlaying(
                    nowPlaying = NowPlayingUi(
                        id = "current",
                        title = "Current track",
                        subtitle = "Artist",
                        stateLabel = "Playing",
                        isPlaying = true,
                        canPlayPause = true,
                        queueCurrentIndex = 0,
                        upNext = listOf(
                            NaviampNowPlayingItemUi("queue:1", "First upcoming", "Artist"),
                            NaviampNowPlayingItemUi("queue:2", "Second upcoming", "Artist"),
                        ),
                    ),
                    playbackProgress = null,
                    colors = NaviampColors.Dark,
                    actions = NaviampNowPlayingActions(
                        onPlaybackAction = { _ -> },
                        onDisplayAction = { _ -> },
                        onCurrentTrackAction = { _ -> },
                        onQueueAction = queueActions::add,
                        onSleepTimerAction = { _ -> },
                        onSelectionAction = { _ -> },
                        onQueueItemAction = { _ -> },
                    ),
                    onClose = {},
                    onOpenSettings = {},
                )
            }
        }
        mainClock.advanceTimeBy(200)
        onNodeWithContentDescription("Queue").performClick()
        mainClock.advanceTimeBy(400)

        onNodeWithTag(TelevisionNowPlayingQueueCurrentTestTag).assertExists()
        assertEquals(0, onAllNodesWithText("MOVING").fetchSemanticsNodes().size)
        onNodeWithTag("${TelevisionNowPlayingQueueUpcomingTestTagPrefix}0").assertIsFocused()
        onNodeWithTag("${TelevisionNowPlayingQueueUpcomingTestTagPrefix}0").performKeyInput {
            pressKey(Key.DirectionLeft)
        }
        mainClock.advanceTimeBy(100)
        assertEquals(1, onAllNodesWithText("MOVING").fetchSemanticsNodes().size)
        onNodeWithTag("${TelevisionNowPlayingQueueUpcomingTestTagPrefix}0").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        mainClock.advanceTimeBy(200)
        onNodeWithTag("${TelevisionNowPlayingQueueUpcomingTestTagPrefix}1").assertIsFocused()
        onNodeWithTag("${TelevisionNowPlayingQueueUpcomingTestTagPrefix}1").performKeyInput {
            pressKey(Key.DirectionRight)
        }
        runOnIdle {
            assertEquals(
                NowPlayingQueueActionRequest(
                    action = NowPlayingQueueAction.MoveQueueItem,
                    queueIndex = 1,
                    destinationQueueIndex = 2,
                ),
                queueActions.single(),
            )
            dispatcher.currentHandler?.invoke()
        }
        mainClock.advanceTimeBy(200)

        onNodeWithTag(TelevisionNowPlayingQueueTestTag).assertDoesNotExist()
        onNodeWithContentDescription("Queue").assertIsFocused()
    }

    @Test
    fun rightOnCurrentQueueItemOpensActionsInsteadOfLeavingTheQueue() = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent {
            TelevisionNowPlaying(
                nowPlaying = NowPlayingUi(
                    id = "current",
                    title = "Current track",
                    subtitle = "Artist",
                    stateLabel = "Playing",
                    isPlaying = true,
                    canPlayPause = true,
                    queueCurrentIndex = 0,
                    upNext = listOf(
                        NaviampNowPlayingItemUi("queue:1", "Upcoming track", "Artist"),
                    ),
                ),
                playbackProgress = null,
                colors = NaviampColors.Dark,
                actions = NaviampNowPlayingActions(
                    onPlaybackAction = { _ -> },
                    onDisplayAction = { _ -> },
                    onCurrentTrackAction = { _ -> },
                    onQueueAction = { _ -> },
                    onSleepTimerAction = { _ -> },
                    onSelectionAction = { _ -> },
                    onQueueItemAction = { _ -> },
                ),
                onClose = {},
                onOpenSettings = {},
            )
        }
        mainClock.advanceTimeBy(200)
        onNodeWithContentDescription("Queue").performClick()
        mainClock.advanceTimeBy(400)

        onNodeWithTag("${TelevisionNowPlayingQueueUpcomingTestTagPrefix}0").performKeyInput {
            pressKey(Key.DirectionUp)
        }
        mainClock.advanceTimeBy(100)
        onNodeWithTag(TelevisionNowPlayingQueueCurrentTestTag)
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        mainClock.advanceTimeBy(100)

        onNodeWithText("Queue actions").assertExists()
        onNodeWithText("Play Next").assertIsFocused()
    }

    @Test
    fun liveRadioQueueShowsSavedStationsAndSelectsThemAsStations() = runComposeUiTest {
        mainClock.autoAdvance = false
        val selections = mutableListOf<NowPlayingSelectionActionRequest>()
        setContent {
            TelevisionNowPlaying(
                nowPlaying = NowPlayingUi(
                    id = "current-station",
                    title = "Current stream title",
                    subtitle = "Current station",
                    stateLabel = "Playing",
                    isLive = true,
                    isPlaying = true,
                    canPlayPause = true,
                    radioStations = listOf(
                        NaviampNowPlayingItemUi("current-station", "Current station", "Internet radio"),
                        NaviampNowPlayingItemUi("other-station", "Other station", "Internet radio"),
                    ),
                ),
                playbackProgress = null,
                colors = NaviampColors.Dark,
                actions = NaviampNowPlayingActions(
                    onPlaybackAction = { _ -> },
                    onDisplayAction = { _ -> },
                    onCurrentTrackAction = { _ -> },
                    onQueueAction = { _ -> },
                    onSleepTimerAction = { _ -> },
                    onSelectionAction = selections::add,
                    onQueueItemAction = { _ -> },
                ),
                onClose = {},
                onOpenSettings = {},
            )
        }
        mainClock.advanceTimeBy(200)

        onNodeWithContentDescription("Queue").performClick()
        mainClock.advanceTimeBy(400)

        onNodeWithText("INTERNET RADIO").assertExists()
        onNodeWithTag(TelevisionNowPlayingQueueCurrentTestTag).assertExists()
        onNodeWithText("Other station").assertExists()
        onNodeWithTag("${TelevisionNowPlayingQueueUpcomingTestTagPrefix}0")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        mainClock.advanceTimeBy(100)

        assertEquals(1, selections.size)
        assertEquals(NowPlayingSelectionAction.SelectRadioStation, selections.single().action)
        assertEquals("other-station", selections.single().item.id)
    }

    @Test
    fun onlyOrdinaryNavigationKeysWakeListeningMode() {
        assertEquals(true, televisionWakesNowPlayingControls(Key.DirectionCenter))
        assertEquals(true, televisionWakesNowPlayingControls(Key.DirectionDown))
        assertEquals(false, televisionWakesNowPlayingControls(Key.Back))
    }

    @Test
    fun miniPlayerIsStatusOnlyWithoutPlaybackOrOpenActions() = runComposeUiTest {
        setContent {
            TelevisionMiniPlayer(
                nowPlaying = NowPlayingUi(
                    id = "track",
                    title = "Status Track",
                    subtitle = "Status Artist",
                    stateLabel = "Playing",
                    isPlaying = true,
                    hasPrevious = true,
                    hasNext = true,
                    canPlayPause = true,
                ),
                colors = NaviampColors.Dark,
            )
        }

        onNodeWithText("Status Track").assertExists()
        onNodeWithText("Status Artist").assertExists()
        onNodeWithContentDescription("Previous").assertDoesNotExist()
        onNodeWithContentDescription("Pause").assertDoesNotExist()
        onNodeWithContentDescription("Next").assertDoesNotExist()
    }

    @Test
    fun searchBackUnwindsResultsThenKeyboardThenNavigation() {
        assertEquals(
            TelevisionSearchBackTarget.Query,
            televisionSearchBackTarget(resultsActive = true, searchFieldFocused = false, keyboardActive = false),
        )
        assertEquals(
            TelevisionSearchBackTarget.HideKeyboard,
            televisionSearchBackTarget(resultsActive = false, searchFieldFocused = true, keyboardActive = true),
        )
        assertEquals(
            TelevisionSearchBackTarget.Navigation,
            televisionSearchBackTarget(resultsActive = false, searchFieldFocused = true, keyboardActive = false),
        )
    }
}
