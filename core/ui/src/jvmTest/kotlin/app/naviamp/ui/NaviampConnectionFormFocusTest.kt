package app.naviamp.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class NaviampConnectionFormFocusTest {
    @Test
    fun imeNextAdvancesThroughRequiredConnectionFields() = runComposeUiTest {
        setContent {
            NaviampConnectionForm(
                form = ConnectionFormState(),
                colors = NaviampColors(),
                isReconnect = false,
                onFormChanged = {},
                onConnect = {},
                onCancel = null,
            )
        }

        onNodeWithTag(ConnectionNameFieldTestTag).performClick()
        onNodeWithTag(ConnectionNameFieldTestTag).performImeAction()
        onNodeWithTag(ConnectionServerUrlFieldTestTag).assertIsFocused()

        onNodeWithTag(ConnectionServerUrlFieldTestTag).performImeAction()
        onNodeWithTag(ConnectionUsernameFieldTestTag).assertIsFocused()

        onNodeWithTag(ConnectionUsernameFieldTestTag).performImeAction()
        onNodeWithTag(ConnectionPasswordFieldTestTag).assertIsFocused()

        onNodeWithTag(ConnectionPasswordFieldTestTag).performImeAction()
        onNodeWithTag(ConnectionPasswordFieldTestTag).assertIsNotFocused()
    }
}
