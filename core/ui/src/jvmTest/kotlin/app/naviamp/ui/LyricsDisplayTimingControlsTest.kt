package app.naviamp.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import app.naviamp.domain.lyrics.LyricsTiming
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class LyricsDisplayTimingControlsTest {
    @Test
    fun selectedTimingRemainsEnabledAndExposesRadioSelectionSemantics() = runComposeUiTest {
        setContent {
            LyricsDisplayTimingControls(
                availableTiming = LyricsTiming.WordSynced,
                selectedTiming = LyricsTiming.LineSynced,
                colors = NaviampColors(),
                onSelected = {},
            )
        }

        onNodeWithText("Text").assertIsEnabled().assertIsNotSelected()
        onNodeWithText("Lines").assertIsEnabled().assertIsSelected()
        onNodeWithText("Words").assertIsEnabled().assertIsNotSelected()
    }

    @Test
    fun unavailableTimingIsDisabledWithoutBeingReportedAsSelected() = runComposeUiTest {
        setContent {
            LyricsDisplayTimingControls(
                availableTiming = LyricsTiming.LineSynced,
                selectedTiming = LyricsTiming.LineSynced,
                colors = NaviampColors(),
                onSelected = {},
            )
        }

        onNodeWithText("Lines").assertIsEnabled().assertIsSelected()
        onNodeWithText("Words").assertIsNotEnabled().assertIsNotSelected()
    }
}
