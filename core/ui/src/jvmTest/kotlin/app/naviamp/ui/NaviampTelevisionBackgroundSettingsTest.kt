package app.naviamp.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.naviamp.domain.settings.AppBackgroundStyle
import app.naviamp.domain.settings.InterfaceSettings
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class NaviampTelevisionBackgroundSettingsTest {
    @Test
    fun styleSelectionStaysOnBackgroundPageAndShowsSelectedStyleControls() =
        runDesktopComposeUiTest(1920, 1080) {
            val state = mutableStateOf(shell(AppBackgroundStyle.Aurora))
            setContent {
                TelevisionBackgroundSettings(
                    uiState = state.value,
                    colors = NaviampTelevisionColors,
                    actions = settingsActions { updated ->
                        state.value = state.value.copy(
                            general = state.value.general.copy(interfaceSettings = updated),
                        )
                    },
                    firstFocusRequester = FocusRequester(),
                )
            }

            onNodeWithText("Aurora").assertIsDisplayed()
            onNodeWithText("Album Blur").assertIsDisplayed().performClick()
            onNodeWithText("Single Color").assertIsDisplayed()
            onNodeWithText("Blur amount").assertIsDisplayed()

            onNodeWithText("Single Color").performClick()
            onNodeWithText("Selected color").assertIsDisplayed()
            onNodeWithText("Hue").assertIsDisplayed()

            onNodeWithText("Aurora").performClick()
            onNodeWithText("Balanced").assertIsDisplayed()
            onNodeWithText("Aurora color steps").assertIsDisplayed()
        }

    private fun shell(style: AppBackgroundStyle) = NaviampAppShellUiState(
        general = NaviampGeneralSettingsUi(
            interfaceSettings = InterfaceSettings(appBackgroundStyle = style),
        ),
    )

    private fun settingsActions(onChanged: (InterfaceSettings) -> Unit) = NaviampSettingsValueActions(
        onInterfaceSettingsChanged = onChanged,
        onPlaybackSettingsChanged = {},
        onPlaybackSettingsChangedAndRedownload = {},
        onCacheSettingsChanged = {},
        onDownloadLocationChanged = {},
        onAudioCacheLocationChanged = {},
    )
}
