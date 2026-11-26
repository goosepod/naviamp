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
        onRemotePrevious = {}, onRemotePlayPause = {}, onRemoteNext = {}, onStopControlling = {},
        onRemoteHandoffQueue = {}, onReceiveRemoteQueue = {}, onProvisionTarget = {},
        onDismissSourceMismatchRecovery = {},
        remoteNowPlayingActions = NaviampNowPlayingActions({}, {}, {}, {}, {}, {}, {}),
    )
}
