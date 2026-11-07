package app.naviamp.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NaviampCurrentTrackRadioMenuTest {
    @Test
    fun overflowDispatchesRadioAndRemovesItWhenEligibilityChanges() = runDesktopComposeUiTest(900, 750) {
        val current = mutableStateOf(NowPlayingUi(
            id = "seed", title = "Seed track", subtitle = "Artist", stateLabel = "Paused",
            isPaused = true, canStartRadio = true, menuEnabled = true,
        ))
        val requests = mutableListOf<NowPlayingCurrentTrackUiActionRequest>()
        setContent {
            NaviampNowPlayingPanel(
                nowPlaying = current.value, colors = NaviampColors.Dark,
                actions = NaviampNowPlayingActions({}, {}, { requests += it }, {}, {}, {}, {}),
            )
        }
        onNodeWithContentDescription("Track actions").performClick()
        onNodeWithText("Start Radio").assertIsDisplayed().performClick()
        assertEquals(listOf(NowPlayingCurrentTrackAction.StartRadio), requests.map { it.action })
        onNodeWithText("Start Radio").assertDoesNotExist()

        runOnIdle { current.value = current.value.copy(canStartRadio = false) }
        onNodeWithContentDescription("Track actions").performClick()
        onNodeWithText("Start Radio").assertDoesNotExist()
    }
}
