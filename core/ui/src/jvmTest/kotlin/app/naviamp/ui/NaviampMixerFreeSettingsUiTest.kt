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
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NaviampMixerFreeSettingsUiTest {
    @Test
    fun audioOutputHidesUnavailableDownmixWithoutChangingTheSavedPreference() = runDesktopComposeUiTest(600, 900) {
        val available = mutableStateOf(true)
        val saved = PlaybackSettings(stereoDownmixEnabled = true)
        setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                NaviampPlaybackSettingsSection(
                    colors = NaviampColors(), playbackSettings = saved,
                    supportsReplayGain = true, supportsGapless = false, supportsCrossfade = false,
                    supportsStereoDownmix = available.value, supportsEqualizer = true,
                    onPlaybackSettingsChanged = { error("Capability changes must not write settings") },
                )
            }
        }
        onNodeWithText("Audio Output").performClick()
        onNodeWithText("Stereo Downmix").performScrollTo().assertIsDisplayed()
        runOnIdle { available.value = false }
        onNodeWithText("Stereo Downmix").assertDoesNotExist()
        runOnIdle { assertTrue(saved.stereoDownmixEnabled); available.value = true }
        onNodeWithText("Stereo Downmix").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun televisionOnlyOffersMixerControlsWhenTheSharedCapabilitiesAllowThem() = runDesktopComposeUiTest(1280, 1080) {
        val available = mutableStateOf(true)
        val saved = PlaybackSettings(stereoDownmixEnabled = true)
        setContent {
            TelevisionPlaybackSettings(
                uiState = NaviampAppShellUiState(playback = NaviampPlaybackSettingsUi(settings = saved,
                    gaplessAvailable = available.value, crossfadeAvailable = available.value,
                    stereoDownmixAvailable = available.value)),
                colors = NaviampTelevisionColors,
                actions = NaviampSettingsValueActions({}, { error("Unexpected settings write") }, {}, {}, {}, {}),
                onTestSimilarity = {}, firstFocusRequester = FocusRequester(), returnFocusRequester = FocusRequester(),
                returnChoice = null, onChoiceSelected = {},
            )
        }
        onNodeWithText("Stereo downmix").performScrollTo().assertIsDisplayed()
        runOnIdle { available.value = false }
        onNodeWithText("Stereo downmix").assertDoesNotExist()
        onNodeWithText("Gapless playback").assertDoesNotExist()
        onNodeWithText("Crossfade").assertDoesNotExist()
        runOnIdle { assertTrue(saved.stereoDownmixEnabled); available.value = true }
        onNodeWithText("Stereo downmix").performScrollTo().assertIsDisplayed()
    }
}
