package app.naviamp.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NaviampConnectionTlsAccessibilityTest {
    @Test
    fun tlsLabelAndCheckboxExposeOneNamedControlAndToggleOnce() = runComposeUiTest {
        val form = mutableStateOf(ConnectionFormState())
        var changes = 0
        setContent {
            Column(Modifier.height(600.dp).verticalScroll(rememberScrollState())) {
                NaviampConnectionForm(
                    form = form.value,
                    colors = NaviampColors.Dark,
                    isReconnect = false,
                    capabilities = NaviampConnectionCapabilitiesUi(insecureServerVerification = true),
                    onFormChanged = { changes++; form.value = it },
                    onConnect = {},
                    onCancel = null,
                )
            }
        }
        onNodeWithTag(ConnectionAdvancedActionTestTag).performClick()
        val checkbox = onNodeWithText("Skip TLS certificate verification").performScrollTo()
        checkbox.assertIsToggleable().assertIsOff()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
        onAllNodes(isToggleable(), useUnmergedTree = true).assertCountEquals(1)

        // The label and the visual box are parts of the same native-accessible target.
        checkbox.performClick().assertIsOn()
        runOnIdle { assertEquals(true, form.value.skipTlsVerification); assertEquals(1, changes) }
        checkbox.performClick().assertIsOff()
        runOnIdle { assertEquals(false, form.value.skipTlsVerification); assertEquals(2, changes) }
    }
}
