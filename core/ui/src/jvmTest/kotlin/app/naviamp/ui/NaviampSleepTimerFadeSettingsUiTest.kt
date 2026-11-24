package app.naviamp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.*
import app.naviamp.domain.settings.PlaybackSettings
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NaviampSleepTimerFadeSettingsUiTest {
    @Test
    fun fadesSectionChoosesTheSleepTimerFadeLength() = runDesktopComposeUiTest(600, 900) {
        val settings = mutableStateOf(PlaybackSettings())
        setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                NaviampPlaybackSettingsSection(
                    colors = NaviampColors(), playbackSettings = settings.value,
                    supportsReplayGain = true, supportsGapless = true, supportsCrossfade = true,
                    supportsStereoDownmix = true, supportsEqualizer = true,
                    onPlaybackSettingsChanged = { settings.value = it },
                )
            }
        }
        onNodeWithText("Fades").performClick()
        onNodeWithText("Sleep timer fade-out").performScrollTo().performClick()
        onNodeWithText("Stop playback when the timer ends.").assertIsDisplayed()
        onNodeWithText("3 min").performScrollTo().performClick()
        runOnIdle { assertEquals(3, settings.value.sleepTimerFadeMinutes) }
    }

    @Test
    fun televisionPlaybackSettingsOfferTheSleepTimerFade() = runDesktopComposeUiTest(1280, 1080) {
        var opened: TelevisionSettingsChoicePage? = null
        setContent {
            TelevisionPlaybackSettings(
                uiState = NaviampAppShellUiState(playback = NaviampPlaybackSettingsUi(
                    settings = PlaybackSettings(sleepTimerFadeMinutes = 5))),
                colors = NaviampTelevisionColors,
                actions = NaviampSettingsValueActions({}, { error("Unexpected settings write") }, {}, {}, {}, {}),
                onTestSimilarity = {}, firstFocusRequester = FocusRequester(), returnFocusRequester = FocusRequester(),
                returnChoice = null, onChoiceSelected = { opened = it },
            )
        }
        onNodeWithText("5 min").performScrollTo().assertIsDisplayed()
        onNodeWithText("Sleep timer fade-out").performScrollTo().performClick()
        runOnIdle { assertEquals(TelevisionSettingsChoicePage.SleepTimerFade, opened) }
    }
}
