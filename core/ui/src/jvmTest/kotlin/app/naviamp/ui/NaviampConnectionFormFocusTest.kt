package app.naviamp.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NaviampConnectionFormFocusTest {
    @Test
    fun editedConnectionCanExitByVisibleBackOrSystemBackWithoutConnecting() = runComposeUiTest {
        val open = mutableStateOf(true)
        val form = mutableStateOf(ConnectionFormState())
        val back = NaviampSystemBackDispatcher()
        var cancellations = 0
        var connections = 0
        setContent {
            CompositionLocalProvider(LocalNaviampSystemBackDispatcher provides back) {
                if (open.value) NaviampConnectionForm(
                    form = form.value, colors = NaviampColors.Dark, isReconnect = true,
                    onFormChanged = { form.value = it }, onConnect = { connections++ },
                    onCancel = { cancellations++; open.value = false },
                )
            }
        }
        onNodeWithTag(ConnectionNameFieldTestTag).performTextReplacement("Unsaved edit")
        onNodeWithTag(ConnectionBackButtonTestTag).assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput { pressKey(Key.DirectionCenter) }
        onNodeWithTag(ConnectionNameFieldTestTag).assertDoesNotExist()
        assertEquals(1, cancellations)
        assertEquals(0, connections)
        runOnIdle { open.value = true }
        onNodeWithTag(ConnectionNameFieldTestTag).assertExists()
        runOnIdle { requireNotNull(back.currentHandler).invoke() }
        onNodeWithTag(ConnectionNameFieldTestTag).assertDoesNotExist()
        assertEquals(2, cancellations)
        assertEquals(0, connections)
    }

    @Test
    fun initialSetupDoesNotOfferAnExitWithoutACancelDestination() = runComposeUiTest {
        setContent {
            NaviampConnectionForm(ConnectionFormState(), NaviampColors.Dark, false,
                onFormChanged = {}, onConnect = {}, onCancel = null)
        }
        onNodeWithTag(ConnectionBackButtonTestTag).assertDoesNotExist()
    }

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
        onNodeWithTag(ConnectionConnectButtonTestTag).assertIsFocused()

        onNodeWithTag(ConnectionConnectButtonTestTag).performKeyInput { pressKey(Key.DirectionUp) }
        onNodeWithTag(ConnectionAdvancedActionTestTag).assertIsFocused()
    }

    @Test
    fun localFileActionsCanBeExcludedFromTelevisionConnectionSetup() = runComposeUiTest {
        setContent {
            NaviampConnectionForm(
                form = ConnectionFormState(),
                colors = NaviampColors(),
                isReconnect = false,
                capabilities = NaviampConnectionCapabilitiesUi(
                    customServerCertificates = true,
                    clientCertificates = true,
                ),
                allowLocalFileInputs = false,
                allowFallbackUrls = false,
                onFormChanged = {},
                onConnect = {},
                onImportSettingsSyncFile = {},
                onCancel = null,
            )
        }

        onNodeWithTag(ConnectionAdvancedActionTestTag).performClick()

        listOf(
            "Import provider settings",
            "Trusted certificate or CA file",
            "Client certificate PKCS12 file",
            "Client certificate password",
            "Fallback URLs",
            "Add fallback URL",
        ).forEach { hiddenLabel ->
            assertEquals(0, onAllNodesWithText(hiddenLabel).fetchSemanticsNodes().size)
        }
    }
}
