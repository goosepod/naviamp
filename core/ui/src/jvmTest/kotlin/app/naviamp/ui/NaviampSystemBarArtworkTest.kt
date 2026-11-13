package app.naviamp.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.test.*
import app.naviamp.domain.settings.NowPlayingDisplaySettings
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class NaviampSystemBarArtworkTest {
    @Test fun filledArtworkUsesTheWholeWindowWhileControlsAvoidSystemBars() = runDesktopComposeUiTest(412, 818) {
        setContent {
            NaviampNowPlayingPanel(
                NowPlayingUi(id = "track", title = "Full window", subtitle = "Artist",
                    stateLabel = "Paused", isPaused = true, canPlayPause = true),
                colors = NaviampColors.Dark,
                actions = NaviampNowPlayingActions({}, {}, {}, {}, {}, {}, {}),
                displaySettings = NowPlayingDisplaySettings(fillArtworkArea = true),
                contentInsets = WindowInsets(top = 24, bottom = 32),
            )
        }
        val window = onRoot().fetchSemanticsNode().boundsInRoot
        val art = onNodeWithTag("now-playing-filled-artwork").fetchSemanticsNode().boundsInRoot
        assertEquals(window, art)
        val controls = onNodeWithTag("now-playing-bottom-actions").fetchSemanticsNode().boundsInRoot
        assertTrue(controls.bottom <= window.bottom - 32)
        assertTrue(controls.top >= window.top + 24)
    }

}
