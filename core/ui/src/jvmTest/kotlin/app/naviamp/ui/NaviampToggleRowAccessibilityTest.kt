package app.naviamp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.*
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NaviampToggleRowAccessibilityTest {
    private fun SemanticsNodeInteraction.assertSwitch(state: ToggleableState) =
        assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, state))

    @Test fun aSettingsSwitchRowIsOneToggleThatAnnouncesAndFlipsItsState() = runComposeUiTest {
        var checked by mutableStateOf(false)
        setContent {
            SettingsCheckboxRow(NaviampColors.Dark, checked = checked, label = "Offline Mode",
                subtitle = "Only play downloads") { checked = it }
        }
        // Label, subtitle and switch merge into a single focus target with the switch state.
        assertEquals(1, onAllNodes(isToggleable()).fetchSemanticsNodes().size)
        val row = onNode(isToggleable())
        row.assertTextContains("Offline Mode").assertTextContains("Only play downloads")
            .assertSwitch(ToggleableState.Off).assertIsEnabled()
        row.performClick()
        assertEquals(true, checked)
        row.assertSwitch(ToggleableState.On)
        row.performClick()
        assertEquals(false, checked)
        row.assertSwitch(ToggleableState.Off)
    }

    @Test fun anInlineSettingsSwitchExposesItsStateAndDisabledRowsCannotToggle() = runComposeUiTest {
        var changes = 0
        setContent {
            InlineSettingsToggleRow(NaviampColors.Dark, checked = true, enabled = false,
                label = "Allow downloads on mobile data") { changes++ }
        }
        val row = onNode(isToggleable())
        row.assertTextContains("Allow downloads on mobile data").assertSwitch(ToggleableState.On).assertIsNotEnabled()
        row.performClick()
        assertEquals(0, changes)
    }

    @Test fun aTelevisionOnOffRowIsASwitchWithoutRepeatingTheStateAsText() = runComposeUiTest {
        var checked by mutableStateOf(true)
        setContent {
            TelevisionSettingsToggleRow("Gapless playback", "Play albums without gaps", checked, NaviampColors.Dark,
                onClick = { checked = !checked })
        }
        val row = onNode(isToggleable())
        row.assertSwitch(ToggleableState.On).assertTextContains("Gapless playback")
        // The visible On/Off value is not read again after the switch state.
        onAllNodesWithText("On").assertCountEquals(0)
        row.performClick()
        row.assertSwitch(ToggleableState.Off)
    }
}
