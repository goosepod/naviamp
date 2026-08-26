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

        onNodeWithContentDescription("Pause").assertIsFocused()
    }

    @Test
    fun upSelectsScrubberAndRightSeeksTenSeconds() = runComposeUiTest {
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
    fun remoteSeekTargetsClampToTrackBounds() {
        assertEquals(0.0, televisionSeekTargetSeconds(3.0, 120.0, -1))
        assertEquals(120.0, televisionSeekTargetSeconds(117.0, 120.0, 1))
        assertEquals(null, televisionSeekTargetSeconds(30.0, 0.0, 1))
    }
}
