package app.naviamp.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NaviampTelevisionNowPlayingFocusTest {
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
                onSearch = {},
            )
        }
        mainClock.advanceTimeBy(200)

        onNodeWithContentDescription("Pause").assertIsFocused()
    }

    @Test
    fun upSelectsScrubberAndRightSeeksTenSeconds() = runComposeUiTest {
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
                onSearch = {},
            )
        }
        mainClock.advanceTimeBy(200)

        onNodeWithContentDescription("Play").performKeyInput { pressKey(Key.DirectionUp) }
        onNodeWithTag(TelevisionNowPlayingScrubberTestTag).assertIsFocused()
        onNodeWithTag(TelevisionNowPlayingScrubberTestTag).performKeyInput { pressKey(Key.DirectionRight) }
        onNodeWithText("0:40").assertExists()

        runOnIdle {
            assertEquals(NowPlayingPlaybackAction.Seek, playbackActions.single().action)
            assertEquals(40.0, playbackActions.single().seekSeconds)
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
                onSearch = {},
            )
        }
        mainClock.advanceTimeBy(200)

        onNodeWithContentDescription("Pause").assertIsFocused()
        mainClock.advanceTimeBy(TelevisionNowPlayingControlsTimeoutMillis + 400)
        onNodeWithContentDescription("Pause").assertDoesNotExist()
        onNodeWithTag(TelevisionNowPlayingListeningModeTestTag).assertIsFocused()

        onNodeWithTag(TelevisionNowPlayingListeningModeTestTag).performKeyInput {
            pressKey(Key.DirectionLeft)
        }
        mainClock.advanceTimeBy(200)

        onNodeWithContentDescription("Pause").assertIsFocused()
        runOnIdle { assertEquals(emptyList(), playbackActions) }
    }

    @Test
    fun remoteSeekTargetsClampToTrackBounds() {
        assertEquals(0.0, televisionSeekTargetSeconds(3.0, 120.0, -1))
        assertEquals(120.0, televisionSeekTargetSeconds(117.0, 120.0, 1))
        assertEquals(null, televisionSeekTargetSeconds(30.0, 0.0, 1))
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
