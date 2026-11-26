package app.naviamp.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.*
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NaviampTelevisionConnectSetupTest {
    @Test fun firstRunShowsCodeWithoutAButtonAndStopsOnLeave() = runComposeUiTest {
        val calls = mutableListOf<String>()
        val shown = mutableStateOf(true)
        setContent {
            if (shown.value) TelevisionFirstRunConnectSetup(target().copy(pairingCode = "123456", codeVisible = true),
                actions(calls), NaviampColors.Dark)
        }
        onNodeWithText("123 456").assertIsDisplayed()
        onNodeWithText("Show pairing code").assertDoesNotExist()
        onNodeWithText("Stop pairing").assertDoesNotExist()
        assertEquals(listOf("start"), calls)
        runOnIdle { shown.value = false }
        waitForIdle()
        assertEquals(listOf("start", "stop"), calls)
    }

    @Test fun televisionDevicesShowsTheCodeOnRequestWithoutApprovalRows() = runComposeUiTest {
        val calls = mutableListOf<String>()
        val shown = mutableStateOf(true)
        setContent {
            if (shown.value) TelevisionControllersSettings(target().copy(pairingCode = "654321"), actions(calls),
                NaviampColors.Dark, FocusRequester())
        }
        listOf("Pair a controller", "Stop pairing", "Approve", "Reject request").forEach { onNodeWithText(it).assertDoesNotExist() }
        assertEquals(emptyList(), calls)
        onNodeWithText("Let another device connect").performClick()
        waitForIdle()
        assertEquals(listOf("start"), calls)
        runOnIdle { shown.value = false }
        waitForIdle()
        assertEquals(listOf("start", "stop"), calls, "Leaving the page ends the offer")
    }

    @Test fun setupIntroPutsTheDirectOptionBelowTheCode() = runComposeUiTest {
        setContent {
            androidx.compose.foundation.layout.Column {
                TelevisionSetupIntro(addingAccount = false, connect = target().copy(pairingCode = "123456"),
                    actions = actions(mutableListOf()), colors = NaviampColors.Dark)
            }
        }
        val phoneTop = onNodeWithText("Set up from a phone or computer").fetchSemanticsNode().boundsInRoot.top
        val directTop = onNodeWithText("Or connect to your server directly from this TV.").fetchSemanticsNode().boundsInRoot.top
        kotlin.test.assertTrue(directTop > phoneTop, "The 'Or …' option follows the code box")
    }

    @Test fun addingAnAccountHasNoOrLine() = runComposeUiTest {
        setContent {
            TelevisionSetupIntro(addingAccount = true, connect = target(), actions = actions(mutableListOf()),
                colors = NaviampColors.Dark)
        }
        onNodeWithText("Or connect to your server directly from this TV.").assertDoesNotExist()
        onNodeWithText("Set up from a phone or computer").assertDoesNotExist()
    }

    private fun target() = NaviampConnectSettingsUi(available = true, role = NaviampConnectUiRole.Target,
        localDeviceName = "Living Room TV")

    private fun actions(calls: MutableList<String>) = NaviampConnectSettingsActions(
        onStartPairingMode = { calls += "start" }, onStopPairingMode = { calls += "stop" },
        onTargetSelected = {}, onTrustedDeviceSelected = {},
        onPlaybackDeviceSelected = {}, onLocalDeviceNameChanged = {}, onTrustedDeviceAliasChanged = { _, _ -> },
        onForgetTrustedDevice = {}, onPairingCodeChanged = {}, onSubmitPairingCode = {},
        onRemotePrevious = {}, onRemotePlayPause = {}, onRemoteNext = {}, onStopControlling = {},
        onRemoteHandoffQueue = {}, onReceiveRemoteQueue = {}, onProvisionTarget = {},
        onDismissSourceMismatchRecovery = {},
        remoteNowPlayingActions = NaviampNowPlayingActions({}, {}, {}, {}, {}, {}, {}),
    )
}
