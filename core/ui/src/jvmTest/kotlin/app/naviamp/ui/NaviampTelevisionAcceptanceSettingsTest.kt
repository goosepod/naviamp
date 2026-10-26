package app.naviamp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import app.naviamp.domain.settings.PlaybackSettings
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NaviampTelevisionAcceptanceSettingsTest {
    @Test fun verticalArrowsLeaveRadioSlidersWithoutChangingTheirValues() = runDesktopComposeUiTest(1280, 900) {
        val settings = mutableStateOf(PlaybackSettings(sonicAutoplayEnabled = true))
        setContent {
            Column {
                RadioSettingsSection(NaviampColors.Dark, settings.value, true, NaviampSimilarityTestUi(), null,
                    {}, { settings.value = it }, verticalFocusNavigation = true)
            }
        }
        val radio = onNodeWithContentDescription("Radio batch size")
        radio.performSemanticsAction(SemanticsActions.RequestFocus)
        radio.performKeyInput { pressKey(Key.DirectionRight) }
        runOnIdle { assertEquals(20, settings.value.radioMinimumTracks) }
        radio.performKeyInput { pressKey(Key.DirectionDown) }
        radio.assertIsNotFocused()
        runOnIdle { assertEquals(20, settings.value.radioMinimumTracks) }
        radio.performSemanticsAction(SemanticsActions.RequestFocus)
        radio.performKeyInput { pressKey(Key.DirectionUp) }
        radio.assertIsNotFocused()
        runOnIdle { assertEquals(20, settings.value.radioMinimumTracks) }
        radio.performSemanticsAction(SemanticsActions.RequestFocus)
        radio.performKeyInput { pressKey(Key.DirectionLeft) }
        runOnIdle { assertEquals(10, settings.value.radioMinimumTracks) }

        val autoplay = onNodeWithContentDescription("Autoplay batch size")
        autoplay.performSemanticsAction(SemanticsActions.RequestFocus)
        autoplay.performKeyInput { pressKey(Key.DirectionRight) }
        runOnIdle { assertEquals(20, settings.value.sonicAutoplayMinimumTracks) }
        autoplay.performKeyInput { pressKey(Key.DirectionDown) }
        autoplay.assertIsNotFocused()
        runOnIdle { assertEquals(20, settings.value.sonicAutoplayMinimumTracks) }
    }

    @Test fun televisionAboutExposesCompleteSharedEntriesAndRemoteScrollsToTheEnd() = runDesktopComposeUiTest(850, 480) {
        val about = NaviampAboutUi(changelog = listOf(NaviampChangelogSectionUi("Fixture changes",
            (1..8).map { "Release entry $it: " + "Readable complete content. ".repeat(8) })))
        setContent { TelevisionAboutSettings(NaviampAppShellUiState(general = NaviampGeneralSettingsUi(about = about)),
            NaviampColors.Dark, FocusRequester()) }
        onNodeWithText("Naviamp").performSemanticsAction(SemanticsActions.RequestFocus)
        onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
        onNodeWithText(about.changelog.single().entries.first()).assertIsFocused().assertIsDisplayed()
        repeat(7) { onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }; waitForIdle() }
        onNodeWithText(about.changelog.single().entries.last()).assertIsFocused().assertIsDisplayed()
    }
}
