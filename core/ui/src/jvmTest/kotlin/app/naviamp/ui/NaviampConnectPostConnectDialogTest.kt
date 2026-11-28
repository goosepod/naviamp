package app.naviamp.ui

import androidx.compose.ui.test.*
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NaviampConnectPostConnectDialogTest {
    @Test fun freshDevicePreselectsSignInAndToggleDelegates() = runComposeUiTest {
        val calls = mutableListOf<String>()
        setContent { NaviampConnectPostConnectDialog(sheet(), NaviampColors.Dark, NaviampConnectSettingsUi(), actions(calls)) }
        onNodeWithText("Living Room is connected").assertExists()
        onNodeWithTag("connect-post-sign-in").assertIsOn().performClick()
        assertEquals(listOf("sign-in:false"), calls)
    }

    @Test fun theSheetDoesNotRepeatThatTheDeviceIsConnected() = runComposeUiTest {
        setContent {
            NaviampConnectPostConnectDialog(sheet(), NaviampColors.Dark, NaviampConnectSettingsUi(
                statusMessage = NaviampConnectStatusMessage(NaviampConnectStatusText.ConnectedToDevice, listOf("Living Room"))),
                actions(mutableListOf()))
        }
        onNodeWithText("Living Room is connected").assertExists()
        onNodeWithText("Connected to Living Room.").assertDoesNotExist()
    }

    @Test fun doneAndPlayDelegate() = runComposeUiTest {
        val calls = mutableListOf<String>()
        setContent { NaviampConnectPostConnectDialog(sheet(), NaviampColors.Dark, NaviampConnectSettingsUi(), actions(calls)) }
        onNodeWithText("Play on Living Room").performClick()
        onNodeWithText("Done").performClick()
        assertEquals(listOf("play", "done"), calls)
    }

    @Test fun busyDisablesButtonsAndDeviceWithoutSignInHidesTheOption() = runComposeUiTest {
        setContent {
            NaviampConnectPostConnectDialog(sheet().copy(busy = true, canSignIn = false), NaviampColors.Dark,
                NaviampConnectSettingsUi(), actions(mutableListOf()))
        }
        onNodeWithText("Done").assertIsNotEnabled()
        onNodeWithText("Play on Living Room").assertIsNotEnabled()
        onNodeWithTag("connect-post-sign-in").assertDoesNotExist()
    }

    @Test fun dismissingTheSheetIsNotDone() = runComposeUiTest {
        val calls = mutableListOf<String>()
        val dispatcher = NaviampSystemBackDispatcher()
        setContent {
            androidx.compose.runtime.CompositionLocalProvider(LocalNaviampSystemBackDispatcher provides dispatcher) {
                NaviampConnectPostConnectDialog(sheet(), NaviampColors.Dark, NaviampConnectSettingsUi(), actions(calls))
            }
        }
        runOnIdle { kotlin.test.assertNotNull(dispatcher.currentHandler).invoke() }
        assertEquals(listOf("dismiss"), calls, "Back closes without submitting the ticked sign-in")
    }

    @Test fun aMissingPasswordOutsideTheSheetHasItsOwnPrompt() = runComposeUiTest {
        kotlin.test.assertTrue(shouldShowConnectCredentialPrompt(NaviampConnectSettingsUi(needsProvisioningCredential = true)))
        kotlin.test.assertFalse(shouldShowConnectCredentialPrompt(NaviampConnectSettingsUi(needsProvisioningCredential = true,
            postConnect = sheet())))
        val calls = mutableListOf<String>()
        setContent { NaviampConnectCredentialDialog(NaviampConnectSettingsUi(needsProvisioningCredential = true), NaviampColors.Dark, actions(calls)) }
        onNodeWithTag("connect-credential-password").performTextInput("secret")
        onNodeWithTag("connect-credential-submit").performClick()
        assertEquals(listOf("password"), calls)
    }

    @Test fun pullingSaysWhichDeviceSignsThisOneIn() = runComposeUiTest {
        setContent {
            NaviampConnectPostConnectDialog(sheet().copy(pull = true), NaviampColors.Dark, NaviampConnectSettingsUi(),
                actions(mutableListOf()))
        }
        onNodeWithText("Sign this device in with accounts from Living Room").assertExists()
        onNodeWithTag("connect-post-choose").assertDoesNotExist()
    }

    @Test fun choosingListsAccountsAndAsksForAMissingPasswordHere() = runComposeUiTest {
        val calls = mutableListOf<String>()
        setContent {
            NaviampConnectPostConnectDialog(sheet().copy(canChoose = true, choosing = true, includeSettings = true,
                accounts = listOf(
                    NaviampConnectTransferAccountUi("home", "Home", "navidrome", true, NaviampConnectTransferAccountStatus.Ready),
                    NaviampConnectTransferAccountUi("old", "Old", "subsonic", true, NaviampConnectTransferAccountStatus.NeedsPassword),
                    NaviampConnectTransferAccountUi("cert", "Office", "navidrome", true, NaviampConnectTransferAccountStatus.SetUpOnDevice),
                )), NaviampColors.Dark, NaviampConnectSettingsUi(), actions(calls))
        }
        onNodeWithText("Needs password").assertExists()
        onNodeWithText("Set up on the device").assertExists()
        onNodeWithTag("connect-post-account-cert").assertIsOff().assertIsNotEnabled()
        onNodeWithTag("connect-post-password-old").performTextInput("typed")
        onNodeWithTag("connect-post-account-home").performClick()
        onNodeWithTag("connect-post-settings").assertIsOn().performClick()
        assertEquals(listOf("password:old", "account:home:false", "settings:false"), calls)
    }

    @Test fun resultsShowEachAccountsOutcome() = runComposeUiTest {
        setContent {
            NaviampConnectPostConnectDialog(sheet().copy(canChoose = true, results = listOf(
                NaviampConnectTransferResultUi("Home", "navidrome", NaviampConnectTransferOutcome.Added),
                NaviampConnectTransferResultUi("Old", "subsonic", NaviampConnectTransferOutcome.Failed),
            )), NaviampColors.Dark, NaviampConnectSettingsUi(), actions(mutableListOf()))
        }
        onNodeWithText("Home · Added").assertExists()
        onNodeWithText("Old · Could not sign in").assertExists()
        onNodeWithTag("connect-post-choose").assertDoesNotExist()
    }

    private fun sheet() = NaviampConnectPostConnectUi(deviceName = "Living Room", canSignIn = true,
        signInSelected = true, deviceHasAccounts = false, canPlayOn = true)

    private fun actions(calls: MutableList<String>) = NaviampConnectSettingsActions(
        onStartPairingMode = {}, onStopPairingMode = {}, onTargetSelected = {}, onTrustedDeviceSelected = {},
        onPlaybackDeviceSelected = {}, onLocalDeviceNameChanged = {}, onTrustedDeviceAliasChanged = { _, _ -> },
        onForgetTrustedDevice = {}, onPairingCodeChanged = {}, onSubmitPairingCode = {},
        onRemotePrevious = {}, onRemotePlayPause = {}, onRemoteNext = {}, onStopControlling = {},
        onRemoteHandoffQueue = {}, onReceiveRemoteQueue = {}, onProvisionTarget = {},
        onDismissSourceMismatchRecovery = {},
        remoteNowPlayingActions = NaviampNowPlayingActions({}, {}, {}, {}, {}, {}, {}),
        onPostConnectSignInChanged = { calls += "sign-in:$it" },
        onPostConnectPlayOnDevice = { calls += "play" },
        onPostConnectDone = { calls += "done" },
        onPostConnectDismiss = { calls += "dismiss" },
        onSubmitProvisioningCredential = { calls += "password" },
        onPostConnectAccountPassword = { id, _ -> if ("password:$id" !in calls) calls += "password:$id" },
        onPostConnectAccountSelected = { id, selected -> calls += "account:$id:$selected" },
        onPostConnectSettingsChanged = { calls += "settings:$it" },
    )
}
