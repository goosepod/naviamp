package app.naviamp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NaviampConnectTargetSettingsTest {
    @Test fun standardSettingsStartsCancelsAndRemovesExpiredCodes() = runComposeUiTest {
        val calls = mutableListOf<String>()
        val state = mutableStateOf(target())
        setContent {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                NaviampConnectSettingsSection(NaviampColors.Dark, state.value, actions(calls))
            }
        }
        onNodeWithText("Show pairing code").performScrollTo().performClick()
        assertEquals(listOf("start"), calls)
        runOnIdle { state.value = target().copy(pairingPhase = NaviampConnectPairingUiPhase.Advertising, pairingCode = "123456") }
        onNodeWithText("123 456").performScrollTo().assertIsDisplayed()
        onNodeWithText("Stop pairing").performScrollTo().performClick()
        assertEquals(listOf("start", "stop"), calls)
        runOnIdle { state.value = target().copy(pairingPhase = NaviampConnectPairingUiPhase.Failed) }
        onNodeWithTag("connect-target-code").assertDoesNotExist()
        onNodeWithText("Show pairing code").assertExists()
    }

    @Test fun outgoingPairingDoesNotMasqueradeAsAnIncomingOffer() = runComposeUiTest {
        val calls = mutableListOf<String>()
        setContent {
            NaviampConnectTargetSettings(target().copy(
                pairingPhase = NaviampConnectPairingUiPhase.AwaitingCode, selectedTargetId = "other-device",
            ), NaviampColors.Dark, actions(calls))
        }
        onNodeWithText("Stop pairing").assertDoesNotExist()
        onNodeWithText("Show pairing code").performClick()
        assertEquals(listOf("start"), calls)
    }

    @Test fun incomingApprovalAndRejectionDelegateToCoreAndDisappearWithTheRequest() = runComposeUiTest {
        val calls = mutableListOf<String>()
        val state = mutableStateOf(target().copy(
            pairingPhase = NaviampConnectPairingUiPhase.AwaitingApproval, pendingControllerName = "Pixel",
        ))
        setContent { NaviampConnectTargetSettings(state.value, NaviampColors.Dark, actions(calls)) }
        onNodeWithText("Approve Pixel").performClick()
        onNodeWithTag("connect-target-reject").performClick()
        assertEquals(listOf("approve", "reject"), calls)
        runOnIdle { state.value = target().copy(pairingPhase = NaviampConnectPairingUiPhase.Paired) }
        onNodeWithTag("connect-target-approve").assertDoesNotExist()
        onNodeWithTag("connect-target-reject").assertDoesNotExist()
        onNodeWithText("Show pairing code").assertExists()
    }

    @Test fun sourceSetupNamesTheOfferAndKeepsTargetActionsIndependentOfOutgoingSetup() = runComposeUiTest {
        val calls = mutableListOf<String>()
        val state = mutableStateOf(target().copy(
            pendingProvisioningConnectionName = "Music", pendingProvisioningControllerName = "Pixel",
        ))
        setContent { NaviampConnectTargetSettings(state.value, NaviampColors.Dark, actions(calls)) }
        onNodeWithTag("connect-target-approve-setup").assertTextEquals("Set up Music").performClick()
        onNodeWithTag("connect-target-reject-setup").performClick()
        assertEquals(listOf("approve-setup", "reject-setup"), calls)
        runOnIdle { state.value = state.value.copy(provisioningBusy = true) }
        onNodeWithTag("connect-target-approve-setup").assertIsEnabled()
        onNodeWithTag("connect-target-reject-setup").assertIsEnabled()
        runOnIdle { state.value = target() }
        onNodeWithTag("connect-target-approve-setup").assertDoesNotExist()
    }

    @Test fun controllerOnlyDevicesKeepDiscoveryWithoutTargetControls() = runComposeUiTest {
        setContent {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                NaviampConnectSettingsSection(NaviampColors.Dark,
                    target().copy(role = NaviampConnectUiRole.Controller), actions(mutableListOf()))
            }
        }
        onNodeWithTag("connect-target-pairing").assertDoesNotExist()
        onNodeWithText("Find Naviamp devices").performScrollTo().assertIsDisplayed()
    }

    @Test fun targetOnlyDevicesExposePairingWithoutDiscovery() = runComposeUiTest {
        setContent {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                NaviampConnectSettingsSection(NaviampColors.Dark,
                    target().copy(role = NaviampConnectUiRole.Target), actions(mutableListOf()))
            }
        }
        onNodeWithText("Show pairing code").performScrollTo().assertIsDisplayed()
        onNodeWithText("Find Naviamp devices").assertDoesNotExist()
    }

    private fun target() = NaviampConnectSettingsUi(available = true, role = NaviampConnectUiRole.ControllerAndTarget)
    private fun actions(calls: MutableList<String>) = NaviampConnectSettingsActions(
        onStartPairingMode = { calls += "start" }, onStopPairingMode = { calls += "stop" },
        onRefreshTargets = {}, onTargetSelected = {}, onTrustedDeviceSelected = {},
        onPlaybackDeviceSelected = {}, onLocalDeviceNameChanged = {}, onTrustedDeviceAliasChanged = { _, _ -> },
        onForgetTrustedDevice = {}, onPairingCodeChanged = {}, onSubmitPairingCode = {},
        onApproveController = { calls += "approve" }, onRejectController = { calls += "reject" },
        onRemotePrevious = {}, onRemotePlayPause = {}, onRemoteNext = {}, onStopControlling = {},
        onRemoteHandoffQueue = {}, onReceiveRemoteQueue = {}, onProvisionTarget = {},
        onApproveProvisioning = { calls += "approve-setup" }, onRejectProvisioning = { calls += "reject-setup" },
        onDismissSourceMismatchRecovery = {},
        remoteNowPlayingActions = NaviampNowPlayingActions({}, {}, {}, {}, {}, {}, {}),
    )
}
