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
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NaviampConnectDevicesSettingsTest {
    @Test fun showCodePanelStartsOnEnterAndStopsOnLeave() = runComposeUiTest {
        val calls = mutableListOf<String>()
        val shown = mutableStateOf(true)
        setContent {
            if (shown.value) {
                NaviampConnectShowCodePanel(device().copy(pairingCode = "123456", codeVisible = true),
                    NaviampColors.Dark, actions(calls))
            }
        }
        onNodeWithText("123 456").assertIsDisplayed()
        assertEquals(listOf("start"), calls)
        runOnIdle { shown.value = false }
        waitForIdle()
        assertEquals(listOf("start", "stop"), calls)
    }

    @Test fun showCodePanelShowsWhoConnectedAlongsideTheNextCode() = runComposeUiTest {
        val calls = mutableListOf<String>()
        setContent {
            NaviampConnectShowCodePanel(device().copy(codeVisible = true, pairingCode = "654321",
                connectedControllerName = "Pixel"), NaviampColors.Dark, actions(calls))
        }
        onNodeWithText("Connected to Pixel.").assertExists()
        onNodeWithText("654 321").assertExists()
        onNodeWithText("Connect another device").assertDoesNotExist()
    }

    @Test fun enterCodePanelReportsVisibilityAndListsOnlyNearbyDevices() = runComposeUiTest {
        val calls = mutableListOf<String>()
        val shown = mutableStateOf(true)
        setContent {
            if (shown.value) {
                NaviampConnectEnterCodePanel(device().copy(discoveredTargets = listOf(
                    NaviampConnectDiscoveredTargetUi("tv", "Living Room", "192.0.2.10"))), NaviampColors.Dark, actions(calls))
            }
        }
        assertEquals(listOf("visible:true"), calls)
        onNodeWithTag("connect-nearby-tv").performClick()
        assertTrue("select:tv" in calls)
        runOnIdle { shown.value = false }
        waitForIdle()
        assertEquals("visible:false", calls.last())
    }

    @Test fun typingDigitsDelegatesToCore() = runComposeUiTest {
        val calls = mutableListOf<String>()
        setContent { NaviampConnectEnterCodePanel(device(), NaviampColors.Dark, actions(calls)) }
        onNodeWithTag("connect-code-entry").performTextInput("123456")
        assertEquals("code:123456", calls.last())
        onNodeWithText("Pair securely").assertDoesNotExist()
    }

    @Test fun sixthDigitClosesTheKeyboardSoTheResultIsVisible() = runComposeUiTest {
        setContent { NaviampConnectEnterCodePanel(device(), NaviampColors.Dark, actions(mutableListOf())) }
        onNodeWithTag("connect-code-entry").performClick().assertIsFocused()
        onNodeWithTag("connect-code-entry").performTextInput("123456")
        waitForIdle()
        onNodeWithTag("connect-code-entry").assertIsNotFocused()
    }

    @Test fun trustedPlaybackDeviceCanBeConnectedFromItsRow() = runComposeUiTest {
        val calls = mutableListOf<String>()
        setContent {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                NaviampConnectSettingsSection(NaviampColors.Dark, device().copy(trustedDevices = listOf(
                    NaviampConnectTrustedDeviceUi("trusted-tv", "Living Room", reconnectAvailable = true,
                        playbackTarget = true))), actions(calls))
            }
        }
        onNodeWithText("Living Room").performScrollTo().performClick()
        onNodeWithTag("connect-trusted-trusted-tv").performScrollTo().performClick()
        assertEquals("trusted:trusted-tv", calls.last())
    }

    @Test fun permissionProblemShowsRecoveryInsteadOfEmptyNearby() = runComposeUiTest {
        setContent {
            NaviampConnectEnterCodePanel(device().copy(recovery = NaviampConnectRecoveryUi(
                NaviampConnectRecoveryProblem.LocalNetworkPermission, canOpenSettings = true)),
                NaviampColors.Dark, actions(mutableListOf()))
        }
        onNodeWithTag("connect-recovery").assertExists()
        onNodeWithTag("connect-nearby-empty").assertDoesNotExist()
    }

    @Test fun devicesSectionHasNoLegacyPairingControlsAndShowsOnePanelAtATime() = runComposeUiTest {
        val calls = mutableListOf<String>()
        setContent {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                NaviampConnectSettingsSection(NaviampColors.Dark, device().copy(
                    connectedTargetName = "Living Room",
                    trustedDevices = listOf(NaviampConnectTrustedDeviceUi("trusted-tv", "Living Room", playbackTarget = true)),
                ), actions(calls))
            }
        }
        listOf("Find Naviamp devices", "Pair securely", "Show pairing code", "Stop pairing",
            "Set up Living Room with this connection", "Trusted devices").forEach {
            onNodeWithText(it).assertDoesNotExist()
        }
        onNodeWithText("Your devices").performScrollTo().assertExists()
        onNodeWithText("Stop controlling Living Room").performScrollTo().assertExists()
        onNodeWithText("Let another device connect").performScrollTo().performClick()
        waitForIdle()
        assertEquals(listOf("start"), calls)
        onNodeWithText("Connect a device").performScrollTo().performClick()
        waitForIdle()
        assertEquals(listOf("start", "stop", "visible:true"), calls)
    }

    private fun device() = NaviampConnectSettingsUi(available = true, role = NaviampConnectUiRole.ControllerAndTarget,
        localDeviceName = "Pixel")

    private fun actions(calls: MutableList<String>) = NaviampConnectSettingsActions(
        onStartPairingMode = { calls += "start" }, onStopPairingMode = { calls += "stop" },
        onTargetSelected = { calls += "select:${it.instanceId}" }, onTrustedDeviceSelected = { calls += "trusted:${it.deviceId}" },
        onPlaybackDeviceSelected = {}, onLocalDeviceNameChanged = {}, onTrustedDeviceAliasChanged = { _, _ -> },
        onForgetTrustedDevice = {}, onPairingCodeChanged = { calls += "code:$it" }, onSubmitPairingCode = {},
        onRemotePrevious = {}, onRemotePlayPause = {}, onRemoteNext = {}, onStopControlling = {},
        onRemoteHandoffQueue = {}, onReceiveRemoteQueue = {}, onProvisionTarget = {},
        onDismissSourceMismatchRecovery = {},
        remoteNowPlayingActions = NaviampNowPlayingActions({}, {}, {}, {}, {}, {}, {}),
        onConnectScreenVisibilityChanged = { calls += "visible:$it" },
    )
}
