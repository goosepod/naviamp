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
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.toAwtImage
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NaviampConnectionFormFocusTest {
    @Test
    fun collapsedAdvancedReachesCompanionBeforeSaveInBothDirections() = runDesktopComposeUiTest(850, 1100) {
        val form = mutableStateOf(ConnectionFormState())
        var connections = 0
        setContent {
            androidx.compose.material3.MaterialTheme {
                NaviampConnectionForm(form.value, NaviampColors.Dark, true, saveWithoutReconnect = true,
                    onFormChanged = { form.value = it }, onConnect = { connections++ }, onCancel = null)
            }
        }
        onNodeWithTag(ConnectionAdvancedActionTestTag)
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput { pressKey(Key.DirectionDown) }
        onNodeWithText("Aurral companion").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        onNodeWithTag(ConnectionConnectButtonTestTag).assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionUp) }
        onNodeWithText("Aurral companion").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(true, form.value.aurralCompanion?.enabled)
        assertEquals(0, connections)
        onNodeWithTag(ConnectionAdvancedActionTestTag)
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput { pressKey(Key.DirectionDown) }
        onNodeWithText("Aurral companion").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        onNodeWithTag("aurral-companion-" + ConnectionServerUrlFieldTestTag).assertIsFocused()
    }

    @Test
    fun standaloneAurralKeepsCollapsedAdvancedToConnectTraversal() = runDesktopComposeUiTest(850, 1100) {
        setContent {
            androidx.compose.material3.MaterialTheme {
                NaviampConnectionForm(ConnectionFormState(providerId = app.naviamp.domain.provider.ProviderIdAurral),
                    NaviampColors.Dark, false, onFormChanged = {}, onConnect = {}, onCancel = null)
            }
        }
        onNodeWithText("Aurral companion").assertDoesNotExist()
        onNodeWithTag(ConnectionAdvancedActionTestTag)
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput { pressKey(Key.DirectionDown) }
        onNodeWithTag(ConnectionConnectButtonTestTag).assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionUp) }
        onNodeWithTag(ConnectionAdvancedActionTestTag).assertIsFocused()
    }

    @Test fun companionFieldsEditTheirOwnAccountAndKeepOnePrimarySaveButton() = runDesktopComposeUiTest(850, 1100) {
        val form = mutableStateOf(ConnectionFormState(serverUrl = "https://primary.example", username = "primary",
            aurralCompanion = app.naviamp.domain.source.AurralCompanion(true, "https://aurral.example", "secondary", "secret")))
        setContent {
            androidx.compose.material3.MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme()) {
                NaviampConnectionForm(form.value, NaviampColors.Dark, true, saveWithoutReconnect = true,
                    onFormChanged = { form.value = it }, onConnect = {}, onCancel = {})
            }
        }
        onNodeWithTag("aurral-companion-" + ConnectionServerUrlFieldTestTag).performTextReplacement("https://new-aurral.example")
        onNodeWithTag("aurral-companion-" + ConnectionUsernameFieldTestTag).performTextReplacement("new-secondary")
        assertEquals("https://primary.example", form.value.serverUrl)
        assertEquals("primary", form.value.username)
        assertEquals("https://new-aurral.example", form.value.aurralCompanion!!.baseUrl)
        assertEquals("new-secondary", form.value.aurralCompanion!!.username)
        onNodeWithTag(ConnectionConnectButtonTestTag).assertExists()
        onNodeWithText("Save").assertExists()
        onNodeWithTag("aurral-companion-" + ConnectionConnectButtonTestTag).assertDoesNotExist()
        assertEquals("", form.value.aurralCompanion!!.password)
        System.getenv("NAVIAMP_COMPANION_CAPTURE_DIR")?.let { directory ->
            val target = java.io.File(directory, "companion-connection.png").also { it.parentFile.mkdirs() }
            javax.imageio.ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", target)
        }
    }

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
        onNodeWithText("Aurral companion").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionUp) }
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
