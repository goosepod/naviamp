package app.naviamp.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

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
}
